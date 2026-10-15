# Changelog

## 2026-10-15

### Changed

- Rebuilt the fortress hitbox as one alpha-bounds rectangle shared by both
  fortress sprites and removed fortress-name labels from the hitbox debug view.
- Pinch zoom now anchors on the gesture focus, supports up to 4x zoom, and
  temporarily takes precedence over automatic camera tracking.
- Soil tiles render at a base width of 256 view units in the Android game and
  web map preview; the Android renderer preserves tile proportions and repeats
  the texture vertically.
- Fortress and unit HP bars share one visual style, with the fortress bar
  constrained to its sprite and reload indicators reduced and moved beside it.
- Fortress world dimensions are restored to 280x210 units; simulation snapshots
  now provide fortress positions and foundations so sprites and hitbox previews
  use the same world-space geometry as collision.
- Cropped all blue- and red-facing Raging Boar animation sprites to one shared
  ground-aligned canvas while preserving their visible render size.
