import type { AnimationCatalogResponse } from "@studio/contracts/animations";
import type { ApiErrorBody, HealthResponse } from "@studio/contracts/api";

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

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string" && Array.isArray(candidate.details);
}
