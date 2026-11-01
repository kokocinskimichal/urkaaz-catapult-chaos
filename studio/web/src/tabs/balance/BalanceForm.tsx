import { useState } from "react";
import type * as React from "react";
import type { BalanceValidationIssue } from "@studio/contracts/balance";
import {
  BooleanField,
  FieldGroup,
  MultiSelectField,
  NumberField,
  SelectField,
  TextField,
} from "./BalanceFields";
import {
  createApexFragmentationDraft,
  createAmmunitionBehaviorDraft,
  setBalanceDraftValue,
} from "./model";
import type {
  AiProfileId,
  AmmunitionDefinition,
  AmmunitionEffect,
  AmmunitionFalloff,
  AmmunitionHitLayer,
  AmmunitionImpactBehavior,
  BalanceFormDraft,
  BossAttackTarget,
  BossArchetype,
  BossMovementKind,
  FriendlyFirePolicy,
  GameBalanceDocumentV3,
  SpellCategory,
  SpellDefinition,
  SpellEffectDefinition,
  SpellTargetCategory,
  SpellType,
  UnitConfiguration,
  UnitId,
  UnitStatus,
} from "./model";

type BalanceSection = "bosses" | "units" | "ammunition" | "spells" | "runtime";
type RuntimeSection =
  | "economy"
  | "fortresses"
  | "catapult"
  | "match"
  | "physics"
  | "wind"
  | "combat"
  | "bossCombat"
  | "aiProfiles"
  | "aiStrategy"
  | "rewards"
  | "campaign";

interface BalanceFormProps {
  draft: BalanceFormDraft<GameBalanceDocumentV3>;
  validation: { valid: boolean; issues: BalanceValidationIssue[] } | null;
  onChange: (draft: BalanceFormDraft<GameBalanceDocumentV3>) => void;
}

const SECTIONS: Array<{ id: BalanceSection; label: string }> = [
  { id: "bosses", label: "Bosses" },
  { id: "units", label: "Units" },
  { id: "ammunition", label: "Ammunition" },
  { id: "spells", label: "Spells & Mana" },
  { id: "runtime", label: "Runtime" },
];

const RUNTIME_SECTIONS: Array<{ id: RuntimeSection; label: string }> = [
  { id: "economy", label: "Economy" },
  { id: "fortresses", label: "Fortresses" },
  { id: "catapult", label: "Catapult" },
  { id: "match", label: "Match" },
  { id: "physics", label: "Physics" },
  { id: "wind", label: "Wind" },
  { id: "combat", label: "Combat" },
  { id: "bossCombat", label: "Boss combat" },
  { id: "aiProfiles", label: "AI profiles" },
  { id: "aiStrategy", label: "AI strategy" },
  { id: "rewards", label: "Rewards" },
  { id: "campaign", label: "Campaign" },
];

const UNIT_IDS: readonly UnitId[] = [
  "SAPPER",
  "DEFENDER",
  "DEMOLISHER",
  "RAGING_BOAR",
  "SLINGMASTER",
];
const AMMUNITION_TYPES: readonly AmmunitionDefinition["type"][] = [
  "ROCK",
  "SIEGE_BOMB",
  "POWDER_BARREL",
  "FIRE_RAIN",
  "PLAGUE_CAULDRON",
];
const AMMUNITION_EFFECTS: readonly AmmunitionEffect[] = [
  "DIRECT",
  "EXPLOSIVE",
  "INCENDIARY",
  "PLAGUE",
];
const HIT_LAYERS: readonly AmmunitionHitLayer[] = [
  "TERRAIN",
  "UNIT",
  "FORTRESS",
  "BOSS",
  "OUT_OF_BOUNDS",
];
const FRIENDLY_FIRE_POLICIES: readonly FriendlyFirePolicy[] = [
  "ENEMY_ONLY",
  "ALL_UNITS",
  "ALL_TARGETS",
];
const UNIT_STATUSES: readonly UnitStatus[] = [
  "BURNING",
  "POISONED",
  "SLOWED",
  "STUNNED",
  "HASTED",
];
const SPELL_TYPES: readonly SpellType[] = [
  "ICE_TRAP",
  "RUNIC_BASTION",
  "HASTE",
  "HEAL",
  "FORTRESS_SHIELD",
  "CATAPULT_LOCK",
  "SLOWING_FIELD",
  "VAMPIRIC_BANNER",
  "STONE_SKIN",
  "SIEGE_FRENZY",
  "BERSERKER",
];
const SPELL_CATEGORIES: readonly SpellCategory[] = [
  "OFFENSIVE",
  "DEFENSIVE",
  "BATTLEFIELD_CONTROL",
  "SUPPORT",
  "SIEGE",
];
const SPELL_TARGETS: readonly SpellTargetCategory[] = [
  "ALLIED_FORCE",
  "ALLIED_UNIT",
  "ENEMY_FORCE",
  "ENEMY_SIEGE_ENGINE",
  "OWN_FORTRESS",
  "OWN_GATE",
];
const AI_PROFILE_IDS: readonly AiProfileId[] = ["CADET", "VETERAN", "ELITE"];
const RUNTIME_SUBSECTION_BY_PATH: Record<string, RuntimeSection> = {
  economy: "economy",
  fortresses: "fortresses",
  catapult: "catapult",
  match: "match",
  physics: "physics",
  wind: "wind",
  combat: "combat",
  bossCombat: "bossCombat",
  aiProfiles: "aiProfiles",
  aiStrategy: "aiStrategy",
  rewards: "rewards",
  campaign: "campaign",
};

export function BalanceForm({ draft, validation, onChange }: BalanceFormProps) {
  const [section, setSection] = useState<BalanceSection>("bosses");
  const [runtimeSection, setRuntimeSection] = useState<RuntimeSection>("economy");
  const [bossId, setBossId] = useState(draft.bosses[0]?.id ?? "");
  const [unitId, setUnitId] = useState<UnitId>(
    UNIT_IDS.find(id => draft.units.some(profile => profile.id === id)) ?? "SAPPER",
  );
  const [ammunitionType, setAmmunitionType] = useState(draft.ammunition[0]?.type ?? "ROCK");
  const [spellType, setSpellType] = useState(draft.spells[0]?.type ?? "ICE_TRAP");
  const [aiProfileId, setAiProfileId] = useState<AiProfileId>(
    AI_PROFILE_IDS.find(id => draft.runtime.aiProfiles.some(profile => profile.profileId === id)) ??
      "CADET",
  );

  const bossIndex = Math.max(0, draft.bosses.findIndex(profile => profile.id === bossId));
  const unitIndex = Math.max(0, draft.units.findIndex(profile => profile.id === unitId));
  const ammunitionIndex = Math.max(
    0,
    draft.ammunition.findIndex(profile => profile.type === ammunitionType),
  );
  const spellIndex = Math.max(0, draft.spells.findIndex(profile => profile.type === spellType));
  const aiProfileIndex = Math.max(
    0,
    draft.runtime.aiProfiles.findIndex(profile => profile.profileId === aiProfileId),
  );

  const errorFor = (path: string) =>
    validation?.issues.find(issue => issue.path === path)?.message;
  const update = (path: string, value: unknown) =>
    onChange(setBalanceDraftValue(draft, path, value));

  const numberField = (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options: { unit?: string; min?: number; max?: number; step?: number; hint?: string } = {},
  ) => (
    <NumberField
      path={path}
      label={label}
      value={value}
      error={errorFor(path)}
      onChange={next => update(path, next)}
      {...options}
    />
  );

  const selectField = (
    path: string,
    label: string,
    value: string,
    options: readonly string[],
    onValueChange?: (next: string) => void,
  ) => (
    <SelectField
      path={path}
      label={label}
      value={value}
      options={options}
      error={errorFor(path)}
      onChange={next => onValueChange ? onValueChange(next) : update(path, next)}
    />
  );

  const booleanField = (
    path: string,
    label: string,
    value: boolean | null,
    hint?: string,
  ) => (
    <BooleanField
      path={path}
      label={label}
      value={value}
      hint={hint}
      error={errorFor(path)}
      onChange={next => update(path, next)}
    />
  );

  const textField = (
    path: string,
    label: string,
    value: string,
    options: { readOnly?: boolean; hint?: string } = {},
  ) => (
    <TextField
      path={path}
      label={label}
      value={value}
      hint={options.hint}
      readOnly={options.readOnly}
      error={errorFor(path)}
      onChange={next => update(path, next)}
    />
  );

  const multiSelectField = (
    path: string,
    label: string,
    value: readonly string[],
    options: readonly string[],
    hint?: string,
  ) => (
    <MultiSelectField
      path={path}
      label={label}
      value={value}
      options={options}
      hint={hint}
      error={errorFor(path)}
      onChange={next => update(path, next)}
    />
  );

  const focusValidationIssue = (path: string) => {
    const root = /^([a-zA-Z]+)\[(\d+)\]/.exec(path);
    if (root) {
      const index = Number(root[2]);
      switch (root[1]) {
        case "bosses":
          setSection("bosses");
          setBossId(draft.bosses[index]?.id ?? bossId);
          return;
        case "units":
          setSection("units");
          const unitProfileId = draft.units[index]?.id;
          const stableUnitId = UNIT_IDS.find(id => id === unitProfileId);
          if (stableUnitId) setUnitId(stableUnitId);
          return;
        case "ammunition":
          setSection("ammunition");
          setAmmunitionType(draft.ammunition[index]?.type ?? ammunitionType);
          return;
        case "spells":
          setSection("spells");
          setSpellType(draft.spells[index]?.type ?? spellType);
          return;
      }
    }
    if (path.startsWith("mana.")) {
      setSection("spells");
      return;
    }
    if (path.startsWith("runtime.")) {
      setSection("runtime");
      const runtimeKey = path.split(".")[1]?.replace(/\[\d+\].*$/, "");
      const runtimeSubsection = runtimeKey ? RUNTIME_SUBSECTION_BY_PATH[runtimeKey] : undefined;
      if (runtimeSubsection) {
        setRuntimeSection(runtimeSubsection);
        if (runtimeSubsection === "aiProfiles") {
          const profileIndex = /^runtime\.aiProfiles\[(\d+)\]/.exec(path);
          const profileId = profileIndex
            ? draft.runtime.aiProfiles[Number(profileIndex[1])]?.profileId
            : undefined;
          const stableProfileId = AI_PROFILE_IDS.find(id => id === profileId);
          if (stableProfileId) setAiProfileId(stableProfileId);
        }
      }
    }
    const id = `balance-${path.replace(/[^a-zA-Z0-9_-]/g, "-")}`;
    window.requestAnimationFrame(() => {
      document.getElementById(id)?.focus();
    });
  };

  const boss = draft.bosses[bossIndex];
  const unit = draft.units[unitIndex];
  const ammunition = draft.ammunition[ammunitionIndex];
  const spell = draft.spells[spellIndex];
  const aiProfile = draft.runtime.aiProfiles[aiProfileIndex];

  return (
    <div className="balance-form">
      <div className="balance-section-tabs" role="tablist" aria-label="Balance sections">
        {SECTIONS.map(item => (
          <button
            type="button"
            role="tab"
            aria-selected={section === item.id}
            className={section === item.id ? "selected" : ""}
            key={item.id}
            onClick={() => setSection(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {validation && !validation.valid && (
        <div className="balance-validation-summary" role="region" aria-label="Balance validation errors">
          <h3>Validation errors</h3>
          <ul>
            {validation.issues.map((issue, index) => (
              <li key={`${issue.path}-${index}`}>
                <button type="button" onClick={() => focusValidationIssue(issue.path)}>
                  <code>{issue.path || "gameBalance"}</code>: {issue.message}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="balance-form-content" role="tabpanel">
        {section === "bosses" && boss && (
          <section aria-label="Boss balance">
            <ProfileSelector
              label="Boss profile"
              value={boss.id}
              options={draft.bosses.map(profile => profile.id)}
              onChange={setBossId}
            />
            <FieldGroup title="Identity and archetype">
              {textField(`bosses[${bossIndex}].id`, "Stable profile ID", boss.id, { readOnly: true })}
              {textField(
                `bosses[${bossIndex}].presentationKey`,
                "Presentation key",
                boss.presentationKey,
                { hint: "Must match the registered boss presentation key." },
              )}
              {numberField(`bosses[${bossIndex}].maxHealth`, "Maximum health", boss.maxHealth, {
                unit: "HP",
                min: 1,
                step: 1,
              })}
              {selectField(
                `bosses[${bossIndex}].archetype`,
                "Archetype",
                boss.archetype,
                ["CENTRAL_OBJECTIVE", "MOBILE_SIEGE_BOSS"] satisfies readonly BossArchetype[],
              )}
            </FieldGroup>
            <FieldGroup title="Collision">
              {numberField(`bosses[${bossIndex}].collision.width`, "Width", boss.collision.width, {
                unit: "world units",
                min: 0.01,
                step: 1,
              })}
              {numberField(`bosses[${bossIndex}].collision.height`, "Height", boss.collision.height, {
                unit: "world units",
                min: 0.01,
                step: 1,
              })}
            </FieldGroup>
            <FieldGroup title="Movement">
              {selectField(
                `bosses[${bossIndex}].movement.kind`,
                "Movement kind",
                boss.movement.kind,
                ["STATIC", "ADVANCE"] satisfies readonly BossMovementKind[],
              )}
              {numberField(
                `bosses[${bossIndex}].movement.speedWorldUnitsPerSecond`,
                "Movement speed",
                boss.movement.speedWorldUnitsPerSecond,
                { unit: "world units / s", min: 0, step: 0.1 },
              )}
            </FieldGroup>
            <FieldGroup title="Attacks" description="Existing attack entries and order are fixed.">
              {boss.attacks.map((attack, attackIndex) => {
                const path = `bosses[${bossIndex}].attacks[${attackIndex}]`;
                return (
                  <div className="balance-nested-record" key={`${attack.target}-${attackIndex}`}>
                    <h4>Attack {attackIndex + 1}</h4>
                    {selectField(
                      `${path}.target`,
                      "Target",
                      attack.target,
                      ["UNIT", "FORTRESS_GATE"] satisfies readonly BossAttackTarget[],
                    )}
                    {numberField(`${path}.damage`, "Damage", attack.damage, { min: 1, step: 1 })}
                    {numberField(`${path}.minimumDamage`, "Minimum damage", attack.minimumDamage, {
                      min: 1,
                      step: 1,
                    })}
                    {numberField(`${path}.rangeWorldUnits`, "Range", attack.rangeWorldUnits, {
                      unit: "world units",
                      min: 0,
                      step: 1,
                    })}
                    {numberField(`${path}.cooldownSeconds`, "Cooldown", attack.cooldownSeconds, {
                      unit: "s",
                      min: 0.01,
                      step: 0.01,
                    })}
                    {numberField(
                      `${path}.initialCooldownSeconds`,
                      "Initial cooldown",
                      attack.initialCooldownSeconds,
                      { unit: "s", min: 0, step: 0.01 },
                    )}
                    {numberField(`${path}.windupSeconds`, "Windup", attack.windupSeconds, {
                      unit: "s",
                      min: 0,
                      step: 0.001,
                    })}
                  </div>
                );
              })}
            </FieldGroup>
            <FieldGroup title="Special abilities" description="Existing ability entries and order are fixed.">
              {boss.specialAbilities.length === 0
                ? <p className="balance-empty-note">No special abilities are configured for this profile.</p>
                : boss.specialAbilities.map((ability, abilityIndex) => {
                  const path = `bosses[${bossIndex}].specialAbilities[${abilityIndex}]`;
                  return (
                    <div className="balance-nested-record" key={`${ability.kind}-${abilityIndex}`}>
                      <h4>{ability.kind}</h4>
                      {numberField(
                        `${path}.healthFractionThreshold`,
                        "Health fraction threshold",
                        ability.healthFractionThreshold,
                        { min: 0, max: 1, step: 0.01 },
                      )}
                      {numberField(`${path}.speedMultiplier`, "Speed multiplier", ability.speedMultiplier, {
                        min: 1,
                        step: 0.01,
                      })}
                      {numberField(
                        `${path}.attackCooldownMultiplier`,
                        "Attack cooldown multiplier",
                        ability.attackCooldownMultiplier,
                        { min: 0, max: 1, step: 0.01 },
                      )}
                    </div>
                  );
                })}
            </FieldGroup>
            <FieldGroup title="Ammunition immunities">
              {multiSelectField(
                `bosses[${bossIndex}].immuneAmmunitionTypes`,
                "Immune ammunition types",
                boss.immuneAmmunitionTypes,
                AMMUNITION_TYPES,
                "No selection means the boss has no ammunition immunity.",
              )}
            </FieldGroup>
          </section>
        )}

        {section === "units" && unit && (
          <section aria-label="Unit balance">
            <ProfileSelector
              label="Unit"
              value={unit.id}
              options={draft.units.map(profile => profile.id)}
              onChange={value => {
                if (UNIT_IDS.includes(value as UnitId)) setUnitId(value as UnitId);
              }}
            />
            <FieldGroup title="Identity">
              {textField(`units[${unitIndex}].id`, "Stable unit ID", unit.id, { readOnly: true })}
            </FieldGroup>
            <UnitFields
              unit={unit}
              path={`units[${unitIndex}]`}
              numberField={numberField}
            />
          </section>
        )}

        {section === "ammunition" && ammunition && (
          <section aria-label="Ammunition balance">
            <ProfileSelector
              label="Ammunition"
              value={ammunition.type}
              options={draft.ammunition.map(profile => profile.type)}
              onChange={value => {
                if (AMMUNITION_TYPES.includes(value as AmmunitionDefinition["type"])) {
                  setAmmunitionType(value as AmmunitionDefinition["type"]);
                }
              }}
            />
            <FieldGroup title="Identity and effect">
              {textField(
                `ammunition[${ammunitionIndex}].type`,
                "Stable ammunition ID",
                ammunition.type,
                { readOnly: true },
              )}
              {selectField(
                `ammunition[${ammunitionIndex}].effect`,
                "Effect category",
                ammunition.effect,
                AMMUNITION_EFFECTS,
              )}
            </FieldGroup>
            <FieldGroup title="Reload">
              {numberField(
                `ammunition[${ammunitionIndex}].reload.reloadSeconds`,
                "Reload time",
                ammunition.reload.reloadSeconds,
                { unit: "s", min: 0, step: 0.01 },
              )}
            </FieldGroup>
            <FieldGroup title="Inventory">
              {booleanField(
                `ammunition[${ammunitionIndex}].inventory.unlimited`,
                "Unlimited inventory",
                ammunition.inventory.unlimited,
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].inventory.startingCount`,
                "Starting count",
                ammunition.inventory.startingCount,
                { min: 0, step: 1 },
              )}
            </FieldGroup>
            <FieldGroup title="Flight">
              {numberField(`ammunition[${ammunitionIndex}].flight.mass`, "Mass", ammunition.flight.mass, {
                min: 0.01,
                step: 0.01,
              })}
              {numberField(
                `ammunition[${ammunitionIndex}].flight.windResponse`,
                "Wind response",
                ammunition.flight.windResponse,
                { min: 0, step: 0.01 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].flight.gravityResponse`,
                "Gravity response",
                ammunition.flight.gravityResponse,
                { min: 0, step: 0.01 },
              )}
              {numberField(`ammunition[${ammunitionIndex}].flight.drag`, "Drag", ammunition.flight.drag, {
                min: 0,
                step: 0.01,
              })}
              {numberField(
                `ammunition[${ammunitionIndex}].flight.initialSpeedMultiplier`,
                "Initial speed multiplier",
                ammunition.flight.initialSpeedMultiplier,
                { min: 0.01, step: 0.01 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].flight.maximumLifetimeSeconds`,
                "Maximum lifetime",
                ammunition.flight.maximumLifetimeSeconds,
                { unit: "s", min: 0.01, step: 0.1 },
              )}
              <SelectField
                path={`ammunition[${ammunitionIndex}].flight.apexFragmentation`}
                label="Apex fragmentation"
                value={ammunition.flight.apexFragmentation === null ? "DISABLED" : "ENABLED"}
                options={["DISABLED", "ENABLED"]}
                onChange={value => update(
                  `ammunition[${ammunitionIndex}].flight.apexFragmentation`,
                  value === "ENABLED" ? createApexFragmentationDraft() : null,
                )}
              />
              {ammunition.flight.apexFragmentation && (
                <FragmentationFields
                  index={ammunitionIndex}
                  values={ammunition.flight.apexFragmentation}
                  numberField={numberField}
                />
              )}
            </FieldGroup>
            <FieldGroup title="Collision">
              {multiSelectField(
                `ammunition[${ammunitionIndex}].collision.hitLayers`,
                "Collision hit layers",
                ammunition.collision.hitLayers,
                HIT_LAYERS,
              )}
              {selectField(
                `ammunition[${ammunitionIndex}].collision.mode`,
                "Collision mode",
                ammunition.collision.mode,
                ["STOP_ON_FIRST_TARGET", "PIERCE_UNITS", "IGNORE_UNITS", "IMPACT_ON_TERRAIN"],
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].collision.hitRadius`,
                "Hit radius",
                ammunition.collision.hitRadius,
                { unit: "world units", min: 0, step: 0.1 },
              )}
              {selectField(
                `ammunition[${ammunitionIndex}].collision.friendlyFire`,
                "Friendly fire",
                ammunition.collision.friendlyFire,
                FRIENDLY_FIRE_POLICIES,
              )}
            </FieldGroup>
            <FieldGroup title="Damage">
              {numberField(
                `ammunition[${ammunitionIndex}].damage.unitDamage`,
                "Unit damage",
                ammunition.damage.unitDamage,
                { min: 0, step: 1 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].damage.fortressDamage`,
                "Fortress damage",
                ammunition.damage.fortressDamage,
                { min: 0, step: 1 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].damage.unitRadius`,
                "Unit damage radius",
                ammunition.damage.unitRadius,
                { unit: "world units", min: 0, step: 0.1 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].damage.fortressRadius`,
                "Fortress damage radius",
                ammunition.damage.fortressRadius,
                { unit: "world units", min: 0, step: 0.1 },
              )}
              {selectField(
                `ammunition[${ammunitionIndex}].damage.falloff`,
                "Damage falloff",
                ammunition.damage.falloff,
                ["NONE", "LINEAR"] satisfies readonly AmmunitionFalloff[],
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].damage.minimumDamage`,
                "Minimum damage",
                ammunition.damage.minimumDamage,
                { min: 0, step: 1 },
              )}
            </FieldGroup>
            <FieldGroup title="Terrain">
              {booleanField(
                `ammunition[${ammunitionIndex}].terrain.deformsTerrain`,
                "Deforms terrain",
                ammunition.terrain.deformsTerrain,
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].terrain.craterRadius`,
                "Crater radius",
                ammunition.terrain.craterRadius,
                { unit: "world units", min: 0, step: 0.1 },
              )}
              {numberField(
                `ammunition[${ammunitionIndex}].terrain.craterDepth`,
                "Crater depth",
                ammunition.terrain.craterDepth,
                { unit: "world units", min: 0, step: 0.1 },
              )}
            </FieldGroup>
            <AmmunitionBehaviorFields
              ammunition={ammunition}
              index={ammunitionIndex}
              update={update}
              numberField={numberField}
              selectField={selectField}
              booleanField={booleanField}
            />
            <FieldGroup title="Available layers">
              {multiSelectField(
                `ammunition[${ammunitionIndex}].availableLayers`,
                "Available on layers",
                ammunition.availableLayers,
                HIT_LAYERS,
              )}
            </FieldGroup>
          </section>
        )}

        {section === "spells" && spell && (
          <section aria-label="Spell and mana balance">
            <ProfileSelector
              label="Spell"
              value={spell.type}
              options={draft.spells.map(profile => profile.type)}
              onChange={value => {
                if (SPELL_TYPES.includes(value as SpellType)) setSpellType(value as SpellType);
              }}
            />
            <SpellFields
              spell={spell}
              index={spellIndex}
              numberField={numberField}
              selectField={selectField}
              booleanField={booleanField}
            />
            <ManaFields draft={draft} numberField={numberField} />
          </section>
        )}

        {section === "runtime" && (
          <section aria-label="Runtime balance">
            <div className="balance-runtime-tabs" role="tablist" aria-label="Runtime sections">
              {RUNTIME_SECTIONS.map(item => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={runtimeSection === item.id}
                  className={runtimeSection === item.id ? "selected" : ""}
                  key={item.id}
                  onClick={() => setRuntimeSection(item.id)}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <RuntimeFields
              draft={draft}
              section={runtimeSection}
              profileIndex={aiProfileIndex}
              aiProfile={aiProfile}
              setAiProfileId={setAiProfileId}
              numberField={numberField}
              selectField={selectField}
            />
          </section>
        )}
      </div>
    </div>
  );
}

function ProfileSelector({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: readonly string[];
  onChange: (value: string) => void;
}) {
  return (
    <div className="balance-profile-selector">
      <SelectField
        path={`profile-select-${label.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
        label={label}
        value={value}
        options={options}
        onChange={onChange}
      />
      <span>{options.length} fixed profiles</span>
    </div>
  );
}

function UnitFields({
  unit,
  path,
  numberField,
}: {
  unit: BalanceFormDraft<UnitConfiguration>;
  path: string;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  return (
    <FieldGroup title="Recruitment and combat">
      {numberField(`${path}.supplyCost`, "Supply cost", unit.supplyCost, { min: 0, step: 1 })}
      {numberField(`${path}.maxHealth`, "Maximum health", unit.maxHealth, { unit: "HP", min: 1, step: 1 })}
      {numberField(`${path}.speed`, "Movement speed", unit.speed, {
        unit: "world units / s",
        min: 0,
        step: 0.1,
      })}
      {numberField(`${path}.attackDamage`, "Attack damage", unit.attackDamage, { min: 0, step: 1 })}
      {numberField(`${path}.attackCooldownSeconds`, "Attack cooldown", unit.attackCooldownSeconds, {
        unit: "s",
        min: 0.01,
        step: 0.01,
      })}
      {numberField(`${path}.attackRange`, "Attack range", unit.attackRange, {
        unit: "world units",
        min: 0,
        step: 1,
      })}
      {numberField(`${path}.fortressDamage`, "Fortress damage", unit.fortressDamage, { min: 0, step: 1 })}
      {numberField(
        `${path}.incomingDamageMultiplier`,
        "Incoming damage multiplier",
        unit.incomingDamageMultiplier,
        { min: 0.01, step: 0.01 },
      )}
      {numberField(
        `${path}.contactMovementSpeedMultiplier`,
        "Contact movement speed multiplier",
        unit.contactMovementSpeedMultiplier,
        { min: 0, max: 1, step: 0.01 },
      )}
    </FieldGroup>
  );
}

function FragmentationFields({
  index,
  values,
  numberField,
}: {
  index: number;
  values: NonNullable<BalanceFormDraft<AmmunitionDefinition["flight"]["apexFragmentation"]>>;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  const path = `ammunition[${index}].flight.apexFragmentation`;
  return (
    <div className="balance-nested-record">
      <h4>Fragmentation profile</h4>
      {numberField(`${path}.fragmentsPerSide`, "Fragments per side", values.fragmentsPerSide, {
        min: 1,
        step: 1,
      })}
      {numberField(
        `${path}.minimumReferenceSpeed`,
        "Minimum reference speed",
        values.minimumReferenceSpeed,
        { min: 0.01, step: 0.1 },
      )}
      {numberField(
        `${path}.horizontalSpreadPerStep`,
        "Horizontal spread per step",
        values.horizontalSpreadPerStep,
        { min: 0, step: 0.001 },
      )}
      {numberField(
        `${path}.initialDownwardSpeed`,
        "Initial downward speed",
        values.initialDownwardSpeed,
        { min: 0, step: 0.01 },
      )}
      {numberField(
        `${path}.downwardSpreadPerStep`,
        "Downward spread per step",
        values.downwardSpreadPerStep,
        { min: 0, step: 0.001 },
      )}
    </div>
  );
}

function AmmunitionBehaviorFields({
  ammunition,
  index,
  update,
  numberField,
  selectField,
  booleanField,
}: {
  ammunition: BalanceFormDraft<AmmunitionDefinition>;
  index: number;
  update: (path: string, value: unknown) => void;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
  selectField: (
    path: string,
    label: string,
    value: string,
    options: readonly string[],
    onValueChange?: (next: string) => void,
  ) => React.ReactNode;
  booleanField: (path: string, label: string, value: boolean | null, hint?: string) => React.ReactNode;
}) {
  const path = `ammunition[${index}].behavior`;
  const behavior = ammunition.behavior;
  return (
    <FieldGroup title="Impact behavior">
      {selectField(
        `${path}.kind`,
        "Behavior kind",
        behavior.kind,
        ["DIRECT_IMPACT", "EXPLOSION", "LINGERING_AREA"],
        value => {
          if (
            value === "DIRECT_IMPACT" ||
            value === "EXPLOSION" ||
            value === "LINGERING_AREA"
          ) {
            update(path, createAmmunitionBehaviorDraft(value));
          }
        },
      )}
      {behavior.kind === "EXPLOSION" && (
        <>
          {numberField(`${path}.radius`, "Explosion radius", behavior.radius, {
            unit: "world units",
            min: 0,
            step: 0.1,
          })}
          {numberField(`${path}.lifetimeSeconds`, "Explosion lifetime", behavior.lifetimeSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.01,
          })}
        </>
      )}
      {behavior.kind === "LINGERING_AREA" && (
        <>
          {numberField(`${path}.radius`, "Area radius", behavior.radius, {
            unit: "world units",
            min: 0,
            step: 0.1,
          })}
          {numberField(`${path}.durationSeconds`, "Duration", behavior.durationSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.1,
          })}
          {numberField(`${path}.tickIntervalSeconds`, "Tick interval", behavior.tickIntervalSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.01,
          })}
          {numberField(`${path}.damagePerTick`, "Unit damage per tick", behavior.damagePerTick, {
            min: 0,
            step: 1,
          })}
          {numberField(
            `${path}.fortressDamagePerTick`,
            "Fortress damage per tick",
            behavior.fortressDamagePerTick,
            { min: 0, step: 1 },
          )}
          {booleanField(`${path}.driftsWithWind`, "Drifts with wind", behavior.driftsWithWind)}
          {numberField(
            `${path}.windDriftMultiplier`,
            "Wind drift multiplier",
            behavior.windDriftMultiplier,
            { min: 0, step: 0.01 },
          )}
          {booleanField(`${path}.followsTerrain`, "Follows terrain", behavior.followsTerrain)}
          {selectField(
            `${path}.status`,
            "Unit status",
            behavior.status ?? "",
            UNIT_STATUSES,
            value => update(`${path}.status`, value === "" ? null : value),
          )}
          {selectField(
            `${path}.unitFriendlyFire`,
            "Unit friendly fire",
            behavior.unitFriendlyFire,
            FRIENDLY_FIRE_POLICIES,
          )}
        </>
      )}
    </FieldGroup>
  );
}

function SpellFields({
  spell,
  index,
  numberField,
  selectField,
  booleanField,
}: {
  spell: BalanceFormDraft<SpellDefinition>;
  index: number;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
  selectField: (
    path: string,
    label: string,
    value: string,
    options: readonly string[],
    onValueChange?: (next: string) => void,
  ) => React.ReactNode;
  booleanField: (path: string, label: string, value: boolean | null, hint?: string) => React.ReactNode;
}) {
  const path = `spells[${index}]`;
  return (
    <>
      <FieldGroup title="Spell definition">
        <TextField path={`${path}.type`} label="Stable spell ID" value={spell.type} readOnly />
        {numberField(`${path}.goldCost`, "Gold cost", spell.goldCost, { min: 0, step: 1 })}
        {selectField(
          `${path}.category`,
          "Category",
          spell.category,
          ["OFFENSIVE", "DEFENSIVE", "BATTLEFIELD_CONTROL", "SUPPORT", "SIEGE"] satisfies readonly SpellCategory[],
        )}
        {selectField(
          `${path}.targetCategory`,
          "Target category",
          spell.targetCategory,
          [
            "ALLIED_FORCE",
            "ALLIED_UNIT",
            "ENEMY_FORCE",
            "ENEMY_SIEGE_ENGINE",
            "OWN_FORTRESS",
            "OWN_GATE",
          ] satisfies readonly SpellTargetCategory[],
        )}
        {booleanField(`${path}.special`, "Special spell", spell.special)}
        {numberField(`${path}.manaCost`, "Mana cost", spell.manaCost, { min: 1, step: 1 })}
        {booleanField(`${path}.recastableWhileActive`, "Recastable while active", spell.recastableWhileActive)}
      </FieldGroup>
      <SpellEffectFields effect={spell.effect} path={`${path}.effect`} numberField={numberField} />
    </>
  );
}

function SpellEffectFields({
  effect,
  path,
  numberField,
}: {
  effect: BalanceFormDraft<SpellEffectDefinition> | null;
  path: string;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  if (effect === null) {
    return (
      <FieldGroup title="Runtime effect">
        <p className="balance-empty-note">No runtime effect is implemented for this spell.</p>
      </FieldGroup>
    );
  }
  const effectKind = effect.kind;
  return (
    <FieldGroup title="Runtime effect">
      <TextField path={`${path}.kind`} label="Effect kind" value={effectKind} readOnly />
      {effect.kind === "HASTE" && (
        <>
          {numberField(`${path}.durationMilliseconds`, "Duration", effect.durationMilliseconds, {
            unit: "ms",
            min: 1,
            step: 1,
          })}
          {numberField(
            `${path}.movementSpeedMultiplier`,
            "Movement speed multiplier",
            effect.movementSpeedMultiplier,
            { min: 1.01, step: 0.01 },
          )}
        </>
      )}
      {(effect.kind === "FORTRESS_SHIELD" || effect.kind === "RUNIC_BASTION") &&
        numberField(`${path}.hitPoints`, "Hit points", effect.hitPoints, { unit: "HP", min: 1, step: 1 })}
    </FieldGroup>
  );
}

function ManaFields({
  draft,
  numberField,
}: {
  draft: BalanceFormDraft<GameBalanceDocumentV3>;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  return (
    <FieldGroup title="Mana">
      {numberField("mana.startingMana", "Starting mana", draft.mana.startingMana, {
        min: 0,
        step: 1,
      })}
      {numberField("mana.maximumMana", "Maximum mana", draft.mana.maximumMana, {
        min: 1,
        step: 1,
      })}
      {numberField("mana.regenerationAmount", "Regeneration amount", draft.mana.regenerationAmount, {
        min: 1,
        step: 1,
      })}
      {numberField(
        "mana.regenerationIntervalMilliseconds",
        "Regeneration interval",
        draft.mana.regenerationIntervalMilliseconds,
        { unit: "ms", min: 1, step: 1 },
      )}
    </FieldGroup>
  );
}

function RuntimeFields({
  draft,
  section,
  profileIndex,
  aiProfile,
  setAiProfileId,
  numberField,
  selectField,
}: {
  draft: BalanceFormDraft<GameBalanceDocumentV3>;
  section: RuntimeSection;
  profileIndex: number;
  aiProfile: BalanceFormDraft<GameBalanceDocumentV3["runtime"]["aiProfiles"][number]> | undefined;
  setAiProfileId: (id: AiProfileId) => void;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
  selectField: (
    path: string,
    label: string,
    value: string,
    options: readonly string[],
    onValueChange?: (next: string) => void,
  ) => React.ReactNode;
}) {
  const runtime = draft.runtime;
  switch (section) {
    case "economy":
      return (
        <FieldGroup title="Economy">
          {numberField("runtime.economy.startingGold", "Starting gold", runtime.economy.startingGold, {
            unit: "gold",
            min: 0,
            step: 1,
          })}
          {numberField("runtime.economy.startingSupply", "Starting supply", runtime.economy.startingSupply, {
            min: 0,
            step: 1,
          })}
          {numberField("runtime.economy.maximumSupply", "Maximum supply", runtime.economy.maximumSupply, {
            min: 1,
            step: 1,
          })}
          {numberField(
            "runtime.economy.supplyRegenerationIntervalSeconds",
            "Supply regeneration interval",
            runtime.economy.supplyRegenerationIntervalSeconds,
            { unit: "s", min: 0.01, step: 0.1 },
          )}
        </FieldGroup>
      );
    case "fortresses":
      return (
        <FieldGroup title="Fortresses">
          {numberField(
            "runtime.fortresses.standardMaximumHealth",
            "Standard maximum health",
            runtime.fortresses.standardMaximumHealth,
            { unit: "HP", min: 1, step: 1 },
          )}
          {numberField(
            "runtime.fortresses.demoBlueMaximumHealth",
            "Demo BLUE maximum health",
            runtime.fortresses.demoBlueMaximumHealth,
            { unit: "HP", min: 1, step: 1 },
          )}
        </FieldGroup>
      );
    case "catapult":
      return (
        <FieldGroup title="Catapult">
          {numberField(
            "runtime.catapult.initialAimDirectionDegrees",
            "Initial aim direction",
            runtime.catapult.initialAimDirectionDegrees,
            { unit: "degrees", min: 0, max: 180, step: 0.1 },
          )}
          {numberField(
            "runtime.catapult.initialAimPower",
            "Initial aim power",
            runtime.catapult.initialAimPower,
            { min: 0, max: 100, step: 0.1 },
          )}
        </FieldGroup>
      );
    case "match":
      return (
        <FieldGroup title="Match">
          {numberField("runtime.match.durationMilliseconds", "Match duration", runtime.match.durationMilliseconds, {
            unit: "ms",
            min: 1,
            step: 1,
          })}
          {numberField(
            "runtime.match.bossEncounterDurationMilliseconds",
            "Boss encounter duration",
            runtime.match.bossEncounterDurationMilliseconds,
            { unit: "ms", min: 1, step: 1 },
          )}
        </FieldGroup>
      );
    case "physics":
      return (
        <FieldGroup title="Physics">
          {numberField("runtime.physics.gravityAccelerationY", "Gravity acceleration", runtime.physics.gravityAccelerationY, {
            unit: "world units / s²",
            min: 0,
            step: 0.1,
          })}
          {numberField("runtime.physics.windAccelerationScale", "Wind acceleration scale", runtime.physics.windAccelerationScale, {
            min: 0.01,
            step: 0.1,
          })}
        </FieldGroup>
      );
    case "wind":
      return (
        <FieldGroup title="Wind">
          {numberField("runtime.wind.initialDelaySeconds", "Initial delay", runtime.wind.initialDelaySeconds, {
            unit: "s",
            min: 0,
            step: 0.1,
          })}
          {numberField("runtime.wind.transitionSeconds", "Transition duration", runtime.wind.transitionSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.1,
          })}
          {numberField("runtime.wind.minimumChangeIntervalSeconds", "Minimum change interval", runtime.wind.minimumChangeIntervalSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.1,
          })}
          {numberField("runtime.wind.maximumChangeIntervalSeconds", "Maximum change interval", runtime.wind.maximumChangeIntervalSeconds, {
            unit: "s",
            min: 0.01,
            step: 0.1,
          })}
          {numberField("runtime.wind.minimumStrength", "Minimum strength", runtime.wind.minimumStrength, {
            min: 0,
            step: 0.01,
          })}
          {numberField("runtime.wind.maximumStrength", "Maximum strength", runtime.wind.maximumStrength, {
            min: 0,
            step: 0.01,
          })}
        </FieldGroup>
      );
    case "combat":
      return <CombatFields runtime={runtime} numberField={numberField} />;
    case "bossCombat":
      return (
        <FieldGroup title="Boss combat">
          {numberField(
            "runtime.bossCombat.largerGroupTargetChance",
            "Larger group target chance",
            runtime.bossCombat.largerGroupTargetChance,
            { min: 0, max: 1, step: 0.01 },
          )}
          {numberField(
            "runtime.bossCombat.maximumDistanceFalloff",
            "Maximum distance falloff",
            runtime.bossCombat.maximumDistanceFalloff,
            { min: 0, max: 1, step: 0.01 },
          )}
        </FieldGroup>
      );
    case "aiProfiles":
      return aiProfile
        ? (
          <>
            <ProfileSelector
              label="AI profile"
              value={aiProfile.profileId}
              options={runtime.aiProfiles.map(profile => profile.profileId)}
              onChange={value => {
                if (AI_PROFILE_IDS.includes(value as AiProfileId)) {
                  setAiProfileId(value as AiProfileId);
                }
              }}
            />
            <FieldGroup title="Profile tuning">
              <TextField
                path={`runtime.aiProfiles[${profileIndex}].profileId`}
                label="Stable AI profile ID"
                value={aiProfile.profileId}
                readOnly
              />
              {numberField(`runtime.aiProfiles[${profileIndex}].angleErrorDegrees`, "Angle error", aiProfile.angleErrorDegrees, {
                unit: "degrees",
                min: 0,
                step: 0.01,
              })}
              {numberField(`runtime.aiProfiles[${profileIndex}].powerError`, "Power error", aiProfile.powerError, {
                min: 0,
                step: 0.01,
              })}
              {numberField(
                `runtime.aiProfiles[${profileIndex}].reactionIntervalMilliseconds`,
                "Reaction interval",
                aiProfile.reactionIntervalMilliseconds,
                { unit: "ms", min: 1, step: 1 },
              )}
              {numberField(
                `runtime.aiProfiles[${profileIndex}].unitRecruitmentIntervalMilliseconds`,
                "Unit recruitment interval",
                aiProfile.unitRecruitmentIntervalMilliseconds,
                { unit: "ms", min: 0, step: 1 },
              )}
              {numberField(
                `runtime.aiProfiles[${profileIndex}].progressionErrorReduction`,
                "Progression error reduction",
                aiProfile.progressionErrorReduction,
                { min: 0, max: 1, step: 0.01 },
              )}
              {numberField(
                `runtime.aiProfiles[${profileIndex}].progressionReactionReduction`,
                "Progression reaction reduction",
                aiProfile.progressionReactionReduction,
                { min: 0, max: 1, step: 0.01 },
              )}
            </FieldGroup>
          </>
        )
        : <p className="balance-empty-note">No AI profiles are configured.</p>;
    case "aiStrategy":
      return <AiStrategyFields runtime={runtime} numberField={numberField} />;
    case "rewards":
      return (
        <FieldGroup title="Rewards">
          {numberField("runtime.rewards.winGold", "Win gold", runtime.rewards.winGold, {
            unit: "gold",
            min: 0,
            step: 1,
          })}
          {numberField(
            "runtime.rewards.directEnemyUnitHitGold",
            "Direct enemy unit hit gold",
            runtime.rewards.directEnemyUnitHitGold,
            { unit: "gold", min: 0, step: 1 },
          )}
          {numberField("runtime.rewards.fastWinGold", "Fast win gold", runtime.rewards.fastWinGold, {
            unit: "gold",
            min: 0,
            step: 1,
          })}
          {numberField(
            "runtime.rewards.fastWinThresholdMilliseconds",
            "Fast win threshold",
            runtime.rewards.fastWinThresholdMilliseconds,
            { unit: "ms", min: 1, step: 1 },
          )}
          {numberField(
            "runtime.rewards.untouchedFortressGold",
            "Untouched fortress gold",
            runtime.rewards.untouchedFortressGold,
            { unit: "gold", min: 0, step: 1 },
          )}
          {numberField(
            "runtime.rewards.fortressHitStreakGoldPerHitBeyondFirst",
            "Fortress hit streak gold per hit",
            runtime.rewards.fortressHitStreakGoldPerHitBeyondFirst,
            { unit: "gold", min: 0, step: 1 },
          )}
          {numberField(
            "runtime.rewards.fortressHitStreakStartHits",
            "Fortress hit streak starts at hit",
            runtime.rewards.fortressHitStreakStartHits,
            { min: 2, step: 1 },
          )}
        </FieldGroup>
      );
    case "campaign":
      return (
        <>
          <FieldGroup title="Campaign wind">
            {numberField("runtime.campaign.initialWindStrength", "Initial wind strength", runtime.campaign.initialWindStrength, {
              min: 0,
              step: 0.01,
            })}
            {numberField("runtime.campaign.initialWindStrengthStep", "Wind strength step", runtime.campaign.initialWindStrengthStep, {
              min: 0,
              step: 0.01,
            })}
            {numberField("runtime.campaign.initialWindStrengthBands", "Wind strength bands", runtime.campaign.initialWindStrengthBands, {
              min: 1,
              step: 1,
            })}
            {numberField("runtime.campaign.demoInitialWindStrength", "Demo initial wind strength", runtime.campaign.demoInitialWindStrength, {
              min: 0,
              step: 0.01,
            })}
          </FieldGroup>
          <FieldGroup title="AI difficulty bands" description="Band entries and order are fixed.">
            {runtime.campaign.aiDifficultyBands.map((band, index) => {
              const path = `runtime.campaign.aiDifficultyBands[${index}]`;
              return (
                <div className="balance-nested-record" key={`${band.firstLevel}-${band.lastLevel}`}>
                  <h4>
                    Levels {band.firstLevel.text || "—"}–{band.lastLevel.text || "—"}
                  </h4>
                  {numberField(`${path}.firstLevel`, "First level", band.firstLevel, { min: 1, step: 1 })}
                  {numberField(`${path}.lastLevel`, "Last level", band.lastLevel, { min: 1, step: 1 })}
                  {selectField(`${path}.profileId`, "AI profile", band.profileId, AI_PROFILE_IDS)}
                  {numberField(
                    `${path}.progressionStartPercent`,
                    "Progression start",
                    band.progressionStartPercent,
                    { unit: "%", min: 0, max: 100, step: 1 },
                  )}
                  {numberField(
                    `${path}.progressionEndPercent`,
                    "Progression end",
                    band.progressionEndPercent,
                    { unit: "%", min: 0, max: 100, step: 1 },
                  )}
                </div>
              );
            })}
          </FieldGroup>
        </>
      );
  }
}

function CombatFields({
  runtime,
  numberField,
}: {
  runtime: BalanceFormDraft<GameBalanceDocumentV3["runtime"]>;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  const combat = runtime.combat;
  const path = "runtime.combat";
  return (
    <>
      <FieldGroup title="Damage variation and timing">
        {numberField(`${path}.minimumDamageVarianceFactor`, "Minimum damage variance", combat.minimumDamageVarianceFactor, {
          min: 0.01,
          step: 0.01,
        })}
        {numberField(`${path}.maximumDamageVarianceFactor`, "Maximum damage variance", combat.maximumDamageVarianceFactor, {
          min: 0.01,
          step: 0.01,
        })}
        {numberField(`${path}.unitLuckBiasMaximumMagnitude`, "Unit luck bias magnitude", combat.unitLuckBiasMaximumMagnitude, {
          min: 0,
          step: 0.001,
        })}
        {numberField(`${path}.maximumInitialAttackDelayRatio`, "Initial attack delay ratio", combat.maximumInitialAttackDelayRatio, {
          min: 0,
          max: 1,
          step: 0.01,
        })}
      </FieldGroup>
      <FieldGroup title="Movement and range">
        {numberField(`${path}.unitMovementSpeedMultiplier`, "Unit movement speed multiplier", combat.unitMovementSpeedMultiplier, {
          min: 0.01,
          step: 0.01,
        })}
        {numberField(`${path}.unitMovementSpeedScale`, "Unit movement speed scale", combat.unitMovementSpeedScale, {
          min: 0,
          step: 0.01,
        })}
        {numberField(`${path}.garrisonMovementSpeedScale`, "Garrison movement speed scale", combat.garrisonMovementSpeedScale, {
          min: 0,
          step: 0.01,
        })}
        {numberField(`${path}.unitInteractionRange`, "Unit interaction range", combat.unitInteractionRange, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
        {numberField(`${path}.fortressAttackRange`, "Fortress attack range", combat.fortressAttackRange, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
        {numberField(`${path}.sapperFortressStandoffDistance`, "Sapper fortress standoff distance", combat.sapperFortressStandoffDistance, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
        {numberField(`${path}.sapperContactSpeedMultiplier`, "Sapper contact speed multiplier", combat.sapperContactSpeedMultiplier, {
          min: 0,
          max: 1,
          step: 0.01,
        })}
        {numberField(`${path}.meleeAttackStandoffDistance`, "Melee attack standoff distance", combat.meleeAttackStandoffDistance, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
        {numberField(`${path}.attackFormationSpacing`, "Attack formation spacing", combat.attackFormationSpacing, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
        {numberField(`${path}.meleeSynergyDamagePerAdditionalUnit`, "Melee synergy per additional unit", combat.meleeSynergyDamagePerAdditionalUnit, {
          min: 0,
          step: 0.01,
        })}
        {numberField(`${path}.maximumMeleeSynergyMultiplier`, "Maximum melee synergy multiplier", combat.maximumMeleeSynergyMultiplier, {
          min: 1,
          step: 0.01,
        })}
      </FieldGroup>
      <FieldGroup title="Demolisher">
        {numberField(`${path}.demolisher.explosionDamage`, "Explosion damage", combat.demolisher.explosionDamage, {
          min: 1,
          step: 1,
        })}
        {numberField(`${path}.demolisher.blastRadius`, "Blast radius", combat.demolisher.blastRadius, {
          unit: "world units",
          min: 0.01,
          step: 0.1,
        })}
        {numberField(`${path}.demolisher.throwDurationSeconds`, "Throw duration", combat.demolisher.throwDurationSeconds, {
          unit: "s",
          min: 0.01,
          step: 0.01,
        })}
        {numberField(`${path}.demolisher.throwForwardDistance`, "Throw distance", combat.demolisher.throwForwardDistance, {
          unit: "world units",
          min: 0,
          step: 1,
        })}
      </FieldGroup>
    </>
  );
}

function AiStrategyFields({
  runtime,
  numberField,
}: {
  runtime: BalanceFormDraft<GameBalanceDocumentV3["runtime"]>;
  numberField: (
    path: string,
    label: string,
    value: Parameters<typeof NumberField>[0]["value"],
    options?: { unit?: string; min?: number; max?: number; step?: number; hint?: string },
  ) => React.ReactNode;
}) {
  const strategy = runtime.aiStrategy;
  return (
    <>
      <FieldGroup title="Aiming">
        {numberField(
          "runtime.aiStrategy.aimAngleOffsetDegrees",
          "Aim angle offset",
          strategy.aimAngleOffsetDegrees,
          { unit: "degrees", step: 0.1 },
        )}
        {numberField(
          "runtime.aiStrategy.horizontalDistancePerPowerPercent",
          "Horizontal distance per power percent",
          strategy.horizontalDistancePerPowerPercent,
          { unit: "world units", min: 0.01, step: 0.1 },
        )}
      </FieldGroup>
      <FieldGroup title="Ammunition scoring">
        {Object.entries(strategy.ammunitionScoring).map(([key, value]) => {
          const path = `runtime.aiStrategy.ammunitionScoring.${key}`;
          return numberField(path, humanizeKey(key), value, { min: 0, step: 0.01 });
        })}
      </FieldGroup>
      <FieldGroup title="Unit scoring">
        {Object.entries(strategy.unitScoring).map(([key, value]) => {
          const path = `runtime.aiStrategy.unitScoring.${key}`;
          return numberField(path, humanizeKey(key), value, { min: 0, step: 0.01 });
        })}
      </FieldGroup>
      <FieldGroup title="Wave dispatch">
        {numberField(
          "runtime.aiStrategy.waveDispatch.situationalMinimumReadyUnits",
          "Situational minimum ready units",
          strategy.waveDispatch.situationalMinimumReadyUnits,
          { min: 1, step: 1 },
        )}
        {numberField(
          "runtime.aiStrategy.waveDispatch.counterplayMinimumReadyUnits",
          "Counterplay minimum ready units",
          strategy.waveDispatch.counterplayMinimumReadyUnits,
          { min: 1, step: 1 },
        )}
        {numberField(
          "runtime.aiStrategy.waveDispatch.counterplayMinimumReadyUnitsAgainstMovingEnemies",
          "Minimum ready units against moving enemies",
          strategy.waveDispatch.counterplayMinimumReadyUnitsAgainstMovingEnemies,
          { min: 1, step: 1 },
        )}
        {numberField(
          "runtime.aiStrategy.waveDispatch.lowFortressHealthFraction",
          "Low fortress health fraction",
          strategy.waveDispatch.lowFortressHealthFraction,
          { min: 0, max: 1, step: 0.01 },
        )}
      </FieldGroup>
    </>
  );
}

function humanizeKey(value: string): string {
  return value
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/^./, first => first.toUpperCase());
}
