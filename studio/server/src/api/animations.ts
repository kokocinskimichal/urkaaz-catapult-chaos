import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import type { IncomingMessage, ServerResponse } from "node:http";
import { sendError, sendJson } from "../http.ts";
import {
  animationFramePath,
  readAnimationCatalog,
  teamAnimationMaskPath,
  teamTexturePath,
} from "../services/animationCatalog.ts";

const FRAME_ROUTE = /^\/api\/animations\/([a-z0-9_]+)\/frames\/(\d+)$/;
const MASK_ROUTE = /^\/api\/animations\/(anim_character_[a-z0-9_]+_(?:idle|move|attack|death))\/masks\/(\d+)$/;
const TEXTURE_ROUTE = /^\/api\/animations\/team-textures\/([a-z0-9_]+)\/(blue|red)$/;

/** Returns true when the request path belongs to the animations resource. */
export async function handleAnimationRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  const isCatalog = pathname === "/api/animations";
  const frame = FRAME_ROUTE.exec(pathname);
  const mask = MASK_ROUTE.exec(pathname);
  const texture = TEXTURE_ROUTE.exec(pathname);
  if (!isCatalog && !frame && !mask && !texture) return false;
  if (request.method !== "GET") {
    sendError(response, 405, "method_not_allowed", "Only GET is supported");
    return true;
  }
  if (isCatalog) {
    sendJson(response, 200, await readAnimationCatalog(repoRoot));
    return true;
  }
  const path = frame
    ? animationFramePath(repoRoot, frame[1], Number(frame[2]))
    : mask
      ? teamAnimationMaskPath(repoRoot, mask[1], Number(mask[2]))
      : texture
        ? await teamTexturePath(repoRoot, texture[1], texture[2])
        : null;
  const file = path ? await stat(path).catch(() => null) : null;
  if (!path || !file?.isFile()) {
    sendError(response, 404, "animation_asset_not_found", "Animation asset was not found");
    return true;
  }
  response.writeHead(200, { "content-type": "image/png", "content-length": file.size, "cache-control": "no-cache" });
  createReadStream(path).pipe(response);
  return true;
}
