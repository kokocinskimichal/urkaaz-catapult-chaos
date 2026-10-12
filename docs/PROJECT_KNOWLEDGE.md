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

The Level Definition System (LDS) uses immutable contracts in `game-contracts`:
`MapDefinition`, `TerrainProfile`, `TerrainSample`,
`ProtectedTerrainZone`, fortress and catapult placements, map object
placements, `InitialCameraAnchor` and `MapGenerationMetadata`. Campaign
authoring uses `LevelAuthoringRequest`, `LevelDefinitionBuilder`,
`MapDefinitionValidator` and `CampaignLevelRegistry`. Definitions are passed
through `CampaignLevelDefinition` and `MatchScenario` into
`SimulationConfig`; the deterministic simulation consumes the materialized
terrain profile without mutating the definition. The Android render-state
mapper exposes immutable map objects and the initial camera anchor for
presentation.

The production map rules are: terrain samples every 40 world units, adjacent
height delta no greater than 200 world units, at least three foundation
transition samples, fortress foundation tolerance of ±80 world units,
40-world-unit decoration margin, explicit protected terrain zones, neutral
asset keys and deterministic seed/generator/authoring metadata. Each of the 100 campaign levels is stored in its own `ChapterXLevelY.kt` file
as an explicit `LevelAuthoringRequest` with materialized terrain samples,
placements and metadata. The files are grouped by chapter and exposed through
the same campaign definition boundary. The standalone
`DemoLevelDefinitions.default` map uses the same LDS contracts and validator
without being a campaign level.

The campaign terrain was generated from broad deterministic slope fields, then
materialized into the individual level files. Runtime loading does not invoke a
campaign terrain generator; it consumes the authored samples while preserving
smooth transitions, flat protected foundations and the 200-world-unit
adjacent-sample limit.

`tools/level_ground_authoring.py` is a standard-library Python authoring helper.
It opens a local browser canvas with the canonical 7200x4800 world proportions,
proportional fortress previews, editable fortress foundation heights and
automatic flat foundation zones. It exports 40-world-unit terrain samples and
protected foundation metadata as JSON for later conversion into a campaign
level definition.

The authoring helper also supports chapter generation: it creates ten terrain
proposals from hill count, hill size, terrain style, variation and seed
parameters, applies repeated smoothing, and lets the author accept or reject
each level. Rejected proposals are regenerated individually; a chapter JSON
export is enabled only after all ten levels are accepted. Imported campaign
profiles must retain fortress foundations, terrain-anchored trees, rocks and
props, while keeping decorations outside foundation margins and away from
steep slopes.

The current match HUD displays:

- match countdown timer;
- wind direction and strength;
- pause/resume control;
- ammunition selection at the bottom of the battlefield.
- legacy-style unit deployment controls above the ammunition selector;
  selecting a unit sends `MatchUiAction.DeployUnit`, spends team gold and
  exposes the deployed unit through the match snapshot.
- Unit visuals were migrated from the legacy resource set into
  `android-app/src/main/res/drawable-nodpi`: icons for the deployment panel,
  left/right unit sprites, and the available legacy animation frames. The
  battlefield uses the matching left/right sprite for each deployed unit.
- Unit battlefield placement follows the legacy renderer contract: the
  sprite's bottom is anchored to the ground baseline, dimensions are derived
  from the legacy type heights (`144/150/170/220` world units), the shared
  `0.96` sprite scale is applied, and the raging boar keeps its `1.6` visual
  multiplier. The shared `WorldScale.UNIT_SCALE` is now `3.0` for all unit
  presentation sizes; simulation separation and collision rules remain stable.
  Deployed units use the simulation ground line rather than the previous
  floating `0.72` height.
- A sent wave marks its units as moving, advances them toward the opposing
  fortress during simulation ticks, and switches rendering to the copied
  legacy movement animation frames.
- Supply follows the legacy resource cadence: after spending, it regenerates
  by one point every three seconds up to the maximum of twenty. Wave deployment
  starts from the unit's garrison position without an artificial positional
  jump, and movement uses the legacy `0.45` speed scale multiplied by the
  global `2.0` movement rule.
- Unit waves now resolve legacy-style interactions in the deterministic
   simulation: Defenders and Raging Boars fight nearby enemy units, Slingmasters
   attack visible-range targets, Demolishers detonate on contact, and Sappers
   damage the enemy fortress with their bomb before returning toward their own
   fortress. Units attack the enemy fortress when no enemy unit is in contact,
   and fortress damage is emitted through the existing match events.
- Unit behavior is implemented by dedicated `DefenderUnit`, `SapperUnit`,
   `DemolisherUnit`, `RagingBoarUnit`, and `SlingmasterUnit` classes. Their
   balance values live centrally in `UnitConfigurations.kt`; `UnitFactory`
   creates the dedicated class from a configuration or command identifier, so
   balance tuning does not require editing unit classes.
- Newly recruited units occupy the fortress-side garrison slot while older
  units walk outward into the next slot instead of overlapping. Garrison
repositioning uses `40` world-unit spacing and a slower `0.22` movement scale,
also multiplied by the global `2.0` movement rule; walk animation frames
advance at half the previous cadence. Unit
sprites use a `0.308` presentation multiplier after the additional 20% size
reduction.
- A newly recruited unit spawns behind the existing garrison line and walks
toward the first production slot instead of appearing at its final position.
Its rear spawn distance is a fixed `20` world units, independent of the
number of units already in the garrison.
- Idle animation frames are used where legacy assets provide them
(`DEFENDER` and `RAGING_BOAR`). Each unit applies a stable entity-ID-based
frame offset so idle and walking animations do not start in sync. The shared
animation clock uses a long cycle so idle sets with more than 16 frames are
not truncated to a subset; unit frame playback advances at approximately
`7.5 FPS` to match the slower legacy feel.
- Unit sprite and animation bitmaps are cached by the asset catalog; rendering
does not decode the full animation set again for every frame or every unit.
- Realtime AI decisions now include unit recruitment every second using the
RED team's supply, followed by `SendWave` after three recruited units finish
entering the garrison. The AI now also randomizes its match temperament,
uses defensive SAPPER counter-selection, and varies the wave threshold
(`1/2/3`) like legacy instead of limiting the opponent to projectile fire.
- The match renderer draws a legacy-style HP bar above every unit. The bar is
green above 60% health, orange from 30% through 60%, red below 30%, and keeps
a dark rounded background with a gray empty state.
- Enemy units are constrained by an allied-unit debug selector in `DEBUG
TOOLS`. The selector updates the AI's allowed unit definitions at runtime and
requires at least one enabled type. Allied units in the same wave keep a
small minimum world-space separation so faster units can partially overlap
visually but cannot disappear completely into one another; enemy Sapper
contact still slows only the Sapper and does not hard-block melee movement.
- Unit feet are anchored to the renderer's shared `groundTop`/terrain-cap line,
not to the simulation's normalized Y value, so the sprite and cap remain
aligned when the viewport aspect or HUD height changes.

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
| `android-app/.../ui/UnitPanelView.kt` | In-match unit deployment controls |
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
- `BattlefieldView` keeps `SlingshotAim`, the trajectory preview and the
  rendered catapult geometry aligned: aim origin uses the interpolated terrain
  baseline and the catapult bitmap center, while the preview starts at the
  simulation muzzle (`catapultY - 34`) and applies the current wind vector.
  Preview timing and point density are presentation-only (`36` points at
  `1/16` second intervals); the deterministic simulation remains the source of
  truth for the fired projectile.
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
