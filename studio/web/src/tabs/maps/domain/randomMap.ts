import { paletteAssetsForBiome } from "./assets.ts";
import {
  DEFAULT_TERRAIN_SAMPLE_STEP,
  MAX_TERRAIN_DELTA,
  MAP_BIOMES,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  catapultsForFortresses,
  maximumTerrainHeightInRange,
  sampleXs,
  terrainHeightAt,
} from "./map.ts";
import type { MapDocumentJson, ObjectJson, ProtectedZoneJson } from "./map.ts";

export const DECORATION_DENSITIES = ["LOW", "MEDIUM", "HIGH"] as const;
export type DecorationDensity = typeof DECORATION_DENSITIES[number];

export const MIN_TERRAIN_AMPLITUDE = 100;
export const MAX_TERRAIN_AMPLITUDE = 600;
export const DEFAULT_TERRAIN_AMPLITUDE = 160;
export const DEFAULT_DECORATION_DENSITY: DecorationDensity = "MEDIUM";
export const RANDOM_MAP_GENERATOR_VERSION = "studio-random-map/1";

export const DECORATION_PRESETS: Record<DecorationDensity, { minimum: number; maximum: number }> = {
  LOW: { minimum: 3, maximum: 6 },
  MEDIUM: { minimum: 7, maximum: 11 },
  HIGH: { minimum: 12, maximum: 18 },
};

const MAX_FOUNDATION_DIFFERENCE = 520;
const MAX_TERRAIN_DELTA_PER_WORLD_UNIT = MAX_TERRAIN_DELTA / DEFAULT_TERRAIN_SAMPLE_STEP;
const FOUNDATION_VARIATION = MAX_FOUNDATION_DIFFERENCE / 2;
const NOISE_KNOT_SPACING = 800;
const CORRIDOR_START = WORLD_WIDTH / 3;
const CORRIDOR_END = WORLD_WIDTH * 2 / 3;
const CORRIDOR_FADE = 400;
const CORRIDOR_MAX_DEVIATION = 40;
const DECORATION_MINIMUM_SPACING = 180;
const DECORATION_SLOT_SPACING = 220;
const DECORATION_SLOT_JITTER = 20;
const RELIC_FOOTPRINT_MARGIN = 0.01;

interface NoiseKnot {
  x: number;
  value: number;
}

interface RelicFootprint {
  left: number;
  right: number;
  top: number;
}

interface XRange {
  minimum: number;
  maximum: number;
}

function createRandom(seed: number): () => number {
  if (!Number.isSafeInteger(seed) || seed < 0 || seed > 0xffff_ffff) {
    throw new Error("Random Map seed must be an unsigned 32-bit integer");
  }

  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 0x1_0000_0000;
  };
}

function randomBetween(random: () => number, minimum: number, maximum: number): number {
  return minimum + random() * (maximum - minimum);
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

function smoothStep(value: number): number {
  const clamped = clamp(value, 0, 1);
  return clamped * clamped * (3 - 2 * clamped);
}

function relicFootprint(object: ObjectJson): RelicFootprint | undefined {
  if (!object.relicSize) return undefined;
  const dimensions = {
    SMALL: { halfWidth: 56, halfHeight: 40 },
    MEDIUM: { halfWidth: 96, halfHeight: 72 },
    LARGE: { halfWidth: 160, halfHeight: 120 },
  }[object.relicSize];
  const radians = object.rotationDegrees * Math.PI / 180;
  const halfWidth =
    Math.abs(dimensions.halfWidth * object.scale * Math.cos(radians)) +
    Math.abs(dimensions.halfHeight * object.scale * Math.sin(radians));
  const halfHeight =
    Math.abs(dimensions.halfWidth * object.scale * Math.sin(radians)) +
    Math.abs(dimensions.halfHeight * object.scale * Math.cos(radians));

  return {
    left: object.x - halfWidth,
    right: object.x + halfWidth,
    top: object.y - halfHeight,
  };
}

function relicFootprints(map: MapDocumentJson): RelicFootprint[] {
  return map.objects.flatMap(object => {
    const footprint = relicFootprint(object);
    return footprint ? [footprint] : [];
  });
}

function maxFoundationHeightForRelics(
  zone: ProtectedZoneJson,
  footprints: RelicFootprint[],
): number {
  return footprints.reduce((maximum, footprint) => {
    const overlapsZone = footprint.right >= zone.minX && footprint.left <= zone.maxX;
    return overlapsZone
      ? Math.min(maximum, footprint.top - RELIC_FOOTPRINT_MARGIN)
      : maximum;
  }, WORLD_HEIGHT);
}

function randomFoundationHeight(
  random: () => number,
  baseHeight: number,
  zone: ProtectedZoneJson,
  footprints: RelicFootprint[],
): number {
  const limitFromRelics = maxFoundationHeightForRelics(zone, footprints);
  const maximum = Math.min(WORLD_HEIGHT, limitFromRelics);
  const candidate = randomBetween(
    random,
    baseHeight - FOUNDATION_VARIATION,
    baseHeight + FOUNDATION_VARIATION,
  );
  return clamp(candidate, 0, maximum);
}

function makeNoiseKnots(
  start: number,
  end: number,
  random: () => number,
): NoiseKnot[] {
  const knots: NoiseKnot[] = [{ x: start, value: 0 }];
  for (let x = start + NOISE_KNOT_SPACING; x < end; x += NOISE_KNOT_SPACING) {
    knots.push({ x, value: randomBetween(random, -1, 1) });
  }
  knots.push({ x: end, value: 0 });
  return knots;
}

function noiseAt(knots: NoiseKnot[], x: number): number {
  if (x <= knots[0].x || x >= knots[knots.length - 1].x) return 0;
  const rightIndex = knots.findIndex(knot => knot.x >= x);
  const left = knots[rightIndex - 1];
  const right = knots[rightIndex];
  const amount = (x - left.x) / (right.x - left.x);
  const eased = smoothStep(amount);
  return left.value + (right.value - left.value) * eased;
}

function corridorScale(x: number, amplitude: number): number {
  const minimumScale = Math.min(1, CORRIDOR_MAX_DEVIATION / amplitude);
  if (x >= CORRIDOR_START && x <= CORRIDOR_END) return minimumScale;
  if (x < CORRIDOR_START && x > CORRIDOR_START - CORRIDOR_FADE) {
    const amount = (CORRIDOR_START - x) / CORRIDOR_FADE;
    return minimumScale + (1 - minimumScale) * smoothStep(amount);
  }
  if (x > CORRIDOR_END && x < CORRIDOR_END + CORRIDOR_FADE) {
    const amount = (x - CORRIDOR_END) / CORRIDOR_FADE;
    return minimumScale + (1 - minimumScale) * smoothStep(amount);
  }
  return 1;
}

function baseHeightAt(
  x: number,
  blueZone: ProtectedZoneJson,
  redZone: ProtectedZoneJson,
  blueHeight: number,
  redHeight: number,
): number {
  if (x <= blueZone.maxX) return blueHeight;
  if (x >= redZone.minX) return redHeight;
  if (redZone.minX <= blueZone.maxX) {
    throw new Error("Foundation zones leave no terrain span between fortresses");
  }
  const amount = (x - blueZone.maxX) / (redZone.minX - blueZone.maxX);
  return blueHeight + (redHeight - blueHeight) * amount;
}

function relicHeightLimit(
  footprints: RelicFootprint[],
  x: number,
  sampleStep: number,
): number {
  return footprints.reduce((limit, footprint) => {
    const left = footprint.left - sampleStep;
    const right = footprint.right + sampleStep;
    const distance = x < left
      ? left - x
      : x > right
        ? x - right
        : 0;
    return Math.min(
      limit,
      footprint.top + distance * MAX_TERRAIN_DELTA_PER_WORLD_UNIT,
    );
  }, WORLD_HEIGHT);
}

function generateTerrain(
  map: MapDocumentJson,
  blueZone: ProtectedZoneJson,
  redZone: ProtectedZoneJson,
  blueHeight: number,
  redHeight: number,
  amplitude: number,
  footprints: RelicFootprint[],
  random: () => number,
) {
  const start = blueZone.maxX;
  const end = redZone.minX;
  if (start >= end) {
    throw new Error("Foundation zones leave no terrain span between fortresses");
  }
  const knots = makeNoiseKnots(start, end, random);
  const samples = sampleXs(map.terrain.sampleStep).map(x => {
    const protectedZone = [blueZone, redZone].find(zone => x >= zone.minX && x <= zone.maxX);
    if (protectedZone) {
      return {
        x,
        height: protectedZone.id === blueZone.id ? blueHeight : redHeight,
      };
    }

    const baseline = baseHeightAt(x, blueZone, redZone, blueHeight, redHeight);
    const variation = noiseAt(knots, x) * amplitude * corridorScale(x, amplitude);
    const height = clamp(
      Math.min(baseline + variation, relicHeightLimit(footprints, x, map.terrain.sampleStep)),
      0,
      WORLD_HEIGHT,
    );
    return { x, height };
  });

  const excessiveDelta = samples.find((sample, index) => {
    if (index === 0) return false;
    return Math.abs(sample.height - samples[index - 1].height) > MAX_TERRAIN_DELTA;
  });
  if (excessiveDelta) {
    throw new Error(
      `Generated terrain exceeds the 200-unit slope limit near x=${excessiveDelta.x}`,
    );
  }
  return samples;
}

function openDecorationRanges(
  map: MapDocumentJson,
  zones: ProtectedZoneJson[],
  footprints: RelicFootprint[],
): XRange[] {
  const excluded = [
    ...zones.map(zone => ({
      minimum: zone.minX - DECORATION_MINIMUM_SPACING / 2,
      maximum: zone.maxX + DECORATION_MINIMUM_SPACING / 2,
    })),
    ...footprints.map(footprint => ({
      minimum: footprint.left - DECORATION_MINIMUM_SPACING / 2,
      maximum: footprint.right + DECORATION_MINIMUM_SPACING / 2,
    })),
  ]
    .map(range => ({
      minimum: clamp(range.minimum, 0, map.world.width),
      maximum: clamp(range.maximum, 0, map.world.width),
    }))
    .sort((left, right) => left.minimum - right.minimum);
  const merged: XRange[] = [];
  for (const range of excluded) {
    const previous = merged.at(-1);
    if (previous && range.minimum <= previous.maximum) {
      previous.maximum = Math.max(previous.maximum, range.maximum);
    } else {
      merged.push(range);
    }
  }

  const ranges: XRange[] = [];
  let cursor = 0;

  for (const range of merged) {
    if (range.minimum - cursor >= DECORATION_MINIMUM_SPACING) {
      ranges.push({ minimum: cursor, maximum: range.minimum });
    }
    cursor = Math.max(cursor, range.maximum);
  }
  if (map.world.width - cursor >= DECORATION_MINIMUM_SPACING) {
    ranges.push({ minimum: cursor, maximum: map.world.width });
  }
  return ranges;
}

function decorationPositions(
  random: () => number,
  ranges: XRange[],
  count: number,
): number[] {
  const positions: number[] = [];
  for (const range of ranges) {
    for (let x = range.minimum + DECORATION_SLOT_SPACING / 2;
      x <= range.maximum - DECORATION_MINIMUM_SPACING / 2;
      x += DECORATION_SLOT_SPACING) {
      const position = x + randomBetween(random, -DECORATION_SLOT_JITTER, DECORATION_SLOT_JITTER);
      if (position >= range.minimum + DECORATION_MINIMUM_SPACING / 2 &&
        position <= range.maximum - DECORATION_MINIMUM_SPACING / 2) {
        positions.push(position);
      }
    }
  }
  for (let index = positions.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(random() * (index + 1));
    [positions[index], positions[swapIndex]] = [positions[swapIndex], positions[index]];
  }
  if (positions.length < count) {
    throw new Error("Not enough valid space to place the selected decoration density");
  }
  return positions.slice(0, count);
}

function generateDecorations(
  map: MapDocumentJson,
  seed: number,
  density: DecorationDensity,
  assetKeys: ReadonlySet<string>,
  footprints: RelicFootprint[],
  random: () => number,
): ObjectJson[] {
  const palette = paletteAssetsForBiome(assetKeys, map.biome);
  const availableAssets = (["tree", "rock", "prop"] as const)
    .flatMap(category => palette[category]);
  if (availableAssets.length === 0) {
    throw new Error(`No tree, rock, or prop assets are available for ${map.biome}`);
  }

  const range = DECORATION_PRESETS[density];
  const count = range.minimum + Math.floor(random() * (range.maximum - range.minimum + 1));
  const xRanges = openDecorationRanges(map, map.protectedTerrainZones, footprints);
  const positions = decorationPositions(random, xRanges, count);
  const decorations: ObjectJson[] = [];
  const existingIds = new Set(
    map.objects.filter(object => object.relicSize != null).map(object => object.id),
  );

  for (let index = 0; index < count; index += 1) {
    const x = positions[index];
    const terrainHeight = terrainHeightAt(map, x);
    if (terrainHeight === undefined) throw new Error("Generated terrain has no height at a decoration");

    const asset = availableAssets[Math.floor(random() * availableAssets.length)];
    let id = `random-map-${seed}-${index}`;
    while (existingIds.has(id)) id = `${id}-next`;
    existingIds.add(id);
    decorations.push({
      id,
      type: "DECORATION",
      x,
      y: terrainHeight,
      scale: 1,
      rotationDegrees: 0,
      assetKey: asset.assetKey,
      collision: "NONE",
      allowOverlap: false,
    });
  }
  return decorations;
}

export function generateRandomMap(
  source: MapDocumentJson,
  seed: number,
  terrainAmplitude: number,
  decorationDensity: DecorationDensity,
  assetKeys: ReadonlySet<string>,
): MapDocumentJson {
  if (!Number.isInteger(terrainAmplitude) ||
    terrainAmplitude < MIN_TERRAIN_AMPLITUDE ||
    terrainAmplitude > MAX_TERRAIN_AMPLITUDE) {
    throw new Error(
      `Terrain intensity must be an integer from ${MIN_TERRAIN_AMPLITUDE} to ${MAX_TERRAIN_AMPLITUDE} world units`,
    );
  }
  if (!DECORATION_DENSITIES.includes(decorationDensity)) {
    throw new Error(`Unsupported decoration density preset: ${String(decorationDensity)}`);
  }
  if (!MAP_BIOMES.some(biome => biome.value === source.biome)) {
    throw new Error(`Unsupported map biome: ${String(source.biome)}`);
  }
  if (source.world.width !== WORLD_WIDTH || source.world.height !== WORLD_HEIGHT) {
    throw new Error("Random Map requires a 7200 × 4800 world");
  }
  if (source.terrain.sampleStep !== 20 && source.terrain.sampleStep !== DEFAULT_TERRAIN_SAMPLE_STEP) {
    throw new Error("Random Map supports terrain sample steps of 20 or 40");
  }
  if (!assetKeys) throw new Error("Terrain asset catalogue is not loaded");

  const random = createRandom(seed);
  const blueFortress = source.fortresses.find(fortress => fortress.owner === "BLUE");
  const redFortress = source.fortresses.find(fortress => fortress.owner === "RED");
  if (!blueFortress || !redFortress) {
    throw new Error("Random Map requires one BLUE and one RED fortress");
  }
  const blueZone = source.protectedTerrainZones.find(zone => zone.id === blueFortress.protectedZoneId);
  const redZone = source.protectedTerrainZones.find(zone => zone.id === redFortress.protectedZoneId);
  if (!blueZone || !redZone) {
    throw new Error("Random Map requires a protected foundation zone for each fortress");
  }

  const footprints = relicFootprints(source);
  const averageFoundation = (blueFortress.foundationHeight + redFortress.foundationHeight) / 2;
  let blueHeight = randomFoundationHeight(random, averageFoundation, blueZone, footprints);
  let redHeight = randomFoundationHeight(random, averageFoundation, redZone, footprints);
  if (Math.abs(blueHeight - redHeight) > MAX_FOUNDATION_DIFFERENCE) {
    if (blueHeight > redHeight) blueHeight = redHeight + MAX_FOUNDATION_DIFFERENCE;
    else redHeight = blueHeight + MAX_FOUNDATION_DIFFERENCE;
  }

  const fortresses = source.fortresses.map(fortress => ({
    ...fortress,
    foundationHeight: fortress.owner === "BLUE" ? blueHeight : redHeight,
  }));
  const protectedTerrainZones = source.protectedTerrainZones.map(zone => {
    if (zone.id === blueZone.id) {
      return { ...zone, minHeight: blueHeight, maxHeight: blueHeight };
    }
    if (zone.id === redZone.id) {
      return { ...zone, minHeight: redHeight, maxHeight: redHeight };
    }
    return { ...zone };
  });
  const preservedRelics = source.objects
    .filter(object => object.relicSize != null)
    .map(object => ({ ...object }));
  const generatedMap: MapDocumentJson = {
    ...source,
    terrain: {
      ...source.terrain,
      samples: generateTerrain(
        source,
        blueZone,
        redZone,
        blueHeight,
        redHeight,
        terrainAmplitude,
        footprints,
        random,
      ),
    },
    protectedTerrainZones,
    fortresses,
    catapults: catapultsForFortresses(fortresses),
    objects: preservedRelics,
    generationMetadata: {
      ...source.generationMetadata,
      seed,
      generatorVersion: RANDOM_MAP_GENERATOR_VERSION,
    },
  };
  const generatedDecorations = generateDecorations(
    generatedMap,
    seed,
    decorationDensity,
    assetKeys,
    footprints,
    random,
  );

  return {
    ...generatedMap,
    objects: [
      ...preservedRelics,
      ...generatedDecorations,
    ],
  };
}
