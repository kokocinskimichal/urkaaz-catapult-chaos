export const WORLD_WIDTH = 7200;
export const WORLD_HEIGHT = 4800;
export type Team = "BLUE" | "RED";
export type Facing = "LEFT" | "RIGHT";
export type MapObjectType = "DECORATION" | "GAMEPLAY";
export type EditorPhase = "TERRAIN_EDITING" | "TERRAIN_LOCKED" | "DECORATION_EDITING" | "READY_TO_EXPORT";
export type Category = "tree" | "rock" | "prop" | "relic" | "ambient";

export interface SampleJson { x: number; height: number }
export interface TerrainJson { sampleStep: number; samples: SampleJson[] }
export interface ProtectedZoneJson { id: string; minX: number; maxX: number; minHeight: number; maxHeight: number; craterPolicy?: "PRESERVE_FOUNDATION" }
export interface FortressJson { owner: Team; x: number; foundationHeight: number; halfWidth: number; assetKey: string; hitboxProfile: string; protectedZoneId: string }
export interface CatapultJson { owner: Team; x: number; foundationHeight: number; facing: Facing; halfWidth: number; assetKey: string; protectedZoneId?: string | null }
export interface ObjectJson { id: string; type: MapObjectType; x: number; y: number; scale: number; rotationDegrees: number; assetKey: string; collision: "NONE" | "SOLID"; allowOverlap: boolean; relicSize?: "SMALL" | "MEDIUM" | "LARGE" | null }
export interface MetadataJson { seed: number; generatorVersion: string; authoringVersion: string; source: string; author?: string; fingerprint?: string | null }
export interface MapDocumentJson {
  schemaVersion: number; mapId: string; biome: string;
  world: { width: number; height: number };
  terrain: TerrainJson; protectedTerrainZones: ProtectedZoneJson[];
  fortresses: FortressJson[]; catapults: CatapultJson[]; objects: ObjectJson[];
  initialCameraAnchor: "PLAYER_FORTRESS" | "ENEMY_FORTRESS" | "CENTER";
  generationMetadata: MetadataJson; terrainTiles?: unknown[]; authoringPhase?: EditorPhase;
}
export interface EditorDocument { map: MapDocumentJson; phase: EditorPhase; selectedId?: string; revision: number }
export interface ValidationIssue { severity: "error" | "warning"; path: string; message: string; world?: { x: number; y: number } }
export interface AssetDescriptor { key: string; biome: string; category: Category; path?: string; label: string }

export const foundationExtension = 320;
export const CURRENT_AUTHORING_VERSION = "map-editor-web/0.4";
export const DEFAULT_TERRAIN_SAMPLE_STEP = 40;
export const TERRAIN_CONTROL_SPACING = 200;
export function sampleXs(step: number, width = WORLD_WIDTH): number[] {
  const xs: number[] = []; for (let x = 0; x <= width; x += step) xs.push(Math.min(x, width));
  if (xs[xs.length - 1] !== width) xs.push(width); return xs;
}

export function terrainControlPoints(map: MapDocumentJson): SampleJson[] {
  const stride = Math.max(1, Math.round(TERRAIN_CONTROL_SPACING / map.terrain.sampleStep));
  return map.terrain.samples.filter((sample, index, samples) =>
    (index % stride === 0 || index === samples.length - 1) &&
    !map.protectedTerrainZones.some(zone => sample.x >= zone.minX && sample.x <= zone.maxX)
  );
}
export function defaultMap(id = "web-map", biome = "green_frontier"): MapDocumentJson {
  const foundationHeight = 3180;
  const samples = sampleXs(DEFAULT_TERRAIN_SAMPLE_STEP)
    .map(x => ({ x, height: foundationHeight }));
  const foundationZones = [
    {
      id: "blue-foundation",
      minX: 0,
      maxX: Math.ceil((490 + 240 + foundationExtension) / DEFAULT_TERRAIN_SAMPLE_STEP) * DEFAULT_TERRAIN_SAMPLE_STEP,
      minHeight: foundationHeight,
      maxHeight: foundationHeight,
      craterPolicy: "PRESERVE_FOUNDATION" as const,
    },
    {
      id: "red-foundation",
      minX: Math.floor((6710 - 240 - foundationExtension) / DEFAULT_TERRAIN_SAMPLE_STEP) * DEFAULT_TERRAIN_SAMPLE_STEP,
      maxX: WORLD_WIDTH,
      minHeight: foundationHeight,
      maxHeight: foundationHeight,
      craterPolicy: "PRESERVE_FOUNDATION" as const,
    }
  ];
  foundationZones.forEach(zone => samples.forEach(sample => {
    if (sample.x >= zone.minX && sample.x <= zone.maxX) sample.height = zone.minHeight;
  }));
  return {
    schemaVersion: 1, mapId: id, biome, world: { width: WORLD_WIDTH, height: WORLD_HEIGHT },
    terrain: { sampleStep: DEFAULT_TERRAIN_SAMPLE_STEP, samples },
    protectedTerrainZones: foundationZones,
    fortresses: [
      { owner: "BLUE", x: 490, foundationHeight: 3180, halfWidth: 240, assetKey: "fortress.blue", hitboxProfile: "fortress.default", protectedZoneId: "blue-foundation" },
      { owner: "RED", x: 6710, foundationHeight: 3180, halfWidth: 240, assetKey: "fortress.red", hitboxProfile: "fortress.default", protectedZoneId: "red-foundation" }
    ],
    catapults: [
      { owner: "BLUE", x: 450, foundationHeight: 3180, facing: "RIGHT", halfWidth: 72, assetKey: "catapult.blue", protectedZoneId: "blue-foundation" },
      { owner: "RED", x: 6750, foundationHeight: 3180, facing: "LEFT", halfWidth: 72, assetKey: "catapult.red", protectedZoneId: "red-foundation" }
    ],
    objects: [], initialCameraAnchor: "CENTER",
    generationMetadata: { seed: 1, generatorVersion: "web", authoringVersion: CURRENT_AUTHORING_VERSION, source: "map-editor-web", author: "Urkaaaz" },
    terrainTiles: []
  };
}
