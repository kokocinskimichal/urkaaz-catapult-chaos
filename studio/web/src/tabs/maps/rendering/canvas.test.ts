import { describe, expect, it } from "vitest";
import {
  CATAPULT_RENDER_WIDTH,
  FORTRESS_RENDER_HEIGHT,
  FORTRESS_RENDER_WIDTH,
  isImageDrawable,
  MAP_RELIC_RENDER_SIZES,
  MAP_DECORATION_RENDER_WIDTH,
  mapDecorationRenderSize,
  mapRelicRenderSize,
} from "./canvas";

describe("map image loading", () => {
  it("only returns images that completed with decoded content", () => {
    expect(isImageDrawable({ complete: true, naturalWidth: 128 })).toBe(true);
    expect(isImageDrawable({ complete: false, naturalWidth: 128 })).toBe(false);
    expect(isImageDrawable({ complete: true, naturalWidth: 0 })).toBe(false);
  });
});

describe("map decoration rendering size", () => {
  it("matches the game renderer's 94-unit base width and preserves the sprite aspect ratio", () => {
    expect(MAP_DECORATION_RENDER_WIDTH).toBe(94);
    expect(mapDecorationRenderSize(1, 1, 1.5)).toEqual({ width: 94, height: 141 });
    expect(mapDecorationRenderSize(1, 1, 1.5, 2)).toEqual({ width: 47, height: 70.5 });
    expect(mapDecorationRenderSize(2, 1.5, 1.5, 2)).toEqual({ width: 141, height: 211.5 });
  });

  it("uses the shared world-space fortress and catapult sizes", () => {
    expect([FORTRESS_RENDER_WIDTH, FORTRESS_RENDER_HEIGHT]).toEqual([672, 504]);
    expect(CATAPULT_RENDER_WIDTH).toBeCloseTo(182.4);
  });

  it("renders relics at the same viewport-space sizes as the match renderer", () => {
    expect(MAP_RELIC_RENDER_SIZES).toEqual({ SMALL: 76, MEDIUM: 112, LARGE: 176 });
    expect(mapRelicRenderSize("SMALL", 1, 1, 1.5)).toEqual({ width: 76, height: 114 });
    expect(mapRelicRenderSize("MEDIUM", 2, 1.5, 1, 2)).toEqual({ width: 168, height: 168 });
  });
});
