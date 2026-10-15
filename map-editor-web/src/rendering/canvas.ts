import { EditorDocument, SampleJson, terrainControlPoints, terrainHeightAt, WORLD_WIDTH } from "../domain/map";
import { relicAssetPath, terrainAssetPath, terrainThemeAssetPaths } from "../domain/assets";
import { Viewport, worldToScreen } from "./transform";
const imageCache = new Map<string, HTMLImageElement>();
const SOIL_TILE_WIDTH = 256;
const FORTRESS_VERTICAL_OFFSET = 35;
const CATAPULT_VERTICAL_OFFSET = -200;
const CAP_SLICE_COUNT = 96;
const CAP_TILE_WORLD_WIDTH = 450;

function image(path: string, redraw: () => void): HTMLImageElement | undefined {
  const cached = imageCache.get(path);
  if (cached) return cached.complete ? cached : undefined;
  const value = new Image();
  value.src = path;
  value.onload = redraw;
  imageCache.set(path, value);
  return undefined;
}

function drawDeformedCap(
  c: CanvasRenderingContext2D,
  cap: HTMLImageElement,
  doc: EditorDocument,
  view: Viewport,
  width: number,
  height: number,
) {
  const sliceWorldWidth = CAP_TILE_WORLD_WIDTH / CAP_SLICE_COUNT;
  const sourceSliceWidth = Math.max(1, Math.floor(cap.width / CAP_SLICE_COUNT));
  for (let sliceIndex = 0; sliceIndex * sliceWorldWidth <= WORLD_WIDTH + sliceWorldWidth; sliceIndex += 1) {
    const worldX = sliceIndex * sliceWorldWidth;
    const surfaceY = terrainHeightAt(doc.map, worldX);
    if (surfaceY === undefined) continue;
    const sourceLeft = Math.min(
      (sliceIndex % CAP_SLICE_COUNT) * sourceSliceWidth,
      cap.width - sourceSliceWidth,
    );
    const left = worldToScreen(worldX, surfaceY, view, width, height);
    const right = worldToScreen(worldX + sliceWorldWidth, surfaceY, view, width, height);
    const top = worldToScreen(worldX, surfaceY - 48, view, width, height);
    const bottom = worldToScreen(worldX, surfaceY + 56, view, width, height);
    c.drawImage(
      cap,
      sourceLeft,
      0,
      sourceSliceWidth,
      cap.height,
      left.x,
      top.y,
      right.x - left.x + 1,
      bottom.y - top.y,
    );
  }
}

export function renderMap(
 canvas:HTMLCanvasElement,
 doc:EditorDocument,
 view:Viewport,
 debug=true,
 controlPreview?: SampleJson,
 assetKeys?: ReadonlySet<string>,
){
 const dpr=window.devicePixelRatio||1, rect=canvas.getBoundingClientRect(); canvas.width=rect.width*dpr;canvas.height=rect.height*dpr;
 const c=canvas.getContext("2d")!;c.setTransform(dpr,0,0,dpr,0,0);const w=rect.width,h=rect.height;
 const themeAssets = terrainThemeAssetPaths(doc.map.biome);
 const background = image(themeAssets.background, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
 if (background) c.drawImage(background, 0, 0, w, h);
 else {
   c.fillStyle="#162537";
   c.fillRect(0,0,w,h);
 }
 const p=(x:number,y:number)=>worldToScreen(x,y,view,w,h);const samples=doc.map.terrain.samples;
 c.beginPath();samples.forEach((s,i)=>{const q=p(s.x,s.height);i?c.lineTo(q.x,q.y):c.moveTo(q.x,q.y)});const last=p(7200,4800);c.lineTo(last.x,last.y);c.lineTo(p(0,4800).x,p(0,4800).y);c.closePath();
 const soil = image(themeAssets.soil, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
 const cap = image(themeAssets.cap, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
 c.fillStyle="#72543d";c.fill();
 if (soil) {
   c.save();
   c.clip();
   const pattern = c.createPattern(soil, "repeat");
   if (pattern) {
     const scale = (SOIL_TILE_WIDTH * view.zoom) / soil.width;
     pattern.setTransform(new DOMMatrix([scale, 0, 0, scale, 0, 0]));
     c.fillStyle = pattern;
     c.fill();
   }
   c.restore();
 }
 c.beginPath();samples.forEach((s,i)=>{const q=p(s.x,s.height);i?c.lineTo(q.x,q.y):c.moveTo(q.x,q.y)});c.strokeStyle="#d4bd83";c.lineWidth=4;c.stroke();
 for(const z of doc.map.protectedTerrainZones){const a=p(z.minX,z.minHeight),b=p(z.maxX,z.maxHeight);c.fillStyle="rgba(55,150,255,.16)";c.fillRect(a.x,a.y,b.x-a.x,20);c.strokeStyle="#53a7ff";c.strokeRect(a.x,a.y,b.x-a.x,20)}
 for(const cat of doc.map.catapults){
   const q=p(cat.x,cat.foundationHeight+FORTRESS_VERTICAL_OFFSET+CATAPULT_VERTICAL_OFFSET);
   const path = cat.owner === "BLUE" ? "/assets/terrain/catapult_left.png" : "/assets/terrain/catapult_right.png";
   const sprite = image(path, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
   if (sprite) c.drawImage(sprite, q.x - 44 * view.zoom, q.y - 56 * view.zoom, 88 * view.zoom, 56 * view.zoom);
   else { c.fillStyle=cat.owner==="BLUE"?"#82caff":"#ff9ca5";c.fillRect(q.x-14.4,q.y-44,28.8,44); }
 }
 for(const f of doc.map.fortresses){
   const q=p(f.x,f.foundationHeight+FORTRESS_VERTICAL_OFFSET);
   const path = f.owner === "BLUE" ? "/assets/terrain/fortress_chapter_01_left.png" : "/assets/terrain/fortress_chapter_01_right.png";
   const sprite = image(path, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
   if (sprite) c.drawImage(sprite, q.x - 110 * view.zoom, q.y - 150 * view.zoom, 220 * view.zoom, 165 * view.zoom);
   else { c.fillStyle=f.owner==="BLUE"?"#4ba7ff":"#ef6170";c.fillRect(q.x-45,q.y-90,90,90); }
   c.fillStyle="#fff";c.fillText(f.owner,q.x-20,q.y-100);
 }
 for (const object of doc.map.objects.filter(item => item.relicSize == null)) {
   const assetPath = terrainAssetPath(doc.map.biome, object.assetKey);
   const sprite = image(assetPath, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
   if (!sprite) continue;
   const baselineY = object.y + 12;
   const center = p(object.x, baselineY);
   const halfWidth = 94 * object.scale / 2;
   const left = p(object.x - halfWidth, baselineY);
   const right = p(object.x + halfWidth, baselineY);
   const renderWidth = right.x - left.x;
   const renderHeight = renderWidth * sprite.height / Math.max(1, sprite.width);
   c.save();
   c.translate(center.x, center.y - renderHeight / 2);
   c.rotate(object.rotationDegrees * Math.PI / 180);
   c.drawImage(sprite, -renderWidth / 2, -renderHeight / 2, renderWidth, renderHeight);
   c.restore();
 }
 if (cap && doc.phase !== "TERRAIN_EDITING") drawDeformedCap(c, cap, doc, view, w, h);
 if (doc.phase === "TERRAIN_EDITING") {
   for (const control of terrainControlPoints(doc.map)) {
     const displayed = controlPreview?.x === control.x ? controlPreview : control;
     const point = p(displayed.x, displayed.height);
     const isPreview = displayed !== control;
     c.beginPath();
     c.arc(point.x, point.y, isPreview ? 8 : 6, 0, Math.PI * 2);
     c.fillStyle = isPreview ? "#fff3b0" : "#f1cf62";
     c.fill();
     c.lineWidth = 2;
     c.strokeStyle = "#493c25";
     c.stroke();
   }
 }
 for (const relic of doc.map.objects.filter(item => item.relicSize != null)) {
   const assetPath = relicAssetPath(doc.map.biome, relic.assetKey, assetKeys ?? new Set());
   if (!assetPath) continue;
   const sprite = image(assetPath, () => renderMap(canvas, doc, view, debug, controlPreview, assetKeys));
   if (!sprite) continue;
   const size = ({ SMALL: 76, MEDIUM: 112, LARGE: 176 }[relic.relicSize ?? "SMALL"]) * relic.scale;
   const center = p(relic.x, relic.y);
   const sizeTopLeft = p(relic.x - size / 2, relic.y - size / 2);
   const sizeBottomRight = p(relic.x + size / 2, relic.y + size / 2);
   const renderSize = sizeBottomRight.x - sizeTopLeft.x;
   c.save();
   c.translate(center.x, center.y);
   c.rotate(relic.rotationDegrees * Math.PI / 180);
   c.drawImage(sprite, -renderSize / 2, -renderSize / 2, renderSize, renderSize);
   c.restore();
 }
 if(debug){c.fillStyle="#fff";c.font="12px sans-serif";c.fillText(`${doc.map.mapId} · ${doc.phase} · ${Math.round(view.zoom*100)}%`,12,20)}
}
