import { EditorDocument, SampleJson, terrainControlPoints } from "../domain/map";
import { terrainAssetPath } from "../domain/assets";
import { Viewport, worldToScreen } from "./transform";
const imageCache = new Map<string, HTMLImageElement>();

function image(path: string, redraw: () => void): HTMLImageElement | undefined {
  const cached = imageCache.get(path);
  if (cached) return cached.complete ? cached : undefined;
  const value = new Image();
  value.src = path;
  value.onload = redraw;
  imageCache.set(path, value);
  return undefined;
}

export function renderMap(
 canvas:HTMLCanvasElement,
 doc:EditorDocument,
 view:Viewport,
 debug=true,
 controlPreview?: SampleJson,
){
 const dpr=window.devicePixelRatio||1, rect=canvas.getBoundingClientRect(); canvas.width=rect.width*dpr;canvas.height=rect.height*dpr;
 const c=canvas.getContext("2d")!;c.setTransform(dpr,0,0,dpr,0,0);const w=rect.width,h=rect.height;
 const biome = doc.map.biome === "frost" ? "frostbound" : doc.map.biome;
 const backgroundPath = `/assets/terrain/${biome}/background_${biome === "frostbound" ? "frostbound_pass" : biome}.png`;
 const background = image(backgroundPath, () => renderMap(canvas, doc, view, debug, controlPreview));
 if (background) c.drawImage(background, 0, 0, w, h);
 else {
   c.fillStyle=doc.map.biome==="volcanic_waste"?"#241b2b":"#162537";
   c.fillRect(0,0,w,h);
 }
 const p=(x:number,y:number)=>worldToScreen(x,y,view,w,h);const samples=doc.map.terrain.samples;
 c.beginPath();samples.forEach((s,i)=>{const q=p(s.x,s.height);i?c.lineTo(q.x,q.y):c.moveTo(q.x,q.y)});const last=p(7200,4800);c.lineTo(last.x,last.y);c.lineTo(p(0,4800).x,p(0,4800).y);c.closePath();
 const soilPath = `/assets/terrain/${biome}/terrain_${biome}_soil_00.png`;
 const soil = image(soilPath, () => renderMap(canvas, doc, view, debug, controlPreview));
 c.fillStyle="#72543d";c.fill();
 if (soil) {
   c.save();
   c.clip();
   const pattern = c.createPattern(soil, "repeat");
   if (pattern) { c.fillStyle = pattern; c.fill(); }
   c.restore();
 }
 c.beginPath();samples.forEach((s,i)=>{const q=p(s.x,s.height);i?c.lineTo(q.x,q.y):c.moveTo(q.x,q.y)});c.strokeStyle="#d4bd83";c.lineWidth=4;c.stroke();
 if (doc.phase === "TERRAIN_EDITING") {
   for (const control of terrainControlPoints(doc.map)) {
     const displayed = controlPreview?.x === control.x ? controlPreview : control;
     const q = p(displayed.x, displayed.height);
     const isPreview = displayed !== control;
     c.beginPath();
     c.arc(q.x, q.y, isPreview ? 8 : 6, 0, Math.PI * 2);
     c.fillStyle = isPreview ? "#fff3b0" : "#f1cf62";
     c.fill();
     c.lineWidth = 2;
     c.strokeStyle = "#493c25";
     c.stroke();
   }
 }
 for(const z of doc.map.protectedTerrainZones){const a=p(z.minX,z.minHeight),b=p(z.maxX,z.maxHeight);c.fillStyle="rgba(55,150,255,.16)";c.fillRect(a.x,a.y,b.x-a.x,20);c.strokeStyle="#53a7ff";c.strokeRect(a.x,a.y,b.x-a.x,20)}
 for(const f of doc.map.fortresses){
   const q=p(f.x,f.foundationHeight);
   const path = f.owner === "BLUE" ? "/assets/terrain/fortress_chapter_01_left.png" : "/assets/terrain/fortress_chapter_01_right.png";
   const sprite = image(path, () => renderMap(canvas, doc, view, debug, controlPreview));
   if (sprite) c.drawImage(sprite, q.x - 110 * view.zoom, q.y - 150 * view.zoom, 220 * view.zoom, 165 * view.zoom);
   else { c.fillStyle=f.owner==="BLUE"?"#4ba7ff":"#ef6170";c.fillRect(q.x-45,q.y-90,90,90); }
   c.fillStyle="#fff";c.fillText(f.owner,q.x-20,q.y-100);
 }
 for(const cat of doc.map.catapults){
   const q=p(cat.x,cat.foundationHeight);
   const path = cat.owner === "BLUE" ? "/assets/terrain/catapult_left.png" : "/assets/terrain/catapult_right.png";
   const sprite = image(path, () => renderMap(canvas, doc, view, debug, controlPreview));
   if (sprite) c.drawImage(sprite, q.x - 55 * view.zoom, q.y - 70 * view.zoom, 110 * view.zoom, 70 * view.zoom);
   else { c.fillStyle=cat.owner==="BLUE"?"#82caff":"#ff9ca5";c.fillRect(q.x-18,q.y-55,36,55); }
 }
 for(const o of doc.map.objects){
   const q=p(o.x,o.y);c.save();c.translate(q.x,q.y);c.rotate(o.rotationDegrees*Math.PI/180);c.scale(o.scale,o.scale);
   const assetPath = terrainAssetPath(doc.map.biome, o.assetKey);
   const sprite = image(assetPath, () => renderMap(canvas, doc, view, debug, controlPreview));
   if (sprite) c.drawImage(sprite, -42, -84, 84, 84);
   else { c.fillStyle="#8dc26b";c.fillRect(-24,-24,48,48); }
   c.restore();
 }
 if(debug){c.fillStyle="#fff";c.font="12px sans-serif";c.fillText(`${doc.map.mapId} · ${doc.phase} · ${Math.round(view.zoom*100)}%`,12,20)}
}
