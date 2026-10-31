import type { CampaignChapterCatalogEntry } from "@studio/contracts/campaign";

export interface CampaignMapSelection {
  chapterId: string;
  level: number;
}

export function campaignMapIdForSelection(
  chapters: readonly CampaignChapterCatalogEntry[],
  selection: CampaignMapSelection,
): string {
  const chapterIndex = chapters.findIndex(chapter => chapter.id === selection.chapterId);
  if (chapterIndex < 0) throw new Error(`Unknown campaign chapter '${selection.chapterId}'`);

  const chapter = chapters[chapterIndex];
  if (!Number.isInteger(selection.level) ||
    selection.level < chapter.firstLevel ||
    selection.level > chapter.lastLevel) {
    throw new Error(`Level ${selection.level} is outside chapter '${chapter.id}'`);
  }

  return `chapter${chapterIndex + 1}-level${selection.level}`;
}

export function campaignMapSelectionFromId(
  mapId: string,
  chapters: readonly CampaignChapterCatalogEntry[],
): CampaignMapSelection | null {
  const match = /^chapter([1-9]\d*)-level(\d+)$/.exec(mapId);
  if (!match) return null;

  const chapter = chapters[Number(match[1]) - 1];
  const level = Number(match[2]);
  if (!chapter || level < chapter.firstLevel || level > chapter.lastLevel) return null;

  return { chapterId: chapter.id, level };
}
