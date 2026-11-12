import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  readTutorialCatalog,
  saveTutorialCatalog,
  TutorialConflictError,
  TutorialRegistryError,
  validateTutorialContent,
} from "./tutorialCatalog.ts";
import { resolveRepoRoot } from "./repoRoot.ts";

let repoRoot: string;

const validCatalog = JSON.stringify({
  schemaVersion: 1,
  revision: "tutorial-1",
  registeredMechanicIds: ["unit.defender"],
  registeredEventKinds: ["boss.spawned"],
  registeredMilestoneIds: ["campaign_spell_haste"],
  targets: [{ targetId: "match.boss", screen: "MATCH", kind: "BATTLEFIELD" }],
  tutorials: [{
    tutorialId: "match.boss",
    scope: "MATCH",
    enabled: true,
    trigger: { type: "MATCH_EVENT_OBSERVED", eventKind: "boss.spawned" },
    requires: [],
    priority: 1,
    introduces: ["unit.defender"],
    rules: {
      pauseSimulationOnHint: true,
      suspendAiWhileActive: true,
      restrictAiToIntroducedMechanics: false,
    },
    steps: [{
      stepId: "boss",
      targetId: "match.boss",
      text: { pl: "Boss", en: "Boss", es: "Jefe" },
      placementPreference: "AUTO",
      completion: { type: "NEXT" },
      unlocksTargets: [],
      supplementalContent: null,
    }],
  }],
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-tutorial-"));
  const directory = join(repoRoot, ...[
    "game-application", "src", "main", "resources", "tutorial",
  ]);
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "tutorial-catalog.json"), validCatalog);
});

afterEach(async () => {
  await rm(repoRoot, { recursive: true, force: true });
});

describe("tutorial catalog", () => {
  it("validates the full tutorial contract and rejects unknown authored IDs", () => {
    expect(validateTutorialContent(validCatalog)).toEqual({ valid: true, issues: [] });
    const invalid = JSON.parse(validCatalog) as Record<string, unknown>;
    const tutorials = invalid.tutorials as Array<Record<string, unknown>>;
    const tutorial = tutorials[0];
    tutorial.trigger = { type: "MATCH_EVENT_OBSERVED", eventKind: "arbitrary.event" };
    expect(validateTutorialContent(JSON.stringify(invalid)).issues)
      .toEqual(expect.arrayContaining([
        expect.objectContaining({ path: "tutorials[0].trigger.eventKind" }),
      ]));
  });

  it("rejects missing locales, unknown targets, and dependency cycles", () => {
    const invalid = JSON.parse(validCatalog) as Record<string, unknown>;
    const tutorials = invalid.tutorials as Array<Record<string, unknown>>;
    const first = tutorials[0];
    first.requires = ["second"];
    const steps = first.steps as Array<Record<string, unknown>>;
    const step = steps[0];
    step.targetId = "missing.target";
    step.text = { pl: "Krok", en: "Step" };
    tutorials.push({
      ...first,
      tutorialId: "second",
      trigger: { type: "SCREEN_ENTERED", screen: "DASHBOARD" },
      requires: ["match.boss"],
    });

    const validation = validateTutorialContent(JSON.stringify(invalid));
    expect(validation.valid).toBe(false);
    expect(validation.issues.map(item => item.path)).toEqual(expect.arrayContaining([
      "tutorials[0].steps[0].targetId",
      "tutorials[0].steps[0].text",
      "tutorials.match.boss.requires",
    ]));
    expect(validation.issues.some(item => item.message.includes("cycle"))).toBe(true);
  });

  it("rejects step targets that belong to a different trigger screen", () => {
    const invalid = JSON.parse(validCatalog) as Record<string, unknown>;
    const targets = invalid.targets as Array<Record<string, unknown>>;
    targets[0].screen = "DASHBOARD";

    const validation = validateTutorialContent(JSON.stringify(invalid));

    expect(validation.valid).toBe(false);
    expect(validation.issues).toContainEqual(expect.objectContaining({
      path: "tutorials[0].steps[0].targetId",
      message: expect.stringContaining("expected MATCH"),
    }));
  });

  it("rejects triggers outside their declared tutorial scope", () => {
    const invalid = JSON.parse(validCatalog) as Record<string, unknown>;
    const tutorials = invalid.tutorials as Array<Record<string, unknown>>;
    tutorials[0].scope = "SCREEN";

    const validation = validateTutorialContent(JSON.stringify(invalid));

    expect(validation.valid).toBe(false);
    expect(validation.issues).toContainEqual(expect.objectContaining({
      path: "tutorials[0].scope",
      message: expect.stringContaining("must be MATCH"),
    }));
  });

  it("rejects a screen trigger whose target screen differs from the entered screen", () => {
    const invalid = JSON.parse(validCatalog) as Record<string, unknown>;
    const targets = invalid.targets as Array<Record<string, unknown>>;
    const tutorials = invalid.tutorials as Array<Record<string, unknown>>;
    const tutorial = tutorials[0];
    tutorial.scope = "SCREEN";
    tutorial.trigger = { type: "SCREEN_ENTERED", screen: "DASHBOARD" };
    targets[0].screen = "LOADOUT";

    const validation = validateTutorialContent(JSON.stringify(invalid));

    expect(validation.valid).toBe(false);
    expect(validation.issues).toContainEqual(expect.objectContaining({
      path: "tutorials[0].steps[0].targetId",
      message: expect.stringContaining("expected DASHBOARD"),
    }));
  });

  it("maps screen and campaign milestone scopes to their presentation screens", () => {
    const screenTutorial = JSON.parse(validCatalog) as Record<string, unknown>;
    const screenTargets = screenTutorial.targets as Array<Record<string, unknown>>;
    const screenTutorials = screenTutorial.tutorials as Array<Record<string, unknown>>;
    const screenTutorialDefinition = screenTutorials[0];
    screenTutorialDefinition.scope = "SCREEN";
    screenTutorialDefinition.trigger = {
      type: "CAMPAIGN_MILESTONE_APPLIED",
      milestoneId: "campaign_spell_haste",
    };
    screenTargets[0].targetId = "loadout.start";
    screenTargets[0].screen = "LOADOUT";
    const screenSteps = screenTutorialDefinition.steps as Array<Record<string, unknown>>;
    screenSteps[0].targetId = "loadout.start";

    expect(validateTutorialContent(JSON.stringify(screenTutorial)).valid).toBe(true);

    const matchTutorial = JSON.parse(validCatalog) as Record<string, unknown>;
    const matchTargets = matchTutorial.targets as Array<Record<string, unknown>>;
    const matchTutorials = matchTutorial.tutorials as Array<Record<string, unknown>>;
    const matchTutorialDefinition = matchTutorials[0];
    matchTutorialDefinition.trigger = {
      type: "CAMPAIGN_MILESTONE_APPLIED",
      milestoneId: "campaign_spell_haste",
    };

    expect(validateTutorialContent(JSON.stringify(matchTutorial)).valid).toBe(true);
    matchTargets[0].screen = "LOADOUT";
    expect(validateTutorialContent(JSON.stringify(matchTutorial)).issues)
      .toContainEqual(expect.objectContaining({
        path: "tutorials[0].steps[0].targetId",
        message: expect.stringContaining("expected MATCH"),
      }));
  });

  it("reads exact content and persists atomically with a new revision", async () => {
    const loaded = await readTutorialCatalog(repoRoot);
    const changed = validCatalog.replace('"revision":"tutorial-1"', '"revision":"tutorial-1"')
      .replace('"priority":1', '"priority":2');

    const saved = await saveTutorialCatalog(repoRoot, {
      content: changed,
      expectedHash: loaded.hash,
    });

    expect(JSON.parse(saved.content)).toMatchObject({ revision: "tutorial-2" });
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/tutorial/tutorial-catalog.json"), "utf8"),
    ).resolves.toBe(saved.content);
  });

  it("rejects stale writes without changing the catalog", async () => {
    const changed = validCatalog.replace('"priority":1', '"priority":2');

    await expect(saveTutorialCatalog(repoRoot, {
      content: changed,
      expectedHash: "stale",
    })).rejects.toBeInstanceOf(TutorialConflictError);
    await expect(
      readFile(join(repoRoot, "game-application/src/main/resources/tutorial/tutorial-catalog.json"), "utf8"),
    ).resolves.toBe(validCatalog);
  });

  it("does not allow Studio saves to add application-owned registry IDs", async () => {
    const loaded = await readTutorialCatalog(repoRoot);
    const changed = JSON.parse(validCatalog) as Record<string, unknown>;
    (changed.registeredEventKinds as string[]).push("unregistered.event");

    await expect(saveTutorialCatalog(repoRoot, {
      content: JSON.stringify(changed),
      expectedHash: loaded.hash,
    })).rejects.toBeInstanceOf(TutorialRegistryError);
  });

  it("accepts the checked-in Kotlin runtime catalog", async () => {
    const loaded = await readTutorialCatalog(await resolveRepoRoot());
    expect(validateTutorialContent(loaded.content)).toEqual({ valid: true, issues: [] });
  });
});
