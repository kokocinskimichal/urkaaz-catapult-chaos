import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import type { AddressInfo } from "node:net";
import { request } from "node:http";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createStudioServer } from "../app.ts";
import { readCampaignMapLayout, readChapterCatalog } from "../services/campaignMapCatalog.ts";

let repoRoot: string;
let server: ReturnType<typeof createStudioServer>;
let port: number;

const chapters = {
  schemaVersion: 1,
  chapters: [
    {
      id: "green_frontier",
      title: "The Green Frontier",
      firstLevel: 1,
      lastLevel: 10,
      biome: "green_frontier",
      bossPositions: [5, 10],
    },
    {
      id: "broken_valley",
      title: "The Broken Valley",
      firstLevel: 11,
      lastLevel: 20,
      biome: "frostbound",
      bossPositions: [5, 10],
    },
  ],
};

function layout(x = 0.145) {
  return {
    schemaVersion: 1,
    chapterId: "green_frontier",
    backgroundAssetKey: "campaign_chapter_01_green_frontier_map",
    imageWidthPx: 160,
    imageHeightPx: 90,
    anchors: Array.from({ length: 10 }, (_, index) => ({
      level: index + 1,
      x: index === 0 ? x : (index + 1) / 12,
      y: (index + 1) / 12,
    })),
  };
}

function pngHeader(width: number, height: number): Buffer {
  const buffer = Buffer.alloc(24);
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(buffer, 0);
  buffer.writeUInt32BE(13, 8);
  buffer.write("IHDR", 12);
  buffer.writeUInt32BE(width, 16);
  buffer.writeUInt32BE(height, 20);
  return buffer;
}

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-campaign-"));
  await Promise.all([
    mkdir(join(repoRoot, "game-campaign", "src", "main", "resources", "campaign-map-layouts"), { recursive: true }),
    mkdir(join(repoRoot, "assets_src", "campaign", "chapter-maps"), { recursive: true }),
    mkdir(join(repoRoot, "assets_src", "campaign", "ui"), { recursive: true }),
    mkdir(join(repoRoot, "assets_src", "campaign", "map-markers"), { recursive: true }),
  ]);
  await writeFile(
    join(repoRoot, "game-campaign", "src", "main", "resources", "campaign-chapters.json"),
    JSON.stringify(chapters),
  );
  await writeFile(
    join(repoRoot, "game-campaign", "src", "main", "resources", "campaign-map-layouts", "green_frontier.json"),
    JSON.stringify(layout()),
  );
  await writeFile(
    join(repoRoot, "assets_src", "campaign", "chapter-maps", "campaign_chapter_01_green_frontier_map.png"),
    pngHeader(160, 90),
  );
  await writeFile(
    join(repoRoot, "assets_src", "campaign", "ui", "campaign_map_frame_overlay.png"),
    "frame asset",
  );
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

describe("campaign map API", () => {
  it("accepts the checked-in chapter catalog and Android Chapter 1 layout", async () => {
    const sourceRoot = resolve(process.cwd(), "../..");
    const catalog = await readChapterCatalog(sourceRoot);
    const layout = await readCampaignMapLayout(sourceRoot, "green_frontier");

    expect(catalog.chapters).toHaveLength(10);
    expect(layout).not.toBeNull();
    expect(layout?.content).toContain('"backgroundAssetKey": "campaign_chapter_01_green_frontier_map"');
  });

  it("lists chapters and distinguishes chapters without map layouts", async () => {
    await expect(call("/api/campaign/chapters")).resolves.toMatchObject({
      status: 200,
      body: {
        chapters: [
          { id: "green_frontier", mapAvailable: true },
          { id: "broken_valley", mapAvailable: false },
        ],
      },
    });

    await expect(call("/api/campaign/layouts/broken_valley")).resolves.toMatchObject({
      status: 404,
      body: { code: "map_artwork_missing" },
    });
  });

  it("reads a campaign layout with a content hash and serves allowlisted art", async () => {
    const stored = JSON.stringify(layout());
    await expect(call("/api/campaign/layouts/green_frontier")).resolves.toMatchObject({
      status: 200,
      body: {
        chapterId: "green_frontier",
        content: stored,
        hash: expect.any(String),
      },
    });
    await expect(call("/api/campaign/assets/campaign_map_frame_overlay")).resolves.toMatchObject({
      status: 200,
      body: "frame asset",
    });
    await expect(call("/api/campaign/assets/../../etc/passwd")).resolves.toMatchObject({
      status: 404,
      body: { code: "not_found" },
    });
  });

  it("saves valid positions and rejects stale hashes without overwriting", async () => {
    const first = await call("/api/campaign/layouts/green_frontier");
    const hash = (first.body as { hash: string }).hash;
    const changed = JSON.stringify(layout(0.19));
    const saved = await call("/api/campaign/layouts/green_frontier", {
      method: "PUT",
      body: { content: changed, expectedHash: hash },
    });

    expect(saved).toMatchObject({
      status: 200,
      body: { content: changed, hash: expect.any(String) },
    });

    const stale = await call("/api/campaign/layouts/green_frontier", {
      method: "PUT",
      body: { content: JSON.stringify(layout(0.22)), expectedHash: hash },
    });
    expect(stale).toMatchObject({ status: 409, body: { code: "campaign_layout_conflict" } });
    await expect(
      readFile(
        join(repoRoot, "game-campaign", "src", "main", "resources", "campaign-map-layouts", "green_frontier.json"),
        "utf8",
      ),
    ).resolves.toBe(changed);
  });

  it("waits for another Studio instance's layout lock before checking the hash", async () => {
    const current = await call("/api/campaign/layouts/green_frontier");
    const { content, hash } = current.body as { content: string; hash: string };
    const layoutPath = join(
      repoRoot,
      "game-campaign",
      "src",
      "main",
      "resources",
      "campaign-map-layouts",
      "green_frontier.json",
    );
    const lockPath = `${layoutPath}.lock`;
    const changed = JSON.stringify(layout(0.19));
    await writeFile(lockPath, `${process.pid}\n`);

    const saving = call("/api/campaign/layouts/green_frontier", {
      method: "PUT",
      body: { content: changed, expectedHash: hash },
    });
    let unchanged = "";
    let saved: Awaited<ReturnType<typeof call>> | undefined;
    try {
      await delay(50);
      unchanged = await readFile(layoutPath, "utf8");
    } finally {
      await rm(lockPath, { force: true });
      saved = await saving;
    }

    expect(unchanged).toBe(content);
    expect(saved).toMatchObject({
      status: 200,
      body: { content: changed, hash: expect.any(String) },
    });
  });

  it("validates normalized coordinates before writing", async () => {
    const current = await call("/api/campaign/layouts/green_frontier");
    const hash = (current.body as { hash: string }).hash;
    const invalid = JSON.stringify(layout(1.2));

    await expect(call("/api/campaign/layouts/validate", {
      method: "POST",
      body: { chapterId: "green_frontier", content: invalid },
    })).resolves.toMatchObject({
      status: 200,
      body: { valid: false, issues: [{ path: "anchors[0].x" }] },
    });
    await expect(call("/api/campaign/layouts/green_frontier", {
      method: "PUT",
      body: { content: invalid, expectedHash: hash },
    })).resolves.toMatchObject({
      status: 422,
      body: { code: "campaign_layout_validation_failed" },
    });
  });

  it("rejects unknown chapter IDs and malformed chapter catalogs", async () => {
    await expect(call("/api/campaign/layouts/../../etc/passwd")).resolves.toMatchObject({
      status: 404,
      body: { code: "not_found" },
    });
    await writeFile(
      join(repoRoot, "game-campaign", "src", "main", "resources", "campaign-chapters.json"),
      "{}",
    );
    await expect(call("/api/campaign/chapters")).resolves.toMatchObject({ status: 500 });
  });

  it("does not serve campaign assets through symlinks outside the allowlisted folder", async () => {
    const outsideDirectory = await mkdtemp(join(tmpdir(), "studio-campaign-outside-"));
    const outside = join(outsideDirectory, "private.png");
    const markerPath = join(
      repoRoot,
      "assets_src",
      "campaign",
      "map-markers",
      "campaign_map_marker_boss.png",
    );
    await writeFile(outside, "private");
    await symlink(outside, markerPath);
    try {
      await expect(call("/api/campaign/assets/campaign_map_marker_boss")).resolves.toMatchObject({
        status: 500,
        body: { code: "internal_error" },
      });
    } finally {
      await rm(outsideDirectory, { recursive: true, force: true });
    }
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
      const chunks: Buffer[] = [];
      response.on("data", chunk => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)));
      response.on("end", () => {
        const content = Buffer.concat(chunks).toString("utf8");
        let parsed: unknown;
        try {
          parsed = JSON.parse(content);
        } catch {
          parsed = content;
        }
        resolve({ status: response.statusCode ?? 0, body: parsed });
      });
    });
    req.on("error", reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}
