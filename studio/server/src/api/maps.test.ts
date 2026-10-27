import { mkdtemp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import type { AddressInfo } from "node:net";
import { request } from "node:http";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { defaultMap } from "../../../web/src/tabs/maps/domain/map.ts";
import { parseAssetIndex } from "../../../web/src/tabs/maps/domain/assets.ts";
import { validate } from "../../../web/src/tabs/maps/domain/operations.ts";
import { decode, encode } from "../../../web/src/tabs/maps/format/codec.ts";
import { createStudioServer } from "../app.ts";
import { readTerrainAssetIndex } from "../services/terrainAssets.ts";

let repoRoot: string;
let server: ReturnType<typeof createStudioServer>;
let port: number;

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-maps-"));
  await Promise.all([
    mkdir(join(repoRoot, "game-assets", "src", "main", "resources", "terrain", "green_frontier"), { recursive: true }),
    mkdir(join(repoRoot, "android-app", "src", "main", "res", "drawable-nodpi"), { recursive: true }),
  ]);
  await writeFile(
    join(repoRoot, "game-assets", "src", "main", "resources", "terrain", "asset-index.txt"),
    "terrain_green_frontier_tree_00.png\n",
  );
  await writeFile(
    join(repoRoot, "game-assets", "src", "main", "resources", "terrain", "green_frontier", "terrain_green_frontier_tree_00.png"),
    "png",
  );
  server = createStudioServer({ repoRoot, readGitState: async () => ({ branch: "develop", changedFiles: 0 }) });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  port = (server.address() as AddressInfo).port;
});

afterEach(async () => {
  await new Promise<void>(resolve => server.close(() => resolve()));
  await rm(repoRoot, { recursive: true, force: true });
});

describe("map API", () => {
  it("lists repository maps and saves both copies with a content-pair hash", async () => {
    const content = encode(defaultMap("demo-map"));
    const saved = await call("/api/maps/demo-map", {
      method: "PUT",
      body: { content, expectedHash: null },
    });

    expect(saved.status).toBe(200);
    expect(saved.body).toMatchObject({ id: "demo-map", content, synchronized: true });
    expect((saved.body as { hash: string }).hash).toHaveLength(64);
    await expect(readFile(join(repoRoot, "game-campaign/src/main/resources/maps/demo-map.json"), "utf8"))
      .resolves.toBe(content);
    await expect(readFile(join(repoRoot, "android-app/src/main/assets/maps/demo-map.json"), "utf8"))
      .resolves.toBe(content);
    await expect(call("/api/maps")).resolves.toMatchObject({
      status: 200,
      body: { maps: [{ id: "demo-map", biome: "green_frontier", synchronized: true }] },
    });
  });

  it("rejects stale map writes without altering either file", async () => {
    const first = encode(defaultMap("demo-map"));
    await call("/api/maps/demo-map", { method: "PUT", body: { content: first, expectedHash: null } });
    const changed = encode(defaultMap("demo-map", "frostbound"));

    const response = await call("/api/maps/demo-map", {
      method: "PUT",
      body: { content: changed, expectedHash: null },
    });

    expect(response.status).toBe(409);
    await expect(readFile(join(repoRoot, "game-campaign/src/main/resources/maps/demo-map.json"), "utf8"))
      .resolves.toBe(first);
    await expect(readFile(join(repoRoot, "android-app/src/main/assets/maps/demo-map.json"), "utf8"))
      .resolves.toBe(first);
  });

  it("rejects mismatched map IDs and reports validation errors", async () => {
    const mismatch = await call("/api/maps/another-map", {
      method: "PUT",
      body: { content: encode(defaultMap("demo-map")), expectedHash: null },
    });
    expect(mismatch.status).toBe(400);

    const map = defaultMap("demo-map");
    map.objects.push({
      id: "bad-object",
      type: "DECORATION",
      x: 3600,
      y: 3180,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "unknown.asset",
      collision: "NONE",
      allowOverlap: false,
    });
    const validation = await call("/api/maps/validate", {
      method: "POST",
      body: { content: encode(map) },
    });

    expect(validation.status).toBe(200);
    expect(validation.body).toMatchObject({
      valid: false,
      issues: [{ severity: "error", path: "objects.bad-object.assetKey" }],
    });
    await expect(call("/api/maps/demo-map")).resolves.toMatchObject({ status: 404 });
  });

  it("serves the generated terrain index and assets from repository sources", async () => {
    await expect(call("/api/assets/terrain/index")).resolves.toMatchObject({
      status: 200,
      body: "terrain_green_frontier_tree_00.png\n",
    });
    await expect(call("/api/assets/terrain/green_frontier/terrain_green_frontier_tree_00.png"))
      .resolves.toMatchObject({ status: 200, body: "png" });
    await expect(call("/api/assets/terrain/green_frontier/../../etc/passwd"))
      .resolves.toMatchObject({ status: 404 });
  });

  it("accepts every checked-in campaign map with the shared validator", async () => {
    const sourceRoot = resolve(process.cwd(), "../..");
    const mapsDirectory = join(sourceRoot, "game-campaign", "src", "main", "resources", "maps");
    const assetKeys = parseAssetIndex(await readTerrainAssetIndex(sourceRoot));
    const files = (await readdir(mapsDirectory)).filter(name => name.endsWith(".json"));
    const invalidMaps: string[] = [];

    for (const file of files) {
      const map = decode(await readFile(join(mapsDirectory, file), "utf8"));
      const issues = validate(
        { map, phase: "TERRAIN_EDITING", revision: 0 },
        assetKeys,
      ).filter(issue => issue.severity === "error");
      if (issues.length > 0) invalidMaps.push(`${file}: ${issues.map(issue => issue.message).join(", ")}`);
    }

    expect(invalidMaps).toEqual([]);
  });
});

function call(path: string, options: { method?: string; body?: unknown } = {}) {
  return new Promise<{ status: number; body: unknown }>((resolve, reject) => {
    const body = options.body === undefined ? undefined : JSON.stringify(options.body);
    const req = request({
      host: "127.0.0.1",
      port,
      path,
      method: options.method ?? "GET",
      headers: body === undefined ? {} : { "content-type": "application/json", "content-length": Buffer.byteLength(body) },
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
