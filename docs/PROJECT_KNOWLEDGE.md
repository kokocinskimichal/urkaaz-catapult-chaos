# Urkaaaz project knowledge

## Current state

The project is a modular Android game with a deterministic local match
simulation. The application opens into `StartScreen`, which reproduces the legacy start
menu. Its Play button opens the legacy-style `DashboardScreen`; the dashboard
Campaign button opens the legacy-style `CampaignScreen`; the test mode button
opens `MatchScreen` with `DEMO_LEVEL`. Loadout, shop and result screens remain
placeholders so they can be implemented incrementally.

Terminology convention: **mecz** means the gameplay session and runtime
simulation only. The Android UI is called the **match screen**, while the
selected campaign data is a **campaign level**.

The campaign screen intentionally reuses the legacy visual style only. Its
level count and level selection are driven by the new
`CampaignLevelDefinition` contract. Selecting level `n` navigates with a typed
`campaignLevel = n`, and `MatchViewModel` constructs the new match using that
campaign definition rather than copying legacy campaign behavior.

For emulator testing, `CampaignUiState` currently initializes
`highestUnlockedLevel` to `CampaignLevelDefinition.MAX_LEVELS`, so all 100
campaign entries are selectable. This is a temporary test configuration; it
does not represent persistent campaign progression.

The current match HUD displays:

- match countdown timer;
- wind direction and strength;
- pause/resume control;
- ammunition selection at the bottom of the battlefield.

The ammunition control follows the legacy interaction pattern: the bottom
control shows the currently selected ammunition, and tapping it opens a
four-column popup grid. Unavailable or depleted ammunition is dimmed and
disabled; selecting an enabled item updates the selector and sends
`MatchUiAction.SelectAmmo` through the existing ViewModel boundary.

Campaign wind is now passed from `CampaignLevelDefinition.initialWind` through
`MatchViewModel`, `LocalMatchGateway` and `MatchSession` into
`SimulationConfig.windAccelerationX` and
`SimulationConfig.windAccelerationScale`. The Android match uses a calibrated
physical scale of `260`, preserving a strong but controllable projectile
deflection while allowing extreme opposing wind to stop a projectile before
the opposing fortress. The snapshot continues to expose normalized strength
values for the HUD. Campaign definitions currently use deterministic
alternating directions and four low-to-medium strength bands for testable
level variation.

Wind is dynamic during gameplay: after an initial 8-second period it changes
through a deterministic 3-second transition, then schedules the next change
after 18–24 seconds. `game-simulation/WindSystem` owns this state so the
Android layer only renders the current snapshot.

The HUD is rendered as an overlay on the battlefield rather than as a separate
black strip above the map.

The match now exposes a legacy-style `DEBUG` control after the match starts.
Its panel supports `Show hitboxes`, which renders presentation hitbox guides
for fortress targets and active projectiles, and `SNAPSHOT`, which hides
the HUD and ammunition controls and shows the complete normalized battlefield.
Legacy AI-unit toggles are intentionally not copied because the new match
does not yet expose runtime unit deployment or AI-unit commands.

Catapults are not damage targets. Projectile damage is resolved against the
opposing fortress collision hitbox; the debug overlay therefore does not draw
catapult hitboxes. The fortress hitbox is an alpha-derived approximation made
of six rectangles covering the upper tower, body, lower wings and base. Direct
hits and area damage use the same rectangle profile.

## Data flow

```text
User input
  → MatchUiAction
  → MatchViewModel
  → LocalMatchGateway
  → MatchSession
  → simulation
  → MatchSnapshot
  → RenderStateMapper
  → MatchHudView / BattlefieldView
```

The UI never sends a modified snapshot back to the simulation. Gameplay changes
must go through commands and domain/application boundaries.

## Important files

| File | Purpose |
|---|---|
| `architecture.md` | Clean Architecture rules and migration decisions |
| `android-app/.../MainActivity.kt` | Thin Android entry point |
| `android-app/.../navigation/AppScreen.kt` | Screen identity contract |
| `android-app/.../navigation/AppNavigator.kt` | Navigation and back stack |
| `android-app/.../navigation/ScreenHost.kt` | Active screen container |
| `android-app/.../screens/start/StartScreen.kt` | Legacy-style start menu |
| `android-app/.../screens/dashboard/DashboardScreen.kt` | Legacy-style dashboard |
| `android-app/.../screens/campaign/CampaignScreen.kt` | Legacy-style campaign level selector |
| `android-app/.../screens/match/MatchScreen.kt` | Match screen boundary |
| `android-app/.../ui/MatchScreenController.kt` | Match composition and lifecycle |
| `android-app/.../ui/MatchHudView.kt` | Timer, wind and pause HUD |
| `android-app/.../ui/AmmunitionPanelView.kt` | Ammunition controls |
| `android-app/.../ui/BattlefieldView.kt` | Battlefield interaction and draw entry point |
| `android-app/.../ui/BattlefieldRenderer.kt` | Battlefield draw orchestration |
| `android-app/.../ui/BattlefieldCameraController.kt` | Camera, zoom and pan |
| `android-app/.../ui/MatchScreenController.kt` | Match composition, lifecycle and debug panel |
| `android-app/.../ui/AimGestureController.kt` | Aiming gesture lifecycle |
| `android-app/.../ui/RenderStateMapper.kt` | Snapshot-to-render mapping |
| `game-application/.../MatchSession.kt` | Application match lifecycle |
| `game-application/.../LocalMatchGateway.kt` | Android/application boundary |
| `game-simulation/.../DeterministicMatchSimulation.kt` | Deterministic match execution |
| `game-contracts/.../MatchSnapshot.kt` | Immutable gameplay read model |

## Known decisions

- The demo match duration is five minutes.
- Wind uses low, medium and high visual indicators for left/right direction.
- Projectile trails are keyed by projectile identity, not team label, so a new
  projectile cannot inherit the previous projectile's trail.
- Camera panning must be distinguished from aiming release so `ACTION_UP` fires
  only when the gesture was an aim gesture.
- System status bar and Back handling use modern AndroidX APIs.
- The engine and simulation remain independent of Android.

## Known incomplete areas

- Dashboard, campaign, loadout, shop and result screens are placeholders.
- Campaign level entries are currently all unlocked for emulator testing; a
  persistent progression repository and production unlock rules are still
  pending.
- Navigation is implemented as a lightweight single-activity screen host; it
  can later be replaced by Android Navigation or Compose without changing the
  game modules.
- No network, login, matchmaking or server architecture is in scope.

## Commands

From the repository root:

```bash
./gradlew test assembleDebug --quiet
git diff --check
```

The repository's intended development branch is `develop`.

## Push workflow

The project slash command `/push` is defined in
`.github/skills/push/SKILL.md`. Before a commit, it updates relevant knowledge
Markdown files with durable facts about the changes, then runs the validation
gate and pushes `develop` using the configured project GitHub account.
