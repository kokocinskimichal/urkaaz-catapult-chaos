import { describe, expect, it } from "vitest";
import {
  firstAssetKeyForBiome,
  isKnownMapDecoration,
  paletteAssetsForBiome,
  parseAssetIndex,
  relicAssetPath,
  terrainAssetPath,
  terrainThemeAssetPaths,
} from "./assets";

describe("terrain asset index", () => {
  it("parses registered filenames to stable keys", () => {
    const keys = parseAssetIndex(
      "terrain_green_frontier_tree_00.png\nterrain_frost_tree_00.png\n",
    );
    expect(firstAssetKeyForBiome(keys, "green_frontier", "tree"))
      .toBe("terrain_green_frontier_tree_00");
    expect(firstAssetKeyForBiome(keys, "frostbound", "tree"))
      .toBe("terrain_frost_tree_00");
    expect(terrainAssetPath("frostbound", "terrain_frost_tree_00"))
      .toBe("/api/assets/terrain/frost/terrain_frost_tree_00.png");
  });

  it("rejects malformed index entries and assets from another biome", () => {
    expect(() => parseAssetIndex("tree.default")).toThrow("Unsupported terrain asset index entry");
    const keys = parseAssetIndex("terrain_green_frontier_tree_00.png\nterrain_green_frontier_soil_00.png");
    expect(isKnownMapDecoration("terrain_green_frontier_tree_00", "green_frontier", keys)).toBe(true);
    expect(isKnownMapDecoration("terrain_green_frontier_soil_00", "green_frontier", keys)).toBe(false);
    expect(isKnownMapDecoration("terrain_green_frontier_tree_00", "copperwood", keys)).toBe(false);
    expect(isKnownMapDecoration("relic.green_frontier.small.1", "green_frontier", keys)).toBe(false);
  });

  it("resolves each biome's theme assets and shared Sunken Marshes soil", () => {
    expect(terrainThemeAssetPaths("copperwood")).toEqual({
      background: "/api/assets/terrain/copperwood/background_copperwood.png",
      soil: "/api/assets/terrain/copperwood/terrain_copperwood_soil_00.png",
      cap: "/api/assets/terrain/copperwood/terrain_copperwood_cap_00.png",
    });
    expect(terrainThemeAssetPaths("frostbound")).toEqual({
      background: "/api/assets/terrain/frostbound/background_frostbound_pass.png",
      soil: "/api/assets/terrain/frostbound/terrain_frostbound_soil_00.png",
      cap: "/api/assets/terrain/frostbound/terrain_frostbound_cap_00.png",
    });
    expect(terrainThemeAssetPaths("sunken_marshes")).toEqual({
      background: "/api/assets/terrain/sunken_marshes/background_sunken_marshes.png",
      soil: "/api/assets/terrain/green_frontier/terrain_green_frontier_soil_00.png",
      cap: "/api/assets/terrain/green_frontier/terrain_green_frontier_cap_00.png",
    });
    const keys = parseAssetIndex("terrain_green_frontier_tree_00.png");
    expect(firstAssetKeyForBiome(keys, "sunken_marshes", "tree"))
      .toBe("terrain_green_frontier_tree_00");
  });

  it("builds image palettes and maps relic keys to their biome assets", () => {
    const keys = parseAssetIndex([
      "terrain_green_frontier_tree_00.png",
      "terrain_green_frontier_rock_00.png",
      "terrain_green_frontier_prop_00.png",
      "terrain_green_frontier_relic_01_cracked_watering_can.png",
      "terrain_copperwood_detail_01_copper_coin.png",
      "relic_medium_broken_orc_statue.png",
      "relic_medium_war_drum.png",
    ].join("\n"));
    const palette = paletteAssetsForBiome(keys, "green_frontier");
    expect(palette.tree[0].thumbnailPath)
      .toBe("/api/assets/terrain/green_frontier/terrain_green_frontier_tree_00.png");
    expect(palette.rock).toHaveLength(1);
    expect(palette.prop).toHaveLength(1);
    expect(palette.relic[0]).toMatchObject({
      assetKey: "relic.green_frontier.small.1",
      relicSize: "SMALL",
      thumbnailPath: "/api/assets/terrain/green_frontier/terrain_green_frontier_relic_01_cracked_watering_can.png",
    });
    expect(relicAssetPath("green_frontier", "relic.green_frontier.small.1", keys))
      .toBe(palette.relic[0].thumbnailPath);
    expect(relicAssetPath("copperwood", "relic.copperwood.small.1", keys))
      .toBe("/api/assets/terrain/copperwood/terrain_copperwood_detail_01_copper_coin.png");
    expect(palette.relic).toContainEqual({
      assetKey: "relic.shared.medium.broken_orc_statue",
      category: "relic",
      label: "broken orc statue",
      thumbnailPath: "/api/assets/terrain/shared/relic_medium_broken_orc_statue.png",
      relicSize: "MEDIUM",
    });
    expect(relicAssetPath("green_frontier", "relic.shared.medium.broken_orc_statue", keys))
      .toBe("/api/assets/terrain/shared/relic_medium_broken_orc_statue.png");
    expect(relicAssetPath("copperwood", "relic.shared.medium.war_drum", keys))
      .toBe("/api/assets/terrain/shared/relic_medium_war_drum.png");
    expect(isKnownMapDecoration("relic.shared.medium.broken_orc_statue", "frostbound", keys)).toBe(true);
    expect(isKnownMapDecoration("relic.shared.medium.unknown", "frostbound", keys)).toBe(false);
  });

  it("includes every registered decoration variant in the biome palette", () => {
    const keys = parseAssetIndex([
      ...Array.from({ length: 6 }, (_, index) =>
        `terrain_green_frontier_tree_${String(index).padStart(2, "0")}.png`),
      ...Array.from({ length: 6 }, (_, index) =>
        `terrain_green_frontier_rock_${String(index).padStart(2, "0")}.png`),
      ...Array.from({ length: 6 }, (_, index) =>
        `terrain_green_frontier_prop_${String(index).padStart(2, "0")}.png`),
    ].join("\n"));

    const palette = paletteAssetsForBiome(keys, "sunken_marshes");
    expect(palette.tree).toHaveLength(6);
    expect(palette.rock).toHaveLength(6);
    expect(palette.prop).toHaveLength(6);
    expect(palette.tree[5].assetKey).toBe("terrain_green_frontier_tree_05");
  });
});
