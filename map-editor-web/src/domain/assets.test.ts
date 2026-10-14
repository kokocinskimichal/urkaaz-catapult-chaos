import { describe, expect, it } from "vitest";
import {
  firstAssetKeyForBiome,
  isKnownMapDecoration,
  parseAssetIndex,
  terrainAssetPath,
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
    const keys = parseAssetIndex("terrain_green_frontier_tree_00.png");
    expect(isKnownMapDecoration("terrain_green_frontier_tree_00", "green_frontier", keys)).toBe(true);
    expect(isKnownMapDecoration("terrain_green_frontier_tree_00", "copperwood", keys)).toBe(false);
  });
});
