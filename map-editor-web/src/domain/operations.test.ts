import { describe, expect, it } from "vitest";
import {
  defaultMap,
  foundationExtension,
  TERRAIN_CONTROL_SPACING,
  terrainControlPoints,
} from "./map";
import { applyOperation, validate } from "./operations";
import { parseAssetIndex } from "./assets";
import { decode, encode } from "../format/codec";

describe("map operations", () => {
  it("changes the biome without changing map layout or phase", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_LOCKED" as const, revision: 4 };
    const updated = applyOperation(document, {
      type: "CHANGE_BIOME",
      biome: "copperwood",
    });
    expect(updated.map.biome).toBe("copperwood");
    expect(updated.map.terrain).toEqual(document.map.terrain);
    expect(updated.map.fortresses).toEqual(document.map.fortresses);
    expect(updated.phase).toBe(document.phase);
    expect(updated.revision).toBe(5);
  });

  it("changes foundation height atomically", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    const updated = applyOperation(document, {
      type: "CHANGE_FOUNDATION_HEIGHT",
      owner: "BLUE",
      height: 3000,
    });
    expect(updated.map.fortresses[0].foundationHeight).toBe(3000);
    expect(updated.map.catapults[0].foundationHeight).toBe(3000);
    expect(updated.map.protectedTerrainZones[0].minHeight).toBe(3000);
    expect(updated.map.protectedTerrainZones[0].maxHeight).toBe(3000);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1080)?.height).toBe(3000);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1120)?.height)
      .toBeCloseTo(3002.3, 1);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1200)?.height)
      .toBeCloseTo(3018.7, 1);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1680)?.height).toBe(3180);
    expect(updated.map.terrain.samples.find(sample => sample.x === 6080)?.height).toBe(3180);
  });

  it("moves the whole scene vertically while preserving its relative layout and editor phase", () => {
    const map = defaultMap();
    map.objects.push({
      id: "tree-1",
      type: "DECORATION",
      x: 3600,
      y: 3200,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "terrain_green_frontier_tree_00",
      collision: "NONE",
      allowOverlap: false,
    });
    const document = { map, phase: "DECORATION_EDITING" as const, revision: 2 };

    const updated = applyOperation(document, {
      type: "SHIFT_SCENE_VERTICALLY",
      deltaY: -100,
    });

    expect(updated.map.terrain.samples.every(sample => sample.height === 3080)).toBe(true);
    expect(updated.map.fortresses.map(fortress => fortress.foundationHeight)).toEqual([3080, 3080]);
    expect(updated.map.catapults.map(catapult => catapult.foundationHeight)).toEqual([3080, 3080]);
    expect(updated.map.protectedTerrainZones.map(zone => [zone.minHeight, zone.maxHeight]))
      .toEqual([[3080, 3080], [3080, 3080]]);
    expect(updated.map.objects[0].y).toBe(3100);
    expect(updated.map.fortresses.map(fortress => fortress.x)).toEqual([490, 6710]);
    expect(updated.phase).toBe("DECORATION_EDITING");
    expect(updated.revision).toBe(3);
    expect(document.map.terrain.samples[0].height).toBe(3180);
    expect(document.map.objects[0].y).toBe(3200);
    expect(validate(updated, parseAssetIndex("terrain_green_frontier_tree_00.png"))).toEqual([]);

    const lowered = applyOperation(document, {
      type: "SHIFT_SCENE_VERTICALLY",
      deltaY: 100,
    });
    expect(lowered.map.terrain.samples[0].height).toBe(3280);
    expect(lowered.map.fortresses[0].foundationHeight).toBe(3280);
    expect(lowered.map.objects[0].y).toBe(3300);
  });

  it("rejects whole-scene movement that would push a relic footprint outside the world", () => {
    const map = defaultMap();
    map.objects.push({
      id: "relic-1",
      type: "DECORATION",
      x: 3600,
      y: 100,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "relic.green_frontier.small.1",
      collision: "NONE",
      allowOverlap: false,
      relicSize: "SMALL",
    });
    const document = { map, phase: "TERRAIN_LOCKED" as const, revision: 0 };

    expect(() => applyOperation(document, {
      type: "SHIFT_SCENE_VERTICALLY",
      deltaY: -61,
    })).toThrow("Whole-map vertical shift would move content outside the world");
    expect(document.map.objects[0].y).toBe(100);
  });

  it("rejects foundation heights outside the world", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    expect(() => applyOperation(document, {
      type: "CHANGE_FOUNDATION_HEIGHT",
      owner: "BLUE",
      height: Number.NaN,
    })).toThrow("Foundation height is outside the world");
    expect(() => applyOperation(document, {
      type: "CHANGE_FOUNDATION_HEIGHT",
      owner: "BLUE",
      height: 4801,
    })).toThrow("Foundation height is outside the world");
  });

  it("keeps front foundation extensions fixed", () => {
    const map = defaultMap();
    expect(map.protectedTerrainZones.find(zone => zone.id === "blue-foundation")?.maxX)
      .toBe(1080);
    expect(map.protectedTerrainZones.find(zone => zone.id === "red-foundation")?.minX)
      .toBe(6120);
    expect(map.protectedTerrainZones.find(zone => zone.id === "blue-foundation")?.minX).toBe(0);
    expect(map.protectedTerrainZones.find(zone => zone.id === "red-foundation")?.maxX).toBe(7200);
    expect(map.fortresses.map(fortress => fortress.x)).toEqual([490, 6710]);
    expect(map.catapults.map(catapult => catapult.x)).toEqual([380, 6820]);
    expect(foundationExtension).toBe(320);
    for (const zone of map.protectedTerrainZones) {
      expect(zone.minX % map.terrain.sampleStep).toBe(0);
      expect(zone.maxX % map.terrain.sampleStep).toBe(0);
      expect(map.terrain.samples.filter(sample => sample.x >= zone.minX && sample.x <= zone.maxX)
        .every(sample => sample.height === zone.minHeight)).toBe(true);
    }
    expect(terrainControlPoints(map).every(point =>
      map.protectedTerrainZones.every(zone => point.x < zone.minX || point.x > zone.maxX),
    )).toBe(true);
  });

  it("shows twice as many draggable nodes at the denser spacing", () => {
    const map = defaultMap();
    const nodes = terrainControlPoints(map);
    expect(TERRAIN_CONTROL_SPACING).toBe(200);
    expect(nodes.length).toBe(25);
    expect(nodes.slice(1).every((node, index) => node.x - nodes[index].x === 200)).toBe(true);
  });

  it("interpolates a drawn profile between its input points", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    const updated = applyOperation(document, {
      type: "DRAW_TERRAIN_PROFILE",
      points: [{ x: 1000, height: 3000 }, { x: 2000, height: 3400 }],
    });
    expect(updated.map.terrain.samples.find(sample => sample.x === 1200)?.height).toBe(3080);
  });

  it("moves a contour node and proportionally moves nearby terrain", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    const before = document.map.terrain.samples.find(sample => sample.x === 2000)?.height;
    const nearBefore = document.map.terrain.samples.find(sample => sample.x === 1800)?.height;
    const fartherBefore = document.map.terrain.samples.find(sample => sample.x === 1600)?.height;
    const distantBefore = document.map.terrain.samples.find(sample => sample.x === 4000)?.height;
    const updated = applyOperation(document, {
      type: "MOVE_TERRAIN_CONTROL_POINT",
      x: 2000,
      height: 2800,
    });
    expect(updated.map.terrain.samples.find(sample => sample.x === 2000)?.height).toBe(2800);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1800)?.height).not.toBe(nearBefore);
    expect(Math.abs(2800 - (updated.map.terrain.samples.find(sample => sample.x === 1800)?.height ?? 0)))
      .toBeLessThan(Math.abs(2800 - (updated.map.terrain.samples.find(sample => sample.x === 1600)?.height ?? 0)));
    expect(updated.map.terrain.samples.find(sample => sample.x === 1600)?.height).not.toBe(fartherBefore);
    expect(updated.map.terrain.samples.find(sample => sample.x === 4000)?.height).toBe(distantBefore);
    expect(updated.map.terrain.samples.find(sample => sample.x === 1040)?.height).toBe(3180);
    expect(updated.revision).toBe(1);
  });

  it("does not expose protected foundation positions as control points", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    expect(() => applyOperation(document, {
      type: "MOVE_TERRAIN_CONTROL_POINT",
      x: 600,
      height: 2800,
    })).toThrow("Control point is not editable");
  });

  it("draws across foundations without changing protected samples", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    const updated = applyOperation(document, {
      type: "DRAW_TERRAIN_PROFILE",
      points: [{ x: 0, height: 2800 }, { x: 7200, height: 3600 }],
    });
    expect(updated.map.terrain.samples.find(sample => sample.x === 320)?.height).toBe(3180);
    expect(updated.map.terrain.samples.find(sample => sample.x === 2000)?.height)
      .toBeCloseTo(3022.222, 2);
  });

  it("rejects terrain edits after locking", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_LOCKED" as const, revision: 0 };
    expect(() => applyOperation(document, {
      type: "FLATTEN_TERRAIN_RANGE",
      minX: 2000,
      maxX: 2200,
    })).toThrow();
  });

  it("keeps deterministic sample endpoints", () => {
    const document = { map: defaultMap(), phase: "TERRAIN_EDITING" as const, revision: 0 };
    expect(document.map.terrain.samples[0].x).toBe(0);
    expect(document.map.terrain.samples.at(-1)?.x).toBe(7200);
    expect(document.map.terrain.samples.every(sample => sample.height === 3180)).toBe(true);
    expect(validate(document).some(issue => issue.severity === "error")).toBe(false);
  });

  it("keeps imported maps valid and checks registered decoration assets", () => {
    const map = decode(encode(defaultMap()));
    expect(map.catapults.map(catapult => catapult.x)).toEqual([380, 6820]);
    const document = { map, phase: "TERRAIN_EDITING" as const, revision: 0 };
    expect(validate(document, new Set())).toEqual([]);

    map.objects.push({
      id: "tree-1",
      type: "DECORATION",
      x: 3600,
      y: 3180,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "terrain_green_frontier_tree_00",
      collision: "NONE",
      allowOverlap: false,
    });
    const assetKeys = parseAssetIndex("terrain_green_frontier_tree_00.png");
    expect(validate(document, assetKeys)).toEqual([]);
    expect(validate(document, new Set()).some(issue => issue.path === "objects.tree-1.assetKey")).toBe(true);
    expect(() => applyOperation(
      { ...document, phase: "TERRAIN_LOCKED" },
      { type: "MARK_READY_TO_EXPORT" },
    )).toThrow("Fix validation errors");
    expect(applyOperation(
      { ...document, phase: "TERRAIN_LOCKED" },
      { type: "MARK_READY_TO_EXPORT" },
      assetKeys,
    ).phase).toBe("READY_TO_EXPORT");
  });

  it("moves, scales, and rotates decorations without requiring terrain contact or a flat slope", () => {
    const assetKeys = parseAssetIndex("terrain_green_frontier_tree_00.png");
    const document = {
      map: defaultMap(),
      phase: "TERRAIN_LOCKED" as const,
      revision: 0,
    };
    const placed = applyOperation(document, {
      type: "PLACE_OBJECT",
      object: {
        id: "tree-1",
        type: "DECORATION",
        x: 3600,
        y: 3180,
        scale: 1,
        rotationDegrees: 0,
        assetKey: "terrain_green_frontier_tree_00",
        collision: "NONE",
        allowOverlap: false,
      },
    }, assetKeys);
    const scaled = applyOperation(placed, {
      type: "SCALE_OBJECT",
      id: "tree-1",
      scale: 1.5,
    }, assetKeys);
    const rotated = applyOperation(scaled, {
      type: "ROTATE_OBJECT",
      id: "tree-1",
      rotationDegrees: 45,
    }, assetKeys);
    const moved = applyOperation(rotated, {
      type: "MOVE_OBJECT",
      id: "tree-1",
      x: 4000,
      y: 3180,
    }, assetKeys);

    expect(moved.map.objects[0]).toMatchObject({
      x: 4000,
      y: 3180,
      scale: 1.5,
      rotationDegrees: 45,
    });

    const slopedMap = structuredClone(moved.map);
    slopedMap.terrain.samples.find(sample => sample.x === 3560)!.height = 3000;
    const movedOnSlope = applyOperation({ ...moved, map: slopedMap }, {
      type: "MOVE_OBJECT",
      id: "tree-1",
      x: 3600,
      y: 3000,
    }, assetKeys);
    expect(movedOnSlope.map.objects[0]).toMatchObject({ x: 3600, y: 3000 });
    expect(validate(movedOnSlope, assetKeys)).toEqual([]);
  });

  it("commits a corner scale and rotation as one decoration transform", () => {
    const map = defaultMap();
    map.objects.push({
      id: "tree-1",
      type: "DECORATION",
      x: 3600,
      y: 3180,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "terrain_green_frontier_tree_00",
      collision: "NONE",
      allowOverlap: false,
    });
    const assetKeys = parseAssetIndex("terrain_green_frontier_tree_00.png");
    const document = { map, phase: "TERRAIN_LOCKED" as const, revision: 0 };

    const updated = applyOperation(document, {
      type: "TRANSFORM_OBJECT",
      id: "tree-1",
      scale: 1.5,
      rotationDegrees: 45,
      y: 3220,
    }, assetKeys);

    expect(updated.map.objects[0]).toMatchObject({
      scale: 1.5,
      rotationDegrees: 45,
      y: 3220,
    });
    expect(map.objects[0]).toMatchObject({ scale: 1, rotationDegrees: 0, y: 3180 });
  });

  it("places registered Relics inside soil only after terrain is locked", () => {
    const relicCatalog = parseAssetIndex(
      "terrain_green_frontier_relic_01_cracked_watering_can.png",
    );
    const document = {
      map: defaultMap(),
      phase: "TERRAIN_LOCKED" as const,
      revision: 0,
    };
    const relic = {
      id: "relic-1",
      type: "DECORATION" as const,
      x: 3600,
      y: 3250,
      scale: 1,
      rotationDegrees: 0,
      assetKey: "relic.green_frontier.small.1",
      collision: "NONE" as const,
      allowOverlap: false,
      relicSize: "SMALL" as const,
    };

    const updated = applyOperation(document, {
      type: "PLACE_OBJECT",
      object: relic,
    }, relicCatalog);
    expect(updated.phase).toBe("DECORATION_EDITING");
    expect(updated.map.objects).toContainEqual(relic);
    expect(() => applyOperation({
      ...document,
      phase: "TERRAIN_EDITING",
    }, {
      type: "PLACE_OBJECT",
      object: relic,
    }, relicCatalog)).toThrow("Lock terrain before placing decorations");
    expect(() => applyOperation(document, {
      type: "PLACE_OBJECT",
      object: { ...relic, id: "buried-too-shallow", y: 3200 },
    }, relicCatalog)).toThrow("Relic footprint must be fully inside soil");
  });
});
