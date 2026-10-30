import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import type { IncomingMessage, ServerResponse } from "node:http";
import type { MapValidationResponse } from "../contracts/maps.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";
import { decode } from "../../../web/src/tabs/maps/format/codec.ts";
import { parseAssetIndex } from "../../../web/src/tabs/maps/domain/assets.ts";
import { validate } from "../../../web/src/tabs/maps/domain/operations.ts";
import { isMapId, listMaps, MapConflictError, readMap, saveMap } from "../services/mapCatalog.ts";
import { readTerrainAssetIndex, resolveTerrainAssetPath } from "../services/terrainAssets.ts";

const MAX_BODY_BYTES = 4 * 1024 * 1024;
const MAP_ROUTE = /^\/api\/maps\/([a-z0-9][a-z0-9-]*)$/;

export async function handleMapRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/maps/validate") {
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
    const result = await validateMapContent(repoRoot, body.content);
    sendJson(response, 200, result);
    return true;
  }

  if (pathname === "/api/maps") {
    if (request.method !== "GET") {
      sendError(response, 405, "method_not_allowed", "Only GET is supported");
      return true;
    }
    sendJson(response, 200, { maps: await listMaps(repoRoot) });
    return true;
  }

  const match = MAP_ROUTE.exec(pathname);
  if (!match) return false;
  const id = match[1];
  if (!isMapId(id)) {
    sendError(response, 400, "invalid_map_id", "Map id is not supported");
    return true;
  }
  if (request.method === "GET") {
    const map = await readMap(repoRoot, id);
    if (!map) {
      sendError(response, 404, "map_not_found", `Map '${id}' was not found`);
      return true;
    }
    sendJson(response, 200, map);
    return true;
  }
  if (request.method !== "PUT") {
    sendError(response, 405, "method_not_allowed", "Only GET and PUT are supported");
    return true;
  }

  const body = await readJsonObject(request, response, MAX_BODY_BYTES);
  if (!body) return true;
  if (typeof body.content !== "string" ||
    (body.expectedHash !== null && typeof body.expectedHash !== "string")) {
    sendError(response, 400, "invalid_request", "content and expectedHash are required");
    return true;
  }
  let mapId: string;
  try {
    const map = decode(body.content);
    mapId = map.mapId;
    if (mapId !== id) {
      sendError(response, 400, "map_id_mismatch", `Map document id '${mapId}' does not match route id '${id}'`);
      return true;
    }
  } catch (error) {
    sendError(response, 422, "invalid_map", error instanceof Error ? error.message : String(error));
    return true;
  }

  const validation = await validateMapContent(repoRoot, body.content);
  if (!validation.valid) {
    sendError(
      response,
      422,
      "map_validation_failed",
      "Map validation failed",
      validation.issues.filter(issue => issue.severity === "error").map(issue => `${issue.path}: ${issue.message}`),
    );
    return true;
  }
  try {
    sendJson(response, 200, await saveMap(repoRoot, id, body.content, body.expectedHash));
  } catch (error) {
    if (error instanceof MapConflictError) {
      sendError(response, 409, "map_conflict", error.message, error.currentHash ? [error.currentHash] : []);
      return true;
    }
    throw error;
  }
  return true;
}

export async function handleTerrainAssetRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/assets/terrain/index") {
    if (request.method !== "GET") {
      sendError(response, 405, "method_not_allowed", "Only GET is supported");
      return true;
    }
    const indexText = await readTerrainAssetIndex(repoRoot);
    response.writeHead(200, {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "no-store",
    });
    response.end(indexText);
    return true;
  }

  const prefix = "/api/assets/terrain/";
  if (!pathname.startsWith(prefix)) return false;
  if (request.method !== "GET") {
    sendError(response, 405, "method_not_allowed", "Only GET is supported");
    return true;
  }
  let relativePath: string;
  try {
    relativePath = decodeURIComponent(pathname.slice(prefix.length));
  } catch {
    sendError(response, 404, "asset_not_found", "Terrain asset was not found");
    return true;
  }
  const path = await resolveTerrainAssetPath(repoRoot, relativePath);
  const file = path ? await stat(path).catch(() => null) : null;
  if (!path || !file?.isFile()) {
    sendError(response, 404, "asset_not_found", "Terrain asset was not found");
    return true;
  }
  response.writeHead(200, {
    "content-type": "image/png",
    "content-length": file.size,
    "cache-control": "no-cache",
  });
  createReadStream(path).pipe(response);
  return true;
}

async function validateMapContent(repoRoot: string, content: string): Promise<MapValidationResponse> {
  try {
    const map = decode(content);
    const assetKeys = parseAssetIndex(await readTerrainAssetIndex(repoRoot));
    const issues = validate({ map, phase: "TERRAIN_EDITING", revision: 0 }, assetKeys);
    return {
      valid: !issues.some(issue => issue.severity === "error"),
      issues: issues.map(({ severity, path, message }) => ({ severity, path, message })),
    };
  } catch (error) {
    return {
      valid: false,
      issues: [{
        severity: "error",
        path: "map",
        message: error instanceof Error ? error.message : String(error),
      }],
    };
  }
}
