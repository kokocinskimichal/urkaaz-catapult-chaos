import {
  isMapBiome,
  FOUNDATION_HEIGHT_TOLERANCE,
  MAX_TERRAIN_DELTA,
  maximumTerrainHeightInRange,
  TERRAIN_CONTROL_SPACING,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  terrainControlPoints,
} from "./map.ts";
import type { EditorDocument, MapBiome, MapObjectType, Team } from "./map.ts";
import { isKnownMapDecoration } from "./assets.ts";
import { generateRandomMap } from "./randomMap.ts";
import type { DecorationDensity } from "./randomMap.ts";
export type Operation =
 | { type: "CHANGE_BIOME"; biome: MapBiome }
 | {
   type: "GENERATE_RANDOM_MAP";
   seed: number;
   terrainAmplitude: number;
   decorationDensity: DecorationDensity;
 }
 | { type: "DRAW_TERRAIN_PROFILE"; points: {x:number;height:number}[] }
 | { type: "MOVE_TERRAIN_CONTROL_POINT"; x:number; height:number }
 | { type: "SMOOTH_TERRAIN_RANGE"; minX:number; maxX:number }
 | { type: "FLATTEN_TERRAIN_RANGE"; minX:number; maxX:number; height?:number }
 | { type: "CHANGE_FOUNDATION_HEIGHT"; owner:Team; height:number }
 | { type: "SHIFT_SCENE_VERTICALLY"; deltaY:number }
 | { type: "PLACE_OBJECT"; object: {id:string;type:MapObjectType;x:number;y:number;scale:number;rotationDegrees:number;assetKey:string;collision:"NONE"|"SOLID";allowOverlap:boolean;relicSize?:"SMALL"|"MEDIUM"|"LARGE" | null} }
 | { type: "MOVE_OBJECT"; id:string;x:number;y:number } | { type:"ROTATE_OBJECT";id:string;rotationDegrees:number }
 | { type:"SCALE_OBJECT";id:string;scale:number } | { type:"REMOVE_OBJECT";id:string }
 | { type:"TRANSFORM_OBJECT";id:string;scale:number;rotationDegrees:number;y:number };
export function applyOperation(
  doc: EditorDocument,
  op: Operation,
  assetKeys?: ReadonlySet<string>,
): EditorDocument {
  const map = structuredClone(doc.map);
  if (op.type === "CHANGE_BIOME") {
    if (!isMapBiome(op.biome)) throw new Error(`Unsupported biome: ${String(op.biome)}`);
    map.biome = op.biome;
    return { ...doc, map, revision: doc.revision + 1 };
  }
  if (op.type === "GENERATE_RANDOM_MAP") {
    if (!assetKeys) throw new Error("Asset catalogue is not loaded");
    const generatedMap = generateRandomMap(
      doc.map,
      op.seed,
      op.terrainAmplitude,
      op.decorationDensity,
      assetKeys,
    );
    const candidate = { ...doc, map: generatedMap, revision: doc.revision + 1 };
    const errors = validate(candidate, assetKeys)
      .filter(issue => issue.severity === "error");
    if (errors.length > 0) {
      throw new Error(`Generated map is invalid: ${errors.map(issue => `${issue.path}: ${issue.message}`).join("; ")}`);
    }
    return candidate;
  }
  if (op.type === "DRAW_TERRAIN_PROFILE") {
    const points = op.points.filter(p=>p.x>=0&&p.x<=WORLD_WIDTH&&p.height>=0&&p.height<=WORLD_HEIGHT);
    if (points.length < 2) throw new Error("Profile needs at least two points");
    map.terrain.samples = map.terrain.samples.map(s => {
      if (s.x < points[0].x || s.x > points[points.length-1].x) return s;
      if (map.protectedTerrainZones.some(z => s.x >= z.minX && s.x <= z.maxX)) return s;
      const rightIndex = points.findIndex(point => point.x >= s.x);
      const right = points[rightIndex] ?? points[points.length - 1];
      const left = points[Math.max(0, rightIndex - 1)] ?? points[0];
      if (left.x === right.x) return { x: s.x, height: left.height };
      const t = (s.x - left.x) / (right.x - left.x);
      return { x: s.x, height: left.height + (right.height - left.height) * t };
    });
  } else if (op.type === "MOVE_TERRAIN_CONTROL_POINT") {
    if (!Number.isFinite(op.height) || op.height < 0 || op.height > WORLD_HEIGHT) throw new Error("Control point height is outside the world");
    const controls = terrainControlPoints(map);
    const controlIndex = controls.findIndex(point => Math.abs(point.x - op.x) < 0.01);
    if (controlIndex < 0) throw new Error("Control point is not editable");
    const originalHeight = controls[controlIndex].height;
    const displacement = op.height - originalHeight;
    const influenceRadius = terrainInfluenceRadius(map);
    map.terrain.samples = map.terrain.samples.map(sample => {
      if (map.protectedTerrainZones.some(zone => sample.x >= zone.minX && sample.x <= zone.maxX)) return sample;
      const distance = Math.abs(sample.x - op.x);
      if (distance >= influenceRadius) return sample;
      const crossesFoundation = map.protectedTerrainZones.some(zone =>
        Math.min(sample.x, op.x) < zone.maxX && Math.max(sample.x, op.x) > zone.minX,
      );
      if (crossesFoundation) return sample;
      const ratio = distance / influenceRadius;
      const falloff = smoothTerrainFalloff(ratio);
      return { ...sample, height: sample.height + displacement * falloff };
    });
  } else if (op.type === "SMOOTH_TERRAIN_RANGE" || op.type === "FLATTEN_TERRAIN_RANGE") {
    const inside = map.terrain.samples.filter(s=>s.x>=op.minX&&s.x<=op.maxX);
    if (inside.some(s=>map.protectedTerrainZones.some(z=>s.x>=z.minX&&s.x<=z.maxX))) throw new Error("Range intersects protected foundation");
    const h = op.type === "FLATTEN_TERRAIN_RANGE" ? (op.height ?? inside.reduce((a,b)=>a+b.height,0)/Math.max(1,inside.length)) : undefined;
    map.terrain.samples = map.terrain.samples.map((s,i,a) => s.x < op.minX || s.x > op.maxX ? s : { ...s, height: h ?? (a[Math.max(0,i-1)].height+a[Math.min(a.length-1,i+1)].height)/2 });
  } else if (op.type === "CHANGE_FOUNDATION_HEIGHT") {
    if (!Number.isFinite(op.height) || op.height < 0 || op.height > WORLD_HEIGHT) {
      throw new Error("Foundation height is outside the world");
    }
    const fortress = map.fortresses.find(item => item.owner === op.owner);
    const zone = fortress && map.protectedTerrainZones.find(item => item.id === fortress.protectedZoneId);
    const catapult = map.catapults.find(item => item.owner === op.owner);
    if (!fortress || !zone || !catapult) throw new Error("Foundation contract is incomplete");

    const displacement = op.height - fortress.foundationHeight;
    const influenceRadius = terrainInfluenceRadius(map);
    map.terrain.samples = map.terrain.samples.map(sample => {
      if (sample.x >= zone.minX && sample.x <= zone.maxX) {
        return { ...sample, height: op.height };
      }
      if (map.protectedTerrainZones.some(other =>
        sample.x >= other.minX && sample.x <= other.maxX,
      )) {
        return sample;
      }
      const distance = sample.x < zone.minX
        ? zone.minX - sample.x
        : sample.x - zone.maxX;
      if (distance >= influenceRadius) return sample;
      const falloff = smoothTerrainFalloff(distance / influenceRadius);
      return { ...sample, height: sample.height + displacement * falloff };
    });
    fortress.foundationHeight = op.height;
    zone.minHeight = op.height;
    zone.maxHeight = op.height;
    catapult.foundationHeight = op.height;
  } else if (op.type === "SHIFT_SCENE_VERTICALLY") {
    if (!Number.isFinite(op.deltaY)) throw new Error("Whole-map vertical shift must be finite");
    if (op.deltaY === 0) return doc;
    const bounds = wholeSceneVerticalBounds(map);
    if (bounds.minY + op.deltaY < 0 || bounds.maxY + op.deltaY > WORLD_HEIGHT) {
      throw new Error("Whole-map vertical shift would move content outside the world");
    }
    map.terrain.samples = map.terrain.samples.map(sample => ({
      ...sample,
      height: sample.height + op.deltaY,
    }));
    map.protectedTerrainZones = map.protectedTerrainZones.map(zone => ({
      ...zone,
      minHeight: zone.minHeight + op.deltaY,
      maxHeight: zone.maxHeight + op.deltaY,
    }));
    map.fortresses = map.fortresses.map(fortress => ({
      ...fortress,
      foundationHeight: fortress.foundationHeight + op.deltaY,
    }));
    map.catapults = map.catapults.map(catapult => ({
      ...catapult,
      foundationHeight: catapult.foundationHeight + op.deltaY,
    }));
    map.objects = map.objects.map(object => ({
      ...object,
      y: object.y + op.deltaY,
    }));
    return { ...doc, map, revision: doc.revision + 1 };
  } else if (op.type === "PLACE_OBJECT") {
    if (map.objects.some(object => object.id === op.object.id)) throw new Error(`Object id is already used: ${op.object.id}`);
    map.objects.push(op.object);
    const objectIndex = map.objects.length - 1;
    const placementIssue = validate({ ...doc, map }, assetKeys)
      .find(issue => issue.severity === "error" && issue.path.startsWith(`decorations[${objectIndex}]`));
    if (placementIssue) throw new Error(`Invalid placement: ${placementIssue.message}`);
  }
  else if (op.type === "REMOVE_OBJECT") map.objects = map.objects.filter(o=>o.id!==op.id);
  else {
    const object = map.objects.find(o=>o.id===op.id); if(!object) throw new Error("Object not found");
    if(op.type==="MOVE_OBJECT"){
      object.x = op.x;
      object.y = op.y;
    }
    if(op.type==="ROTATE_OBJECT") object.rotationDegrees=op.rotationDegrees
    if(op.type==="SCALE_OBJECT"){if(op.scale<=0) throw new Error("Scale must be positive");object.scale=op.scale}
    if(op.type==="TRANSFORM_OBJECT"){
      if(!Number.isFinite(op.scale)||op.scale<=0) throw new Error("Scale must be positive");
      if(!Number.isFinite(op.rotationDegrees)||!Number.isFinite(op.y)) throw new Error("Transform values must be finite");
      object.scale=op.scale;object.rotationDegrees=op.rotationDegrees;object.y=op.y;
    }
    const placementIssue = validate({ ...doc, map }, assetKeys)
      .find(issue => issue.severity === "error" && issue.path.startsWith(`decorations[${map.objects.indexOf(object)}]`));
    if (placementIssue) throw new Error(`Invalid placement: ${placementIssue.message}`);
  }
  if (isTerrainOperation(op)) {
    const relicIssues = validateRelics(map);
    if (relicIssues.length > 0) {
    throw new Error(`Terrain edit would invalidate ${relicIssues.map(issue => `${issue.path}: ${issue.message}`).join("; ")}`);
    }
  }
  return { ...doc, map, revision: doc.revision + 1 };
}

function isTerrainOperation(operation: Operation): boolean {
  return operation.type === "DRAW_TERRAIN_PROFILE" ||
    operation.type === "MOVE_TERRAIN_CONTROL_POINT" ||
    operation.type === "SMOOTH_TERRAIN_RANGE" ||
    operation.type === "FLATTEN_TERRAIN_RANGE" ||
    operation.type === "CHANGE_FOUNDATION_HEIGHT";
}

function validateRelics(map: EditorDocument["map"]) {
  const issues: import("./map.ts").ValidationIssue[] = [];
  for (const object of map.objects) {
    if (object.relicSize == null) continue;
    validateRelicPlacement(map, object, `objects.${object.id}`, issues);
  }
  return issues;
}

function terrainInfluenceRadius(map: EditorDocument["map"]): number {
  const controlSpacing = Math.max(
    map.terrain.sampleStep,
    Math.round(TERRAIN_CONTROL_SPACING / map.terrain.sampleStep) * map.terrain.sampleStep,
  );
  return controlSpacing * 3;
}

function smoothTerrainFalloff(ratio: number): number {
  return 1 - (3 * ratio * ratio - 2 * ratio * ratio * ratio);
}

function wholeSceneVerticalBounds(map: EditorDocument["map"]): { minY: number; maxY: number } {
  let minY = Number.POSITIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  const include = (y: number) => {
    if (!Number.isFinite(y)) throw new Error("Cannot move a map with non-finite vertical coordinates");
    minY = Math.min(minY, y);
    maxY = Math.max(maxY, y);
  };

  map.terrain.samples.forEach(sample => include(sample.height));
  map.protectedTerrainZones.forEach(zone => {
    include(zone.minHeight);
    include(zone.maxHeight);
  });
  map.fortresses.forEach(fortress => include(fortress.foundationHeight));
  map.catapults.forEach(catapult => include(catapult.foundationHeight));
  map.objects.forEach(object => {
    const footprint = relicWorldFootprint(object);
    include(footprint?.top ?? object.y);
    include(footprint?.bottom ?? object.y);
  });
  if (!Number.isFinite(minY) || !Number.isFinite(maxY)) {
    throw new Error("Cannot move a map without vertical content");
  }
  return { minY, maxY };
}

function relicWorldFootprint(object: EditorDocument["map"]["objects"][number]) {
  if (!object.relicSize) return undefined;
  const dimensions = {
    SMALL: { halfWidth: 56, halfHeight: 40 },
    MEDIUM: { halfWidth: 96, halfHeight: 72 },
    LARGE: { halfWidth: 160, halfHeight: 120 },
  }[object.relicSize];
  const radians = object.rotationDegrees * Math.PI / 180;
  const halfWidth = Math.abs(dimensions.halfWidth * object.scale * Math.cos(radians)) +
    Math.abs(dimensions.halfHeight * object.scale * Math.sin(radians));
  const halfHeight = Math.abs(dimensions.halfWidth * object.scale * Math.sin(radians)) +
    Math.abs(dimensions.halfHeight * object.scale * Math.cos(radians));
  return {
    left: object.x - halfWidth,
    right: object.x + halfWidth,
    top: object.y - halfHeight,
    bottom: object.y + halfHeight,
  };
}

export function validate(doc: EditorDocument, assetKeys?: ReadonlySet<string>) {
  const issues: import("./map.ts").ValidationIssue[] = [];
  const map = doc.map;
  const samples = map.terrain.samples;

  if (map.schemaVersion !== 1) {
    issues.push({ severity: "error", path: "schemaVersion", message: "Only schema version 1 is supported" });
  }
  if (!map.mapId.trim()) {
    issues.push({ severity: "error", path: "mapId", message: "Map id must not be blank" });
  }
  if (!isMapBiome(map.biome)) {
    issues.push({ severity: "error", path: "biome", message: `Unsupported map biome '${map.biome}'` });
  }
  if (map.world.width !== WORLD_WIDTH || map.world.height !== WORLD_HEIGHT) {
    issues.push({ severity: "error", path: "world", message: `World must be ${WORLD_WIDTH} × ${WORLD_HEIGHT}` });
  }
  if (![20, 40].includes(map.terrain.sampleStep)) {
    issues.push({ severity: "error", path: "terrain.sampleStep", message: "Sample step must be 20 or 40" });
  }
  if (samples.length === 0 || samples[0].x !== 0) {
    issues.push({
      severity: "error",
      path: "terrain.heightSamples[0]",
      message: "Profile must start at x=0",
      world: { x: 0, y: 0 },
    });
  }
  if (samples.length === 0 || samples[samples.length - 1].x !== map.world.width) {
    issues.push({
      severity: "error",
      path: `terrain.heightSamples[${Math.max(0, samples.length - 1)}]`,
      message: `Profile must end at x=${map.world.width}`,
      world: { x: map.world.width, y: 0 },
    });
  }
  samples.forEach((sample, index) => {
    if (!Number.isFinite(sample.x) || !Number.isFinite(sample.height)) {
      issues.push({
        severity: "error",
        path: `terrain.heightSamples[${index}]`,
        message: "Coordinates must be finite",
      });
      return;
    }
    if (sample.x < 0 || sample.x > map.world.width ||
      sample.height < 0 || sample.height > map.world.height) {
      issues.push({
        severity: "error",
        path: `terrain.heightSamples[${index}]`,
        message: "Terrain sample must be inside world bounds",
        world: { x: sample.x, y: sample.height },
      });
    }
    if (index === 0) return;

    const previous = samples[index - 1];
    if (sample.x - previous.x !== map.terrain.sampleStep) {
      issues.push({
        severity: "error",
        path: `terrain.heightSamples[${index}].x`,
        message: `Sample spacing must be ${map.terrain.sampleStep}`,
        world: { x: sample.x, y: sample.height },
      });
    }
    if (Math.abs(sample.height - previous.height) > MAX_TERRAIN_DELTA) {
      issues.push({
        severity: "error",
        path: `terrain.heightSamples[${index}].height`,
        message: "Adjacent terrain heights must differ by no more than 200",
        world: { x: sample.x, y: sample.height },
      });
    }
  });

  validateCamera(map, issues);
  validateProtectedZones(map, issues);
  validateFortresses(map, issues);
  validateDecorations(map, assetKeys, issues);
  return issues;
}

function validateCamera(
  map: EditorDocument["map"],
  issues: import("./map.ts").ValidationIssue[],
): void {
  if (!["PLAYER_FORTRESS", "ENEMY_FORTRESS", "CENTER"].includes(map.initialCameraAnchor)) {
    issues.push({ severity: "error", path: "initialCameraAnchor", message: "Unsupported camera anchor" });
  } else if (map.initialCameraAnchor === "PLAYER_FORTRESS" &&
    !map.fortresses.some(fortress => fortress.owner === "BLUE")) {
    issues.push({
      severity: "error",
      path: "initialCameraAnchor",
      message: "PLAYER_FORTRESS requires a BLUE fortress",
    });
  } else if (map.initialCameraAnchor === "ENEMY_FORTRESS" &&
    !map.fortresses.some(fortress => fortress.owner === "RED")) {
    issues.push({
      severity: "error",
      path: "initialCameraAnchor",
      message: "ENEMY_FORTRESS requires a RED fortress",
    });
  }
  if (!Number.isFinite(map.initialCameraZoom) || map.initialCameraZoom < 1 || map.initialCameraZoom > 4) {
    issues.push({ severity: "error", path: "initialCameraZoom", message: "Zoom must be between 1 and 4" });
  }
}

function validateProtectedZones(
  map: EditorDocument["map"],
  issues: import("./map.ts").ValidationIssue[],
): void {
  const ids = new Set<string>();
  map.protectedTerrainZones.forEach((zone, index) => {
    const path = `protectedZones[${index}]`;
    if (!zone.id.trim()) issues.push({ severity: "error", path: `${path}.id`, message: "Zone id must not be blank" });
    if (ids.has(zone.id)) issues.push({ severity: "error", path: `${path}.id`, message: "Zone ids must be unique" });
    ids.add(zone.id);
    if (![zone.minX, zone.maxX, zone.minHeight, zone.maxHeight].every(Number.isFinite) ||
      zone.minX < 0 || zone.maxX > map.world.width || zone.minX >= zone.maxX ||
      zone.minHeight < 0 || zone.maxHeight > map.world.height || zone.minHeight > zone.maxHeight
    ) {
      issues.push({ severity: "error", path, message: "Protected zone bounds are invalid" });
    }
    if (zone.craterPolicy !== undefined && zone.craterPolicy !== "PRESERVE_FOUNDATION") {
      issues.push({ severity: "error", path: `${path}.craterPolicy`, message: "Unsupported crater policy" });
    }
  });
}

function validateFortresses(
  map: EditorDocument["map"],
  issues: import("./map.ts").ValidationIssue[],
): void {
  const owners = map.fortresses.map(fortress => fortress.owner);
  if (map.fortresses.length !== 2 || new Set(owners).size !== 2 ||
    !owners.includes("BLUE") || !owners.includes("RED")) {
    issues.push({ severity: "error", path: "fortresses", message: "Exactly one fortress per team is required" });
  }

  const protectedZoneIds = new Set(map.protectedTerrainZones.map(zone => zone.id));
  map.fortresses.forEach((fortress, index) => {
    const path = `fortresses[${index}]`;
    if (fortress.assetKey !== `fortress.${fortress.owner.toLowerCase()}`) {
      issues.push({ severity: "error", path: `${path}.assetKey`, message: "Asset key must match fortress owner" });
    }
    if (!fortress.hitboxProfile.trim()) {
      issues.push({ severity: "error", path: `${path}.hitboxProfile`, message: "Hitbox profile must not be blank" });
    }
    if (![fortress.x, fortress.foundationHeight, fortress.halfWidth].every(Number.isFinite) ||
      fortress.x < 0 || fortress.x > map.world.width ||
      fortress.foundationHeight < 0 || fortress.foundationHeight > map.world.height ||
      fortress.halfWidth <= 0
    ) {
      issues.push({ severity: "error", path, message: "Fortress placement is outside world bounds" });
    }
    if (!protectedZoneIds.has(fortress.protectedZoneId)) {
      issues.push({
        severity: "error",
        path: `${path}.protectedZoneId`,
        message: "Fortress must reference an existing protected zone",
      });
    }

    const zone = map.protectedTerrainZones.find(item => item.id === fortress.protectedZoneId);
    if (zone && (
      zone.minX > fortress.x - fortress.halfWidth ||
      zone.maxX < fortress.x + fortress.halfWidth ||
      fortress.foundationHeight < zone.minHeight ||
      fortress.foundationHeight > zone.maxHeight
    )) {
      issues.push({
        severity: "error",
        path: `${path}.protectedZoneId`,
        message: "Protected zone must cover the fortress foundation",
      });
    }
    validateFoundation(map, fortress, path, issues);

    const catapult = map.catapults.find(item => item.owner === fortress.owner);
    if (!catapult || catapult.foundationHeight !== fortress.foundationHeight) {
      issues.push({
        severity: "error",
        path,
        message: "Catapult foundation must match its fortress",
        world: { x: fortress.x, y: fortress.foundationHeight },
      });
    }
  });
}

function validateFoundation(
  map: EditorDocument["map"],
  fortress: EditorDocument["map"]["fortresses"][number],
  path: string,
  issues: import("./map.ts").ValidationIssue[],
): void {
  const tolerance = FOUNDATION_HEIGHT_TOLERANCE;
  const foundationSamples = map.terrain.samples.filter(sample =>
    sample.x >= fortress.x - tolerance && sample.x <= fortress.x + tolerance,
  );
  if (foundationSamples.length < 3) {
    issues.push({ severity: "error", path, message: "Foundation transition needs at least three terrain samples" });
    return;
  }
  const heights = foundationSamples.map(sample => sample.height);
  if (Math.max(...heights) - Math.min(...heights) > tolerance) {
    issues.push({ severity: "error", path, message: "Foundation terrain must be flat" });
  }
  if (fortress.foundationHeight < Math.min(...heights) - tolerance ||
    fortress.foundationHeight > Math.max(...heights) + tolerance) {
    issues.push({
      severity: "error",
      path: `${path}.foundationHeight`,
      message: "Foundation height is outside the allowed range",
    });
  }
}

function validateDecorations(
  map: EditorDocument["map"],
  assetKeys: ReadonlySet<string> | undefined,
  issues: import("./map.ts").ValidationIssue[],
): void {
  const ids = new Set<string>();
  if (map.objects.filter(object => object.relicSize === "LARGE").length > 1) {
    issues.push({ severity: "error", path: "decorations", message: "At most one LARGE relic is allowed per map" });
  }
  map.objects.forEach((object, index) => {
    const path = `decorations[${index}]`;
    if (!object.id.trim()) issues.push({ severity: "error", path: `${path}.id`, message: "Decoration id must not be blank" });
    if (ids.has(object.id)) issues.push({ severity: "error", path: `${path}.id`, message: "Decoration ids must be unique" });
    ids.add(object.id);
    if (!assetKeys) {
      issues.push({ severity: "error", path: `${path}.assetKey`, message: "Asset catalogue is not loaded" });
    } else if (!isKnownMapDecoration(object.assetKey, map.biome, assetKeys) ||
      !relicAssetMatchesSize(object, map.biome)) {
      issues.push({
        severity: "error",
        path: `${path}.assetKey`,
        message: `Unknown decoration asset '${object.assetKey}' for ${map.biome}`,
      });
    }

    function relicAssetMatchesSize(
      object: EditorDocument["map"]["objects"][number],
      biome: EditorDocument["map"]["biome"],
    ): boolean {
      if (object.relicSize == null) return !object.assetKey.startsWith("relic.");
      if (object.relicSize === "MEDIUM") return object.assetKey.startsWith("relic.shared.medium.");
      return object.assetKey.startsWith(`relic.${biome}.small.`);
    }
    if (object.collision !== "NONE") {
      issues.push({ severity: "error", path: `${path}.collision`, message: "Map decorations must be visual-only" });
    }
    if (![object.x, object.y, object.scale, object.rotationDegrees].every(Number.isFinite) ||
      object.scale <= 0) {
      issues.push({ severity: "error", path, message: "Position and transform values must be finite and scale must be positive" });
      return;
    }
    if (object.relicSize == null) {
      validateGroundedPlacement(object, path, issues);
    } else {
      validateRelicPlacement(map, object, path, issues);
    }
  });
}

function validateGroundedPlacement(
  object: EditorDocument["map"]["objects"][number],
  path: string,
  issues: import("./map.ts").ValidationIssue[],
): void {
  if (object.x < 0 || object.x > WORLD_WIDTH || object.y < 0 || object.y > WORLD_HEIGHT) {
    issues.push({ severity: "error", path: `${path}.position`, message: "Decoration must be inside world bounds" });
  }
}

function validateRelicPlacement(
  map: EditorDocument["map"],
  object: EditorDocument["map"]["objects"][number],
  path: string,
  issues: import("./map.ts").ValidationIssue[],
): void {
  const footprint = relicWorldFootprint(object);
  if (!footprint) return;
  const { left, right, top, bottom } = footprint;
  if (left < 0 || right > WORLD_WIDTH || top < 0 || bottom > WORLD_HEIGHT) {
    issues.push({ severity: "error", path: `${path}.footprint`, message: "Relic footprint must stay inside world bounds" });
    return;
  }
  const terrainHeight = maximumTerrainHeightInRange(map, left, right);
  if (terrainHeight === undefined) {
    issues.push({ severity: "error", path: `${path}.footprint`, message: "Relic requires a terrain profile" });
  } else if (top < terrainHeight) {
    issues.push({ severity: "error", path: `${path}.footprint`, message: "Relic footprint must be fully inside soil" });
  }
}
