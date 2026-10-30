export interface BalanceDocumentResponse {
  content: string;
  hash: string;
}

export interface BalanceSaveRequest {
  content: string;
  expectedHash: string;
}

export interface BalanceValidationIssue {
  severity: "error";
  path: string;
  message: string;
}

export interface BalanceValidationResponse {
  valid: boolean;
  issues: BalanceValidationIssue[];
}
