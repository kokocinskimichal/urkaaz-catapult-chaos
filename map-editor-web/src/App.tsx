import React, { useEffect, useRef, useState } from "react";
import { decode, encode } from "./format/codec";
import { paletteAssetsForBiome, parseAssetIndex } from "./domain/assets";
import {
  defaultMap,
  EditorDocument,
  MAP_BIOMES,
  maximumTerrainHeightInRange,
  SampleJson,
  terrainControlPoints,
  terrainHeightAt,
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from "./domain/map";
import { applyOperation, validate } from "./domain/operations";
import { useEditorStore } from "./state/store";
import { renderMap } from "./rendering/canvas";
import { screenToWorld, worldToScreen } from "./rendering/transform";
import "./ui/styles.css";

const AUTOSAVE = "urkaaz-map-editor-autosave";
type WorldPoint = { x: number; y: number };
type CursorPosition = WorldPoint & { screenX: number; screenY: number };
type ControlDrag = { x: number; originalHeight: number; height: number };
const ASSET_PALETTE_GROUPS = [
  { category: "tree", label: "Trees" },
  { category: "rock", label: "Rocks" },
  { category: "prop", label: "Props" },
  { category: "relic", label: "Relics" },
] as const;

function findControlAt(
  point: WorldPoint,
  document: EditorDocument,
  view: { zoom: number; panX: number; panY: number },
  width: number,
  height: number,
): SampleJson | undefined {
  return terrainControlPoints(document.map).find(control => {
    const screen = worldToScreen(control.x, control.height, view, width, height);
    return Math.hypot(screen.x - point.x, screen.y - point.y) <= 12;
  });
}

export default function App() {
  const [registeredAssetKeys, setRegisteredAssetKeys] = useState<ReadonlySet<string>>();
  const [assetCatalogError, setAssetCatalogError] = useState<string>();
  const { state, operation, undo, redo, load } = useEditorStore(registeredAssetKeys);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [view, setView] = useState({ zoom: 1, panX: 0, panY: 0 });
  const [issues, setIssues] = useState(validate(state.present));
  const [controlDrag, setControlDrag] = useState<ControlDrag | null>(null);
  const [showCursorCoordinates, setShowCursorCoordinates] = useState(false);
  const [cursorPosition, setCursorPosition] = useState<CursorPosition | null>(null);
  const [status, setStatus] = useState("Unsaved");
  const nextObjectId = useRef(0);
  const restored = useRef(false);

  useEffect(() => {
    let cancelled = false;
    fetch("/assets/terrain/asset-index.txt")
      .then(response => {
        if (!response.ok) throw new Error(`Asset index request failed (${response.status})`);
        return response.text();
      })
      .then(parseAssetIndex)
      .then(assetKeys => {
        if (!cancelled) setRegisteredAssetKeys(assetKeys);
      })
      .catch(error => {
        if (!cancelled) {
          setAssetCatalogError(error instanceof Error ? error.message : String(error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (restored.current) return;
    restored.current = true;
    const saved = localStorage.getItem(AUTOSAVE);
    if (!saved) return;
    try {
      const map = decode(saved);
      load({ map, phase: "TERRAIN_EDITING", revision: 0 });
      setStatus("Restored local draft");
    } catch (error) {
      setStatus(error instanceof Error ? `Autosave could not be restored: ${error.message}` : String(error));
    }
  }, [load]);

  useEffect(() => {
    if (canvas.current) {
      const displayDocument = controlDrag
        ? applyOperation(state.present, {
          type: "MOVE_TERRAIN_CONTROL_POINT",
          x: controlDrag.x,
          height: controlDrag.height,
        }, registeredAssetKeys)
        : state.present;
      renderMap(
        canvas.current,
        displayDocument,
        view,
        true,
        controlDrag ? { x: controlDrag.x, height: controlDrag.height } : undefined,
        registeredAssetKeys,
      );
    }
    const validation = validate(state.present, registeredAssetKeys);
    setIssues(validation);
    localStorage.setItem(AUTOSAVE, encode(state.present));
    setStatus("Autosaved");
  }, [state.present, view, controlDrag, registeredAssetKeys]);

  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "z") {
        event.preventDefault();
        event.shiftKey ? redo() : undo();
      }
      if (event.key === "Escape") setControlDrag(null);
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [redo, undo]);

  const newMap = () => load({ map: defaultMap(), phase: "TERRAIN_EDITING", revision: 0 });
  const resetMap = () => {
    if (!window.confirm("Reset the entire map to a new empty map? This cannot be undone.")) return;
    load({
      map: defaultMap("web-map", state.present.map.biome),
      phase: "TERRAIN_EDITING",
      revision: 0,
    });
    setStatus("Map reset");
  };

  const fileImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    file.text()
      .then(text => {
        const map = decode(text);
        load({ map, phase: "TERRAIN_EDITING", revision: 0 });
      })
      .catch(error => setStatus(error instanceof Error ? error.message : String(error)));
    event.target.value = "";
  };

  const exportFile = (final = false) => {
    if (final && validate(state.present, registeredAssetKeys).some(issue => issue.severity === "error")) {
      setStatus("Fix validation errors before export");
      return;
    }
    const anchor = document.createElement("a");
    anchor.href = URL.createObjectURL(
      new Blob([encode(state.present)], { type: "application/json" }),
    );
    anchor.download = `${state.present.map.mapId}.json`;
    anchor.click();
    URL.revokeObjectURL(anchor.href);
    setStatus(final ? "Exported" : "Draft downloaded");
  };

  const assetPalette = registeredAssetKeys
    ? paletteAssetsForBiome(registeredAssetKeys, state.present.map.biome)
    : { tree: [], rock: [], prop: [], relic: [] };
  const canPlaceAssets = ["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(state.present.phase);
  const cursorCoordinatesStyle = cursorPosition
    ? {
      left: Math.max(
        0,
        Math.min(cursorPosition.screenX + 12, (canvas.current?.clientWidth ?? 0) - 150),
      ),
      top: Math.max(
        0,
        Math.min(cursorPosition.screenY + 12, (canvas.current?.clientHeight ?? 0) - 30),
      ),
    }
    : undefined;

  const pointer = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const element = canvas.current;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      setCursorPosition(null);
      return;
    }
    const screen = { x: event.clientX - rect.left, y: event.clientY - rect.top };
    const world = screenToWorld(screen.x, screen.y, view, rect.width, rect.height);
    if (event.type === "pointermove") {
      setCursorPosition({
        ...world,
        screenX: screen.x,
        screenY: screen.y,
      });
      if (controlDrag) {
        setControlDrag({
          ...controlDrag,
          height: Math.max(0, Math.min(WORLD_HEIGHT, world.y)),
        });
      }
      return;
    }
    if (event.type === "pointerdown") {
      if (state.present.phase !== "TERRAIN_EDITING") return;
      const control = findControlAt(screen, state.present, view, rect.width, rect.height);
      if (control) {
        element.setPointerCapture(event.pointerId);
        setControlDrag({ x: control.x, originalHeight: control.height, height: control.height });
      }
      return;
    }
    if (event.type === "pointerup" && controlDrag) {
      if (controlDrag.height !== controlDrag.originalHeight) {
        operation({
          type: "MOVE_TERRAIN_CONTROL_POINT",
          x: controlDrag.x,
          height: controlDrag.height,
        });
      }
      setControlDrag(null);
      if (element.hasPointerCapture(event.pointerId)) element.releasePointerCapture(event.pointerId);
    }
  };

  const dropAsset = (event: React.DragEvent<HTMLCanvasElement>) => {
    if (!canPlaceAssets) return;
    event.preventDefault();
    const assetKey = event.dataTransfer.getData("application/x-urkaaz-map-asset");
    const asset = Object.values(assetPalette).flat().find(item => item.assetKey === assetKey);
    if (!asset) {
      setStatus(`Unknown or unavailable map asset: ${assetKey}`);
      return;
    }
    const element = canvas.current;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const world = screenToWorld(
      event.clientX - rect.left,
      event.clientY - rect.top,
      view,
      rect.width,
      rect.height,
    );
    const map = state.present.map;
    if (world.x < 0 || world.x > WORLD_WIDTH || world.y < 0 || world.y > WORLD_HEIGHT) {
      setStatus("Assets must be placed inside the map bounds");
      return;
    }

    let y: number;
    if (asset.relicSize) {
      const halfWidth = 56;
      const halfHeight = 40;
      if (world.x - halfWidth < 0 || world.x + halfWidth > WORLD_WIDTH) {
        setStatus("Relic must fit inside the map bounds");
        return;
      }
      const soilTop = maximumTerrainHeightInRange(map, world.x - halfWidth, world.x + halfWidth);
      if (soilTop === undefined) {
        setStatus("Cannot place a relic without a terrain profile");
        return;
      }
      y = Math.max(world.y, soilTop + halfHeight);
      if (y + halfHeight > WORLD_HEIGHT) {
        setStatus("Relic must fit completely inside the soil");
        return;
      }
    } else {
      const surface = terrainHeightAt(map, world.x);
      const left = terrainHeightAt(map, world.x - map.terrain.sampleStep);
      const right = terrainHeightAt(map, world.x + map.terrain.sampleStep);
      if (surface === undefined || left === undefined || right === undefined) {
        setStatus("Cannot place an asset without a terrain profile");
        return;
      }
      if (Math.abs(left - surface) > 1 || Math.abs(right - surface) > 1) {
        setStatus("Trees, rocks, and props can only be placed on flat terrain");
        return;
      }
      y = surface;
    }

    operation({
      type: "PLACE_OBJECT",
      object: {
        id: `map-asset-${Date.now()}-${nextObjectId.current++}`,
        type: "DECORATION",
        x: world.x,
        y,
        scale: 1,
        rotationDegrees: 0,
        assetKey: asset.assetKey,
        collision: "NONE",
        allowOverlap: false,
        ...(asset.relicSize ? { relicSize: asset.relicSize } : {}),
      },
    });
  };

  const wheel = (event: React.WheelEvent) => {
    event.preventDefault();
    setView(current => ({
      ...current,
      zoom: Math.max(0.35, Math.min(4, current.zoom * (event.deltaY < 0 ? 1.1 : 0.9))),
    }));
  };

  return (
    <main>
      <header>
        <strong>Urkaaaz Map Editor</strong>
        <button onClick={newMap}>New</button>
        <button onClick={resetMap}>Reset</button>
        <label className="button">
          Open
          <input type="file" accept=".json,application/json" onChange={fileImport} />
        </label>
        <button onClick={() => exportFile(false)}>Save draft</button>
        <button onClick={() => exportFile(true)}>Export</button>
        <button onClick={undo}>Undo</button>
        <button onClick={redo}>Redo</button>
        <button onClick={() => setView({ zoom: 1, panX: 0, panY: 0 })}>Fit</button>
        <span className="status">{status}</span>
      </header>
      <section className="layout">
        <aside>
          <h3>Biome</h3>
          <select
            aria-label="Biome"
            value={state.present.map.biome}
            onChange={event => {
              const selected = MAP_BIOMES.find(biome => biome.value === event.currentTarget.value);
              if (selected) operation({ type: "CHANGE_BIOME", biome: selected.value });
            }}
          >
            {MAP_BIOMES.map(biome => (
              <option key={biome.value} value={biome.value}>{biome.label}</option>
            ))}
          </select>
          <p>Fortress and catapult sprites are shared between biomes.</p>
          <h3>Debug</h3>
          <button
            className={showCursorCoordinates ? "active" : ""}
            aria-pressed={showCursorCoordinates}
            onClick={() => setShowCursorCoordinates(value => !value)}
          >
            Cursor coordinates: {showCursorCoordinates ? "On" : "Off"}
          </button>
          <button
            disabled={state.present.phase !== "TERRAIN_EDITING"}
            onClick={() => operation({ type: "LOCK_TERRAIN" })}
          >
            Lock terrain
          </button>
          <p>World<br />{WORLD_WIDTH} × {WORLD_HEIGHT}</p>
        </aside>
        <div className="canvas-wrap">
          <canvas
            ref={canvas}
            onPointerDown={pointer}
            onPointerMove={pointer}
            onPointerUp={pointer}
            onPointerCancel={() => setControlDrag(null)}
            onPointerLeave={() => setCursorPosition(null)}
            onDragOver={event => {
              if (!canPlaceAssets) return;
              event.preventDefault();
              event.dataTransfer.dropEffect = "copy";
            }}
            onDrop={dropAsset}
            onWheel={wheel}
          />
          {showCursorCoordinates && cursorPosition && cursorCoordinatesStyle && (
            <div className="cursor-coordinates" style={cursorCoordinatesStyle}>
              X: {cursorPosition.x.toFixed(1)} · Y: {cursorPosition.y.toFixed(1)}
            </div>
          )}
          {controlDrag && (
            <div className="hint">Adjusting contour point · release to commit</div>
          )}
        </div>
        <aside>
          <h3>Validation</h3>
          {issues.length === 0 ? (
            <p className="ok">No issues</p>
          ) : (
            <ul>
              {issues.map((issue, index) => (
                <li className={issue.severity} key={`${issue.path}-${index}`}>
                  {issue.path}: {issue.message}
                </li>
              ))}
            </ul>
          )}
          <h3>Assets</h3>
          <p>Biome: {state.present.map.biome}</p>
          {assetCatalogError && <p className="error">Asset catalogue unavailable: {assetCatalogError}</p>}
          {!assetCatalogError && !registeredAssetKeys && <p>Loading asset catalogue…</p>}
          <p>{canPlaceAssets ? "Drag an asset onto the map." : "Lock terrain before placing assets."}</p>
          {ASSET_PALETTE_GROUPS.map(group => (
            <section className="asset-category" key={group.category}>
              <h4>{group.label}</h4>
              {assetPalette[group.category].length === 0 ? (
                <p className="asset-empty">No assets for this biome.</p>
              ) : (
                <div className="asset-grid">
                  {assetPalette[group.category].map(asset => (
                    <button
                      className="asset-item"
                      key={asset.assetKey}
                      draggable={canPlaceAssets}
                      disabled={!canPlaceAssets}
                      title={asset.label}
                      aria-label={`Drag ${group.label.toLowerCase()} asset ${asset.label} onto the map`}
                      onDragStart={event => {
                        event.dataTransfer.setData("application/x-urkaaz-map-asset", asset.assetKey);
                        event.dataTransfer.effectAllowed = "copy";
                      }}
                    >
                      <img src={asset.thumbnailPath} alt="" draggable={false} />
                      <span>{asset.label}</span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          ))}
        </aside>
      </section>
      <footer>{state.message ?? "Drag yellow contour nodes to shape terrain · Lock terrain to finish"}</footer>
    </main>
  );
}
