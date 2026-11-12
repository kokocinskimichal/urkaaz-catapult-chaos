export type TutorialLanguage = "pl" | "en" | "es";
export type TutorialScreen = "DASHBOARD" | "CAMPAIGN" | "LOADOUT" | "MATCH" | "SHOP";
export type TutorialTargetKind = "VIEW" | "BATTLEFIELD";
export type TutorialScope = "SCREEN" | "MATCH";
export type TutorialPlacementPreference = "AUTO" | "ABOVE" | "BELOW" | "LEFT" | "RIGHT";
export type TutorialStatus = "COMPLETED" | "SKIPPED";
export type TutorialPlayerActionKind =
  | "FIRE"
  | "DEPLOY_UNIT"
  | "SEND_WAVE"
  | "CAST_SPELL"
  | "SELECT_AMMUNITION";

export interface TutorialCatalog {
  schemaVersion: 1;
  revision: string;
  registeredMechanicIds: string[];
  registeredEventKinds: string[];
  registeredMilestoneIds: string[];
  targets: TutorialTargetDefinition[];
  tutorials: TutorialDefinition[];
}

export interface TutorialTargetDefinition {
  targetId: string;
  screen: TutorialScreen;
  kind: TutorialTargetKind;
}

export interface TutorialDefinition {
  tutorialId: string;
  scope: TutorialScope;
  enabled: boolean;
  trigger: TutorialTrigger;
  requires: string[];
  priority: number;
  introduces: string[];
  rules: TutorialRuleSet;
  steps: TutorialStep[];
}

export type TutorialTrigger =
  | { type: "SCREEN_ENTERED"; screen: TutorialScreen }
  | {
    type: "MATCH_STARTED";
    campaignLevelRange: { first: number; last: number } | null;
    requiresMechanicId: string | null;
  }
  | { type: "MATCH_EVENT_OBSERVED"; eventKind: string }
  | { type: "CAMPAIGN_MILESTONE_APPLIED"; milestoneId: string };

export interface TutorialStep {
  stepId: string;
  targetId: string | null;
  text: Record<TutorialLanguage, string>;
  placementPreference: TutorialPlacementPreference;
  completion: TutorialStepCompletion;
  unlocksTargets: string[];
  supplementalContent: "WIND_STRENGTH_LEGEND" | null;
}

export type TutorialStepCompletion =
  | { type: "NEXT" }
  | { type: "PLAYER_ACTION"; kind: TutorialPlayerActionKind; count: number }
  | { type: "MATCH_EVENT_COUNT"; eventKind: string; count: number };

export interface TutorialRuleSet {
  pauseSimulationOnHint: boolean;
  suspendAiWhileActive: boolean;
  restrictAiToIntroducedMechanics: boolean;
}

export interface TutorialDocumentResponse {
  content: string;
  hash: string;
}

export interface TutorialSaveRequest {
  content: string;
  expectedHash: string;
}

export interface TutorialValidationIssue {
  severity: "error";
  path: string;
  message: string;
}

export interface TutorialValidationResponse {
  valid: boolean;
  issues: TutorialValidationIssue[];
}
