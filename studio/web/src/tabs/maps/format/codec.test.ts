import { describe, expect, it } from "vitest";
import { defaultMap } from "../domain/map";
import { decode, encode } from "./codec";

describe("map JSON codec", () => {
  it("round trips only the accepted production schema", () => {
    const text = encode(defaultMap());
    const json = JSON.parse(text) as Record<string, unknown>;
    expect(Object.keys(json)).toEqual([
      "schemaVersion", "mapId", "biome", "initialCameraAnchor", "initialCameraZoom", "world", "terrain",
      "protectedZones", "fortresses", "decorations", "generationMetadata",
    ]);
    expect(json).not.toHaveProperty("catapults");
    expect(json.initialCameraAnchor).toBe("PLAYER_FORTRESS");
    expect(json.initialCameraZoom).toBe(1);
    expect(json.generationMetadata).toMatchObject({
      authoringVersion: "urkaaz-studio/0.1.0",
    });
    expect(json).not.toHaveProperty("objects");
    expect(json).not.toHaveProperty("terrainTiles");
    expect(encode(decode(text))).toBe(text);
  });

  it("imports legacy maps with the existing player-fortress framing defaults", () => {
    const map = JSON.parse(encode(defaultMap())) as Record<string, unknown>;
    delete map.initialCameraAnchor;
    delete map.initialCameraZoom;

    const imported = decode(JSON.stringify(map));

    expect(imported.initialCameraAnchor).toBe("PLAYER_FORTRESS");
    expect(imported.initialCameraZoom).toBe(1);
  });

  it("rejects unknown fields and obsolete map properties", () => {
    const map = JSON.parse(encode(defaultMap())) as Record<string, unknown>;
    expect(() => decode(JSON.stringify({ ...map, catapults: [] }))).toThrow("catapults");
    const terrain = map.terrain as Record<string, unknown>;
    expect(() => decode(JSON.stringify({
      ...map,
      terrain: { ...terrain, tiles: [] },
    }))).toThrow("terrain.tiles");
  });

  it("rejects unsupported enum values and future schemas", () => {
    const map = JSON.parse(encode(defaultMap())) as Record<string, unknown>;
    expect(() => decode(JSON.stringify({ ...map, biome: "unknown_biome" }))).toThrow("map.biome");
    expect(() => decode(JSON.stringify({ ...map, initialCameraAnchor: "UNKNOWN" })))
      .toThrow("initialCameraAnchor");
    expect(() => decode(JSON.stringify({ ...map, initialCameraZoom: 4.1 })))
      .toThrow("initialCameraZoom");
    expect(() => decode(JSON.stringify({ ...map, schemaVersion: 2 }))).toThrow("schemaVersion");
  });
});
