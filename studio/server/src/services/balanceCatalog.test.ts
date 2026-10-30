import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  readBalance,
  validateBalanceContent,
} from "./balanceCatalog.ts";
import { resolveRepoRoot } from "./repoRoot.ts";

let repoRoot: string;

const validBalance = JSON.stringify({
  schemaVersion: 1,
  balanceRevision: "test-1",
  bosses: [{
    id: "boss.central-oak.level-05",
    presentationKey: "oak",
    archetype: "CENTRAL_OBJECTIVE",
    maxHealth: 12000,
    collision: { width: 1440, height: 2016 },
    movement: { kind: "STATIC", speedWorldUnitsPerSecond: 0 },
    attacks: [{
      target: "UNIT",
      damage: 150,
      minimumDamage: 35,
      rangeWorldUnits: 780,
      cooldownSeconds: 4,
      initialCooldownSeconds: 2.5,
      windupSeconds: 2.291,
    }],
    specialAbilities: [],
    immuneAmmunitionTypes: ["PLAGUE_CAULDRON"],
  }],
});

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-balance-"));
  const directory = join(repoRoot, "game-domain", "src", "main", "resources", "balance");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "game-balance.json"), validBalance);
});

afterEach(async () => {
  await rm(repoRoot, { recursive: true, force: true });
});

describe("game balance catalog", () => {
  it("validates the supported boss profile schema", () => {
    expect(validateBalanceContent(validBalance)).toEqual({ valid: true, issues: [] });
  });

  it("rejects unknown fields and archetype-incompatible attacks", () => {
    const invalid = JSON.parse(validBalance) as Record<string, unknown>;
    const profile = (invalid.bosses as Record<string, unknown>[])[0];
    profile.unexpected = true;
    (profile.attacks as Record<string, unknown>[])[0].target = "FORTRESS_GATE";

    const result = validateBalanceContent(JSON.stringify(invalid));
    expect(result.valid).toBe(false);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: "bosses[0].unexpected" }),
      expect.objectContaining({ path: "bosses[0].attacks", message: expect.stringContaining("central objectives") }),
    ]));
  });

  it("reads the exact file bytes and returns their SHA-256 hash", async () => {
    const result = await readBalance(repoRoot);
    expect(result.content).toBe(validBalance);
    expect(result.hash).toHaveLength(64);
  });

  it("accepts the checked-in boss balance file", async () => {
    const root = await resolveRepoRoot();
    const balance = await readBalance(root);
    expect(validateBalanceContent(balance.content)).toEqual({ valid: true, issues: [] });
  });
});
