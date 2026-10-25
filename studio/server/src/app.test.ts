import type { AddressInfo } from "node:net";
import { request } from "node:http";
import { afterEach, describe, expect, it } from "vitest";
import { createStudioServer, STUDIO_VERSION } from "./app.ts";
import type { ApiErrorBody, HealthResponse } from "./contracts/api.ts";

const servers: ReturnType<typeof createStudioServer>[] = [];

async function start(changedFiles: number | null = 0, branch: string | null = "develop") {
  const server = createStudioServer({ repoRoot: "/repo", readGitState: async () => ({ branch, changedFiles }) });
  servers.push(server);
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  return (server.address() as AddressInfo).port;
}

function call(port: number, path: string, options: { method?: string; host?: string } = {}) {
  return new Promise<{ status: number; body: unknown }>((resolve, reject) => {
    const req = request(
      { host: "127.0.0.1", port, path, method: options.method ?? "GET", headers: options.host ? { host: options.host } : {} },
      res => {
        let data = "";
        res.on("data", chunk => (data += chunk));
        res.on("end", () => resolve({ status: res.statusCode ?? 0, body: JSON.parse(data) }));
      },
    );
    req.on("error", reject);
    req.end();
  });
}

afterEach(async () => {
  await Promise.all(servers.splice(0).map(server => new Promise(resolve => server.close(resolve))));
});

describe("studio server", () => {
  it("reports health with a clean working tree", async () => {
    const { status, body } = await call(await start(0), "/api/health");
    expect(status).toBe(200);
    expect(body).toEqual<HealthResponse>({
      version: STUDIO_VERSION,
      repoRoot: "/repo",
      branch: "develop",
      clean: true,
      changedFiles: 0,
    });
  });

  it("reports a dirty working tree", async () => {
    const { body } = await call(await start(3), "/api/health");
    expect(body).toMatchObject({ clean: false, changedFiles: 3 });
  });

  it("reports unknown git state when git is unavailable", async () => {
    const { body } = await call(await start(null, null), "/api/health");
    expect(body).toMatchObject({ branch: null, clean: null, changedFiles: null });
  });

  it("returns a structured error for unknown routes", async () => {
    const { status, body } = await call(await start(), "/api/nope");
    expect(status).toBe(404);
    expect(body).toEqual<ApiErrorBody>({ code: "not_found", message: "No route for /api/nope", details: [] });
  });

  it("rejects non-GET health requests", async () => {
    const { status, body } = await call(await start(), "/api/health", { method: "POST" });
    expect(status).toBe(405);
    expect((body as ApiErrorBody).code).toBe("method_not_allowed");
  });

  it("rejects non-loopback host headers", async () => {
    const { status, body } = await call(await start(), "/api/health", { host: "evil.example.com" });
    expect(status).toBe(403);
    expect((body as ApiErrorBody).code).toBe("forbidden_host");
  });
});
