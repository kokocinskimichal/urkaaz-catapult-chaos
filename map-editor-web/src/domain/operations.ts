import {
  EditorDocument,
  MapObjectType,
  Team,
  TERRAIN_CONTROL_SPACING,
  WORLD_HEIGHT,
  WORLD_WIDTH,
  terrainControlPoints,
} from "./map";
import { isKnownMapDecoration } from "./assets";
export type Operation =
 | { type: "DRAW_TERRAIN_PROFILE"; points: {x:number;height:number}[] }
 | { type: "MOVE_TERRAIN_CONTROL_POINT"; x:number; height:number }
 | { type: "SMOOTH_TERRAIN_RANGE"; minX:number; maxX:number }
 | { type: "FLATTEN_TERRAIN_RANGE"; minX:number; maxX:number; height?:number }
 | { type: "CHANGE_FOUNDATION_HEIGHT"; owner:Team; height:number }
 | { type: "LOCK_TERRAIN" } | { type: "MARK_READY_TO_EXPORT" }
 | { type: "PLACE_OBJECT"; object: {id:string;type:MapObjectType;x:number;y:number;scale:number;rotationDegrees:number;assetKey:string;collision:"NONE"|"SOLID";allowOverlap:boolean} }
 | { type: "MOVE_OBJECT"; id:string;x:number;y:number } | { type:"ROTATE_OBJECT";id:string;rotationDegrees:number }
 | { type:"SCALE_OBJECT";id:string;scale:number } | { type:"REMOVE_OBJECT";id:string };
export function applyOperation(
  doc: EditorDocument,
  op: Operation,
  assetKeys?: ReadonlySet<string>,
): EditorDocument {
  const terrainPhase = doc.phase === "TERRAIN_EDITING";
  if (["DRAW_TERRAIN_PROFILE","MOVE_TERRAIN_CONTROL_POINT","SMOOTH_TERRAIN_RANGE","FLATTEN_TERRAIN_RANGE","CHANGE_FOUNDATION_HEIGHT"].includes(op.type) && !terrainPhase) throw new Error("Terrain is locked");
  const map = structuredClone(doc.map);
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
    const controlSpacing = Math.max(
      map.terrain.sampleStep,
      Math.round(TERRAIN_CONTROL_SPACING / map.terrain.sampleStep) * map.terrain.sampleStep,
    );
    const influenceRadius = controlSpacing * 3;
    map.terrain.samples = map.terrain.samples.map(sample => {
      if (map.protectedTerrainZones.some(zone => sample.x >= zone.minX && sample.x <= zone.maxX)) return sample;
      const distance = Math.abs(sample.x - op.x);
      if (distance >= influenceRadius) return sample;
      const crossesFoundation = map.protectedTerrainZones.some(zone =>
        Math.min(sample.x, op.x) < zone.maxX && Math.max(sample.x, op.x) > zone.minX,
      );
      if (crossesFoundation) return sample;
      const ratio = distance / influenceRadius;
      const falloff = 1 - (3 * ratio * ratio - 2 * ratio * ratio * ratio);
      return { ...sample, height: sample.height + displacement * falloff };
    });
  } else if (op.type === "SMOOTH_TERRAIN_RANGE" || op.type === "FLATTEN_TERRAIN_RANGE") {
    const inside = map.terrain.samples.filter(s=>s.x>=op.minX&&s.x<=op.maxX);
    if (inside.some(s=>map.protectedTerrainZones.some(z=>s.x>=z.minX&&s.x<=z.maxX))) throw new Error("Range intersects protected foundation");
    const h = op.type === "FLATTEN_TERRAIN_RANGE" ? (op.height ?? inside.reduce((a,b)=>a+b.height,0)/Math.max(1,inside.length)) : undefined;
    map.terrain.samples = map.terrain.samples.map((s,i,a) => s.x < op.minX || s.x > op.maxX ? s : { ...s, height: h ?? (a[Math.max(0,i-1)].height+a[Math.min(a.length-1,i+1)].height)/2 });
  } else if (op.type === "CHANGE_FOUNDATION_HEIGHT") {
    const fortress = map.fortresses.find(f=>f.owner===op.owner); const zone = fortress && map.protectedTerrainZones.find(z=>z.id===fortress.protectedZoneId);
    if (!fortress || !zone) throw new Error("Foundation contract is incomplete");
    fortress.foundationHeight = op.height; zone.minHeight = op.height; zone.maxHeight = op.height;
    map.terrain.samples = map.terrain.samples.map(s=>s.x>=zone.minX&&s.x<=zone.maxX?{...s,height:op.height}:s);
    const catapult = map.catapults.find(c=>c.owner===op.owner); if(catapult) catapult.foundationHeight=op.height;
  } else if (op.type === "PLACE_OBJECT") {
    if (!["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(doc.phase)) throw new Error("Lock terrain before placing decorations");
    if (map.objects.some(object => object.id === op.object.id)) throw new Error(`Object id is already used: ${op.object.id}`);
    map.objects.push(op.object);
  }
  else if (op.type === "REMOVE_OBJECT") map.objects = map.objects.filter(o=>o.id!==op.id);
  else {
    if (!["TERRAIN_LOCKED", "DECORATION_EDITING", "READY_TO_EXPORT"].includes(doc.phase)) throw new Error("Lock terrain before editing objects");
    const object = map.objects.find(o=>o.id===op.id); if(!object) throw new Error("Object not found");
    if(op.type==="MOVE_OBJECT"){object.x=op.x;object.y=op.y}
    if(op.type==="ROTATE_OBJECT") object.rotationDegrees=op.rotationDegrees
    if(op.type==="SCALE_OBJECT"){if(op.scale<=0) throw new Error("Scale must be positive");object.scale=op.scale}
  }
  return {...doc,map,phase: doc.phase === "TERRAIN_LOCKED" && map.objects.length > 0 ? "DECORATION_EDITING" : doc.phase,revision:doc.revision+1};
}

export function validate(doc: EditorDocument, assetKeys?: ReadonlySet<string>) {
  const issues: import("./map").ValidationIssue[] = []; const m=doc.map; const samples=m.terrain.samples;
  if (m.world.width!==WORLD_WIDTH||m.world.height!==WORLD_HEIGHT) issues.push({severity:"error",path:"world",message:"World must be 7200 × 4800"});
  if (!samples.length||samples[0].x!==0) issues.push({severity:"error",path:"terrain.samples[0]",message:"Profile must start at x=0",world:{x:0,y:0}});
  if (!samples.length||samples[samples.length-1].x!==m.world.width) issues.push({severity:"error",path:`terrain.samples[${samples.length-1}]`,message:"Profile must end at world width",world:{x:m.world.width,y:0}});
  for(let i=1;i<samples.length;i++) if(Math.abs(samples[i].height-samples[i-1].height)>400) issues.push({severity:"error",path:`terrain.samples[${i}]`,message:"Adjacent slope exceeds limit",world:{x:samples[i].x,y:samples[i].height}});
  for(const f of m.fortresses){const c=m.catapults.find(x=>x.owner===f.owner);if(!c||c.foundationHeight!==f.foundationHeight) issues.push({severity:"error",path:`fortresses.${f.owner}`,message:"Catapult is not synchronized",world:{x:f.x,y:f.foundationHeight}})}
  const ids=new Set<string>(); for(const o of m.objects){
    if(ids.has(o.id))issues.push({severity:"error",path:`objects.${o.id}`,message:"Duplicate object id",world:{x:o.x,y:o.y}});
    if (!assetKeys) issues.push({severity:"error",path:`objects.${o.id}.assetKey`,message:"Asset catalogue is not loaded"});
    else if (!isKnownMapDecoration(o.assetKey, m.biome, assetKeys)) issues.push({severity:"error",path:`objects.${o.id}.assetKey`,message:`Unknown decoration asset '${o.assetKey}' for ${m.biome}`});
    ids.add(o.id);
  }
  return issues;
}
