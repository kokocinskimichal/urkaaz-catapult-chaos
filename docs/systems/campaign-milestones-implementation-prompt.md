# Prompt: implement Campaign milestone rewards

## Task

Implement the approved Campaign milestone rewards described in
[`campaign-milestones-plan.md`](campaign-milestones-plan.md) and integrate them
with the existing campaign selector, match completion event, player
progression read model, and loadout defaults. Do not stop at a proposal:
implement the code, focused tests, and directly related documentation in the
current repository.

## Confirmed requirements — do not ask again

- Award Haste only after a `BLUE_WIN` on campaign level 5. Make it available
  from level 6.
- Award Heal only after a `BLUE_WIN` on campaign level 7. Make it available
  from level 8.
- Only `MatchEvent.MatchFinished` is the canonical completion signal. A
  snapshot outcome alone must not independently trigger another completion.
- Only BLUE wins complete a campaign level and unlock the next level.
  `RED_WIN`, `DRAW`, `SURRENDER`, interruption, and missing results do not
  progress the campaign.
- Persist completed levels, highest unlocked level, and applied milestone
  IDs in one versioned local campaign-progress record.
- Completion, next-level unlock, and matching rewards must be committed
  atomically and idempotently. Repository failures are explicit and retryable.
- Campaign rewards are entitlements owned by CampaignProgress. The loadout
  progression read model unions them with profile spell unlocks; do not write
  campaign grants into a second profile unlock list.
- Milestones specify whether their reward is preferred in a new default
  loadout. Apply that preference only when no selection exists for that level;
  never overwrite or append to an existing saved loadout.
- Keep level availability separate from player entitlement. A selected spell
  must satisfy both existing LoadoutSystem checks.
- TutorialSystem is deferred to later cross-game work. Do not implement
  tutorial metadata, flags, UI, seen/completed state, or tutorial progress.
- Do not implement a new ResultScreen flow or replace the existing match
  result presentation.
- Do not implement a shop, economy, rewards beyond the configured spells,
  stock reconciliation, cloud sync, or legacy data migration.
- Do not commit, push, switch branches, reset, or discard user changes.

## Repository investigation

Read the current code and tests before editing. The existing architecture
includes:

- `game-campaign`: `CampaignLevelDefinition` and
  `CampaignLevelRegistry`; level definitions currently gate Haste from 6 and
  Heal from 8, while `forLevel` clamps invalid values.
- `game-contracts`: immutable `MatchEvent.MatchFinished` and `MatchOutcome`.
- `game-application`: local match gateway/session and asynchronous Loadout/
  PlayerProgression ports, but no CampaignSystem implementation yet.
- `android-app`: shared AndroidX Preferences DataStore, a test-only campaign
  selector that currently unlocks every level, and match event consumption in
  `MatchViewModel`.
- `docs/systems/campaign-system.md`, `player-progression-system.md`,
  `loadout-system.md`, `architecture.md`, `AI_PROJECT_MAP.md`, and
  `docs/PROJECT_KNOWLEDGE.md`.

Treat documentation as intended design, not proof of working behavior.
Preserve pre-existing working-tree changes and integrate with them rather
than reverting or overwriting them.

## Implementation requirements

### 1. Campaign milestone catalog

- Add immutable milestone definitions and a registry in `game-campaign`; do
  not create a second level registry or a new Gradle module.
- Use stable milestone persistence IDs. Validate non-blank IDs, valid
  completion/availability levels, and known non-special spell IDs.
- Configure Haste (complete 5, available 6, preferred for new defaults) and
  Heal (complete 7, available 8, preferred for new defaults).
- Keep level availability and player unlock entitlement separate. Make
  Haste/Heal availability derive consistently from the same milestone
  definitions rather than maintaining duplicate thresholds.
- Do not add tutorial fields or alter existing level-1 tutorial behavior.
- Add tests for catalog contents, validation, and Haste/Heal availability at
  levels immediately before, at, and after each threshold.

### 2. Campaign progress contracts and application use cases

- Add immutable campaign progress, repository result/failure types, and
  application use cases under `game-application/application/campaign`.
- Add `game-campaign` as an application dependency if required. Keep Android
  APIs and DataStore types out of core modules.
- Initialize absent data to highest unlocked level 1, with no completed
  levels or applied milestones. Reject malformed or unsupported records
  explicitly.
- Validate requested levels against `1..MAX_LEVELS` and the actual registry;
  do not use `CampaignLevelDefinition.forLevel(...coerceIn...)` as an
  authorization check.
- Expose an immutable progress read and a typed level-selection result. A
  locked or unknown level must not produce a definition or scenario.
- Implement completion from the actual `MatchEvent.MatchFinished` plus the
  level context. Ignore non-`BLUE_WIN` outcomes without changing state.
- For BLUE wins, update completed levels, highest unlocked level, and every
  matching un-applied milestone ID in a single repository operation.
- Replaying a completion, retrying after a storage failure, or reporting a
  previously completed level must be safe and monotonic. Cap next-level
  unlock at `MAX_LEVELS`.
- Return typed results for completion, no progress, locked/invalid level, and
  storage failure. Do not hide storage errors or substitute defaults after a
  read failure.
- Add unit tests for initial state, selection validation, BLUE_WIN, each
  non-winning outcome, duplicate delivery, an earlier already completed
  level, monotonic progress, max-level behavior, and every repository failure.

### 3. Android persistence

- Implement a versioned `DataStoreCampaignProgressRepository` in
  `android-app` using the existing shared Preferences DataStore instance and
  a campaign-specific key namespace.
- Use one atomic `DataStore.updateData` transaction to persist the complete
  campaign state. Preserve profile and loadout keys.
- Initialize only when all campaign keys are absent. Partial, malformed,
  unsupported-version, and I/O/corruption cases must be returned as explicit
  failures and must not be overwritten by defaults.
- Add adapter tests for first initialization, exact round-trip, atomic
  completion/reward update, duplicate completion, preserving other namespaces,
  partial/corrupt data, unsupported version, and I/O failure where the test
  harness supports it.

### 4. Campaign UI and result-event integration

- Replace the all-levels-unlocked test default in `CampaignUiState` with an
  explicit loading/available/failure state backed by CampaignSystem.
- CampaignScreen must render only levels allowed by durable progress. A
  failure must not enable all levels or navigate to a match.
- Route selection through the CampaignSystem use case; do not rely only on
  disabled buttons for authorization.
- Respect screen lifecycle for asynchronous work, following the existing
  LoadoutScreen scope pattern. Do not block the UI thread.
- Pass the selected campaign level to MatchScreen as before. Demo matches
  must not write campaign progress.
- Surface `MatchEvent.MatchFinished` from `MatchViewModel`/match screen to an
  application callback exactly once per delivered event. The callback should
  call CampaignSystem with the selected campaign level. Do not infer
  completion by polling `MatchSnapshot.outcome`.
- Keep persistence/error policy out of `MainActivity`; it may compose
  dependencies and provide callbacks only.
- Returning to CampaignScreen must reload the persisted progress.
- Do not create or redesign the result screen. Preserve existing match result
  display and back navigation.
- Add focused tests for screen state, disabled/blocked selection, failure
  handling, correct event forwarding, demo exclusion, and returning to updated
  campaign progress.

### 5. Loadout integration

- Add a derived `defaultSpellTypes` (or equivalently precise typed field) to
  the existing `LoadoutProgressionSnapshot`; it must not be persisted in the
  player profile.
- Implement an application-level composition/projection that combines the
  existing `PlayerProgressionUseCases` result with campaign grants for the
  requested level, preserving the existing purchase behavior and typed
  failures.
- Use `CampaignProgress.appliedMilestoneIds` and the canonical milestone
  registry to derive granted spell IDs and default preferences. Unknown
  persisted milestone IDs must be explicit invalid campaign data, not silently
  ignored.
- `MatchLoadout.defaults` should include eligible preferred milestone spells
  first, then fill remaining slots in existing canonical spell order without
  duplicates and within the current limit.
- Existing saved loadout normalization must not append preferred spells to a
  non-empty saved selection. If normalization falls back because no valid
  spell remains, use the same deterministic new-default ordering.
- Add tests proving Haste is granted/defaulted at level 6 after the level 5
  victory, Heal at level 8 after level 7 victory, unearned spells remain
  locked, and persisted selections stay unchanged.

### 6. Documentation and project knowledge

- Update `docs/systems/campaign-system.md` to describe the implemented
  progress/milestone contract, persistence, event integration, errors, and
  tests; distinguish implemented behavior from remaining future work.
- Update `docs/systems/player-progression-system.md` and
  `docs/systems/loadout-system.md` so campaign entitlements and the new-default
  behavior are accurate. Clearly state that tutorials remain deferred to
  TutorialSystem and are not part of milestone persistence.
- Update `architecture.md`, `AI_PROJECT_MAP.md`, and
  `docs/PROJECT_KNOWLEDGE.md` only for durable architecture and current-state
  facts. Do not leave stale statements that all levels are unlocked or that
  milestone authorization is still entirely blocked.
- Keep this prompt and its plan linked from directly related system docs.
- Do not mark the full TutorialSystem, shop, spell gameplay, cloud sync, or
  result-screen UX as implemented.

## Acceptance and validation

The task is complete only when:

- Campaign starts at level 1 and the stored progress survives process/repository
  recreation.
- Only a canonical BLUE_WIN advances progress and grants the correct milestone.
- Completion, level unlock, and reward IDs are atomic, idempotent, and
  monotonic.
- The campaign UI and application use case reject locked/invalid levels even
  if called without the UI.
- Campaign spells are merged into the loadout read model without duplicating
  persisted profile unlocks.
- Haste/Heal are preferred only for a new default loadout and existing saved
  selections remain intact.
- Storage failures remain explicit; no all-unlocked or success-shaped
  fallback exists.
- Tutorials and full result UI remain out of scope.
- All core modules remain Android-independent and layering rules hold.

Run from the repository root:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Do not commit or push. In the completion report, summarize the behavior,
changed files, validation results, remaining boundaries, and confirm the
current branch and absence of a commit/push.
