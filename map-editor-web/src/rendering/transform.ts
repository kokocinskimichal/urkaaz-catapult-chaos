export interface Viewport { zoom:number; panX:number; panY:number }
export function worldToScreen(x:number,y:number,v:Viewport,w:number,h:number){const scale=Math.min(w/7200,h/4800)*v.zoom;return{x:(x-3600)*scale+w/2+v.panX,y:(y-2400)*scale+h/2+v.panY}}
export function screenToWorld(x:number,y:number,v:Viewport,w:number,h:number){const scale=Math.min(w/7200,h/4800)*v.zoom;return{x:(x-w/2-v.panX)/scale+3600,y:(y-h/2-v.panY)/scale+2400}}
