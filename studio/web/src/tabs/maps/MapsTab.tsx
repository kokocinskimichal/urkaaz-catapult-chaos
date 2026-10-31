import React, { useEffect, useRef, useState } from "react";
import type { CampaignChapterCatalogEntry } from "@studio/contracts/campaign";
import { decode, encode } from "./format/codec";
import { paletteAssetsForBiome, parseAssetIndex } from "./domain/assets";
import {
  campaignMapIdForSelection,
  campaignMapSelectionFromId,
} from "./domain/campaignMap";
import {
  defaultMap,
  MAP_BIOMES,
  maximumTerrainHeightInRange,
  terrainControlPoints,
  WORLD_HEIGHT,
  WORLD_WIDTH,
} from "./domain/map";
import type { EditorDocument, SampleJson } from "./domain/map";
import { applyOperation, validate } from "./domain/operations";
import { useEditorStore } from "./state/store";
import {
  MAP_DECORATION_GROUND_SINK,
  mapDecorationAspectRatio,
  mapDecorationRenderSize,
  mapRelicAspectRatio,
  mapRelicRenderSize,
  renderMap,
} from "./rendering/canvas";
import {
  rotatedRectangleCorners,
  screenToWorld,
  transformFromCornerDrag,
  worldToScreen,
} from "./rendering/transform";
import {
  ApiError,
  fetchCampaignChapters,
  fetchMap,
  fetchMaps,
  fetchTerrainAssetIndex,
  saveMap,
} from "../../shared/api";
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
  assetKeys?: ReadonlySet<string>,
): EditorDocument["map"]["objects"][number] | undefined {
  const relics = document.map.objects.filter(object => object.relicSize != null).reverse();
  const decorations = document.map.objects.filter(object => object.relicSize == null).reverse();
  return [...relics, ...decorations].find(object => {
    const geometry = objectScreenGeometry(object, document, view, width, height, assetKeys);
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
  assetKeys?: ReadonlySet<string>,
) {
  const anchor = worldToScreen(object.x, object.y, view, width, height);
  if (object.relicSize) {
    const size = mapRelicRenderSize(
      object.relicSize,
      object.scale,
      view.zoom,
      mapRelicAspectRatio(document.map.biome, object.assetKey, assetKeys) ?? 1,
      window.devicePixelRatio || 1,
    );
    return { centerX: anchor.x, centerY: anchor.y, ...size };
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
  assetKeys?: ReadonlySet<string>,
): boolean {
  const geometry = objectScreenGeometry(object, document, view, width, height, assetKeys);
  return rotatedRectangleCorners(
    { x: geometry.centerX, y: geometry.centerY },
    geometry.width,
    geometry.height,
    object.rotationDegrees,
  ).some(corner => Math.hypot(corner.x - point.x, corner.y - point.y) <= 10);
}

export function MapsTab() {
  const [registeredAssetKeys, setRegisteredAssetKeys] = useState<ReadonlySet<string>>();
  const [assetCatalogError, setAssetCatalogError] = useState<string>();
  const { state, operation, undo, redo, load, setMapId } = useEditorStore(registeredAssetKeys);
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
  const [maps, setMaps] = useState<Awaited<ReturnType<typeof fetchMaps>>["maps"]>([]);
  const [mapCatalogError, setMapCatalogError] = useState<string>();
  const [campaignChapters, setCampaignChapters] = useState<CampaignChapterCatalogEntry[]>([]);
  const [campaignChapterError, setCampaignChapterError] = useState<string>();
  const [editorReady, setEditorReady] = useState(false);
  const [selectedMapId, setSelectedMapId] = useState(state.present.map.mapId);
  const [savedHash, setSavedHash] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [saveChapterId, setSaveChapterId] = useState("");
  const [saveLevel, setSaveLevel] = useState<number | null>(null);
  const nextObjectId = useRef(0);
  const lastSavedContent = useRef<string | null>(null);
  const presentRef = useRef(state.present);
  presentRef.current = state.present;
  const selectedSaveChapter = campaignChapters.find(chapter => chapter.id === saveChapterId);
  const saveMapId = selectedSaveChapter && saveLevel !== null
    ? campaignMapIdForSelection(campaignChapters, {
      chapterId: selectedSaveChapter.id,
      level: saveLevel,
    })
    : null;
  const saveTargetExists = saveMapId !== null && maps.some(map => map.id === saveMapId);

  useEffect(() => {
    let cancelled = false;
    fetchTerrainAssetIndex()
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
    let cancelled = false;
    fetchCampaignChapters()
      .then(result => {
        if (!cancelled) setCampaignChapters(result.chapters);
      })
      .catch(error => {
        if (!cancelled) {
          setCampaignChapterError(error instanceof Error ? error.message : String(error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        let mapList: Awaited<ReturnType<typeof fetchMaps>>["maps"] = [];
        let catalogLoaded = true;
        try {
          mapList = (await fetchMaps()).maps;
          if (!cancelled) setMaps(mapList);
        } catch (error) {
          catalogLoaded = false;
          if (!cancelled) setMapCatalogError(error instanceof Error ? error.message : String(error));
        }

        const saved = localStorage.getItem(AUTOSAVE);
        if (saved) {
          try {
            const map = decode(saved);
            let baseline: string | null = null;
            let hash: string | null = null;
            let repositoryUnavailable = !catalogLoaded;
            if (catalogLoaded && mapList.some(entry => entry.id === map.mapId)) {
              try {
                const remote = await fetchMap(map.mapId);
                baseline = encode(decode(remote.content));
                hash = remote.hash;
              } catch (error) {
                repositoryUnavailable = true;
              }
            }
            if (cancelled) return;
            lastSavedContent.current = baseline;
            setSavedHash(hash);
            setSelectedMapId(map.mapId);
            load({ map, phase: "TERRAIN_EDITING", revision: 0 });
            setStatus(repositoryUnavailable
              ? "Restored local draft; repository version could not be checked"
              : "Restored local draft");
            return;
          } catch (error) {
            if (!cancelled) setStatus(`Autosave could not be restored: ${error instanceof Error ? error.message : String(error)}`);
          }
        }

        if (mapList[0]) {
          try {
            const remote = await fetchMap(mapList[0].id);
            const map = decode(remote.content);
            if (cancelled) return;
            lastSavedContent.current = encode(map);
            setSavedHash(remote.hash);
            setSelectedMapId(remote.id);
            load({ map, phase: "TERRAIN_EDITING", revision: 0 });
            setStatus(remote.synchronized ? "Loaded from repository" : "Map copies differ; saving will synchronize them");
          } catch (error) {
            if (!cancelled) setStatus(error instanceof Error ? error.message : String(error));
          }
        }
      } finally {
        if (!cancelled) setEditorReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [load]);

  useEffect(() => {
    if (!editorReady) return;
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
    const content = encode(state.present);
    localStorage.setItem(AUTOSAVE, content);
    if (lastSavedContent.current !== content) setStatus("Unsaved changes");
  }, [editorReady, state.present, view, controlDrag, objectDrag, cornerDrag, registeredAssetKeys, selectedObjectId]);

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
        setSaveDialogOpen(false);
      }
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [redo, undo]);

  const newMap = () => {
    const mapId = `studio-map-${Date.now()}`;
    lastSavedContent.current = null;
    setSavedHash(null);
    setSelectedMapId(mapId);
    load({ map: defaultMap(mapId), phase: "TERRAIN_EDITING", revision: 0 });
    setSelectedObjectId(undefined);
    setStatus("New map");
  };
  const resetMap = () => {
    if (!window.confirm("Reset the entire map to a new empty map? This cannot be undone.")) return;
    load({
      map: defaultMap(state.present.map.mapId, state.present.map.biome),
      phase: "TERRAIN_EDITING",
      revision: 0,
    });
    setSelectedObjectId(undefined);
    setStatus("Map reset; save to apply the reset");
  };

  const openSelectedMap = async () => {
    try {
      setStatus(`Loading ${selectedMapId}…`);
      const remote = await fetchMap(selectedMapId);
      const map = decode(remote.content);
      lastSavedContent.current = encode(map);
      setSavedHash(remote.hash);
      load({ map, phase: "TERRAIN_EDITING", revision: 0 });
      setSelectedObjectId(undefined);
      setStatus(remote.synchronized ? "Loaded from repository" : "Map copies differ; saving will synchronize them");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  };

  const openSaveDialog = () => {
    if (campaignChapters.length === 0) {
      setStatus(campaignChapterError ?? "Campaign chapters are still loading");
      return;
    }
    const savedSelection = campaignMapSelectionFromId(
      state.present.map.mapId,
      campaignChapters,
    );
    const chapter = savedSelection
      ? campaignChapters.find(item => item.id === savedSelection.chapterId)
      : campaignChapters[0];
    if (!chapter) {
      setStatus("Campaign chapter list is empty");
      return;
    }
    setSaveChapterId(chapter.id);
    setSaveLevel(
      savedSelection?.chapterId === chapter.id
        ? savedSelection.level
        : chapter.firstLevel,
    );
    setSaveDialogOpen(true);
  };

  const saveToCampaignLevel = async () => {
    if (!selectedSaveChapter || saveLevel === null || saveMapId === null) return;
    const targetDocument = {
      ...state.present,
      map: { ...state.present.map, mapId: saveMapId },
    };
    const currentIssues = validate(targetDocument, registeredAssetKeys);
    if (currentIssues.some(issue => issue.severity === "error")) {
      setStatus("Fix validation errors before export");
      return;
    }
    const content = encode(targetDocument);
    setSaving(true);
    setStatus(`Saving Chapter ${campaignChapters.indexOf(selectedSaveChapter) + 1}, Level ${saveLevel}…`);
    try {
      let expectedHash: string | null;
      try {
        expectedHash = (await fetchMap(saveMapId)).hash;
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) expectedHash = null;
        else throw error;
      }
      const saved = await saveMap(saveMapId, { content, expectedHash });
      lastSavedContent.current = content;
      setSavedHash(saved.hash);
      setSelectedMapId(saved.id);
      setMapId(saved.id);
      setMaps(current => {
        const entry = { id: saved.id, biome: state.present.map.biome, synchronized: saved.synchronized };
        return current.some(map => map.id === saved.id)
          ? current.map(map => map.id === saved.id ? entry : map)
          : [...current, entry].sort((a, b) => a.id.localeCompare(b.id));
      });
      const latestContent = encode({
        ...presentRef.current,
        map: { ...presentRef.current.map, mapId: saved.id },
      });
      setStatus(latestContent === content
        ? "Saved to both repository map files"
        : "Saved; newer edits are still unsaved");
      setSaveDialogOpen(false);
    } catch (error) {
      const details = error instanceof ApiError && error.details.length > 0
        ? `: ${error.details.join("; ")}`
        : "";
      setStatus(`${error instanceof Error ? error.message : String(error)}${details}`);
    } finally {
      setSaving(false);
    }
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
        findTransformHandleAt(
          screen,
          selected,
          state.present,
          view,
          rect.width,
          rect.height,
          registeredAssetKeys,
        )) {
        const geometry = objectScreenGeometry(
          selected,
          state.present,
          view,
          rect.width,
          rect.height,
          registeredAssetKeys,
        );
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
      const object = findObjectAt(
        screen,
        state.present,
        view,
        rect.width,
        rect.height,
        registeredAssetKeys,
      );
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
    <div className="maps-editor">
      <div className="map-editor-toolbar">
        <strong>Maps</strong>
        <select
          aria-label="Repository map"
          value={selectedMapId}
          disabled={saving}
          onChange={event => setSelectedMapId(event.currentTarget.value)}
        >
          {!maps.some(map => map.id === selectedMapId) && (
            <option value={selectedMapId}>Current map · {selectedMapId}</option>
          )}
          {maps.map(map => (
            <option key={map.id} value={map.id}>
              {map.id}{map.biome ? ` · ${map.biome}` : ""}{map.synchronized ? "" : " · copies differ"}
            </option>
          ))}
        </select>
        <button onClick={openSelectedMap} disabled={saving || !maps.some(map => map.id === selectedMapId)}>Open</button>
        <button onClick={newMap} disabled={saving}>New map</button>
        <button onClick={resetMap} disabled={saving}>Reset</button>
        <button
          onClick={openSaveDialog}
          disabled={saving || campaignChapters.length === 0 || issues.some(issue => issue.severity === "error")}
        >
          {saving ? "Saving…" : "Save to repository"}
        </button>
        <button onClick={undo}>Undo</button>
        <button onClick={redo}>Redo</button>
        <button onClick={() => setView({ zoom: 1, panX: 0, panY: 0 })}>Fit</button>
        <span className="status">{status}</span>
      </div>
      {campaignChapterError && (
        <p className="map-catalog-error">Campaign chapters unavailable: {campaignChapterError}</p>
      )}
      {mapCatalogError && <p className="map-catalog-error">Map list unavailable: {mapCatalogError}</p>}
      {saveDialogOpen && selectedSaveChapter && saveLevel !== null && saveMapId && (
        <div className="map-save-dialog-backdrop">
          <section
            className="map-save-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="map-save-title"
            aria-describedby="map-save-description"
          >
            <h2 id="map-save-title">Save campaign map</h2>
            <p id="map-save-description">
              Choose the campaign chapter and global level that should use this map.
              The game picks up the saved map after rebuild and restart.
            </p>
            <label>
              Chapter
              <select
                aria-label="Save chapter"
                value={saveChapterId}
                onChange={event => {
                  const chapter = campaignChapters.find(item => item.id === event.currentTarget.value);
                  if (chapter) {
                    setSaveChapterId(chapter.id);
                    setSaveLevel(chapter.firstLevel);
                  }
                }}
              >
                {campaignChapters.map((chapter, index) => (
                  <option key={chapter.id} value={chapter.id}>
                    Chapter {index + 1} · {chapter.title}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Level
              <select
                aria-label="Save level"
                value={saveLevel}
                onChange={event => setSaveLevel(Number(event.currentTarget.value))}
              >
                {Array.from(
                  { length: selectedSaveChapter.lastLevel - selectedSaveChapter.firstLevel + 1 },
                  (_, index) => selectedSaveChapter.firstLevel + index,
                ).map(level => (
                  <option key={level} value={level}>Level {level}</option>
                ))}
              </select>
            </label>
            <p className="map-save-target">Target map ID: {saveMapId}</p>
            {saveTargetExists && (
              <p className="map-save-warning" role="status">
                This campaign map already exists and will be updated on both repository copies.
              </p>
            )}
            <div className="map-save-dialog-actions">
              <button type="button" onClick={() => setSaveDialogOpen(false)} disabled={saving}>
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void saveToCampaignLevel()}
                disabled={saving || issues.some(issue => issue.severity === "error")}
              >
                {saving ? "Saving…" : "Save campaign map"}
              </button>
            </div>
          </section>
        </div>
      )}
      <section className="map-editor-layout">
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
        <div className="map-canvas-wrap">
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
      <footer className="map-editor-footer">
        {state.message ?? "Drag yellow contour nodes to shape terrain · Lock terrain to finish"}
      </footer>
    </div>
  );
}
