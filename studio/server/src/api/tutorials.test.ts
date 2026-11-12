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

const initialCatalog = JSON.stringify({
  schemaVersion: 1,
  revision: "tutorial-1",
  registeredMechanicIds: [],
  registeredEventKinds: [],
  registeredMilestoneIds: [],
  targets: [{ targetId: "dashboard.campaign", screen: "DASHBOARD", kind: "VIEW" }],
  tutorials: [{
    tutorialId: "dashboard.intro",
    scope: "SCREEN",
    enabled: true,
    trigger: { type: "SCREEN_ENTERED", screen: "DASHBOARD" },
    requires: [],
    priority: 1,
    introduces: [],
    rules: {
      pauseSimulationOnHint: true,
      suspendAiWhileActive: true,
      restrictAiToIntroducedMechanics: false,
    },
    steps: [{
      stepId: "welcome",
      targetId: "dashboard.campaign",
      text: { pl: "Witaj", en: "Welcome", es: "Bienvenido" },
      placementPreference: "AUTO",
      completion: { type: "NEXT" },
      unlocksTargets: [],
      supplementalContent: null,
    }],
  }],
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-tutorial-api-"));
  const directory = join(repoRoot, "game-application/src/main/resources/tutorial");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "tutorial-catalog.json"), initialCatalog);
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

describe("tutorial API", () => {
  it("reads, validates, and saves the catalog with an automatic revision bump", async () => {
    const loaded = await call("/api/tutorials");
    const changed = initialCatalog.replace('"priority":1', '"priority":2');
    await expect(call("/api/tutorials/validate", {
      method: "POST",
      body: { content: changed },
    })).resolves.toMatchObject({ status: 200, body: { valid: true } });

    const saved = await call("/api/tutorials", {
      method: "PUT",
      body: { content: changed, expectedHash: (loaded.body as { hash: string }).hash },
    });

    expect(saved.status).toBe(200);
    expect(JSON.parse((saved.body as { content: string }).content)).toMatchObject({
      revision: "tutorial-2",
    });
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/tutorial/tutorial-catalog.json"), "utf8"),
    ).resolves.toBe((saved.body as { content: string }).content);
  });

  it("rejects stale hashes and invalid content without modifying the file", async () => {
    const loaded = await call("/api/tutorials");
    const stale = await call("/api/tutorials", {
      method: "PUT",
      body: { content: initialCatalog, expectedHash: "stale" },
    });
    expect(stale.status).toBe(409);

    const invalid = await call("/api/tutorials", {
      method: "PUT",
      body: {
        content: initialCatalog.replace('"en":"Welcome"', '"en":""'),
        expectedHash: (loaded.body as { hash: string }).hash,
      },
    });
    expect(invalid.status).toBe(422);
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/tutorial/tutorial-catalog.json"), "utf8"),
    ).resolves.toBe(initialCatalog);
  });
});

function call(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<{ status: number; body: unknown }> {
  return new Promise((resolve, reject) => {
    const body = options.body === undefined ? undefined : JSON.stringify(options.body);
    const req = request({
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
    req.on("error", reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}
