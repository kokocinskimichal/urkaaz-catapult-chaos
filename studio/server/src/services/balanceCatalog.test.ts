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
let validBalance: string;

beforeEach(async () => {
  validBalance = (await readBalance(await resolveRepoRoot())).content;
  repoRoot = await mkdtemp(join(tmpdir(), "studio-balance-"));
  const directory = join(repoRoot, "game-domain", "src", "main", "resources", "balance");
  await mkdir(directory, { recursive: true });
  await writeFile(join(directory, "game-balance.json"), validBalance);
});

afterEach(async () => {
  await rm(repoRoot, { recursive: true, force: true });
});

describe("game balance catalog", () => {
  it("validates the supported schema 5 balance document", () => {
    expect(validateBalanceContent(validBalance)).toEqual({ valid: true, issues: [] });
  });

  it("rejects schema 4 instead of applying an implicit migration", () => {
    const document = JSON.parse(validBalance) as Record<string, unknown>;
    document.schemaVersion = 4;

    expect(validateBalanceContent(JSON.stringify(document)).issues).toContainEqual(
      expect.objectContaining({ path: "schemaVersion" }),
    );
  });

  it("accepts only gameplay speed multipliers from 1 through 1.5", () => {
    for (const multiplier of [1, 1.2, 1.5]) {
      const document = JSON.parse(validBalance) as Record<string, unknown>;
      const runtime = document.runtime as Record<string, unknown>;
      const combat = runtime.combat as Record<string, unknown>;
      combat.gameplaySpeedMultiplier = multiplier;
      expect(validateBalanceContent(JSON.stringify(document))).toEqual({
        valid: true,
        issues: [],
      });
    }

    for (const multiplier of [0.99, 1.51, "1.2", Number.NaN]) {
      const document = JSON.parse(validBalance) as Record<string, unknown>;
      const runtime = document.runtime as Record<string, unknown>;
      const combat = runtime.combat as Record<string, unknown>;
      combat.gameplaySpeedMultiplier = multiplier;
      expect(validateBalanceContent(JSON.stringify(document)).issues).toContainEqual(
        expect.objectContaining({ path: "runtime.combat.gameplaySpeedMultiplier" }),
      );
    }
  });

  it("validates nullable non-negative shop prices only for limited ammunition", () => {
    for (const [type, price] of [
      ["ROCK", null],
      ["POWDER_BARREL", null],
      ["POWDER_BARREL", 0],
      ["POWDER_BARREL", 250],
      ["FIRE_RAIN", 350],
      ["PLAGUE_CAULDRON", 500],
    ] as const) {
      const document = JSON.parse(validBalance) as Record<string, unknown>;
      const ammunition = document.ammunition as Record<string, unknown>[];
      const profile = ammunition.find(item => item.type === type);
      expect(profile).toBeDefined();
      profile!.shopCostGold = price;
      expect(validateBalanceContent(JSON.stringify(document))).toEqual({
        valid: true,
        issues: [],
      });
    }

    for (const invalidPrice of [-1, 1.5, "250", undefined]) {
      const document = JSON.parse(validBalance) as Record<string, unknown>;
      const ammunition = document.ammunition as Record<string, unknown>[];
      const profile = ammunition.find(item => item.type === "POWDER_BARREL");
      expect(profile).toBeDefined();
      if (invalidPrice === undefined) delete profile!.shopCostGold;
      else profile!.shopCostGold = invalidPrice;

      expect(validateBalanceContent(JSON.stringify(document)).issues).toContainEqual(
        expect.objectContaining({ path: "ammunition[2].shopCostGold" }),
      );
    }

    const unlimited = JSON.parse(validBalance) as Record<string, unknown>;
    const ammunition = unlimited.ammunition as Record<string, unknown>[];
    const rock = ammunition.find(item => item.type === "ROCK");
    expect(rock).toBeDefined();
    rock!.shopCostGold = 10;
    expect(validateBalanceContent(JSON.stringify(unlimited)).issues).toContainEqual(
      expect.objectContaining({ path: "ammunition[0].shopCostGold" }),
    );
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

  it("rejects missing unit coverage and invalid ammunition, spell, and mana profiles", () => {
    const missingUnit = JSON.parse(validBalance) as Record<string, unknown>;
    (missingUnit.units as unknown[]).pop();
    expect(validateBalanceContent(JSON.stringify(missingUnit)).valid).toBe(false);

    const invalidAmmunition = JSON.parse(validBalance) as Record<string, unknown>;
    const ammunition = (invalidAmmunition.ammunition as Record<string, unknown>[])[0];
    ammunition.behavior = { kind: "SCRIPT" };
    expect(validateBalanceContent(JSON.stringify(invalidAmmunition)).valid).toBe(false);

    const invalidSpell = JSON.parse(validBalance) as Record<string, unknown>;
    (invalidSpell.spells as Record<string, unknown>[])[0].manaCost = 0;
    expect(validateBalanceContent(JSON.stringify(invalidSpell)).valid).toBe(false);

    const invalidMana = JSON.parse(validBalance) as Record<string, unknown>;
    (invalidMana.mana as Record<string, unknown>).startingMana = 101;
    expect(validateBalanceContent(JSON.stringify(invalidMana)).valid).toBe(false);
  });

  it("rejects fields required by the runtime balance loader when omitted", () => {
    const invalid = JSON.parse(validBalance) as Record<string, unknown>;
    const boss = (invalid.bosses as Record<string, unknown>[])[0];
    delete boss.immuneAmmunitionTypes;

    const ammunition = invalid.ammunition as Record<string, unknown>[];
    const rock = ammunition.find(profile => profile.type === "ROCK");
    const fireRain = ammunition.find(profile => profile.type === "FIRE_RAIN");
    expect(rock).toBeDefined();
    expect(fireRain).toBeDefined();
    const rockInventory = rock!.inventory as Record<string, unknown>;
    const rockFlight = rock!.flight as Record<string, unknown>;
    const rockCollision = rock!.collision as Record<string, unknown>;
    const rockDamage = rock!.damage as Record<string, unknown>;
    const fireBehavior = fireRain!.behavior as Record<string, unknown>;
    delete rockInventory.startingCount;
    delete rockFlight.gravityResponse;
    delete rockFlight.drag;
    delete rockFlight.initialSpeedMultiplier;
    delete rockFlight.maximumLifetimeSeconds;
    delete rockFlight.apexFragmentation;
    delete rockCollision.friendlyFire;
    delete rockDamage.falloff;
    delete rockDamage.minimumDamage;
    delete fireBehavior.windDriftMultiplier;
    delete fireBehavior.followsTerrain;
    delete fireBehavior.unitFriendlyFire;

    const spell = (invalid.spells as Record<string, unknown>[])
      .find(profile => profile.type === "ICE_TRAP");
    expect(spell).toBeDefined();
    delete spell!.effect;
    delete spell!.recastableWhileActive;

    const result = validateBalanceContent(JSON.stringify(invalid));
    expect(result.issues).toEqual(expect.arrayContaining([
      "bosses[0].immuneAmmunitionTypes",
      "ammunition[0].inventory.startingCount",
      "ammunition[0].flight.gravityResponse",
      "ammunition[0].flight.drag",
      "ammunition[0].flight.initialSpeedMultiplier",
      "ammunition[0].flight.maximumLifetimeSeconds",
      "ammunition[0].flight.apexFragmentation",
      "ammunition[0].collision.friendlyFire",
      "ammunition[0].damage.falloff",
      "ammunition[0].damage.minimumDamage",
      "ammunition[3].behavior.windDriftMultiplier",
      "ammunition[3].behavior.followsTerrain",
      "ammunition[3].behavior.unitFriendlyFire",
      "spells[0].effect",
      "spells[0].recastableWhileActive",
    ].map(path => expect.objectContaining({ path }))));
  });

  it("requires valid runtime profiles and contiguous campaign difficulty bands", () => {
    const missingRuntime = JSON.parse(validBalance) as Record<string, unknown>;
    delete missingRuntime.runtime;
    expect(validateBalanceContent(JSON.stringify(missingRuntime)).valid).toBe(false);

    const invalidSupply = JSON.parse(validBalance) as Record<string, unknown>;
    const supplyRuntime = (invalidSupply.runtime as Record<string, unknown>);
    (supplyRuntime.economy as Record<string, unknown>).startingSupply = 21;
    expect(validateBalanceContent(JSON.stringify(invalidSupply)).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "runtime.economy.startingSupply" }),
      ]),
    );

    const invalidCombat = JSON.parse(validBalance) as Record<string, unknown>;
    const combat = (invalidCombat.runtime as Record<string, unknown>)
      .combat as Record<string, unknown>;
    combat.maximumMeleeSynergyMultiplier = 0.5;
    expect(validateBalanceContent(JSON.stringify(invalidCombat)).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "runtime.combat.maximumMeleeSynergyMultiplier" }),
      ]),
    );

    const invalidCampaign = JSON.parse(validBalance) as Record<string, unknown>;
    const campaign = (invalidCampaign.runtime as Record<string, unknown>)
      .campaign as Record<string, unknown>;
    const bands = campaign.aiDifficultyBands as Record<string, unknown>[];
    bands[1].firstLevel = 12;
    expect(validateBalanceContent(JSON.stringify(invalidCampaign)).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "runtime.campaign.aiDifficultyBands[1].firstLevel" }),
      ]),
    );

    const invalidCatapult = JSON.parse(validBalance) as Record<string, unknown>;
    const catapult = (invalidCatapult.runtime as Record<string, unknown>)
      .catapult as Record<string, unknown>;
    catapult.initialAimPower = 101;
    expect(validateBalanceContent(JSON.stringify(invalidCatapult)).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "runtime.catapult.initialAimPower" }),
      ]),
    );

    const invalidAiStrategy = JSON.parse(validBalance) as Record<string, unknown>;
    const aiStrategy = (invalidAiStrategy.runtime as Record<string, unknown>)
      .aiStrategy as Record<string, unknown>;
    aiStrategy.horizontalDistancePerPowerPercent = 0;
    expect(validateBalanceContent(JSON.stringify(invalidAiStrategy)).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "runtime.aiStrategy.horizontalDistancePerPowerPercent" }),
      ]),
    );
  });
});
