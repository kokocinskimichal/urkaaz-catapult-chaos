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
