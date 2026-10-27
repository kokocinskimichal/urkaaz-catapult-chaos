import { describe, expect, it } from "vitest";
import { desaturateTeamTexture, TEAM_TEXTURE_SATURATION } from "./teamTexture";

describe("team texture coloring", () => {
  it("reduces blue and red saturation while preserving luminance", () => {
    for (const [red, green, blue] of [[30, 80, 200], [190, 35, 35]]) {
      const toned = desaturateTeamTexture(red, green, blue);
      const luminance = (red * 299 + green * 587 + blue * 114) / 1000;
      const tonedLuminance = (toned[0] * 299 + toned[1] * 587 + toned[2] * 114) / 1000;

      expect(tonedLuminance).toBeCloseTo(luminance);
      expect(toned[0] - luminance).toBeCloseTo((red - luminance) * TEAM_TEXTURE_SATURATION);
      expect(toned[1] - luminance).toBeCloseTo((green - luminance) * TEAM_TEXTURE_SATURATION);
      expect(toned[2] - luminance).toBeCloseTo((blue - luminance) * TEAM_TEXTURE_SATURATION);
    }
  });

  it("leaves neutral texture pixels unchanged", () => {
    expect(desaturateTeamTexture(80, 80, 80)).toEqual([80, 80, 80]);
  });
});
