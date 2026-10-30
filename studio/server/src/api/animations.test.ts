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
    expect(catalog.catalogIssues).toEqual([]);
    expect(catalog.sequences.filter(sequence => sequence.category === "boss")).toHaveLength(8);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_boss_thorncrown_idle"))
      .toMatchObject({
        frameIndices: [0],
        targetConfigured: true,
        hasRuntimeLoader: true,
      });
    expect(catalog.sequences.some(sequence => sequence.id === "anim_character_defender_move")).toBe(true);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_demolisher_idle")?.supportsTeamTexture).toBe(true);
    const demolisherAttack = catalog.sequences.find(sequence => sequence.id === "anim_character_demolisher_attack");
    expect(demolisherAttack?.supportsTeamTexture).toBe(true);
    expect(demolisherAttack?.frameIndices).toHaveLength(97);
    expect(catalog.sequences.some(sequence => sequence.id === "anim_character_demolisher_death")).toBe(false);
    const demolisherExplosion = catalog.sequences.find(sequence => sequence.id === "anim_ammunition_demolisher_explosion");
    expect(demolisherExplosion).toMatchObject({
      category: "ammunition",
      target: "demolisher",
      type: "explosion",
      targetConfigured: true,
      supportsTeamTexture: false,
    });
    expect(demolisherExplosion?.frameIndices).toHaveLength(20);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_sapper_idle")?.supportsTeamTexture).toBe(true);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_sapper_move")?.supportsTeamTexture).toBe(true);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_sapper_attack")?.supportsTeamTexture).toBe(true);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_sapper_turn")?.supportsTeamTexture).toBe(true);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_sapper_return")?.supportsTeamTexture).toBe(true);
  });

  it("serves a frame as PNG", async () => {
    const response = await fetch(`${await baseUrl()}/api/animations/anim_character_defender_move/frames/0`);
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/png");
    const bytes = new Uint8Array(await response.arrayBuffer());
    expect([...bytes.slice(1, 4)]).toEqual([0x50, 0x4e, 0x47]);

    const explosion = await fetch(`${await baseUrl()}/api/animations/anim_ammunition_demolisher_explosion/frames/0`);
    expect(explosion.status).toBe(200);
    expect(explosion.headers.get("content-type")).toBe("image/png");
  });

  it("returns 404 for missing frames and unsafe ids", async () => {
    const base = await baseUrl();
    expect((await fetch(`${base}/api/animations/anim_character_defender_move/frames/999`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/anim_unknown_nothing/frames/0`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/..%2F..%2Fsettings/frames/0`)).status).toBe(404);
  });

  it("serves masks and team textures for each configured team-texture unit", async () => {
    const base = await baseUrl();
    for (const path of [
      "/api/animations/anim_character_defender_idle/masks/0",
      "/api/animations/anim_character_demolisher_idle/masks/0",
      "/api/animations/anim_character_demolisher_move/masks/0",
      "/api/animations/anim_character_demolisher_attack/masks/0",
      "/api/animations/anim_character_sapper_idle/masks/0",
      "/api/animations/anim_character_sapper_move/masks/0",
      "/api/animations/anim_character_sapper_attack/masks/0",
      "/api/animations/anim_character_sapper_turn/masks/0",
      "/api/animations/anim_character_sapper_return/masks/0",
      "/api/animations/team-textures/defender/blue",
      "/api/animations/team-textures/defender/red",
      "/api/animations/team-textures/demolisher/blue",
      "/api/animations/team-textures/demolisher/red",
      "/api/animations/team-textures/sapper/blue",
      "/api/animations/team-textures/sapper/red",
    ]) {
      const response = await fetch(`${base}${path}`);
      expect(response.status).toBe(200);
      expect(response.headers.get("content-type")).toBe("image/png");
    }
    expect(
      (await fetch(`${base}/api/animations/anim_character_demolisher_death/masks/0`)).status,
    ).toBe(404);
    expect((await fetch(`${base}/api/animations/anim_character_defender_idle/masks/999`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/team-textures/demolisher/green`)).status).toBe(404);
    expect((await fetch(`${base}/api/animations/anim_character_demolisher_idle/../../settings/masks/0`)).status).toBe(404);
  });

  it("rejects non-GET requests", async () => {
    const response = await fetch(`${await baseUrl()}/api/animations`, { method: "POST" });
    expect(response.status).toBe(405);
  });
});
