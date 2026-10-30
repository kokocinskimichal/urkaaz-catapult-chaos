import { describe, expect, it } from "vitest";
import type { CampaignChapterCatalogEntry, CampaignMapLayout } from "@studio/contracts/campaign";
import {
  anchorToScreen,
  chapterLandmarkKind,
  coverMapBounds,
  encodeCampaignMapLayout,
  findOverlappingMarkers,
  parseCampaignMapLayout,
  markerVisualBounds,
  screenToAnchor,
} from "./model";

const chapter: CampaignChapterCatalogEntry = {
  id: "broken_valley",
  title: "The Broken Valley",
  firstLevel: 11,
  lastLevel: 20,
  biome: "frostbound",
  bossPositions: [5, 10],
  mapAvailable: true,
};

const layout: CampaignMapLayout = {
  schemaVersion: 1,
  chapterId: chapter.id,
  backgroundAssetKey: "campaign_chapter_02_broken_valley_map",
  imageWidthPx: 1600,
  imageHeightPx: 900,
  anchors: Array.from({ length: 10 }, (_, index) => ({
    level: 11 + index,
    x: 0.1 + index * 0.08,
    y: 0.2 + index * 0.05,
  })),
};

describe("campaign map editor model", () => {
  it("round-trips normalized anchors through a center-cropped preview", () => {
    const bounds = coverMapBounds(1200, 700, 1600, 900);
    const anchor = { x: 0.255, y: 0.545 };
    const screen = anchorToScreen(bounds, anchor);
    const restored = screenToAnchor(bounds, screen);

    expect(restored.x).toBeCloseTo(anchor.x);
    expect(restored.y).toBeCloseTo(anchor.y);
    expect(bounds.width).toBeGreaterThan(1200);
    expect(bounds.height).toBe(700);
  });

  it("validates complete chapter-global level IDs and serializes stable JSON", () => {
    const parsed = parseCampaignMapLayout(encodeCampaignMapLayout(layout), chapter);

    expect(parsed.anchors.map(anchor => anchor.level)).toEqual(
      Array.from({ length: 10 }, (_, index) => index + 11),
    );
    expect(chapterLandmarkKind(chapter, 15)).toBe("BOSS");
    expect(chapterLandmarkKind(chapter, 20)).toBe("BOSS");
    expect(chapterLandmarkKind(chapter, 14)).toBe("FORTRESS");
  });

  it("rejects malformed or out-of-range anchors", () => {
    const invalid = { ...layout, anchors: layout.anchors.slice(0, 9) };
    expect(() => parseCampaignMapLayout(JSON.stringify(invalid), chapter))
      .toThrow("every chapter level");

    const invalidCoordinate: CampaignMapLayout = {
      ...layout,
      anchors: layout.anchors.map((anchor, index) =>
        index === 0 ? { ...anchor, x: 2 } : anchor,
      ),
    };
    expect(() => parseCampaignMapLayout(JSON.stringify(invalidCoordinate), chapter))
      .toThrow("Campaign anchor 11 is invalid");
  });

  it("reports visual marker overlaps without modifying the layout", () => {
    const warnings = findOverlappingMarkers([
      { level: 11, left: 100, top: 100, right: 200, bottom: 200 },
      { level: 12, left: 190, top: 150, right: 270, bottom: 230 },
      { level: 13, left: 300, top: 300, right: 400, bottom: 400 },
    ]);

    expect(warnings).toEqual([[11, 12]]);
    expect(layout.anchors[0].x).toBe(0.1);
  });

  it("does not warn for the authored Chapter 1 spacing at the game landscape viewport", () => {
    const greenFrontier: CampaignChapterCatalogEntry = {
      id: "green_frontier",
      title: "The Green Frontier",
      firstLevel: 1,
      lastLevel: 10,
      biome: "green_frontier",
      bossPositions: [5, 10],
      mapAvailable: true,
    };
    const anchors = [
      [0.145, 0.750],
      [0.255, 0.545],
      [0.365, 0.365],
      [0.485, 0.260],
      [0.645, 0.330],
      [0.845, 0.245],
      [0.855, 0.455],
      [0.755, 0.675],
      [0.565, 0.750],
      [0.485, 0.475],
    ];
    const viewport = coverMapBounds(914, 411, 1608, 905);
    const bounds = anchors.map(([x, y], index) =>
      markerVisualBounds(
        index + 1,
        anchorToScreen(viewport, { x, y }),
        chapterLandmarkKind(greenFrontier, index + 1),
      ),
    );

    expect(findOverlappingMarkers(bounds)).toEqual([]);
  });
});
