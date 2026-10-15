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
      .toBe("/assets/terrain/frost/terrain_frost_tree_00.png");
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
      background: "/assets/terrain/copperwood/background_copperwood.png",
      soil: "/assets/terrain/copperwood/terrain_copperwood_soil_00.png",
      cap: "/assets/terrain/copperwood/terrain_copperwood_cap_00.png",
    });
    expect(terrainThemeAssetPaths("frostbound")).toEqual({
      background: "/assets/terrain/frostbound/background_frostbound_pass.png",
      soil: "/assets/terrain/frostbound/terrain_frostbound_soil_00.png",
      cap: "/assets/terrain/frostbound/terrain_frostbound_cap_00.png",
    });
    expect(terrainThemeAssetPaths("sunken_marshes")).toEqual({
      background: "/assets/terrain/sunken_marshes/background_sunken_marshes.png",
      soil: "/assets/terrain/green_frontier/terrain_green_frontier_soil_00.png",
      cap: "/assets/terrain/green_frontier/terrain_green_frontier_cap_00.png",
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
    ].join("\n"));
    const palette = paletteAssetsForBiome(keys, "green_frontier");
    expect(palette.tree[0].thumbnailPath)
      .toBe("/assets/terrain/green_frontier/terrain_green_frontier_tree_00.png");
    expect(palette.rock).toHaveLength(1);
    expect(palette.prop).toHaveLength(1);
    expect(palette.relic[0]).toMatchObject({
      assetKey: "relic.green_frontier.small.1",
      relicSize: "SMALL",
      thumbnailPath: "/assets/terrain/green_frontier/terrain_green_frontier_relic_01_cracked_watering_can.png",
    });
    expect(relicAssetPath("green_frontier", "relic.green_frontier.small.1", keys))
      .toBe(palette.relic[0].thumbnailPath);
    expect(relicAssetPath("copperwood", "relic.copperwood.small.1", keys))
      .toBe("/assets/terrain/copperwood/terrain_copperwood_detail_01_copper_coin.png");
  });
});
