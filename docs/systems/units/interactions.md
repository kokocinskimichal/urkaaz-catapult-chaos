# Unit interactions

This document is copied from the legacy `GAME_DESIGN.md` unit-interaction
section. The pair in the matrix is unordered: the same rule applies regardless
of which team owns the row or column unit.

## Implemented Sapper/Demolisher role rework

- **Sapper:** at most one active Sapper is allowed at a time. Ordinary enemy
  units can attack it on contact, but contact slows it to 65% of its normal
  movement speed instead of hard-blocking it. Ordinary unit attacks deal 45%
  of their normal damage to the Sapper. The Sapper cannot push a defender
  backward while crossing contact range. This gives defenders a short
  interception window without making the Sapper unable to reach the fortress.
  It can also take damage from catapult projectiles, spells and area effects.
- The Sapper travels to an enemy fortress, Runic Bastion or future obstacle,
  throws a held bomb from the appropriate stand-off distance, then returns to
  its own fortress using normal movement in the opposite direction. After
  returning, it can begin another bomb run.
- The Sapper's bomb deals medium damage to the primary target and half that
  damage to units in the target fortress's garrison. Throwing consumes the bomb
  and the Sapper returns immediately; reaching its own gate refills the bomb
  and restores the Sapper to full health.
- **Demolisher:** remains a slow, one-use suicide unit. It detonates on contact
  with an enemy Defender, Goblin on a Raging Boar, another Demolisher, a
  fortress, a Runic Bastion or a future obstacle. Its explosion deals 300
  damage with a 260 world-unit radius. The unit that triggers the detonation
  receives full explosion damage. Nearby units receive damage reduced by
  distance.
- Demolishers detonate on contact with Sappers. The explosion kills the Sapper
  when it is within the blast radius and continues to damage other units using
  the existing explosion rules.
- Mixed-unit formations are intentionally deferred and are not part of this
  decision.
- Returning Sapper movement uses a mirrored version of the existing run
  animation. A future animation variant should show the Sapper without its
  bomb after throwing and while returning; the gameplay state is implemented,
  but the bomb-less artwork is not yet available.

These rules are implemented for the current unit roster. Exact future obstacle
interactions and the bomb-less artwork remain separate follow-up work.

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
| **RANGED** | Goblin Slingmaster | Keeps distance, attacks a visible target from attack range, and does not participate in melee synergy. |
| **CONTACT_EXPLOSIVE** | Demolisher | Approaches a valid target, detonates on contact, applies area damage, and destroys itself. It is not a normal melee attacker and does not participate in melee synergy. |
| **SIEGE_MISSION** | Sapper | Follows a fortress-bomb mission, attacks the enemy fortress from its stand-off point, consumes its bomb, and returns to refill. It does not participate in unit melee synergy unless a future combat profile explicitly grants it a separate melee attack. |

The attack type is a domain rule and must be available to targeting,
movement, combat resolution, event generation and presentation. Renderers
must select animations from the resolved action and attack type, not infer the
type from the bitmap or unit name.

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

| Unit \ Target | Defender | Sapper | Demolisher | Goblin Raging Boar | Goblin Slingmaster |
|---|---|---|---|---|---|
| **Defender** | Standard contact combat. Multiple allied Defenders can receive synergy when attacking the same target. | Attacks on contact. Sapper moves at 65% speed, receives 45% ordinary unit damage, and cannot push Defender backward. | Contact triggers Demolisher explosion; Defender is damaged or killed and Demolisher is destroyed. | Standard contact combat. | Defender advances; Slingmaster attacks from range when visible and in range. |
| **Sapper** | Attacks are handled by Defender; Sapper is slowed and takes reduced damage. | Neither attacks the other. No hard block; both continue with contact slowdown while overlapping. | Demolisher explodes on contact and kills Sapper within the blast radius. | Boar attacks on contact; Sapper is slowed, takes reduced damage, and cannot push Boar backward. | Slingmaster attacks from range with line of sight; Sapper does not attack back. |
| **Demolisher** | Contact triggers an explosion damaging Defender and destroying Demolisher. | Contact triggers an explosion killing Sapper within the blast radius. | Contact triggers an explosion affecting both Demolishers and nearby units. | Contact triggers an explosion damaging Boar and destroying Demolisher. | Slingmaster can attack from range; contact with Slingmaster alone does not trigger the explosion. |
| **Goblin Raging Boar** | Standard contact combat. | Attacks Sapper on contact; Sapper is slowed and takes reduced damage. | Contact triggers Demolisher explosion; Boar is damaged or killed. | Standard contact combat. | Boar advances; Slingmaster attacks from range when visible and in range. |
| **Goblin Slingmaster** | Attacks from range when Defender is visible and in range; does not need contact. | Attacks Sapper from range with line of sight. | Attacks Demolisher from range; does not trigger it by contact alone. | Attacks Boar from range when visible and in range. | Both use ranged attacks against visible targets. |
