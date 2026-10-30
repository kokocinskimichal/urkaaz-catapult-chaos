import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  readDeveloperProgress,
  validateDeveloperProgress,
} from "./progressCatalog.ts";
import { resolveRepoRoot } from "./repoRoot.ts";

let repoRoot: string;

const validPreset = JSON.stringify({
  schemaVersion: 2,
  revision: "developer-progress-1",
  currentLevel: 12,
  lives: 4,
  gold: 350,
  fieldsToApply: ["currentLevel"],
  applyOnNextLaunch: true,
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-progress-"));
  const directory = join(repoRoot, "game-application", "src", "main", "resources", "progression");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "developer-progress.json"), validPreset);
});

afterEach(async () => {
  await rm(repoRoot, { recursive: true, force: true });
});

describe("developer progress catalog", () => {
  it("validates the progress preset and current playable range", () => {
    expect(validateDeveloperProgress(validPreset)).toEqual({ valid: true, issues: [] });
    const invalid = JSON.parse(validPreset) as Record<string, unknown>;
    invalid.currentLevel = 55;
    expect(validateDeveloperProgress(JSON.stringify(invalid)).issues)
      .toEqual(expect.arrayContaining([
        expect.objectContaining({ path: "currentLevel" }),
      ]));
  });

  it("rejects unknown, duplicate, or empty queued fields", () => {
    for (const fieldsToApply of [
      ["unknown"],
      ["gold", "gold"],
      [],
    ]) {
      const invalid = JSON.parse(validPreset) as Record<string, unknown>;
      invalid.fieldsToApply = fieldsToApply;
      expect(validateDeveloperProgress(JSON.stringify(invalid)).valid).toBe(false);
    }
  });

  it("returns exact file content and SHA-256", async () => {
    const preset = await readDeveloperProgress(repoRoot);
    expect(preset.content).toBe(validPreset);
    expect(preset.hash).toMatch(/^[a-f0-9]{64}$/);
  });

  it("accepts the checked-in developer progress file", async () => {
    const preset = await readDeveloperProgress(await resolveRepoRoot());
    expect(validateDeveloperProgress(preset.content)).toEqual({ valid: true, issues: [] });
  });
});
