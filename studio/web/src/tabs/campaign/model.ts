import type {
  CampaignChapterCatalogEntry,
  CampaignLandmarkKind,
  CampaignMapAnchor,
  CampaignMapLayout,
} from "@studio/contracts/campaign";

export interface CampaignMapBounds {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface CampaignPixelPoint {
  x: number;
  y: number;
}

export interface CampaignMarkerBounds {
  level: number;
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export function parseCampaignMapLayout(
  content: string,
  chapter: CampaignChapterCatalogEntry,
): CampaignMapLayout {
  const parsed: unknown = JSON.parse(content);
  if (!isRecord(parsed)) throw new Error("Campaign layout must be a JSON object");
  if (!hasOnlyKeys(parsed, [
    "schemaVersion",
    "chapterId",
    "backgroundAssetKey",
    "imageWidthPx",
    "imageHeightPx",
    "anchors",
  ])) {
    throw new Error("Campaign layout contains unsupported fields");
  }
  if (parsed.schemaVersion !== 1 || parsed.chapterId !== chapter.id) {
    throw new Error("Campaign layout schema or chapter ID is invalid");
  }
  const imageWidthPx = parsed.imageWidthPx;
  const imageHeightPx = parsed.imageHeightPx;
  if (typeof parsed.backgroundAssetKey !== "string" ||
    typeof imageWidthPx !== "number" || !Number.isInteger(imageWidthPx) || imageWidthPx <= 0 ||
    typeof imageHeightPx !== "number" || !Number.isInteger(imageHeightPx) || imageHeightPx <= 0 ||
    !Array.isArray(parsed.anchors)
  ) {
    throw new Error("Campaign layout map metadata is invalid");
  }
  const expectedLevels = Array.from(
    { length: chapter.lastLevel - chapter.firstLevel + 1 },
    (_, index) => chapter.firstLevel + index,
  );
  if (parsed.anchors.length !== expectedLevels.length) {
    throw new Error("Campaign layout must contain every chapter level");
  }
  const anchors = parsed.anchors.map((value, index): CampaignMapAnchor => {
    if (!isRecord(value) ||
      !hasOnlyKeys(value, ["level", "x", "y"]) ||
      typeof value.level !== "number" || value.level !== expectedLevels[index] ||
      typeof value.x !== "number" || !Number.isFinite(value.x) || value.x < 0 || value.x > 1 ||
      typeof value.y !== "number" || !Number.isFinite(value.y) || value.y < 0 || value.y > 1
    ) {
      throw new Error(`Campaign anchor ${expectedLevels[index]} is invalid`);
    }
    return { level: value.level, x: value.x, y: value.y };
  });
  return {
    schemaVersion: 1,
    chapterId: chapter.id,
    backgroundAssetKey: parsed.backgroundAssetKey,
    imageWidthPx,
    imageHeightPx,
    anchors,
  };
}

export function encodeCampaignMapLayout(layout: CampaignMapLayout): string {
  return `${JSON.stringify(layout, null, 2)}\n`;
}

export function chapterLandmarkKind(
  chapter: CampaignChapterCatalogEntry,
  level: number,
): CampaignLandmarkKind {
  const position = level - chapter.firstLevel + 1;
  return chapter.bossPositions.includes(position) ? "BOSS" : "FORTRESS";
}

export function coverMapBounds(
  viewportWidth: number,
  viewportHeight: number,
  imageWidth: number,
  imageHeight: number,
): CampaignMapBounds {
  const scale = Math.max(viewportWidth / imageWidth, viewportHeight / imageHeight);
  const width = imageWidth * scale;
  const height = imageHeight * scale;
  return {
    left: (viewportWidth - width) / 2,
    top: (viewportHeight - height) / 2,
    width,
    height,
  };
}

export function anchorToScreen(
  bounds: CampaignMapBounds,
  anchor: Pick<CampaignMapAnchor, "x" | "y">,
): CampaignPixelPoint {
  return {
    x: bounds.left + bounds.width * anchor.x,
    y: bounds.top + bounds.height * anchor.y,
  };
}

export function screenToAnchor(
  bounds: CampaignMapBounds,
  point: CampaignPixelPoint,
): Pick<CampaignMapAnchor, "x" | "y"> {
  return {
    x: clamp((point.x - bounds.left) / bounds.width),
    y: clamp((point.y - bounds.top) / bounds.height),
  };
}

export function findOverlappingMarkers(
  bounds: CampaignMarkerBounds[],
): Array<[number, number]> {
  const overlaps: Array<[number, number]> = [];
  for (let first = 0; first < bounds.length; first += 1) {
    for (let second = first + 1; second < bounds.length; second += 1) {
      const a = bounds[first];
      const b = bounds[second];
      if (a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top) {
        overlaps.push([a.level, b.level]);
      }
    }
  }
  return overlaps;
}

export function markerDisplaySize(kind: CampaignLandmarkKind): number {
  return kind === "BOSS" ? 76 : 100;
}

export function markerVisualBounds(
  level: number,
  center: CampaignPixelPoint,
  kind: CampaignLandmarkKind,
): CampaignMarkerBounds {
  const markerSize = markerDisplaySize(kind);
  const iconSize = kind === "BOSS" ? 48 : 72;
  const visibleIconSize = iconSize * 0.82;
  const markerTop = center.y - markerSize / 2;
  const iconTop = markerTop + (iconSize - visibleIconSize) / 2;
  const plateTop = markerTop + iconSize - 12;
  const halfWidth = Math.max(visibleIconSize / 2, 28);
  return {
    level,
    left: center.x - halfWidth,
    top: Math.min(iconTop, plateTop),
    right: center.x + halfWidth,
    bottom: Math.max(iconTop + visibleIconSize, plateTop + 22),
  };
}

function clamp(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: readonly string[]): boolean {
  return Object.keys(value).every(key => allowed.includes(key));
}
