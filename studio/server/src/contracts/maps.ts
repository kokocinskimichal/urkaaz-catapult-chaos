export interface MapCatalogEntry {
  id: string;
  biome: string | null;
  synchronized: boolean;
}

export interface MapCatalogResponse {
  maps: MapCatalogEntry[];
}

export interface MapDocumentResponse {
  id: string;
  content: string;
  hash: string;
  synchronized: boolean;
}

export interface MapSaveRequest {
  content: string;
  expectedHash: string | null;
}

export interface MapValidationIssue {
  severity: "error" | "warning";
  path: string;
  message: string;
}

export interface MapValidationResponse {
  valid: boolean;
  issues: MapValidationIssue[];
}
