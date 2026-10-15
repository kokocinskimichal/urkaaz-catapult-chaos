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
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from "./domain/map";
import { applyOperation, validate } from "./domain/operations";
import { useEditorStore } from "./state/store";
import {
  MAP_DECORATION_GROUND_SINK,
  mapDecorationAspectRatio,
  mapDecorationRenderSize,
  renderMap,
} from "./rendering/canvas";
import {
  rotatedRectangleCorners,
  screenToWorld,
  transformFromCornerDrag,
  worldToScreen,
} from "./rendering/transform";
import "./ui/styles.css";

const AUTOSAVE = "urkaaz-map-editor-autosave";
type WorldPoint = { x: number; y: number };
type CursorPosition = WorldPoint & { screenX: number; screenY: number };
type ControlDrag = { x: number; originalHeight: number; height: number };
type ObjectDrag = {
  id: string;
  pointerOffsetX: number;
  pointerOffsetY: number;
  x: number;
  y: number;
};
type CornerDrag = {
  id: string;
  centerX: number;
  centerY: number;
  pointerX: number;
  pointerY: number;
  initialScale: number;
  initialRotationDegrees: number;
  initialY: number;
  scale: number;
  rotationDegrees: number;
  y: number;
};
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

function findObjectAt(
  point: WorldPoint,
  document: EditorDocument,
  view: { zoom: number; panX: number; panY: number },
  width: number,
  height: number,
): EditorDocument["map"]["objects"][number] | undefined {
  const relics = document.map.objects.filter(object => object.relicSize != null).reverse();
  const decorations = document.map.objects.filter(object => object.relicSize == null).reverse();
  return [...relics, ...decorations].find(object => {
    const geometry = objectScreenGeometry(object, document, view, width, height);
    const radians = -object.rotationDegrees * Math.PI / 180;
    const dx = point.x - geometry.centerX;
    const dy = point.y - geometry.centerY;
    const localX = dx * Math.cos(radians) - dy * Math.sin(radians);
    const localY = dx * Math.sin(radians) + dy * Math.cos(radians);
    return Math.abs(localX) <= geometry.width / 2 + 6 &&
      Math.abs(localY) <= geometry.height / 2 + 6;
  });
}

function objectScreenGeometry(
  object: EditorDocument["map"]["objects"][number],
  document: EditorDocument,
  view: { zoom: number; panX: number; panY: number },
  width: number,
  height: number,
) {
  const anchor = worldToScreen(object.x, object.y, view, width, height);
  if (object.relicSize) {
    const relicSize = ({ SMALL: 76, MEDIUM: 112, LARGE: 176 }[object.relicSize]) * object.scale;
    const worldScale = Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT) * view.zoom;
    const size = relicSize * worldScale;
    return { centerX: anchor.x, centerY: anchor.y, width: size, height: size };
  }

  const aspectRatio = mapDecorationAspectRatio(document.map.biome, object.assetKey) ?? 1;
  const size = mapDecorationRenderSize(
    object.scale,
    view.zoom,
    aspectRatio,
    window.devicePixelRatio || 1,
  );
  return {
    centerX: anchor.x,
    centerY: anchor.y + MAP_DECORATION_GROUND_SINK * view.zoom / (window.devicePixelRatio || 1) - size.height / 2,
    width: size.width,
    height: size.height,
  };
}

function findTransformHandleAt(
  point: WorldPoint,
  object: EditorDocument["map"]["objects"][number],
  document: EditorDocument,
  view: { zoom: number; panX: number; panY: number },
  width: number,
  height: number,
): boolean {
  const geometry = objectScreenGeometry(object, document, view, width, height);
  return rotatedRectangleCorners(
    { x: geometry.centerX, y: geometry.centerY },
    geometry.width,
    geometry.height,
    object.rotationDegrees,
  ).some(corner => Math.hypot(corner.x - point.x, corner.y - point.y) <= 10);
}

export default function App() {
  const [registeredAssetKeys, setRegisteredAssetKeys] = useState<ReadonlySet<string>>();
  const [assetCatalogError, setAssetCatalogError] = useState<string>();
  const { state, operation, undo, redo, load } = useEditorStore(registeredAssetKeys);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [view, setView] = useState({ zoom: 1, panX: 0, panY: 0 });
  const [issues, setIssues] = useState(validate(state.present));
  const [controlDrag, setControlDrag] = useState<ControlDrag | null>(null);
  const [objectDrag, setObjectDrag] = useState<ObjectDrag | null>(null);
  const [cornerDrag, setCornerDrag] = useState<CornerDrag | null>(null);
  const [selectedObjectId, setSelectedObjectId] = useState<string>();
  const [showCursorCoordinates, setShowCursorCoordinates] = useState(false);
  const [cursorPosition, setCursorPosition] = useState<CursorPosition | null>(null);
  const [sceneShiftAmount, setSceneShiftAmount] = useState(40);
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
      let displayDocument = state.present;
      if (controlDrag) {
        displayDocument = applyOperation(state.present, {
          type: "MOVE_TERRAIN_CONTROL_POINT",
          x: controlDrag.x,
          height: controlDrag.height,
        }, registeredAssetKeys);
      } else if (cornerDrag) {
        const map = structuredClone(state.present.map);
        const object = map.objects.find(item => item.id === cornerDrag.id);
        if (object) {
          object.scale = cornerDrag.scale;
          object.rotationDegrees = cornerDrag.rotationDegrees;
          object.y = cornerDrag.y;
          displayDocument = { ...state.present, map };
        }
      } else if (objectDrag) {
        const map = structuredClone(state.present.map);
        const object = map.objects.find(item => item.id === objectDrag.id);
        if (object) {
          object.x = objectDrag.x;
          object.y = objectDrag.y;
          displayDocument = { ...state.present, map };
        }
      }
      renderMap(
        canvas.current,
        displayDocument,
        view,
        true,
        controlDrag ? { x: controlDrag.x, height: controlDrag.height } : undefined,
        registeredAssetKeys,
        selectedObjectId,
      );
    }
    const validation = validate(state.present, registeredAssetKeys);
    setIssues(validation);
    localStorage.setItem(AUTOSAVE, encode(state.present));
    setStatus("Autosaved");
  }, [state.present, view, controlDrag, objectDrag, cornerDrag, registeredAssetKeys, selectedObjectId]);

  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "z") {
        event.preventDefault();
        event.shiftKey ? redo() : undo();
      }
      if (event.key === "Escape") {
        setControlDrag(null);
        setObjectDrag(null);
        setCornerDrag(null);
      }
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [redo, undo]);

  const newMap = () => {
    load({ map: defaultMap(), phase: "TERRAIN_EDITING", revision: 0 });
    setSelectedObjectId(undefined);
  };
  const resetMap = () => {
    if (!window.confirm("Reset the entire map to a new empty map? This cannot be undone.")) return;
    load({
      map: defaultMap("web-map", state.present.map.biome),
      phase: "TERRAIN_EDITING",
      revision: 0,
    });
    setSelectedObjectId(undefined);
    setStatus("Map reset");
  };

  const fileImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    file.text()
      .then(text => {
        const map = decode(text);
        load({ map, phase: "TERRAIN_EDITING", revision: 0 });
        setSelectedObjectId(undefined);
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
  const selectedObject = state.present.map.objects.find(object => object.id === selectedObjectId);
  const shiftWholeMap = (direction: -1 | 1) => {
    if (!Number.isFinite(sceneShiftAmount) || sceneShiftAmount <= 0) return;
    operation({
      type: "SHIFT_SCENE_VERTICALLY",
      deltaY: direction * sceneShiftAmount,
    });
  };

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
      if (cornerDrag) {
        const object = state.present.map.objects.find(item => item.id === cornerDrag.id);
        if (object) {
          const transform = transformFromCornerDrag(
            { x: cornerDrag.pointerX, y: cornerDrag.pointerY },
            screen,
            { x: cornerDrag.centerX, y: cornerDrag.centerY },
            cornerDrag.initialScale,
            cornerDrag.initialRotationDegrees,
          );
          let y = cornerDrag.y;
          if (!object.relicSize) {
            const aspectRatio = mapDecorationAspectRatio(state.present.map.biome, object.assetKey) ?? 1;
            const size = mapDecorationRenderSize(
              transform.scale,
              view.zoom,
              aspectRatio,
              window.devicePixelRatio || 1,
            );
            const baselineScreenY = cornerDrag.centerY -
              MAP_DECORATION_GROUND_SINK * view.zoom / (window.devicePixelRatio || 1) +
              size.height / 2;
            y = screenToWorld(
              cornerDrag.centerX,
              baselineScreenY,
              view,
              rect.width,
              rect.height,
            ).y;
          }
          setCornerDrag({ ...cornerDrag, ...transform, y });
        }
        return;
      }
      if (controlDrag) {
        setControlDrag({
          ...controlDrag,
          height: Math.max(0, Math.min(WORLD_HEIGHT, world.y)),
        });
      }
      if (objectDrag) {
        const object = state.present.map.objects.find(item => item.id === objectDrag.id);
        if (object) {
          const x = Math.max(
            0,
            Math.min(WORLD_WIDTH, world.x - objectDrag.pointerOffsetX),
          );
          const y = Math.max(
            0,
            Math.min(WORLD_HEIGHT, world.y - objectDrag.pointerOffsetY),
          );
          setObjectDrag({ ...objectDrag, x, y });
        }
      }
      return;
    }
    if (event.type === "pointerdown") {
      if (state.present.phase === "TERRAIN_EDITING") {
        const control = findControlAt(screen, state.present, view, rect.width, rect.height);
        if (control) {
          element.setPointerCapture(event.pointerId);
          setControlDrag({ x: control.x, originalHeight: control.height, height: control.height });
        }
        return;
      }
      const selected = state.present.map.objects.find(item => item.id === selectedObjectId);
      if (selected && canPlaceAssets &&
        findTransformHandleAt(screen, selected, state.present, view, rect.width, rect.height)) {
        const geometry = objectScreenGeometry(selected, state.present, view, rect.width, rect.height);
        element.setPointerCapture(event.pointerId);
        setCornerDrag({
          id: selected.id,
          centerX: geometry.centerX,
          centerY: geometry.centerY,
          pointerX: screen.x,
          pointerY: screen.y,
          initialScale: selected.scale,
          initialRotationDegrees: selected.rotationDegrees,
          initialY: selected.y,
          scale: selected.scale,
          rotationDegrees: selected.rotationDegrees,
          y: selected.y,
        });
        return;
      }
      const object = findObjectAt(screen, state.present, view, rect.width, rect.height);
      setSelectedObjectId(object?.id);
      if (object && canPlaceAssets) {
        element.setPointerCapture(event.pointerId);
        setObjectDrag({
          id: object.id,
          pointerOffsetX: world.x - object.x,
          pointerOffsetY: world.y - object.y,
          x: object.x,
          y: object.y,
        });
      }
      return;
    }
    if (event.type === "pointerup" && cornerDrag) {
      const changed = cornerDrag.scale !== cornerDrag.initialScale ||
        cornerDrag.rotationDegrees !== cornerDrag.initialRotationDegrees ||
        cornerDrag.y !== cornerDrag.initialY;
      if (changed) {
        operation({
          type: "TRANSFORM_OBJECT",
          id: cornerDrag.id,
          scale: cornerDrag.scale,
          rotationDegrees: cornerDrag.rotationDegrees,
          y: cornerDrag.y,
        });
      }
      setCornerDrag(null);
      if (element.hasPointerCapture(event.pointerId)) element.releasePointerCapture(event.pointerId);
      return;
    }
    if (event.type === "pointerup" && objectDrag) {
      if (objectDrag.x !== state.present.map.objects.find(item => item.id === objectDrag.id)?.x ||
        objectDrag.y !== state.present.map.objects.find(item => item.id === objectDrag.id)?.y) {
        operation({
          type: "MOVE_OBJECT",
          id: objectDrag.id,
          x: objectDrag.x,
          y: objectDrag.y,
        });
      }
      setObjectDrag(null);
      if (element.hasPointerCapture(event.pointerId)) element.releasePointerCapture(event.pointerId);
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

    let y = world.y;
    if (asset.relicSize) {
      const halfWidth = asset.relicSize === "MEDIUM" ? 96 : 56;
      const halfHeight = asset.relicSize === "MEDIUM" ? 72 : 40;
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
    }

    const objectId = `map-asset-${Date.now()}-${nextObjectId.current++}`;
    operation({
      type: "PLACE_OBJECT",
      object: {
        id: objectId,
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
    setSelectedObjectId(objectId);
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
          <section className="foundation-controls">
            <h3>Foundation height</h3>
            <p>Fortress and catapult move together; nearby terrain follows smoothly.</p>
            {state.present.map.fortresses.map(fortress => (
              <label key={fortress.owner}>
                {fortress.owner} · Y {Math.round(fortress.foundationHeight)}
                <input
                  aria-label={`${fortress.owner} foundation height`}
                  type="range"
                  min="0"
                  max={WORLD_HEIGHT}
                  step="10"
                  value={fortress.foundationHeight}
                  disabled={state.present.phase !== "TERRAIN_EDITING"}
                  onChange={event => operation({
                    type: "CHANGE_FOUNDATION_HEIGHT",
                    owner: fortress.owner,
                    height: Number(event.currentTarget.value),
                  })}
                />
              </label>
            ))}
          </section>
          <section className="scene-position-controls">
            <h3>Whole map position</h3>
            <p>Raise or lower terrain, fortresses, catapults, and assets together. The background stays fixed.</p>
            <label>
              Move by (world units)
              <input
                aria-label="Whole map movement step"
                type="number"
                min="1"
                max={WORLD_HEIGHT}
                step="10"
                value={sceneShiftAmount}
                onChange={event => setSceneShiftAmount(Number(event.currentTarget.value))}
              />
            </label>
            <div className="scene-position-actions">
              <button
                aria-label="Raise whole map"
                disabled={!Number.isFinite(sceneShiftAmount) || sceneShiftAmount <= 0}
                onClick={() => shiftWholeMap(-1)}
              >
                Raise
              </button>
              <button
                aria-label="Lower whole map"
                disabled={!Number.isFinite(sceneShiftAmount) || sceneShiftAmount <= 0}
                onClick={() => shiftWholeMap(1)}
              >
                Lower
              </button>
            </div>
          </section>
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
            onPointerCancel={() => {
              setControlDrag(null);
              setObjectDrag(null);
              setCornerDrag(null);
            }}
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
          {cornerDrag && (
            <div className="hint">Drag a corner to resize and rotate · release to commit</div>
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
          {selectedObject && (
            <section className="object-controls">
              <h3>Selected decoration</h3>
              <p>{selectedObject.assetKey}</p>
              <p>Drag it to move it; drag a corner to scale and rotate around its center.</p>
              <label>
                Scale ({selectedObject.scale.toFixed(2)}×)
                <input
                  aria-label="Decoration scale"
                  type="number"
                  min="0.1"
                  step="0.1"
                  value={selectedObject.scale}
                  onChange={event => {
                    const scale = Number(event.currentTarget.value);
                    if (Number.isFinite(scale) && scale > 0) {
                      operation({ type: "SCALE_OBJECT", id: selectedObject.id, scale });
                    }
                  }}
                />
              </label>
              <label>
                Rotation (°)
                <input
                  aria-label="Decoration rotation"
                  type="number"
                  step="1"
                  value={selectedObject.rotationDegrees}
                  onChange={event => {
                    const rotationDegrees = Number(event.currentTarget.value);
                    if (Number.isFinite(rotationDegrees)) {
                      operation({
                        type: "ROTATE_OBJECT",
                        id: selectedObject.id,
                        rotationDegrees,
                      });
                    }
                  }}
                />
              </label>
              <button
                onClick={() => {
                  operation({ type: "REMOVE_OBJECT", id: selectedObject.id });
                  setSelectedObjectId(undefined);
                }}
              >
                Remove decoration
              </button>
            </section>
          )}
          <h3>Assets</h3>
          <p>Biome: {state.present.map.biome}</p>
          {assetCatalogError && <p className="error">Asset catalogue unavailable: {assetCatalogError}</p>}
          {!assetCatalogError && !registeredAssetKeys && <p>Loading asset catalogue…</p>}
          <p>
            {canPlaceAssets
              ? "Drag an asset onto the map. Click and drag a placed decoration to move it."
              : "Lock terrain before placing assets."}
          </p>
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
