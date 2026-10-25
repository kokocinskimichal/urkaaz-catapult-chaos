import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { ApiErrorBody, HealthResponse } from "./contracts/api.ts";
import { readGitState, type GitStateReader } from "./services/gitState.ts";

export const STUDIO_VERSION = "0.1.0";

export interface StudioAppOptions {
  repoRoot: string;
  readGitState?: GitStateReader;
}

const LOOPBACK_HOSTS = new Set(["127.0.0.1", "localhost", "[::1]"]);

export function createStudioServer(options: StudioAppOptions): Server {
  const gitReader = options.readGitState ?? readGitState;
  return createServer((request, response) => {
    handle(request, response, options.repoRoot, gitReader).catch(() => {
      sendError(response, 500, "internal_error", "Unexpected server error");
    });
  });
}

async function handle(
  request: IncomingMessage,
  response: ServerResponse,
  repoRoot: string,
  gitReader: GitStateReader,
): Promise<void> {
  // Rejects DNS-rebinding requests: only loopback host names are served.
  if (!isLoopbackHost(request.headers.host)) {
    sendError(response, 403, "forbidden_host", "Studio only serves loopback hosts");
    return;
  }
  const url = new URL(request.url ?? "/", "http://localhost");
  if (url.pathname === "/api/health") {
    if (request.method !== "GET") {
      sendError(response, 405, "method_not_allowed", "Only GET is supported");
      return;
    }
    const git = await gitReader(repoRoot);
    const body: HealthResponse = {
      version: STUDIO_VERSION,
      repoRoot,
      branch: git.branch,
      clean: git.changedFiles === null ? null : git.changedFiles === 0,
      changedFiles: git.changedFiles,
    };
    sendJson(response, 200, body);
    return;
  }
  sendError(response, 404, "not_found", `No route for ${url.pathname}`);
}

function isLoopbackHost(hostHeader: string | undefined): boolean {
  if (!hostHeader) return false;
  const host = hostHeader.startsWith("[") ? hostHeader.slice(0, hostHeader.indexOf("]") + 1) : hostHeader.split(":")[0];
  return LOOPBACK_HOSTS.has(host);
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(body));
}

function sendError(response: ServerResponse, status: number, code: string, message: string, details: string[] = []): void {
  const body: ApiErrorBody = { code, message, details };
  sendJson(response, status, body);
}
