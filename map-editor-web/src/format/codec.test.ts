import { describe, expect, it } from "vitest";
import { defaultMap } from "../domain/map";
import { decode, encode } from "./codec";

describe("codec", () => {
  it("round trips semantic JSON", () => {
    const text = encode(defaultMap());
    expect(encode(decode(text))).toBe(text);
  });

  it("migrates legacy foundations to the map edges and keeps them flat", () => {
    const legacy = defaultMap();
    legacy.generationMetadata.authoringVersion = "map-editor-web/0.1";
    legacy.protectedTerrainZones[0].minX = 300;
    legacy.protectedTerrainZones[0].maxX = 800;
    legacy.protectedTerrainZones[1].minX = 6400;
    legacy.protectedTerrainZones[1].maxX = 6900;
    legacy.terrain.samples.forEach(sample => {
      if (sample.x < 300 || sample.x > 6900) sample.height = 3500;
    });

    const migrated = decode(encode(legacy));
    expect(migrated.protectedTerrainZones[0].minX).toBe(0);
    expect(migrated.protectedTerrainZones[0].maxX).toBe(1080);
    expect(migrated.protectedTerrainZones[1].minX).toBe(6120);
    expect(migrated.protectedTerrainZones[1].maxX).toBe(7200);
    expect(migrated.terrain.samples.find(sample => sample.x === 0)?.height).toBe(3180);
    expect(migrated.terrain.samples.find(sample => sample.x === 7200)?.height).toBe(3180);
    expect(migrated.generationMetadata.authoringVersion).toBe("map-editor-web/0.4");
    expect(decode(encode(migrated)).protectedTerrainZones).toEqual(migrated.protectedTerrainZones);
  });

  it("flattens an untouched legacy starter map to fortress height", () => {
    const legacy = defaultMap();
    legacy.generationMetadata.authoringVersion = "map-editor-web/0.3";
    legacy.terrain.samples.forEach(sample => {
      const inFoundation = legacy.protectedTerrainZones.some(
        zone => sample.x >= zone.minX && sample.x <= zone.maxX,
      );
      sample.height = inFoundation ? 3180 : 3300 + 180 * Math.sin(sample.x / 560);
    });

    const migrated = decode(encode(legacy));
    expect(migrated.terrain.samples.every(sample => sample.height === 3180)).toBe(true);
    expect(migrated.generationMetadata.authoringVersion).toBe("map-editor-web/0.4");
  });
});
