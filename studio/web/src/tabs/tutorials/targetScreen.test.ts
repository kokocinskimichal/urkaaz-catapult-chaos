import { describe, expect, it } from "vitest";
import { tutorialTargetScreen } from "./targetScreen.ts";

describe("tutorialTargetScreen", () => {
  it("uses the entered screen for screen tutorials", () => {
    expect(tutorialTargetScreen(
      "SCREEN",
      { type: "SCREEN_ENTERED", screen: "DASHBOARD" },
    )).toBe("DASHBOARD");
  });

  it("uses the match screen for match triggers", () => {
    expect(tutorialTargetScreen(
      "MATCH",
      { type: "MATCH_STARTED", campaignLevelRange: null, requiresMechanicId: null },
    )).toBe("MATCH");
    expect(tutorialTargetScreen(
      "MATCH",
      { type: "MATCH_EVENT_OBSERVED", eventKind: "boss.spawned" },
    )).toBe("MATCH");
  });

  it("maps campaign milestone tutorials to their scope screen", () => {
    const trigger = {
      type: "CAMPAIGN_MILESTONE_APPLIED" as const,
      milestoneId: "campaign_spell_haste",
    };

    expect(tutorialTargetScreen("SCREEN", trigger)).toBe("LOADOUT");
    expect(tutorialTargetScreen("MATCH", trigger)).toBe("MATCH");
  });

  it("does not offer targets for incompatible trigger scopes", () => {
    expect(tutorialTargetScreen(
      "MATCH",
      { type: "SCREEN_ENTERED", screen: "DASHBOARD" },
    )).toBeNull();
    expect(tutorialTargetScreen(
      "SCREEN",
      { type: "MATCH_STARTED", campaignLevelRange: null, requiresMechanicId: null },
    )).toBeNull();
  });
});
