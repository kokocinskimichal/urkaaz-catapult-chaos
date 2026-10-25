import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import type { IncomingMessage, ServerResponse } from "node:http";
import { sendError, sendJson } from "../http.ts";
import { animationFramePath, readAnimationCatalog } from "../services/animationCatalog.ts";

const FRAME_ROUTE = /^\/api\/animations\/([a-z0-9_]+)\/frames\/(\d+)$/;

/** Returns true when the request path belongs to the animations resource. */
export async function handleAnimationRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  const isCatalog = pathname === "/api/animations";
  const frame = FRAME_ROUTE.exec(pathname);
  if (!isCatalog && !frame) return false;
  if (request.method !== "GET") {
    sendError(response, 405, "method_not_allowed", "Only GET is supported");
    return true;
  }
  if (isCatalog) {
    sendJson(response, 200, await readAnimationCatalog(repoRoot));
    return true;
  }
  const path = animationFramePath(repoRoot, frame![1], Number(frame![2]));
  const file = path ? await stat(path).catch(() => null) : null;
  if (!path || !file?.isFile()) {
    sendError(response, 404, "frame_not_found", `No frame ${frame![2]} for ${frame![1]}`);
    return true;
  }
  response.writeHead(200, { "content-type": "image/png", "content-length": file.size, "cache-control": "no-cache" });
  createReadStream(path).pipe(response);
  return true;
}
