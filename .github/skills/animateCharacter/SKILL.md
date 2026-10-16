---
name: animateCharacter
description: Generate and integrate character idle, jog, combat, and death animations using the AnimGen Public API.
argument-hint: "[character name]"
---

# /animateCharacter — AnimGen character animation workflow

Use this skill when the user asks to generate or integrate character
animations for Urkaaaz. It uses the official AnimGen Public API and the
existing Android battlefield asset catalog and renderer.

## Scope

Work on one character at a time unless the user explicitly names several.
Generate four animation types, each in two team-specific variants, for eight
separate animation sequences total:

1. `idle`;
2. `jog` — the character's trucht/movement cycle;
3. `combat` — one clearly described attack action;
4. `death` — a non-looping defeat animation.

For every type, create both:

- the player's **left/blue** variant, based on the approved left/blue source
  image;
- the opponent's **right/red** variant, based on the approved right/red
  source image.

These are distinct source artworks, not one animation recolored after
generation. Preserve each variant's team colors, facing direction, equipment,
and other approved visual differences. Keep the paired animations consistent
in framing, scale, canvas, ground anchor, action timing, and motion style.

The skill owns the end-to-end workflow: clarify the intended motion, prepare
and quote API requests, generate and review the outputs, validate frames,
then wire user-approved assets into the existing Android presentation. It
does not change gameplay rules, balance, or simulation state.

## Conversation contract

Ask one question at a time. Reuse information the user has already provided.
Do not begin a paid generation until the user has approved its current quote.
Do not import or wire generated frames until the user has approved the
preview.

For each character:

1. Identify the character and whether it already exists in the game.
2. Identify the exact source images to animate: one left/blue player image and
   one right/red opponent image. Prefer existing approved character assets or
   user-provided transparent first-frame images. Confirm that each image has
   the correct team palette and facing direction. Do not upload either image
   until it is a selected reference.
3. Ask how the `idle` animation should look.
4. Ask how the `jog` animation should look.
5. Ask how the `combat` animation should look, including the attack motion and
   any weapon or visible effect.
6. Ask how the `death` animation should look, including its final pose.
7. Ask only for remaining visual decisions needed to write the four prompts,
   such as facing direction, camera angle, palette, or an important silhouette
   detail.

Never combine the four appearance questions into one. If the user has not
specified a visual detail, ask rather than inventing a character redesign.
Preserve each source variant's character identity, costume, proportions, team
palette, facing direction, and rendering style. Keep the camera fixed and the
character readable at battlefield scale.

## AnimGen API safety and prerequisites

The Public API is asynchronous and generation/export requests are paid.
Before using it:

- Read the current official API reference; do not rely on remembered model IDs,
  prices, model limits, or endpoint behavior.
- Require `ANIMGEN_API_KEY` from a private environment or secret manager. On
  macOS, if the environment variable is unavailable, read the key from the
  user's login Keychain generic password item with service name
  `AnimGen API Key` (for example, `security find-generic-password -s
  "AnimGen API Key" -w`). Keep the value in process memory or pipe it directly
  to the API client; never print it, include it in command arguments, or commit
  it. Do not write it to a file unless the user explicitly chooses the
  documented plaintext-file fallback below. The key must have
  `animations:read` and `animations:write` scopes. Never ask the user to paste
  the key into chat.
- If the user explicitly chooses a local plaintext secret file instead, allow
  `~/.config/animgen/api-key` as a fallback. Require that it be outside the
  repository, excluded from cloud-synced folders, and readable only by the
  current user (`0600` file permissions and a private parent directory).
  Read it without printing or copying its contents elsewhere; remind the user
  that this is plaintext storage and can be revoked after the task.
- Use only the selected character image and animation prompt as API input. Do
  not send repository source code, unrelated assets, or secrets.
- Check account eligibility and available credits. Do not purchase credits or
  continue when balance, permissions, or limits prevent the requested work.
- Obtain a fresh quote for every paid animation task and show the quoted
  credits before asking the user to approve that task. A quote is not a price
  lock. Re-quote immediately before creation; if the quote increased, ask for
  approval again. Do not treat a local approval amount as a server-enforced
  spend cap.
- Generate one animation task at a time so the user can review each result and
  so account queue limits are respected.
- Keep request state, prompts, task IDs, and source data in a private
  session-scoped directory outside the repository. Never log raw request
  bodies, API headers, signed download URLs, or credentials.

Use the current model catalog (`GET /models`) to choose an enabled model that
supports the selected first-frame input and required settings. Do not hardcode
a provider/model ID. Use the documented AnimGen base URL
`https://api.animgen.com/v1` unless the user has explicitly configured a
trusted alternative.

## Request lifecycle

1. Inspect both source images locally. Confirm each one's format, dimensions,
   alpha channel, visible bounds, team palette, facing direction, and
   available padding.
2. Query `GET /models`, `GET /account`, and `GET /credits/balance`. Choose a
   compatible model based on its live modes, resolutions, ratios, durations,
   and transparency capabilities.
3. Upload each selected team source once with `POST /files` (multipart field
   `file`) and use its returned public `file_id` as that side's
   `input.first_frame`. Uploading is not a paid generation, but it sends the
   selected art to AnimGen; do not upload unrelated or unapproved files.
4. Build one request for one animation type and one team side at a time. Use
   `frames_zip` so frames can be inspected and integrated individually. Use
   the exact same request body for the quote and creation. Values in angle
   brackets below are placeholders; replace them with concrete values of the
   types required by the live schema before sending:

   ```json
   {
     "input": {
       "first_frame": {
         "type": "file",
         "file_id": "<uploaded-file-id>"
       }
     },
     "prompt": "<user-approved motion description for this team variant>",
     "negative_prompt": "<only when supported by the selected model>",
     "video": {
       "model": "<live model id>",
       "duration_seconds": "<supported duration>",
       "ratio": "<supported ratio>",
       "resolution": "<supported resolution>",
       "input_canvas": {
         "enabled": true,
         "aspect_ratio": "follow_output",
         "background": "transparent",
         "source_scale": "<chosen value from 0.5 to 1.0>",
         "position_x": 0.5,
         "position_y": "<chosen normalized anchor>"
       },
       "transparency": {
         "mode": "alpha_key",
         "key_selection": "auto"
       }
     },
     "selection": {
       "mode": "full"
     },
     "export": {
       "output_formats": ["frames_zip"],
       "frame_count": "<chosen count>",
       "output_width": "<chosen width>",
       "output_height": "<chosen height>",
       "transparent": {
         "enabled": true
       }
     }
   }
   ```

   Include only fields supported by the selected model and the current API
   schema. If the source lacks meaningful transparency, do not claim that
   `alpha_key` will produce clean transparent frames; ask for an approved
   transparent source or a decision about another background-removal workflow.
5. Call `POST /animations/quote`, show the current quote and available balance,
   and wait for explicit user approval before calling `POST /animations`.
   Obtain approval for every paid task, including both variants of each
   animation type. The create request requires a unique `Idempotency-Key` of
   8–200 characters. Persist that key with the unchanged request and returned
   task ID.
6. Poll `GET /animations/{id}` using `Retry-After` and a bounded overall
   deadline. `202` means queued, not complete. At every terminal state inspect
   all available outputs, including partial outputs on failure or cancellation.
7. Download output assets by their asset IDs. Treat signed URLs as temporary
   credentials: never log them and never forward the API key to a signed URL
   or redirected storage host. If a response is uncertain, resume the same
   task with its original key and unchanged body; do not silently create a new
   paid task.
8. Show the extracted animation preview and wait for explicit approval before
   importing it. Review each side individually and compare its pair for
   matching style, scale, framing, timing, and action readability. If a result
   needs a new motion prompt or changed settings, obtain a new quote and
   approval for the new paid request.

## Frame count, resolution, and safe canvas

Choose technical settings yourself after inspecting the source, the motion
brief, the live model catalog, and the renderer. Do not ask the user to pick
frame counts or dimensions unless an API limitation forces a choice.

- Use one consistent output canvas size, aspect ratio, subject scale, and
  ground anchor across all eight sequences and both team variants.
- Predict the full motion envelope, not just the source pose. Leave room for
  extended weapons, limbs, jumps, recoil, a horizontal fall, and the death
  pose. If a motion extends sideways, use a supported landscape ratio rather
  than squeezing or cropping it.
- Enable input-canvas expansion when the model supports it. Select
  `source_scale` from the documented range based on the measured character
  bounds and expected motion; use a smaller subject scale when more safety
  margin is needed. Keep the bottom-center gameplay anchor stable while
  allowing enough transparent space below and around poses.
- Select output width and height independently within the API's live limits.
  The current documented export range is 64–1024 pixels per dimension; use
  the smallest size that preserves the accepted source detail at game scale,
  increasing it when the motion envelope or source detail requires it.
- Choose frame count from the action duration and the renderer's playback
  cadence. Use the same frame count for the two variants of each animation type.
  Prefer a smooth, coherent loop for idle and jog, one readable attack for
  combat, and a non-looping sequence with a stable final pose for death. Keep
  frame counts within the current renderer's supported range, or update the
  loader and tests if a larger count is justified.
- The current API supports `frame_count` from 1 through 240 and output
  dimensions up to 1024 pixels, but model-specific limits take precedence.
  Do not interpolate or duplicate frames to disguise an insufficient
  generation.
- Never crop frames to make them fit. If any pose touches the canvas edge,
  loses part of the character, weapon, or effect, or shifts the feet/anchor,
  reject that sequence and regenerate with a larger canvas or smaller subject
  scale.

## Motion prompts

Write four motion descriptions from the user's answers, then prepare a
side-specific prompt for each description and each source image. Each prompt
should preserve that source's character identity and request a clean
game-sprite animation:

- **Idle:** subtle readable breathing or approved idle behavior; seamless
  loop; no camera motion.
- **Jog:** the requested trucht cycle; stable ground contact; seamless loop;
  no camera motion or world movement.
- **Combat:** one distinct user-approved attack action; readable anticipation,
  strike, and recovery; no unapproved props or effects.
- **Death:** the approved defeat motion ending in a clear final pose; no loop
  and no gore unless specifically requested and appropriate.

For all eight prompts, request a fixed side-view camera, consistent framing
and scale, a stable lower gameplay anchor, and no scenery, text, labels,
watermark, or extra characters. Keep the left/blue source facing the
player-facing direction and the right/red source facing the opponent-facing
direction; do not ask the model to change team colors or flip the character.
Use `negative_prompt` only if the chosen model supports it. Do not ask the
model to combine multiple animation states or team variants into a sheet.

## Validation and Android integration

After the user approves a sequence, validate it before copying it into game
resources:

- Verify archive integrity, expected frame count, sequential order starting at
  zero, PNG format, equal dimensions, and true RGBA alpha where transparency
  was requested.
- Inspect every frame and a contact sheet. Check for clipping, halos, opaque
  background pixels, character drift, inconsistent scale, changed identity,
  broken loop endpoints, foot sliding, unstable ground anchor, and malformed
  weapon/effect poses. Reject any failed sequence; do not silently repair a
  clipped pose by cropping.
- Preserve the downloaded originals in the existing project asset-source
  location when available, and copy only approved, validated frames to
  `android-app/src/main/res/drawable-nodpi/`.
- Use Android-safe lowercase drawable names and sequential numeric suffixes.
  Follow the exact prefix and padding convention already used by
  `BattlefieldAssetCatalog` for the selected unit and state. Do not create a
  second asset catalog.
- Inspect `BattlefieldAssetCatalog` and `BattlefieldRenderer` to determine
  which team/facing variants and states the selected character uses. Wire
  left/blue player and right/red opponent frames for idle, jog, combat, and
  death to their existing state boundaries; do not move gameplay decisions
  into Android rendering.
- If the current renderer does not expose a required animation state, make
  the smallest screen-layer change that consumes the existing immutable
  render state. Do not add animation state to domain or simulation unless
  gameplay requires it.
- Add focused tests for frame naming/count or state-to-animation mapping
  where those contracts change. Update `docs/PROJECT_KNOWLEDGE.md` when the
  new asset conventions or renderer behavior become durable project facts.
- Run `./gradlew test assembleDebug --quiet` and `git diff --check`. Do not
  commit or push unless separately requested.

## Stop conditions

Stop and report the exact blocker without pretending generation or import
succeeded when:

- `ANIMGEN_API_KEY`, the supported secret-manager entry, and any
  user-approved private file are unavailable, invalid, or lack the required
  scope;
- the selected model cannot satisfy first-frame, transparency, ratio, or
  resolution requirements;
- the user has not approved the current quote or animation preview;
- the service reports insufficient credits, account restrictions, or a
  non-retryable error;
- an uncertain paid create cannot be safely resumed with its original
  idempotency key and request.

Never automatically buy credits, retry an unkeyed paid create, submit changed
prompts under an old idempotency key, or replace an existing animation before
the user approves the new result.

## Official API references

- [AnimGen API quickstart](https://animgen.com/docs/en/api/quickstart)
- [Authentication and image inputs](https://animgen.com/docs/en/api/authentication-and-inputs)
- [Models](https://animgen.com/docs/en/api-reference/list-models)
- [Request and option schemas](https://animgen.com/docs/en/api-reference/schemas)
- [Quote animation](https://animgen.com/docs/en/api-reference/quote-animations)
- [Create animation](https://animgen.com/docs/en/api-reference/create-animations)
- [Polling and downloads](https://animgen.com/docs/en/api/polling-and-downloads)
- [Errors and safe retries](https://animgen.com/docs/en/api/errors-and-retries)
