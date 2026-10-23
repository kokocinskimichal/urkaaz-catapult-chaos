# Implementation Prompt: Match Result Popup

**Status:** Completed historical prompt. It captures the original first-pass
scope; approved outcome plaques and biome illustrations were integrated
afterward. See `docs/project/knowledge.md` for the current implementation.

Implement the first version of the Urkaaaz Victory/Defeat popup in the
Android app. Follow `docs/systems/match-result-popup-implementation-plan.md`
as the implementation scope and acceptance checklist.

## Requirements

- Build a popup overlay over the finished match, not a separate result
  screen. The battlefield must remain visible behind the popup, dimmed by an
  in-root scrim. The frame's transparent illustration window should reveal
  the dimmed match until artwork is added later.
- Use only these existing assets:
  - Panel:
    `R.drawable.match_result_popup_panel`
  - Gold button:
    `R.drawable.loadout_start_match_button`
  - Red button:
    `R.drawable.ui_button_red_m`
  - Dark button:
    `R.drawable.ui_button_dark_m`
- Render outcome, level/biome, status, and button labels as live Android text.
  Use `UiTypography.applyButtonTypeface` so all button labels use Cinzel
  SemiBold.
- Do not add placeholder illustrations, icons, rewards, gold amounts,
  unlocks, separate title-plaque artwork, or other new visual assets.
- Leave `ResultScreen` and `AppScreen.Result` unused; do not navigate away
  from the match when the popup is shown.

## Existing code and contracts

- `MatchScreenController` owns the match `FrameLayout`, renders the outcome,
  receives the campaign-completion callback, currently shows a single
  finished-match action, and already gates next-level access.
- `MatchViewModel` emits the match finish event and dispatches
  `MatchUiAction.RestartMatch`.
- `CampaignCompletionPresenter` persists campaign completion and reports
  whether it was recorded.
- `MainActivity` wires campaign return through `onBackClicked` and next-level
  navigation through `onNextLevelClicked`; the latter opens the next level's
  Loadout.
- `UiTypography` exposes the shared Cinzel SemiBold typeface helper.
- The reusable panel PNG is in
  `assets_src/match/result-ui/match_result_popup_panel.png` and Android
  `drawable-nodpi`. Its 2400 x 1680 RGBA image was upscaled from a
  1499 x 1049 preview; do not resize or modify it as part of this task.

Before coding, inspect `AGENTS.md`, `docs/architecture/architecture.md`,
`docs/project/knowledge.md`, `docs/architecture/implementation-rules.md`,
`docs/architecture/project-map.md`, and the current implementation of the files above.
Follow any repository-local instructions.

## UI behavior

1. Add a dedicated match-result popup view in `android-app`. Pass it an
   immutable presentation state and callbacks; do not let it mutate match or
   campaign state.
2. Add a translucent full-screen scrim and the popup as the topmost children
   of the existing match root. The scrim must consume touches so controls
   underneath cannot be activated after the match ends.
3. Keep the popup responsive in landscape. Preserve the panel's 10:7 aspect
   ratio, use its 600 x 420 dp target as a maximum, and constrain it to the
   available width/height with safe margins. Layer live content over the
   panel; do not scale the PNG non-uniformly.
4. Place the dynamic `LEVEL X · BIOME` line in the blank top plaque area and
   the live outcome title in the blank band above the illustration window.
   Obtain level/biome data from existing campaign/map definitions, not
   hardcoded mockup text.
5. Place two M-size actions side by side when both are available. Center a
   single action when only one is available. Use Cinzel SemiBold with
   contrasting text colors: dark text on gold, warm light text on red/dark.
6. Hide the old center restart/next button while the popup is visible; avoid
   duplicate or overlapping actions.

### Victory

- Show `VICTORY!`.
- If another campaign level exists and completion is persisted, show gold
  `NEXT LEVEL` and dark `EXIT TO CAMPAIGN`.
- Keep `NEXT LEVEL` disabled/unavailable while persistence is pending; retain
  the existing saving-status behavior.
- On the final campaign level, show only gold `EXIT TO CAMPAIGN`.
- If persistence fails, do not offer `NEXT LEVEL`; expose a retry action and
  campaign return, preserving the existing ability to restart.

### Defeat

- Show `DEFEAT`.
- Show red `RETRY` and dark `EXIT TO CAMPAIGN`.
- `RETRY` restarts the current level through the existing match command; it
  must not advance campaign progression.

### Demo mode

- Never show `NEXT LEVEL` or imply campaign progress for a demo match.
- Show dark `BACK TO DASHBOARD` and use the existing navigation callback;
  do not label a Dashboard return as `EXIT TO CAMPAIGN`.

## Architecture and change boundaries

- Keep the feature in `android-app`; do not add Android dependencies to core
  modules or add new gameplay/campaign rules.
- Reuse existing outcome and completion state. Do not infer victory from
  artwork, create duplicate campaign state, or bypass
  `CampaignCompletionPresenter`.
- Prefer a small view/state mapper and focused callbacks over adding fields to
  `MainActivity`, `MatchViewModel`, or a new global singleton.
- Do not extend `LoadoutAssetCatalog` with result-screen assets. A tiny
  result-specific resource mapping is acceptable only if it matches existing
  catalog conventions and provides useful separation.
- Preserve existing pause/resume and match lifecycle behavior. Ensure the
  popup is refreshed when the completion callback changes pending status to
  success or failure.
- Keep reward and unlock areas absent until their systems and artwork exist.

## Tests and validation

Add focused tests, following current project patterns, for:

- Victory with a next level: `NEXT LEVEL` is available only after successful
  completion persistence.
- Victory at the campaign boundary: no `NEXT LEVEL`; campaign return remains.
- Pending or failed completion persistence: no premature next-level action.
- Defeat: retry and campaign return are shown; retry targets the current
  level.
- Demo mode: no campaign progression action or misleading destination.
- Restart/return callbacks and popup visibility after match state changes.

Verify the scrim and panel layer above the HUD, scrim touch blocking, frame
aspect ratio, and live label readability. Do not add external test
dependencies unless existing project facilities cannot cover a required
behavior.

Run:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Do not commit or push. Report the changed files, test/build result, and any
remaining limitation caused by the intentionally deferred artwork.
