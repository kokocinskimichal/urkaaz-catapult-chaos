import {
  CURRENT_AUTHORING_VERSION,
  defaultMap,
  EditorDocument,
  MapDocumentJson,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  foundationExtension,
} from "../domain/map";
export const CURRENT_SCHEMA_VERSION = 1;
export function normalizeMap(input: unknown): MapDocumentJson {
  if (!input || typeof input !== "object") throw new Error("Map document must be an object");
  const raw = input as Record<string, unknown>;
  if (typeof raw.schemaVersion !== "number" || raw.schemaVersion > CURRENT_SCHEMA_VERSION) throw new Error(`Unsupported schemaVersion: ${String(raw.schemaVersion)}`);
  const world = raw.world as Record<string, unknown> | undefined;
  if (!world || typeof world.width !== "number" || typeof world.height !== "number") throw new Error("world.width and world.height are required");
  if (world.width !== WORLD_WIDTH || world.height !== WORLD_HEIGHT) throw new Error(`World must be ${WORLD_WIDTH} × ${WORLD_HEIGHT}`);
  const terrain = raw.terrain as Record<string, unknown> | undefined;
  const rawSamples = Array.isArray(terrain?.samples) ? terrain.samples : terrain?.heightSamples;
  if (!terrain || !Array.isArray(rawSamples)) throw new Error("terrain.samples/terrain.heightSamples is required");
  const samples = rawSamples.map((s) => {
    const v = s as Record<string, unknown>;
    if (typeof v.x !== "number" || typeof v.height !== "number") throw new Error("Invalid terrain sample");
    return { x: v.x, height: v.height };
  }).sort((a, b) => a.x - b.x);
  const normalized: MapDocumentJson = {
    ...(raw as unknown as MapDocumentJson), schemaVersion: raw.schemaVersion as number, mapId: String(raw.mapId ?? "imported-map"),
    biome: String(raw.biome ?? "green_frontier"), world: { width: world.width, height: world.height },
    terrain: { sampleStep: Number(terrain.sampleStep ?? 40), samples },
    protectedTerrainZones: Array.isArray(raw.protectedTerrainZones)
      ? raw.protectedTerrainZones as MapDocumentJson["protectedTerrainZones"]
      : Array.isArray(raw.protectedZones)
        ? raw.protectedZones as MapDocumentJson["protectedTerrainZones"]
        : [],
    fortresses: Array.isArray(raw.fortresses) ? raw.fortresses as MapDocumentJson["fortresses"] : [],
    catapults: Array.isArray(raw.catapults) ? raw.catapults as MapDocumentJson["catapults"] : [],
    objects: Array.isArray(raw.objects) ? raw.objects as MapDocumentJson["objects"] : [],
    initialCameraAnchor: (raw.initialCameraAnchor ?? "CENTER") as MapDocumentJson["initialCameraAnchor"],
    generationMetadata: (raw.generationMetadata ?? raw.metadata ?? {
      seed: 0,
      generatorVersion: "imported",
      authoringVersion: "map-editor-web",
      source: "import",
    }) as MapDocumentJson["generationMetadata"],
    terrainTiles: Array.isArray(raw.terrainTiles) ? raw.terrainTiles : Array.isArray(terrain.tiles) ? terrain.tiles : [],
    authoringPhase: typeof (raw.metadata as Record<string, unknown> | undefined)?.phase === "string"
      ? (raw.metadata as Record<string, unknown>).phase as MapDocumentJson["authoringPhase"]
      : undefined,
  };
  if (normalized.generationMetadata.authoringVersion === CURRENT_AUTHORING_VERSION) return normalized;
  const hasUntouchedDefaultTerrain = normalized.mapId === "web-map" &&
    normalized.generationMetadata.source === "map-editor-web" &&
    normalized.terrain.samples.every(sample => {
      const inLegacyFoundation = normalized.protectedTerrainZones.some(
        zone => sample.x >= zone.minX && sample.x <= zone.maxX,
      );
      const expected = inLegacyFoundation
        ? normalized.fortresses[0]?.foundationHeight
        : 3300 + 180 * Math.sin(sample.x / 560);
      return expected !== undefined && Math.abs(sample.height - expected) < 0.01;
    });

  normalized.fortresses.forEach(fortress => {
    const zone = normalized.protectedTerrainZones.find(candidate => candidate.id === fortress.protectedZoneId);
    if (!zone) return;
    const step = normalized.terrain.sampleStep;
    if (fortress.owner === "BLUE") {
      zone.minX = 0;
      zone.maxX = Math.min(
        normalized.world.width,
        Math.ceil((fortress.x + fortress.halfWidth + foundationExtension) / step) * step,
      );
    } else {
      zone.minX = Math.max(
        0,
        Math.floor((fortress.x - fortress.halfWidth - foundationExtension) / step) * step,
      );
      zone.maxX = normalized.world.width;
    }
    zone.minHeight = fortress.foundationHeight;
    zone.maxHeight = fortress.foundationHeight;
    const catapult = normalized.catapults.find(candidate => candidate.owner === fortress.owner);
    if (catapult) catapult.foundationHeight = fortress.foundationHeight;
    normalized.terrain.samples.forEach(sample => {
      if (sample.x >= zone.minX && sample.x <= zone.maxX) sample.height = fortress.foundationHeight;
    });
  });
  if (hasUntouchedDefaultTerrain && normalized.fortresses.length > 0) {
    const initialHeight = normalized.fortresses[0].foundationHeight;
    normalized.terrain.samples.forEach(sample => { sample.height = initialHeight; });
  }
  normalized.generationMetadata.authoringVersion = CURRENT_AUTHORING_VERSION;
  return normalized;
}
export function encode(document: EditorDocument | MapDocumentJson): string {
  const map = "map" in document ? document.map : document;
  const phase = "phase" in document ? document.phase : "READY_TO_EXPORT";
  const sorted = {
    schemaVersion: map.schemaVersion,
    mapId: map.mapId,
    biome: map.biome,
    world: { ...map.world, tileSize: map.terrain.sampleStep },
    terrain: {
      sampleStep: map.terrain.sampleStep,
      heightSamples: [...map.terrain.samples].sort((a,b)=>a.x-b.x),
      tiles: map.terrainTiles ?? [],
    },
    fortresses: map.fortresses,
    catapults: map.catapults,
    protectedZones: [...map.protectedTerrainZones].sort((a,b)=>a.id.localeCompare(b.id)),
    objects: [...map.objects].sort((a,b)=>a.id.localeCompare(b.id)),
    metadata: {
      phase,
      terrainLocked: phase !== "TERRAIN_EDITING",
      ...map.generationMetadata,
    },
  };
  return JSON.stringify(sorted, null, 2) + "\n";
}
export function decode(text: string): MapDocumentJson { try { return normalizeMap(JSON.parse(text)); } catch (e) { throw new Error(`Invalid map JSON: ${e instanceof Error ? e.message : String(e)}`); } }
export function newDocument(): EditorDocument { return { map: defaultMap(), phase: "TERRAIN_EDITING", revision: 0 }; }
