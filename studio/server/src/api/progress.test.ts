import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { AddressInfo } from "node:net";
import { request } from "node:http";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createStudioServer } from "../app.ts";

let repoRoot: string;
let server: ReturnType<typeof createStudioServer>;
let port: number;

const initialPreset = JSON.stringify({
  schemaVersion: 2,
  revision: "developer-progress-1",
  currentLevel: 1,
  lives: 5,
  gold: 0,
  fieldsToApply: [],
  applyOnNextLaunch: false,
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-progress-api-"));
  const directory = join(repoRoot, "game-application", "src", "main", "resources", "progression");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "developer-progress.json"), initialPreset);
  server = createStudioServer({
    repoRoot,
    readGitState: async () => ({ branch: "develop", changedFiles: 0 }),
  });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  port = (server.address() as AddressInfo).port;
});

afterEach(async () => {
  await new Promise<void>(resolve => server.close(() => resolve()));
  await rm(repoRoot, { recursive: true, force: true });
});

describe("progress API", () => {
  it("validates and saves a changed preset with a matching hash", async () => {
    const loaded = await call("/api/progress");
    const content = initialPreset
      .replace('"revision":"developer-progress-1"', '"revision":"developer-progress-2"')
      .replace('"currentLevel":1', '"currentLevel":12')
      .replace('"lives":5', '"lives":3')
      .replace('"gold":0', '"gold":250')
      .replace('"fieldsToApply":[]', '"fieldsToApply":["currentLevel","lives","gold"]')
      .replace('"applyOnNextLaunch":false', '"applyOnNextLaunch":true');
    await expect(call("/api/progress/validate", {
      method: "POST",
      body: { content },
    })).resolves.toMatchObject({ status: 200, body: { valid: true } });
    const saved = await call("/api/progress", {
      method: "PUT",
      body: { content, expectedHash: (loaded.body as { hash: string }).hash },
    });

    expect(saved).toMatchObject({ status: 200, body: { content, hash: expect.any(String) } });
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/progression/developer-progress.json"), "utf8"),
    ).resolves.toBe(content);
  });

  it("rejects stale writes and content edits that keep the same revision", async () => {
    const loaded = await call("/api/progress");
    const changed = initialPreset.replace('"gold":0', '"gold":1');
    const stale = await call("/api/progress", {
      method: "PUT",
      body: { content: changed, expectedHash: "stale" },
    });
    expect(stale.status).toBe(409);

    const revisionMissing = await call("/api/progress", {
      method: "PUT",
      body: { content: changed, expectedHash: (loaded.body as { hash: string }).hash },
    });
    expect(revisionMissing.status).toBe(422);
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/progression/developer-progress.json"), "utf8"),
    ).resolves.toBe(initialPreset);
  });
});

function call(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<{ status: number; body: unknown }> {
  return new Promise((resolve, reject) => {
    const body = options.body === undefined ? undefined : JSON.stringify(options.body);
    const requestBody = request({
      host: "127.0.0.1",
      port,
      path,
      method: options.method ?? "GET",
      headers: body === undefined ? {} : {
        "content-type": "application/json",
        "content-length": Buffer.byteLength(body),
      },
    }, response => {
      let data = "";
      response.setEncoding("utf8");
      response.on("data", chunk => { data += chunk; });
      response.on("end", () => {
        let parsed: unknown;
        try {
          parsed = JSON.parse(data);
        } catch {
          parsed = data;
        }
        resolve({ status: response.statusCode ?? 0, body: parsed });
      });
    });
    requestBody.on("error", reject);
    if (body !== undefined) requestBody.write(body);
    requestBody.end();
  });
}
