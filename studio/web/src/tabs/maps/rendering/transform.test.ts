import { describe, expect, it } from "vitest";
import {
  rotatedRectangleCorners,
  screenToWorld,
  transformFromCornerDrag,
  worldHeightToScreen,
  worldToScreen,
  worldWidthToScreen,
} from "./transform";

describe("map viewport transforms", () => {
  it("fills the viewport with independent world-axis scales like the match renderer", () => {
    const view = { zoom: 1, panX: 0, panY: 0 };
    expect(worldToScreen(7200, 4800, view, 1200, 800)).toEqual({ x: 1200, y: 800 });
    expect(worldToScreen(3600, 2400, view, 1200, 800)).toEqual({ x: 600, y: 400 });
    expect(worldToScreen(7200, 4800, view, 900, 900)).toEqual({ x: 900, y: 900 });
  });

  it("round-trips pointer coordinates with non-uniform scaling, zoom, and pan", () => {
    const view = { zoom: 1.3, panX: -45, panY: 28 };
    const screen = worldToScreen(1720, 3140, view, 900, 700);
    const world = screenToWorld(screen.x, screen.y, view, 900, 700);
    expect(world.x).toBeCloseTo(1720);
    expect(world.y).toBeCloseTo(3140);
  });

  it("scales fortress and catapult dimensions in world coordinates", () => {
    const view = { zoom: 1, panX: 0, panY: 0 };
    expect(worldWidthToScreen(672, view, 1200)).toBeCloseTo(112);
    expect(worldHeightToScreen(504, view, 800)).toBeCloseTo(84);
    expect(worldWidthToScreen(182.4, view, 1200)).toBeCloseTo(30.4);
  });
});

describe("decoration corner transforms", () => {
  it("returns the rotated corners of the selection frame", () => {
    const corners = rotatedRectangleCorners({ x: 50, y: 50 }, 20, 10, 90);
    expect(corners).toHaveLength(4);
    expect(corners[0].x).toBeCloseTo(55);
    expect(corners[0].y).toBeCloseTo(40);
    expect(corners[2].x).toBeCloseTo(45);
    expect(corners[2].y).toBeCloseTo(60);
  });

  it("scales radially and rotates angularly around the selection center", () => {
    expect(transformFromCornerDrag(
      { x: 10, y: 0 },
      { x: 0, y: 20 },
      { x: 0, y: 0 },
      1,
      15,
    )).toEqual({ scale: 2, rotationDegrees: 105 });
  });

  it("keeps scale positive when a corner is dragged through the center", () => {
    expect(transformFromCornerDrag(
      { x: 10, y: 0 },
      { x: 0, y: 0 },
      { x: 0, y: 0 },
      1,
      0,
    ).scale).toBe(0.1);
  });
});
