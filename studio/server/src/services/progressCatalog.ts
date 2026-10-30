import { createHash, randomUUID } from "node:crypto";
import { readFile, realpath, rename, rm, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, join, relative, sep } from "node:path";
import type {
  DeveloperProgressPreset,
  ProgressDocumentResponse,
  ProgressValidationIssue,
  ProgressValidationResponse,
} from "../contracts/progress.ts";

export const DEVELOPER_PROGRESS_FILE = [
  "game-application",
  "src",
  "main",
  "resources",
  "progression",
  "developer-progress.json",
] as const;
export const LAST_COMPLETE_CAMPAIGN_LEVEL = 54;

let writeQueue: Promise<void> = Promise.resolve();

export class ProgressConflictError extends Error {
  readonly currentHash: string;

  constructor(currentHash: string) {
    super("Developer progress preset changed since it was loaded");
    this.currentHash = currentHash;
  }
}

export class ProgressRevisionError extends Error {
  readonly revision: string;

  constructor(revision: string) {
    super("Progress revision must change when preset content changes");
    this.revision = revision;
  }
}

export async function readDeveloperProgress(
  repoRoot: string,
): Promise<ProgressDocumentResponse> {
  const path = await progressFilePath(repoRoot);
  const content = await readFile(path, "utf8");
  return { content, hash: hashContent(content) };
}

export function validateDeveloperProgress(
  content: string,
): ProgressValidationResponse {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch (error) {
    return {
      valid: false,
      issues: [{
        severity: "error",
        path: "progress",
        message: error instanceof Error ? error.message : String(error),
      }],
    };
  }
  const issues: ProgressValidationIssue[] = [];
  const preset = asRecord(value);
  if (!preset) return invalid("progress", "must be a JSON object");
  checkKeys(
    preset,
    [
      "schemaVersion",
      "revision",
      "currentLevel",
      "lives",
      "gold",
      "fieldsToApply",
      "applyOnNextLaunch",
    ],
    issues,
  );
  if (preset.schemaVersion !== 2) {
    issues.push(issue("schemaVersion", "must be the supported schema version 2"));
  }
  if (typeof preset.revision !== "string" || preset.revision.trim() === "") {
    issues.push(issue("revision", "must be a non-empty string"));
  }
  if (!integerInRange(preset.currentLevel, 1, LAST_COMPLETE_CAMPAIGN_LEVEL)) {
    issues.push(issue("currentLevel", `must be between 1 and ${LAST_COMPLETE_CAMPAIGN_LEVEL}`));
  }
  if (!nonNegativeInt(preset.lives)) {
    issues.push(issue("lives", "must be a non-negative integer"));
  }
  if (!nonNegativeInt(preset.gold)) {
    issues.push(issue("gold", "must be a non-negative integer"));
  }
  if (!Array.isArray(preset.fieldsToApply)) {
    issues.push(issue("fieldsToApply", "must be an array"));
  } else {
    const allowed = new Set(["currentLevel", "lives", "gold"]);
    const fields = new Set<string>();
    preset.fieldsToApply.forEach((value, index) => {
      if (typeof value !== "string" || !allowed.has(value)) {
        issues.push(issue(`fieldsToApply[${index}]`, "is not a supported progress field"));
      } else if (fields.has(value)) {
        issues.push(issue(`fieldsToApply[${index}]`, "must be unique"));
      } else {
        fields.add(value);
      }
    });
    if (preset.applyOnNextLaunch === true && fields.size === 0) {
      issues.push(issue("fieldsToApply", "must select at least one field to apply"));
    }
  }
  if (typeof preset.applyOnNextLaunch !== "boolean") {
    issues.push(issue("applyOnNextLaunch", "must be a boolean"));
  }
  return { valid: issues.length === 0, issues };
}

export async function saveDeveloperProgress(
  repoRoot: string,
  content: string,
  expectedHash: string,
): Promise<ProgressDocumentResponse> {
  const queued = writeQueue.then(async () => {
    const current = await readDeveloperProgress(repoRoot);
    if (current.hash !== expectedHash) throw new ProgressConflictError(current.hash);
    const validation = validateDeveloperProgress(content);
    if (!validation.valid) throw new Error("Cannot save invalid developer progress");
    const contentHash = hashContent(content);
    if (contentHash === current.hash) return current;

    const currentPreset = parsePreset(current.content);
    const nextPreset = parsePreset(content);
    if (currentPreset && currentPreset.revision === nextPreset?.revision) {
      throw new ProgressRevisionError(currentPreset.revision);
    }

    const path = await progressFilePath(repoRoot);
    const temporaryPath = `${path}.${randomUUID()}.tmp`;
    try {
      await writeFile(temporaryPath, content, { encoding: "utf8", flag: "wx" });
      await rename(temporaryPath, path);
    } finally {
      await rm(temporaryPath, { force: true });
    }
    return readDeveloperProgress(repoRoot);
  });
  writeQueue = queued.then(() => undefined, () => undefined);
  return queued;
}

function parsePreset(content: string): DeveloperProgressPreset | null {
  try {
    const value: unknown = JSON.parse(content);
    return isValidDeveloperProgress(value) ? value : null;
  } catch {
    return null;
  }
}

function isValidDeveloperProgress(value: unknown): value is DeveloperProgressPreset {
  const record = asRecord(value);
  return record !== null &&
    record.schemaVersion === 2 &&
    typeof record.revision === "string" &&
    integerInRange(record.currentLevel, 1, LAST_COMPLETE_CAMPAIGN_LEVEL) &&
    nonNegativeInt(record.lives) &&
    nonNegativeInt(record.gold) &&
    Array.isArray(record.fieldsToApply) &&
    record.fieldsToApply.every(value =>
      value === "currentLevel" || value === "lives" || value === "gold",
    ) &&
    new Set(record.fieldsToApply).size === record.fieldsToApply.length &&
    (!record.applyOnNextLaunch || record.fieldsToApply.length > 0) &&
    typeof record.applyOnNextLaunch === "boolean";
}

async function progressFilePath(repoRoot: string): Promise<string> {
  const root = await realpath(repoRoot);
  const candidate = join(root, ...DEVELOPER_PROGRESS_FILE);
  const directory = await realpath(dirname(candidate));
  if (!isWithin(root, directory)) {
    throw new Error("Developer progress path escapes the repository root");
  }
  const actualFile = await realpath(candidate).catch(error => {
    if (isMissing(error)) return candidate;
    throw error;
  });
  if (!isWithin(root, actualFile) || actualFile !== candidate) {
    throw new Error("Developer progress file is not the expected repository file");
  }
  return actualFile;
}

function checkKeys(
  value: Record<string, unknown>,
  expected: readonly string[],
  issues: ProgressValidationIssue[],
): void {
  const keys = new Set(expected);
  expected.forEach(key => {
    if (!(key in value)) issues.push(issue(key, "is required"));
  });
  Object.keys(value).forEach(key => {
    if (!keys.has(key)) issues.push(issue(key, "is not supported"));
  });
}

const asRecord = (value: unknown): Record<string, unknown> | null =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;

const integerInRange = (value: unknown, min: number, max: number): value is number =>
  typeof value === "number" &&
  Number.isInteger(value) &&
  value >= min &&
  value <= max;

const nonNegativeInt = (value: unknown): value is number =>
  integerInRange(value, 0, 2_147_483_647);

function invalid(path: string, message: string): ProgressValidationResponse {
  return { valid: false, issues: [issue(path, message)] };
}

function issue(path: string, message: string): ProgressValidationIssue {
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
