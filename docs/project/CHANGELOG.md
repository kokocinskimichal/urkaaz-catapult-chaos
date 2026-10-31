# Changelog

## 2026-10-31

### Added

- Added the Ancient War Oak's 75-frame idle and approved 60-frame rightward
  branch-hand slam. The match screen plays the attack from a simulation event,
  and Studio loads only the explicitly registered runtime sequence.
- Added an always-visible `Show hitboxes` match control for terrain, units,
  fortresses, bastions, bosses, projectiles and map-asset bounds.

### Changed

- Reduced the Ancient War Oak collision box to 432×576 world units and adjusted
  presentation scale to preserve the visible tree size.
- Replaced the shared unit collision radius with type-specific rectangular
  hitboxes sized to about 80% of measured visible body bounds. Simulation
  contact checks and the `Show hitboxes` overlay now use the same profiles.

### Fixed

- Fixed the Oak ignoring melee units stopped outside its attack range. Its
  attack now winds up for 2.291 seconds and applies damage at the animation's
  impact frame while preserving the original cooldown cadence.

## 2026-10-30

### Added

- Implemented the first BossSystem scope for campaign levels 5–50, including
  central objectives, mobile siege bosses, a shared typed balance JSON, Studio
  Balance editing, static boss sprites, and a boss HUD.
- Added the Studio Progress tab for debug presets that can set campaign level,
  lives, and gold.
- Imported the complete 97-frame, 256x256 Demolisher forward-throw export with
  per-frame masks, replacing the incomplete run-only attack in Studio and
  gameplay.
- Imported the shared, effect-only 20-frame Demolisher explosion at 256x256.

### Changed

- Boss encounters now use the shared balance revision and content hash in
  snapshots and replay configuration; campaign progression stops at level 54
  until the remaining boss profiles and assets are available.
- Defender formation rank extends attack reach against bosses without changing
  the one-behind-another formation spacing.
- Split the Studio Animations browser into Units, Spells and Ammunition tabs;
  each tab filters the sequence list while keeping the shared preview controls.
- Demolisher reaches the fortress face, plays the forward-lunge frames while
  moving 90 world units into the target over 0.5 seconds, then explodes
  immediately. Unit-target contacts still explode immediately without the
  throw; gameplay damage is applied at the end of the throw.
- Removed the Demolisher death animation, its team-color masks, pre-impact
  presentation and archived AnimGen outputs in preparation for a new sequence
  at the same 256x256 resolution as the existing idle and move animations.

### Fixed

- Progress presets now apply only selected fields, so setting a campaign level
  does not overwrite existing lives or gold.
- Campaign Studio validates X and Y coordinates independently and serializes
  the expected-hash check and layout replacement across Studio instances.

## 2026-10-29

### Added

- Added the extensible ten-chapter campaign registry with canonical chapter
  biomes and typed fortress/boss landmarks, plus a chapter-based campaign map
  with current/preview chapters, level selection, and a Start Level action.
- Added a confirmed Dashboard reset for local player progress, clearing
  campaign progress, profile balances and unlocks, inventory, loadouts, and
  reward receipts from the shared Preferences DataStore.
- Added and integrated the Chapter 1 map, campaign frame, fortress and boss
  markers, three level-state plaques, status badges, and available-level glow.
  Added copy-ready AGPs for campaign maps and reusable campaign art.
- Added the Studio Campaign tab for repositioning the chapter's ten map
  markers with drag controls and numeric X/Y fields, saving layout JSON shared
  by Studio and the Android game.

### Changed

- Campaign level biome assignment now comes from the canonical chapter
  registry; the campaign map center-crops its art and positions markers using
  the same viewport transform.
- Studio's development launcher now searches for a free API and web port when
  the default ports are already in use.

## 2026-10-28

### Added

- Replaced the Raging Boar's team-specific death sprites with a shared
  24-frame buck-and-fall sequence, per-frame garment masks and the existing
  BLUE/RED team textures.
- Implemented extensible campaign gold rewards for BLUE victories, including
  direct-hit, fast-win, untouched-fortress and fortress-streak achievements
  with atomic campaign/profile settlement.
- Added provisional rising `+X OrCoins` feedback for hit-based rewards and an
  itemized gold total in the victory popup.

### Changed

- Campaign levels already completed are marked and disabled; the fortress
  streak is displayed as “Fortress combo” in the reward summary.
- Tuned the victory reward summary to use a translucent backplate.

## 2026-10-27

### Added

- Replaced the Raging Boar's team-specific idle with one shared 60-frame
  sequence, per-frame garment masks and the existing BLUE/RED team textures.
- Replaced the Raging Boar's team-specific move with a shared 28-frame loop,
  per-frame garment masks and the existing BLUE/RED team textures.
- Replaced the Raging Boar's team-specific attack with a shared 19-frame
  head-jerk sequence, per-frame garment masks and the existing BLUE/RED team
  textures.
- Migrated the map editor into the Studio Maps tab. Studio now lists, validates,
  reads and hash-protects writes to both campaign and Android map JSON copies,
  and serves terrain assets from their source directories without generated
  public copies.
- Added the discoverable `/push` repository command for validation, changelog
  review, committing and pushing changes to `develop`.
- Added a small Sapper bomb flight using the existing Siege Bomb frames. The
  presentation follows an arc to the target after release without changing
  simulation damage timing.

### Fixed

- Extended all 15 Sapper return masks over the head wrap and neutralized the
  added frame regions so BLUE/RED color stays continuous across the cycle.
- Fixed Demolishers stopping at enemy fortresses without detonating. They now
  stop at the fortress front face, explode on contact and apply blast falloff
  against the fortress hitbox for either team.
- Fixed Studio map selection being overwritten by local draft restoration and
  guarded Canvas rendering against incomplete or failed asset images.
- Matched the Studio map preview to the match renderer's independent world-axis
  scaling and fortress/catapult dimensions.

### Changed

- Reduced the saturation of shared BLUE/RED team textures by 20% for all
  masked characters, consistently in Android rendering and the Studio preview.
- Updated Chapter 1 Level 4's terrain profile and decoration placements.

## 2026-10-26

### Changed

- Replaced the Sapper's return animation with a forward-bounding hop cycle.
  Its takeoff, airborne and landing poses visibly express forward travel while
  the cycle returns to its starting horizontal position, leaving world
  movement to the existing simulation. Kept the 15-frame transparent asset
  pipeline, team masks and BLUE/RED textures.
- Replaced the high-bounce return with a cheerful light jog. The new loop keeps
  its vertical motion subtle and preserves the simulation as the sole owner of
  battlefield movement.
- Moved the Sapper's fortress attack stop point to 200 world units in front of
  the enemy fortress hitbox; the strike now occurs before entering its
  footprint for either team.
- Matched the Sapper's idle canvas scale to jog and added a brief jog-to-idle
  transition on arrival, so the Sapper does not stay in jog throughout its
  initial attack delay.
- Smoothed the Sapper's attack-to-turn and turn-to-return size transitions by
  interpolating the turn canvas scale between the neighboring animation scales.

## 2026-10-25

### Added

- Added the proposed Urkaaaz Studio architecture
  (`docs/architecture/studio-architecture.md`): tabbed local tool, local
  backend with safe repository writes, unified asset index, animation
  visualizer and `map-editor-web` migration plan.
- Implemented Studio step 1 (`studio/`): loopback-only Node server with
  `GET /api/health` and a React/Vite tabbed shell (Maps, Animations
  placeholders), with Vitest coverage for the server and web client.
- Implemented Studio step 2: read-only animation visualizer. The server
  exposes `GET /api/animations` and `GET /api/animations/{id}/frames/{n}`; the
  Animations tab shows a category/target/type tree, blue/red variant switch and
  a canvas preview with play/pause, frame stepping, FPS and background controls.
  Targets and sequences without a runtime loader come from
  `studio/config/animations.json`.
- Implemented the Defender team-texture pilot: replaced team-specific animation
  frames with one neutral sequence per state, added per-frame garment-mask PNGs
  and configurable BLUE/RED fabric textures, and added a bounded renderer cache.
  Masks cover blue-dominant garment and shield pixels, including small details
  that previously showed through in gameplay; texture lighting preserves source
  shading without suppressing the palette colors. Studio previews both textures
  and can overlay the generated masks. Masks are an automatic first draft and
  still require visual review.
- Generalized team-texture rendering and Studio previews to support shared
  neutral character sequences. Migrated Demolisher idle (48 frames) and move
  (60 frames) to shared 256x256 frames with per-frame masks; the opponent now
  uses mirrored shared frames recolored through the RED palette instead of
  separate red sequences.
- Replaced Demolisher death with a shared 32-frame, 768x432 transparent
  sequence: the existing forward dive is followed by a back-mounted powder
  barrel explosion. Added per-frame cloth masks so BLUE and mirrored RED use
  the existing team-texture renderer; both teams retain the unit through the
  full death animation. The renderer uses a death-specific canvas scale so the
  character matches the visible size of its 256x256 idle/move animations.
- Added the Sapper's shared 30-frame, transparent 256x256 idle animation:
  he tosses and catches his bomb, BLUE/RED appearance is applied through
  per-frame masks and the existing team fabric textures, and RED mirrors the
  BLUE-derived neutral frames. Added a matching 15-frame shared 256x256 jog
  sequence with per-frame garment masks; RED mirrors and uses its team texture.
  Added a 15-frame overhand bomb-throw attack that ends at release, leaving
  projectile travel and impact to the existing separate presentation.
  Idle, jog and attack use state-specific canvas scales and bottom offsets to
  keep the character size and ground anchor consistent.
- Added the Sapper's shared 15-frame turn and bomb-less hopping return
  animations. The turn plays once on entering `RETURNING`, followed by a
  looping return cycle; the paused, per-unit presentation clock leaves
  simulation movement unchanged. Studio previews both sequences with team
  masks and textures.
- Demolisher death presentation predicts imminent unit contact and starts its
  run-and-dive early, holds before the explosion until simulation-confirmed
  death, and cancels the pre-roll when the predicted contact is no longer
  plausible.

- Added `/loadContext` and a curated Markdown index for loading the core
  project context and task-specific documentation in one command.
- Implemented HASTE for deployed allied units: a 15-second 2× movement-speed
  buff, refreshable without stacking, with deterministic snapshots and a
  match-HUD countdown. Integrated the user-approved 12-frame AnimGen
  orbiting-chevron effect behind moving HASTED units, replacing the earlier
  wind-trail and quickstep runtime sprites.
- Added a copy-ready AGP brief and source image for the HASTE orbiting-chevron
  effect; retained its source image and all AnimGen output sets under
  `assets_src/spells/haste/`.
- Added animated Fortress Shield dome and hit-flash presentation. The flash
  starts at its first clearly visible frame and remains visible when a hit
  depletes the shield.
- Assigned the visual Frostbound biome to Chapter II campaign levels 11–20.
- Added Runic Bastion as a destructible 360 HP gate defense with creation,
  idle-rune and destruction animations; units and projectiles can attack it.

### Changed

- Unified the Defender death animation resolution: both teams' 120 frames were
  downscaled from 512×512 to 256×256 like every other Defender sequence, with
  identical framing and no renderer change.
- Increased Fortress Shield durability from 80 to 150 HP.
- Runic Bastion cannot be recast while its previous structure remains; its HUD
  icon stays dimmed until destruction, and attackers wait through the
  destruction animation.
- Standardized the filenames of 1,394 Android animation frames across 43
  sequences and updated their runtime lookups, tests and asset manifests.

### Fixed

- Matched the Runic Bastion destruction animation's rendered size and ground
  anchor to its creation and idle animations.
- Corrected the renamed projectile-animation prefixes for Siege Bomb, Powder
  Barrel and Plague Cauldron so their flight and impact frames load.

## 2026-10-23

### Added

- Added a subtle Retreat control with a resized 256 × 256 runner icon beside
  the match timer. It pauses the match while confirmation is open; confirming
  ends the match as a defeat, while cancelling restores the prior
  running/paused state.
- Added matching normal and pressed Send Wave button artwork and integrated it
  as a square 66 × 66 dp control without a text label.

### Changed

- Refined the match dock: unit, ammunition and spell choices use 60 dp slots
  with 20 dp cost/stock badges, spell status text is replaced by visual and
  accessibility states, and the Supply/unit group is wider than the spell/Mana
  group.
- Added a demo-only Loadout flow with independent persistence and schema
  versioning, making the full catalog selectable without changing
  campaign-level loadouts.
- Replaced the Mana orb shell with the supplied transparent gold-filigree
  asset; centered the live Mana count and changed the liquid to deep blue.
- The Supply gauge now uses the approved crate icon, with its live count
  centered over the icon, a smoothly animated progress ring, and the same
  80 × 80 dp footprint as the Mana orb.
- Match ammunition slots now show stock badges (including an infinity icon for
  unlimited stock) and an overlaid reload countdown/progress. Changing types
  restarts that type's reload, and the AI waits for the selected type to finish
  loading before firing.
- Replaced the visible wind-strength text with strength-specific wind icons,
  enlarged the icon, and reduced the match health/timer panels.
- Mana now regenerates in 1-point steps every 0.8 seconds instead of 10-point
  steps every eight seconds.

### Removed

- Removed the Swing desktop map editor and its Kotlin asset adapter; the local
  React/Vite editor is now the only map-authoring UI.

## 2026-10-21

### Added

- Implemented the first SpellSystem gameplay slice: centralized spell
  definitions, deterministic mana regeneration, typed `CastSpell` results,
  Fortress Shield activation and projectile interception, shield snapshots,
  replay support, shared shield collision/render geometry, and match
  HUD/rendering integration.
- Added a unified match HUD redesign prompt covering the normal match and
  spell-targeting states.

### Fixed

- Campaign victories now deliver queued `MatchFinished` events even when the
  match screen observes an already-finished snapshot; a tick that ends the match
  returns its final snapshot without a spurious `requireRunning()` failure.
- `NEXT LEVEL` dispatches the action already enabled by the persisted campaign
  completion state instead of rechecking potentially stale render state.

### Changed

- Reorganized the match HUD into coordinated top status zones and a three-part
  command dock with Supply, loadout-filtered units/ammunition/spells, and a
  liquid-filled Mana orb. Added transient, text-only spell feedback and
  generalized activation through `MatchUiAction.CastSpell`.
- Added a reusable `HealthCardView` for BLUE and RED fortress indicators; the
  top health cards, timer module, and shared command dock use the same
  stretchable universal frame.
- Added subtle programmatic elevation shadows to the top health cards, timer
  module, and shared dock, plus a text shadow for transient feedback.
- Kept the three dock groups and pause control visually distinct with subtle
  UI-drawn dividers instead of separate panel frames. Alternate slot and
  dock-group frame assets are prepared but not used by the live HUD.
- Matched the widths of the Supply/unit and spell/Mana dock panels while
  keeping ammunition centered between them.
- Split dock headings so `SUPPLY`, `UNITS`, `AMMUNITION`, `SPELLS`, and `MANA`
  align with their respective controls.
- Centralized maximum Supply with the domain resource configuration so the
  simulation and HUD use the same capacity.
- Routed Loadout spell metadata through the canonical domain catalog and
  exposed the selected ammunition and spell state in the in-match HUD.
- Reduced early campaign AI pressure: levels 1–10 now use CADET, with wider aim
  error and an 8-second interval between unit recruitments; VETERAN starts at
  level 11 and ELITE at 31.
- Set initial and maximum supply to 20 for both teams; post-spend regeneration
  replenishes one point every three seconds.
- Automatic projectile tracking now restores the prior zoom while the camera
  returns to the firing team's framing.
- Restored both campaign fortresses to the standard 500 HP; the demo mode's
  existing BLUE fortress health bonus is unchanged.
- Configured immersive full-screen mode to hide Android system bars.

## 2026-10-20

### Added

- Added generation prompts for all 11 spell-card illustrations, the Loadout
  header frame, and the fixed ROCK icon.
- Added dedicated card artwork for all 11 spells and three Loadout summary
  category icons.
- Added normal/pressed XS gold button backgrounds for compact BACK, NEXT LEVEL,
  and match restart actions.
- Imported the complete red button-background family, matching the gold
  standard sizes and XS length variants, with normal/pressed selectors.
- Imported the complete dark button-background family in matching sizes, with
  normal/pressed selectors.
- Applied the existing Cinzel SemiBold typeface to all live-text button
  controls.
- Imported the shared match-result popup panel with a transparent illustration
  window for the Victory/Defeat UI.
- Imported the Victory and Defeat title plaques as separate transparent PNGs
  and layered each beneath its live outcome title in the popup.
- Imported and layered separate Green Frontier Victory and Defeat
  illustrations into the popup's framed window.
- Added the Victory/Defeat in-match popup overlay, with live level/biome labels,
  persisted-progress gating, retry and context-appropriate return actions.
- Campaign victories now offer NEXT LEVEL after progress is saved, opening the
  newly unlocked level's Loadout.

### Changed

- Completed the Loadout visual update with dedicated ammunition-card art,
  ROCK's fixed-slot marker, a framed live title/level header, global Cinzel
  SemiBold typography, and category panels sized to their cards.
- Reworked the Loadout footer into a live, icon-based summary and retained the
  original 220 x 54 dp Start Match button with its supplied backgrounds and
  dark, bold label.

## 2026-10-19

### Added

- Campaign progress is now stored independently from player loadout data.
  Winning levels unlock the next campaign level and apply Haste/Heal milestones
  at levels 5/7, with the spells available from levels 6/8.
- Loadout unit carousels now use five dedicated full-card illustrations.
- Loadout ammunition carousels now use five dedicated full-card illustrations.
- Campaign opponents now use extensible CADET, VETERAN and ELITE AI profiles,
  assembled from registry-selected catapult, recruitment and wave strategies.
  ELITE progression is bounded through level 100 without resource or unit-stat
  bonuses.
- Match sessions now capture a versioned, deterministic replay with its
  explicit random seed, initial configuration, accepted commands and lifecycle
  transitions; the replay can be run again in-process.

### Changed

- Applied the Loadout cards' Cinzel SemiBold font across Android game text,
  including custom Canvas labels and match HUD overlays.
- Resized Loadout category panels to fit their headers and card rows, retaining
  evenly spaced horizontal margins.
- Loadout unit, ammunition and spell options now share horizontal carousels
  with equal-sized cards and available options first. ROCK stays fixed in the
  first ammunition slot; other ammunition uses the same toggle selection
  behavior as units and spells. Selected cards use a glowing gold frame and
  check mark.
- Loadout selection frames, check badges and information buttons now use
  supplied transparent PNG artwork instead of vector placeholders.
- Removed the stray cropped line from the left edge of the Loadout check badge.
- Removed the visible "AVAILABLE", "FIXED" and "UNLIMITED" labels from
  Loadout cards and prepared an asset-generation prompt for an
  unlimited-ammunition icon.
- Integrated the supplied infinity icon for unlimited ammunition cards.
- Added the supplied fixed-slot lock icon after ROCK's unlimited marker.
- Added the supplied Loadout header frame and aligned the title and live level
  label with its separate text areas.
- Removed obsolete duplicate unit-animation frame sequences that are no longer
  loaded by the battlefield asset catalog.
- Unit, ammunition and spell cards now share a centered Cinzel SemiBold title
  over a slightly translucent dark bottom strip; long names reduce their font
  size before truncation.
- Level-unavailable and locked Loadout cards are desaturated and show the
  supplied grayscale lock icon instead of availability/locked labels.
- The match unit panel hides unit icons unavailable to the selected campaign
  level or committed player loadout.
- Increased CADET AI aim error to ±18° and ±30 power so its shots are less
  accurate.
- Unit attacks and ammunition now use seeded damage variation clamped to
  80%-120% of base damage. Lingering ammunition effects reuse their impact
  roll for every tick.
- Each unit receives a seeded delay of up to half its attack cooldown before
  its first attack; subsequent attack timing is unchanged.
- Reduced initial and maximum supply from 20 to 10 for both teams; supply
  continues to regenerate by one every three seconds.
- Campaign `POWDER_BARREL` ammunition now unlocks at level 10.
- Campaign opponents now use only units and ammunition available on the
  selected level; the player's committed loadout may further restrict the BLUE
  team.

## 2026-10-17

### Added

- Completed FIRE_RAIN battlefield presentation with the approved animations
  for container flight, apex split, shard flight and ground fire; original
  AnimGen exports are preserved under `assets_src/ammunition/fire-rain/`.

### Changed

- FIRE_RAIN container and fragment speeds are halved, and its ground fire now
  lasts 9 seconds while retaining the existing 500 ms damage tick interval.
- Halved flying projectile collision hitboxes for every ammunition type;
  impact and damage radii are unchanged.

### Fixed

- Manual camera pan or zoom no longer disables BLUE projectile tracking for
  the rest of the match; tracking resumes at the next launch, and an
  incomplete pan gesture does not suppress it.
- Aiming pull strength and the aim-versus-pan threshold now use screen-space
  distance, so camera zoom does not reduce the force from the same finger drag.

## 2026-10-18

### Added

- Added a versioned local progression profile and per-level loadout storage;
  campaign matches start only after the loadout is validated and saved.

### Changed

- Restored FIRE_RAIN's standard launch speed multiplier of 8 and reduced its
  horizontal fragment spread per step from 0.18 to 0.09.
- Halved SIEGE_BOMB flight and impact render sizes; gameplay collision and
  damage values are unchanged.
- PLAGUE_CAULDRON reuses its impact frames for a growing, 50%-opaque cloud
  that follows terrain at 75% wind drift; its gameplay radius is now 300 units.

### Fixed

- Campaign ammunition selection now lists only level-available ammunition
  equipped in the committed loadout, preventing rejected selections from
  crashing the match screen.
- FIRE_RAIN fire-field playback no longer loops through its extinguishing
  frames, avoiding a visible burnout and relight during the burn.
- ROCK impact frames remain at flight size until the rock breaks apart, then
  expand to the impact size.

## 2026-10-16

### Added

- Restored Fire Rain's apex split into six independently simulated fragments
  using a typed flight profile and a deterministic projectile-split event.
- Imported the user-approved AnimGen Fire Rain container-flight sequence.
- Imported the user-approved AnimGen Fire Rain split-flash sequence.
- Added generated idle, jog, combat and death animations for both Defender
  teams, plus blue/red Demolisher idle and jog animations and the accepted blue
  Demolisher contact explosion.
- Added per-unit death-animation playback and retention so defeated units stay
  visible until their presentation sequence completes.

### Changed

- Melee units now stop at a shared visual standoff distance while remaining in
  attack range, with wider spacing for additional formation ranks.
- Manual battlefield panning persists across match-phase changes.
- Increased the Slingmaster's visible unit-panel icon size to match the other
  unit icons without cropping its artwork.

## 2026-10-15

### Added

- Chapter 1, Level 3 now uses the imported Green Frontier map with its authored
  terrain, fortress foundations and tree decoration; the map is bundled for
  Android offline loading.
- The web map editor now provides a graphical Trees, Rocks, Props and Relics
  palette that can place assets after terrain is locked.
- Chapter 1, Level 4 now uses its imported Green Frontier map, bundled for
  Android offline loading with its authored terrain, foundations and relics.
- The map editor palette now includes every imported legacy tree, rock and
  prop variant across Green Frontier, Frostbound, Copperwood and Ashen March;
  Sunken Marshes shares Green Frontier's decoration set.
- Selected map objects can be moved directly on the canvas, and corner
  handles scale and rotate decorations and Relics around their centers.
- Ten transparent medium Relic assets are shared across all biomes and are
  available in the map editor and Android battlefield renderer.

### Changed

- Campaign maps preserve an initial camera anchor and zoom; Level 3 opens on
  the player fortress at 1.3× while other maps retain their current framing.
- The map editor layers regular decorations below the terrain cap and buried
  Relics above it; regular decorations may be positioned freely within map
  bounds, while Relics remain constrained to the soil.
- Map-editor decoration previews now preserve source-image proportions and use
  the same 94-pixel base width as the Android renderer.
- Map-editor foundation controls raise or lower each fortress and catapult
  together, smoothly adjusting adjacent terrain samples.
- The map editor can raise or lower the whole authored scene relative to its
  fixed background, moving terrain, foundations, protected zones and assets as
  one undoable operation.
- Rebuilt the fortress hitbox as one alpha-bounds rectangle shared by both
  fortress sprites and removed fortress-name labels from the hitbox debug view.
- Pinch zoom now anchors on the gesture focus, supports up to 4x zoom, and
  temporarily takes precedence over automatic camera tracking.
- Soil tiles render at a base width of 256 view units in the Android game and
  web map preview; the Android renderer preserves tile proportions and repeats
  the texture vertically.
- Fortress and unit HP bars share one visual style, with the fortress bar
  constrained to its sprite and reload indicators reduced and moved beside it.
- Fortress world dimensions are now 672x504 units, 20% larger than the doubled
  560x420 size; sprite, collision and protected foundation geometry stay linked.
- Catapult sprites now scale with the fortresses, preserving their relative
  size in both the match screen and map editor.
- On full-size maps, the first garrison slot is now 690 world units from each
  edge (up from 250) and slots use 70-unit spacing (up from 40); compact maps
  clamp the first slot to keep it clear of the midpoint.
- The web map editor's Debug panel can show live world coordinates beside the
  mouse cursor, using the current zoom and pan.
- The Android demo's Debug tools can show world coordinates beside a mouse
  cursor, accounting for the battlefield camera zoom and offset.
- Both fortresses are lowered by 35 world units together with their catapults,
  firing anchors and collision geometry; anchors stay above local terrain and
  protected terrain foundations remain at their authored heights.
- Catapult sprites are 20% smaller in the match and map-editor previews.
- Catapults remain 200 world units higher and 70 units farther outward, behind
  the fortress sprites; their projectile launch and aiming origins are restored
  to their previous fortress-relative positions.
- The terrain cap now renders in front of fortress sprites so the ground edge
  can overlap and conceal their lower portions.
- Cropped all blue- and red-facing Raging Boar animation sprites to one shared
  ground-aligned canvas while preserving their visible render size.
- The demo now starts with light eastward wind, and projectile wind strength
  is increased consistently in the simulation and trajectory preview.
- Rebalanced ammunition and unit health; the AI now avoids ammunition that
  cannot damage the opposing fortress.
- Fortress collision bounds are inset to 90% of their previous dimensions.

### Fixed

- Entity-impact visuals now use the reported collision coordinates instead of
  the fortress center.

### Removed

- Removed Cluster Bomb, its fragmentation behavior and projectile metadata,
  UI/rendering paths, tests, and 97 app image assets.
