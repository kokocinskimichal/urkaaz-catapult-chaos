export type BossArchetype = "CENTRAL_OBJECTIVE" | "MOBILE_SIEGE_BOSS";
export type BossMovementKind = "STATIC" | "ADVANCE";
export type BossAttackTarget = "UNIT" | "FORTRESS_GATE";
export type UnitId = "SAPPER" | "DEFENDER" | "DEMOLISHER" | "RAGING_BOAR" | "SLINGMASTER";
export type AmmunitionType =
  | "ROCK"
  | "SIEGE_BOMB"
  | "POWDER_BARREL"
  | "FIRE_RAIN"
  | "PLAGUE_CAULDRON";
export type AmmunitionEffect = "DIRECT" | "EXPLOSIVE" | "INCENDIARY" | "PLAGUE";
export type AmmunitionHitLayer = "TERRAIN" | "UNIT" | "FORTRESS" | "BOSS" | "OUT_OF_BOUNDS";
export type AmmunitionCollisionMode =
  | "STOP_ON_FIRST_TARGET"
  | "PIERCE_UNITS"
  | "IGNORE_UNITS"
  | "IMPACT_ON_TERRAIN";
export type FriendlyFirePolicy = "ENEMY_ONLY" | "ALL_UNITS" | "ALL_TARGETS";
export type AmmunitionFalloff = "NONE" | "LINEAR";
export type UnitStatus = "BURNING" | "POISONED" | "SLOWED" | "STUNNED" | "HASTED";
export type SpellType =
  | "ICE_TRAP"
  | "RUNIC_BASTION"
  | "HASTE"
  | "HEAL"
  | "FORTRESS_SHIELD"
  | "CATAPULT_LOCK"
  | "SLOWING_FIELD"
  | "VAMPIRIC_BANNER"
  | "STONE_SKIN"
  | "SIEGE_FRENZY"
  | "BERSERKER";
export type SpellCategory = "OFFENSIVE" | "DEFENSIVE" | "BATTLEFIELD_CONTROL" | "SUPPORT" | "SIEGE";
export type SpellTargetCategory =
  | "ALLIED_FORCE"
  | "ALLIED_UNIT"
  | "ENEMY_FORCE"
  | "ENEMY_SIEGE_ENGINE"
  | "OWN_FORTRESS"
  | "OWN_GATE";
export type AiProfileId = "CADET" | "VETERAN" | "ELITE";

export interface BossAttack {
  target: BossAttackTarget;
  damage: number;
  minimumDamage: number;
  rangeWorldUnits: number;
  cooldownSeconds: number;
  initialCooldownSeconds: number;
  windupSeconds: number;
}

export interface BossAbility {
  kind: "ENRAGE";
  healthFractionThreshold: number;
  speedMultiplier: number;
  attackCooldownMultiplier: number;
}

export interface BossProfile {
  id: string;
  presentationKey: string;
  archetype: BossArchetype;
  maxHealth: number;
  collision: { width: number; height: number };
  movement: { kind: BossMovementKind; speedWorldUnitsPerSecond: number };
  attacks: BossAttack[];
  specialAbilities: BossAbility[];
  immuneAmmunitionTypes: AmmunitionType[];
}

export interface UnitConfiguration {
  id: UnitId;
  supplyCost: number;
  maxHealth: number;
  speed: number;
  attackDamage: number;
  attackCooldownSeconds: number;
  attackRange: number;
  fortressDamage: number;
  incomingDamageMultiplier: number;
  contactMovementSpeedMultiplier: number;
}

export interface ApexFragmentationProfile {
  fragmentsPerSide: number;
  minimumReferenceSpeed: number;
  horizontalSpreadPerStep: number;
  initialDownwardSpeed: number;
  downwardSpreadPerStep: number;
}

export type AmmunitionImpactBehavior =
  | { kind: "DIRECT_IMPACT" }
  | { kind: "EXPLOSION"; radius: number; lifetimeSeconds: number }
  | {
    kind: "LINGERING_AREA";
    radius: number;
    durationSeconds: number;
    tickIntervalSeconds: number;
    damagePerTick: number;
    fortressDamagePerTick: number;
    driftsWithWind: boolean;
    windDriftMultiplier: number;
    followsTerrain: boolean;
    status?: UnitStatus | null;
    unitFriendlyFire: FriendlyFirePolicy;
  };

export interface AmmunitionDefinition {
  type: AmmunitionType;
  effect: AmmunitionEffect;
  reload: { reloadSeconds: number };
  inventory: { unlimited: boolean; startingCount: number };
  shopCostGold: number | null;
  flight: {
    mass: number;
    windResponse: number;
    gravityResponse: number;
    drag: number;
    initialSpeedMultiplier: number;
    maximumLifetimeSeconds: number;
    apexFragmentation: ApexFragmentationProfile | null;
  };
  collision: {
    hitLayers: AmmunitionHitLayer[];
    mode: AmmunitionCollisionMode;
    hitRadius: number;
    friendlyFire: FriendlyFirePolicy;
  };
  damage: {
    unitDamage: number;
    fortressDamage: number;
    unitRadius: number;
    fortressRadius: number;
    falloff: AmmunitionFalloff;
    minimumDamage: number;
  };
  terrain: { deformsTerrain: boolean; craterRadius: number; craterDepth: number };
  behavior: AmmunitionImpactBehavior;
  availableLayers: AmmunitionHitLayer[];
}

export type SpellEffectDefinition =
  | { kind: "HASTE"; durationMilliseconds: number; movementSpeedMultiplier: number }
  | { kind: "FORTRESS_SHIELD"; hitPoints: number }
  | { kind: "RUNIC_BASTION"; hitPoints: number };

export interface SpellDefinition {
  type: SpellType;
  goldCost: number;
  category: SpellCategory;
  targetCategory: SpellTargetCategory;
  special: boolean;
  manaCost: number;
  effect: SpellEffectDefinition | null;
  recastableWhileActive: boolean;
}

export interface AiProfileBalance {
  profileId: AiProfileId;
  angleErrorDegrees: number;
  powerError: number;
  reactionIntervalMilliseconds: number;
  unitRecruitmentIntervalMilliseconds: number;
  progressionErrorReduction: number;
  progressionReactionReduction: number;
}

export interface RuntimeBalance {
  economy: {
    startingGold: number;
    startingSupply: number;
    maximumSupply: number;
    supplyRegenerationIntervalSeconds: number;
  };
  fortresses: { standardMaximumHealth: number; demoBlueMaximumHealth: number };
  catapult: { initialAimDirectionDegrees: number; initialAimPower: number };
  match: { durationMilliseconds: number; bossEncounterDurationMilliseconds: number };
  physics: { gravityAccelerationY: number; windAccelerationScale: number };
  wind: {
    initialDelaySeconds: number;
    transitionSeconds: number;
    minimumChangeIntervalSeconds: number;
    maximumChangeIntervalSeconds: number;
    minimumStrength: number;
    maximumStrength: number;
  };
  combat: {
    minimumDamageVarianceFactor: number;
    maximumDamageVarianceFactor: number;
    unitLuckBiasMaximumMagnitude: number;
    maximumInitialAttackDelayRatio: number;
    gameplaySpeedMultiplier: number;
    unitMovementSpeedScale: number;
    garrisonMovementSpeedScale: number;
    unitInteractionRange: number;
    fortressAttackRange: number;
    sapperFortressStandoffDistance: number;
    sapperContactSpeedMultiplier: number;
    meleeAttackStandoffDistance: number;
    attackFormationSpacing: number;
    meleeSynergyDamagePerAdditionalUnit: number;
    maximumMeleeSynergyMultiplier: number;
    demolisher: {
      explosionDamage: number;
      blastRadius: number;
      throwDurationSeconds: number;
      throwForwardDistance: number;
    };
  };
  bossCombat: { largerGroupTargetChance: number; maximumDistanceFalloff: number };
  aiProfiles: AiProfileBalance[];
  aiStrategy: {
    aimAngleOffsetDegrees: number;
    horizontalDistancePerPowerPercent: number;
    specialSpellStockPerMatch: number;
    ammunitionScoring: {
      situationalFortressDamageWeight: number;
      situationalUnitDamageWeight: number;
      situationalFortressRadiusWeight: number;
      situationalUnitRadiusWeight: number;
      counterplayUnitDamageWeight: number;
      counterplayUnitRadiusWeight: number;
      counterplayFortressDamageWeight: number;
      counterplayFortressRadiusWeight: number;
    };
    unitScoring: {
      sapperPriorityScore: number;
      demolisherPriorityScore: number;
      attackDamageWeight: number;
      speedWeight: number;
      defenderVsSapperRoleBonus: number;
      rangedVsRangedRoleBonus: number;
      rangedVsMeleeRoleBonus: number;
      supplyCostPenaltyWeight: number;
    };
    waveDispatch: {
      situationalMinimumReadyUnits: number;
      counterplayMinimumReadyUnits: number;
      counterplayMinimumReadyUnitsAgainstMovingEnemies: number;
      lowFortressHealthFraction: number;
    };
  };
  rewards: {
    winGold: number;
    directEnemyUnitHitGold: number;
    fastWinGold: number;
    fastWinThresholdMilliseconds: number;
    untouchedFortressGold: number;
    fortressHitStreakGoldPerHitBeyondFirst: number;
    fortressHitStreakStartHits: number;
  };
  campaign: {
    initialWindStrength: number;
    initialWindStrengthStep: number;
    initialWindStrengthBands: number;
    demoInitialWindStrength: number;
    lives: {
      initialLives: number;
      maximumLives: number;
      regenerationIntervalMilliseconds: number;
    };
    aiDifficultyBands: Array<{
      firstLevel: number;
      lastLevel: number;
      profileId: AiProfileId;
      progressionStartPercent: number;
      progressionEndPercent: number;
    }>;
  };
}

export interface GameBalanceDocumentV6 {
  schemaVersion: 6;
  balanceRevision: string;
  bosses: BossProfile[];
  units: UnitConfiguration[];
  ammunition: AmmunitionDefinition[];
  spells: SpellDefinition[];
  mana: {
    startingMana: number;
    maximumMana: number;
    regenerationAmount: number;
    regenerationIntervalMilliseconds: number;
  };
  runtime: RuntimeBalance;
}

export interface NumericInputDraft {
  readonly __numberInputDraft: true;
  text: string;
}

export type BalanceFormDraft<T> =
  T extends number ? NumericInputDraft
    : T extends boolean ? boolean | null
      : T extends string ? T | ""
        : T extends readonly (infer Item)[] ? BalanceFormDraft<Item>[]
          : T extends object ? { [Key in keyof T]: BalanceFormDraft<T[Key]> }
            : T;

export interface FormIssue {
  severity: "error";
  path: string;
  message: string;
}

export type MaterializedDraft =
  | { valid: true; document: GameBalanceDocumentV6 }
  | { valid: false; issues: FormIssue[] };

const ROOT_KEYS = [
  "schemaVersion",
  "balanceRevision",
  "bosses",
  "units",
  "ammunition",
  "spells",
  "mana",
  "runtime",
] as const;

const REQUIRED_BOOLEAN_FIELDS = new Set([
  "unlimited",
  "special",
  "recastableWhileActive",
  "deformsTerrain",
  "driftsWithWind",
  "followsTerrain",
]);

const NULLABLE_VALUE_FIELDS = new Set([
  "effect",
  "apexFragmentation",
  "status",
  "shopCostGold",
]);
const OPTIONAL_DRAFT_PATH = /^ammunition\[\d+\]\.behavior\.status$/;

export class BalanceDocumentError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BalanceDocumentError";
  }
}

export function parseBalanceDocument(content: string): GameBalanceDocumentV6 {
  let value: unknown;
  try {
    value = JSON.parse(content) as unknown;
  } catch (cause) {
    if (cause instanceof SyntaxError) {
      throw new BalanceDocumentError("The balance file is not valid JSON.");
    }
    throw cause;
  }

  if (!isRecord(value)) {
    throw new BalanceDocumentError("The balance document must be a JSON object.");
  }
  if (value.schemaVersion !== 6) {
    throw new BalanceDocumentError(
      `Unsupported balance schema version: ${String(value.schemaVersion)}.`,
    );
  }

  const allowedKeys = new Set<string>(ROOT_KEYS);
  const unknownKey = Object.keys(value).find(key => !allowedKeys.has(key));
  if (unknownKey) {
    throw new BalanceDocumentError(`Unsupported balance document field: ${unknownKey}.`);
  }
  const missingKey = ROOT_KEYS.find(key => !(key in value));
  if (missingKey) {
    throw new BalanceDocumentError(`The balance document is missing ${missingKey}.`);
  }
  if (
    typeof value.balanceRevision !== "string" ||
    !Array.isArray(value.bosses) ||
    !Array.isArray(value.units) ||
    !Array.isArray(value.ammunition) ||
    !Array.isArray(value.spells) ||
    !isRecord(value.mana) ||
    !isRecord(value.runtime)
  ) {
    throw new BalanceDocumentError("The balance document does not match schema 6.");
  }

  return value as unknown as GameBalanceDocumentV6;
}

export function toBalanceFormDraft<T>(value: T): BalanceFormDraft<T> {
  if (typeof value === "number") {
    return { __numberInputDraft: true, text: String(value) } as BalanceFormDraft<T>;
  }
  if (Array.isArray(value)) {
    return value.map(item => toBalanceFormDraft(item)) as BalanceFormDraft<T>;
  }
  if (isRecord(value)) {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, toBalanceFormDraft(item)]),
    ) as BalanceFormDraft<T>;
  }
  return value as BalanceFormDraft<T>;
}

export function materializeBalanceDraft(
  draft: BalanceFormDraft<GameBalanceDocumentV6>,
): MaterializedDraft {
  const issues: FormIssue[] = [];
  const document = materializeValue(draft, "", issues);
  if (issues.length > 0 || !isRecord(document)) {
    return { valid: false, issues };
  }
  return { valid: true, document: document as unknown as GameBalanceDocumentV6 };
}

export function serializeBalanceDocument(document: GameBalanceDocumentV6): string {
  return `${JSON.stringify(document, null, 2)}\n`;
}

export function setBalanceDraftValue(
  draft: BalanceFormDraft<GameBalanceDocumentV6>,
  path: string,
  value: unknown,
): BalanceFormDraft<GameBalanceDocumentV6> {
  const segments = parsePath(path);
  if (segments.length === 0) throw new RangeError("Balance draft path must not be empty.");

  const next = structuredClone(draft);
  let current: unknown = next;
  segments.forEach((segment, index) => {
    const isLast = index === segments.length - 1;
    if (Array.isArray(current)) {
      const arrayIndex = Number(segment);
      if (!Number.isInteger(arrayIndex) || arrayIndex < 0 || arrayIndex >= current.length) {
        throw new RangeError(`Balance draft array index is out of range: ${segment}.`);
      }
      if (isLast) {
        current[arrayIndex] = value;
      } else {
        current = current[arrayIndex];
      }
      return;
    }

    if (
      !isRecord(current) ||
      (!Object.hasOwn(current, segment) && !(isLast && OPTIONAL_DRAFT_PATH.test(path)))
    ) {
      throw new RangeError(`Unknown balance draft path: ${path}.`);
    }
    if (isLast) {
      current[segment] = value;
    } else {
      current = current[segment];
    }
  });
  return next;
}

export function createAmmunitionBehaviorDraft(
  kind: AmmunitionImpactBehavior["kind"],
): BalanceFormDraft<AmmunitionImpactBehavior> {
  switch (kind) {
    case "DIRECT_IMPACT":
      return { kind } as BalanceFormDraft<AmmunitionImpactBehavior>;
    case "EXPLOSION":
      return {
        kind,
        radius: numberInput(""),
        lifetimeSeconds: numberInput(""),
      } as BalanceFormDraft<AmmunitionImpactBehavior>;
    case "LINGERING_AREA":
      return {
        kind,
        radius: numberInput(""),
        durationSeconds: numberInput(""),
        tickIntervalSeconds: numberInput(""),
        damagePerTick: numberInput(""),
        fortressDamagePerTick: numberInput(""),
        driftsWithWind: null,
        windDriftMultiplier: numberInput(""),
        followsTerrain: null,
        status: null,
        unitFriendlyFire: "",
      } as BalanceFormDraft<AmmunitionImpactBehavior>;
  }
}

export function createApexFragmentationDraft(): BalanceFormDraft<ApexFragmentationProfile> {
  return {
    fragmentsPerSide: numberInput(""),
    minimumReferenceSpeed: numberInput(""),
    horizontalSpreadPerStep: numberInput(""),
    initialDownwardSpeed: numberInput(""),
    downwardSpreadPerStep: numberInput(""),
  };
}

export function withIncrementedBalanceRevision(
  current: GameBalanceDocumentV6,
  next: GameBalanceDocumentV6,
): GameBalanceDocumentV6 {
  if (current.balanceRevision !== next.balanceRevision) return next;
  const match = /^(.*?)([-.])(\d+)$/.exec(next.balanceRevision);
  const balanceRevision = match
    ? `${match[1]}${match[2]}${Number(match[3]) + 1}`
    : `${next.balanceRevision}-2`;
  return { ...next, balanceRevision };
}

export function balanceDocumentMatches(
  document: GameBalanceDocumentV6,
  baseline: GameBalanceDocumentV6,
): boolean {
  return JSON.stringify(document) === JSON.stringify(baseline);
}

function numberInput(text: string): NumericInputDraft {
  return { __numberInputDraft: true, text };
}

function materializeValue(
  value: unknown,
  path: string,
  issues: FormIssue[],
): unknown {
  if (isNumericInputDraft(value)) {
    const raw = value.text.trim();
    if (raw === "" || !JSON_NUMBER_PATTERN.test(raw)) {
      issues.push({
        severity: "error",
        path,
        message: "Enter a complete finite number.",
      });
      return null;
    }
    const number = Number(raw);
    if (!Number.isFinite(number)) {
      issues.push({ severity: "error", path, message: "Number must be finite." });
      return null;
    }
    return number;
  }
  if (Array.isArray(value)) {
    return value.map((item, index) => materializeValue(item, `${path}[${index}]`, issues));
  }
  if (isRecord(value)) {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [
        key,
        materializeValue(item, path ? `${path}.${key}` : key, issues),
      ]),
    );
  }
  if (value === "") {
    issues.push({ severity: "error", path, message: "Choose a value." });
    return null;
  }
  if (value === null && path && REQUIRED_BOOLEAN_FIELDS.has(path.slice(path.lastIndexOf(".") + 1))) {
    issues.push({ severity: "error", path, message: "Choose true or false." });
    return null;
  }
  if (value === null && path && !NULLABLE_VALUE_FIELDS.has(path.slice(path.lastIndexOf(".") + 1))) {
    issues.push({ severity: "error", path, message: "A value is required." });
  }
  return value;
}

function parsePath(path: string): string[] {
  const segments = path.match(/[^.[\]]+/g) ?? [];
  if (segments.join(".") === "") return [];
  return segments;
}

function isNumericInputDraft(value: unknown): value is NumericInputDraft {
  return isRecord(value) &&
    value.__numberInputDraft === true &&
    typeof value.text === "string";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const JSON_NUMBER_PATTERN = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/;
