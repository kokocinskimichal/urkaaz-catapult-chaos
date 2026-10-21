# Match Result Popup — Implementation Plan

**Status:** Completed; this document records the popup's first implementation.
The approved outcome plaques and biome illustrations were added afterward. See
`docs/PROJECT_KNOWLEDGE.md` for the current assets and behavior.

## Goal

Show Victory and Defeat as an in-match popup over the existing battlefield.
Use only the imported frame and button assets for now; the illustration,
outcome plaques, reward row, and unlock artwork will be added later.

The popup is an Android presentation overlay, not a new gameplay system or a
navigation to a replacement result screen. Keep the finished match visible
behind a dim scrim and through the frame's transparent illustration window.

## Available assets and behavior

| Asset or behavior | Current source |
|---|---|
| Shared popup frame, including transparent illustration window | `R.drawable.match_result_popup_panel` |
| Victory primary button | `R.drawable.loadout_start_match_button` (gold M) |
| Retry button | `R.drawable.ui_button_red_m` |
| Secondary/return button | `R.drawable.ui_button_dark_m` |
| Live button typography | `UiTypography.applyButtonTypeface` (Cinzel SemiBold) |
| Match outcome and finish lifecycle | `MatchScreenController` / `MatchViewModel` |
| Persisted campaign completion callback | `CampaignCompletionPresenter` |
| Next-level navigation to Loadout | Existing `onNextLevelClicked` callback |
| Return navigation | Existing `onBackClicked` callback |

The frame is available in both `assets_src/match/result-ui/` and Android
`drawable-nodpi`. It is 2400 x 1680 px (10:7) and was upscaled from the
1499 x 1049 preview, so avoid further resampling where possible.

## First-iteration scope

- Add an in-root dim scrim and centered popup overlay to the finished
  `MatchScreen`; keep the battlefield and its existing HUD underneath.
- Render the outcome title and `LEVEL X · BIOME` context as live Cinzel
  SemiBold text. Use warm gold for Victory and restrained red for Defeat.
- Place the shared frame behind text and controls. Its transparent window
  shows the dimmed battlefield until separate result illustrations exist.
- Render the action labels as live Cinzel SemiBold text over existing button
  backgrounds, with label colors chosen for contrast.
- Omit the illustration, victory/defeat title plaque artwork, gold row, gold
  amount, unlock row, coin/icon assets, and any fake placeholder content.
- Do not replace the match with `AppScreen.Result` or implement the current
  `ResultScreen` placeholder.
- Do not change domain, simulation, campaign persistence, or progression
  rules.

## Result actions

| Match/result condition | Popup actions |
|---|---|
| Defeat | Red `RETRY`; dark `EXIT TO CAMPAIGN` |
| Victory, next level exists and completion is persisted | Gold `NEXT LEVEL`; dark `EXIT TO CAMPAIGN` |
| Victory on final campaign level | Gold `EXIT TO CAMPAIGN`; no `NEXT LEVEL` |
| Victory while completion is being saved | Keep `NEXT LEVEL` unavailable; show the existing saving status and keep it non-actionable |
| Victory completion save fails | No `NEXT LEVEL`; offer retry and campaign return |
| Demo match | Red `RETRY`; dark `BACK TO DASHBOARD`; never show campaign progression actions |

`RETRY` restarts the current match through `MatchUiAction.RestartMatch`.
`NEXT LEVEL` uses the existing callback, which opens the next level's Loadout.
Campaign return uses the existing back/navigation callback. The screen must
not enable `NEXT LEVEL` before successful completion persistence or when no
next campaign level exists.

## Implementation sequence

1. Add a small result-popup view that receives an immutable presentation
   state and action callbacks; keep it owned by the match presentation layer.
2. Add a result-specific asset mapping only if it follows existing Android
   catalog patterns; do not put result assets into `LoadoutAssetCatalog`.
3. Add the panel, a full-screen scrim, live context/outcome text, and the
   action buttons to the `MatchScreenController` root `FrameLayout`, above
   current match controls.
4. Replace or hide the existing centered finished-match action so it cannot
   appear over or alongside the popup.
5. Derive popup visibility and actions from the existing render outcome,
   demo-mode flag, level boundary, and `campaignCompletionRecorded` callback.
   Do not create a second source of gameplay or campaign state.
6. Add/reuse string resources and keep all button text in Cinzel SemiBold.
7. Add focused tests for action eligibility and result-state transitions;
   validate overlay layering, touch blocking, and return/retry/next callbacks.
8. Update directly related project knowledge and changelog after
   implementation.

## Acceptance criteria

- Both Victory and Defeat appear as overlays while the finished battlefield
  remains visible and dimmed behind them.
- The frame retains its transparent illustration window; no illustration,
  reward, or unlock is fabricated.
- Buttons use the existing gold/red/dark M backgrounds and live Cinzel
  SemiBold labels.
- Victory progression is gated by successful persistence and campaign bounds.
- Defeat retry restarts the same level; campaign return leaves the match.
- Demo mode never offers `NEXT LEVEL` or a misleading campaign action.
- Reopening/restarting the match removes the popup and restores normal input.
- `./gradlew test assembleDebug --quiet` and `git diff --check` pass.

## Explicitly deferred

Separate Victory/Defeat title plaques, result illustrations, gold rewards,
unlock presentation, and any additional panel or button artwork are outside
this implementation.
