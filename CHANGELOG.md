# Changelog

## 2026-10-19

### Added

- Campaign progress is now stored independently from player loadout data.
  Winning levels unlock the next campaign level and apply Haste/Heal milestones
  at levels 5/7, with the spells available from levels 6/8.
- Loadout unit carousels now use five dedicated full-card illustrations.
- Campaign opponents now use extensible CADET, VETERAN and ELITE AI profiles,
  assembled from registry-selected catapult, recruitment and wave strategies.
  ELITE progression is bounded through level 100 without resource or unit-stat
  bonuses.
- Match sessions now capture a versioned, deterministic replay with its
  explicit random seed, initial configuration, accepted commands and lifecycle
  transitions; the replay can be run again in-process.

### Changed

- Loadout unit, ammunition and spell options now share horizontal carousels
  with equal-sized cards and available options first. ROCK stays fixed in the
  first ammunition slot; other ammunition uses the same toggle selection
  behavior as units and spells. Selected cards use a glowing gold frame and
  check mark.
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
