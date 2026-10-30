import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import type { IncomingMessage, ServerResponse } from "node:http";
import {
  CampaignChapterNotFoundError,
  CampaignLayoutArtworkMissingError,
  CampaignLayoutConflictError,
  CampaignLayoutLockTimeoutError,
  CampaignLayoutValidationError,
  listCampaignChapters,
  readCampaignMapLayout,
  readChapterCatalog,
  resolveCampaignAssetPath,
  saveCampaignMapLayout,
  validateCampaignMapLayout,
} from "../services/campaignMapCatalog.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";

const MAX_BODY_BYTES = 512 * 1024;
const LAYOUT_ROUTE = /^\/api\/campaign\/layouts\/([a-z][a-z0-9_]*)$/;
const ASSET_ROUTE = /^\/api\/campaign\/assets\/([a-z][a-z0-9_]*)$/;

export async function handleCampaignRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/campaign/chapters") {
    if (request.method !== "GET") {
      sendError(response, 405, "method_not_allowed", "Only GET is supported");
      return true;
    }
    sendJson(response, 200, await listCampaignChapters(repoRoot));
    return true;
  }

  if (pathname === "/api/campaign/layouts/validate") {
    if (request.method !== "POST") {
      sendError(response, 405, "method_not_allowed", "Only POST is supported");
      return true;
    }
    const body = await readJsonObject(request, response, MAX_BODY_BYTES);
    if (!body) return true;
    if (typeof body.chapterId !== "string" || typeof body.content !== "string") {
      sendError(response, 400, "invalid_request", "chapterId and content must be strings");
      return true;
    }
    const catalog = await readChapterCatalog(repoRoot);
    const chapter = catalog.chapters.find(candidate => candidate.id === body.chapterId);
    if (!chapter) {
      sendError(response, 404, "campaign_chapter_not_found", "Campaign chapter was not found");
      return true;
    }
    sendJson(response, 200, await validateCampaignMapLayout(repoRoot, chapter, body.content));
    return true;
  }

  const assetMatch = ASSET_ROUTE.exec(pathname);
  if (assetMatch) {
    if (request.method !== "GET") {
      sendError(response, 405, "method_not_allowed", "Only GET is supported");
      return true;
    }
    const assetPath = await resolveCampaignAssetPath(repoRoot, assetMatch[1]);
    if (!assetPath) {
      sendError(response, 404, "campaign_asset_not_found", "Campaign asset was not found");
      return true;
    }
    const file = await stat(assetPath);
    response.writeHead(200, {
      "content-type": "image/png",
      "content-length": file.size,
      "cache-control": "no-cache",
    });
    createReadStream(assetPath).pipe(response);
    return true;
  }

  const layoutMatch = LAYOUT_ROUTE.exec(pathname);
  if (!layoutMatch) return false;
  const chapterId = layoutMatch[1];
  if (request.method === "GET") {
    try {
      const layout = await readCampaignMapLayout(repoRoot, chapterId);
      if (!layout) {
        sendError(response, 404, "map_artwork_missing", "Campaign map artwork is not available for this chapter");
        return true;
      }
      sendJson(response, 200, layout);
    } catch (error) {
      if (error instanceof CampaignChapterNotFoundError) {
        sendError(response, 404, "campaign_chapter_not_found", error.message);
        return true;
      }
      if (error instanceof CampaignLayoutValidationError) {
        sendError(
          response,
          422,
          "campaign_layout_invalid",
          error.message,
          error.issues.filter(issue => issue.severity === "error").map(issue => `${issue.path}: ${issue.message}`),
        );
        return true;
      }
      throw error;
    }
    return true;
  }

  if (request.method !== "PUT") {
    sendError(response, 405, "method_not_allowed", "Only GET and PUT are supported");
    return true;
  }
  const body = await readJsonObject(request, response, MAX_BODY_BYTES);
  if (!body) return true;
  if (typeof body.content !== "string" || typeof body.expectedHash !== "string") {
    sendError(response, 400, "invalid_request", "content and expectedHash must be strings");
    return true;
  }
  try {
    sendJson(
      response,
      200,
      await saveCampaignMapLayout(repoRoot, chapterId, body.content, body.expectedHash),
    );
  } catch (error) {
    if (error instanceof CampaignChapterNotFoundError) {
      sendError(response, 404, "campaign_chapter_not_found", error.message);
      return true;
    }
    if (error instanceof CampaignLayoutArtworkMissingError) {
      sendError(response, 404, "map_artwork_missing", error.message);
      return true;
    }
    if (error instanceof CampaignLayoutConflictError) {
      sendError(
        response,
        409,
        "campaign_layout_conflict",
        error.message,
        error.currentHash ? [error.currentHash] : [],
      );
      return true;
    }
    if (error instanceof CampaignLayoutLockTimeoutError) {
      sendError(response, 503, "campaign_layout_busy", error.message);
      return true;
    }
    if (error instanceof CampaignLayoutValidationError) {
      sendError(
        response,
        422,
        "campaign_layout_validation_failed",
        error.message,
        error.issues.filter(issue => issue.severity === "error").map(issue => `${issue.path}: ${issue.message}`),
      );
      return true;
    }
    throw error;
  }
  return true;
}
