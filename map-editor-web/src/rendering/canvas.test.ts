import { describe, expect, it } from "vitest";
import {
  MAP_DECORATION_RENDER_WIDTH,
  mapDecorationRenderSize,
} from "./canvas";

describe("map decoration rendering size", () => {
  it("matches the game renderer's 94-unit base width and preserves the sprite aspect ratio", () => {
    expect(MAP_DECORATION_RENDER_WIDTH).toBe(94);
    expect(mapDecorationRenderSize(1, 1, 1.5)).toEqual({ width: 94, height: 141 });
    expect(mapDecorationRenderSize(1, 1, 1.5, 2)).toEqual({ width: 47, height: 70.5 });
    expect(mapDecorationRenderSize(2, 1.5, 1.5, 2)).toEqual({ width: 141, height: 211.5 });
  });
});
