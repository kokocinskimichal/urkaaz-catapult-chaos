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

Campaign maps use one immutable JSON contract in `game-contracts`:
`MapDefinition` contains a map ID and biome, fixed world dimensions,
`terrain.heightSamples`, protected zones, fortresses, visual-only decorations,
an initial camera anchor and zoom, and generation metadata. Catapults, terrain
tiles, editor state and gameplay objects are excluded. `MapDefinitionRepository`
strictly decodes bundled JSON and reports explicit load failures;
`MapDefinitionValidator` checks schema, fixed world dimensions, known biome and
assets, terrain, protected zones, fortress placements, decoration bounds and
transforms, relic soil footprints, and camera framing. Ordinary decorations
may be placed at any height within the map and on slopes.

The normalized `game-campaign/src/main/resources/maps/demo-map.json` came from
the supplied `~/Downloads/web-map.json`; the source file was not modified.
Android also bundles the same file at
`android-app/src/main/assets/maps/demo-map.json` for offline loading. All 100
campaign levels used `demo-map` temporarily while retaining their Kotlin
gameplay configuration. Chapter 1, Levels 3 and 4 use their imported maps in
both resource locations; other levels continue to use `demo-map`. Level 4
preserves the authored 2870/3390 foundation heights and has three relics moved
to validated, non-overlapping soil placements. This is a transition mapping,
not the final one-map-per-level arrangement. The former Kotlin campaign map
definitions and map authoring builder have been removed.

Catapult positions are derived from fortress positions by
`CatapultPresentationContract`, and simulation dimensions always come from
`WorldDimensions`. The map's biome is the rendering-theme source. Decorations
use stable asset keys and remain visual-only; buried Relics use
`relic.<biome>.small.<index>` or shared `relic.shared.medium.<name>` keys and
validate their size-specific soil footprint. The shared medium set is
available to every biome.

The web and Kotlin editor codecs read and write the same strict schema-1 JSON.
Editor phase and selection remain editor-only; production JSON contains no
catapult placements, terrain tile placements or generic object list. The web
editor derives its preview catapults from fortress placements, syncs the
shared terrain asset index for decoration-key validation, and keeps those
editor-only catapults out of exported JSON. The Android camera consumes
authored framing data without changing simulation state. Both campaign and
editor maps are available offline.

The shared terrain catalog now indexes the complete legacy decoration sets:
Green Frontier (6 trees, 6 rocks, 6 props), Frostbound (6, 6, 12), Copperwood
(8, 6, 6), and Ashen March (6, 6, 8). Sunken Marshes intentionally shares the
Green Frontier assets; that alias is recognized by both the editor and
`AssetCatalog`, so its palette choices also pass campaign map validation.
Before locking terrain, the editor can change either foundation height. The
associated fortress and catapult move together, the protected samples remain
flat at the new height, and adjacent samples use the same smooth falloff as a
manually moved terrain control point.
The editor can also raise or lower the complete map scene relative to its
fixed background. Terrain, fortresses, derived catapults, protected zones,
decorations and Relics move together in one undoable operation; world bounds
prevent moving authored coordinates or Relic footprints out of the map.
Selected map decorations and Relics display corner handles: radial dragging
scales around the visual center while angular dragging rotates; the selected
object panel remains available for precise numeric transforms.

The current match HUD displays:

- match countdown timer;
- wind direction and strength;
- pause/resume control;
- a `RESTART MATCH` control after the match outcome overlay, which restarts the
  current local match through `LocalMatchGateway.restart`;
- ammunition selection at the bottom of the battlefield.
- `MatchViewModel` blocks firing when the selected limited ammunition has no
  remaining stock, preventing an exhausted selection from crashing the Android
  match screen.
- The demo match's Debug tools can enable a world-coordinate readout beside a
  mouse cursor. `BattlefieldView` handles hover, generic-motion and pointer-move
  events, and `BattlefieldCameraController.screenToGameWorld` converts them
  through the current camera transform and viewport dimensions; campaign debug
  tools omit this demo-only option.
- The demo starts with light eastward wind (`direction = 1`, `strength = 0.25`),
  enabling the deterministic wind system: it begins transitioning after eight
  seconds and selects another wind target every 18–24 seconds.
- The match tick reads the immutable simulation snapshot through
  `LocalMatchGateway` before advancing. This prevents a stale presentation
  state from dispatching `AdvanceSimulation` after the simulation has reached
  `FINISHED`; the finished snapshot keeps the restart control visible.
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
- Melee units stop `150` world units from their target and attack from that
   standoff distance, with `55` additional world units per formation rank. This
   keeps opposing sprites visually separated while preserving melee attacks.
- Newly recruited units occupy the fortress-side garrison slot while older
  units walk outward into the next slot instead of overlapping. The first slot
  is `690` world units from the world edge on full-size maps (`worldWidth - 690`
  for RED), with `70` world-unit spacing. Maps narrower than `2000` world units
  clamp that first-slot distance to leave `60` units to the midpoint.
  Garrison repositioning uses a slower `0.22`
  movement scale, also multiplied by the global `2.0` movement rule; walk
  animation frames advance at half the previous cadence. Unit
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
- The approved blue and red Demolisher idle animations each use 48 transparent
  512x512 frames named `demolisher_{left,right}_idle_000` through `_047`.
  Source indices 006-053 are used for both variants; the running poses at
  indices 000-005 and 054-059 were excluded. The original AnimGen archives are
  retained under `assets_src/animgen/demolisher/{blue,red}/idle/`. The generated
  red animation's barrel panels and cloth were locally hue-corrected to match
  the red source sprite while preserving the orange fuse, green skin, and
  brown equipment. Both expanded canvases are scaled 2x and bottom-aligned
  with a `75/512` transparent-bottom offset.
- The approved blue and red Demolisher jog animations each use 60 transparent
  512x512 frames named `demolisher_{left,right}_jog_000` through `_059`. The
  original AnimGen archives are retained under
  `assets_src/animgen/demolisher/{blue,red}/jog/`; the red jog's barrel panels
  and cloth were locally hue-corrected while preserving the orange fuse, green
  skin, and brown equipment. The 3-digit jog sequences load separately from
  the legacy 2-digit Demolisher movement frames and use the same 2x canvas
  scale and `75/512` bottom anchor offset as the generated idle.
- The user-approved blue Demolisher contact/death animation uses 60 generated
  512x512 frames named `demolisher_left_death_000` through `_059`, styled after
  the in-game Powder Barrel impact. Its original AnimGen archive is retained
  under `assets_src/animgen/demolisher/blue/death/`. The accepted sequence has
  visible effects touching the canvas edge in frames 034-055; it is imported
  unchanged at the same generated canvas scale and bottom anchor as the blue
  Demolisher idle/jog. Death retention supports this blue sequence and the
  existing two-team Defender death animations; a red Demolisher death sequence
  has not been generated.
- Both blue and red Defender idle and jog animations use 60 transparent
  256x256 frames from their approved AnimGen sequences, sampled at every other
  source frame to match the renderer's approximately `7.5 FPS` cadence. Their
  expanded canvases are scaled and bottom-aligned by `BattlefieldRenderer`,
  with per-idle-sequence offsets and a shared jog offset keeping the feet and
  health bars stable. Both Defender combat animations use 30 transparent
  frames for one overhead axe strike; the opponent's red variant attacks with
  the left hand. Each generated state has its own measured transparent-bottom
  offset to keep the feet and health bars stable. One accepted blue-idle frame
  retains the source's top-clipped axe toss (source frames 23-25); the red
  idle sequence has a consistent 79/512 transparent-bottom ratio, and neither
  jog sequence has edge clipping.
- Both blue and red Defender death animations use 60 approved transparent
  512x512 frames at the renderer's approximately `10 FPS` death cadence,
  driven by an independent per-unit clock that starts when each unit enters
  the dead state. The renderer retains either team's immutable dead-unit
  snapshot after simulation cleanup long enough to play all 60 frames. Their
  expanded canvases use measured transparent-bottom offsets (`70/512` blue,
  `79/512` red) to keep the ground anchor stable; the red animation's final
  transparent frames let the defeated unit disappear.
- Unit sprite and animation bitmaps are cached by the asset catalog; rendering
does not decode the full animation set again for every frame or every unit.
- Realtime AI decisions now include unit recruitment every second using the
RED team's supply, followed by `SendWave` after three recruited units finish
entering the garrison. The AI now also randomizes its match temperament,
uses defensive SAPPER counter-selection, and varies the wave threshold
(`1/2/3`) like legacy instead of limiting the opponent to projectile fire.
- Fortress and unit HP bars share one renderer style: a rounded dark track,
8-unit height, and green above 60% health, orange from 30% through 60%, or
red below 30%. Zero health leaves the dark track unfilled.
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
`SimulationConfig.windAccelerationScale`. The simulation uses a physical
scale of `390`; the Android trajectory preview uses the same scale so its
projected deflection matches the stronger projectile response. The snapshot
continues to expose normalized strength values for the HUD. Campaign
definitions currently use deterministic alternating directions and four
low-to-medium strength bands for testable level variation.

Wind is dynamic during gameplay: after an initial 8-second period it changes
through a deterministic 3-second transition, then schedules the next change
after 18–24 seconds. `game-simulation/WindSystem` owns this state so the
Android layer only renders the current snapshot.

The HUD is rendered as an overlay on the battlefield rather than as a separate
black strip above the map.

The match aiming presentation uses the shared catapult presentation contract
for the pull-radius circle, trajectory preview and projectile rendering. Its
aim origin is tied to the fortress-relative launch point, not the relocated
catapult sprite; the circle, preview and simulation all consume the same
snapshot anchor. The trajectory preview applies the same scaled projectile
physics as the deterministic simulation. The Android renderer draws the active
projectile, hitbox and trail at the raw snapshot position without a second
render-space offset. The pull-radius circle adapts to the visible viewport and
camera transform so it remains inside the screen. The preview is intentionally
a short sequence of decreasing dots rather than a full-flight line. Active
projectile sprites use a smaller world-space render size and therefore scale
with the battlefield camera zoom.

The match now exposes a legacy-style `DEBUG` control after the match starts.
Its panel supports `Show hitboxes`, which renders presentation hitbox guides
for fortress targets and active projectiles, and `SNAPSHOT`, which hides
the HUD and ammunition controls and shows the complete normalized battlefield.
Legacy AI-unit toggles are intentionally not copied because the new match
does not yet expose runtime unit deployment or AI-unit commands.
`Show hitboxes` also draws the rendered canvas bounds of terrain trees, rocks,
props and relics. These are asset bounds, not gameplay collision shapes:
transparent padding inside a bitmap can make the visible pixels appear offset
from the frame. Tree, rock, prop and relic bounds use distinct debug colors,
and tree bounds include a lower-anchor cross for checking terrain placement.

Catapults are not damage targets. Projectile damage is resolved against the
opposing fortress collision hitbox; the debug overlay therefore does not draw
catapult hitboxes. The fortress hitbox is one rectangle inset to 90% of the
opaque bounds in both left and right fortress sprites. It is scaled from the
1024x768 source canvas and centered on the rendered fortress. Direct hits, area
damage and the debug preview use the same rectangle.

Fortress presentation and collision share `WorldScale`: the current fortress
dimensions are 672x504 world units, with a 470.4-unit half-width for protected
terrain. Demo-map protected foundations cover that full half-width.
Catapult sprites use the fortress presentation scale with a 0.8 size multiplier
in both the match and map editor, giving a render width of 182.4 world units;
the presentation contract positions them 110 world units outward from fortress
centers and 165 units above the authored foundation after the linked fortress
and catapult offsets. The catapult is rendered behind the fortress; the terrain
cap renders in front of the fortress and can conceal its lower edge.
Both fortresses are presented 35 world units lower than their authored ground
foundation. The vertical offset is applied consistently to fortress collision,
catapult positions and FHP; protected terrain and map foundation heights remain
unchanged. Both catapults sit 200 units above that fortress presentation offset
and 70 units farther outward than before. Their launch/aim anchors remain at
the original fortress-relative offsets (`±40` X and `-360 * worldHeight / 4800`
Y from the presented foundation), with a clamp above local ground if necessary.
Any future fortress translation or size change must apply the same transform to
its catapult, FHP and associated mage placement across all maps and biomes.
The web editor preview uses the same frozen geometry and draw order.
Simulation `FortressSnapshot` values carry each fortress center X and
foundation Y; `RenderStateMapper` forwards them, and the Android renderer maps
the sprite and alpha-bounds hitbox through the same world-to-view transform.
Mapless fortress and catapult placements start from the same configured
reference height, then apply their respective `+35` and `+35 - 200` world-Y
presentation offsets. The aim origin remains fortress-relative.
Initial battlefield framing remains at 1x and waits for a non-zero viewport.

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

- **Unit purchase icons:** `UnitPanelView` keeps all five buttons at 48dp and
  uses 1dp image padding for the Slingmaster (4dp for the others) so its
  smaller artwork has a matching visible size without cropping the icon.
- **Map JSON campaign migration:** campaign geometry is loaded from the
  bundled schema-1 map repository. The temporary explicit assignment maps all
  campaign levels to `demo-map`; there is no fallback to old Kotlin geometry.
- **Map editor export:** the web and Kotlin codecs export only the shared map
  contract. Editing phase, terrain tiles, catapult positions and camera
  anchors are not serialized. The desktop editor edits the same height samples
  and visual decoration records as the runtime model.
- The demo match duration is five minutes.
- Wind uses low, medium and high visual indicators for left/right direction.
- Projectile trails are keyed by projectile identity, not team label, so a new
  projectile cannot inherit the previous projectile's trail.
- `BattlefieldView` uses the explicit fortress-relative `anchorPosition` for
  `SlingshotAim`, the trajectory preview, and projectile starts; this remains
  independent of the relocated catapult bitmap center. The legacy
  `muzzlePosition()` offset was removed, while the Android renderer keeps the
  active flight and trail in anchor space.
  Preview timing and point density are presentation-only (`36` points at
  `1/16` second intervals); the deterministic simulation remains the source
  of truth for the fired projectile.
- Camera panning must be distinguished from aiming release so `ACTION_UP` fires
  only when the gesture was an aim gesture.
- Pinch zoom anchors the same battlefield point beneath the gesture focus
  (`ScaleGestureDetector.focusX/Y`) and is capped at a 4x view scale. Confirmed
  manual pan or zoom keeps the chosen framing across match-phase changes, but
  automatic tracking resumes when the next BLUE projectile launches. A pan
  touch that ends before the drag threshold does not disable tracking, and a
  match restart resets the manual override even if projectile IDs are reused.
  Aiming power uses screen-space pull distance, so the same finger drag keeps
  its strength across zoom levels; the aiming circle and gesture threshold are
  also measured in screen space.
- Soil textures repeat at a base tile width of 256 view units in both the
  Android battlefield and web map preview. Android tiles preserve the source
  aspect ratio and repeat vertically rather than stretching to the soil depth.
- Fortress and unit health bars share the same rounded track, 8-unit height and
  health-ratio colors; fortress bars are narrower than the fortress canvas, and
  compact reload indicators sit beside the bar instead of over the fortress.
- Raging Boar world sprites use one shared 368x301 crop across all animation
  frames and both facing directions. Their render height compensates for the
  crop so visible size is preserved while the alpha-bounded feet meet the
  ground baseline.
- The ammunition selector remains clickable when the equipped limited
  ammunition is depleted; only depleted entries inside the selection popup are
  disabled so the player can switch to another ammunition type.
- Terrain impact detection samples the projectile's full movement interval and
  binary-searches the first terrain crossing, rather than checking only the
  final position of a simulation tick. This also handles a projectile that
  starts inside the sampled surface, exits it while rising and later returns.
- Ammunition damage profiles keep separate `unitDamage` and `fortressDamage`
  values and separate falloff radii, even when a type currently uses the same
  value for both target classes.
- `POWDER_BARREL` currently deals up to 120 damage within a 220-world-unit
  blast radius, with unit damage falling off by distance. It creates a
  150-world-unit crater with depth 90 and can damage allied units but not the
  firing team's fortress. Its inventory remains limited to three shots with a
  6.5-second reload.
- The ammunition catalog contains Rock, Siege Bomb, Powder Barrel, Fire Rain
  and Plague Cauldron; Cluster Bomb and its fragment-only projectile metadata
  and rendering have been removed. Rock deals 50 damage to units and
  fortresses, Siege Bomb deals 100 fortress damage, Fire Rain ticks for 4
  damage, and Plague Cauldron ticks for 8 unit damage without harming
  fortresses. Fire Rain splits at its flight apex into its continuing parent
  projectile and six independently simulated fragments; the split profile is
  defined in the ammunition flight catalog and published as a
  `ProjectileSplit` event.
- FIRE_RAIN uses one neutral AnimGen presentation set shared by both teams:
  24 container-flight frames (`fire_rain_flight_00`–`23`), 12 split-flash
  frames (`fire_rain_split_flash_00`–`11`), 24 fragment-flight frames
  (`fire_rain_fragment_flight_00`–`23`), and 24 fire-field frames
  (`fire_rain_field_00`–`23`). All are transparent 512×512 PNGs under
  `android-app/src/main/res/drawable-nodpi/`; each original ZIP and MP4 is
  preserved in its corresponding `assets_src/ammunition/fire-rain/` phase
  directory. The accepted no-reference fire-field generation used no Powder
  Barrel image.
- PLAGUE_CAULDRON uses its approved 24-frame 768×576 impact sequence for
  both the cauldron breakup and lingering poison cloud; no separate cloud
  animation is loaded. The impact grows from the cauldron size to a visible
  cloud footprint of about 600×450 world units, overlays units at 50%
  opacity, then loops mature impact frames until the six-second effect fades.
  The presentation clock pauses with the match and follows the wind-drifted
  effect along the terrain; the cauldron's drift rate is 75% of the
  wind-derived rate. Its gameplay and lingering radii are both 300 world
  units, matching the cloud's approximate 600-unit visible width.
- The user supplied an isolated 1254×1254 RGBA Fire Rain shard image generated
  in a separate tool. It was uploaded as the fragment-flight first frame; the
  composite ammunition icon was not sent to AnimGen for this phase. The
  approved 45-credit generation and preview succeeded; 24 sequential 512×512
  RGBA PNG frames are imported as `fire_rain_fragment_flight_00.png` through
  `_23.png`. ZIP integrity passed; no frame touches a canvas edge and the
  minimum alpha-bound margin is 162 pixels. The original ZIP and MP4 are
  preserved under `assets_src/ammunition/fire-rain/fragment-flight/`. The
  `BattlefieldAssetCatalog` strictly loads all four FIRE_RAIN sequences and
  reports a phase/index-specific error for missing, invalid, or extra frames;
  unknown ammunition animations fail explicitly instead of falling back to
  ROCK. `RenderStateMapper` carries every matching `ProjectileSplit` and
  active FIRE_RAIN `EffectSnapshot`. A view-owned presentation clock hides the
  parent projectile and its trail after the split, assigns fragment animation
  by child ID, independently animates split flashes and fields, freezes during
  match/activity pause or view detachment, and resets on match identity/
  generation changes. View detachment preserves presentation IDs for
  reattachment. The renderer draws all phases through the existing battlefield
  camera; the fire-field canvas is rendered at 4× the gameplay diameter to
  account for transparent padding, with its visible lower alpha bound aligned
  to terrain. Its loop uses only continuous-burning frames 2–12, excluding
  the source animation's extinguishing frames so the fire does not visibly
  die and reignite. Field fade selects its final 500 ms from
  `EffectSnapshot.remainingMilliseconds`. Mapper, clock, and PNG-set tests
  cover these contracts; FIRE_RAIN uses launch speed multiplier 8 and
  horizontal fragment spread per step 0.09, and its ground fire lasts
  9 seconds with the existing 500 ms tick interval.
- All projectile collision radii are half their previous values, including
  the default fortress collision margin (23 world units); impact and damage
  radii are unchanged.
- ROCK impact sprites use the same render size as the flying rock through
  frame 8 and expand from breakup frame 9, avoiding an apparent size jump
  before impact.
- SIEGE_BOMB renders at 18 world units in flight and 54 world units on impact;
  these are presentation sizes and do not affect collision or damage.
- The AI only considers ammunition with positive fortress damage when choosing
  a shot against a fortress. Entity-impact presentation uses the collision
  coordinates from `ProjectileHitEntity`.
- Unit maximum health is centralized in `UnitConfigurations.kt`: Sapper 20,
  Defender 50, Demolisher 25, Raging Boar 80 and Slingmaster 25.
- Demo matches configure the BLUE player fortress with 5,000 maximum health
  (ten times the standard 500), while the RED fortress and non-demo matches
  retain 500 maximum health. The value is passed through the application
  gateway into `SimulationConfig`.
- Projectile and impact rendering must not clamp world Y to the fixed
  `groundTop` screen line. Such clamping freezes falling projectiles at one
  screen height and makes their remaining horizontal movement appear as
  level flight.
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

## Map authoring

Map authoring decisions are documented in `docs/MAP_EDITOR_DESIGN.md`,
`docs/MAP_EDITOR_RISKS.md` and
`docs/MAP_EDITOR_IMPLEMENTATION_PROMPT.md`.

The local React/Vite profile editor is available under `map-editor-web`. It
shares the strict versioned `MapDefinition` JSON shape, stores drafts only in
browser `localStorage`, and leaves the Swing editor available. Run it with `npm install`,
`npm run dev`, `npm run test`, or `npm run build` from that directory. The
desktop editor continues to use the shared `game-assets` source, JSON as the
single map-definition source of truth, terrain locking before decoration
placement, and validated versioned exports.
The web editor's Debug panel can toggle a live world-coordinate readout beside
the mouse cursor; coordinates account for the current viewport zoom and pan.

During terrain editing, the web editor displays draggable terrain contour
nodes at roughly 200 world-unit intervals. Dragging a node applies a smooth,
distance-weighted vertical displacement to nearby terrain over three node
intervals in each direction, preserving the existing profile while reducing
sharp local changes. A drag is one undoable operation. Nodes inside protected
foundation zones are omitted, the influence does not cross those zones, and
the denser serialized terrain samples remain the simulation-facing
representation.

The web editor's protected foundation ground runs from the outer world edge to
the fortress approach on both sides: BLUE reaches `x=0`, RED reaches
`worldWidth`, and each front edge extends at least 320 world units beyond the
fortress footprint toward the battlefield center. Front edges are rounded
outward to the terrain sample grid so the full protected span has flat samples;
contour control points are omitted throughout both foundations. Legacy
New editor maps start with a fully flat terrain profile at the fortress
foundation height. Editor phase and draft bookkeeping do not enter exported
map JSON.

Map decorations are authored only from map object data; the renderer no longer
adds a second hard-coded decoration layout. Green Frontier tree, rock and prop
resources use the full `terrain_green_frontier_<category>_<variant>` naming
convention, and map decoration variants are selected deterministically from
the map object ID. The terrain cap is drawn after map decorations and relics
so it covers their buried lower edges.

`MapDefinitionValidator` is the single semantic map validator used by the
repository before a match starts. It validates ordinary decoration bounds and
transforms without requiring contact with the terrain or flat terrain beneath
them; Relics remain constrained to the soil. Editor feedback does not replace
this runtime gate.

## Map editor vertical slice

The repository includes `game-assets`, `map-editor-domain`,
`map-editor-format`, `map-editor-rendering`, `map-editor-assets` and
`map-editor-desktop`. Map authoring uses the shared `MapDefinition` contract
and `MapDefinitionValidator`, and stores schema-1 JSON. Drafts are written as
`<map>.draft.json` using an atomic replace and never overwrite final export.
The desktop entry point is `com.urkaaaz.mapeditor.desktop.MapEditorMainKt` and
runs with `./gradlew :map-editor-desktop:run`.

The desktop viewport fits the canonical 7200 x 4800 game world, renders the
fortresses and derives catapult preview positions from the shared fortress
contract. It edits the sampled terrain profile and places typed visual
decorations; no tile placements are written to map files. After terrain
locking it overlays the selected biome's shared background, soil and cap
assets.
