# CampaignSystem

## Status

**Campaign progression and spell milestone rewards are implemented locally.**
Campaign selection is authorized by persisted progress, and only a canonical
`MatchEvent.MatchFinished` with `MatchOutcome.BLUE_WIN` records a completion.
Haste is awarded for level 5 and available from level 6; Heal is awarded for
level 7 and available from level 8. TutorialSystem, a dedicated result-screen
flow, cloud sync, and legacy-install migration are not implemented.

Detailed decisions and scope are recorded in
[`campaign-milestones-plan.md`](campaign-milestones-plan.md); execution
instructions are in
[`campaign-milestones-implementation-prompt.md`](campaign-milestones-implementation-prompt.md).

## Ownership and boundaries

| Responsibility | Owner |
|---|---|
| Static campaign level definitions, registry, and milestone reward catalog | `game-campaign` |
| Progress contracts, level selection/completion use cases, entitlement projection | `game-application.application.campaign` |
| Atomic local progress persistence | `android-app.persistence.DataStoreCampaignProgressRepository` |
| Campaign selector rendering and user input | `CampaignScreen` / `CampaignViewModel` |
| Match runtime and canonical finish event | existing match application/simulation |
| Player profile and committed per-level selection | PlayerProgressionSystem / LoadoutSystem |
| Tutorial content and tutorial state | future, game-wide TutorialSystem |

`game-application` depends on `game-campaign` and match contracts. Core modules
do not import Android. Campaign progress is never copied into simulation
runtime state, `MatchSession`, or replay data. The player profile does not
persist a second copy of campaign spell unlocks.

## Definitions and progress state

`CampaignLevelRegistry` remains the only level registry.
`CampaignMilestoneRegistry` in `game-campaign` is the source for campaign spell
milestones and the availability thresholds of milestone spells:

| Milestone ID | Winning completion | First available level | Granted spell | Preferred in a new default loadout |
|---|---:|---:|---|---|
| `campaign_spell_haste` | 5 | 6 | `HASTE` | Yes |
| `campaign_spell_heal` | 7 | 8 | `HEAL` | Yes |

Definitions are immutable and use stable IDs. The catalog is extensible for
future campaign rewards; it does not contain tutorial fields.

`CampaignProgress` is the durable, immutable record:

- `highestUnlockedLevel`, initialized to `1` and kept within the registry;
- `completedLevels`;
- `appliedMilestoneIds`.

Missing data initializes once to the initial state. Partial, malformed,
unknown-milestone, and unsupported-version data is a typed failure; it is not
replaced with defaults and cannot expose all levels.

## Application contract

`CampaignSystem` provides:

- `progress()` — reads/initializes progress and validates it against the
  current level and milestone registries;
- `selectLevel(level)` — returns a registered level definition only if the
  level is unlocked;
- `completeLevel(level, MatchEvent.MatchFinished)` — records a valid win and
  its rewards;
- `loadoutRewards(level)` — projects earned spell grants and eligible default
  preferences for a selected level.

Invalid and locked selections are typed rejections. Storage errors and invalid
persisted progress are typed failures. No level lookup may treat
`CampaignLevelDefinition.forLevel` clamping as authorization.

Only `BLUE_WIN` changes progress. `RED_WIN`, `DRAW`, and `SURRENDER` return an
explicit no-progress result. A missing/aborted match does not emit a finish
event and therefore cannot advance the campaign.

For a winning completion of an unlocked level, the application update:

1. marks the level completed;
2. monotonically unlocks the following level, capped at the maximum;
3. adds all matching, unapplied milestone IDs;
4. persists all changes through one atomic repository update.

The set-based update is idempotent. Re-delivering a finish event does not
duplicate rewards or lower progress. A completion of an already completed
level can apply a matching milestone that is not yet present in the record.

## Persistence

`DataStoreCampaignProgressRepository` uses the existing AndroidX Preferences
DataStore backend with a separate `campaign.*` namespace and schema version.
Profile, loadout, and campaign values remain distinct but are preserved by
each atomic DataStore update.

The adapter initializes only when all campaign keys are absent. It returns
explicit failures for partial data, invalid values, unsupported schema, I/O,
and corruption. Campaign completion, level unlock, and milestone IDs share a
single record update; no partial profile unlock write is required.

No campaign persistence is stored in `CampaignUiState`, match snapshots,
runtime simulation, or replay. Returning to the campaign screen reloads the
repository.

## Integration flow

```text
Dashboard
  → CampaignScreen / CampaignViewModel
  → CampaignSystem: read progress and validate selection
  → LoadoutSystem: campaign-aware progression snapshot
  → MatchScreen → LocalMatchGateway → MatchSession / simulation
  → MatchEvent.MatchFinished
  → CampaignSystem: atomic completion + milestone update
  → CampaignScreen reloads durable progress on return
```

The match screen forwards finish events with their selected campaign level.
Demo-mode matches are excluded. The callback uses the event, not
`MatchSnapshot.outcome`, as the completion trigger. Repeated delivery is
additionally safe through idempotent progress sets.

The existing simplified result presentation remains on the match screen.
`ResultScreen` is still a placeholder; no new result navigation or reward
animation is part of this implementation. A campaign persistence failure is
reported explicitly and can be retried by completing the level again.

## Loadout entitlement projection

`CampaignAwareLoadoutProgressionPort` combines the existing player profile
snapshot with earned campaign grants:

- `unlockedSpellTypes` is the union of profile unlocks and earned campaign
  spell rewards;
- `defaultSpellTypes` is a derived, non-persisted preference for spells whose
  milestone has been earned and whose availability level has been reached.

`MatchLoadout.defaults` places those eligible preferences first, then fills
the remaining slots in the established spell order. This only affects a
new/default selection. An existing saved per-level loadout is normalized by
the existing rules and is not appended to or overwritten by the milestone.
Ordinary availability checks still apply, so entitlement does not make a spell
usable on a level where it is unavailable.

## Tutorials and other non-goals

Campaign milestones contain no tutorial configuration and CampaignProgress
stores no tutorial flags. The existing level-one tutorial parameters remain
untouched. A future TutorialSystem will own tutorial observation, state,
presentation, and progression across the game; it may consume campaign events
or milestone IDs through an explicit contract.

CampaignSystem does not own player gold, purchases, ammunition/spell stock
consumption, shop behavior, spell casting/effects, account sync, or legacy
save migration. It does not add controls to `MainActivity`.

## Tests and implementation files

Focused tests cover milestone definitions and thresholds, win and non-win
outcomes, locked/invalid selection, monotonic/idempotent updates, terminal
level bounds, progress read failures, campaign-aware loadout projection,
default spell preference, saved-loadout preservation, event forwarding/demo
exclusion, and DataStore initialization/round-trip/failure behavior.

Key files:

| Area | Files |
|---|---|
| Milestones and level availability | `game-campaign/.../CampaignMilestoneDefinition.kt`, `CampaignLevelDefinition.kt` |
| Progress and application use cases | `game-application/.../application/campaign/CampaignProgressRepository.kt`, `CampaignSystem.kt` |
| Loadout projection | `game-application/.../progression/CampaignAwareLoadoutProgressionPort.kt`, `application/loadout/LoadoutContracts.kt`, `MatchLoadout.kt` |
| Persistence | `android-app/.../persistence/DataStoreCampaignProgressRepository.kt` |
| UI and event wiring | `android-app/.../screens/campaign/`, `ui/MatchViewModel.kt`, `MainActivity.kt` |
| Plan and execution prompt | `campaign-milestones-plan.md`, `campaign-milestones-implementation-prompt.md` |
