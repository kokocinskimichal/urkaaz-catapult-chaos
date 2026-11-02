import { readFileSync, readdirSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseAssetIndex } from "./assets.ts";
import { defaultMap } from "./map.ts";
import { validate } from "./operations.ts";
import { decode } from "../format/codec.ts";
import {
  DECORATION_PRESETS,
  DECORATION_DENSITIES,
  DEFAULT_TERRAIN_AMPLITUDE,
  generateRandomMap,
  MAX_TERRAIN_AMPLITUDE,
  MIN_TERRAIN_AMPLITUDE,
  RANDOM_MAP_GENERATOR_VERSION,
} from "./randomMap.ts";

const assetKeys = parseAssetIndex([
  "terrain_green_frontier_tree_00.png",
  "terrain_green_frontier_tree_01.png",
  "terrain_green_frontier_rock_00.png",
  "terrain_green_frontier_prop_00.png",
  "terrain_green_frontier_relic_01_cracked_watering_can.png",
  "terrain_copperwood_tree_00.png",
  "terrain_copperwood_rock_00.png",
  "terrain_copperwood_prop_00.png",
].join("\n"));
const referenceMapUrl = new URL(
  "../../../../../../game-campaign/src/main/resources/maps/chapter1-level4.json",
  import.meta.url,
);
const referenceMapDirectoryUrl = new URL(
  "../../../../../../game-campaign/src/main/resources/maps/",
  import.meta.url,
);
const terrainIndexUrl = new URL(
  "../../../../../../game-assets/src/main/resources/terrain/asset-index.txt",
  import.meta.url,
);
const drawableDirectoryUrl = new URL(
  "../../../../../../android-app/src/main/res/drawable-nodpi/",
  import.meta.url,
);
const referenceMap = decode(readFileSync(referenceMapUrl, "utf8"));
const referenceMaps = readdirSync(referenceMapDirectoryUrl)
  .filter(name => name.endsWith(".json"))
  .map(name => decode(readFileSync(new URL(name, referenceMapDirectoryUrl), "utf8")));
const referenceAssetKeys = parseAssetIndex([
  readFileSync(terrainIndexUrl, "utf8"),
  ...readdirSync(drawableDirectoryUrl).filter(name =>
    /^terrain_(green_frontier|frost|copperwood|ashen_march)_(?:relic|detail)_\d{2}_[^.]+\.png$/.test(name) ||
    /^relic_medium_[a-z0-9_]+\.png$/.test(name),
  ),
].join("\n"));

describe("Random Map generator", () => {
  it("reproduces the same complete map for the same seed and settings", () => {
    const source = defaultMap("chapter1-level4");
    const first = generateRandomMap(source, 42, DEFAULT_TERRAIN_AMPLITUDE, "LOW", assetKeys);
    const second = generateRandomMap(source, 42, DEFAULT_TERRAIN_AMPLITUDE, "LOW", assetKeys);

    expect(first).toEqual(second);
    expect(first.generationMetadata).toMatchObject({
      seed: 42,
      generatorVersion: RANDOM_MAP_GENERATOR_VERSION,
      authoringVersion: source.generationMetadata.authoringVersion,
    });
    expect(source.terrain.samples.every(sample => sample.height === 3180)).toBe(true);
    expect(source.objects).toEqual([]);
  });

  it("changes generated map content when the seed changes", () => {
    const source = defaultMap();
    const first = generateRandomMap(source, 100, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys);
    const second = generateRandomMap(source, 101, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys);

    expect(first.terrain.samples).not.toEqual(second.terrain.samples);
    expect(first.objects).not.toEqual(second.objects);
  });

  it("keeps the selected map identity, biome, camera, sample step, and world", () => {
    const source = defaultMap("chapter3-level25", "copperwood");
    source.initialCameraAnchor = "CENTER";
    source.initialCameraZoom = 1.5;
    const generated = generateRandomMap(source, 200, MIN_TERRAIN_AMPLITUDE, "LOW", assetKeys);

    expect(generated).toMatchObject({
      mapId: "chapter3-level25",
      biome: "copperwood",
      initialCameraAnchor: "CENTER",
      initialCameraZoom: 1.5,
      world: { width: 7200, height: 4800 },
      terrain: { sampleStep: source.terrain.sampleStep },
    });
    expect(generated.terrain.samples[0].x).toBe(0);
    expect(generated.terrain.samples.at(-1)?.x).toBe(7200);
  });

  it("uses a bounded continuous terrain amplitude and bounded decoration density presets", () => {
    expect(MIN_TERRAIN_AMPLITUDE).toBe(100);
    expect(DEFAULT_TERRAIN_AMPLITUDE).toBe(160);
    expect(MAX_TERRAIN_AMPLITUDE).toBe(600);
    expect(DECORATION_DENSITIES).toEqual(["LOW", "MEDIUM", "HIGH"]);
    expect(DECORATION_PRESETS.LOW.maximum).toBeLessThan(DECORATION_PRESETS.MEDIUM.minimum);
    expect(DECORATION_PRESETS.MEDIUM.maximum).toBeLessThan(DECORATION_PRESETS.HIGH.minimum);

    const source = defaultMap();
    const minimum = generateRandomMap(source, 300, MIN_TERRAIN_AMPLITUDE, "LOW", assetKeys);
    const initial = generateRandomMap(source, 300, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys);
    const maximum = generateRandomMap(source, 300, MAX_TERRAIN_AMPLITUDE, "HIGH", assetKeys);
    const maximumDeviation = (map: typeof minimum) => {
      const blue = map.fortresses.find(fortress => fortress.owner === "BLUE")!;
      const red = map.fortresses.find(fortress => fortress.owner === "RED")!;
      const blueZone = map.protectedTerrainZones.find(zone => zone.id === blue.protectedZoneId)!;
      const redZone = map.protectedTerrainZones.find(zone => zone.id === red.protectedZoneId)!;
      return Math.max(...map.terrain.samples
        .filter(sample => sample.x >= blueZone.maxX && sample.x <= redZone.minX)
        .map(sample => {
          const ratio = (sample.x - blueZone.maxX) / (redZone.minX - blueZone.maxX);
          const baseline = blue.foundationHeight + (red.foundationHeight - blue.foundationHeight) * ratio;
          return Math.abs(sample.height - baseline);
        }));
    };

    expect(maximumDeviation(minimum)).toBeLessThanOrEqual(maximumDeviation(initial));
    expect(maximumDeviation(initial)).toBeLessThanOrEqual(maximumDeviation(maximum));
    expect(minimum.objects.length).toBeGreaterThanOrEqual(DECORATION_PRESETS.LOW.minimum);
    expect(minimum.objects.length).toBeLessThanOrEqual(DECORATION_PRESETS.LOW.maximum);
    expect(initial.objects.length).toBeGreaterThanOrEqual(DECORATION_PRESETS.MEDIUM.minimum);
    expect(initial.objects.length).toBeLessThanOrEqual(DECORATION_PRESETS.MEDIUM.maximum);
    expect(maximum.objects.length).toBeGreaterThanOrEqual(DECORATION_PRESETS.HIGH.minimum);
    expect(maximum.objects.length).toBeLessThanOrEqual(DECORATION_PRESETS.HIGH.maximum);
  });

  it("generates valid terrain, synchronized foundations, and biome-local decorations", () => {
    const generated = generateRandomMap(defaultMap(), 400, MAX_TERRAIN_AMPLITUDE, "HIGH", assetKeys);
    const document = { map: generated, revision: 1 };
    const heights = generated.terrain.samples.map(sample => sample.height);
    const foundationHeights = generated.fortresses.map(fortress => fortress.foundationHeight);

    expect(Math.abs(foundationHeights[0] - foundationHeights[1])).toBeLessThanOrEqual(520);
    expect(generated.catapults.map(catapult => catapult.foundationHeight)).toEqual(foundationHeights);
    expect(generated.terrain.samples.every(sample => sample.height >= 0 && sample.height <= 4800)).toBe(true);
    expect(generated.terrain.samples.slice(1).every((sample, index) =>
      Math.abs(sample.height - heights[index]) <= 200,
    )).toBe(true);
    expect(generated.terrain.samples.every(sample =>
      generated.protectedTerrainZones.every(zone =>
        sample.x < zone.minX || sample.x > zone.maxX || sample.height === zone.minHeight,
      ),
    )).toBe(true);
    expect(generated.objects.every(object =>
      object.assetKey.startsWith("terrain_green_frontier_tree_") ||
      object.assetKey.startsWith("terrain_green_frontier_rock_") ||
      object.assetKey.startsWith("terrain_green_frontier_prop_"),
    )).toBe(true);
    expect(validate(document, assetKeys)).toEqual([]);
  });

  it("preserves existing Relics exactly and keeps their soil footprints valid", () => {
    const source = defaultMap();
    const relic = {
      id: "kept-relic",
      type: "DECORATION" as const,
      x: 3600,
      y: 3300,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "relic.green_frontier.small.1",
      collision: "NONE" as const,
      allowOverlap: false,
      relicSize: "SMALL" as const,
    };
    source.objects.push(relic);

    const generated = generateRandomMap(source, 500, MAX_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys);

    expect(generated.objects.find(object => object.id === relic.id)).toEqual(relic);
    expect(generated.objects.filter(object => object.relicSize == null)).toHaveLength(
      generated.objects.length - 1,
    );
    expect(validate({ map: generated, revision: 1 }, assetKeys)).toEqual([]);
    expect(source.objects).toEqual([relic]);
  });

  it("generates valid maps at the slider bounds and default across every map fixture", () => {
    const amplitudes = [MIN_TERRAIN_AMPLITUDE, DEFAULT_TERRAIN_AMPLITUDE, MAX_TERRAIN_AMPLITUDE];
    for (const source of referenceMaps) {
      for (const terrainAmplitude of amplitudes) {
        for (const decorationDensity of DECORATION_DENSITIES) {
          const generated = generateRandomMap(
            source,
            700,
            terrainAmplitude,
            decorationDensity,
            referenceAssetKeys,
          );
          expect(generated.objects.filter(object => object.relicSize != null))
            .toEqual(source.objects.filter(object => object.relicSize != null));
          expect(
            validate({ map: generated, revision: 1 }, referenceAssetKeys),
            `${source.mapId}, amplitude ${terrainAmplitude}, density ${decorationDensity}`,
          ).toEqual([]);
        }
      }
    }
  });

  it("supports every integer value in the terrain amplitude slider range", () => {
    for (const source of referenceMaps) {
      for (let amplitude = MIN_TERRAIN_AMPLITUDE; amplitude <= MAX_TERRAIN_AMPLITUDE; amplitude += 1) {
        const generated = generateRandomMap(source, 701, amplitude, "MEDIUM", referenceAssetKeys);
        expect(
          validate({ map: generated, revision: 1 }, referenceAssetKeys),
          `${source.mapId}, amplitude ${amplitude}`,
        ).toEqual([]);
      }
    }
  });

  it("reports missing or unsupported asset catalogues instead of producing a fallback map", () => {
    expect(() => generateRandomMap(defaultMap(), 600, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", new Set()))
      .toThrow("No tree, rock, or prop assets are available for green_frontier");
    expect(() => generateRandomMap(defaultMap(), -1, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys))
      .toThrow("Random Map seed must be an unsigned 32-bit integer");
    expect(() => generateRandomMap({
      ...defaultMap(),
      terrain: { ...defaultMap().terrain, sampleStep: 10 },
    }, 600, DEFAULT_TERRAIN_AMPLITUDE, "MEDIUM", assetKeys))
      .toThrow("Random Map supports terrain sample steps of 20 or 40");
  });

  it("rejects terrain amplitudes outside the slider range or off its one-unit steps", () => {
    for (const amplitude of [99, 601, 100.5, Number.NaN]) {
      expect(() => generateRandomMap(defaultMap(), 800, amplitude, "MEDIUM", assetKeys))
        .toThrow("Terrain intensity must be an integer from 100 to 600 world units");
    }

    expect(() => generateRandomMap(defaultMap(), 801, 100, "MEDIUM", assetKeys)).not.toThrow();
    expect(() => generateRandomMap(defaultMap(), 802, 600, "MEDIUM", assetKeys)).not.toThrow();
  });
});
