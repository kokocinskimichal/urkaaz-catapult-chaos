# Ammunition System Design

## 1. Status and scope

This document defines the target architecture for projectile ammunition in
Urkaaaz and is the source of truth for the incremental implementation. The
first implementation slice now includes typed definitions, per-ammunition
reload state, deterministic profiles, unit status persistence and structured
simulation logging.

Legacy `AmmunitionType` remains as the stable serialized identifier for
campaigns, snapshots and replay compatibility. `AmmunitionCatalog` maps each
identifier to an explicit `AmmunitionDefinition` object with independent
flight, collision, damage, terrain, inventory, reload and impact behavior
profiles. This compatibility layer allows the simulation and UI to migrate
without invalidating existing saves or commands.

The system must fit the existing module boundaries:

```text
game-domain
  ammunition types, immutable profiles, value objects and gameplay rules

game-contracts
  commands, snapshots and immutable ammunition/projectile events

game-simulation
  projectile movement, collision, impact effects and deterministic timing

game-application
  match lifecycle, command validation and domain-to-contract mapping

game-ai
  ammunition selection based on snapshots and available resources

android-app
  ammunition controls, visual mapping, animation and feedback
```

The ammunition system must not depend on Android, `Canvas`, bitmap dimensions,
render frames, wall-clock time or UI state.

## 2. Design goals

- Give every ammunition type a clear domain identity and explicit type.
- Make new ammunition types possible without expanding one central `when`.
- Keep projectile physics separate from impact behavior.
- Keep unit damage, fortress damage and terrain damage independent.
- Support multi-phase ammunition such as fragmentation, fire and toxic clouds.
- Allow an ammunition type to behave differently when it hits terrain, a unit
  or a fortress.
- Make all randomness deterministic and replayable from the match seed.
- Keep friendly fire, penetration and special effects explicit per type.
- Keep AI and player actions on the same ammunition catalog and rules.
- Keep Android responsible for presentation only.
- Scale projectile flight, impact, explosion and area-effect animations with
  the world camera and map zoom.
- Make projectile and impact behavior observable through structured,
  host-independent logs similar to the combat logging contract.
- Apply Clean Code and Single Responsibility Principle throughout the design.

## 3. Current and future ammunition

The current ammunition baseline is:

```text
ROCK
SIEGE_BOMB
POWDER_BARREL
CLUSTER_BOMB
FIRE_RAIN
PLAGUE_CAULDRON
```

Future types may include fantasy and humorous equivalents of modern weapons,
for example:

```text
NAPALM_LANTERN
ARCANE_SUN_CORE
DRAGON_BREATH_BARREL
VOID_METEOR
FROST_SHATTERER
SWARM_HIVE
```

These names are design proposals, not approved content. Every future type must
follow the same domain contract and must be balanced for one battle. Extremely
powerful ammunition is rare, has a small stock and has a long reload time.

## 4. Explicit ammunition type model

Every ammunition type has its own explicit class or object. Shared interfaces
define the stable boundary; they must not erase meaningful differences between
types.

Conceptual model:

```text
Ammunition
  id
  displayKey
  reloadProfile
  inventoryProfile
  flightProfile
  collisionProfile
  damageProfile
  impactBehavior
  availabilityRule
```

The exact implementation may use sealed types, data objects or data classes,
but a new ammunition type must be visible in the domain model and must not be
represented only by a collection of unrelated flags.

Example conceptual types:

```text
RockAmmunition
SiegeBombAmmunition
PowderBarrelAmmunition
ClusterBombAmmunition
FireRainAmmunition
PlagueCauldronAmmunition
```

An explicit type may own parameters unique to its behavior:

```text
ClusterBombAmmunition
  fragmentCount
  fragmentSpread
  fragmentAmmunition

PlagueCauldronAmmunition
  cloudRadius
  cloudDuration
  tickInterval
  driftsWithWind
  maximumStacks
```

Simple balance values remain data inside the type. Unique rules remain explicit
behavior and are not hidden behind a generic string or boolean.

## 5. Ammunition properties

### 5.1 Identity and availability

Each ammunition type defines:

- stable identifier;
- localization/display key;
- whether it is unlimited;
- team inventory usage;
- stock limit or replenishment rule;
- campaign/chapter availability;
- scenario restrictions, if any.

Campaign progression decides when ammunition is available. The domain type
defines what the ammunition does, not which level unlocks it.

### 5.2 Reload and inventory

Each type defines its own reload duration. Reload state belongs to the used
ammunition type and does not silently reset when the player selects another
type.

The player may select a different type while a reload is active. The current
reload continues, and the selection applies to the next shot.

Limited ammunition uses a shared team inventory. The inventory is not stored in
Android UI state.

### 5.3 Flight profile

Flight behavior is separate from impact behavior:

```text
FlightProfile
  mass
  initialSpeedMultiplier
  windResponse
  gravityResponse
  drag
  maximumLifetime
  canPierceUnits
  canHitTerrain
  canHitFortress
```

Mass is a gameplay input to the flight model. The simulation may derive
effective wind response, drag or terminal velocity from it, but those
relationships must be explicit and deterministic.

A projectile stores the ammunition identity and the immutable flight values
used at launch. A later balance change must not alter an already-running
projectile during replay.

### 5.4 Damage profile

Unit and fortress damage are separate values:

```text
DamageProfile
  unitDamage
  fortressDamage
  unitDamageFalloff
  fortressDamageFalloff
  minimumDamage
  maximumDamage
  friendlyFirePolicy
```

The system must not assume that a type dealing high unit damage also deals high
fortress damage. A type may damage units but not fortresses, or the reverse.

Area damage defaults to full damage at the center and a gradual falloff toward
the edge. A specific ammunition type may explicitly select another model.

### 5.5 Terrain profile

Terrain deformation is optional per ammunition type:

```text
TerrainProfile
  deformsTerrain
  craterRadius
  craterDepth
  deformationShape
```

Only types that explicitly opt in may deform terrain. Deformation is a battle
effect and does not persist into campaign state.

### 5.6 Collision profile

Collision behavior is defined per type:

```text
CollisionProfile
  targetLayers
  hitRadius
  stopsOnUnit
  piercesUnits
  stopsOnTerrain
  detonatesOnFortress
  friendlyFire
```

Possible behaviors include:

- stop at the first unit;
- hit and continue through units;
- ignore units and resolve on terrain;
- detonate at a fortress;
- resolve only after a timer expires;
- resolve after leaving the battlefield.

The collision system must not infer these rules from the renderer or from the
visual asset.

## 6. Impact behavior model

Special behavior is an explicit, typed part of the ammunition definition.
It must not be a free-form string and must not become a single large switch in
`DeterministicMatchSimulation`.

Conceptual behavior categories:

```text
DirectImpact
Explosion
Fragmentation
LingeringArea
StatusApplication
TerrainDeformation
DelayedDetonation
```

A single ammunition type may compose multiple phases:

```text
impact
  -> explosion
  -> fragments
  -> lingering fire
```

Each phase has a clear responsibility and produces inspectable commands or
events. A subprojectile is a full projectile with its own flight and collision
rules, but it cannot create another generation of subprojectiles.

## 7. Impact context

The same ammunition type may behave differently depending on what it hits.
Impact resolution receives explicit context:

```text
ImpactContext
  projectileId
  ammunitionId
  position
  hitTarget
  hitLayer
  simulationTime
  deterministicSeed
```

`hitLayer` distinguishes at least:

```text
TERRAIN
UNIT
FORTRESS
OUT_OF_BOUNDS
```

The ammunition definition decides which phases run for each hit layer. For
example, a type may create a crater on terrain, a direct hit on a unit and a
different siege effect on a fortress.

## 8. Layered architecture and responsibilities

### 8.1 `game-domain`

Owns:

- explicit ammunition types;
- ammunition IDs and value objects;
- flight, collision, damage and terrain profiles;
- typed impact behaviors;
- status and stacking policies;
- inventory and availability rules;
- pure validation of ammunition definitions.

It does not own:

- Android resources;
- UI selection;
- logging implementation;
- mutable simulation collections;
- wall-clock timers.

### 8.2 `game-contracts`

Owns immutable boundary models:

- selected ammunition commands;
- ammunition availability snapshots;
- projectile snapshots;
- active area-effect snapshots;
- projectile and impact events;
- damage and status events.

Contracts must not expose mutable domain objects or Android classes.

### 8.3 `game-simulation`

The simulation coordinates focused systems:

```text
ProjectileSimulation
  -> ProjectileMovementSystem
  -> ProjectileCollisionSystem
  -> ImpactResolutionSystem
  -> DamageAreaSystem
  -> StatusEffectSystem
  -> LingeringEffectSystem
  -> TerrainDeformationSystem
  -> ProjectileEventCollector
```

The coordinator controls ordering and deterministic time. It does not contain
the detailed rule for every ammunition type.

#### ProjectileMovementSystem

Owns only:

- deterministic position and velocity updates;
- gravity, wind, mass and drag;
- lifetime and out-of-bounds handling.

It does not decide damage or status effects.

#### ProjectileCollisionSystem

Owns only:

- collision candidates;
- target layer filtering;
- first-hit, piercing and stopping behavior;
- creation of an `ImpactContext`.

It does not apply damage.

#### ImpactResolutionSystem

Owns only:

- selecting the typed impact behavior;
- executing ordered impact phases;
- creating damage, terrain, status and subprojectile commands.

It does not render effects or mutate Android state.

#### DamageAreaSystem

Owns only:

- affected-target selection;
- distance falloff;
- unit damage;
- fortress damage;
- friendly-fire policy;
- resistance and vulnerability modifiers.

Unit damage and fortress damage remain separate resolution paths.

#### StatusEffectSystem

Owns only:

- applying status effects to eligible units;
- immunity and resistance checks;
- refresh and stacking limits;
- status expiration.

Fortresses do not automatically receive unit statuses. Siege-specific fortress
effects must be defined separately.

#### LingeringEffectSystem

Owns only:

- active area lifetime;
- tick scheduling;
- optional wind drift;
- overlap limits;
- expiration events.

Fire and toxic clouds are examples of lingering effects. Their movement under
wind is opt-in per effect.

#### TerrainDeformationSystem

Owns only:

- validating terrain deformation parameters;
- applying battle-local heightmap changes;
- emitting terrain events.

### 8.4 `game-application`

Owns:

- validating player and AI ammunition commands;
- passing commands to the match session;
- mapping domain snapshots and events to contracts;
- preserving the application lifecycle.

It does not calculate trajectories or damage.

### 8.5 `game-ai`

AI uses the same catalog, inventory rules and simulation behavior as the
player. AI owns only selection strategy:

- target evaluation;
- ammunition choice;
- timing and tactical preference.

AI must not have private ammunition physics or damage formulas.

### 8.6 `android-app`

Owns:

- ammunition selection controls;
- availability and stock presentation;
- projectile visual mapping;
- impact animation mapping;
- audio and particle effects;
- HUD feedback.

The renderer consumes snapshots and events. It never decides whether an impact
deals damage or whether a status stacks.

Projectile and ammunition effects are world-space presentation. Flight sprites,
impact animations, explosions, fragments, fire, toxic clouds and other
lingering effects must use the same camera transform and zoom scale as the
battlefield. Their position and visible size must therefore scale together
with the map.

The renderer must not use a second, screen-space scale for ammunition effects.
The following elements remain screen-space and do not scale with map zoom:

- ammunition selection controls;
- reload indicators;
- HUD labels and counters;
- debug controls.

An impact event carries world coordinates and an effect identifier. The Android
layer maps it to a world-space render state, and the battlefield renderer
applies the camera transform exactly once. This prevents explosions or clouds
from appearing detached from the terrain when the map is zoomed.

## 9. Ammunition logging and observability

Projectile behavior must be diagnosable without attaching Android-specific
logging to the simulation. The design follows the combat logging contract:

```text
game-simulation
  AmmunitionLogSink
  AmmunitionLogRecord
  NoOpAmmunitionLogSink

android-app
  AndroidAmmunitionLogSink
```

The simulation receives the sink through dependency injection. Tests and
headless hosts use `NoOpAmmunitionLogSink` or an in-memory collector. Android
provides the Logcat adapter from the application/controller boundary.

The logger is diagnostic only. It must never decide collision results, change
damage, advance timers, create fallback gameplay behavior or swallow
simulation errors.

### 9.1 Structured log record

Every record includes:

```text
AmmunitionLogRecord
  matchId
  simulationTimeMilliseconds
  reason
  projectileStates
  activeEffectStates
```

Each projectile state includes:

```text
ProjectileLogState
  projectileId
  ammunitionId
  firedBy
  position
  velocity
  mass
  windResponse
  flightPhase
  targetLayer
  targetId
  lifetimeRemaining
  collisionMode
  parentProjectileId
  subprojectileGeneration
```

Each active effect state includes:

```text
ImpactEffectLogState
  effectId
  sourceProjectileId
  ammunitionId
  effectType
  center
  radius
  remainingSeconds
  tickInterval
  nextTickInSeconds
  affectedTargetCount
  stackCount
  driftsWithWind
```

Damage and status records additionally include:

```text
  targetId
  targetLayer
  baseDamage
  falloffMultiplier
  resistanceMultiplier
  finalDamage
  statusId
  statusDuration
  statusStrength
```

### 9.2 Stable log reasons

Log reasons must be stable identifiers rather than arbitrary prose:

```text
match-started
ammunition-selected
projectile-fired
projectile-advanced
projectile-collision-candidate
projectile-hit-terrain
projectile-hit-unit
projectile-hit-fortress
impact-resolved
damage-applied
subprojectile-spawned
lingering-effect-started
lingering-effect-ticked
lingering-effect-refreshed
lingering-effect-expired
status-applied
status-refreshed
status-expired
terrain-deformed
projectile-expired
```

The `reason` value enables filtering without parsing log text.

### 9.3 Logging modes

The logger should support explicit verbosity modes:

```text
OFF
EVENTS_ONLY
STATE_CHANGES
TICK
```

Recommended defaults:

- production: `OFF` or `EVENTS_ONLY`;
- local debugging: `STATE_CHANGES`;
- difficult trajectory or zoom bugs: `TICK`;
- deterministic tests: an in-memory sink with explicit assertions.

`TICK` mode may produce substantial output and must be opt-in. Important
transitions must always be represented by structured events, so tick logging
is never the only way to understand a failure.

### 9.4 World-space and zoom diagnostics

Because ammunition animations scale with map zoom, debug records keep
simulation coordinates separate from render coordinates:

```text
World-space log:
  projectile position
  impact center
  effect radius

Render diagnostic, only when enabled:
  camera scale
  camera offset
  mapped screen position
  mapped screen radius
```

World-space values are authoritative. Render diagnostics are produced by the
Android presentation layer and must not be fed back into gameplay.

This makes it possible to distinguish:

- a simulation error in the impact position or radius;
- a camera transform error;
- an effect being scaled twice;
- an effect rendered in screen-space by mistake.

### 9.5 Android adapter

The Android adapter may use a dedicated Logcat tag:

```text
UrkaaazAmmunition
```

Example filtering command:

```bash
adb logcat -s UrkaaazAmmunition:D '*:S'
```

The adapter formats structured records for Logcat but does not change their
meaning. It is injected only from the Android/application boundary so local
JVM tests do not call `android.util.Log`.

### 9.6 Logging tests

The logging contract must have tests for:

- no-op logging on default/headless hosts;
- one record for every important projectile transition;
- stable projectile and effect IDs;
- parent and subprojectile generation tracking;
- correct world-space positions and radii;
- correct damage, falloff and resistance values;
- deterministic ordering for equal-time events;
- no Android dependency in simulation tests;
- optional render diagnostics using the same camera transform as rendering.

## 10. Effect resolution pipeline

Each simulation step follows this order:

```text
1. Advance deterministic simulation time.
2. Advance active projectiles using their flight profiles.
3. Detect terrain, unit, fortress and bounds collisions.
4. Create immutable impact contexts.
5. Resolve typed impact phases.
6. Create damage, status, terrain and subprojectile commands.
7. Resolve area targets and distance falloff.
8. Apply unit resistances and fortress siege modifiers.
9. Start or refresh lingering effects.
10. Spawn one permitted generation of subprojectiles.
11. Apply terrain deformation.
12. Emit ordered domain events.
13. Publish the next immutable snapshot.
```

Commands are collected before state mutation where simultaneous effects are
possible. Resolution must not depend on collection iteration order.

## 11. Status effects and stacking

Statuses are unit effects, not fortress effects. Examples include:

```text
BURNING
POISONED
SLOWED
STUNNED
```

Each status application defines:

- duration;
- strength;
- source ammunition;
- refresh policy;
- maximum strength;
- maximum stack count, if stacking is permitted.

The default reapplication policy is:

```text
same status + same unit
  -> refresh duration
  -> do not exceed maximum strength
```

Different statuses may coexist unless a unit-specific immunity or conflict
rule says otherwise.

## 12. Persistent area effects

An area effect stores:

```text
LingeringEffect
  id
  sourceAmmunition
  center
  radius
  remainingDuration
  tickInterval
  damagePerTick
  statusEffects
  driftsWithWind
  maximumOverlap
```

Overlapping effects are allowed with explicit limits and stacking rules. The
system must not allow unlimited damage multiplication from repeated fire or
toxic clouds.

## 13. Determinism and replay

All random decisions use a match-seeded random source:

- damage variation;
- fragment spread;
- subprojectile order;
- optional status variation;
- optional effect drift variation.

Randomness must not depend on:

- Android frame timing;
- wall-clock time;
- hash-map iteration order;
- sprite order;
- network arrival order.

Events must include stable projectile IDs, ammunition IDs, source IDs and
simulation timestamps. A replay must produce the same impact positions, damage,
status durations and terrain changes.

## 14. Events and contracts

Recommended events:

```text
ProjectileFired
ProjectileAdvanced
ProjectileHitTerrain
ProjectileHitUnit
ProjectileHitFortress
ImpactResolved
DamageApplied
AreaDamageApplied
SubprojectileSpawned
LingeringEffectStarted
LingeringEffectTicked
LingeringEffectRefreshed
LingeringEffectExpired
StatusApplied
StatusRefreshed
StatusExpired
TerrainDeformed
```

Damage events should include:

- source projectile ID;
- ammunition ID;
- source team;
- target ID;
- target layer;
- base damage;
- falloff modifier;
- resistance modifier;
- final damage;
- simulation timestamp.

The UI may use events for immediate feedback, but the snapshot remains the
authoritative state.

## 15. Ammunition examples

### 14.1 Rock

```text
flight:
  heavy, direct trajectory

impact:
  direct unit/fortress damage
  small optional crater
```

### 14.2 Siege Bomb

```text
flight:
  heavy projectile with moderate wind response

impact:
  radial unit damage
  separate fortress damage
  medium crater
```

### 14.3 Cluster Bomb

```text
impact:
  primary impact
  deterministic fragment spread
  one generation of full subprojectiles
  no subprojectile recursion
```

### 14.4 Fire Rain

```text
impact:
  immediate area damage
  lingering burning area
  optional unit burning status
  optional wind drift
```

### 14.5 Plague Cauldron

```text
impact:
  toxic cloud
  periodic damage
  poison status
  controlled overlap
  no automatic fortress poison status
```

### 14.6 Future powerful fantasy ammunition

A future napalm-like or atomic-like fantasy type may combine:

```text
impact
  -> large local explosion
  -> heat or radiation-like status
  -> temporary lingering area
  -> local terrain deformation
```

It must remain battle-local, rare and strongly limited by stock and reload.

## 16. Adding a new ammunition type

The extension checklist is:

1. Add a new explicit domain type and stable ID.
2. Define reload, inventory and availability rules.
3. Define the flight profile.
4. Define collision layers and stopping/piercing behavior.
5. Define separate unit and fortress damage.
6. Define terrain deformation, if any.
7. Define impact phases and their order.
8. Define status, lingering and overlap rules, if any.
9. Register the type in the ammunition catalog.
10. Add domain and simulation tests.
11. Add contract events/snapshot fields only when required.
12. Add AI selection policy or priority.
13. Add Android visual and audio mappings.
14. Add localization and balance documentation.

Adding a new type must not require editing unrelated ammunition algorithms.

## 17. Testing strategy

### Domain tests

- every type has valid positive timing and non-negative ranges;
- unit and fortress damage are independently defined;
- invalid profile combinations are rejected;
- subprojectile recursion is rejected;
- status stacking limits are valid.

### Simulation tests

- mass and wind produce deterministic trajectories;
- collision behavior matches the type profile;
- direct, radial and falloff damage are correct;
- unit and fortress damage use separate values;
- friendly-fire rules are respected;
- terrain deformation is battle-local;
- lingering effects tick and expire deterministically;
- wind drift applies only when enabled;
- subprojectiles have one permitted generation;
- status refresh and resistance rules work;
- simultaneous impacts are independent of collection order;
- replaying the same match produces the same result.

### Application and contract tests

- unavailable ammunition cannot be fired;
- team inventory is consumed correctly;
- changing selection during reload affects the next shot only;
- snapshots expose active projectiles and effects;
- events contain stable IDs and required diagnostic data.

### Android tests

- every registered type has a visual mapping;
- depleted ammunition is visibly unavailable;
- reload state is rendered independently from selection;
- impact events map to the correct animation and sound;
- the renderer does not calculate gameplay damage.

## 18. Migration plan

Implementation should be incremental:

## 19. Implementation status

The current implementation includes:

- explicit catalog definitions for all six production ammunition types;
- immutable launch-time projectile flight profiles;
- per-ammunition reload state and shared team inventory;
- typed direct, explosive, fragmentation and lingering behaviors;
- deterministic subprojectile IDs, parent links and generation limits;
- terrain, unit and fortress collision layers;
- configurable piercing and ignored-unit collision modes;
- area damage with unit/fortress separation and linear falloff;
- status persistence, refresh caps, duration expiry, slowing and stunning;
- status immunity and resistance hooks on unit definitions;
- deterministic lingering-area drift configuration;
- rich projectile, effect and status snapshots;
- structured ammunition events and host-independent logging;
- Android world-space rendering compatibility;
- AI usage of the shared catalog and per-type reload state.

Future ammunition content remains intentionally outside the current baseline.
Adding a new type requires a catalog definition, balance review, simulation
tests, replay coverage, localization and the corresponding Android assets.

1. Introduce stable ammunition IDs while preserving current behavior.
2. Extract immutable flight, collision and damage profiles.
3. Introduce explicit ammunition types and a catalog.
4. Move impact resolution from the central branch into focused systems.
5. Add typed lingering effects and status effects.
6. Add explicit subprojectiles and deterministic spread.
7. Expand contracts and Android presentation mappings.
8. Remove obsolete effect flags and central ammunition branching.

Each migration step must preserve existing replay and gameplay behavior unless a
separate balance decision explicitly changes it.

## 19. Non-goals

This architecture does not currently define:

- campaign persistence of terrain or status effects;
- multiplayer synchronization;
- network authority;
- a final list of future ammunition names;
- final balance values;
- final projectile art or animation assets;
- a second independent ammunition system for AI.

Those decisions must be made separately and must not weaken the module
boundaries described here.
