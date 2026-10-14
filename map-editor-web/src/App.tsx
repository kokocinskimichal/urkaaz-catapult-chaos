import React, { useEffect, useRef, useState } from "react";
import { decode, encode } from "./format/codec";
import { firstAssetKeyForBiome, parseAssetIndex } from "./domain/assets";
import {
  defaultMap,
  EditorDocument,
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
type Tool = "draw" | "smooth" | "flatten" | "foundation" | "select";
type WorldPoint = { x: number; y: number };
type ControlDrag = { x: number; originalHeight: number; height: number };

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
  const [tool, setTool] = useState<Tool>("draw");
  const [issues, setIssues] = useState(validate(state.present));
  const [drag, setDrag] = useState<WorldPoint[] | null>(null);
  const [controlDrag, setControlDrag] = useState<ControlDrag | null>(null);
  const [selectedTeam, setSelectedTeam] = useState<"BLUE" | "RED">("BLUE");
  const [height, setHeight] = useState(3180);
  const [status, setStatus] = useState("Unsaved");
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
      if (event.key === "Escape") {
        setDrag(null);
        setControlDrag(null);
      }
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

  const treeAssetKey = registeredAssetKeys
    ? firstAssetKeyForBiome(registeredAssetKeys, state.present.map.biome, "tree")
    : undefined;

  const pointer = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const element = canvas.current;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    const screen = { x: event.clientX - rect.left, y: event.clientY - rect.top };
    const world = screenToWorld(screen.x, screen.y, view, rect.width, rect.height);

    if (event.type === "pointerdown") {
      element.setPointerCapture(event.pointerId);
      if (tool === "draw" && state.present.phase === "TERRAIN_EDITING") {
        const control = findControlAt(screen, state.present, view, rect.width, rect.height);
        if (control) {
          setControlDrag({ x: control.x, originalHeight: control.height, height: control.height });
          return;
        }
      }
      if (tool === "draw" || tool === "smooth" || tool === "flatten") {
        setDrag([world]);
      } else if (tool === "foundation") {
        operation({ type: "CHANGE_FOUNDATION_HEIGHT", owner: selectedTeam, height });
      } else if (tool === "select") {
        const object = state.present.map.objects.find(
          item => Math.abs(item.x - world.x) < 120 && Math.abs(item.y - world.y) < 180,
        );
        setStatus(object ? `Selected ${object.id}` : "No object selected");
      }
      return;
    }

    if (event.type === "pointermove") {
      if (controlDrag) {
        setControlDrag({
          ...controlDrag,
          height: Math.max(0, Math.min(WORLD_HEIGHT, world.y)),
        });
      } else if (drag) {
        setDrag([...drag, world]);
      }
      return;
    }

    if (event.type === "pointerup") {
      if (controlDrag) {
        if (controlDrag.height !== controlDrag.originalHeight) {
          operation({
            type: "MOVE_TERRAIN_CONTROL_POINT",
            x: controlDrag.x,
            height: controlDrag.height,
          });
        }
        setControlDrag(null);
      } else if (drag) {
        const points = [...drag, world].sort((left, right) => left.x - right.x);
        const minX = points[0].x;
        const maxX = points[points.length - 1].x;
        if (tool === "draw") {
          operation({
            type: "DRAW_TERRAIN_PROFILE",
            points: points.map(point => ({ x: point.x, height: point.y })),
          });
        } else if (tool === "smooth") {
          operation({ type: "SMOOTH_TERRAIN_RANGE", minX, maxX });
        } else if (tool === "flatten") {
          operation({ type: "FLATTEN_TERRAIN_RANGE", minX, maxX });
        }
        setDrag(null);
      }
      element.releasePointerCapture(event.pointerId);
    }
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
          <h3>Tools</h3>
          {(["draw", "smooth", "flatten", "foundation", "select"] as const).map(value => (
            <button
              className={tool === value ? "active" : ""}
              onClick={() => setTool(value)}
              key={value}
            >
              {value}
            </button>
          ))}
          <p className="tool-help">
            In draw mode, drag a yellow point to reshape the terrain contour.
          </p>
          <button
            disabled={state.present.phase !== "TERRAIN_EDITING"}
            onClick={() => operation({ type: "LOCK_TERRAIN" })}
          >
            Lock terrain
          </button>
          <button
            disabled={!["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(state.present.phase)}
            onClick={() => operation({ type: "MARK_READY_TO_EXPORT" })}
          >
            Ready to export
          </button>
          <button onClick={() => setIssues(validate(state.present, registeredAssetKeys))}>Validate</button>
          <h3>Foundation</h3>
          <select
            value={selectedTeam}
            onChange={event => setSelectedTeam(event.target.value as "BLUE" | "RED")}
          >
            <option>BLUE</option>
            <option>RED</option>
          </select>
          <input
            type="number"
            value={height}
            onChange={event => setHeight(Number(event.target.value))}
          />
          <p>World<br />{WORLD_WIDTH} × {WORLD_HEIGHT}</p>
        </aside>
        <div className="canvas-wrap">
          <canvas
            ref={canvas}
            onPointerDown={pointer}
            onPointerMove={pointer}
            onPointerUp={pointer}
            onPointerCancel={() => {
              setDrag(null);
              setControlDrag(null);
            }}
            onWheel={wheel}
          />
          {(drag || controlDrag) && (
            <div className="hint">
              {controlDrag ? "Adjusting contour point · release to commit" : "Drawing preview · release to commit"}
            </div>
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
          {registeredAssetKeys && !treeAssetKey && <p>No registered tree asset for this biome.</p>}
          <button
            disabled={!["TERRAIN_LOCKED", "DECORATION_EDITING"].includes(state.present.phase) || !treeAssetKey}
            onClick={() => {
              const y = terrainHeightAt(state.present.map, 3600);
              if (!treeAssetKey || y === undefined) {
                setStatus("Cannot place a tree without a registered asset and terrain profile");
                return;
              }
              operation({
                type: "PLACE_OBJECT",
                object: {
                  id: `tree-${Date.now()}`,
                  type: "DECORATION",
                  x: 3600,
                  y,
                  scale: 1,
                  rotationDegrees: 0,
                  assetKey: treeAssetKey,
                  collision: "NONE",
                  allowOverlap: false,
                },
              });
            }}
          >
            Add tree
          </button>
        </aside>
      </section>
      <footer>{state.message ?? "Terrain contour points are draggable · changes support undo/redo"}</footer>
    </main>
  );
}
