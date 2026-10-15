import {
  EditorDocument,
  isMapBiome,
  MapBiome,
  MapObjectType,
  maximumTerrainHeightInRange,
  Team,
  TERRAIN_CONTROL_SPACING,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  terrainControlPoints,
} from "./map";
import { isKnownMapDecoration } from "./assets";
export type Operation =
 | { type: "CHANGE_BIOME"; biome: MapBiome }
 | { type: "DRAW_TERRAIN_PROFILE"; points: {x:number;height:number}[] }
 | { type: "MOVE_TERRAIN_CONTROL_POINT"; x:number; height:number }
 | { type: "SMOOTH_TERRAIN_RANGE"; minX:number; maxX:number }
 | { type: "FLATTEN_TERRAIN_RANGE"; minX:number; maxX:number; height?:number }
 | { type: "CHANGE_FOUNDATION_HEIGHT"; owner:Team; height:number }
 | { type: "SHIFT_SCENE_VERTICALLY"; deltaY:number }
 | { type: "LOCK_TERRAIN" } | { type: "MARK_READY_TO_EXPORT" }
 | { type: "PLACE_OBJECT"; object: {id:string;type:MapObjectType;x:number;y:number;scale:number;rotationDegrees:number;assetKey:string;collision:"NONE"|"SOLID";allowOverlap:boolean;relicSize?:"SMALL"|"MEDIUM"|"LARGE" | null} }
 | { type: "MOVE_OBJECT"; id:string;x:number;y:number } | { type:"ROTATE_OBJECT";id:string;rotationDegrees:number }
 | { type:"SCALE_OBJECT";id:string;scale:number } | { type:"REMOVE_OBJECT";id:string }
 | { type:"TRANSFORM_OBJECT";id:string;scale:number;rotationDegrees:number;y:number };
export function applyOperation(
  doc: EditorDocument,
  op: Operation,
  assetKeys?: ReadonlySet<string>,
): EditorDocument {
  const terrainPhase = doc.phase === "TERRAIN_EDITING";
  if (["DRAW_TERRAIN_PROFILE","MOVE_TERRAIN_CONTROL_POINT","SMOOTH_TERRAIN_RANGE","FLATTEN_TERRAIN_RANGE","CHANGE_FOUNDATION_HEIGHT"].includes(op.type) && !terrainPhase) throw new Error("Terrain is locked");
  const map = structuredClone(doc.map);
  if (op.type === "CHANGE_BIOME") {
    if (!isMapBiome(op.biome)) throw new Error(`Unsupported biome: ${String(op.biome)}`);
    map.biome = op.biome;
    return { ...doc, map, revision: doc.revision + 1 };
  }
  if (op.type === "LOCK_TERRAIN") {
    if (!terrainPhase) throw new Error("Terrain is already locked");
    const issues = validate({ ...doc, map }, assetKeys);
    if (issues.some(issue => issue.severity === "error")) throw new Error("Fix validation errors before locking terrain");
    return { ...doc, map, phase: "TERRAIN_LOCKED", revision: doc.revision + 1 };
  }
  if (op.type === "MARK_READY_TO_EXPORT") {
    if (!["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(doc.phase)) throw new Error("Lock terrain before export");
    const issues = validate({ ...doc, map }, assetKeys);
    if (issues.some(issue => issue.severity === "error")) throw new Error("Fix validation errors before export");
    return { ...doc, map, phase: "READY_TO_EXPORT", revision: doc.revision + 1 };
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
    if (!["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(doc.phase)) throw new Error("Lock terrain before placing decorations");
    if (map.objects.some(object => object.id === op.object.id)) throw new Error(`Object id is already used: ${op.object.id}`);
    map.objects.push(op.object);
    const placementIssue = validate({ ...doc, map }, assetKeys)
      .find(issue => issue.severity === "error" && issue.path.startsWith(`objects.${op.object.id}`));
    if (placementIssue) throw new Error(`Invalid placement: ${placementIssue.message}`);
  }
  else if (op.type === "REMOVE_OBJECT") map.objects = map.objects.filter(o=>o.id!==op.id);
  else {
    if (!["TERRAIN_LOCKED", "DECORATION_EDITING", "READY_TO_EXPORT"].includes(doc.phase)) throw new Error("Lock terrain before editing objects");
    const object = map.objects.find(o=>o.id===op.id); if(!object) throw new Error("Object not found");
    if(op.type==="MOVE_OBJECT"){object.x=op.x;object.y=op.y}
    if(op.type==="ROTATE_OBJECT") object.rotationDegrees=op.rotationDegrees
    if(op.type==="SCALE_OBJECT"){if(op.scale<=0) throw new Error("Scale must be positive");object.scale=op.scale}
    if(op.type==="TRANSFORM_OBJECT"){
      if(!Number.isFinite(op.scale)||op.scale<=0) throw new Error("Scale must be positive");
      if(!Number.isFinite(op.rotationDegrees)||!Number.isFinite(op.y)) throw new Error("Transform values must be finite");
      object.scale=op.scale;object.rotationDegrees=op.rotationDegrees;object.y=op.y;
    }
    const placementIssue = validate({ ...doc, map }, assetKeys)
      .find(issue => issue.severity === "error" && issue.path.startsWith(`objects.${op.id}`));
    if (placementIssue) throw new Error(`Invalid placement: ${placementIssue.message}`);
  }
  return {...doc,map,phase: doc.phase === "TERRAIN_LOCKED" && map.objects.length > 0 ? "DECORATION_EDITING" : doc.phase,revision:doc.revision+1};
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
  const issues: import("./map").ValidationIssue[] = []; const m=doc.map; const samples=m.terrain.samples;
  if (m.world.width!==WORLD_WIDTH||m.world.height!==WORLD_HEIGHT) issues.push({severity:"error",path:"world",message:"World must be 7200 × 4800"});
  if (!samples.length||samples[0].x!==0) issues.push({severity:"error",path:"terrain.samples[0]",message:"Profile must start at x=0",world:{x:0,y:0}});
  if (!samples.length||samples[samples.length-1].x!==m.world.width) issues.push({severity:"error",path:`terrain.samples[${samples.length-1}]`,message:"Profile must end at world width",world:{x:m.world.width,y:0}});
  for(let i=1;i<samples.length;i++) if(Math.abs(samples[i].height-samples[i-1].height)>400) issues.push({severity:"error",path:`terrain.samples[${i}]`,message:"Adjacent slope exceeds limit",world:{x:samples[i].x,y:samples[i].height}});
  for(const f of m.fortresses){const c=m.catapults.find(x=>x.owner===f.owner);if(!c||c.foundationHeight!==f.foundationHeight) issues.push({severity:"error",path:`fortresses.${f.owner}`,message:"Catapult is not synchronized",world:{x:f.x,y:f.foundationHeight}})}
  const ids = new Set<string>();
  for (const object of m.objects) {
    const path = `objects.${object.id}`;
    if (ids.has(object.id)) {
      issues.push({ severity: "error", path, message: "Duplicate object id", world: { x: object.x, y: object.y } });
    }
    if (!assetKeys) {
      issues.push({ severity: "error", path: `${path}.assetKey`, message: "Asset catalogue is not loaded" });
    } else if (!isKnownMapDecoration(object.assetKey, m.biome, assetKeys)) {
      issues.push({
        severity: "error",
        path: `${path}.assetKey`,
        message: `Unknown decoration asset '${object.assetKey}' for ${m.biome}`,
      });
    }
    ids.add(object.id);

    if (!Number.isFinite(object.x) || !Number.isFinite(object.y) ||
      !Number.isFinite(object.scale) || object.scale <= 0 ||
      !Number.isFinite(object.rotationDegrees)
    ) {
      issues.push({ severity: "error", path, message: "Position and transform values must be finite and scale must be positive" });
      continue;
    }

    if (object.relicSize) {
      validateRelicPlacement(m, object, path, issues);
    } else {
      validateGroundedPlacement(object, path, issues);
    }
  }
  return issues;
}

function validateGroundedPlacement(
  object: EditorDocument["map"]["objects"][number],
  path: string,
  issues: import("./map").ValidationIssue[],
) {
  if (object.x < 0 || object.x > WORLD_WIDTH || object.y < 0 || object.y > WORLD_HEIGHT) {
    issues.push({ severity: "error", path: `${path}.position`, message: "Decoration must be inside world bounds" });
  }
}

function validateRelicPlacement(
  map: EditorDocument["map"],
  object: EditorDocument["map"]["objects"][number],
  path: string,
  issues: import("./map").ValidationIssue[],
) {
  const footprint = relicWorldFootprint(object);
  if (!footprint) return;
  const { left, right, top, bottom } = footprint;
  if (left < 0 || right > WORLD_WIDTH || top < 0 || bottom > WORLD_HEIGHT) {
    issues.push({ severity: "error", path: `${path}.footprint`, message: "Relic footprint must stay inside world bounds" });
    return;
  }
  const terrainHeight = maximumTerrainHeightInRange(map, left, right);
  if (terrainHeight !== undefined && top < terrainHeight) {
    issues.push({ severity: "error", path: `${path}.footprint`, message: "Relic footprint must be fully inside soil" });
  }
}
