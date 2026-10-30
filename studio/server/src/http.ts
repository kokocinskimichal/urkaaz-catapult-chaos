import type { IncomingMessage } from "node:http";
import type { ServerResponse } from "node:http";
import type { ApiErrorBody } from "./contracts/api.ts";

export function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(body));
}

export function sendError(response: ServerResponse, status: number, code: string, message: string, details: string[] = []): void {
  const body: ApiErrorBody = { code, message, details };
  sendJson(response, status, body);
}

export async function readJsonObject(
  request: IncomingMessage,
  response: ServerResponse,
  maximumBytes: number,
): Promise<Record<string, unknown> | null> {
  try {
    const chunks: Buffer[] = [];
    let size = 0;
    for await (const chunk of request) {
      const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
      size += buffer.length;
      if (size > maximumBytes) {
        sendError(response, 413, "request_too_large", `Request body exceeds the ${maximumBytes} byte limit`);
        return null;
      }
      chunks.push(buffer);
    }
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
      sendError(response, 400, "invalid_request", "Request body must be a JSON object");
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch {
    sendError(response, 400, "invalid_request", "Request body must contain valid JSON");
    return null;
  }
}
