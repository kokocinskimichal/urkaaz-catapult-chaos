import type { AnimationCatalogResponse } from "@studio/contracts/animations";
import type { ApiErrorBody, HealthResponse } from "@studio/contracts/api";
import type {
  CampaignChapterCatalogResponse,
  CampaignMapLayoutDocumentResponse,
  CampaignMapLayoutSaveRequest,
  CampaignMapLayoutValidationResponse,
} from "@studio/contracts/campaign";
import type {
  MapCatalogResponse,
  MapDocumentResponse,
  MapSaveRequest,
  MapValidationResponse,
} from "@studio/contracts/maps";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: string[];

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.status = status;
    this.code = body.code;
    this.details = body.details;
  }
}

export type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export async function requestJson<T>(path: string, fetcher: Fetcher = fetch, init?: RequestInit): Promise<T> {
  const response = await fetcher(path, init);
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, isApiErrorBody(body) ? body : { code: "unknown", message: response.statusText, details: [] });
  }
  return body as T;
}

export const fetchHealth = (fetcher?: Fetcher) => requestJson<HealthResponse>("/api/health", fetcher);
export const fetchAnimationCatalog = (fetcher?: Fetcher) => requestJson<AnimationCatalogResponse>("/api/animations", fetcher);
export const fetchMaps = (fetcher?: Fetcher) => requestJson<MapCatalogResponse>("/api/maps", fetcher);
export const fetchMap = (id: string, fetcher?: Fetcher) =>
  requestJson<MapDocumentResponse>(`/api/maps/${encodeURIComponent(id)}`, fetcher);
export const saveMap = (
  id: string,
  request: MapSaveRequest,
  fetcher?: Fetcher,
) => requestJson<MapDocumentResponse>(`/api/maps/${encodeURIComponent(id)}`, fetcher, {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify(request),
});
export const validateMap = (content: string, fetcher?: Fetcher) =>
  requestJson<MapValidationResponse>("/api/maps/validate", fetcher, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ content }),
  });
export const fetchCampaignChapters = (fetcher?: Fetcher) =>
  requestJson<CampaignChapterCatalogResponse>("/api/campaign/chapters", fetcher);
export const fetchCampaignMapLayout = (chapterId: string, fetcher?: Fetcher) =>
  requestJson<CampaignMapLayoutDocumentResponse>(
    `/api/campaign/layouts/${encodeURIComponent(chapterId)}`,
    fetcher,
  );
export const saveCampaignMapLayout = (
  chapterId: string,
  request: CampaignMapLayoutSaveRequest,
  fetcher?: Fetcher,
) => requestJson<CampaignMapLayoutDocumentResponse>(
  `/api/campaign/layouts/${encodeURIComponent(chapterId)}`,
  fetcher,
  {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
  },
);
export const validateCampaignMapLayout = (
  chapterId: string,
  content: string,
  fetcher?: Fetcher,
) => requestJson<CampaignMapLayoutValidationResponse>("/api/campaign/layouts/validate", fetcher, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ chapterId, content }),
});
export async function fetchTerrainAssetIndex(fetcher: Fetcher = fetch): Promise<string> {
  const response = await fetcher("/api/assets/terrain/index");
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      isApiErrorBody(body)
        ? body
        : { code: "unknown", message: response.statusText, details: [] },
    );
  }
  return response.text();
}

export async function fetchCampaignAsset(assetKey: string, fetcher: Fetcher = fetch): Promise<Blob> {
  const response = await fetcher(`/api/campaign/assets/${encodeURIComponent(assetKey)}`);
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    throw new ApiError(
      response.status,
      isApiErrorBody(body)
        ? body
        : { code: "unknown", message: response.statusText, details: [] },
    );
  }
  return response.blob();
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.details);
}
