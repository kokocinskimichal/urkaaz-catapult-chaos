# Campaign milestone rewards

## Status

**Implemented and validated.** This plan records the approved scope for
durable, replay-safe campaign progress and milestone spell rewards. It does
not replace `CampaignLevelDefinition`, `CampaignLevelRegistry`, the player
profile, or the separate future `TutorialSystem`.

## Problem

`CampaignLevelDefinition` currently makes Haste available from level 6 and
Heal from level 8, but the app has no authoritative campaign completion
contract or persistent unlock state. The campaign selector currently exposes
all levels, and the player profile cannot distinguish a spell unlocked by a
campaign reward from one unlocked by another progression system.

`MatchEvent.MatchFinished` and `MatchOutcome` already provide the canonical
match completion signal. The match screen consumes those events, but no
campaign use case currently persists their result. The result screen remains
placeholder UI.

## Approved decisions

| Concern | Decision |
|---|---|
| Reward trigger | Completing level 5 grants Haste; completing level 7 grants Heal. Merely opening levels 6 or 8 does not grant either spell. |
| Success condition | Only `MatchOutcome.BLUE_WIN` completes a campaign level and unlocks the next one. `RED_WIN`, `DRAW`, `SURRENDER`, interruption, and missing results do not progress the campaign. |
| Progress owner | `game-application` owns CampaignSystem use cases and immutable progress contracts. `game-campaign` owns the milestone definitions alongside campaign definitions. |
| Durable state | Store completed level IDs, highest unlocked level, and applied milestone reward IDs. New progress starts at level 1. |
| Atomicity and retries | Completion, next-level unlock, and rewards are one atomic repository update. Repeated delivery is idempotent; storage errors are typed failures and can be retried. |
| Reward projection | Campaign rewards remain in CampaignProgress. The loadout read model unions campaign spell grants with profile unlocks; it does not create a second persisted spell-unlock record. |
| New loadouts | A milestone may request its spell be preferred in the default loadout from its availability level. This preference applies only when no saved loadout exists for that level. Existing saved loadouts are preserved. |
| Availability | Milestone definitions specify the completion level and the first campaign level where the spell is available. Haste is available from 6 and Heal from 8. Level scenario availability remains distinct from the player's actual unlock entitlement. |
| Tutorials | Excluded completely from this implementation: no tutorial metadata in milestones, flags, tutorial UI, or tutorial progress. The future cross-game TutorialSystem may consume campaign milestone/completion signals. |
| Result presentation | Persist completion from the canonical match-finished event. Do not implement a new result screen or replace the existing match result presentation in this scope. |
| Persistence | Local AndroidX Preferences DataStore adapter, sharing the existing DataStore backend while retaining a separate, versioned campaign namespace. No cloud sync or legacy-install migration. |

## Architecture

```text
CampaignLevelRegistry + CampaignMilestoneRegistry
                  │
                  ▼
game-application CampaignSystem
  ├── read/validate selectable campaign progress
  ├── apply BLUE_WIN completion and milestone rewards
  └── expose campaign spell grants for the loadout projection
                  │
                  ▼
CampaignProgressRepository port
                  │
                  ▼
android-app DataStore campaign adapter

MatchEvent.MatchFinished + selected campaign level
                  │
                  └──> CampaignSystem.completeLevel(...)

Player profile + CampaignProgress rewards
                  │
                  └──> LoadoutProgressionSnapshot read model
                         ├── union of unlocked spells
                         └── default-spell preference for new loadouts
```

`game-application` may depend on `game-campaign` and existing match contracts;
it must not depend on Android. Campaign progress and reward persistence do not
enter `MatchSession`, simulation state, or replay data. Android screens send
intent through the application boundary and render immutable results.

## State and invariants

`CampaignProgress` is an immutable value with:

- `highestUnlockedLevel`, in `1..CampaignLevelDefinition.MAX_LEVELS`;
- unique `completedLevels`;
- unique, stable `appliedMilestoneIds`.

The repository initializes missing campaign data to level 1 only when no
campaign data exists. Partial, malformed, or unsupported-version data is a
typed failure, never an all-levels-unlocked fallback.

On a valid `BLUE_WIN` for an unlocked campaign level:

1. mark the level completed;
2. monotonically unlock the next level, capped at the campaign maximum;
3. apply every matching, not-yet-applied milestone;
4. persist the updated progress and milestone IDs atomically.

Repeated completion cannot lower progress or grant a milestone twice. A
non-winning outcome returns an explicit non-progress result. Invalid or locked
levels are rejected before looking up a clamped level definition.

Milestone IDs are stable persistence identifiers. Each definition includes
the completed level, first available level, spell reward, and whether the
rewarded spell should be preferred in a newly generated default loadout.
Haste and Heal milestone values are:

| Milestone | Complete level | First available level | Spell | Prefer in a new default loadout |
|---|---:|---:|---|---|
| `campaign_spell_haste` | 5 | 6 | `HASTE` | Yes |
| `campaign_spell_heal` | 7 | 8 | `HEAL` | Yes |

## UI and loadout integration

- CampaignScreen reads persisted progress and enables only levels at or below
  `highestUnlockedLevel`; it must not initialize to the maximum level.
- Selecting a level is validated by CampaignSystem, not only by a disabled UI
  control.
- Match completion is processed from `MatchEvent.MatchFinished`, paired with
  the campaign level context that launched the match. Demo matches do not
  grant campaign progress.
- Returning to CampaignScreen reloads progress from the repository.
- The loadout progression read model unions campaign-granted spells with
  profile unlocks and exposes preferred default spells for the requested
  level.
- Loadout defaults use eligible preferred spells before filling remaining
  slots in the existing canonical order. The preference is used only when
  that level has no persisted selection; stored selections are normalized by
  existing rules without appending new rewards.
- Level availability remains a separate check from progression entitlement.
  A spell must be both unlocked and available in the selected level.
- Storage failures remain visible and block level selection or match start
  where required; no success-shaped fallback is allowed.

## Out of scope

- Tutorial content, tutorial flags, seen/completed state, and tutorial UI.
- A new ResultScreen flow, reward animation, economy, gold, shop transaction,
  stock consumption, cloud sync, or importing legacy app data.
- Gameplay spell casting or spell-effect implementation.
- Replacing or duplicating campaign level definitions and registries.
- Changing the existing level balance except to make Haste/Heal availability
  derive consistently from the campaign milestone definitions.

## Implementation and verification

Detailed execution instructions are in
[`campaign-milestones-implementation-prompt.md`](campaign-milestones-implementation-prompt.md).

Required coverage includes milestone definition invariants, win/loss outcome
handling, sequential and capped unlocking, idempotent atomic persistence,
invalid/corrupt storage, loadout spell projection and new-default preference,
preservation of saved selections, locked-level rejection, and match-event
integration with demo-mode exclusion.

The implementation passed the repository validation gate:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

The Gradle command completed successfully. The working tree remains
uncommitted; no commit or push was performed.
