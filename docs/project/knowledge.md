# Urkaaaz project knowledge

## Current state

The project is a modular Android game with a deterministic local match
simulation. The application opens into `StartScreen`, which reproduces the
legacy start menu. Its Play button opens the legacy-style `DashboardScreen`;
the dashboard Campaign button opens `CampaignScreen`, and selecting a level
opens the loadout screen. The test mode button still opens `MatchScreen` with
`DEMO_LEVEL`. The loadout core, local player profile and per-level loadout
persistence are implemented with AndroidX DataStore. Campaign matches start
only after the profile and saved loadout are read, validated and committed.
Shop and result screens remain placeholders.
`MainActivity` hides Android status and navigation bars for immersive
full-screen presentation; system bars can be revealed transiently with a swipe.

Terminology convention: **mecz** means the gameplay session and runtime
simulation only. The Android UI is called the **match screen**, while the
selected campaign data is a **campaign level**.

The campaign screen intentionally reuses the legacy visual style only. Its
level count and level selection are driven by the new
`CampaignLevelDefinition` contract. `MatchScenario` carries level availability
and an optional committed `PlayerLoadoutConfig` separately, plus a typed
`AiProfileSelection`. `MatchSession`
limits campaign AI units to the level's `availableUnits`; the player's
committed loadout may narrow that set further for BLUE. AI ammunition choices,
RED ammunition commands, and both teams' limited ammunition inventories are
restricted to the scenario's `availableAmmunition`. Campaign
`availableSpells` are level-wide in the scenario, but AI spell casting is not
implemented yet. The match ammunition selector is filtered to level
availability and the committed player's equipped slots.

`game-ai` separates catapult/ammunition, recruitment and wave decisions into
registry-selected strategies. `AiProfileCatalog` maps `CADET` to simple
heuristics, `VETERAN` to situational evaluation and `ELITE` to counterplay.
`AiMatchCoordinator` in `game-application` owns the agent lifecycle and
simulation-time reaction cadence; `MatchViewModel` dispatches the resulting
commands through `LocalMatchGateway`. Campaign profile assignment preserves
the previous level mapping: levels 3, 4, 6, 7 and 11–30 use VETERAN; 8–9 and
31–100 use ELITE; remaining levels use CADET. ELITE progression is 0% at
level 31 and 100% at level 100, with bounded interpolation between. AI uses
the match seed, leads moving counterplay targets by movement speed times its
reaction interval, gets no resource or unit-stat bonuses, and does not cast
spells. The unused campaign enemy health/fortress multipliers and separate
aim-error/reaction overrides were removed; profile tuning is the single source
for those AI parameters. Base aim errors are ±18°/±30 power for CADET, ±3°/±6
for VETERAN, and ±1.25°/±2.5 for ELITE.
The match unit panel hides unit icons not available to the campaign level or
committed player loadout; insufficient supply still disables available units.

`game-application/application/loadout` owns the canonical immutable
`MatchLoadout`, deterministic defaults, stored-selection normalization,
typed validation, asynchronous progression/repository ports, and the
read-only `LoadoutCatalog` presentation projection of domain balance data.
`PlayerProgressionUseCases` initializes a new profile from the confirmed
legacy baseline (`FORTRESS_SHIELD`, zero gold and zero limited stock).
AndroidX DataStore adapters persist that profile and committed per-level
loadouts separately. `LoadoutScreen` edits units, ammunition slots and spells,
shows item details in an in-screen page, preserves the draft/scroll when
returning, and only navigates after a successful commit. Its three horizontal
carousels use equal-sized cards and list available/selected options first;
five dedicated full-card unit illustrations are mapped by
`LoadoutAssetCatalog`. Unit, ammunition and spell cards share a full-card
illustration layout and a centered Cinzel SemiBold title over a slightly
translucent dark bottom strip, with compact status or item metadata beneath.
The Loadout header uses `loadout_header_frame.png`; its title and live campaign
level are centered over the frame's separate upper and lower text panels.
The three Loadout category panels wrap to their headers and carousel cards
instead of stretching vertically; their horizontal margins and gaps are equal.
Selection frames, check badges and information buttons use supplied
transparent PNG assets (`loadout_card_selected_frame`,
`loadout_card_selected_badge`, `loadout_card_info`) rather than
native-drawn shapes or text glyphs. Original files are kept in
`assets_src/loadout/ui/`; Android resources use `drawable-nodpi`.
Cinzel SemiBold is the global Android game UI font and is also explicitly
applied to text rendered on custom canvases.
Live-text button controls explicitly use the shared `UiTypography` helper to
retain Cinzel SemiBold. Button labels baked into image assets are unchanged.
Level-unavailable units, ammunition and spells, plus locked spells, are
desaturated and dimmed with the supplied centered lock asset; the card hides
the visible availability/locked label while retaining accessible descriptions.
The "AVAILABLE", "FIXED" and "UNLIMITED" text labels are hidden on cards;
limited-stock amounts remain visible. Unlimited ammunition uses the supplied
infinity icon from `assets_src/loadout/ui/loadout_unlimited_icon.png`, mapped
to `R.drawable.loadout_unlimited_icon`. Fixed ROCK also shows its supplied
gold lock marker next to the infinity icon. Five approved full-card ammunition
illustrations are stored in `assets_src/loadout/ammunition-cards/` and mapped
to their `loadout_ammunition_*_card` drawables for carousel rendering.
The bottom Loadout summary displays separate live unit, ammunition and spell
counts beside the supplied category icons in `assets_src/loadout/ui/`; labels,
counts, status and the Start Match button remain Android-rendered text and
controls. The Start Match button uses separate transparent normal and pressed
backgrounds from `assets_src/loadout/ui/`; its live label is dark and bold.
The XS family uses a 32dp graphic inside a 48dp touch target: Short for Loadout
`BACK`, Normal for campaign `NEXT LEVEL`, and Long for match restart actions.
Each selector swaps its matching normal/pressed PNG. The larger Start Match
button retains its original 220 x 54 dp selector and layout.
The reusable red button backgrounds are stored in `assets_src/loadout/ui/`
and Android `drawable-nodpi`, with normal/pressed selectors for XS, S, M, L,
XL, XS short, and XS long. Their fourteen PNGs are imported for future UI use;
current screens do not yet select this red family. Labels remain live Android
text and should use a light, high-contrast color on red.
The dark button backgrounds are also stored in `assets_src/loadout/ui/` and
Android `drawable-nodpi`, with normal/pressed selectors for the same seven
footprints. They use a charcoal face with bronze/gold trim and are not yet
assigned to a screen; labels remain live Android text in a contrasting light
color.
The shared match-result popup panel is stored at
`assets_src/match/result-ui/match_result_popup_panel.png` and
`drawable-nodpi/match_result_popup_panel.png`. It has a transparent
illustration window and is used by the in-match Victory/Defeat popup; the
approved 1499 x 1049 preview was upscaled to the AGP's 2400 x 1680 canvas.
The outcome plaques are stored at
`assets_src/match/result-ui/match_result_{victory,defeat}_plaque.png` and
Android `drawable-nodpi`; the popup layers the matching plaque under its live
outcome title. The source PNGs are 1920 x 480 RGBA canvases.
Eleven approved full-card spell illustrations are stored in
`assets_src/loadout/spell-cards/` and mapped to their
`loadout_spell_*_card` drawables for carousel and detail rendering. The
11-type generation prompt is `docs/systems/loadout-spell-card-art.agp`.
`CampaignSystem`
persists completed levels, the highest unlocked level and applied milestone
IDs separately from the profile. Only `BLUE_WIN` advances the campaign. The
match result remains an overlay over the dimmed battlefield. `NEXT LEVEL`
appears only after that completion is persisted and opens the next level's
Loadout; during a pending save, NEXT LEVEL stays disabled while campaign return
remains available. The controller dispatches the enabled action directly;
completion and level eligibility are owned by the popup state mapper rather
than rechecked against potentially stale render state. Failed saves offer retry
plus campaign return. Defeat offers retry and campaign return, while demo
matches offer retry and Dashboard return.
The popup uses live level/biome and outcome text with the shared panel,
outcome-specific plaque, and existing button assets. Separate Green Frontier
Victory and Defeat illustrations are stored in `assets_src/match/result-ui/`
and Android `drawable-nodpi`, sized to the panel window. Reward content remains
deferred.
Haste is awarded for level 5 and Heal for level 7; their availability starts
at levels 6 and 8. Campaign rewards are projected into Loadout progression,
and preferred spells affect only newly generated defaults. Tutorials remain
deferred to the future game-wide TutorialSystem. The SpellSystem has implemented
runtime effects for `FORTRESS_SHIELD`, HASTE and `RUNIC_BASTION`; the other
eight spell effects and AI spell casting remain unimplemented. HASTE costs 50
mana and catalogues at 900 gold, lasts 15 seconds (an approved change from the legacy 5-second
baseline), doubles movement speed for deployed allies only, includes units
deployed while active, refreshes on recast without stacking, and
multiplicatively combines with SLOWED; STUNNED still stops movement. Its timer
and per-unit HASTED status are exposed in snapshots. Its 15-second countdown
uses the same top-down icon shade as depleted Fortress Shield HP, with the
remaining seconds retained in the accessibility description. The user-approved
12-frame gold/turquoise orbiting-chevron effect is integrated as
`anim_spell_haste_orbiting_chevron_000.png`–`anim_spell_haste_orbiting_chevron_011.png`; it loops
behind living, moving HASTED units, distributing the frames across 16 match
render ticks (about 22.7 fps at the current 33 ms tick), and mirrors with
facing direction. All frames are unique RGBA 1024×576 PNGs with transparent
edges and at least 69 px of visible-content margin. The prior wind-trail and
quickstep generations remain archived under `assets_src/spells/haste/`; the
current source image and AnimGen ZIP/video are under
`assets_src/spells/haste/orbiting-chevrons-first-frame.png` and
`assets_src/spells/haste/orbiting-chevrons/`. Its copy-ready brief is
[`haste-orbiting-chevrons-first-frame.agp`](../systems/haste-orbiting-chevrons-first-frame.agp).
Runic Bastion uses shared `288 × 216` world-space geometry, is lifted 12 world
units above its terrain foundation, and has 360 HP. Its center is offset 436
world units toward the battlefield center from the team's gate attack point.
Its approved creation sequence is 20 RGBA 640×360 frames,
`anim_spell_runic_bastion_creation_000.png`–`anim_spell_runic_bastion_creation_019.png`, loaded strictly
by `BattlefieldAssetCatalog.runicBastionCreationFrames`. It plays once at 120 ms
per frame (2.4 s total) when a new Bastion appears, freezes while paused, then
holds the final frame. `RunicBastionPresentation` maps the visible artwork
bounds to the shared geometry, with the terrain cap rendered over its lower
edge. The approved trimmed ZIP and original 24-frame creation generation,
together with the shared source/reference images, are preserved in
`assets_src/spells/runic-bastion/creation/`; the untrimmed generation's clipped
flash frame is not imported. After creation, an approved
24-frame RGBA
640×360 idle loop (`anim_spell_runic_bastion_idle_000.png`–`anim_spell_runic_bastion_idle_023.png`)
plays at 167 ms per frame; the central rune has a small user-approved
shape/size variation during its glow pulse. Its source ZIP/video and review
previews are in `assets_src/spells/runic-bastion/idle-runes/`. The approved 24-frame destruction
revision uses an expanded transparent input canvas (`source_scale = 0.5`); all
RGBA 640×360 frames stay inside the canvas without touching its edges. It plays
once at 167 ms per frame after the Bastion disappears. Since the wall occupies
a smaller area of that source canvas, the renderer maps the destruction
sequence's first-frame visible bounds separately so the wall retains the same
world-space size and ground anchor as creation and idle. The new and previous
source ZIPs, videos, and review previews are preserved in
`assets_src/spells/runic-bastion/destruction/`. Attacking units hold their
positions until the destruction animation ends; other advancing enemies cannot
cross the Bastion footprint during that interval. The 4,008 ms duration is
shared between simulation and presentation by `RunicBastionAnimationTiming`.
The HUD keeps the Runic Bastion spell icon dimmed while the player's Bastion
exists and restores its normal availability styling after destruction.
The shield costs 80 mana, has 150 HP, and
intercepts projectiles within the same fortress-centered ellipse used by its
world-space rendering. Its hit animation starts at frame 04 and remains
visible when the hit depletes the shield. `FortressShieldGeometry` in
`game-contracts` is the shared shape definition. Mana starts at 100,
regenerates 1 every 0.8 seconds of simulation time, and is exposed through
match snapshots. Spell-card artwork is available, but artwork for the
remaining world effects still needs to be mapped as those effects are
implemented.

Future idea, not implemented: progression could grant an otherwise limited
ammunition type a temporary effective unlimited entitlement after a qualifying
user action (15 minutes is an example, not approved balance). Keep the base
ammunition catalog immutable; model the grant and expiry in progression, then
project it into Loadout validation and match inventory rules. Expiry during an
active match, clock authority and grant stacking remain undecided. Details are
recorded in `docs/systems/loadout-system.md` and
`docs/systems/ammunition-design.md`.

Combat randomness uses `SimulationConfig.combatRandomSeed`, generated by
`MatchSession` on every start and restart and published through
`MatchEvent.MatchStarted`. `MatchReplay` in `game-application` captures that
seed, initial match configuration, accepted commands, pause/resume/stop
transitions and simulation time-step commands; `MatchReplayPlayer` reproduces
the trace in-process. Replay records are not persisted to disk and have no UI.
Each unit receives one seeded first-attack delay from zero to half its own
cooldown; later attacks keep the existing cooldown. Unit and ammunition damage
rolls are clamped to 80%-120% of base damage. Each lingering ammunition effect
reuses the same impact roll for all ticks. The simulation's damage-roll helper
accepts a team luck bias for future effects, but no LUCK spell or modifier
runtime is implemented.

Campaign progress is initialized at level 1 in the versioned local campaign
DataStore namespace. `CampaignScreen` reloads progress asynchronously and
blocks selection on storage/corruption failures; it no longer exposes all 100
levels by default. Campaign fortresses use 500 HP each; demo mode preserves its
existing 5000 HP BLUE fortress and 500 HP RED fortress.

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
Chapter II levels 11–20 currently keep the `demo-map` geometry but override
its biome to Frostbound for visual theme only; no additional gameplay
modifiers are applied.

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

Animation frame PNGs use the shared convention
`anim_<category>_<target>_<type>[_<variant>]_<frame:3>.png`, with categories
`character`, `spell` and `ammunition`. Names are lowercase snake_case, team
variants are `blue`/`red`, and neutral sequences omit the variant. Every
sequence starts at `000`, uses three-digit indices and has no gaps. Character
locomotion aliases (`jog`, `run`, and generic `anim`) normalize to `move`;
`fight` normalizes to `attack`, and `death_anim` to `death`. Examples:
`anim_character_defender_move_blue_000.png`,
`anim_spell_runic_bastion_creation_000.png`, and
`anim_ammunition_fire_rain_fragment_flight_000.png`. Android's battlefield
asset loader and animation-asset tests use this naming. The 1,394 imported
frames currently form 24 character, 6 spell and 13 ammunition sequences.
Demolisher has no red death sequence; the existing Fire Rain `impact` sequence
is retained as an asset but is not currently loaded by the runtime catalog.
Projectile catalog entries that use the shared animation helper provide the
`anim_ammunition_<target>` prefix; the helper appends `_flight` and `_impact`
to resolve the matching frame sequences.

The current match HUD uses a three-zone top rail (BLUE fortress health,
Retreat/timer/wind/pause, RED fortress health) and a three-group bottom dock
(Supply and units/orders, ammunition, spells and Mana). The Supply/Units group
is wider than the Spells/Mana group to fit its resource gauge and deployment
action; ammunition remains centered between them. Dock headings are aligned to
their subareas: Supply/Units, Ammunition, and Spells/Mana. Each loadout group
displays at most three real choices. Unit and ammunition choices
follow the committed loadout and level availability; spell choices follow
those filters, use a shared `MatchUiAction.CastSpell(spellType)` path, and
remain disabled when their canonical catalog definition has no implemented
effect. Spell costs, unit Supply costs and mana capacity come from
`LoadoutCatalog`; the simulation and HUD share
`SupplyConfiguration.MAXIMUM_SUPPLY`.
The Supply gauge uses the approved 256 x 256 crate icon from
`assets_src/match/hud/match_hud_supply_icon.png`, mirrored in the Android
`drawable-nodpi` resources. Its live current/maximum counter is centered over
the icon like the Mana value on a subtly darkened translucent backing. The
Supply progress ring is retained around it and animates smoothly between
values; the complete Supply gauge is 80 x 80 dp, matching the Mana orb.
Mana is rendered as a dynamic deep-blue liquid fill (`#18598E`) with a
transparent gold-filigree shell supplied by the user, normalized to 512 x 512
and layered above it. The orb occupies an 80 x 80 dp view, with its
current/maximum value centered over the glass and a subtle shadow for
contrast. The short presentation-only ripple remains native UI; the shell
replaces the programmatic rim and highlight.
The BLUE and RED fortress-health cards share the reusable `HealthCardView`,
which receives the team label and health-bar color as parameters. The current
match HUD uses `HudPanelStyle.universalFrameBackground()` for the complete
dock, both health cards, and the timer module. The dock groups no
longer have individual frames; subtle dividers are drawn by Android between
groups and between timer/wind and pause. The approved source is
`assets_src/match/hud/match_hud_universal_frame.png`, with the Android
Nine-patch at `android-app/src/main/res/drawable-nodpi/match_hud_universal_frame.9.png`.
Major HUD panels use a subtle, rounded Android elevation shadow rendered in
code; shadows are not baked into panel assets. Spell feedback is shown as text
only above the dock after an actual cast attempt, then fades after 2.5 seconds.
No persistent `READY` panel or reserved strip space is used.
Retreat opens a confirmation dialog, pausing a running match until the player
chooses. Cancelling resumes only a match paused by the dialog; confirming sends
`MatchCommand.Surrender`, records `MatchOutcome.SURRENDER`, and opens the
standard defeat result without campaign completion. The Retreat button uses a
transparent 256 × 256 PNG at
`assets_src/match/hud/match_hud_retreat_runner.png`, integrated as
`android-app/src/main/res/drawable-nodpi/match_hud_retreat_runner.png`.
The ammunition selector shows only each ammunition name below its icon, with
finite stock as a small numeric badge and unlimited stock as an infinity-icon
badge. Reload countdowns are drawn over the icon; a dark overlay recedes across
the icon and selected orange background with reload progress, and the icon is
disabled until it is ready. Changing the selected ammunition starts that type's
full catalog-defined reload; selecting the same type again does not reset its
timer. AI waits for a newly selected type's reload before issuing its shot.
Unit selector names appear beneath icons, with Supply costs in small badges on
the icons. Unit, spell, and ammunition artwork is rendered at up to
50 x 50 dp inside 60 x 60 dp slots. The Send Wave button is 66 x 66 dp, 10%
larger than the neighboring 60 x 60 dp icon slots. Unit, spell, and ammunition
stock/cost badges are 20 x 20 dp; the ammunition reload countdown badge is
20 dp high.
The Mana orb's liquid and Supply crate artwork are each approximately
50 x 50 dp inside their 80 x 80 dp views. Send Wave uses its square normal and
pressed button artwork without a visible text label. The two button states are
stored under `assets_src/match/hud/`, imported to Android
`drawable-nodpi/`, and selected by
`match_hud_send_wave_button_states.xml`.
Spell-card artwork is center-cropped to fill each framed 60 dp slot without
side letterboxing, keeping the central illustration prominent. Each slot shows
only the spell name beneath its icon and the Mana cost in an overlaid badge;
persistent READY, ACTIVE, PLANNED, and LOW MANA labels are omitted.
Availability and active state remain conveyed through the slot's visual state
and accessibility description.
The wind indicator is 28 dp and selects a directional icon variant for low,
medium, or high strength; the textual strength label is reserved for its
accessibility description.

The current match HUD also displays:

- match countdown timer, wind direction/strength, and Retreat/pause controls;
- BLUE and RED fortress health in the top rail, while world reload indicators
  remain beside their catapults;
- Supply, committed unit choices with canonical costs, and `SEND WAVE`;
- up to three committed ammunition choices with stock/reload information;
- up to three committed spell choices, Mana and shared spell status feedback;
- the existing result overlay and restart action; match navigation is available
  through the result flow rather than a separate top-rail Back button;
- a `RESTART MATCH` control after the match outcome overlay, which restarts the
  current local match through `LocalMatchGateway.restart`;
- transient spell feedback that does not reserve space or move the dock.
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
  `LocalMatchGateway` before advancing. An advance that itself transitions to
  `FINISHED` must return its final snapshot and events without checking
  `requireRunning()` again; this lets the match screen render the result popup
  without a spurious terminal-tick exception. If a tick later observes an
  already-finished snapshot, `MatchViewModel` also drains pending events so
  `MatchFinished` still reaches campaign completion.
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
- Supply starts at 20 for each team and regenerates by one point every three
  seconds up to a regeneration cap of 20. Wave deployment starts from the
  unit's garrison position without an artificial positional jump, and movement
  uses the legacy `0.45` speed scale multiplied by the global `2.0` movement
  rule.
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
- Shared BLUE/RED team-texture masks desaturate the fabric texture by 20%
  toward its original luminance before applying frame lighting. Android
  rendering and the Studio animation preview use the same transform for
  every masked character, including Defender, Demolisher and Sapper.
- Demolisher idle now uses one shared 48-frame transparent 256x256 sequence;
  move uses one shared 60-frame transparent 256x256 sequence. Both use
  `anim_character_demolisher_{idle,move}_NNN`, per-frame team-colour masks and
  the configured BLUE/RED textures. RED mirrors the BLUE-derived neutral
  frames instead of loading separate team-specific animation frames. The
  masked blue cloth and barrel panels are neutralized in the shared frames;
  skin, orange fuse and brown equipment remain unchanged. Original source sets
  are retained under `assets_src/animgen/demolisher/{blue,red}/{idle,jog}/`.
  Rendering retains the 2x canvas scale and `75/512` bottom anchor offset.
- Sapper idle and movement use shared transparent 256x256 sequences,
  `anim_character_sapper_idle_NNN` (30 frames) and
  `anim_character_sapper_move_NNN` (15 frames), plus the 15-frame
  `anim_character_sapper_attack_NNN` throw. All three use per-frame garment
  masks and the existing BLUE/RED fabric textures. RED mirrors the
  BLUE-derived neutral frames. Android renders idle and jog at 2x with their
  respective `31/256` and `33/256` bottom padding, avoiding a size pop between
  the states. On arriving at a target, a bomb-carrying Sapper keeps the jog
  frames for four presentation updates, then returns to idle even if the
  simulation remains in `MOVING` during the initial attack delay. Attack
  renders at 2x with `32/256` bottom padding and ends when the bomb leaves his
  hand; on release a small world-space bomb flight reuses the existing Siege
  Bomb frames at 30 world units, animating from the Sapper to its fortress or
  Bastion target over 500 ms. This is presentation-only; direct simulation
  damage timing and impact resolution remain unchanged.
  Original AnimGen output is retained under
  `assets_src/animgen/sapper/blue/{idle,jog,attack}/`.
- Sapper return presentation uses shared 15-frame 256x256 `turn` and `return`
  sequences with per-frame garment masks and the existing BLUE/RED team
  textures. The renderer plays the turn once when the existing `RETURNING`
  action state begins, then loops the bomb-less cheerful, low-bounce jog;
  simulation movement remains authoritative. Return masks include the head
  wrap in all 15 frames, keeping the team color continuous; masked frame pixels
  are neutralized and each frame covers at least 400 pixels. A per-entity
  presentation clock pauses
  with the match and resets when the Sapper leaves `RETURNING` or the match
  changes. Turn scale interpolates from the attack's 2x to the return's 2.38x
  across its 15 frames; turn uses `29/256` bottom padding and return uses
  `27/256`. Original AnimGen output is retained under
  `assets_src/animgen/sapper/blue/turn/` and
  `assets_src/animgen/sapper/blue/return-cheerful-jog/`; the previous return
  generations are retained under `assets_src/animgen/sapper/blue/return/` and
  `assets_src/animgen/sapper/blue/return-forward-hop/`.
- The Sapper stops 200 world units beyond an enemy fortress's front hitbox
  face before throwing its bomb. The approach and attack check share the
  `FortressHitboxProfile` geometry and apply symmetrically to BLUE and RED.
- Demolisher death uses one shared 32-frame, transparent 768x432 sequence:
  the first 12 frames preserve the approved run-and-dive motion, followed by a
  20-frame explosion from the powder barrel on his back. Per-frame masks
  neutralize the BLUE cloth and barrel panels; RED mirrors the shared frames
  and applies the existing RED palette. Both teams remain rendered until the
  sequence completes. Because the death canvas is wider and taller than the
  256x256 idle/move canvases, its opening frames use a 2.42x renderer scale
  (versus 2x for idle/move) to keep the visible character size consistent; the
  same scale carries through the explosion. Original AnimGen outputs are
  retained under `assets_src/animgen/demolisher/blue/death/{throw,explosion}/`.
  The user approved the source explosion frames without another generation;
  smoke reaches the upper canvas edge in frames 013-024, with a visible
  green/purple matte fringe during the fade. Android presentation estimates
  contact time from successive unit positions and starts the run-and-dive when
  predicted contact is within the first 13 frames' playback time. It holds on
  the final pre-explosion frame until the simulation marks the Demolisher dead,
  then starts the explosion at frame 13. The prediction is cancelled if
  contact is no longer closing or the target becomes invalid; it never changes
  simulation damage or death timing.
- Defender uses one neutral 256x256 sequence for each state: idle and move have
  60 frames, attack 30, and death 60. Neutral frames were selected from the
  former BLUE sequence; RED is rendered by mirroring the shared frames. The
  RED-specific attack-hand animation is not retained. Team appearance is
  applied at render time from per-frame alpha masks and repeating fabric
  textures configured by `android-app/src/main/assets/animations/defender/palettes.json`.
  The masks also cover the remaining blue-dominant details that showed through
  in gameplay, while leaving skin and gold equipment unchanged.
  Current masks are an automatic color-segmentation draft, not art-approved;
  they now include all blue regions, including the shield field, and may need
  manual cleanup. `TeamTextureBitmapCache` preserves source luminance and alpha
  and uses a 6 MiB LRU for generated frames. Studio's Animations tab previews
  BLUE/RED textures and exposes a red mask overlay. Death playback still uses
  the independent per-unit clock and retains the defeated unit after simulation
  cleanup long enough to play all 60 frames. Since the neutral source is BLUE,
  unmasked equipment details and skin remain unchanged when the RED texture is
  selected.
- Unit sprite and animation bitmaps are cached by the asset catalog; rendering
does not decode the full animation set again for every frame or every unit.
- Realtime AI uses `AiMatchCoordinator` and deterministic, profile-selected
  strategies for artillery, recruitment and wave dispatch. Its reaction
  interval and aim error are bounded by the campaign profile; it uses only
  RED's available supply and has no resource/stat bonuses.
- Campaign AI progresses from CADET on levels 1–10 to VETERAN on 11–30 and
  ELITE from level 31. CADET adds aim error of up to ±28° and ±45 power and has
  an 8-second interval between unit recruitments; combat decisions continue
  during that interval.
- BLUE and RED both start with 20 supply and regenerate one supply every three
  seconds, up to a shared cap of 20.
- Unit HP bars use a rounded dark track, 8-unit height, and green above 60%
  health, orange from 30% through 60%, or red below 30%. Fortress health is
  shown only in the match HUD's top panels.
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
| `AGENTS.md` and `docs/agent-context-index.md` | Repository instructions and curated Markdown context loaded by `/loadContext` |
| `docs/architecture/architecture.md` | Clean Architecture rules and migration decisions |
| `android-app/.../MainActivity.kt` | Thin Android entry point |
| `android-app/.../navigation/AppScreen.kt` | Screen identity contract |
| `android-app/.../navigation/AppNavigator.kt` | Navigation and back stack |
| `android-app/.../navigation/ScreenHost.kt` | Active screen container |
| `android-app/.../screens/start/StartScreen.kt` | Legacy-style start menu |
| `android-app/.../screens/dashboard/DashboardScreen.kt` | Legacy-style dashboard |
| `android-app/.../screens/campaign/CampaignScreen.kt` | Legacy-style campaign level selector |
| `game-application/.../progression/PlayerProgressionUseCases.kt` | Local profile initialization and snapshot validation |
| `android-app/.../persistence/DataStorePlayerProgressionRepository.kt` | Versioned profile storage |
| `android-app/.../persistence/DataStoreLoadoutRepository.kt` | Versioned campaign-level and demo loadout storage |
| `android-app/.../screens/loadout/LoadoutViewModel.kt` | Async loadout state and guarded commit |
| `android-app/.../screens/match/MatchScreen.kt` | Match screen boundary |
| `android-app/.../ui/MatchScreenController.kt` | Match composition and lifecycle |
| `android-app/.../ui/MatchHudView.kt` | Top rail, timer, wind, fortress health, Retreat and pause controls |
| `android-app/.../ui/AmmunitionPanelView.kt` | Three-slot ammunition selector |
| `android-app/.../ui/UnitPanelView.kt` | Supply, unit deployment and wave controls |
| `android-app/.../ui/SpellPanelView.kt` | Loadout spell slots and Mana orb |
| `android-app/.../ui/ManaOrbView.kt` | Dynamic liquid-style Mana presentation |
| `android-app/.../ui/MatchFeedbackToastView.kt` | Transient text-only spell feedback with fade-out |
| `android-app/.../ui/BattlefieldView.kt` | Battlefield interaction and draw entry point |
| `android-app/.../ui/BattlefieldRenderer.kt` | Battlefield draw orchestration |
| `android-app/.../ui/BattlefieldCameraController.kt` | Camera, zoom and pan |
| `android-app/.../ui/MatchScreenController.kt` | Match composition, lifecycle and debug panel |
| `android-app/.../ui/AimGestureController.kt` | Aiming gesture lifecycle |
| `android-app/.../ui/RenderStateMapper.kt` | Snapshot-to-render mapping |
| `game-application/.../MatchSession.kt` | Application match lifecycle |
| `game-application/.../MatchReplay.kt` | In-memory replay recording and playback |
| `game-application/.../AiMatchCoordinator.kt` | AI lifecycle and decision cadence |
| `game-application/.../LocalMatchGateway.kt` | Android/application boundary |
| `game-ai/.../AiAgent.kt` | Profile tuning and strategy composition |
| `game-ai/.../AiStrategies.kt` | Strategy contracts, registry and built-in strategies |
| `game-contracts/.../AiProfile.kt` | Typed AI profile selection |
| `game-simulation/.../DeterministicMatchSimulation.kt` | Deterministic match execution |
| `game-contracts/.../MatchSnapshot.kt` | Immutable gameplay read model |

## Known decisions

- **Gameplay prompts:** For a new game, core loop, or substantial gameplay
  change, check for a defined player loop, success/failure conditions,
  difficulty progression, responsive controls, readability and feedback, and
  scope priorities. Flag important omissions; ask a focused question only
  when the missing decision changes behavior or scope. Do not apply this
  checklist to small, unambiguous technical fixes.
- **Unit purchase icons:** `UnitPanelView` keeps all five buttons at 48dp and
  uses 1dp image padding for the Slingmaster (4dp for the others) so its
  smaller artwork has a matching visible size without cropping the icon.
- **Map JSON campaign migration:** campaign geometry is loaded from the
  bundled schema-1 map repository. The temporary explicit assignment maps all
  campaign levels to `demo-map`; there is no fallback to old Kotlin geometry.
- **Map editor export:** the web and Kotlin codecs export only the shared map
  contract. Editing phase, terrain tiles, catapult positions and camera
  anchors are not serialized.
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
  Automatic projectile tracking raises zoom to at least 1.25x, then restores
  the pre-follow scale while the camera returns to the tracked team's catapult.
  A manual zoom during tracking cancels that pending automatic restoration.
  Aiming power uses screen-space pull distance, so the same finger drag keeps
  its strength across zoom levels; the aiming circle and gesture threshold are
  also measured in screen space.
- Soil textures repeat at a base tile width of 256 view units in both the
  Android battlefield and web map preview. Android tiles preserve the source
  aspect ratio and repeat vertically rather than stretching to the soil depth.
- Unit health bars use the rounded track, 8-unit height and health-ratio colors.
  Fortress health is shown only in the top HUD panels; compact reload
  indicators sit beside their catapults.
- Raging Boar world sprites use one shared 368x301 crop across all animation
  frames and both facing directions. Their render height compensates for the
  crop so visible size is preserved while the alpha-bounded feet meet the
  ground baseline.
- Match ammunition slots with no remaining stock or an active reload are
  disabled. A different ready type can still be selected while the current
  type is reloading.
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
  24 container-flight frames (`anim_ammunition_fire_rain_flight_000`–`23`), 12 split-flash
  frames (`anim_ammunition_fire_rain_split_flash_000`–`11`), 24 fragment-flight frames
  (`anim_ammunition_fire_rain_fragment_flight_000`–`23`), and 24 fire-field frames
  (`anim_ammunition_fire_rain_field_000`–`23`). All are transparent 512×512 PNGs under
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
  RGBA PNG frames are imported as `anim_ammunition_fire_rain_fragment_flight_000.png` through
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

- Loadout profile and persistence adapters are implemented using DataStore
  schema v1; first-run values are Fortress Shield, zero gold, and zero
  limited-ammunition/special-spell stock. The screen supports interactive
  selection, normalized-loadout status, item details and guarded commit.
  Campaign progression and Haste/Heal milestone persistence are implemented;
  tutorials remain deferred to TutorialSystem. Runtime ammunition consumption
  is not reconciled with persistent stock.
- `CastSpell` is implemented for `FORTRESS_SHIELD`, HASTE and `RUNIC_BASTION`,
  including mana validation, replay, typed results and Android presentation.
  The other eight spell effects and AI casting are not implemented. HASTE's
  approved AnimGen world-space visual is integrated into Android and rendered
  behind moving HASTED units.
- Shop and result screens remain placeholders.
- `ResultScreen` remains a placeholder; match results are still shown by the
  existing match presentation. Campaign progress is local-only and has no
  cloud synchronization or legacy-install migration.
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

## Urkaaaz Studio

`studio/` is a local developer tool (npm workspaces `server` + `web`), separate
from the game modules; the plan lives in
`docs/architecture/studio-architecture.md`. Step 1 (skeleton) is implemented:
a dependency-free Node server (`node src/main.ts`, loopback-only, rejects
non-loopback `Host` headers) exposing `GET /api/health` (version, repo root, git
branch, working-tree state), and a React/Vite shell with Maps/Animations
tabs (Maps remains a placeholder). API contracts live in `studio/server/src/contracts` and are
imported by the web app as types via `@studio/contracts`. Studio only reports
git state and never commits or switches branches. Run it with
`cd studio && npm run studio`; it is outside `./gradlew test assembleDebug`,
so validate it with `npm test && npm run lint` in `studio/`.

Step 2 (animation visualizer, read-only) is implemented: `GET /api/animations`
parses `anim_<category>_<target>_<type>[_<variant>]_<frame:3>.png` files in
`android-app/src/main/res/drawable-nodpi` (variant = trailing `blue`/`red`;
target = longest prefix listed in `studio/config/animations.json`, otherwise the
last token is the type and `targetConfigured=false`; non-contiguous frames
become `issues`), and `GET /api/animations/{id}/frames/{n}` streams a PNG with
path-traversal checks. `studio/config/animations.json` is maintained by hand:
add new targets there and list sequences without a runtime loader in
`withoutLoader` (currently `anim_ammunition_fire_rain_impact`).

The team-texture animation renderer is shared by units: it combines one neutral
frame sequence per state with per-frame alpha masks and a BLUE/RED texture
configuration in `android-app/src/main/assets/animations/<unit>/`. RED mirrors
the shared neutral frames and masks before applying its texture. The Defender
uses this for idle, move, attack and death; Demolisher currently uses it for
idle (48 shared 256x256 frames) and move (60 shared 256x256 frames).
Demolisher's masks cover its blue-dominant cloth and barrel panels; blue sources
were neutralized in those masked pixels, while skin and equipment remain
unchanged. Studio's
`teamTextureTargets` configuration marks shared sequences and previews both
textures plus a mask overlay. Other Demolisher states and remaining units still
use their existing team-specific assets until migrated. This is presentation
only and does not touch simulation state.

## Push workflow

The project slash command `/push` is defined in
`.github/skills/push/SKILL.md`. Before a commit, it updates relevant knowledge
Markdown files with durable facts about the changes, then runs the validation
gate and pushes `develop` using the configured project GitHub account.

## Map authoring

Map authoring decisions are documented in `docs/map-editor/design.md`,
`docs/map-editor/risks.md` and `docs/map-editor/web-migration-plan.md`.

The local React/Vite profile editor is available under `map-editor-web`. It
shares the strict versioned `MapDefinition` JSON shape and stores drafts only
in browser `localStorage`. It is the only map editor; the Swing editor and its
Kotlin asset adapter have been removed. Run it with `npm install`,
`npm run dev`, `npm run test`, or `npm run build` from that directory. The
web editor uses the shared `game-assets` source, JSON as the single map
definition source of truth, terrain locking before decoration placement, and
validated versioned exports.
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

- Fortress Shield dome art (77 credits, AnimGen Seedance 2.0 fast, first and
  last frame both the user's dome PNG, `alpha_key`): 24 RGBA 640×480 frames
  imported as `anim_spell_fortress_shield_dome_000`–`23` and loaded strictly by
  `BattlefieldAssetCatalog.fortressShieldDomeFrames`. The renderer stretches the
  visible dome bounds (`FortressShieldPresentation`) over the
  `FortressShieldGeometry` ellipse, loops one frame per 6 render ticks and
  keeps a fixed paint alpha independent of shield HP. Soil is redrawn over the
  shield sprite within its bounds, clipped to the terrain surface. The user
  accepted known `alpha_key` artifacts. `SpellPanelView` shares the ammunition
  reload shade to cover the shield icon from top down in proportion to lost HP;
  it clears when the shield is inactive. The approved Fortress Shield hit
  flash is a second 24-frame RGBA 640×480 sequence in
  `anim_spell_fortress_shield_hit_000`–`23`; it plays once on `ShieldDamaged` and returns
  to idle. `RenderStateMapper` routes the hit by fortress team, and the
  renderer mirrors only the blue/left fortress hit sequence because incoming
  enemy projectiles strike its right side. The preview has a colored halo
  outside the dome and some silhouette/position drift in middle frames; the
  user approved it. The first hit-flash generation (also 77 credits) was
  rejected for opaque/clipped frames; the approved retry cost 77 credits.
  Both originals are in `assets_src/spells/fortress-shield/` by animation.
  The AnimGen MCP could not be authorized (the server rejected the Copilot
  app's OAuth client metadata URL), so the REST API with the private key file
  was used.

Map authoring uses the shared `MapDefinition` contract and
`MapDefinitionValidator`, and stores schema-1 JSON. Drafts are written as
`<map>.draft.json` using an atomic replace and never overwrite final export.
The sole editor is the local React/Vite application in `map-editor-web`;
shared Kotlin map-editor domain, format and rendering modules remain available
for contract and tooling use, but there is no desktop UI or desktop asset
adapter.
