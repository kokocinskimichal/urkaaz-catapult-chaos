# Fire Rain AnimGen Prompts

The animation prompts below are for AnimGen. Do not create animation frames.
The user generated and supplied the single-shard source image in a separate
image tool, using the Fire Rain icon only as a visual reference. Do not create
or edit that image here. The composite icon was not sent to AnimGen for
fragment flight; use the supplied isolated shard image as its first frame. Do
not use existing `fire_rain_flight_*` or `fire_rain_impact_*` frames. The
fire-field phase also needs a separate still source image from the user's
external image tool; its prompt is included below.

The four phases form one neutral set shared by both teams. Simulation moves
the objects through world space; generated animation keeps each subject
centered and animates only its local appearance. Choose duration, canvas,
resolution, frame count, FPS, transparency mode and export format from the
live AnimGen model capabilities and renderer requirements, not from fixed
values in this document.

## Shared prompt suffix

Append this to each phase prompt:

```text
Create a clean 2D game sprite animation matching the visual material and
palette of the supplied Fire Rain ammunition reference. Keep a fixed
orthographic side-view camera and a stable subject scale and anchor on every
frame. Keep the requested subject centered; do not move it across the canvas
because gameplay supplies world-space motion. Use a transparent background
when supported by the selected model and preserve clean alpha edges. No
environment, scenery, text, labels, watermark, camera motion, zoom, extra
characters, or unrelated objects. Keep the silhouette fully inside the
canvas with generous transparent margins.
```

## 1. Container flight

```text
Animate only the central Fire Rain canister shown in the supplied visual
reference. Preserve its compact dark forged-metal body, warm bronze bands,
recognizable opening and restrained orange ember seams. Create a subtle,
readable in-place axial roll and gentle ember pulse suitable for a short
seamless flight loop. Keep the canister's center and overall dimensions
stable. Do not show the full composite ammunition icon, multiple canisters,
an explosion, falling debris, or a large flame trail.
```

## 2. Split flash

```text
Create a brief one-shot Fire Rain breakup flash: a compact hot orange-white
core expands into an irregular amber flare with a few tiny sparks, then
quickly dissipates to transparency. This effect is only the flash at the
split point. Do not show a canister, persistent stone fragments, a ground
impact, smoke that hides the effect, or a large screen-filling explosion.
```

## 3. Fragment flight

### Single-fragment source image (for the user's separate image tool)

```text
Create one standalone 2D game-sprite image of exactly one Fire Rain fragment:
a small, irregular, faceted shard of dark charcoal stone with a few thin,
restrained glowing orange fissures and a subtle ember core. Use the attached
Fire Rain ammunition icon only as a reference for material, palette, lighting,
and rendering style. Do not copy its composite layout or include its canister,
other fragments, explosion, flame trail, smoke, ground, scenery, interface,
text, logo, border, or watermark. Show a single complete shard, centered and
fully inside a square canvas with generous transparent margins. Keep the
silhouette crisp and readable at small battlefield scale. Use a transparent
background with clean alpha, not a checkerboard. Return one image only, not
an animation, sprite sheet, collage, or set of variations.
```

The user supplied this source image. It was verified as a 1254×1254 RGBA PNG
with transparency and selected as AnimGen's required first frame. Use
input-canvas expansion to leave safe animation margins.

```text
Animate one isolated Fire Rain fragment: a small, irregular, faceted dark
stone shard with a few restrained glowing orange fissures and a faint ember
core. Give it a subtle, slow tumbling loop and mild ember flicker while its
center and scale remain fixed. The sprite must contain exactly one shard;
simulation supplies six independent fragment positions and trajectories.
Do not show the canister, a cluster of rocks, a flame trail, or a background.
```

## 4. Fire ignition, loop and fade

### Fire-field source image (for the user's separate image tool)

```text
Create one standalone 2D game-sprite image of a compact Fire Rain ground-fire
patch. Show low, ground-hugging orange and amber flames rising from a small bed
of dark charcoal embers, matching the material, palette, and restrained
glowing fissures of the attached Fire Rain ammunition icon. Keep the whole
patch centered, fully visible, and compact on a square canvas with generous
transparent margins on every side. Use a fixed orthographic side view and a
transparent background with clean alpha, not a checkerboard. Include only the
fire patch and embers: no canister, falling fragments, impact flash, smoke
cloud, terrain, horizon, scenery, interface, text, logo, border, or watermark.
Return one still image only, not an animation, sprite sheet, collage, or set
of variations.
```

The user supplied a transparent 1254×1254 PNG for this phase and selected it
despite its taller flame plume and visible shards. It was inspected and
uploaded as AnimGen's first frame; the animation prompt asks the tall flare to
settle quickly into a compact ground fire. Do not substitute an existing impact
frame. The first 24-frame generation was rejected because frames 01 and 02
touch the top canvas edge. A revised generation used a transparently padded
first frame and `powder_barrel_impact_09` as an additional visual reference.
It produced a brief explosion followed by the low fire patch and fade, but
frames 03 and 04 still touch the canvas edge. The user rejected this preview;
it was not imported. The user requested that no Powder Barrel frame be used
in the next attempt. The following 24-frame no-reference generation used only
the padded Fire Rain first frame and succeeded for 45 credits. All frames are
512x512 RGBA, sequential, and remain within the canvas with at least 138 px of
transparent margin; the final frame is fully transparent. The preview is
user-approved and imported as `fire_rain_field_00.png`–`23.png`; the original
ZIP and MP4 are preserved under `assets_src/ammunition/fire-rain/fire-field/`.
These are runtime resources and are loaded by `BattlefieldAssetCatalog` for
the integrated Android battlefield renderer.

```text
Create one small, ground-hugging Fire Rain flame patch over a restrained bed
of dark embers. The opening frames show a quick ignition; the middle frames
settle into a low, readable flame cycle that can loop without changing the
patch footprint; the final frames gradually extinguish into dim embers and
transparency. Keep the bottom-center ground anchor fixed. The effect must
remain compact and readable at battlefield scale; do not show falling
projectiles, a large explosion, scenery, or flames extending beyond the
local patch.
```

## Optional negative prompt

Use only if supported by the selected model:

```text
Opaque background, scenery, landscape, camera movement, camera zoom, subject
drifting, changing scale, clipped silhouette, cropped effects, text, labels,
watermark, extra characters, unrelated props, multiple canisters, duplicate
shards, oversized explosion, dense smoke obscuring the sprite, hard opaque
halo around transparent edges.
```

## Generation gates

- Check the live model catalog before selecting an input mode. Attach the
  ammunition icon only as a supported reference image; do not create or edit
  per-phase source images on the user's behalf.
- If the model requires a distinct first-frame image and there is no
  user-approved source image for that phase, stop and ask for a source-image
  decision.
- Quote one phase at a time and obtain explicit approval for its current
  quote before creating a paid task. A generated preview requires separate
  approval before import.
- A prompt change or changed request setting requires a fresh quote and
  approval. These prompt drafts do not approve a quote, generation, preview,
  or import.
