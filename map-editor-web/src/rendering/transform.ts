export interface Viewport { zoom:number; panX:number; panY:number }
export interface ScreenPoint { x:number; y:number }
export function worldToScreen(x:number,y:number,v:Viewport,w:number,h:number){const scale=Math.min(w/7200,h/4800)*v.zoom;return{x:(x-3600)*scale+w/2+v.panX,y:(y-2400)*scale+h/2+v.panY}}
export function screenToWorld(x:number,y:number,v:Viewport,w:number,h:number){const scale=Math.min(w/7200,h/4800)*v.zoom;return{x:(x-w/2-v.panX)/scale+3600,y:(y-h/2-v.panY)/scale+2400}}

export function rotatedRectangleCorners(
  center: ScreenPoint,
  width: number,
  height: number,
  rotationDegrees: number,
): ScreenPoint[] {
  const radians = rotationDegrees * Math.PI / 180;
  return [
    { x: -width / 2, y: -height / 2 },
    { x: width / 2, y: -height / 2 },
    { x: width / 2, y: height / 2 },
    { x: -width / 2, y: height / 2 },
  ].map(point => ({
    x: center.x + point.x * Math.cos(radians) - point.y * Math.sin(radians),
    y: center.y + point.x * Math.sin(radians) + point.y * Math.cos(radians),
  }));
}

export function transformFromCornerDrag(
  initialPointer: ScreenPoint,
  currentPointer: ScreenPoint,
  center: ScreenPoint,
  initialScale: number,
  initialRotationDegrees: number,
): { scale: number; rotationDegrees: number } {
  const initialX = initialPointer.x - center.x;
  const initialY = initialPointer.y - center.y;
  const currentX = currentPointer.x - center.x;
  const currentY = currentPointer.y - center.y;
  const initialDistance = Math.hypot(initialX, initialY);
  const currentDistance = Math.hypot(currentX, currentY);
  const initialAngle = Math.atan2(initialY, initialX);
  const currentAngle = Math.atan2(currentY, currentX);
  const angleDelta = Math.atan2(
    Math.sin(currentAngle - initialAngle),
    Math.cos(currentAngle - initialAngle),
  );

  return {
    scale: Math.max(0.1, initialScale * currentDistance / Math.max(1, initialDistance)),
    rotationDegrees: initialRotationDegrees + angleDelta * 180 / Math.PI,
  };
}
