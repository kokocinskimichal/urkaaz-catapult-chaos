import { createHash, randomUUID } from "node:crypto";
import { readFile, realpath, rename, rm, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, join, relative, sep } from "node:path";
import type {
  BalanceDocumentResponse,
  BalanceValidationIssue,
  BalanceValidationResponse,
} from "../contracts/balance.ts";

export const BALANCE_FILE = [
  "game-domain",
  "src",
  "main",
  "resources",
  "balance",
  "game-balance.json",
] as const;

const PROFILE_ID = /^boss\.[a-z0-9-]+\.level-\d{2}$/;
const PRESENTATION_KEY = /^[a-z][a-z0-9_]*$/;
const AMMUNITION_ID = /^[A-Z][A-Z0-9_]*$/;
let writeQueue: Promise<void> = Promise.resolve();

export class BalanceConflictError extends Error {
  readonly currentHash: string;

  constructor(currentHash: string) {
    super("Game balance changed since it was loaded");
    this.currentHash = currentHash;
  }
}

export class BalanceRevisionError extends Error {
  readonly revision: string;

  constructor(revision: string) {
    super("Balance revision must change when balance content changes");
    this.revision = revision;
  }
}

export async function readBalance(repoRoot: string): Promise<BalanceDocumentResponse> {
  const path = await balanceFilePath(repoRoot);
  const content = await readFile(path, "utf8");
  return { content, hash: hashContent(content) };
}

export function validateBalanceContent(content: string): BalanceValidationResponse {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch (error) {
    return {
      valid: false,
      issues: [{
        severity: "error",
        path: "gameBalance",
        message: error instanceof Error ? error.message : String(error),
      }],
    };
  }

  const issues: BalanceValidationIssue[] = [];
  const root = asRecord(value);
  if (!root) return invalid("gameBalance", "must be a JSON object");
  checkKeys(root, "gameBalance", ["schemaVersion", "balanceRevision", "bosses"], issues);
  if (root.schemaVersion !== 1) {
    issues.push(issue("schemaVersion", "must be the supported schema version 1"));
  }
  if (typeof root.balanceRevision !== "string" || root.balanceRevision.trim() === "") {
    issues.push(issue("balanceRevision", "must be a non-empty string"));
  }
  if (!Array.isArray(root.bosses) || root.bosses.length === 0) {
    issues.push(issue("bosses", "must contain at least one boss profile"));
  } else {
    const ids = new Set<string>();
    root.bosses.forEach((value, index) => {
      const path = `bosses[${index}]`;
      const profile = asRecord(value);
      if (!profile) {
        issues.push(issue(path, "must be a JSON object"));
        return;
      }
      validateProfile(profile, path, issues);
      if (typeof profile.id === "string") {
        if (ids.has(profile.id)) issues.push(issue(`${path}.id`, "must be unique"));
        ids.add(profile.id);
      }
    });
  }
  return { valid: issues.length === 0, issues };
}

export async function saveBalance(
  repoRoot: string,
  content: string,
  expectedHash: string,
): Promise<BalanceDocumentResponse> {
  const queued = writeQueue.then(async () => {
    const current = await readBalance(repoRoot);
    if (current.hash !== expectedHash) throw new BalanceConflictError(current.hash);
    const validation = validateBalanceContent(content);
    if (!validation.valid) throw new Error("Cannot save invalid game balance content");
    if (hashContent(content) !== current.hash) {
      const currentDocument = parseRecord(current.content);
      const nextDocument = asRecord(JSON.parse(content) as unknown);
      if (
        typeof currentDocument?.balanceRevision === "string" &&
        currentDocument.balanceRevision === nextDocument?.balanceRevision
      ) {
        throw new BalanceRevisionError(currentDocument.balanceRevision);
      }
    } else {
      return current;
    }

    const path = await balanceFilePath(repoRoot);
    const temporaryPath = `${path}.${randomUUID()}.tmp`;
    try {
      await writeFile(temporaryPath, content, { encoding: "utf8", flag: "wx" });
      await rename(temporaryPath, path);
    } finally {
      await rm(temporaryPath, { force: true });
    }
    return readBalance(repoRoot);
  });
  writeQueue = queued.then(() => undefined, () => undefined);
  return queued;
}

function validateProfile(
  profile: Record<string, unknown>,
  path: string,
  issues: BalanceValidationIssue[],
): void {
  checkKeys(
    profile,
    path,
    [
      "id",
      "presentationKey",
      "archetype",
      "maxHealth",
      "collision",
      "movement",
      "attacks",
      "specialAbilities",
      "immuneAmmunitionTypes",
    ],
    issues,
  );
  if (typeof profile.id !== "string" || !PROFILE_ID.test(profile.id)) {
    issues.push(issue(`${path}.id`, "must be a stable boss profile ID"));
  }
  if (typeof profile.presentationKey !== "string" || !PRESENTATION_KEY.test(profile.presentationKey)) {
    issues.push(issue(`${path}.presentationKey`, "must be a lowercase presentation key"));
  }
  if (profile.archetype !== "CENTRAL_OBJECTIVE" && profile.archetype !== "MOBILE_SIEGE_BOSS") {
    issues.push(issue(`${path}.archetype`, "is not a supported boss archetype"));
  }
  if (!positiveInteger(profile.maxHealth)) {
    issues.push(issue(`${path}.maxHealth`, "must be a positive integer"));
  }
  const collision = asRecord(profile.collision);
  if (!collision) {
    issues.push(issue(`${path}.collision`, "must be an object"));
  } else {
    checkKeys(collision, `${path}.collision`, ["width", "height"], issues);
    if (!positiveNumber(collision.width)) issues.push(issue(`${path}.collision.width`, "must be positive"));
    if (!positiveNumber(collision.height)) issues.push(issue(`${path}.collision.height`, "must be positive"));
  }
  const movement = asRecord(profile.movement);
  if (!movement) {
    issues.push(issue(`${path}.movement`, "must be an object"));
  } else {
    checkKeys(movement, `${path}.movement`, ["kind", "speedWorldUnitsPerSecond"], issues);
    if (movement.kind !== "STATIC" && movement.kind !== "ADVANCE") {
      issues.push(issue(`${path}.movement.kind`, "must be STATIC or ADVANCE"));
    }
    if (!nonNegativeNumber(movement.speedWorldUnitsPerSecond)) {
      issues.push(issue(`${path}.movement.speedWorldUnitsPerSecond`, "must be non-negative"));
    } else if (
      (movement.kind === "STATIC" && movement.speedWorldUnitsPerSecond !== 0) ||
      (movement.kind === "ADVANCE" && movement.speedWorldUnitsPerSecond <= 0)
    ) {
      issues.push(issue(`${path}.movement.speedWorldUnitsPerSecond`, "does not match the movement kind"));
    }
  }
  const targets = new Set<string>();
  if (!Array.isArray(profile.attacks) || profile.attacks.length === 0) {
    issues.push(issue(`${path}.attacks`, "must contain at least one attack"));
  } else {
    profile.attacks.forEach((value, index) => {
      const attackPath = `${path}.attacks[${index}]`;
      const attack = asRecord(value);
      if (!attack) {
        issues.push(issue(attackPath, "must be an object"));
        return;
      }
      checkKeys(
        attack,
        attackPath,
        [
          "target",
          "damage",
          "minimumDamage",
          "rangeWorldUnits",
          "cooldownSeconds",
          "initialCooldownSeconds",
        ],
        issues,
        ["windupSeconds"],
      );
      if (attack.target !== "UNIT" && attack.target !== "FORTRESS_GATE") {
        issues.push(issue(`${attackPath}.target`, "is not a supported attack target"));
      } else if (targets.has(attack.target)) {
        issues.push(issue(`${attackPath}.target`, "must be unique within the profile"));
      } else {
        targets.add(attack.target);
      }
      if (!positiveInteger(attack.damage)) issues.push(issue(`${attackPath}.damage`, "must be a positive integer"));
      if (
        !positiveInteger(attack.minimumDamage) ||
        typeof attack.damage === "number" &&
          typeof attack.minimumDamage === "number" &&
          attack.minimumDamage > attack.damage
      ) {
        issues.push(issue(`${attackPath}.minimumDamage`, "must be a positive integer no greater than damage"));
      }
      if (!nonNegativeNumber(attack.rangeWorldUnits)) {
        issues.push(issue(`${attackPath}.rangeWorldUnits`, "must be non-negative"));
      }
      if (!positiveNumber(attack.cooldownSeconds)) {
        issues.push(issue(`${attackPath}.cooldownSeconds`, "must be positive"));
      }
      if (!nonNegativeNumber(attack.initialCooldownSeconds)) {
        issues.push(issue(`${attackPath}.initialCooldownSeconds`, "must be non-negative"));
      }
      if (attack.windupSeconds !== undefined && !nonNegativeNumber(attack.windupSeconds)) {
        issues.push(issue(`${attackPath}.windupSeconds`, "must be non-negative"));
      }
      if (attack.windupSeconds !== undefined && attack.windupSeconds !== 0 && attack.target !== "UNIT") {
        issues.push(issue(`${attackPath}.windupSeconds`, "is only supported for unit attacks"));
      }
    });
  }
  if (!Array.isArray(profile.specialAbilities)) {
    issues.push(issue(`${path}.specialAbilities`, "must be an array"));
  } else {
    let enrages = 0;
    profile.specialAbilities.forEach((value, index) => {
      const abilityPath = `${path}.specialAbilities[${index}]`;
      const ability = asRecord(value);
      if (!ability) {
        issues.push(issue(abilityPath, "must be an object"));
        return;
      }
      if (ability.kind !== "ENRAGE") {
        issues.push(issue(`${abilityPath}.kind`, "is not a supported ability"));
        return;
      }
      enrages += 1;
      checkKeys(
        ability,
        abilityPath,
        ["kind", "healthFractionThreshold", "speedMultiplier", "attackCooldownMultiplier"],
        issues,
      );
      if (!fraction(ability.healthFractionThreshold)) {
        issues.push(issue(`${abilityPath}.healthFractionThreshold`, "must be between 0 and 1"));
      }
      if (!numberAtLeast(ability.speedMultiplier, 1)) {
        issues.push(issue(`${abilityPath}.speedMultiplier`, "must be at least 1"));
      }
      if (!fraction(ability.attackCooldownMultiplier)) {
        issues.push(issue(`${abilityPath}.attackCooldownMultiplier`, "must be between 0 and 1"));
      }
    });
    if (enrages > 1) issues.push(issue(`${path}.specialAbilities`, "may contain at most one ENRAGE"));
  }
  if (!Array.isArray(profile.immuneAmmunitionTypes)) {
    issues.push(issue(`${path}.immuneAmmunitionTypes`, "must be an array"));
  } else {
    const immunities = new Set<string>();
    profile.immuneAmmunitionTypes.forEach((value, index) => {
      if (typeof value !== "string" || !AMMUNITION_ID.test(value)) {
        issues.push(issue(`${path}.immuneAmmunitionTypes[${index}]`, "must be a stable ammunition ID"));
      } else if (immunities.has(value)) {
        issues.push(issue(`${path}.immuneAmmunitionTypes[${index}]`, "must be unique"));
      } else {
        immunities.add(value);
      }
    });
  }
  if (profile.archetype === "CENTRAL_OBJECTIVE") {
    if (movement && movement.kind !== "STATIC") {
      issues.push(issue(`${path}.movement.kind`, "central objectives must be STATIC"));
    }
    if (Array.isArray(profile.attacks) && profile.attacks.some(attack => asRecord(attack)?.target !== "UNIT")) {
      issues.push(issue(`${path}.attacks`, "central objectives may only target units"));
    }
  } else if (profile.archetype === "MOBILE_SIEGE_BOSS") {
    if (movement && movement.kind !== "ADVANCE") {
      issues.push(issue(`${path}.movement.kind`, "mobile bosses must ADVANCE"));
    }
    if (!targets.has("FORTRESS_GATE")) {
      issues.push(issue(`${path}.attacks`, "mobile bosses must define a fortress gate attack"));
    }
  }
}

async function balanceFilePath(repoRoot: string): Promise<string> {
  const root = await realpath(repoRoot);
  const candidate = join(root, ...BALANCE_FILE);
  const directory = await realpath(dirname(candidate));
  if (!isWithin(root, directory)) throw new Error("Game balance path escapes the repository root");
  const actualFile = await realpath(candidate).catch(error => {
    if (isMissing(error)) return candidate;
    throw error;
  });
  if (!isWithin(root, actualFile) || actualFile !== candidate) {
    throw new Error("Game balance file is not the expected repository file");
  }
  return actualFile;
}

function checkKeys(
  value: Record<string, unknown>,
  path: string,
  expected: readonly string[],
  issues: BalanceValidationIssue[],
  optional: readonly string[] = [],
): void {
  const expectedSet = new Set([...expected, ...optional]);
  for (const key of expected) {
    if (!(key in value)) issues.push(issue(path ? `${path}.${key}` : key, "is required"));
  }
  for (const key of Object.keys(value)) {
    if (!expectedSet.has(key)) issues.push(issue(path ? `${path}.${key}` : key, "is not supported"));
  }
}

const asRecord = (value: unknown): Record<string, unknown> | null =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;

function parseRecord(content: string): Record<string, unknown> | null {
  try {
    return asRecord(JSON.parse(content) as unknown);
  } catch {
    return null;
  }
}

const positiveInteger = (value: unknown): value is number =>
  typeof value === "number" && Number.isInteger(value) && value > 0;

const positiveNumber = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value > 0;

const nonNegativeNumber = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value >= 0;

const numberAtLeast = (value: unknown, minimum: number): value is number =>
  typeof value === "number" && Number.isFinite(value) && value >= minimum;

const fraction = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 1;

function invalid(path: string, message: string): BalanceValidationResponse {
  return { valid: false, issues: [issue(path, message)] };
}

function issue(path: string, message: string): BalanceValidationIssue {
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
