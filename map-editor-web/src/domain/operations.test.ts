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
    expect(map.catapults.map(catapult => catapult.x)).toEqual([450, 6750]);
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
    expect(map.catapults.map(catapult => catapult.x)).toEqual([450, 6750]);
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
});
