import { describe, expect, it } from "vitest";
import {
  animationFramePath,
  buildAnimationCatalog,
  parseAnimationConfig,
  readAnimationCatalog,
  teamAnimationMaskPath,
  teamTexturePath,
  type AnimationConfig,
} from "./animationCatalog.ts";
import { resolveRepoRoot } from "./repoRoot.ts";

const config: AnimationConfig = parseAnimationConfig({
  targets: {
    character: ["defender", "demolisher", "raging_boar", "slingmaster"],
    spell: ["fortress_shield", "haste"],
    ammunition: ["fire_rain", "rock"],
  },
  teamTextureTargets: ["defender", "demolisher"],
  withoutLoader: ["anim_ammunition_fire_rain_impact"],
});

const frames = (stem: string, count: number, start = 0) =>
  Array.from({ length: count }, (_, i) => `${stem}_${String(start + i).padStart(3, "0")}.png`);

function sequenceOf(stem: string, count = 2) {
  const catalog = buildAnimationCatalog(frames(stem, count), config);
  expect(catalog.invalidFiles).toEqual([]);
  return catalog.sequences[0];
}

describe("buildAnimationCatalog", () => {
  it("parses character sequences with team variants and compound targets", () => {
    expect(sequenceOf("anim_character_raging_boar_attack_red", 3)).toMatchObject({
      id: "anim_character_raging_boar_attack_red",
      category: "character",
      target: "raging_boar",
      type: "attack",
      variant: "red",
      frameIndices: [0, 1, 2],
      targetConfigured: true,
      hasRuntimeLoader: true,
      issues: [],
    });
  });

  it("treats sequences without a team token as neutral", () => {
    expect(sequenceOf("anim_character_slingmaster_move")).toMatchObject({ target: "slingmaster", type: "move", variant: null });
  });

  it("marks configured neutral character sequences for team-texture preview", () => {
    expect(sequenceOf("anim_character_demolisher_idle", 48)).toMatchObject({
      target: "demolisher",
      type: "idle",
      variant: null,
      supportsTeamTexture: true,
    });
    expect(sequenceOf("anim_character_slingmaster_move")).toMatchObject({
      supportsTeamTexture: false,
    });
  });

  it("keeps compound types for spells and ammunition", () => {
    expect(sequenceOf("anim_ammunition_fire_rain_fragment_flight")).toMatchObject({ target: "fire_rain", type: "fragment_flight" });
    expect(sequenceOf("anim_spell_fortress_shield_dome")).toMatchObject({ category: "spell", target: "fortress_shield", type: "dome" });
    expect(sequenceOf("anim_spell_haste_orbiting_chevron")).toMatchObject({ target: "haste", type: "orbiting_chevron" });
  });

  it("prefers the longest configured target", () => {
    const longest = parseAnimationConfig({ targets: { ammunition: ["fire", "fire_rain"] } });
    const catalog = buildAnimationCatalog(frames("anim_ammunition_fire_rain_field", 1), longest);
    expect(catalog.sequences[0]).toMatchObject({ target: "fire_rain", type: "field" });
  });

  it("marks sequences without a runtime loader", () => {
    expect(sequenceOf("anim_ammunition_fire_rain_impact")).toMatchObject({ hasRuntimeLoader: false });
  });

  it("falls back to the last token for unconfigured targets and flags them", () => {
    expect(sequenceOf("anim_character_new_unit_move_blue")).toMatchObject({
      target: "new_unit",
      type: "move",
      variant: "blue",
      targetConfigured: false,
    });
  });

  it("reports non-contiguous frame indices as issues", () => {
    const catalog = buildAnimationCatalog(
      [...frames("anim_character_defender_idle_blue", 2), "anim_character_defender_idle_blue_004.png"],
      config,
    );
    expect(catalog.sequences[0].frameIndices).toEqual([0, 1, 4]);
    expect(catalog.sequences[0].issues).toHaveLength(1);
  });

  it("lists anim files that break the naming convention and ignores other files", () => {
    const catalog = buildAnimationCatalog(
      ["anim_character_defender_move_blue_1.png", "anim_unit_x_move_000.png", "Anim_character_x_000.png", "icon.png", "anim_character_defender_move_blue_000.png"],
      config,
    );
    expect(catalog.invalidFiles).toEqual(["anim_character_defender_move_blue_1.png", "anim_unit_x_move_000.png"]);
    expect(catalog.sequences.map(sequence => sequence.id)).toEqual(["anim_character_defender_move_blue"]);
  });
});

describe("animationFramePath", () => {
  it("builds a padded path inside the drawable directory", () => {
    expect(animationFramePath("/repo", "anim_spell_haste_orbiting_chevron", 7)).toBe(
      "/repo/android-app/src/main/res/drawable-nodpi/anim_spell_haste_orbiting_chevron_007.png",
    );
  });

  it("rejects traversal, unknown categories and invalid indices", () => {
    expect(animationFramePath("/repo", "anim_spell_../../secret", 0)).toBeNull();
    expect(animationFramePath("/repo", "icon_haste", 0)).toBeNull();
    expect(animationFramePath("/repo", "anim_spell_haste", -1)).toBeNull();
    expect(animationFramePath("/repo", "anim_spell_haste", 1000)).toBeNull();
    expect(animationFramePath("/repo", "anim_spell_haste", 1.5)).toBeNull();
  });

  it("builds team mask paths only for supported animation types", () => {
    expect(teamAnimationMaskPath("/repo", "anim_character_demolisher_idle", 7)).toBe(
      "/repo/android-app/src/main/assets/animations/demolisher/masks/idle/007.png",
    );
    expect(teamAnimationMaskPath("/repo", "anim_character_demolisher_move", 7)).toBe(
      "/repo/android-app/src/main/assets/animations/demolisher/masks/move/007.png",
    );
    expect(teamAnimationMaskPath("/repo", "anim_character_../../secret_idle", 0)).toBeNull();
    expect(teamAnimationMaskPath("/repo", "anim_character_demolisher_idle", 1000)).toBeNull();
  });
});

describe("teamTexturePath", () => {
  it("loads configured textures for each team and rejects invalid inputs", async () => {
    const root = await resolveRepoRoot();
    expect(await teamTexturePath(root, "defender", "blue")).toContain(
      "/android-app/src/main/assets/animations/defender/",
    );
    expect(await teamTexturePath(root, "demolisher", "red")).toContain(
      "/android-app/src/main/assets/animations/defender/",
    );
    expect(await teamTexturePath(root, "../secret", "blue")).toBeNull();
    expect(await teamTexturePath(root, "demolisher", "green")).toBeNull();
  });
});

describe("repository animation catalog", () => {
  it("follows the naming convention and has no unconfigured targets", async () => {
    const catalog = await readAnimationCatalog(await resolveRepoRoot());
    expect(catalog.sequences.length).toBeGreaterThan(0);
    expect(catalog.invalidFiles).toEqual([]);
    expect(catalog.sequences.filter(sequence => sequence.issues.length > 0).map(sequence => sequence.id)).toEqual([]);
    expect(catalog.sequences.filter(sequence => !sequence.targetConfigured).map(sequence => sequence.id)).toEqual([]);
    expect(catalog.sequences.find(sequence => sequence.id === "anim_character_demolisher_idle")).toMatchObject({
      supportsTeamTexture: true,
      variant: null,
      frameIndices: Array.from({ length: 48 }, (_, index) => index),
    });
  });
});
