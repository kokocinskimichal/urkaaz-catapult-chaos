import type { IncomingMessage, ServerResponse } from "node:http";
import type { TypographySaveRequest } from "../contracts/typography.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";
import {
  readButtonTypography,
  saveButtonTypography,
  TypographyConflictError,
  TypographyResourceError,
  TYPOGRAPHY_MAX_BODY_BYTES,
  validateButtonTypography,
} from "../services/typographyCatalog.ts";

export async function handleTypographyRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/typography/validate") {
    if (request.method !== "POST") {
      sendError(response, 405, "method_not_allowed", "Only POST is supported");
      return true;
    }
    const body = await readJsonObject(request, response, TYPOGRAPHY_MAX_BODY_BYTES);
    if (!body) return true;
    if (!Array.isArray(body.entries)) {
      sendError(response, 400, "invalid_request", "entries must be an array");
      return true;
    }
    sendJson(response, 200, await validateButtonTypography(repoRoot, body.entries));
    return true;
  }

  if (pathname !== "/api/typography") return false;
  if (request.method === "GET") {
    try {
      sendJson(response, 200, await readButtonTypography(repoRoot));
    } catch (error) {
      if (error instanceof TypographyResourceError) {
        sendError(response, 422, "typography_resources_invalid", error.message);
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

  const body = await readJsonObject(request, response, TYPOGRAPHY_MAX_BODY_BYTES);
  if (!body) return true;
  if (!Array.isArray(body.entries) || typeof body.expectedHash !== "string") {
    sendError(response, 400, "invalid_request", "entries and expectedHash are required");
    return true;
  }

  const requestBody: TypographySaveRequest = {
    entries: body.entries as TypographySaveRequest["entries"],
    expectedHash: body.expectedHash,
  };
  const validation = await validateButtonTypography(repoRoot, requestBody.entries);
  if (!validation.valid) {
    sendError(
      response,
      422,
      "typography_validation_failed",
      "Button typography validation failed",
      validation.issues.map(item => `${item.path}: ${item.message}`),
    );
    return true;
  }

  try {
    sendJson(response, 200, await saveButtonTypography(repoRoot, requestBody));
  } catch (error) {
    if (error instanceof TypographyConflictError) {
      sendError(response, 409, "typography_conflict", error.message, [error.currentHash]);
      return true;
    }
    if (error instanceof TypographyResourceError) {
      sendError(response, 422, "typography_resources_invalid", error.message);
      return true;
    }
    throw error;
  }
  return true;
}
