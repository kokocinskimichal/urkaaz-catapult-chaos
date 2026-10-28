# CampaignSystem

## Status

**Campaign progression, spell milestones and level gold rewards are implemented locally.**
Campaign selection is authorized by persisted progress, and only a canonical
`MatchEvent.MatchFinished` with `MatchOutcome.BLUE_WIN` records a completion.
Haste is awarded for level 5 and available from level 6; Heal is awarded for
level 7 and available from level 8. TutorialSystem, a dedicated result-screen
flow, cloud sync, and legacy-install migration are not implemented.
The injected `RewardSettlementUseCase` additionally evaluates the match reward
result and atomically saves gold, completion, unlocks, milestone IDs and an
idempotent receipt. Current level rewards are documented in
[`reward-system.md`](reward-system.md#status-i-cel).

Detailed decisions and scope are recorded in
[`campaign-milestones-plan.md`](campaign-milestones-plan.md); execution
instructions are in
[`campaign-milestones-implementation-prompt.md`](campaign-milestones-implementation-prompt.md).

## Ownership and boundaries

| Responsibility | Owner |
|---|---|
| Static campaign level definitions, registry, and shared reward catalog | `game-campaign` |
| Progress contracts, level selection/completion use cases, entitlement projection | `game-application.application.campaign` |
| Atomic local reward settlement and receipt persistence | `android-app.persistence.DataStoreRewardSettlementAdapter` |
| Campaign progress reads and non-reward compatibility persistence | `android-app.persistence.DataStoreCampaignProgressRepository` |
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
`CampaignRewardRegistry` in `game-campaign` is the source for per-level gold
reward definitions and existing campaign spell milestones. Its milestone
catalog is the source for spell unlock thresholds:

| Milestone ID | Winning completion | First available level | Granted spell | Preferred in a new default loadout |
|---|---:|---:|---|---|
| `campaign_spell_haste` | 5 | 6 | `HASTE` | Yes |
| `campaign_spell_heal` | 7 | 8 | `HEAL` | Yes |

Definitions are immutable and use stable IDs. The shared catalog is extensible
for future campaign rewards; it does not contain tutorial fields. Level reward
values and conditions are listed in the
[RewardSystem design](reward-system.md#status-i-cel).

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
  level is unlocked and not completed;
- `completeLevel(level, MatchEvent.MatchFinished)` — records a valid win and
  its rewards;
- `loadoutRewards(level)` — projects earned spell grants and eligible default
  preferences for a selected level.

Invalid, locked and completed selections are typed rejections. The campaign
screen marks completed levels and disables their controls. Storage errors and
invalid persisted progress are typed failures. No level lookup may treat
`CampaignLevelDefinition.forLevel` clamping as authorization.

Only `BLUE_WIN` changes progress. `RED_WIN`, `DRAW`, and `SURRENDER` return an
explicit no-progress result. A missing/aborted match does not emit a finish
event and therefore cannot advance the campaign.

In the production Android wiring, a winning completion is settled through
`RewardSettlementUseCase`, which:

1. marks the level completed;
2. monotonically unlocks the following level, capped at the maximum;
3. adds all matching, unapplied milestone IDs;
4. credits the reward result to the existing profile gold balance and records
   an idempotent receipt;
5. persists all changes through one atomic DataStore update.

Re-delivering the same match result returns the existing receipt and does not
credit gold again. A previously completed level without a receipt is not paid
retroactively, and completed levels cannot be selected again. Failures are
explicit and the result popup can retry persistence without replaying the
match.

## Persistence

`DataStoreCampaignProgressRepository` uses the existing AndroidX Preferences
DataStore backend with a separate `campaign.*` namespace and schema version.
The reward settlement adapter reads and updates the same DataStore atomically;
profile, loadout, campaign and receipt values remain distinct namespaces.

The adapter initializes only when all campaign keys are absent. It returns
explicit failures for partial data, invalid values, unsupported schema, I/O,
and corruption. Campaign completion, level unlock, and milestone IDs share a
single record update with the profile gold balance and receipt; no split
campaign/profile payout write is required.

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
  → MatchSession event observer / RewardTracker
  → MatchEvent.MatchFinished → CampaignSystem / RewardSettlementUseCase
  → atomic gold + completion + unlock + milestone + receipt update
  → existing match result popup shows the committed reward
  → CampaignScreen reloads durable progress on return
```

The match screen forwards finish events with their selected campaign level.
Demo-mode matches are excluded. The callback uses the event, not `MatchSnapshot.outcome`, as the completion
trigger. Repeated delivery is safe through the persisted settlement receipt.

The existing simplified result presentation remains on the match screen.
`ResultScreen` is still a placeholder; no new result navigation or reward
art assets are part of this implementation. Hit-based reward conditions show
temporary floating feedback during the match; final-only conditions appear in
the committed popup breakdown. A settlement persistence failure is reported
explicitly and can be retried from the result popup without replaying the
match.

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

Focused tests cover milestone and reward definitions, win and non-win
outcomes, locked/completed/invalid selection, idempotent settlement,
terminal-level bounds, reward tracking and metadata, campaign-aware loadout
projection, default spell preference, saved-loadout preservation, event
forwarding/demo exclusion, and DataStore initialization/round-trip/failure
behavior.

Key files:

| Area | Files |
|---|---|
| Milestones, level rewards, and level availability | `game-campaign/.../CampaignMilestoneDefinition.kt`, `CampaignRewardDefinition.kt`, `CampaignLevelDefinition.kt` |
| Progress and application use cases | `game-application/.../application/campaign/CampaignProgressRepository.kt`, `CampaignSystem.kt`, `application/reward/` |
| Loadout projection | `game-application/.../progression/CampaignAwareLoadoutProgressionPort.kt`, `application/loadout/LoadoutContracts.kt`, `MatchLoadout.kt` |
| Persistence | `android-app/.../persistence/DataStoreCampaignProgressRepository.kt`, `DataStoreRewardSettlementAdapter.kt` |
| UI and event wiring | `android-app/.../screens/campaign/`, `ui/MatchViewModel.kt`, match result popup, `MainActivity.kt` |
| Plan and execution prompt | `campaign-milestones-plan.md`, `campaign-milestones-implementation-prompt.md` |
