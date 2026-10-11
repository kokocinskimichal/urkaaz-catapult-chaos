# Combat System Design

## 1. Status and scope

This document defines the target architecture for unit combat in Urkaaaz. It
is a design document only. It does not authorize implementation in the
current simulation.

The design must fit the existing module boundaries:

```text
game-domain
  pure gameplay models, attack profiles, balance and value objects

game-contracts
  commands, snapshots and domain events exposed across boundaries

game-simulation
  deterministic systems that advance the match and resolve combat

game-application
  MatchSession lifecycle and command/event boundary

game-ai
  snapshot-only decisions; no direct simulation mutation

android-app
  ViewModel, render-state mapping, battlefield rendering and HUD feedback
```

The combat system must not depend on Android, `Canvas`, bitmap dimensions,
render frames, wall-clock time or UI state.

## 2. Design goals

- Keep one authoritative combat state inside the simulation.
- Separate movement, targeting, groups, attack timing, damage and presentation.
- Make simultaneous attacks independent of collection order.
- Make damage randomness reproducible through a match seed.
- Keep target selection deterministic and stable.
- Support melee synergy without hard-coding Defender-only behavior.
- Make new attack categories such as `AIRBORNE` possible without rewriting the
  existing melee and ranged systems.
- Expose short-lived combat events for UI feedback instead of making UI infer
  combat from positions or HP differences.
- Keep every class focused on one responsibility in accordance with SRP.

## 3. Attack categories

Every unit owns one primary `AttackType` in its domain combat profile.
`AttackType` is an extensible enum/category, not a renderer decision.

| Attack type | Current unit | Responsibility |
|---|---|---|
| `MELEE` | Defender, Goblin Raging Boar | Approach a target, establish contact and attack the selected unit. |
| `RANGED` | Goblin Slingmaster | Attack a visible target from distance without melee contact. |
| `CONTACT_EXPLOSIVE` | Demolisher | Approach a valid target, detonate on contact and destroy itself. |
| `SIEGE_MISSION` | Sapper | Approach the enemy fortress, throw a bomb and return to refill. |

Future types, such as `AIRBORNE` or `BURROWING`, must define their own
targeting, movement, damage, interruption, synergy and presentation policies
before a unit is assigned to them.

An attack category describes the primary combat model. A unit may also have
secondary actions, but a secondary action must be explicit and must not change
the meaning of the primary category implicitly.

## 4. Domain model

### 4.1 Unit state

The runtime unit remains a domain object containing health, position, team and
unit definition. Combat-specific mutable data belongs in a dedicated value
object rather than a collection of unrelated booleans.

```text
Unit
  id
  definition
  team
  position
  health
  movementState
  combatState
  missionState
```

```text
CombatState
  actionState
  targetId
  targetLockedAt
  attackCycleId
  attackStartedAt
  attackHitAt
  attackRecoveryEndsAt
  lastAttackRoll
```

Recommended action states:

```text
GARRISONED
MOVING
SEEKING_TARGET
WINDING_UP
ATTACKING
RECOVERING
RETURNING
DEAD
```

`DEAD` is excluded immediately from targeting and collision, but can remain in
the snapshot temporarily for its death animation.

### 4.2 Attack profile

The unit definition exposes an immutable `AttackProfile`:

```text
AttackProfile
  attackType
  baseDamage
  damageRange
  cooldown
  windUpDuration
  hitMoment
  recoveryDuration
  attackRange
  engagementRange
  lineOfSightRequired
  participatesInMeleeSynergy
  targetPolicy
```

`Defender` and `Raging Boar` use the same melee behavior and differ through
configuration values such as HP, speed, cooldown, fortress damage and damage
range. Their special behavior must not be duplicated in separate combat
algorithms unless a later design explicitly adds a Boar-only mechanic.

## 5. System responsibilities

The simulation owns a `CombatStepCoordinator`, but each operation is delegated
to a focused system.

```text
CombatStepCoordinator
  -> UnitMovementSystem
  -> TargetAcquisitionSystem
  -> AttackGroupSystem
  -> AttackScheduleSystem
  -> DamageResolutionSystem
  -> DeathResolutionSystem
  -> CombatEventCollector
```

### 5.1 UnitMovementSystem

Owns only spatial movement:

- advance units toward their current movement objective;
- stop melee units at contact distance;
- keep ranged units at their preferred distance;
- apply Sapper contact slowdown;
- maintain readable allied spacing;
- resolve gradual position changes after a group join;
- never choose targets;
- never apply damage.

The system may perform small formation corrections during an attack, but an
attack does not resume the unit's primary march.

### 5.2 TargetAcquisitionSystem

Owns target candidates and target locks:

- filter invalid targets;
- apply the target policy for the attack type;
- preserve an existing valid `targetId`;
- select a replacement only when the target dies, leaves valid range or becomes
  otherwise invalid;
- apply stable tie-breakers;
- never use renderer frames or wall-clock time.

Target acquisition is not executed as a fresh random decision every tick.

### 5.3 AttackGroupSystem

Owns group membership:

- create a group when compatible units select the same target;
- add a compatible allied unit when it reaches the engagement area;
- remove units that die, lose compatibility, change target or leave the area;
- split a group when its members no longer share a valid target;
- expose the group's target and active members to synergy calculation;
- never teleport members or push existing members backward.

An attack group is not a wave, a garrison or a movement formation. It is a
temporary combat relationship.

```text
AttackGroup
  id
  team
  targetId
  memberIds
  attackType
  createdAt
```

For the first version, a group has one primary target. Individual members still
retain their own `targetId`, allowing future subgroups without changing the
domain boundary.

### 5.4 AttackScheduleSystem

Owns attack lifecycle:

```text
IDLE
  -> WINDING_UP
  -> ATTACK_HIT
  -> RECOVERING
  -> WINDING_UP
```

Damage is applied at one explicit hit moment in the attack animation. The
renderer receives this timing through combat state and events; it does not
decide when damage occurs.

An attack is interrupted when its target becomes invalid. Ordinary incoming
damage does not interrupt an attack. A future crowd-control effect may
interrupt it through an explicit combat rule.

### 5.5 DamageResolutionSystem

Owns damage calculation only. It does not choose targets or move units.

```text
DamageRequest
  sourceId
  sourceType
  targetId
  damageKind
  baseDamage
  damageRoll
  synergyMultiplier
  incomingModifier
  distanceModifier
  finalDamage
```

### 5.6 DeathResolutionSystem

Owns death transitions:

- mark a unit dead;
- remove it from targeting, collision and group membership;
- emit `UnitDefeated`;
- preserve it temporarily in the snapshot for death presentation;
- resolve on-death effects such as Demolisher detonation.

Dead units do not receive new attacks. Attacks scheduled in the same combat
step are resolved from the pre-damage state, so two units can kill each other
in the same tick.

## 6. Simulation tick pipeline

Each simulation tick follows this order:

```text
1. Advance deterministic time.
2. Update cooldowns and mission timers.
3. Advance movement and formation spacing.
4. Remove invalid target locks.
5. Acquire targets for units without valid targets.
6. Create, join, split and clean attack groups.
7. Schedule attacks whose range and timing conditions are satisfied.
8. Collect all attack intents for the tick.
9. Resolve all intents against the same pre-hit state.
10. Apply damage, modifiers and explosions.
11. Resolve simultaneous deaths and on-death effects.
12. Update group membership and unit states.
13. Emit domain events.
14. Publish the next immutable snapshot.
```

Damage must not be applied while iterating over attacker order. Attack intents
are collected first, which guarantees that two equal melee units can both
deliver their hit even when both receive lethal damage in that tick.

## 7. Target selection

Target selection is deterministic and sticky. It is not random.

Candidates are filtered by:

- alive state;
- team;
- attack category;
- range;
- line of sight for ranged attacks;
- mission and return state;
- target policy.

The score order is:

1. valid target of the existing attack group;
2. current contact target;
3. profile-specific priority;
4. distance;
5. threat to the friendly fortress;
6. stable `entityId` tie-breaker.

A unit does not abandon a valid target merely because another target becomes
slightly closer. Target replacement occurs only when the current target becomes
invalid. A future explicit retarget policy may add hysteresis or a required
score advantage.

## 8. Attack groups and synergy

### 8.1 Group formation

Compatible units selecting the same target form one attack group. A compatible
ally joining an active engagement becomes a member of the existing group on the
next combat decision.

The UI must keep group members readable:

- no exact position overlap;
- partial overlap allowed;
- no unit may hide almost the entire sprite of another;
- members must not spread into a long line while still being one group;
- spacing is resolved in world coordinates;
- bottom anchors and depth ordering remain stable.

### 8.2 Melee synergy

Melee synergy applies to all living melee units actually engaged with the same
target. The initial participants are Defender and Raging Boar.

```text
multiplier = min(1 + (N - 1) * 0.35, 2.0)
```

```text
N = 1 -> 1.00x
N = 2 -> 1.35x
N = 3 -> 1.70x
N >= 4 -> 2.00x
```

The multiplier applies to each participating attack. Ranged units, Sapper and
Demolisher do not participate unless a future profile explicitly opts into
melee synergy.

## 9. Damage model

### 9.1 Damage range

Units do not deal one fixed damage value on every hit. Each attack rolls
uniformly from `90%..110%` of the configured base damage, rounded to an integer
with a minimum of `1`.

```text
base damage 30 -> possible roll 27..33
```

The roll uses a deterministic match-seeded random source. It cannot depend on
collection iteration order, Android frames or wall-clock time.

### 9.2 Luck

Luck changes the probability distribution while preserving the `90%..110%`
range. Positive Luck biases rolls upward; negative Luck biases rolls downward.
Luck does not silently expand the configured boundaries.

The damage pipeline is:

```text
base range
  -> seeded damage roll
  -> Luck distribution
  -> target incoming modifier
  -> melee synergy multiplier
  -> distance/falloff modifier
  -> integer clamp
  -> DamageApplied
```

The exact roll, Luck input, synergy count and final damage are part of the
attack event for tests and replay diagnostics.

### 9.3 Simultaneous damage

All attacks scheduled for the same simulation tick read the same pre-hit state.
After all requests are calculated, the resolver applies them as a batch.

Therefore, two equal Defender units can kill each other in the same tick. The
result cannot depend on list order.

## 10. Unit-specific combat policies

### 10.1 Defender and Raging Boar

Both use the same `MELEE` policy:

- acquire a target;
- approach contact range;
- join a compatible group;
- attack at the configured hit moment;
- receive and contribute to melee synergy;
- retain the target until it becomes invalid.

Their differences are configuration values and presentation. Future Boar-only
mechanics such as a charge must be explicit extensions to the melee profile.

### 10.2 Slingmaster

Slingmaster uses `RANGED`:

- requires a valid target within range;
- requires line of sight;
- does not need contact;
- does not contribute to melee synergy;
- produces a projectile or ranged attack event;
- loses or interrupts the attack if the target becomes invalid.

Terrain and blocking obstacles may break line of sight.

### 10.3 Demolisher

Demolisher uses `CONTACT_EXPLOSIVE`:

- chooses the nearest valid unit target;
- detonates on contact with the first important target;
- always dies after detonating, even if no target is hit;
- if killed before contact, detonates at its death position;
- damages all units in the radius, including allies;
- may damage a fortress in the radius;
- uses full damage near the center and smooth distance falloff;
- uses one shared base damage roll for the entire explosion;
- does not participate in melee synergy.

The explosion is one domain operation that creates one explosion event and
multiple damage results.

### 10.4 Sapper

Sapper uses `SIEGE_MISSION`, not ordinary melee:

```text
ADVANCING
  -> BOMB_APPROACH
  -> BOMB_WIND_UP
  -> BOMB_HIT
  -> RETURNING
  -> REFILLING
  -> ADVANCING
```

Rules:

- at most one active Sapper per team;
- primary target is the enemy fortress;
- stops at a stand-off position;
- throws the bomb at the explicit hit moment;
- returns immediately after the hit;
- receives full ranged and explosion damage;
- receives `45%` of normal melee damage;
- contact with melee slows it to `65%` of normal speed;
- contact does not stop or push it backward;
- does not attack ordinary units;
- returns with full HP and a refilled bomb.

Future special targets such as Bastions require explicit target policies.

## 11. Domain events

Combat events are short-lived facts emitted by the simulation:

```text
TargetAcquired
TargetLost
AttackGroupCreated
AttackGroupJoined
AttackGroupSplit
AttackStarted
AttackHit
DamageApplied
SynergyApplied
ExplosionTriggered
UnitDefeated
SapperMissionStarted
SapperBombThrown
SapperReturned
```

Events contain stable IDs and all values needed by UI and diagnostics. For
damage events this includes:

- source and target IDs;
- source unit type;
- attack type;
- base damage;
- rolled damage;
- Luck influence;
- synergy count and multiplier;
- distance modifier;
- final damage;
- world position;
- simulation timestamp.

Events are not UI commands. The application boundary transports them, and the
Android layer maps them to presentation effects.

## 12. Contracts and application layer

`game-contracts` exposes immutable snapshots and event DTOs. It must not expose
mutable domain units or Android classes.

`UnitSnapshot` should eventually contain:

```text
entityId
team
unitType
attackType
x
y
health
maxHealth
actionState
targetId
attackGroupId
attackCycleId
attackProgress
facing
```

`MatchSession` remains responsible for:

- starting and stopping the simulation;
- dispatching commands;
- advancing deterministic time;
- exposing the latest snapshot;
- exposing and consuming events.

It must not calculate damage or select targets.

## 13. Android UI and rendering

### 13.1 UI data flow

```text
MatchSession
  -> MatchSnapshot + MatchEvent list
  -> MatchViewModel
  -> RenderStateMapper
  -> BattlefieldRenderer / HUD views
```

`RenderStateMapper` maps contracts to presentation models. It does not infer
combat state from HP or positions.

### 13.2 Battlefield rendering

The renderer uses snapshot data for persistent visuals:

- unit position and bottom anchor;
- health and max health;
- action state;
- attack type;
- target direction;
- group ID;
- attack progress;
- facing.

It uses events for transient visuals:

- attack flash;
- hit marker;
- floating damage number;
- `SYNERGIA` label;
- explosion;
- Sapper bomb trail;
- death effect;
- group join feedback.

The global render clock may animate idle ambience and non-authoritative
presentation loops. It must not decide combat hits, damage or attack frame
progress.

### 13.3 Attack animation contract

The renderer selects animation by resolved action and attack type:

```text
actionState + attackType + team + facing
```

For a normal attack:

```text
attackCycleId changes
  -> reset animation phase
attackProgress advances from 0 to 1
  -> hit frame occurs at the domain hit moment
```

All frames in one animation family must share a stable canvas and bottom
anchor. The renderer must preserve aspect ratio and must not change combat
position because source bitmap dimensions differ.

### 13.4 Group presentation

Group spacing is solved by the simulation in world coordinates. The renderer
must not solve severe overlap by shrinking sprites, hiding units or changing
their combat position.

The UI should make group state readable through:

- stable individual sprites;
- HP bars above every unit;
- floating damage numbers at the hit target;
- a short `SYNERGIA` feedback when the multiplier is above `1.0x`;
- optional subtle group highlight, without obscuring units;
- consistent depth ordering based on world position and stable ID.

The UI must never expose a manual target picker because target selection is an
automatic simulation rule.

### 13.5 HUD and debug presentation

The HUD remains responsible for:

- supply and recruitment;
- wave sending;
- fortress health;
- match timer;
- ammunition and spells;
- debug controls.

Combat debug tools may show:

- unit action state;
- target ID;
- group ID and members;
- attack type;
- cooldown and attack progress;
- last damage roll;
- synergy count and multiplier;
- last combat event.

Debug data is read-only and must not bypass commands or mutate the simulation.

## 14. Clean Architecture and SRP rules

The following boundaries are mandatory:

| Responsibility | Owner |
|---|---|
| Unit balance and attack profiles | `game-domain` |
| Unit runtime state | `game-domain` |
| Target candidate scoring | `TargetAcquisitionSystem` |
| Group membership | `AttackGroupSystem` |
| Movement and spacing | `UnitMovementSystem` |
| Attack lifecycle | `AttackScheduleSystem` |
| Damage rolls and modifiers | `DamageResolutionSystem` |
| Death and on-death effects | `DeathResolutionSystem` |
| Event construction | `CombatEventCollector` |
| Snapshot transport | `game-contracts` and `MatchSession` |
| AI decisions | `game-ai` |
| Presentation mapping | `RenderStateMapper` |
| Drawing and animations | `BattlefieldRenderer` |
| HUD interaction | Android UI and ViewModel |

No system may:

- mutate another system's private state;
- call Android code;
- inspect bitmap dimensions to decide gameplay;
- use UI state as a combat input;
- select targets and apply damage in the same helper;
- hide invalid inputs behind broad catches;
- depend on unordered collection iteration for outcomes.

## 15. Testing strategy

### Domain tests

- every unit has a valid `AttackType`;
- melee units share the same behavior profile;
- future attack categories can be added without changing existing profiles;
- damage ranges have valid boundaries;
- Luck preserves the configured damage boundaries.

### Simulation tests

- target locks remain stable;
- invalid targets are replaced deterministically;
- compatible units join the existing group;
- incompatible units create a separate group;
- group members remain within spacing constraints;
- melee synergy follows `1.00x`, `1.35x`, `1.70x`, `2.00x`;
- two equal melee units can kill each other in one simultaneous resolution;
- damage does not depend on collection order;
- seeded damage rolls replay identically;
- ranged attacks require line of sight;
- Demolisher damages allies and enemies in its radius;
- Demolisher uses one shared explosion roll;
- lethal Demolisher damage triggers death-position detonation;
- Sapper contact slows it without pushing or stopping it;
- Sapper receives reduced melee but full ranged/explosion damage;
- only one Sapper can be active per team.

### Contract and mapper tests

- snapshots expose action, target, group and attack progress;
- every combat event maps to the correct presentation event;
- no combat event is lost between `MatchSession` and the UI;
- UI mapping never changes domain values.

### Android presentation tests

- attack animation resets on `attackCycleId`;
- hit feedback appears at the event position;
- source bitmap size cannot change the unit anchor;
- group members remain readable;
- HP bars and synergy labels do not obscure the unit group.

## 16. Implementation order

1. Add immutable combat profile concepts to the domain.
2. Add explicit combat state to runtime units.
3. Extract movement from the current mixed simulation path.
4. Implement deterministic target acquisition and target locks.
5. Implement attack groups and world-space group spacing.
6. Implement attack intents and batched damage resolution.
7. Add damage ranges, seeded RNG, Luck and melee synergy.
8. Implement Defender and Raging Boar through the shared melee profile.
9. Implement Slingmaster ranged targeting and line of sight.
10. Implement Demolisher explosion and death-position detonation.
11. Implement Sapper mission phases.
12. Extend contracts with combat state and events.
13. Connect `MatchSession` event transport.
14. Replace renderer inference with snapshot/event-driven animation.
15. Add debug inspection and the full test matrix.

## Podsumowanie

Combat system powinien być deterministycznym, event-driven systemem domenowym,
w którym ruch, targetowanie, grupy, cykl ataku, obrażenia i śmierć są osobnymi
odpowiedzialnościami. Jednostki melee używają wspólnego modelu i synergii,
obrażenia są losowane z zakresu `90–110%` przez seedowane RNG, a ataki z tego
samego ticka są rozwiązywane symultanicznie. Demolisher i Sapper mają osobne
profile misji, natomiast UI otrzymuje jawny snapshot oraz eventy i nie odgaduje
stanu walki z pozycji, bitmap ani zmian HP.
