import {
  EditorDocument,
  MapDocumentJson,
  Team,
  isMapBiome,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  catapultsForFortresses,
  defaultMap,
} from "../domain/map";

export const CURRENT_SCHEMA_VERSION = 1;

type JsonObject = Record<string, unknown>;

function object(value: unknown, path: string): JsonObject {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
  return value as JsonObject;
}

function keys(value: JsonObject, allowed: string[], required: string[], path: string): void {
  for (const key of Object.keys(value)) {
    if (!allowed.includes(key)) throw new Error(`${path}.${key} is not supported`);
  }
  for (const key of required) {
    if (!(key in value)) throw new Error(`${path}.${key} is required`);
  }
}

function number(value: unknown, path: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error(`${path} must be a finite number`);
  return value;
}

function string(value: unknown, path: string): string {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${path} must be a non-empty string`);
  return value;
}

export function normalizeMap(input: unknown): MapDocumentJson {
  const raw = object(input, "map");
  keys(
    raw,
    ["schemaVersion", "mapId", "biome", "initialCameraAnchor", "initialCameraZoom", "world", "terrain", "protectedZones", "fortresses", "decorations", "generationMetadata"],
    ["schemaVersion", "mapId", "biome", "world", "terrain", "protectedZones", "fortresses", "decorations"],
    "map",
  );
  if (raw.schemaVersion !== CURRENT_SCHEMA_VERSION) {
    throw new Error(`Unsupported schemaVersion: ${String(raw.schemaVersion)}`);
  }
  const mapId = string(raw.mapId, "map.mapId");
  const biome = string(raw.biome, "map.biome");
  if (!isMapBiome(biome)) {
    throw new Error(`map.biome is unsupported: ${biome}`);
  }
  const initialCameraAnchor = raw.initialCameraAnchor ?? "PLAYER_FORTRESS";
  if (initialCameraAnchor !== "PLAYER_FORTRESS" &&
    initialCameraAnchor !== "ENEMY_FORTRESS" &&
    initialCameraAnchor !== "CENTER") {
    throw new Error("map.initialCameraAnchor is unsupported");
  }
  const initialCameraZoom = raw.initialCameraZoom === undefined
    ? 1
    : number(raw.initialCameraZoom, "map.initialCameraZoom");
  if (initialCameraZoom < 1 || initialCameraZoom > 4) {
    throw new Error("map.initialCameraZoom must be between 1 and 4");
  }
  const world = object(raw.world, "world");
  keys(world, ["width", "height"], ["width", "height"], "world");
  const worldWidth = number(world.width, "world.width");
  const worldHeight = number(world.height, "world.height");
  if (worldWidth !== WORLD_WIDTH || worldHeight !== WORLD_HEIGHT) {
    throw new Error(`World must be ${WORLD_WIDTH} × ${WORLD_HEIGHT}`);
  }

  const terrain = object(raw.terrain, "terrain");
  keys(terrain, ["sampleStep", "heightSamples"], ["sampleStep", "heightSamples"], "terrain");
  if (!Array.isArray(terrain.heightSamples)) throw new Error("terrain.heightSamples must be an array");
  const sampleStep = number(terrain.sampleStep, "terrain.sampleStep");
  const samples = terrain.heightSamples.map((sample, index) => {
    const item = object(sample, `terrain.heightSamples[${index}]`);
    keys(item, ["x", "height"], ["x", "height"], `terrain.heightSamples[${index}]`);
    return {
      x: number(item.x, `terrain.heightSamples[${index}].x`),
      height: number(item.height, `terrain.heightSamples[${index}].height`),
    };
  });

  if (!Array.isArray(raw.protectedZones) || !Array.isArray(raw.fortresses) || !Array.isArray(raw.decorations)) {
    throw new Error("protectedZones, fortresses, and decorations must be arrays");
  }
  const protectedTerrainZones = raw.protectedZones.map((entry, index) => {
    const path = `protectedZones[${index}]`;
    const zone = object(entry, path);
    keys(zone, ["id", "minX", "maxX", "minHeight", "maxHeight", "craterPolicy"],
      ["id", "minX", "maxX", "minHeight", "maxHeight", "craterPolicy"], path);
    if (zone.craterPolicy !== "PRESERVE_FOUNDATION") throw new Error(`${path}.craterPolicy is unsupported`);
    return {
      id: string(zone.id, `${path}.id`),
      minX: number(zone.minX, `${path}.minX`),
      maxX: number(zone.maxX, `${path}.maxX`),
      minHeight: number(zone.minHeight, `${path}.minHeight`),
      maxHeight: number(zone.maxHeight, `${path}.maxHeight`),
      craterPolicy: "PRESERVE_FOUNDATION" as const,
    };
  });
  const fortresses = raw.fortresses.map((entry, index) => {
    const path = `fortresses[${index}]`;
    const fortress = object(entry, path);
    keys(fortress, ["owner", "x", "foundationHeight", "halfWidth", "assetKey", "hitboxProfile", "protectedZoneId"],
      ["owner", "x", "foundationHeight", "halfWidth", "assetKey", "hitboxProfile", "protectedZoneId"], path);
    if (fortress.owner !== "BLUE" && fortress.owner !== "RED") throw new Error(`${path}.owner is unsupported`);
    const owner = fortress.owner as Team;
    return {
      owner,
      x: number(fortress.x, `${path}.x`),
      foundationHeight: number(fortress.foundationHeight, `${path}.foundationHeight`),
      halfWidth: number(fortress.halfWidth, `${path}.halfWidth`),
      assetKey: string(fortress.assetKey, `${path}.assetKey`),
      hitboxProfile: string(fortress.hitboxProfile, `${path}.hitboxProfile`),
      protectedZoneId: string(fortress.protectedZoneId, `${path}.protectedZoneId`),
    };
  });
  const objects = raw.decorations.map((entry, index) => {
    const path = `decorations[${index}]`;
    const decoration = object(entry, path);
    keys(decoration, ["id", "assetKey", "x", "y", "scale", "rotationDegrees", "relicSize"],
      ["id", "assetKey", "x", "y", "scale", "rotationDegrees"], path);
    const relicSize = decoration.relicSize;
    if (relicSize !== undefined && relicSize !== null &&
      relicSize !== "SMALL" && relicSize !== "MEDIUM" && relicSize !== "LARGE") {
      throw new Error(`${path}.relicSize is unsupported`);
    }
    return {
      id: string(decoration.id, `${path}.id`),
      type: "DECORATION" as const,
      x: number(decoration.x, `${path}.x`),
      y: number(decoration.y, `${path}.y`),
      scale: number(decoration.scale, `${path}.scale`),
      rotationDegrees: number(decoration.rotationDegrees, `${path}.rotationDegrees`),
      assetKey: string(decoration.assetKey, `${path}.assetKey`),
      collision: "NONE" as const,
      allowOverlap: false,
      relicSize: relicSize as "SMALL" | "MEDIUM" | "LARGE" | null | undefined,
    };
  });

  let generationMetadata: MapDocumentJson["generationMetadata"] = {
    seed: 0,
    generatorVersion: "imported",
  };
  if (raw.generationMetadata !== undefined && raw.generationMetadata !== null) {
    const metadata = object(raw.generationMetadata, "generationMetadata");
    keys(
      metadata,
      ["seed", "generatorVersion", "authoringVersion"],
      ["seed", "generatorVersion"],
      "generationMetadata",
    );
    generationMetadata = {
      seed: number(metadata.seed, "generationMetadata.seed"),
      generatorVersion: string(metadata.generatorVersion, "generationMetadata.generatorVersion"),
      ...(metadata.authoringVersion === undefined
        ? {}
        : { authoringVersion: string(metadata.authoringVersion, "generationMetadata.authoringVersion") }),
    };
  }

  return {
    schemaVersion: CURRENT_SCHEMA_VERSION,
    mapId,
    biome,
    initialCameraAnchor,
    initialCameraZoom,
    world: { width: worldWidth, height: worldHeight },
    terrain: { sampleStep, samples },
    protectedTerrainZones,
    fortresses,
    catapults: catapultsForFortresses(fortresses),
    objects,
    generationMetadata,
  };
}

export function encode(document: EditorDocument | MapDocumentJson): string {
  const map = "map" in document ? document.map : document;
  const objects = map.objects.map((decoration, index) => {
    if (decoration.collision !== "NONE") {
      throw new Error(`objects[${index}] is not a visual-only decoration`);
    }
    return {
      id: decoration.id,
      assetKey: decoration.assetKey,
      x: decoration.x,
      y: decoration.y,
      scale: decoration.scale,
      rotationDegrees: decoration.rotationDegrees,
      ...(decoration.relicSize ? { relicSize: decoration.relicSize } : {}),
    };
  });
  const output = {
    schemaVersion: CURRENT_SCHEMA_VERSION,
    mapId: map.mapId,
    biome: map.biome,
    initialCameraAnchor: map.initialCameraAnchor,
    initialCameraZoom: map.initialCameraZoom,
    world: { width: map.world.width, height: map.world.height },
    terrain: {
      sampleStep: map.terrain.sampleStep,
      heightSamples: [...map.terrain.samples].sort((a, b) => a.x - b.x),
    },
    protectedZones: [...map.protectedTerrainZones]
      .sort((a, b) => a.id.localeCompare(b.id))
      .map(({ id, minX, maxX, minHeight, maxHeight, craterPolicy }) => ({
        id, minX, maxX, minHeight, maxHeight, craterPolicy: craterPolicy ?? "PRESERVE_FOUNDATION",
      })),
    fortresses: map.fortresses,
    decorations: objects.sort((a, b) => a.id.localeCompare(b.id)),
    ...(map.generationMetadata ? {
      generationMetadata: {
        seed: map.generationMetadata.seed,
        generatorVersion: map.generationMetadata.generatorVersion,
        ...(map.generationMetadata.authoringVersion
          ? { authoringVersion: map.generationMetadata.authoringVersion }
          : {}),
      },
    } : {}),
  };
  return JSON.stringify(output, null, 2) + "\n";
}

export function decode(text: string): MapDocumentJson {
  try {
    return normalizeMap(JSON.parse(text) as unknown);
  } catch (error) {
    throw new Error(`Invalid map JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
}

export function newDocument(): EditorDocument {
  const map = normalizeMap(JSON.parse(encode(defaultMap())));
  return { map, phase: "TERRAIN_EDITING", revision: 0 };
}
