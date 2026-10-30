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

const initialBalance = JSON.stringify({
  schemaVersion: 1,
  balanceRevision: "initial",
  bosses: [{
    id: "boss.central-oak.level-05",
    presentationKey: "oak",
    archetype: "CENTRAL_OBJECTIVE",
    maxHealth: 12000,
    collision: { width: 1440, height: 2016 },
    movement: { kind: "STATIC", speedWorldUnitsPerSecond: 0 },
    attacks: [{
      target: "UNIT",
      damage: 150,
      minimumDamage: 35,
      rangeWorldUnits: 780,
      cooldownSeconds: 4,
      initialCooldownSeconds: 2.5,
    }],
    specialAbilities: [],
    immuneAmmunitionTypes: ["PLAGUE_CAULDRON"],
  }],
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-balance-api-"));
  const directory = join(repoRoot, "game-domain", "src", "main", "resources", "balance");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "game-balance.json"), initialBalance);
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

describe("balance API", () => {
  it("validates and saves with a matching content hash", async () => {
    const loaded = await call("/api/balance");
    const content = initialBalance.replace('"balanceRevision":"initial"', '"balanceRevision":"updated"');
    const validation = await call("/api/balance/validate", {
      method: "POST",
      body: { content },
    });
    expect(validation).toMatchObject({ status: 200, body: { valid: true, issues: [] } });

    const saved = await call("/api/balance", {
      method: "PUT",
      body: { content, expectedHash: (loaded.body as { hash: string }).hash },
    });
    expect(saved).toMatchObject({
      status: 200,
      body: { content, hash: expect.any(String) },
    });
    await expect(
      readFile(join(repoRoot, "game-domain/src/main/resources/balance/game-balance.json"), "utf8"),
    ).resolves.toBe(content);
  });

  it("rejects invalid JSON and stale writes without changing the file", async () => {
    const invalid = await call("/api/balance/validate", {
      method: "POST",
      body: { content: "{" },
    });
    expect(invalid).toMatchObject({ status: 200, body: { valid: false } });

    const stale = await call("/api/balance", {
      method: "PUT",
      body: { content: initialBalance, expectedHash: "stale" },
    });
    expect(stale.status).toBe(409);
    await expect(
      readFile(join(repoRoot, "game-domain/src/main/resources/balance/game-balance.json"), "utf8"),
    ).resolves.toBe(initialBalance);
  });

  it("requires a new balance revision when content changes", async () => {
    const loaded = await call("/api/balance");
    const changed = initialBalance.replace('"maxHealth":12000', '"maxHealth":12001');
    const response = await call("/api/balance", {
      method: "PUT",
      body: {
        content: changed,
        expectedHash: (loaded.body as { hash: string }).hash,
      },
    });

    expect(response.status).toBe(422);
    await expect(
      readFile(join(repoRoot, "game-domain/src/main/resources/balance/game-balance.json"), "utf8"),
    ).resolves.toBe(initialBalance);
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
