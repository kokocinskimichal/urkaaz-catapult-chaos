import { describe, expect, it } from "vitest";
import { rotatedRectangleCorners, transformFromCornerDrag } from "./transform";

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
