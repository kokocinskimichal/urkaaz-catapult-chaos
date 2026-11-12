import { createHash, randomUUID } from "node:crypto";
import { readFile, realpath, rename, rm, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, join, relative, sep } from "node:path";
import type {
  TutorialCatalog,
  TutorialDocumentResponse,
  TutorialLanguage,
  TutorialSaveRequest,
  TutorialScreen,
  TutorialTargetDefinition,
  TutorialValidationIssue,
  TutorialValidationResponse,
} from "../contracts/tutorials.ts";

export const TUTORIAL_CATALOG_FILE = [
  "game-application",
  "src",
  "main",
  "resources",
  "tutorial",
  "tutorial-catalog.json",
] as const;

const LANGUAGES = new Set<TutorialLanguage>(["pl", "en", "es"]);
const SCREENS = new Set<TutorialScreen>(["DASHBOARD", "CAMPAIGN", "LOADOUT", "MATCH", "SHOP"]);
const TARGET_KINDS = new Set<TutorialTargetDefinition["kind"]>(["VIEW", "BATTLEFIELD"]);
const SCOPES = new Set(["SCREEN", "MATCH"]);
const PLACEMENTS = new Set(["AUTO", "ABOVE", "BELOW", "LEFT", "RIGHT"]);
const ACTIONS = new Set([
  "FIRE", "DEPLOY_UNIT", "SEND_WAVE", "CAST_SPELL", "SELECT_AMMUNITION",
]);
const STABLE_ID = /^[a-z][a-z0-9._-]*$/;
let writeQueue: Promise<void> = Promise.resolve();

export class TutorialConflictError extends Error {
  readonly currentHash: string;

  constructor(currentHash: string) {
    super("Tutorial catalog changed since it was loaded");
    this.currentHash = currentHash;
  }
}

export class TutorialRegistryError extends Error {
  constructor() {
    super("Registered tutorial mechanic, event, and milestone IDs are application-owned");
  }
}

export async function readTutorialCatalog(repoRoot: string): Promise<TutorialDocumentResponse> {
  const path = await tutorialCatalogPath(repoRoot);
  const content = await readFile(path, "utf8");
  return { content, hash: hashContent(content) };
}

export function validateTutorialContent(content: string): TutorialValidationResponse {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch (error) {
    return {
      valid: false,
      issues: [{
        severity: "error",
        path: "tutorialCatalog",
        message: error instanceof Error ? error.message : String(error),
      }],
    };
  }
  const issues: TutorialValidationIssue[] = [];
  const catalog = asRecord(value);
  if (!catalog) return invalid("tutorialCatalog", "must be a JSON object");
  checkKeys(
    catalog,
    "tutorialCatalog",
    [
      "schemaVersion",
      "revision",
      "registeredMechanicIds",
      "registeredEventKinds",
      "registeredMilestoneIds",
      "targets",
      "tutorials",
    ],
    issues,
  );
  if (catalog.schemaVersion !== 1) {
    issues.push(issue("schemaVersion", "must be the supported schema version 1"));
  }
  if (typeof catalog.revision !== "string" || catalog.revision.trim() === "") {
    issues.push(issue("revision", "must be a non-empty string"));
  }
  const mechanicIds = validateIdList(
    catalog.registeredMechanicIds,
    "registeredMechanicIds",
    issues,
  );
  const eventKinds = validateIdList(catalog.registeredEventKinds, "registeredEventKinds", issues);
  const milestoneIds = validateIdList(
    catalog.registeredMilestoneIds,
    "registeredMilestoneIds",
    issues,
  );
  const targets = validateTargets(catalog.targets, issues);
  const targetsById = new Map(targets.map(target => [target.targetId, target]));
  const tutorials = validateTutorials(
    catalog.tutorials,
    targetsById,
    mechanicIds,
    eventKinds,
    milestoneIds,
    issues,
  );
  validateRequirementGraph(tutorials, issues);
  return { valid: issues.length === 0, issues };
}

export async function saveTutorialCatalog(
  repoRoot: string,
  request: TutorialSaveRequest,
): Promise<TutorialDocumentResponse> {
  const queued = writeQueue.then(async () => {
    const current = await readTutorialCatalog(repoRoot);
    if (current.hash !== request.expectedHash) throw new TutorialConflictError(current.hash);
    const validation = validateTutorialContent(request.content);
    if (!validation.valid) throw new Error("Cannot save an invalid tutorial catalog");
    if (hashContent(request.content) === current.hash) return current;

    const currentCatalog = parseCatalog(current.content);
    const nextCatalog = parseCatalog(request.content);
    if (!currentCatalog || !nextCatalog) {
      throw new Error("Tutorial catalog became invalid after validation");
    }
    if (
      JSON.stringify(currentCatalog.registeredMechanicIds) !==
        JSON.stringify(nextCatalog.registeredMechanicIds) ||
      JSON.stringify(currentCatalog.registeredEventKinds) !==
        JSON.stringify(nextCatalog.registeredEventKinds) ||
      JSON.stringify(currentCatalog.registeredMilestoneIds) !==
        JSON.stringify(nextCatalog.registeredMilestoneIds)
    ) {
      throw new TutorialRegistryError();
    }
    if (currentCatalog.revision === nextCatalog.revision) {
      nextCatalog.revision = nextRevision(currentCatalog.revision);
    }
    const content = `${JSON.stringify(nextCatalog, null, 2)}\n`;
    const path = await tutorialCatalogPath(repoRoot);
    const temporaryPath = `${path}.${randomUUID()}.tmp`;
    try {
      await writeFile(temporaryPath, content, { encoding: "utf8", flag: "wx" });
      await rename(temporaryPath, path);
    } finally {
      await rm(temporaryPath, { force: true });
    }
    return readTutorialCatalog(repoRoot);
  });
  writeQueue = queued.then(() => undefined, () => undefined);
  return queued;
}

function validateTargets(
  value: unknown,
  issues: TutorialValidationIssue[],
): TutorialTargetDefinition[] {
  if (!Array.isArray(value)) {
    issues.push(issue("targets", "must be an array"));
    return [];
  }
  const targets: TutorialTargetDefinition[] = [];
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const path = `targets[${index}]`;
    const target = asRecord(value);
    if (!target) {
      issues.push(issue(path, "must be an object"));
      return;
    }
    checkKeys(target, path, ["targetId", "screen", "kind"], issues);
    const targetId = stableIdentifier(target.targetId, `${path}.targetId`, issues);
    const screen = enumValue(target.screen, SCREENS, `${path}.screen`, issues);
    const kind = enumValue(target.kind, TARGET_KINDS, `${path}.kind`, issues);
    if (targetId !== null) {
      if (ids.has(targetId)) issues.push(issue(`${path}.targetId`, "must be unique"));
      ids.add(targetId);
      if (screen !== null && kind !== null) targets.push({ targetId, screen, kind });
    }
  });
  return targets;
}

function validateTutorials(
  value: unknown,
  targetsById: ReadonlyMap<string, TutorialTargetDefinition>,
  mechanicIds: Set<string>,
  eventKinds: Set<string>,
  milestoneIds: Set<string>,
  issues: TutorialValidationIssue[],
): Array<{ tutorialId: string; requires: string[] }> {
  if (!Array.isArray(value)) {
    issues.push(issue("tutorials", "must be an array"));
    return [];
  }
  const tutorials: Array<{ tutorialId: string; requires: string[] }> = [];
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const path = `tutorials[${index}]`;
    const tutorial = asRecord(value);
    if (!tutorial) {
      issues.push(issue(path, "must be an object"));
      return;
    }
    checkKeys(
      tutorial,
      path,
      [
        "tutorialId", "scope", "enabled", "trigger", "requires", "priority",
        "introduces", "rules", "steps",
      ],
      issues,
    );
    const tutorialId = stableIdentifier(tutorial.tutorialId, `${path}.tutorialId`, issues);
    const scope = enumValue(tutorial.scope, SCOPES, `${path}.scope`, issues);
    if (typeof tutorial.enabled !== "boolean") {
      issues.push(issue(`${path}.enabled`, "must be a boolean"));
    }
    if (!Number.isInteger(tutorial.priority)) {
      issues.push(issue(`${path}.priority`, "must be an integer"));
    }
    const requires = validateIdArray(tutorial.requires, `${path}.requires`, issues);
    validateIdArray(tutorial.introduces, `${path}.introduces`, issues).forEach(id => {
      if (!mechanicIds.has(id)) issues.push(issue(`${path}.introduces`, `unknown mechanic '${id}'`));
    });
    validateRules(tutorial.rules, `${path}.rules`, issues);
    validateTrigger(
      tutorial.trigger,
      `${path}.trigger`,
      mechanicIds,
      eventKinds,
      milestoneIds,
      issues,
    );
    const targetScreen = expectedTargetScreen(
      scope,
      tutorial.trigger,
      `${path}.scope`,
      issues,
    );
    const steps = validateSteps(
      tutorial.steps,
      `${path}.steps`,
      targetsById,
      targetScreen,
      eventKinds,
      issues,
    );
    if (steps === 0) issues.push(issue(`${path}.steps`, "must contain at least one step"));
    if (tutorialId !== null) {
      if (ids.has(tutorialId)) issues.push(issue(`${path}.tutorialId`, "must be unique"));
      ids.add(tutorialId);
      if (scope !== null) tutorials.push({ tutorialId, requires });
    }
  });
  return tutorials;
}

function expectedTargetScreen(
  scope: string | null,
  triggerValue: unknown,
  scopePath: string,
  issues: TutorialValidationIssue[],
): TutorialScreen | null {
  const trigger = asRecord(triggerValue);
  if (!trigger || typeof trigger.type !== "string") return null;

  switch (trigger.type) {
    case "SCREEN_ENTERED":
      if (scope !== "SCREEN") {
        issues.push(issue(scopePath, "must be SCREEN for a SCREEN_ENTERED trigger"));
        return null;
      }
      return typeof trigger.screen === "string"
        ? [...SCREENS].find(screen => screen === trigger.screen) ?? null
        : null;
    case "MATCH_STARTED":
    case "MATCH_EVENT_OBSERVED":
      if (scope !== "MATCH") {
        issues.push(issue(scopePath, "must be MATCH for a match trigger"));
        return null;
      }
      return "MATCH";
    case "CAMPAIGN_MILESTONE_APPLIED":
      if (scope === "SCREEN") return "LOADOUT";
      if (scope === "MATCH") return "MATCH";
      return null;
    default:
      return null;
  }
}

function validateTrigger(
  value: unknown,
  path: string,
  mechanicIds: Set<string>,
  eventKinds: Set<string>,
  milestoneIds: Set<string>,
  issues: TutorialValidationIssue[],
): void {
  const trigger = asRecord(value);
  if (!trigger || typeof trigger.type !== "string") {
    issues.push(issue(path, "must be a typed trigger object"));
    return;
  }
  switch (trigger.type) {
    case "SCREEN_ENTERED":
      checkKeys(trigger, path, ["type", "screen"], issues);
      enumValue(trigger.screen, SCREENS, `${path}.screen`, issues);
      return;
    case "MATCH_STARTED": {
      checkKeys(
        trigger,
        path,
        ["type", "campaignLevelRange", "requiresMechanicId"],
        issues,
      );
      if (trigger.campaignLevelRange !== null) {
        const range = asRecord(trigger.campaignLevelRange);
        if (!range) {
          issues.push(issue(`${path}.campaignLevelRange`, "must be null or a range object"));
        } else {
          checkKeys(range, `${path}.campaignLevelRange`, ["first", "last"], issues);
          if (!integer(range.first) || range.first < 1) {
            issues.push(issue(`${path}.campaignLevelRange.first`, "must be a positive integer"));
          }
          if (!integer(range.last) || range.last < (integer(range.first) ? range.first : 1)) {
            issues.push(issue(`${path}.campaignLevelRange.last`, "must be >= first"));
          }
        }
      }
      if (trigger.requiresMechanicId !== null) {
        const id = stableIdentifier(trigger.requiresMechanicId, `${path}.requiresMechanicId`, issues);
        if (id !== null && !mechanicIds.has(id)) {
          issues.push(issue(`${path}.requiresMechanicId`, `unknown mechanic '${id}'`));
        }
      }
      return;
    }
    case "MATCH_EVENT_OBSERVED":
      checkKeys(trigger, path, ["type", "eventKind"], issues);
      if (typeof trigger.eventKind !== "string" || !eventKinds.has(trigger.eventKind)) {
        issues.push(issue(`${path}.eventKind`, "must be a registered event kind"));
      }
      return;
    case "CAMPAIGN_MILESTONE_APPLIED":
      checkKeys(trigger, path, ["type", "milestoneId"], issues);
      if (
        typeof trigger.milestoneId !== "string" ||
        !milestoneIds.has(trigger.milestoneId)
      ) {
        issues.push(issue(`${path}.milestoneId`, "must be a registered campaign milestone"));
      }
      return;
    default:
      issues.push(issue(`${path}.type`, `unknown trigger type '${trigger.type}'`));
  }
}

function validateSteps(
  value: unknown,
  path: string,
  targetsById: ReadonlyMap<string, TutorialTargetDefinition>,
  targetScreen: TutorialScreen | null,
  eventKinds: Set<string>,
  issues: TutorialValidationIssue[],
): number {
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return 0;
  }
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const itemPath = `${path}[${index}]`;
    const step = asRecord(value);
    if (!step) {
      issues.push(issue(itemPath, "must be an object"));
      return;
    }
    checkKeys(
      step,
      itemPath,
      [
        "stepId", "targetId", "text", "placementPreference", "completion",
        "unlocksTargets", "supplementalContent",
      ],
      issues,
    );
    const stepId = stableIdentifier(step.stepId, `${itemPath}.stepId`, issues);
    if (stepId !== null) {
      if (ids.has(stepId)) issues.push(issue(`${itemPath}.stepId`, "must be unique"));
      ids.add(stepId);
    }
    if (step.targetId !== null) {
      const targetId = stableIdentifier(step.targetId, `${itemPath}.targetId`, issues);
      if (targetId !== null) {
        const target = targetsById.get(targetId);
        if (!target) {
          issues.push(issue(`${itemPath}.targetId`, `unknown target '${targetId}'`));
        } else if (targetScreen !== null && target.screen !== targetScreen) {
          issues.push(
            issue(
              `${itemPath}.targetId`,
              `target '${targetId}' is registered for ${target.screen}, expected ${targetScreen}`,
            ),
          );
        }
      }
    }
    validateText(step.text, `${itemPath}.text`, issues);
    enumValue(step.placementPreference, PLACEMENTS, `${itemPath}.placementPreference`, issues);
    validateCompletion(step.completion, `${itemPath}.completion`, eventKinds, issues);
    validateIdArray(step.unlocksTargets, `${itemPath}.unlocksTargets`, issues).forEach(id => {
      if (!targetsById.has(id)) {
        issues.push(issue(`${itemPath}.unlocksTargets`, `unknown target '${id}'`));
      }
    });
    if (
      step.supplementalContent !== null &&
      step.supplementalContent !== "WIND_STRENGTH_LEGEND"
    ) {
      issues.push(issue(`${itemPath}.supplementalContent`, "is not a supported content block"));
    }
  });
  return value.length;
}

function validateText(
  value: unknown,
  path: string,
  issues: TutorialValidationIssue[],
): void {
  const text = asRecord(value);
  if (!text) {
    issues.push(issue(path, "must contain localized text"));
    return;
  }
  if (
    Object.keys(text).length !== LANGUAGES.size ||
    [...LANGUAGES].some(language => !(language in text))
  ) {
    issues.push(issue(path, "must contain exactly pl, en, and es"));
  }
  LANGUAGES.forEach(language => {
    if (typeof text[language] !== "string" || text[language].trim() === "") {
      issues.push(issue(`${path}.${language}`, "must be a non-empty string"));
    }
  });
}

function validateCompletion(
  value: unknown,
  path: string,
  eventKinds: Set<string>,
  issues: TutorialValidationIssue[],
): void {
  const completion = asRecord(value);
  if (!completion || typeof completion.type !== "string") {
    issues.push(issue(path, "must be a typed completion object"));
    return;
  }
  switch (completion.type) {
    case "NEXT":
      checkKeys(completion, path, ["type"], issues);
      return;
    case "PLAYER_ACTION":
      checkKeys(completion, path, ["type", "kind", "count"], issues);
      if (typeof completion.kind !== "string" || !ACTIONS.has(completion.kind)) {
        issues.push(issue(`${path}.kind`, "must be a supported player action"));
      }
      if (!integer(completion.count) || completion.count < 1) {
        issues.push(issue(`${path}.count`, "must be a positive integer"));
      }
      return;
    case "MATCH_EVENT_COUNT":
      checkKeys(completion, path, ["type", "eventKind", "count"], issues);
      if (typeof completion.eventKind !== "string" || !eventKinds.has(completion.eventKind)) {
        issues.push(issue(`${path}.eventKind`, "must be a registered event kind"));
      }
      if (!integer(completion.count) || completion.count < 1) {
        issues.push(issue(`${path}.count`, "must be a positive integer"));
      }
      return;
    default:
      issues.push(issue(`${path}.type`, `unknown completion type '${completion.type}'`));
  }
}

function validateRules(value: unknown, path: string, issues: TutorialValidationIssue[]): void {
  const rules = asRecord(value);
  if (!rules) {
    issues.push(issue(path, "must be an object"));
    return;
  }
  const keys = [
    "pauseSimulationOnHint", "suspendAiWhileActive", "restrictAiToIntroducedMechanics",
  ];
  checkKeys(rules, path, keys, issues);
  keys.forEach(key => {
    if (typeof rules[key] !== "boolean") issues.push(issue(`${path}.${key}`, "must be a boolean"));
  });
}

function validateRequirementGraph(
  tutorials: Array<{ tutorialId: string; requires: string[] }>,
  issues: TutorialValidationIssue[],
): void {
  const definitions = new Map(tutorials.map(tutorial => [tutorial.tutorialId, tutorial]));
  tutorials.forEach(tutorial => tutorial.requires.forEach(required => {
    if (!definitions.has(required)) {
      issues.push(issue(
        `tutorials.${tutorial.tutorialId}.requires`,
        `references unknown tutorial '${required}'`,
      ));
    }
  }));
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const visit = (id: string, chain: string[]) => {
    if (visiting.has(id)) {
      issues.push(issue(
        `tutorials.${id}.requires`,
        `contains a dependency cycle: ${[...chain, id].join(" -> ")}`,
      ));
      return;
    }
    if (visited.has(id)) return;
    visiting.add(id);
    definitions.get(id)?.requires.forEach(required => {
      if (definitions.has(required)) visit(required, [...chain, id]);
    });
    visiting.delete(id);
    visited.add(id);
  };
  definitions.forEach((_, id) => visit(id, []));
}

function validateIdList(value: unknown, path: string, issues: TutorialValidationIssue[]): Set<string> {
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return new Set();
  }
  const ids = new Set<string>();
  value.forEach((item, index) => {
    const id = stableIdentifier(item, `${path}[${index}]`, issues);
    if (id !== null) {
      if (ids.has(id)) issues.push(issue(`${path}[${index}]`, "must be unique"));
      ids.add(id);
    }
  });
  return ids;
}

function validateIdArray(value: unknown, path: string, issues: TutorialValidationIssue[]): string[] {
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return [];
  }
  const result: string[] = [];
  const seen = new Set<string>();
  value.forEach((item, index) => {
    const id = stableIdentifier(item, `${path}[${index}]`, issues);
    if (id !== null) {
      if (seen.has(id)) issues.push(issue(`${path}[${index}]`, "must be unique"));
      seen.add(id);
      result.push(id);
    }
  });
  return result;
}

function stableIdentifier(
  value: unknown,
  path: string,
  issues: TutorialValidationIssue[],
): string | null {
  if (typeof value !== "string" || !STABLE_ID.test(value)) {
    issues.push(issue(path, "must be a stable lowercase identifier"));
    return null;
  }
  return value;
}

function enumValue<T extends string>(
  value: unknown,
  supported: ReadonlySet<T>,
  path: string,
  issues: TutorialValidationIssue[],
): T | null {
  if (typeof value === "string") {
    const match = [...supported].find(candidate => candidate === value);
    if (match !== undefined) return match;
  }
  issues.push(issue(path, `must be one of ${[...supported].join(", ")}`));
  return null;
}

function checkKeys(
  value: Record<string, unknown>,
  path: string,
  expected: readonly string[],
  issues: TutorialValidationIssue[],
): void {
  const keys = new Set(expected);
  expected.forEach(key => {
    if (!(key in value)) issues.push(issue(`${path}.${key}`, "is required"));
  });
  Object.keys(value).forEach(key => {
    if (!keys.has(key)) issues.push(issue(`${path}.${key}`, "is not supported"));
  });
}

function parseCatalog(content: string): TutorialCatalog | null {
  try {
    const value: unknown = JSON.parse(content);
    return asRecord(value) as TutorialCatalog | null;
  } catch {
    return null;
  }
}

function nextRevision(revision: string): string {
  const match = /^(.*?[-.])(\d+)$/.exec(revision);
  return match
    ? `${match[1]}${Number(match[2]) + 1}`
    : `${revision}-2`;
}

async function tutorialCatalogPath(repoRoot: string): Promise<string> {
  const root = await realpath(repoRoot);
  const candidate = join(root, ...TUTORIAL_CATALOG_FILE);
  const directory = await realpath(dirname(candidate));
  if (!isWithin(root, directory)) throw new Error("Tutorial catalog directory escapes repository root");
  const actualFile = await realpath(candidate).catch(error => {
    if (isMissing(error)) return candidate;
    throw error;
  });
  if (!isWithin(root, actualFile) || actualFile !== candidate) {
    throw new Error("Tutorial catalog is not the expected repository file");
  }
  return actualFile;
}

const asRecord = (value: unknown): Record<string, unknown> | null =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;

const integer = (value: unknown): value is number =>
  typeof value === "number" && Number.isInteger(value);

function invalid(path: string, message: string): TutorialValidationResponse {
  return { valid: false, issues: [issue(path, message)] };
}

function issue(path: string, message: string): TutorialValidationIssue {
  return { severity: "error", path, message };
}

const hashContent = (content: string): string =>
  createHash("sha256").update(content, "utf8").digest("hex");

function isWithin(root: string, path: string): boolean {
  const relativePath = relative(root, path);
  return relativePath === "" ||
    (!relativePath.startsWith(`..${sep}`) && relativePath !== ".." && !isAbsolute(relativePath));
}

function isMissing(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === "ENOENT";
}
