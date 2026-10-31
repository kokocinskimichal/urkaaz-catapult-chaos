import { describe, expect, it } from "vitest";
import type { CampaignChapterCatalogEntry } from "@studio/contracts/campaign";
import { campaignMapIdForSelection, campaignMapSelectionFromId } from "./campaignMap";

const chapters: CampaignChapterCatalogEntry[] = [
  {
    id: "green_frontier",
    title: "The Green Frontier",
    firstLevel: 1,
    lastLevel: 10,
    biome: "green_frontier",
    bossPositions: [5, 10],
    mapAvailable: true,
  },
  {
    id: "broken_valley",
    title: "The Broken Valley",
    firstLevel: 11,
    lastLevel: 20,
    biome: "frostbound",
    bossPositions: [5, 10],
    mapAvailable: false,
  },
];

describe("campaign map IDs", () => {
  it("uses the ordered chapter number and global level number", () => {
    expect(
      campaignMapIdForSelection(chapters, { chapterId: "broken_valley", level: 11 }),
    ).toBe("chapter2-level11");
  });

  it("recovers a valid chapter and level selection from a campaign map ID", () => {
    expect(campaignMapSelectionFromId("chapter1-level3", chapters)).toEqual({
      chapterId: "green_frontier",
      level: 3,
    });
  });

  it("rejects campaign map IDs whose level is outside the chapter range", () => {
    expect(campaignMapSelectionFromId("chapter2-level3", chapters)).toBeNull();
    expect(() =>
      campaignMapIdForSelection(chapters, { chapterId: "broken_valley", level: 3 }),
    ).toThrow("outside chapter 'broken_valley'");
  });
});
