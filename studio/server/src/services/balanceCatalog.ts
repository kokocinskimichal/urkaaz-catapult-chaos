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
const UNIT_IDS = new Set(["SAPPER", "DEFENDER", "DEMOLISHER", "RAGING_BOAR", "SLINGMASTER"]);
const AMMUNITION_TYPES = new Set([
  "ROCK", "SIEGE_BOMB", "POWDER_BARREL", "FIRE_RAIN", "PLAGUE_CAULDRON",
]);
const SPELL_TYPES = new Set([
  "ICE_TRAP", "RUNIC_BASTION", "HASTE", "HEAL", "FORTRESS_SHIELD",
  "CATAPULT_LOCK", "SLOWING_FIELD", "VAMPIRIC_BANNER", "STONE_SKIN",
  "SIEGE_FRENZY", "BERSERKER",
]);
const AI_PROFILE_IDS = new Set(["CADET", "VETERAN", "ELITE"]);
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
  checkKeys(
    root,
    "gameBalance",
    [
      "schemaVersion", "balanceRevision", "bosses", "units", "ammunition",
      "spells", "mana", "runtime",
    ],
    issues,
  );
  if (root.schemaVersion !== 3) {
    issues.push(issue("schemaVersion", "must be the supported schema version 3"));
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
  validateUnits(root.units, issues);
  validateAmmunition(root.ammunition, issues);
  validateSpells(root.spells, issues);
  validateMana(root.mana, issues);
  validateRuntime(root.runtime, issues);
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

function validateUnits(value: unknown, issues: BalanceValidationIssue[]): void {
  const path = "units";
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return;
  }
  if (value.length !== UNIT_IDS.size) {
    issues.push(issue(path, "must define every stable unit ID exactly once"));
  }
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const itemPath = `${path}[${index}]`;
    const unit = asRecord(value);
    if (!unit) {
      issues.push(issue(itemPath, "must be a JSON object"));
      return;
    }
    checkKeys(
      unit,
      itemPath,
      [
        "id", "supplyCost", "maxHealth", "speed", "attackDamage",
        "attackCooldownSeconds", "attackRange", "fortressDamage",
        "incomingDamageMultiplier", "contactMovementSpeedMultiplier",
      ],
      issues,
    );
    if (typeof unit.id !== "string" || !UNIT_IDS.has(unit.id)) {
      issues.push(issue(`${itemPath}.id`, "must be a stable unit ID"));
    } else if (ids.has(unit.id)) {
      issues.push(issue(`${itemPath}.id`, "must be unique"));
    } else {
      ids.add(unit.id);
    }
    if (!nonNegativeInteger(unit.supplyCost)) {
      issues.push(issue(`${itemPath}.supplyCost`, "must be a non-negative integer"));
    }
    if (!positiveInteger(unit.maxHealth)) {
      issues.push(issue(`${itemPath}.maxHealth`, "must be a positive integer"));
    }
    if (!nonNegativeNumber(unit.speed)) {
      issues.push(issue(`${itemPath}.speed`, "must be non-negative"));
    }
    if (!nonNegativeInteger(unit.attackDamage)) {
      issues.push(issue(`${itemPath}.attackDamage`, "must be a non-negative integer"));
    }
    if (!positiveNumber(unit.attackCooldownSeconds)) {
      issues.push(issue(`${itemPath}.attackCooldownSeconds`, "must be positive"));
    }
    if (!nonNegativeNumber(unit.attackRange)) {
      issues.push(issue(`${itemPath}.attackRange`, "must be non-negative"));
    }
    if (!nonNegativeInteger(unit.fortressDamage)) {
      issues.push(issue(`${itemPath}.fortressDamage`, "must be a non-negative integer"));
    }
    if (!positiveNumber(unit.incomingDamageMultiplier)) {
      issues.push(issue(`${itemPath}.incomingDamageMultiplier`, "must be positive"));
    }
    if (!fraction(unit.contactMovementSpeedMultiplier)) {
      issues.push(issue(`${itemPath}.contactMovementSpeedMultiplier`, "must be between 0 and 1"));
    }
  });
  if (ids.size === UNIT_IDS.size && [...UNIT_IDS].some(id => !ids.has(id))) {
    issues.push(issue(path, "must define every stable unit ID exactly once"));
  }
}

function validateAmmunition(value: unknown, issues: BalanceValidationIssue[]): void {
  const path = "ammunition";
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return;
  }
  if (value.length !== AMMUNITION_TYPES.size) {
    issues.push(issue(path, "must define every stable ammunition ID exactly once"));
  }
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const itemPath = `${path}[${index}]`;
    const ammunition = asRecord(value);
    if (!ammunition) {
      issues.push(issue(itemPath, "must be a JSON object"));
      return;
    }
    checkKeys(
      ammunition,
      itemPath,
      [
        "type", "effect", "reload", "inventory", "flight", "collision",
        "damage", "terrain", "behavior", "availableLayers",
      ],
      issues,
    );
    if (typeof ammunition.type !== "string" || !AMMUNITION_TYPES.has(ammunition.type)) {
      issues.push(issue(`${itemPath}.type`, "must be a stable ammunition ID"));
    } else if (ids.has(ammunition.type)) {
      issues.push(issue(`${itemPath}.type`, "must be unique"));
    } else {
      ids.add(ammunition.type);
    }
    if (!oneOf(ammunition.effect, ["DIRECT", "EXPLOSIVE", "INCENDIARY", "PLAGUE"])) {
      issues.push(issue(`${itemPath}.effect`, "is not a supported ammunition effect"));
    }
    validateReload(ammunition.reload, `${itemPath}.reload`, issues);
    validateInventory(ammunition.inventory, `${itemPath}.inventory`, issues);
    validateFlight(ammunition.flight, `${itemPath}.flight`, issues);
    validateCollision(ammunition.collision, `${itemPath}.collision`, issues);
    validateDamage(ammunition.damage, `${itemPath}.damage`, issues);
    validateTerrain(ammunition.terrain, `${itemPath}.terrain`, issues);
    validateAmmunitionBehavior(ammunition.behavior, `${itemPath}.behavior`, issues);
    validateEnumArray(
      ammunition.availableLayers,
      `${itemPath}.availableLayers`,
      ["TERRAIN", "UNIT", "FORTRESS", "BOSS", "OUT_OF_BOUNDS"],
      issues,
      true,
    );
  });
  if (ids.size === AMMUNITION_TYPES.size && [...AMMUNITION_TYPES].some(id => !ids.has(id))) {
    issues.push(issue(path, "must define every stable ammunition ID exactly once"));
  }
}

function validateReload(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const reload = asRecord(value);
  if (!reload) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(reload, path, ["reloadSeconds"], issues);
  if (!nonNegativeNumber(reload.reloadSeconds)) {
    issues.push(issue(`${path}.reloadSeconds`, "must be non-negative"));
  }
}

function validateInventory(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const inventory = asRecord(value);
  if (!inventory) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(inventory, path, ["unlimited", "startingCount"], issues);
  if (typeof inventory.unlimited !== "boolean") {
    issues.push(issue(`${path}.unlimited`, "must be a boolean"));
  }
  if (inventory.startingCount !== undefined && !nonNegativeInteger(inventory.startingCount)) {
    issues.push(issue(`${path}.startingCount`, "must be a non-negative integer"));
  }
}

function validateFlight(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const flight = asRecord(value);
  if (!flight) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(
    flight,
    path,
    [
      "mass", "windResponse", "gravityResponse", "drag", "initialSpeedMultiplier",
      "maximumLifetimeSeconds", "apexFragmentation",
    ],
    issues,
  );
  if (!positiveNumber(flight.mass)) issues.push(issue(`${path}.mass`, "must be positive"));
  if (!nonNegativeNumber(flight.windResponse)) {
    issues.push(issue(`${path}.windResponse`, "must be non-negative"));
  }
  if (flight.gravityResponse !== undefined && !nonNegativeNumber(flight.gravityResponse)) {
    issues.push(issue(`${path}.gravityResponse`, "must be non-negative"));
  }
  if (flight.drag !== undefined && !nonNegativeNumber(flight.drag)) {
    issues.push(issue(`${path}.drag`, "must be non-negative"));
  }
  if (flight.initialSpeedMultiplier !== undefined && !positiveNumber(flight.initialSpeedMultiplier)) {
    issues.push(issue(`${path}.initialSpeedMultiplier`, "must be positive"));
  }
  if (flight.maximumLifetimeSeconds !== undefined && !positiveNumber(flight.maximumLifetimeSeconds)) {
    issues.push(issue(`${path}.maximumLifetimeSeconds`, "must be positive"));
  }
  if (flight.apexFragmentation !== undefined && flight.apexFragmentation !== null) {
    const apex = asRecord(flight.apexFragmentation);
    const apexPath = `${path}.apexFragmentation`;
    if (!apex) {
      issues.push(issue(apexPath, "must be a JSON object or null"));
      return;
    }
    checkKeys(
      apex,
      apexPath,
      [
        "fragmentsPerSide", "minimumReferenceSpeed", "horizontalSpreadPerStep",
        "initialDownwardSpeed", "downwardSpreadPerStep",
      ],
      issues,
    );
    if (!positiveInteger(apex.fragmentsPerSide)) {
      issues.push(issue(`${apexPath}.fragmentsPerSide`, "must be a positive integer"));
    }
    if (!positiveNumber(apex.minimumReferenceSpeed)) {
      issues.push(issue(`${apexPath}.minimumReferenceSpeed`, "must be positive"));
    }
    for (const key of ["horizontalSpreadPerStep", "initialDownwardSpeed", "downwardSpreadPerStep"]) {
      if (!nonNegativeNumber(apex[key])) issues.push(issue(`${apexPath}.${key}`, "must be non-negative"));
    }
  }
}

function validateCollision(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const collision = asRecord(value);
  if (!collision) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(collision, path, ["hitLayers", "mode", "hitRadius", "friendlyFire"], issues);
  validateEnumArray(
    collision.hitLayers,
    `${path}.hitLayers`,
    ["TERRAIN", "UNIT", "FORTRESS", "BOSS", "OUT_OF_BOUNDS"],
    issues,
    true,
  );
  if (!oneOf(collision.mode, ["STOP_ON_FIRST_TARGET", "PIERCE_UNITS", "IGNORE_UNITS", "IMPACT_ON_TERRAIN"])) {
    issues.push(issue(`${path}.mode`, "is not a supported collision mode"));
  }
  if (!nonNegativeNumber(collision.hitRadius)) {
    issues.push(issue(`${path}.hitRadius`, "must be non-negative"));
  }
  if (
    collision.friendlyFire !== undefined &&
    !oneOf(collision.friendlyFire, ["ENEMY_ONLY", "ALL_UNITS", "ALL_TARGETS"])
  ) {
    issues.push(issue(`${path}.friendlyFire`, "is not a supported friendly-fire policy"));
  }
}

function validateDamage(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const damage = asRecord(value);
  if (!damage) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(
    damage,
    path,
    ["unitDamage", "fortressDamage", "unitRadius", "fortressRadius", "falloff", "minimumDamage"],
    issues,
  );
  for (const key of ["unitDamage", "fortressDamage"]) {
    if (!nonNegativeInteger(damage[key])) {
      issues.push(issue(`${path}.${key}`, "must be a non-negative integer"));
    }
  }
  for (const key of ["unitRadius", "fortressRadius"]) {
    if (!nonNegativeNumber(damage[key])) issues.push(issue(`${path}.${key}`, "must be non-negative"));
  }
  if (damage.falloff !== undefined && !oneOf(damage.falloff, ["NONE", "LINEAR"])) {
    issues.push(issue(`${path}.falloff`, "must be NONE or LINEAR"));
  }
  if (damage.minimumDamage !== undefined && !nonNegativeInteger(damage.minimumDamage)) {
    issues.push(issue(`${path}.minimumDamage`, "must be a non-negative integer"));
  }
}

function validateTerrain(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  const terrain = asRecord(value);
  if (!terrain) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(terrain, path, ["deformsTerrain", "craterRadius", "craterDepth"], issues);
  if (typeof terrain.deformsTerrain !== "boolean") {
    issues.push(issue(`${path}.deformsTerrain`, "must be a boolean"));
  }
  if (!nonNegativeNumber(terrain.craterRadius)) {
    issues.push(issue(`${path}.craterRadius`, "must be non-negative"));
  }
  if (!nonNegativeNumber(terrain.craterDepth)) {
    issues.push(issue(`${path}.craterDepth`, "must be non-negative"));
  }
  if (
    terrain.deformsTerrain === false &&
    (terrain.craterRadius !== 0 || terrain.craterDepth !== 0)
  ) {
    issues.push(issue(path, "disabled terrain deformation must have zero crater values"));
  }
}

function validateAmmunitionBehavior(
  value: unknown,
  path: string,
  issues: BalanceValidationIssue[],
): void {
  const behavior = asRecord(value);
  if (!behavior) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  if (behavior.kind === "DIRECT_IMPACT") {
    checkKeys(behavior, path, ["kind"], issues);
    return;
  }
  if (behavior.kind === "EXPLOSION") {
    checkKeys(behavior, path, ["kind", "radius", "lifetimeSeconds"], issues);
    if (!nonNegativeNumber(behavior.radius)) issues.push(issue(`${path}.radius`, "must be non-negative"));
    if (!positiveNumber(behavior.lifetimeSeconds)) {
      issues.push(issue(`${path}.lifetimeSeconds`, "must be positive"));
    }
    return;
  }
  if (behavior.kind === "LINGERING_AREA") {
    checkKeys(
      behavior,
      path,
      [
        "kind", "radius", "durationSeconds", "tickIntervalSeconds", "damagePerTick",
        "fortressDamagePerTick", "driftsWithWind", "windDriftMultiplier", "followsTerrain",
        "unitFriendlyFire",
      ],
      issues,
      ["status"],
    );
    if (!nonNegativeNumber(behavior.radius)) issues.push(issue(`${path}.radius`, "must be non-negative"));
    if (!positiveNumber(behavior.durationSeconds)) {
      issues.push(issue(`${path}.durationSeconds`, "must be positive"));
    }
    if (!positiveNumber(behavior.tickIntervalSeconds)) {
      issues.push(issue(`${path}.tickIntervalSeconds`, "must be positive"));
    }
    for (const key of ["damagePerTick", "fortressDamagePerTick"]) {
      if (!nonNegativeInteger(behavior[key])) {
        issues.push(issue(`${path}.${key}`, "must be a non-negative integer"));
      }
    }
    if (typeof behavior.driftsWithWind !== "boolean") {
      issues.push(issue(`${path}.driftsWithWind`, "must be a boolean"));
    }
    if (behavior.windDriftMultiplier !== undefined && !nonNegativeNumber(behavior.windDriftMultiplier)) {
      issues.push(issue(`${path}.windDriftMultiplier`, "must be non-negative"));
    }
    if (behavior.followsTerrain !== undefined && typeof behavior.followsTerrain !== "boolean") {
      issues.push(issue(`${path}.followsTerrain`, "must be a boolean"));
    }
    if (behavior.status !== undefined && behavior.status !== null &&
        !oneOf(behavior.status, ["BURNING", "POISONED", "SLOWED", "STUNNED", "HASTED"])) {
      issues.push(issue(`${path}.status`, "is not a supported unit status"));
    }
    if (
      behavior.unitFriendlyFire !== undefined &&
      !oneOf(behavior.unitFriendlyFire, ["ENEMY_ONLY", "ALL_UNITS", "ALL_TARGETS"])
    ) {
      issues.push(issue(`${path}.unitFriendlyFire`, "is not a supported friendly-fire policy"));
    }
    return;
  }
  issues.push(issue(`${path}.kind`, "is not a supported ammunition behavior"));
}

function validateSpells(value: unknown, issues: BalanceValidationIssue[]): void {
  const path = "spells";
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return;
  }
  if (value.length !== SPELL_TYPES.size) {
    issues.push(issue(path, "must define every stable spell ID exactly once"));
  }
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const itemPath = `${path}[${index}]`;
    const spell = asRecord(value);
    if (!spell) {
      issues.push(issue(itemPath, "must be a JSON object"));
      return;
    }
    checkKeys(
      spell,
      itemPath,
      [
        "type", "goldCost", "category", "targetCategory", "special", "manaCost",
        "effect", "recastableWhileActive",
      ],
      issues,
    );
    if (typeof spell.type !== "string" || !SPELL_TYPES.has(spell.type)) {
      issues.push(issue(`${itemPath}.type`, "must be a stable spell ID"));
    } else if (ids.has(spell.type)) {
      issues.push(issue(`${itemPath}.type`, "must be unique"));
    } else {
      ids.add(spell.type);
    }
    if (!nonNegativeInteger(spell.goldCost)) {
      issues.push(issue(`${itemPath}.goldCost`, "must be a non-negative integer"));
    }
    if (!oneOf(spell.category, ["OFFENSIVE", "DEFENSIVE", "BATTLEFIELD_CONTROL", "SUPPORT", "SIEGE"])) {
      issues.push(issue(`${itemPath}.category`, "is not a supported spell category"));
    }
    if (!oneOf(spell.targetCategory, [
      "ALLIED_FORCE", "ALLIED_UNIT", "ENEMY_FORCE", "ENEMY_SIEGE_ENGINE", "OWN_FORTRESS", "OWN_GATE",
    ])) {
      issues.push(issue(`${itemPath}.targetCategory`, "is not a supported spell target"));
    }
    if (typeof spell.special !== "boolean") issues.push(issue(`${itemPath}.special`, "must be a boolean"));
    if (!positiveInteger(spell.manaCost)) {
      issues.push(issue(`${itemPath}.manaCost`, "must be a positive integer"));
    }
    if (
      spell.recastableWhileActive !== undefined &&
      typeof spell.recastableWhileActive !== "boolean"
    ) {
      issues.push(issue(`${itemPath}.recastableWhileActive`, "must be a boolean"));
    }
    if (spell.type === "HASTE" || spell.type === "HEAL") {
      if (spell.special !== false) {
        issues.push(issue(`${itemPath}.special`, "campaign milestone spells must not be special"));
      }
    }
    validateSpellEffect(spell.effect, `${itemPath}.effect`, issues);
    const expectedEffect = spell.type === "HASTE"
      ? "HASTE"
      : spell.type === "FORTRESS_SHIELD"
        ? "FORTRESS_SHIELD"
        : spell.type === "RUNIC_BASTION"
          ? "RUNIC_BASTION"
          : null;
    const actualEffect = asRecord(spell.effect)?.kind;
    if (
      expectedEffect === null
        ? spell.effect !== undefined && spell.effect !== null
        : actualEffect !== expectedEffect
    ) {
      issues.push(issue(`${itemPath}.effect`, "does not match the spell's implemented effect"));
    }
  });
  if (ids.size === SPELL_TYPES.size && [...SPELL_TYPES].some(id => !ids.has(id))) {
    issues.push(issue(path, "must define every stable spell ID exactly once"));
  }
}

function validateSpellEffect(value: unknown, path: string, issues: BalanceValidationIssue[]): void {
  if (value === undefined || value === null) return;
  const effect = asRecord(value);
  if (!effect) {
    issues.push(issue(path, "must be a JSON object or null"));
    return;
  }
  if (effect.kind === "HASTE") {
    checkKeys(effect, path, ["kind", "durationMilliseconds", "movementSpeedMultiplier"], issues);
    if (!positiveInteger(effect.durationMilliseconds)) {
      issues.push(issue(`${path}.durationMilliseconds`, "must be a positive integer"));
    }
    if (!numberAtLeast(effect.movementSpeedMultiplier, Number.MIN_VALUE) ||
        typeof effect.movementSpeedMultiplier === "number" && effect.movementSpeedMultiplier <= 1) {
      issues.push(issue(`${path}.movementSpeedMultiplier`, "must be greater than 1"));
    }
    return;
  }
  if (effect.kind === "FORTRESS_SHIELD" || effect.kind === "RUNIC_BASTION") {
    checkKeys(effect, path, ["kind", "hitPoints"], issues);
    if (!positiveInteger(effect.hitPoints)) {
      issues.push(issue(`${path}.hitPoints`, "must be a positive integer"));
    }
    return;
  }
  issues.push(issue(`${path}.kind`, "is not a supported spell effect"));
}

function validateMana(value: unknown, issues: BalanceValidationIssue[]): void {
  const path = "mana";
  const mana = asRecord(value);
  if (!mana) {
    issues.push(issue(path, "must be a JSON object"));
    return;
  }
  checkKeys(
    mana,
    path,
    ["startingMana", "maximumMana", "regenerationAmount", "regenerationIntervalMilliseconds"],
    issues,
  );
  if (!nonNegativeInteger(mana.startingMana)) {
    issues.push(issue(`${path}.startingMana`, "must be a non-negative integer"));
  }
  if (!positiveInteger(mana.maximumMana)) {
    issues.push(issue(`${path}.maximumMana`, "must be a positive integer"));
  }
  if (
    typeof mana.startingMana === "number" &&
    typeof mana.maximumMana === "number" &&
    mana.startingMana > mana.maximumMana
  ) {
    issues.push(issue(`${path}.startingMana`, "must not exceed maximumMana"));
  }
  if (!positiveInteger(mana.regenerationAmount)) {
    issues.push(issue(`${path}.regenerationAmount`, "must be a positive integer"));
  }
  if (!positiveInteger(mana.regenerationIntervalMilliseconds)) {
    issues.push(issue(`${path}.regenerationIntervalMilliseconds`, "must be a positive integer"));
  }
}

function validateRuntime(value: unknown, issues: BalanceValidationIssue[]): void {
  const runtime = requiredRecord(
    value,
    "runtime",
    [
      "economy", "fortresses", "catapult", "match", "physics", "wind", "combat",
      "bossCombat", "aiProfiles", "aiStrategy", "rewards", "campaign",
    ],
    issues,
  );
  if (!runtime) return;

  const economy = requiredRecord(
    runtime.economy,
    "runtime.economy",
    ["startingGold", "startingSupply", "maximumSupply", "supplyRegenerationIntervalSeconds"],
    issues,
  );
  if (economy) {
    if (!nonNegativeInteger(economy.startingGold)) {
      issues.push(issue("runtime.economy.startingGold", "must be a non-negative integer"));
    }
    if (!nonNegativeInteger(economy.startingSupply)) {
      issues.push(issue("runtime.economy.startingSupply", "must be a non-negative integer"));
    }
    if (!positiveInteger(economy.maximumSupply)) {
      issues.push(issue("runtime.economy.maximumSupply", "must be a positive integer"));
    }
    if (
      typeof economy.startingSupply === "number" &&
      typeof economy.maximumSupply === "number" &&
      economy.startingSupply > economy.maximumSupply
    ) {
      issues.push(issue("runtime.economy.startingSupply", "must not exceed maximumSupply"));
    }
    if (!positiveNumber(economy.supplyRegenerationIntervalSeconds)) {
      issues.push(issue(
        "runtime.economy.supplyRegenerationIntervalSeconds",
        "must be positive",
      ));
    }
  }

  const fortresses = requiredRecord(
    runtime.fortresses,
    "runtime.fortresses",
    ["standardMaximumHealth", "demoBlueMaximumHealth"],
    issues,
  );
  if (fortresses) {
    for (const key of ["standardMaximumHealth", "demoBlueMaximumHealth"]) {
      if (!positiveInteger(fortresses[key])) {
        issues.push(issue(`runtime.fortresses.${key}`, "must be a positive integer"));
      }
    }
  }

  const catapult = requiredRecord(
    runtime.catapult,
    "runtime.catapult",
    ["initialAimDirectionDegrees", "initialAimPower"],
    issues,
  );
  if (catapult) {
    if (
      typeof catapult.initialAimDirectionDegrees !== "number" ||
      !Number.isFinite(catapult.initialAimDirectionDegrees) ||
      catapult.initialAimDirectionDegrees < 0 ||
      catapult.initialAimDirectionDegrees > 180
    ) {
      issues.push(issue(
        "runtime.catapult.initialAimDirectionDegrees",
        "must be between 0 and 180 degrees",
      ));
    }
    if (
      typeof catapult.initialAimPower !== "number" ||
      !Number.isFinite(catapult.initialAimPower) ||
      catapult.initialAimPower < 0 ||
      catapult.initialAimPower > 100
    ) {
      issues.push(issue("runtime.catapult.initialAimPower", "must be between 0 and 100"));
    }
  }

  const match = requiredRecord(
    runtime.match,
    "runtime.match",
    ["durationMilliseconds", "bossEncounterDurationMilliseconds"],
    issues,
  );
  if (match) {
    for (const key of ["durationMilliseconds", "bossEncounterDurationMilliseconds"]) {
      if (!positiveInteger(match[key])) {
        issues.push(issue(`runtime.match.${key}`, "must be a positive integer"));
      }
    }
  }

  const physics = requiredRecord(
    runtime.physics,
    "runtime.physics",
    ["gravityAccelerationY", "windAccelerationScale"],
    issues,
  );
  if (physics) {
    if (!nonNegativeNumber(physics.gravityAccelerationY)) {
      issues.push(issue("runtime.physics.gravityAccelerationY", "must be non-negative"));
    }
    if (!positiveNumber(physics.windAccelerationScale)) {
      issues.push(issue("runtime.physics.windAccelerationScale", "must be positive"));
    }
  }

  const wind = requiredRecord(
    runtime.wind,
    "runtime.wind",
    [
      "initialDelaySeconds", "transitionSeconds", "minimumChangeIntervalSeconds",
      "maximumChangeIntervalSeconds", "minimumStrength", "maximumStrength",
    ],
    issues,
  );
  if (wind) {
    if (!nonNegativeNumber(wind.initialDelaySeconds)) {
      issues.push(issue("runtime.wind.initialDelaySeconds", "must be non-negative"));
    }
    if (!positiveNumber(wind.transitionSeconds)) {
      issues.push(issue("runtime.wind.transitionSeconds", "must be positive"));
    }
    if (!positiveNumber(wind.minimumChangeIntervalSeconds)) {
      issues.push(issue("runtime.wind.minimumChangeIntervalSeconds", "must be positive"));
    }
    if (!positiveNumber(wind.maximumChangeIntervalSeconds)) {
      issues.push(issue("runtime.wind.maximumChangeIntervalSeconds", "must be positive"));
    }
    if (
      typeof wind.minimumChangeIntervalSeconds === "number" &&
      typeof wind.maximumChangeIntervalSeconds === "number" &&
      wind.maximumChangeIntervalSeconds < wind.minimumChangeIntervalSeconds
    ) {
      issues.push(issue(
        "runtime.wind.maximumChangeIntervalSeconds",
        "must not be less than minimumChangeIntervalSeconds",
      ));
    }
    if (!nonNegativeNumber(wind.minimumStrength)) {
      issues.push(issue("runtime.wind.minimumStrength", "must be non-negative"));
    }
    if (!nonNegativeNumber(wind.maximumStrength)) {
      issues.push(issue("runtime.wind.maximumStrength", "must be non-negative"));
    }
    if (
      typeof wind.minimumStrength === "number" &&
      typeof wind.maximumStrength === "number" &&
      wind.maximumStrength < wind.minimumStrength
    ) {
      issues.push(issue("runtime.wind.maximumStrength", "must not be less than minimumStrength"));
    }
  }

  const combat = requiredRecord(
    runtime.combat,
    "runtime.combat",
    [
      "minimumDamageVarianceFactor", "maximumDamageVarianceFactor",
      "unitLuckBiasMaximumMagnitude", "maximumInitialAttackDelayRatio", "unitMovementSpeedMultiplier",
      "unitMovementSpeedScale", "garrisonMovementSpeedScale", "unitInteractionRange",
      "fortressAttackRange", "sapperFortressStandoffDistance",
      "sapperContactSpeedMultiplier", "meleeAttackStandoffDistance",
      "attackFormationSpacing", "meleeSynergyDamagePerAdditionalUnit",
      "maximumMeleeSynergyMultiplier", "demolisher",
    ],
    issues,
  );
  if (combat) {
    if (!positiveNumber(combat.minimumDamageVarianceFactor)) {
      issues.push(issue("runtime.combat.minimumDamageVarianceFactor", "must be positive"));
    }
    if (!positiveNumber(combat.maximumDamageVarianceFactor)) {
      issues.push(issue("runtime.combat.maximumDamageVarianceFactor", "must be positive"));
    }
    if (
      typeof combat.minimumDamageVarianceFactor === "number" &&
      typeof combat.maximumDamageVarianceFactor === "number" &&
      combat.maximumDamageVarianceFactor < combat.minimumDamageVarianceFactor
    ) {
      issues.push(issue(
        "runtime.combat.maximumDamageVarianceFactor",
        "must not be less than minimumDamageVarianceFactor",
      ));
    }
    if (!nonNegativeNumber(combat.unitLuckBiasMaximumMagnitude)) {
      issues.push(issue("runtime.combat.unitLuckBiasMaximumMagnitude", "must be non-negative"));
    }
    if (!fraction(combat.maximumInitialAttackDelayRatio)) {
      issues.push(issue(
        "runtime.combat.maximumInitialAttackDelayRatio",
        "must be between 0 and 1",
      ));
    }
    if (!positiveNumber(combat.unitMovementSpeedMultiplier)) {
      issues.push(issue("runtime.combat.unitMovementSpeedMultiplier", "must be positive"));
    }
    for (const key of [
      "unitMovementSpeedScale", "garrisonMovementSpeedScale", "unitInteractionRange",
      "fortressAttackRange", "sapperFortressStandoffDistance",
      "meleeAttackStandoffDistance", "attackFormationSpacing",
      "meleeSynergyDamagePerAdditionalUnit",
    ]) {
      if (!nonNegativeNumber(combat[key])) {
        issues.push(issue(`runtime.combat.${key}`, "must be non-negative"));
      }
    }
    if (!fraction(combat.sapperContactSpeedMultiplier)) {
      issues.push(issue(
        "runtime.combat.sapperContactSpeedMultiplier",
        "must be between 0 and 1",
      ));
    }
    if (!numberAtLeast(combat.maximumMeleeSynergyMultiplier, 1)) {
      issues.push(issue(
        "runtime.combat.maximumMeleeSynergyMultiplier",
        "must be at least 1",
      ));
    }

    const demolisher = requiredRecord(
      combat.demolisher,
      "runtime.combat.demolisher",
      ["explosionDamage", "blastRadius", "throwDurationSeconds", "throwForwardDistance"],
      issues,
    );
    if (demolisher) {
      if (!positiveInteger(demolisher.explosionDamage)) {
        issues.push(issue("runtime.combat.demolisher.explosionDamage", "must be a positive integer"));
      }
      if (!positiveNumber(demolisher.blastRadius)) {
        issues.push(issue("runtime.combat.demolisher.blastRadius", "must be positive"));
      }
      if (!positiveNumber(demolisher.throwDurationSeconds)) {
        issues.push(issue("runtime.combat.demolisher.throwDurationSeconds", "must be positive"));
      }
      if (!nonNegativeNumber(demolisher.throwForwardDistance)) {
        issues.push(issue(
          "runtime.combat.demolisher.throwForwardDistance",
          "must be non-negative",
        ));
      }
    }
  }

  const bossCombat = requiredRecord(
    runtime.bossCombat,
    "runtime.bossCombat",
    ["largerGroupTargetChance", "maximumDistanceFalloff"],
    issues,
  );
  if (bossCombat) {
    for (const key of ["largerGroupTargetChance", "maximumDistanceFalloff"]) {
      if (!fraction(bossCombat[key])) {
        issues.push(issue(`runtime.bossCombat.${key}`, "must be between 0 and 1"));
      }
    }
  }

  validateAiProfiles(runtime.aiProfiles, issues);
  validateAiStrategy(runtime.aiStrategy, issues);

  const rewards = requiredRecord(
    runtime.rewards,
    "runtime.rewards",
    [
      "winGold", "directEnemyUnitHitGold", "fastWinGold",
      "fastWinThresholdMilliseconds", "untouchedFortressGold",
      "fortressHitStreakGoldPerHitBeyondFirst", "fortressHitStreakStartHits",
    ],
    issues,
  );
  if (rewards) {
    for (const key of [
      "winGold", "directEnemyUnitHitGold", "fastWinGold", "untouchedFortressGold",
      "fortressHitStreakGoldPerHitBeyondFirst",
    ]) {
      if (!nonNegativeInteger(rewards[key])) {
        issues.push(issue(`runtime.rewards.${key}`, "must be a non-negative integer"));
      }
    }
    if (!positiveInteger(rewards.fastWinThresholdMilliseconds)) {
      issues.push(issue("runtime.rewards.fastWinThresholdMilliseconds", "must be a positive integer"));
    }
    if (
      !positiveInteger(rewards.fortressHitStreakStartHits) ||
      typeof rewards.fortressHitStreakStartHits === "number" &&
        rewards.fortressHitStreakStartHits < 2
    ) {
      issues.push(issue("runtime.rewards.fortressHitStreakStartHits", "must be at least 2"));
    }
  }

  validateCampaignBalance(runtime.campaign, issues);
}

function validateAiProfiles(value: unknown, issues: BalanceValidationIssue[]): void {
  const path = "runtime.aiProfiles";
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return;
  }
  if (value.length !== AI_PROFILE_IDS.size) {
    issues.push(issue(path, "must define every standard AI profile exactly once"));
  }
  const ids = new Set<string>();
  value.forEach((value, index) => {
    const itemPath = `${path}[${index}]`;
    const profile = requiredRecord(
      value,
      itemPath,
      [
        "profileId", "angleErrorDegrees", "powerError", "reactionIntervalMilliseconds",
        "unitRecruitmentIntervalMilliseconds", "progressionErrorReduction",
        "progressionReactionReduction",
      ],
      issues,
    );
    if (!profile) return;
    if (typeof profile.profileId !== "string" || !AI_PROFILE_IDS.has(profile.profileId)) {
      issues.push(issue(`${itemPath}.profileId`, "must be a standard AI profile ID"));
    } else if (ids.has(profile.profileId)) {
      issues.push(issue(`${itemPath}.profileId`, "must be unique"));
    } else {
      ids.add(profile.profileId);
    }
    if (!nonNegativeNumber(profile.angleErrorDegrees)) {
      issues.push(issue(`${itemPath}.angleErrorDegrees`, "must be non-negative"));
    }
    if (!nonNegativeNumber(profile.powerError)) {
      issues.push(issue(`${itemPath}.powerError`, "must be non-negative"));
    }
    if (!positiveInteger(profile.reactionIntervalMilliseconds)) {
      issues.push(issue(`${itemPath}.reactionIntervalMilliseconds`, "must be a positive integer"));
    }
    if (!nonNegativeInteger(profile.unitRecruitmentIntervalMilliseconds)) {
      issues.push(issue(`${itemPath}.unitRecruitmentIntervalMilliseconds`, "must be non-negative"));
    }
    if (!fraction(profile.progressionErrorReduction)) {
      issues.push(issue(`${itemPath}.progressionErrorReduction`, "must be between 0 and 1"));
    }
    if (!fraction(profile.progressionReactionReduction)) {
      issues.push(issue(`${itemPath}.progressionReactionReduction`, "must be between 0 and 1"));
    }
  });
  if (ids.size === AI_PROFILE_IDS.size && [...AI_PROFILE_IDS].some(id => !ids.has(id))) {
    issues.push(issue(path, "must define every standard AI profile exactly once"));
  }
}

function validateCampaignBalance(value: unknown, issues: BalanceValidationIssue[]): void {
  const campaign = requiredRecord(
    value,
    "runtime.campaign",
    [
      "initialWindStrength", "initialWindStrengthStep", "initialWindStrengthBands",
      "demoInitialWindStrength", "aiDifficultyBands",
    ],
    issues,
  );
  if (!campaign) return;
  for (const key of ["initialWindStrength", "initialWindStrengthStep", "demoInitialWindStrength"]) {
    if (!nonNegativeNumber(campaign[key])) {
      issues.push(issue(`runtime.campaign.${key}`, "must be non-negative"));
    }
  }
  if (!positiveInteger(campaign.initialWindStrengthBands)) {
    issues.push(issue("runtime.campaign.initialWindStrengthBands", "must be a positive integer"));
  }
  if (!Array.isArray(campaign.aiDifficultyBands) || campaign.aiDifficultyBands.length === 0) {
    issues.push(issue("runtime.campaign.aiDifficultyBands", "must contain at least one band"));
    return;
  }

  const bands: { firstLevel: number; lastLevel: number }[] = [];
  campaign.aiDifficultyBands.forEach((value, index) => {
    const path = `runtime.campaign.aiDifficultyBands[${index}]`;
    const band = requiredRecord(
      value,
      path,
      [
        "firstLevel", "lastLevel", "profileId",
        "progressionStartPercent", "progressionEndPercent",
      ],
      issues,
    );
    if (!band) return;
    if (!positiveInteger(band.firstLevel)) {
      issues.push(issue(`${path}.firstLevel`, "must be a positive integer"));
    }
    if (!positiveInteger(band.lastLevel)) {
      issues.push(issue(`${path}.lastLevel`, "must be a positive integer"));
    }
    if (
      positiveInteger(band.firstLevel) &&
      positiveInteger(band.lastLevel) &&
      band.lastLevel < band.firstLevel
    ) {
      issues.push(issue(`${path}.lastLevel`, "must not precede firstLevel"));
    }
    if (typeof band.profileId !== "string" || !AI_PROFILE_IDS.has(band.profileId)) {
      issues.push(issue(`${path}.profileId`, "must be a standard AI profile ID"));
    }
    for (const key of ["progressionStartPercent", "progressionEndPercent"]) {
      if (!nonNegativeInteger(band[key]) || typeof band[key] === "number" && band[key] > 100) {
        issues.push(issue(`${path}.${key}`, "must be an integer between 0 and 100"));
      }
    }
    if (positiveInteger(band.firstLevel) && positiveInteger(band.lastLevel)) {
      bands.push({ firstLevel: band.firstLevel, lastLevel: band.lastLevel });
    }
  });
  if (bands.length > 0) {
    if (bands[0].firstLevel !== 1) {
      issues.push(issue("runtime.campaign.aiDifficultyBands[0].firstLevel", "must start at level 1"));
    }
    for (let index = 1; index < bands.length; index += 1) {
      if (bands[index - 1].lastLevel + 1 !== bands[index].firstLevel) {
        issues.push(issue(
          `runtime.campaign.aiDifficultyBands[${index}].firstLevel`,
          "bands must be ordered, contiguous, and non-overlapping",
        ));
      }
    }
    if (bands[bands.length - 1].lastLevel !== 100) {
      issues.push(issue(
        `runtime.campaign.aiDifficultyBands[${bands.length - 1}].lastLevel`,
        "must cover the final campaign level 100",
      ));
    }
  }
}

function validateAiStrategy(value: unknown, issues: BalanceValidationIssue[]): void {
  const strategy = requiredRecord(
    value,
    "runtime.aiStrategy",
    [
      "aimAngleOffsetDegrees", "horizontalDistancePerPowerPercent",
      "ammunitionScoring", "unitScoring", "waveDispatch",
    ],
    issues,
  );
  if (!strategy) return;
  if (typeof strategy.aimAngleOffsetDegrees !== "number" ||
      !Number.isFinite(strategy.aimAngleOffsetDegrees)) {
    issues.push(issue("runtime.aiStrategy.aimAngleOffsetDegrees", "must be finite"));
  }
  if (!positiveNumber(strategy.horizontalDistancePerPowerPercent)) {
    issues.push(issue(
      "runtime.aiStrategy.horizontalDistancePerPowerPercent",
      "must be positive",
    ));
  }

  const ammunitionScoring = requiredRecord(
    strategy.ammunitionScoring,
    "runtime.aiStrategy.ammunitionScoring",
    [
      "situationalFortressDamageWeight", "situationalUnitDamageWeight",
      "situationalFortressRadiusWeight", "situationalUnitRadiusWeight",
      "counterplayUnitDamageWeight", "counterplayUnitRadiusWeight",
      "counterplayFortressDamageWeight", "counterplayFortressRadiusWeight",
    ],
    issues,
  );
  if (ammunitionScoring) {
    for (const key of [
      "situationalFortressDamageWeight", "situationalUnitDamageWeight",
      "situationalFortressRadiusWeight", "situationalUnitRadiusWeight",
      "counterplayUnitDamageWeight", "counterplayUnitRadiusWeight",
      "counterplayFortressDamageWeight", "counterplayFortressRadiusWeight",
    ]) {
      if (!nonNegativeNumber(ammunitionScoring[key])) {
        issues.push(issue(`runtime.aiStrategy.ammunitionScoring.${key}`, "must be non-negative"));
      }
    }
  }

  const unitScoring = requiredRecord(
    strategy.unitScoring,
    "runtime.aiStrategy.unitScoring",
    [
      "sapperPriorityScore", "demolisherPriorityScore", "attackDamageWeight",
      "speedWeight", "defenderVsSapperRoleBonus", "rangedVsRangedRoleBonus",
      "rangedVsMeleeRoleBonus", "supplyCostPenaltyWeight",
    ],
    issues,
  );
  if (unitScoring) {
    for (const key of [
      "sapperPriorityScore", "demolisherPriorityScore", "attackDamageWeight",
      "speedWeight", "defenderVsSapperRoleBonus", "rangedVsRangedRoleBonus",
      "rangedVsMeleeRoleBonus", "supplyCostPenaltyWeight",
    ]) {
      if (!nonNegativeNumber(unitScoring[key])) {
        issues.push(issue(`runtime.aiStrategy.unitScoring.${key}`, "must be non-negative"));
      }
    }
  }

  const waveDispatch = requiredRecord(
    strategy.waveDispatch,
    "runtime.aiStrategy.waveDispatch",
    [
      "situationalMinimumReadyUnits", "counterplayMinimumReadyUnits",
      "counterplayMinimumReadyUnitsAgainstMovingEnemies", "lowFortressHealthFraction",
    ],
    issues,
  );
  if (waveDispatch) {
    for (const key of [
      "situationalMinimumReadyUnits", "counterplayMinimumReadyUnits",
      "counterplayMinimumReadyUnitsAgainstMovingEnemies",
    ]) {
      if (!positiveInteger(waveDispatch[key])) {
        issues.push(issue(`runtime.aiStrategy.waveDispatch.${key}`, "must be a positive integer"));
      }
    }
    if (!fraction(waveDispatch.lowFortressHealthFraction)) {
      issues.push(issue(
        "runtime.aiStrategy.waveDispatch.lowFortressHealthFraction",
        "must be between 0 and 1",
      ));
    }
  }
}

function requiredRecord(
  value: unknown,
  path: string,
  keys: readonly string[],
  issues: BalanceValidationIssue[],
): Record<string, unknown> | null {
  const record = asRecord(value);
  if (!record) {
    issues.push(issue(path, "must be a JSON object"));
    return null;
  }
  checkKeys(record, path, keys, issues);
  return record;
}

function validateEnumArray(
  value: unknown,
  path: string,
  allowed: readonly string[],
  issues: BalanceValidationIssue[],
  nonEmpty: boolean,
): void {
  if (!Array.isArray(value)) {
    issues.push(issue(path, "must be an array"));
    return;
  }
  if (nonEmpty && value.length === 0) issues.push(issue(path, "must not be empty"));
  const seen = new Set<string>();
  value.forEach((item, index) => {
    if (typeof item !== "string" || !allowed.includes(item)) {
      issues.push(issue(`${path}[${index}]`, "contains an unsupported value"));
    } else if (seen.has(item)) {
      issues.push(issue(`${path}[${index}]`, "must be unique"));
    } else {
      seen.add(item);
    }
  });
}

function oneOf(value: unknown, allowed: readonly string[]): boolean {
  return typeof value === "string" && allowed.includes(value);
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
          "windupSeconds",
        ],
        issues,
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
      if (!nonNegativeNumber(attack.windupSeconds)) {
        issues.push(issue(`${attackPath}.windupSeconds`, "must be non-negative"));
      }
      if (attack.windupSeconds !== 0 && attack.target !== "UNIT") {
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
  const immuneAmmunitionTypes = profile.immuneAmmunitionTypes;
  if (!Array.isArray(immuneAmmunitionTypes)) {
    issues.push(issue(`${path}.immuneAmmunitionTypes`, "must be an array"));
  } else {
    const immunities = new Set<string>();
    immuneAmmunitionTypes.forEach((value, index) => {
      if (typeof value !== "string" || !AMMUNITION_TYPES.has(value)) {
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

const nonNegativeInteger = (value: unknown): value is number =>
  typeof value === "number" && Number.isInteger(value) && value >= 0;

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
