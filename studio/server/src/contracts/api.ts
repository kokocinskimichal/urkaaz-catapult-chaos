export interface ApiErrorBody {
  code: string;
  message: string;
  details: string[];
}

export interface HealthResponse {
  version: string;
  repoRoot: string;
  branch: string | null;
  clean: boolean | null;
  changedFiles: number | null;
}
