import type { IncomingMessage, ServerResponse } from "node:http";
import type { ProgressSaveRequest } from "../contracts/progress.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";
import {
  ProgressConflictError,
  ProgressRevisionError,
  readDeveloperProgress,
  saveDeveloperProgress,
  validateDeveloperProgress,
} from "../services/progressCatalog.ts";

const MAX_BODY_BYTES = 1024 * 1024;

export async function handleProgressRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/progress/validate") {
    if (request.method !== "POST") {
      sendError(response, 405, "method_not_allowed", "Only POST is supported");
      return true;
    }
    const body = await readJsonObject(request, response, MAX_BODY_BYTES);
    if (!body) return true;
    if (typeof body.content !== "string") {
      sendError(response, 400, "invalid_request", "content must be a string");
      return true;
    }
    sendJson(response, 200, validateDeveloperProgress(body.content));
    return true;
  }

  if (pathname !== "/api/progress") return false;
  if (request.method === "GET") {
    sendJson(response, 200, await readDeveloperProgress(repoRoot));
    return true;
  }
  if (request.method !== "PUT") {
    sendError(response, 405, "method_not_allowed", "Only GET and PUT are supported");
    return true;
  }

  const body = await readJsonObject(request, response, MAX_BODY_BYTES);
  if (!body) return true;
  if (typeof body.content !== "string" || typeof body.expectedHash !== "string") {
    sendError(response, 400, "invalid_request", "content and expectedHash are required");
    return true;
  }
  const requestBody: ProgressSaveRequest = {
    content: body.content,
    expectedHash: body.expectedHash,
  };
  const validation = validateDeveloperProgress(requestBody.content);
  if (!validation.valid) {
    sendError(
      response,
      422,
      "progress_validation_failed",
      "Developer progress validation failed",
      validation.issues.map(item => `${item.path}: ${item.message}`),
    );
    return true;
  }
  try {
    sendJson(
      response,
      200,
      await saveDeveloperProgress(repoRoot, requestBody.content, requestBody.expectedHash),
    );
  } catch (error) {
    if (error instanceof ProgressConflictError) {
      sendError(response, 409, "progress_conflict", error.message, [error.currentHash]);
      return true;
    }
    if (error instanceof ProgressRevisionError) {
      sendError(response, 422, "progress_revision_required", error.message, [
        `revision: change '${error.revision}' before saving progress edits`,
      ]);
      return true;
    }
    throw error;
  }
  return true;
}
