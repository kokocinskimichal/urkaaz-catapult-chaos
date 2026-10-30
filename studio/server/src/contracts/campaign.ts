export type CampaignLandmarkKind = "FORTRESS" | "BOSS";

export interface CampaignChapterCatalogEntry {
  id: string;
  title: string;
  firstLevel: number;
  lastLevel: number;
  biome: string;
  bossPositions: number[];
  mapAvailable: boolean;
}

export interface CampaignChapterCatalogResponse {
  chapters: CampaignChapterCatalogEntry[];
}

export interface CampaignMapAnchor {
  level: number;
  x: number;
  y: number;
}

export interface CampaignMapLayout {
  schemaVersion: 1;
  chapterId: string;
  backgroundAssetKey: string;
  imageWidthPx: number;
  imageHeightPx: number;
  anchors: CampaignMapAnchor[];
}

export interface CampaignMapLayoutDocumentResponse {
  chapterId: string;
  content: string;
  hash: string;
}

export interface CampaignMapLayoutSaveRequest {
  content: string;
  expectedHash: string;
}

export interface CampaignMapLayoutValidationIssue {
  severity: "error" | "warning";
  path: string;
  message: string;
}

export interface CampaignMapLayoutValidationResponse {
  valid: boolean;
  issues: CampaignMapLayoutValidationIssue[];
}
