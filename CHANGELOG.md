# Changelog

## 2026-10-15

### Added

- Chapter 1, Level 3 now uses the imported Green Frontier map with its authored
  terrain, fortress foundations and tree decoration; the map is bundled for
  Android offline loading.
- The web map editor now provides a graphical Trees, Rocks, Props and Relics
  palette that can place assets after terrain is locked.

### Changed

- Campaign maps preserve an initial camera anchor and zoom; Level 3 opens on
  the player fortress at 1.3× while other maps retain their current framing.
- The map editor layers grounded decorations below the terrain cap and buried
  Relics above it, with placement validation for flat ground and soil.
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
