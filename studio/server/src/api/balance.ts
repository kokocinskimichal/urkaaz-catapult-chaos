import type { IncomingMessage, ServerResponse } from "node:http";
import type { BalanceSaveRequest } from "../contracts/balance.ts";
import { readJsonObject, sendError, sendJson } from "../http.ts";
import {
  BalanceConflictError,
  BalanceRevisionError,
  readBalance,
  saveBalance,
  validateBalanceContent,
} from "../services/balanceCatalog.ts";

const MAX_BODY_BYTES = 1024 * 1024;

export async function handleBalanceRoute(
  request: IncomingMessage,
  response: ServerResponse,
  pathname: string,
  repoRoot: string,
): Promise<boolean> {
  if (pathname === "/api/balance/validate") {
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
    sendJson(response, 200, validateBalanceContent(body.content));
    return true;
  }

  if (pathname !== "/api/balance") return false;
  if (request.method === "GET") {
    sendJson(response, 200, await readBalance(repoRoot));
    return true;
  }
  if (request.method !== "PUT") {
    sendError(response, 405, "method_not_allowed", "Only GET and PUT are supported");
    return true;
  }
  const body = await readJsonObject(request, response, MAX_BODY_BYTES);
  if (!body) return true;
  if (
    typeof body.content !== "string" ||
    typeof body.expectedHash !== "string"
  ) {
    sendError(response, 400, "invalid_request", "content and expectedHash are required");
    return true;
  }
  const saveRequest: BalanceSaveRequest = {
    content: body.content,
    expectedHash: body.expectedHash,
  };
  const validation = validateBalanceContent(saveRequest.content);
  if (!validation.valid) {
    sendError(
      response,
      422,
      "balance_validation_failed",
      "Game balance validation failed",
      validation.issues.map(item => `${item.path}: ${item.message}`),
    );
    return true;
  }
  try {
    sendJson(
      response,
      200,
      await saveBalance(repoRoot, saveRequest.content, saveRequest.expectedHash),
    );
  } catch (error) {
    if (error instanceof BalanceConflictError) {
      sendError(response, 409, "balance_conflict", error.message, [error.currentHash]);
      return true;
    }
    if (error instanceof BalanceRevisionError) {
      sendError(response, 422, "balance_revision_required", error.message, [
        `balanceRevision: change '${error.revision}' before saving balance edits`,
      ]);
      return true;
    }
    throw error;
  }
  return true;
}
