import type { IncomingMessage, ServerResponse } from "node:http";
import type { TutorialSaveRequest } from "../contracts/tutorials.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";
import {
  readTutorialCatalog,
  saveTutorialCatalog,
  TutorialConflictError,
  TutorialRegistryError,
  validateTutorialContent,
} from "../services/tutorialCatalog.ts";

const MAX_BODY_BYTES = 1024 * 1024;

export async function handleTutorialRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/tutorials/validate") {
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
    sendJson(response, 200, validateTutorialContent(body.content));
    return true;
  }

  if (pathname !== "/api/tutorials") return false;
  if (request.method === "GET") {
    sendJson(response, 200, await readTutorialCatalog(repoRoot));
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
  const saveRequest: TutorialSaveRequest = {
    content: body.content,
    expectedHash: body.expectedHash,
  };
  const validation = validateTutorialContent(saveRequest.content);
  if (!validation.valid) {
    sendError(
      response,
      422,
      "tutorial_validation_failed",
      "Tutorial catalog validation failed",
      validation.issues.map(item => `${item.path}: ${item.message}`),
    );
    return true;
  }
  try {
    sendJson(
      response,
      200,
      await saveTutorialCatalog(repoRoot, saveRequest),
    );
  } catch (error) {
    if (error instanceof TutorialConflictError) {
      sendError(response, 409, "tutorial_conflict", error.message, [error.currentHash]);
      return true;
    }
    if (error instanceof TutorialRegistryError) {
      sendError(response, 422, "tutorial_registry_locked", error.message);
      return true;
    }
    throw error;
  }
  return true;
}
