---
name: campaignMapAgp
description: Create a copy-ready image-generation prompt for any Urkaaaz campaign chapter map, with ten fortress sites and a connected route.
argument-hint: "[chapter number or title]"
---

# /campaignMapAgp — campaign chapter map AGP

Use this skill when the user asks for an AGP or image-generation prompt for a
campaign chapter map.

## Scope

- Prepare one copy-ready AGP for one campaign chapter at a time.
- The AGP generates one full-bleed campaign-map background image; it does not
  generate gameplay level maps, markers, UI, fortress sprites, or a contact
  sheet.
- Do not generate or import artwork, edit campaign layouts, or change code.
- Return the AGP in chat by default. Create or edit an `.agp` file only when the
  user explicitly asks to save it.
- The campaign catalog is the source of truth for chapter ID, title, biome,
  level range, and boss positions:
  `game-campaign/src/main/resources/campaign-chapters.json`.

## Conversation contract

Ask one focused question at a time, using `ask_user`; never combine questions
or ask through plain text. Reuse decisions already given by the user. Ask only
for information that materially changes the artwork or cannot be obtained from
the campaign catalog or the user's description. The user attaches reference
images to their image-generation tool; this skill only prepares the AGP.
Do not ask the user to upload those images into this chat unless they want the
assistant to inspect them.

Resolve these inputs before writing the AGP:

1. **Target chapter.** Infer it from the request when clear. Otherwise ask for
   the chapter number or title, then read the catalog for its official ID,
   title, biome, and ten-level range.
2. **Reference roles in the AGP.** The target chapter's biome background is
   the environment reference for terrain, palette, lighting, flora, and
   atmosphere. For later chapters, specify an existing campaign map from a
   different chapter as an additional reference for map presentation, framing,
   visual density, and route readability. The first campaign map is the
   bootstrap exception: specify only its biome background and explicitly
   instruct the image generator to invent an original map composition.
3. **Campaign frame and HUD keep-out reference.** Include a current campaign
   screen screenshot, or the frame plus HUD layout reference, so the generator
   can see where the map is covered by the decorative frame, header/Back panel,
   chapter navigation, compass, and Start Level control. Tell the generator
   this is a keep-out mask only: do not copy its map image, text, markers, UI,
   or decorations into the new background. These references are attached to
   the user's image-generation tool; do not ask for uploads into this chat.
4. **Geography.** If the user has not said whether to preserve an existing
   target-chapter layout or invent new geography, ask. A map from another
   chapter is only a style/composition reference; do not copy its exact
   geography. If there is no other chapter map yet, create an original
   composition using the biome background.
5. **Distinctive landmarks or route treatment.** Ask only if the user requests
   a specific landmark, route motif, season, or visual treatment that is not
   specified by the biome or attached references. Bridges are optional and
   should appear only where they fit the terrain, unless the user asks for
   them.

## Default visual contract

Apply these defaults unless the user explicitly changes them:

- Generate exactly **10** clearly visible, unoccupied fortress-site
  placeholders, one for each level in the chapter.
- Represent each placeholder as a readable, empty foundation or
  terrain-cleared fortress site that fits the target biome, with open center
  space for the campaign UI marker. Do not build a complete castle, tower,
  keep, or fortress on it.
- Arrange all ten placeholders directly along **one continuous,
  non-branching route** in level order. The route itself must pass through each
  placeholder; do not put sites beside the route on side spurs. It has exactly
  one start and one end, with no forks, loops, alternate paths, parallel
  routes, or branches at the placeholders. Bridges may form part of this same
  route where terrain calls for them.
- Treat the route as a simple path graph with exactly ten site-nodes:
  placeholder 1 and placeholder 10 are the only endpoints; placeholders 2–9
  each have exactly one entry and one exit. No site may have a third route
  connection. The path must not cross itself, form a junction, or split and
  reconnect elsewhere. At every point there must be only one way to continue
  from level 1 toward level 10.
- Read boss positions from the chapter catalog (`bossPositions`, currently
  levels 5 and 10). Make those empty sites clearly and immediately more
  prominent than the other eight, for example with a larger raised foundation,
  a strong concentric stone ring, and a distinct clearing. They remain empty
  UI-marker sites: do not add a boss, fortress, boss icon, number, or label.
- Keep all ten sites unobstructed, separated, inside the image bounds, and
  large enough for later overlay markers. Do not add other castle-like
  structures or extra site markers that could be mistaken for placeholders.
- Reserve the frame and HUD occlusion zones shown by the campaign UI reference.
  No placeholder, marker center, or part of the continuous route may fall
  behind the outer frame, header/Back panel, chapter controls, compass, or
  Start Level button. Keep sufficient clearance around every site for its
  marker artwork and status treatment.
- For later chapters, match the other chapter map's wide campaign-map
  presentation and visual polish while using the target biome background for
  the actual environment. Do not copy its exact landforms, paths, landmarks,
  or layout. For the bootstrap map, create an original campaign-map
  composition from the biome background without a map-image reference.
- Produce one high-resolution 16:9 landscape background image. No text,
  numbers, level icons, UI, map legend, panels, borders, watermark, or
  alternate images.
- Use English for the generator prompt unless the user requests another
  language. Keep the surrounding explanation in Polish.

## AGP assembly

Once required decisions and references are clear:

1. Read the selected chapter's catalog record and use its official name and
   biome terminology.
2. State the reference-image roles explicitly in the AGP. For a later chapter,
   use the other-chapter map for presentation style only and the target biome
   background for the depicted environment. For the bootstrap map, omit the
   campaign-map reference and use only the target biome background. In both
   cases, use the campaign frame/HUD reference only to establish keep-out
   zones; the user attaches all references to the image generator.
3. Describe the ten empty sites and state that all ten lie directly on one
   continuous, non-branching route in level order. Specify a simple path graph:
   only sites 1 and 10 are endpoints, and each site 2–9 has exactly two route
   connections. Explicitly forbid side spurs, third connections, forks,
   junctions, crossings, loops and alternate routes; mention bridges only as
   parts of the same route where appropriate.
4. Make the catalog's boss sites at levels 5 and 10 clearly more prominent
   than the other eight while keeping all ten sites empty for the
   CampaignScreen marker overlay.
5. Suggest the asset key `campaign_chapter_<NN>_<chapter_id>_map`, with a
   two-digit chapter number and the catalog's stable chapter ID.
6. Return one copy-ready AGP block. Do not save it to the repository unless
   explicitly requested.

## Reviewing a generated map image

When the user shares a generated preview for review, verify the visible
structure rather than judging only its style:

- count exactly ten placeholder sites;
- trace the route from site 1 through sites 2–9 to site 10 without making a
  choice at any point;
- reject the route if any placeholder has a third connection or if there is
  any branch, spur, crossing, loop, parallel route, or disconnected segment;
- check that boss sites are clearly highlighted but remain empty.
- composite the generated map with the campaign frame and HUD reference at
  their intended display size; reject or revise it if the frame or any HUD
  control hides a placeholder or interrupts the visible route.

If a preview fails, identify the specific visual failure and tighten the next
AGP with the simple-path requirements above.
