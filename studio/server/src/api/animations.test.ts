import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { createStudioServer } from "../app.ts";
import type { AnimationCatalogResponse } from "../contracts/animations.ts";
import { resolveRepoRoot } from "../services/repoRoot.ts";

const servers: ReturnType<typeof createStudioServer>[] = [];

async function baseUrl(): Promise<string> {
  const server = createStudioServer({ repoRoot: await resolveRepoRoot(), readGitState: async () => ({ branch: null, changedFiles: null }) });
  servers.push(server);
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
}

afterEach(async () => {
  await Promise.all(servers.splice(0).map(server => new Promise(resolve => server.close(resolve))));
});

describe("animations API", () => {
  it("serves the catalog", async () => {
    const response = await fetch(`${await baseUrl()}/api/animations`);
    const catalog = (await response.json()) as AnimationCatalogResponse;
    expect(response.status).toBe(200);
    expect(catalog.sequences.some(sequence => sequence.id === "anim_character_defender_move_blue")).toBe(true);
  });

  it("serves a frame as PNG", async () => {
    const response = await fetch(`${await baseUrl()}/api/animations/anim_character_defender_move_blue/frames/0`);
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/png");
    const bytes = new Uint8Array(await response.arrayBuffer());
    expect([...bytes.slice(1, 4)]).toEqual([0x50, 0x4e, 0x47]);
  });

  it("returns 404 for missing frames and unsafe ids", async () => {
    const base = await baseUrl();
    expect((await fetch(`${base}/api/animations/anim_character_defender_move_blue/frames/999`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/anim_unknown_nothing/frames/0`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/..%2F..%2Fsettings/frames/0`)).status).toBe(404);
  });

  it("rejects non-GET requests", async () => {
    const response = await fetch(`${await baseUrl()}/api/animations`, { method: "POST" });
    expect(response.status).toBe(405);
  });
});
