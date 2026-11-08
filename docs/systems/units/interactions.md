# Unit interactions

This document is copied from the legacy `GAME_DESIGN.md` unit-interaction
section. The pair in the matrix is unordered: the same rule applies regardless
of which team owns the row or column unit.

## Implemented Sapper/Demolisher role rework

- **Sapper:** at most one active Sapper is allowed at a time. Ordinary enemy
  units cannot target or damage it, including Demolisher explosions. Physical
  contact still slows it to 65% of its normal movement speed without
  hard-blocking or pushing it. Bosses, catapult projectiles, spells and other
  area effects can still damage the Sapper.
- The Sapper travels to the nearest hostile siege objective ahead of it: a boss,
  active Runic Bastion, or enemy fortress. It compares their approach points,
  attacks from the selected target's stand-off distance, consumes its held
  bomb, then returns to its own fortress using normal movement in the opposite
  direction. Before an enemy fortress or boss, it uses the configured 200-world
  unit stand-off from the target's front; Runic Bastion keeps its existing
  front-face approach. After returning, it can begin another bomb run.
- The Sapper's bomb deals medium damage to the primary target and half that
  damage to units in the target fortress's garrison. Boss and Bastion targets
  receive only the primary-target damage. Throwing consumes the bomb and the
  Sapper returns immediately. Its presentation plays one turn toward its
  fortress, then loops a bomb-less, cheerful light jog with only a subtle
  spring in each step; simulation movement remains authoritative. A small
  world-space bomb flight is triggered by the held-bomb transition and targets
  the fortress, Bastion, or boss; it is presentation-only and does not change
  damage timing. Reaching its own gate refills the bomb and restores the Sapper
  to full health.
- **Demolisher:** remains a slow, one-use suicide unit. Unit contact triggers an
  immediate detonation. At an enemy fortress or Runic Bastion front face it
  performs a 0.5-second forward throw, moving 90 world units before detonating.
  Its explosion deals 300 damage with a 260 world-unit radius. The unit that
  triggers the detonation receives full explosion damage. Nearby units receive
  damage reduced by distance.
- Demolishers do not target Sappers. Their explosions exclude Sappers while
  continuing to damage other units under the existing explosion rules.
- Mixed-unit formations are intentionally deferred and are not part of this
  decision.
- Returning Sapper animation playback is presentation-only and keyed to its
  existing `RETURNING` action state. The turn plays once before the low-bounce
  jog cycle; the loop keeps a stable gameplay anchor, and RED uses the existing
  mirrored neutral-frame and team-texture pipeline.

These rules are implemented for the current unit roster, including boss and
Runic Bastion targeting. Other future siege objectives remain separate
follow-up work.

## Attack types

Every unit has one primary attack type in its combat profile. Attack type
determines which targeting, movement, synergy and animation rules apply. A
unit may also have a separate special action, but a special action does not
silently change its primary attack type.

The enum is intentionally extensible. Future categories may include airborne
attacks, burrowing attacks or other movement-specific combat models. A new
category must define its targeting, movement, damage, synergy and presentation
rules before a unit is assigned to it.

| Attack type | Current units | Core behavior |
|---|---|---|
| **MELEE** | Defender, Goblin Raging Boar | Approaches an enemy, establishes contact range, and attacks the selected unit directly. Participates in melee synergy. |
| **RANGED** | Goblin Slingmaster | Approaches until a visible target is in attack range, then holds its current position and attacks without backpedaling to restore maximum range. Does not participate in melee synergy. |
| **CONTACT_EXPLOSIVE** | Demolisher | Immediately detonates on a valid unit contact. At a fortress or Runic Bastion front face, performs a forward throw before detonating. It is not a normal melee attacker and does not participate in melee synergy. |
| **SIEGE_MISSION** | Sapper | Selects the nearest hostile boss, Runic Bastion, or fortress along its route; attacks with its single bomb and returns to refill. It does not participate in unit melee synergy unless a future combat profile explicitly grants it a separate melee attack. |

The attack type is a domain rule and must be available to targeting,
movement, combat resolution, event generation and presentation. Renderers
must select animations from the resolved action and attack type, not infer the
type from the bitmap or unit name.

Ranged units do not retreat to maintain an exact maximum attack distance. If a
target is in range, the unit holds its current world position and attacks. If a
target moves behind and out of range, the unit releases it and resumes its
forward route or selects another valid target. Target acquisition must honor at
least the unit's configured attack range so it can stop before approaching
closer than necessary.

On fortress or Runic Bastion contact, the Demolisher enters `THROWING`
immediately, advances 90 world units over 0.5 seconds, then applies its
explosion at the new position. Unit contact skips this action and detonates in
the same simulation step. Presentation uses the forward-lunge window from the
full attack source and starts the standalone explosion as soon as the
simulation emits `ExplosionTriggered`.

## Melee synergy

Melee synergy is a shared combat rule, not a Defender-only rule. It applies to
every unit type whose combat profile is marked as a melee attacker. The initial
melee attackers are **Defender** and **Goblin Raging Boar**. Additional melee
units can opt into the same profile in the future without changing the synergy
algorithm. A unit with a separate mission, such as a Sapper's fortress-bomb
run, does not participate unless its combat profile explicitly includes melee
unit attacks.

For one target, let `N` be the number of living allied melee attackers that
are simultaneously engaged with that target. Each participating melee
attacker's damage is multiplied by:

```text
min(1 + (N - 1) * 0.35, 2.0)
```

This gives the following progression:

| Engaged direct attackers | Damage multiplier |
|---:|---:|
| 1 | 1.00x |
| 2 | 1.35x |
| 3 | 1.70x |
| 4 or more | 2.00x |

The rule has these constraints:

- only living melee allies attacking the same target count;
- a unit must be in its melee-contact engagement range with that target;
- all participating attackers must keep the same target identity for the
  current combat decision;
- the multiplier applies to each participating attack, not only to the first
  attacker;
- ranged attackers and Demolishers do not participate unless their future
  combat profile explicitly opts into melee synergy;
- the maximum multiplier is `2.0x`;
- combat events expose the base damage, synergy count, multiplier and final
  damage so the rule is testable and visible to feedback systems.

## Attack-group formation and joining

An attack group also has a presentation and spacing contract. Group members
must remain individually readable:

- units must not occupy exactly the same screen position;
- partial sprite overlap is allowed when it matches the battlefield depth and
  keeps the formation compact;
- one unit must never obscure almost the entire sprite of another unit;
- units must not form a long, widely separated line while still being shown as
  one attack group;
- spacing is resolved in world space before rendering, so the same group
  remains readable at different viewport sizes;
- the renderer must preserve each member's bottom anchor and draw order, rather
  than solving overlap by shrinking or hiding sprites.

The group is dynamic. When a compatible allied unit reaches an active group's
engagement area and selects the same target, it joins that group instead of
creating a parallel group. Its target, attack profile and synergy contribution
are then resolved as part of the existing group on the next combat decision.
When the unit leaves the engagement area, loses compatibility, changes target
or is defeated, it leaves the group.

Joining a group must not teleport a unit or push an existing unit backward.
The movement system gradually resolves the new member's position into a
readable slot while the combat system adds it to the group's member set.

## Damage ranges

Units do not use one fixed damage value for every successful attack. Each unit
configuration exposes a damage range derived from its balance value. The
initial rule is `90%..110%` of the configured base damage, rounded to integer
damage and clamped to at least `1`.

For a base damage of `30`, the possible roll is therefore:

```text
27..33
```

The combat pipeline applies the range in this order:

```text
damage range roll
  → target-specific incoming modifiers
  → melee synergy multiplier
  → final integer damage
```

The roll is generated by the simulation's seeded random source. It must not
depend on collection order, renderer frames or wall-clock time. The resolved
roll is included in the attack event so a replay and a test can reproduce the
same result. Two otherwise identical melee units can therefore eventually
produce different outcomes, while equal simultaneous attacks are still
resolved from the same pre-hit state.

## Current unit interaction matrix

Ordinary units cannot acquire or damage a Sapper. A Demolisher blast also
ignores Sappers; the Sapper's own contact slowdown remains a movement effect,
not an attack.

| Unit \ Target | Defender | Sapper | Demolisher | Goblin Raging Boar | Goblin Slingmaster |
|---|---|---|---|---|---|
| **Defender** | Standard contact combat. Multiple allied Defenders can receive synergy when attacking the same target. | Ignores Sapper as an attack target; contact only slows the Sapper. | Contact triggers Demolisher explosion; Defender is damaged or killed and Demolisher is destroyed. | Standard contact combat. | Defender advances; Slingmaster attacks from range when visible and in range. |
| **Sapper** | Neither attacks the other; contact may slow the Sapper. | Neither attacks the other; overlapping Sappers only apply contact slowdown. | Sapper ignores Demolisher; the Demolisher neither targets nor damages Sapper. | Sapper does not attack units; Boar ignores it, though contact slows the Sapper. | Sapper does not attack units; Slingmaster ignores it. |
| **Demolisher** | Contact triggers an explosion damaging Defender and destroying Demolisher. | Ignores Sapper as a target, and its blast does not damage Sapper. | Contact triggers an explosion affecting both Demolishers and nearby units. | Contact triggers Demolisher explosion; Boar is damaged or killed. | Slingmaster can attack from range; contact with Slingmaster alone does not trigger the explosion. |
| **Goblin Raging Boar** | Standard contact combat. | Ignores Sapper as an attack target; contact only slows the Sapper. | Contact triggers Demolisher explosion; Boar is damaged or killed. | Standard contact combat. | Boar advances; Slingmaster attacks from range when visible and in range. |
| **Goblin Slingmaster** | Attacks from range when Defender is visible and in range; does not need contact. | Ignores Sapper as an attack target. | Attacks Demolisher from range; does not trigger it by contact alone. | Attacks Boar from range when visible and in range. | Both use ranged attacks against visible targets. |

## Future proposal: terrain-aware unit attacks

**Status:** proposal, not approved.

**Naturalniejsze ataki jednostek na stromych stokach** — uwzględniać różnicę
wysokości przeciwników przy określaniu zasięgu i rozpoczęcia ataku, a gdy
jest zbyt duża, pozwolić jednostce podejść po terenie do czytelniejszej
pozycji starcia. Zachować zakotwiczenie stóp do terenu; rozważyć efekty
trafienia przy celu i osobne animacje ataku pod górę/w dół.
