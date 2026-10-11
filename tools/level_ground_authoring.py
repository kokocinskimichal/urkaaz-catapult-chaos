#!/usr/bin/env python3
"""Browser-based Urkaaaz ground-profile authoring tool.

Run:
    python3 tools/level_ground_authoring.py

The tool uses only the Python standard library. It starts a local HTTP server
and opens a browser, avoiding platform-specific Tk dependencies.
"""

from __future__ import annotations

import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = "127.0.0.1"
PORT = 8765

PAGE = r"""<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Urkaaaz — authoring profilu terenu</title>
<style>
  :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
  body { margin: 0; background: #eef7fb; color: #20333d; }
  header { padding: 14px 18px 8px; }
  h1 { margin: 0 0 5px; font-size: 21px; }
  p { margin: 0; color: #526872; font-size: 13px; }
  main { display: grid; grid-template-columns: minmax(700px, 1fr) 280px; gap: 14px; padding: 0 18px 18px; }
  .panel { background: white; border: 1px solid #b9d0da; border-radius: 10px; padding: 12px; box-shadow: 0 2px 9px #54748320; }
  .canvas-wrap { aspect-ratio: 3 / 2; width: 100%; }
  canvas { width: 100%; height: 100%; display: block; border: 1px solid #6b8793; border-radius: 6px; cursor: crosshair; touch-action: none; }
  label { display: block; font-size: 12px; font-weight: 600; margin: 10px 0 4px; }
  input[type="text"], input[type="number"] { box-sizing: border-box; width: 100%; padding: 7px; border: 1px solid #a7c0ca; border-radius: 5px; }
  input[type="range"] { width: 100%; }
  button { width: 100%; margin-top: 9px; padding: 9px; border: 0; border-radius: 5px; background: #2f73c8; color: white; font-weight: 600; cursor: pointer; }
  button.secondary { background: #65808c; }
  .hint { margin-top: 12px; padding: 8px; background: #edf6fa; border-radius: 5px; line-height: 1.4; }
  #status { margin-top: 10px; min-height: 34px; color: #45616d; font-size: 12px; }
  @media (max-width: 980px) { main { grid-template-columns: 1fr; } }
</style>
</head>
<body>
<header>
  <h1>Urkaaaz — authoring profilu terenu</h1>
  <p>Narysuj przybliżoną linię ground na mapie. Eksport zapisuje próbki co 40 world units.</p>
</header>
<main>
  <section class="panel">
    <div class="canvas-wrap"><canvas id="map" width="1200" height="800"></canvas></div>
  </section>
  <aside class="panel">
    <label for="levelId">Level ID</label>
    <input id="levelId" type="text" value="chapter-1-level-1">
    <label for="leftHeight">Wysokość lewej fortecy: <span id="leftValue"></span></label>
    <input id="leftHeight" type="range" min="300" max="4600" step="10" value="3650">
    <input id="leftNumber" type="number" min="300" max="4600" step="10" value="3650">
    <label for="rightHeight">Wysokość prawej fortecy: <span id="rightValue"></span></label>
    <input id="rightHeight" type="range" min="300" max="4600" step="10" value="3650">
    <input id="rightNumber" type="number" min="300" max="4600" step="10" value="3650">
    <button id="equalize" class="secondary">Wyrównaj wysokości fortec</button>
    <button id="clear" class="secondary">Wyczyść i zacznij od nowa</button>
    <label for="notes">Notatka</label>
    <input id="notes" type="text" placeholder="Opis kształtu mapy">
    <button id="export">Pobierz definicję JSON</button>
    <div class="hint">
      Świat: <b>7200 × 4800</b><br>
      Fundamenty: <b>x=0..686</b> i <b>x=6514..7200</b><br>
      Różnica wysokości fundamentów: maks. <b>80</b><br>
      Fortece: szerokość <b>274.4</b> world units
    </div>
    <div id="status"></div>
  </aside>
</main>
<script>
const WORLD_WIDTH = 7200;
const WORLD_HEIGHT = 4800;
const STEP = 40;
const FOUNDATION_WIDTH = 686;
const FOUNDATION_DELTA = 80;
const FORTRESS_MARGIN = 490;
const FORTRESS_HALF_WIDTH = 196 * 0.70;
const FORTRESS_HEIGHT = 210 * 0.70;
const MIN_HEIGHT = 300;
const MAX_HEIGHT = 4600;

const canvas = document.getElementById("map");
const ctx = canvas.getContext("2d");
const leftHeight = document.getElementById("leftHeight");
const rightHeight = document.getElementById("rightHeight");
const leftNumber = document.getElementById("leftNumber");
const rightNumber = document.getElementById("rightNumber");
const status = document.getElementById("status");
let drawnPoints = [];
let drawing = false;

function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function foundationHeight(x) {
  if (x <= FOUNDATION_WIDTH) return Number(leftHeight.value);
  if (x >= WORLD_WIDTH - FOUNDATION_WIDTH) return Number(rightHeight.value);
  return null;
}
function geometry() {
  const scale = Math.min(canvas.width / WORLD_WIDTH, canvas.height / WORLD_HEIGHT);
  return {
    scale,
    ox: (canvas.width - WORLD_WIDTH * scale) / 2,
    oy: (canvas.height - WORLD_HEIGHT * scale) / 2,
  };
}
function worldToCanvas(x, y) {
  const g = geometry();
  return [g.ox + x * g.scale, g.oy + y * g.scale];
}
function canvasToWorld(event) {
  const rect = canvas.getBoundingClientRect();
  const px = (event.clientX - rect.left) * canvas.width / rect.width;
  const py = (event.clientY - rect.top) * canvas.height / rect.height;
  const g = geometry();
  return {
    x: clamp((px - g.ox) / g.scale, 0, WORLD_WIDTH),
    y: clamp((py - g.oy) / g.scale, MIN_HEIGHT, MAX_HEIGHT),
  };
}
function syncHeight(source, target, other) {
  let value = clamp(Number(source.value), MIN_HEIGHT, MAX_HEIGHT);
  const otherValue = Number(other.value);
  if (Math.abs(value - otherValue) > FOUNDATION_DELTA) {
    value = otherValue + Math.sign(value - otherValue) * FOUNDATION_DELTA;
  }
  source.value = value;
  target.value = value;
  draw();
}
function bindHeight(range, number, otherRange, otherNumber) {
  range.addEventListener("input", () => syncHeight(range, number, otherRange));
  number.addEventListener("change", () => syncHeight(number, range, otherRange));
}
bindHeight(leftHeight, leftNumber, rightHeight, rightNumber);
bindHeight(rightHeight, rightNumber, leftHeight, leftNumber);
document.getElementById("leftValue").textContent = "3650";
document.getElementById("rightValue").textContent = "3650";

function interpolate(points, x) {
  if (!points.length) return 3650;
  points = [...points].sort((a, b) => a.x - b.x);
  if (x <= points[0].x) return points[0].y;
  if (x >= points[points.length - 1].x) return points[points.length - 1].y;
  for (let i = 0; i < points.length - 1; i++) {
    const a = points[i], b = points[i + 1];
    if (x >= a.x && x <= b.x) {
      const t = (x - a.x) / Math.max(1, b.x - a.x);
      return a.y + (b.y - a.y) * t;
    }
  }
  return points[points.length - 1].y;
}
function heightAt(x) {
  const foundation = foundationHeight(x);
  if (foundation !== null) return foundation;
  return interpolate(drawnPoints, x);
}
function samples() {
  const result = [];
  for (let x = 0; x <= WORLD_WIDTH; x += STEP) {
    result.push({ x, height: Math.round(heightAt(x) * 100) / 100 });
  }
  return result;
}
function drawGrid(g) {
  ctx.strokeStyle = "#c3dce8";
  ctx.lineWidth = 1;
  ctx.font = "11px sans-serif";
  ctx.fillStyle = "#45616d";
  for (let x = 0; x <= WORLD_WIDTH; x += 400) {
    const [px] = worldToCanvas(x, 0);
    ctx.beginPath(); ctx.moveTo(px, g.oy); ctx.lineTo(px, g.oy + WORLD_HEIGHT * g.scale); ctx.stroke();
    ctx.fillText(String(x), px + 3, g.oy + WORLD_HEIGHT * g.scale - 6);
  }
  for (let y = 0; y <= WORLD_HEIGHT; y += 400) {
    const [, py] = worldToCanvas(0, y);
    ctx.beginPath(); ctx.moveTo(g.ox, py); ctx.lineTo(g.ox + WORLD_WIDTH * g.scale, py); ctx.stroke();
  }
}
function drawFortress(x, height, color, label) {
  const g = geometry();
  const [cx, bottom] = worldToCanvas(x, height);
  const width = FORTRESS_HALF_WIDTH * 2 * g.scale;
  const fh = FORTRESS_HEIGHT * g.scale;
  const left = cx - width / 2;
  ctx.fillStyle = color;
  ctx.strokeStyle = "#1c2730";
  ctx.lineWidth = 2;
  ctx.fillRect(left, bottom - fh, width, fh);
  ctx.strokeRect(left, bottom - fh, width, fh);
  ctx.fillRect(left + width * .16, bottom - fh * 1.22, width * .18, fh * 1.22);
  ctx.fillRect(left + width * .66, bottom - fh * 1.22, width * .18, fh * 1.22);
  ctx.fillStyle = color;
  ctx.font = "bold 12px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(label, cx, bottom - fh * 1.28);
}
function drawFoundation(x0, x1, height, color, label) {
  const [left, y] = worldToCanvas(x0, height);
  const [right] = worldToCanvas(x1, height);
  ctx.fillStyle = color;
  ctx.fillRect(left, y - 5, right - left, 10);
  ctx.font = "bold 10px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(label, (left + right) / 2, y - 10);
}
function draw() {
  const g = geometry();
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#d9efff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = "#55717e";
  ctx.lineWidth = 2;
  ctx.strokeRect(g.ox, g.oy, WORLD_WIDTH * g.scale, WORLD_HEIGHT * g.scale);
  drawGrid(g);
  drawFoundation(0, FOUNDATION_WIDTH, Number(leftHeight.value), "#4b8ed8", "BLUE FOUNDATION");
  drawFoundation(WORLD_WIDTH - FOUNDATION_WIDTH, WORLD_WIDTH, Number(rightHeight.value), "#d86868", "RED FOUNDATION");

  const points = samples().map(point => worldToCanvas(point.x, point.height));
  ctx.beginPath();
  ctx.moveTo(g.ox, g.oy + WORLD_HEIGHT * g.scale);
  points.forEach(([x, y]) => ctx.lineTo(x, y));
  ctx.lineTo(g.ox + WORLD_WIDTH * g.scale, g.oy + WORLD_HEIGHT * g.scale);
  ctx.closePath();
  ctx.fillStyle = "#9a6b38";
  ctx.fill();
  ctx.beginPath();
  points.forEach(([x, y], index) => index ? ctx.lineTo(x, y) : ctx.moveTo(x, y));
  ctx.strokeStyle = "#274d2b";
  ctx.lineWidth = Math.max(2, 3 * g.scale);
  ctx.stroke();

  ctx.fillStyle = "#152c38";
  drawnPoints.forEach(point => {
    const [x, y] = worldToCanvas(point.x, point.y);
    ctx.beginPath(); ctx.arc(x, y, 2.5, 0, Math.PI * 2); ctx.fill();
  });
  drawFortress(FORTRESS_MARGIN, Number(leftHeight.value), "#2f73c8", "BLUE");
  drawFortress(WORLD_WIDTH - FORTRESS_MARGIN, Number(rightHeight.value), "#c44747", "RED");
  document.getElementById("leftValue").textContent = `${leftHeight.value} world`;
  document.getElementById("rightValue").textContent = `${rightHeight.value} world`;
}
function addPoint(event) {
  const point = canvasToWorld(event);
  const flat = foundationHeight(point.x);
  if (flat !== null) point.y = flat;
  const previous = drawnPoints[drawnPoints.length - 1];
  if (!previous || Math.abs(point.x - previous.x) >= STEP / 3) drawnPoints.push(point);
  draw();
}
canvas.addEventListener("pointerdown", event => {
  drawing = true;
  drawnPoints = [];
  canvas.setPointerCapture(event.pointerId);
  addPoint(event);
});
canvas.addEventListener("pointermove", event => { if (drawing) addPoint(event); });
canvas.addEventListener("pointerup", event => {
  drawing = false;
  canvas.releasePointerCapture(event.pointerId);
  status.textContent = "Profil gotowy do eksportu.";
});
canvas.addEventListener("pointercancel", () => { drawing = false; });
document.getElementById("clear").addEventListener("click", () => {
  drawnPoints = [];
  status.textContent = "Profil wyczyszczony.";
  draw();
});
document.getElementById("equalize").addEventListener("click", () => {
  rightHeight.value = leftHeight.value;
  rightNumber.value = leftNumber.value;
  draw();
});
document.getElementById("export").addEventListener("click", () => {
  const levelId = document.getElementById("levelId").value.trim();
  if (!levelId) { status.textContent = "Podaj Level ID przed eksportem."; return; }
  const left = Number(leftHeight.value), right = Number(rightHeight.value);
  const payload = {
    levelId, worldWidth: WORLD_WIDTH, worldHeight: WORLD_HEIGHT, sampleStep: STEP,
    terrainSamples: samples(),
    fortresses: [
      { owner: "BLUE", x: FORTRESS_MARGIN, foundationHeight: left, halfWidth: FORTRESS_HALF_WIDTH },
      { owner: "RED", x: WORLD_WIDTH - FORTRESS_MARGIN, foundationHeight: right, halfWidth: FORTRESS_HALF_WIDTH }
    ],
    protectedTerrainZones: [
      { id: `${levelId}-blue-foundation`, minX: 0, maxX: FOUNDATION_WIDTH, minHeight: left - 80, maxHeight: left + 80 },
      { id: `${levelId}-red-foundation`, minX: WORLD_WIDTH - FOUNDATION_WIDTH, maxX: WORLD_WIDTH, minHeight: right - 80, maxHeight: right + 80 }
    ],
    notes: document.getElementById("notes").value.trim()
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], {type: "application/json"});
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${levelId}-ground.json`;
  link.click();
  URL.revokeObjectURL(link.href);
  status.textContent = "Pobrano plik JSON.";
});
window.addEventListener("resize", draw);
draw();
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path not in ("/", "/index.html"):
            self.send_error(404)
            return
        body = PAGE.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format: str, *_args: object) -> None:
        return


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    url = f"http://{HOST}:{PORT}/"
    print(f"Urkaaaz level authoring: {url}")
    print("Zatrzymaj aplikację przez Ctrl+C.")
    threading.Timer(0.25, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nZamykanie aplikacji.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
