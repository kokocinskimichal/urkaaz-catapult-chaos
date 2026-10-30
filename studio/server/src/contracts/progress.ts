export interface ProgressDocumentResponse {
  content: string;
  hash: string;
}

export interface ProgressSaveRequest {
  content: string;
  expectedHash: string;
}

export interface ProgressValidationIssue {
  severity: "error";
  path: string;
  message: string;
}

export interface ProgressValidationResponse {
  valid: boolean;
  issues: ProgressValidationIssue[];
}

export type DeveloperProgressField = "currentLevel" | "lives" | "gold";

export interface DeveloperProgressPreset {
  schemaVersion: 2;
  revision: string;
  currentLevel: number;
  lives: number;
  gold: number;
  fieldsToApply: DeveloperProgressField[];
  applyOnNextLaunch: boolean;
}
