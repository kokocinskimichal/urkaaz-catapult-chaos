import type {
  TutorialScreen,
  TutorialScope,
  TutorialTrigger,
} from "@studio/contracts/tutorials";

export function tutorialTargetScreen(
  scope: TutorialScope,
  trigger: TutorialTrigger,
): TutorialScreen | null {
  switch (trigger.type) {
    case "SCREEN_ENTERED":
      return scope === "SCREEN" ? trigger.screen : null;
    case "MATCH_STARTED":
    case "MATCH_EVENT_OBSERVED":
      return scope === "MATCH" ? "MATCH" : null;
    case "CAMPAIGN_MILESTONE_APPLIED":
      return scope === "SCREEN" ? "LOADOUT" : "MATCH";
  }
}
