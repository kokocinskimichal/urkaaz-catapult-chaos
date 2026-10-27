export const WORLD_WIDTH = 7200;
export const WORLD_HEIGHT = 4800;
export const MAP_BIOMES = [
  { value: "green_frontier", label: "Green Frontier" },
  { value: "frostbound", label: "Frostbound" },
  { value: "copperwood", label: "Copperwood" },
  { value: "ashen_march", label: "Ashen March" },
  { value: "sunken_marshes", label: "Sunken Marshes" },
] as const;
export type MapBiome = typeof MAP_BIOMES[number]["value"];

export function isMapBiome(value: string): value is MapBiome {
  return MAP_BIOMES.some(biome => biome.value === value);
}

export type Team = "BLUE" | "RED";
export type Facing = "LEFT" | "RIGHT";
export type InitialCameraAnchor = "PLAYER_FORTRESS" | "ENEMY_FORTRESS" | "CENTER";
export type MapObjectType = "DECORATION";
export type EditorPhase = "TERRAIN_EDITING" | "TERRAIN_LOCKED" | "DECORATION_EDITING" | "READY_TO_EXPORT";

export interface SampleJson { x: number; height: number }
export interface TerrainJson { sampleStep: number; samples: SampleJson[] }
export interface ProtectedZoneJson { id: string; minX: number; maxX: number; minHeight: number; maxHeight: number; craterPolicy?: "PRESERVE_FOUNDATION" }
export interface FortressJson { owner: Team; x: number; foundationHeight: number; halfWidth: number; assetKey: string; hitboxProfile: string; protectedZoneId: string }
export interface CatapultJson { owner: Team; x: number; foundationHeight: number; facing: Facing; halfWidth: number; assetKey: string; protectedZoneId?: string | null }
export interface ObjectJson { id: string; type: MapObjectType; x: number; y: number; scale: number; rotationDegrees: number; assetKey: string; collision: "NONE" | "SOLID"; allowOverlap: boolean; relicSize?: "SMALL" | "MEDIUM" | "LARGE" | null }
export interface MetadataJson { seed: number; generatorVersion: string; authoringVersion?: string }
export interface MapDocumentJson {
  schemaVersion: number; mapId: string; biome: MapBiome;
  initialCameraAnchor: InitialCameraAnchor; initialCameraZoom: number;
  world: { width: number; height: number };
  terrain: TerrainJson; protectedTerrainZones: ProtectedZoneJson[];
  fortresses: FortressJson[]; catapults: CatapultJson[]; objects: ObjectJson[];
  generationMetadata: MetadataJson;
}
export interface EditorDocument { map: MapDocumentJson; phase: EditorPhase; selectedId?: string; revision: number }
export interface ValidationIssue { severity: "error" | "warning"; path: string; message: string; world?: { x: number; y: number } }

export const foundationExtension = 320;
export const DEFAULT_TERRAIN_SAMPLE_STEP = 40;
export const TERRAIN_CONTROL_SPACING = 200;

export function catapultsForFortresses(fortresses: FortressJson[]): CatapultJson[] {
  return fortresses.map(fortress => ({
    owner: fortress.owner,
    x: fortress.x + (fortress.owner === "BLUE" ? -110 : 110),
    foundationHeight: fortress.foundationHeight,
    facing: fortress.owner === "BLUE" ? "RIGHT" : "LEFT",
    halfWidth: 72,
    assetKey: `catapult.${fortress.owner.toLowerCase()}`,
    protectedZoneId: fortress.protectedZoneId,
  }));
}

export function terrainHeightAt(map: MapDocumentJson, x: number): number | undefined {
  const samples = map.terrain.samples;
  if (samples.length === 0) return undefined;
  const clampedX = Math.max(samples[0].x, Math.min(samples[samples.length - 1].x, x));
  const rightIndex = samples.findIndex(sample => sample.x >= clampedX);
  if (rightIndex <= 0) return samples[0].height;
  const left = samples[rightIndex - 1];
  const right = samples[rightIndex];
  if (right.x === left.x) return left.height;
  const fraction = (clampedX - left.x) / (right.x - left.x);
  return left.height + (right.height - left.height) * fraction;
}

export function maximumTerrainHeightInRange(
  map: MapDocumentJson,
  minX: number,
  maxX: number,
): number | undefined {
  const endpoints = [terrainHeightAt(map, minX), terrainHeightAt(map, maxX)]
    .filter((height): height is number => height !== undefined);
  const samples = map.terrain.samples
    .filter(sample => sample.x >= minX && sample.x <= maxX)
    .map(sample => sample.height);
  const heights = [...endpoints, ...samples];
  return heights.length ? Math.max(...heights) : undefined;
}

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
export function defaultMap(id = "web-map", biome: MapBiome = "green_frontier"): MapDocumentJson {
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
  const fortresses: FortressJson[] = [
    { owner: "BLUE", x: 490, foundationHeight: 3180, halfWidth: 240, assetKey: "fortress.blue", hitboxProfile: "fortress.default", protectedZoneId: "blue-foundation" },
    { owner: "RED", x: 6710, foundationHeight: 3180, halfWidth: 240, assetKey: "fortress.red", hitboxProfile: "fortress.default", protectedZoneId: "red-foundation" }
  ];
  return {
    schemaVersion: 1, mapId: id, biome, world: { width: WORLD_WIDTH, height: WORLD_HEIGHT },
    initialCameraAnchor: "PLAYER_FORTRESS", initialCameraZoom: 1,
    terrain: { sampleStep: DEFAULT_TERRAIN_SAMPLE_STEP, samples },
    protectedTerrainZones: foundationZones,
    fortresses,
    catapults: catapultsForFortresses(fortresses),
    objects: [],
    generationMetadata: {
      seed: 1,
      generatorVersion: "studio",
      authoringVersion: "urkaaz-studio/0.1.0",
    }
  };
}
