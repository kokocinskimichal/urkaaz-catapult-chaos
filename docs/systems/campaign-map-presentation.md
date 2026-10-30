# Campaign Map Presentation

## Status

**Partially implemented.** The campaign has a canonical ten-chapter registry,
typed fortress/boss landmarks, chapter-based biome assignments, and a
progression projection for current/completed/preview chapters. Chapter 1 uses
the supplied map with interactive markers. Per the accepted rollout decision,
chapters without map artwork retain the chapter-specific level-list
presentation until their backgrounds are supplied; Chapters 2–10 do not yet
have map images. Chapter metadata and map marker coordinates are stored as
shared JSON resources in `game-campaign`; the Studio Campaign tab edits the
same Chapter 1 layout that Android reads at runtime.

The first version contains ten chapters with ten levels each. The design must
allow more chapters to be added later without changing the screen navigation
or introducing chapter-specific UI branches. The accepted first-pass biome
mapping is below; assignments for chapters 6–10 are temporary and can be
replaced when new biomes are introduced.

## Goal and scope

Present campaign progression as one illustrated map per chapter. Each map
contains ten distinct level locations corresponding to that chapter's ten
levels. Eight locations use replaceable fortress marker assets; chapter levels
5 and 10 use replaceable boss marker assets instead. Native UI overlays
communicate level status and handle selection, so neither marker art nor map
background needs to change when progress changes.

The selector must make it easy to understand:

- which chapter is currently active;
- which level is the next available one;
- which levels and chapters have been completed;
- which levels are locked;
- that the next chapter can be previewed without unlocking its levels.

### In scope

- Replace the vertical level list with chapter map pages.
- Show all completed chapters, the current chapter, and at most one next
  chapter as a locked preview.
- Give each chapter its own biome-themed map background with ten level
  locations.
- Render fortress and boss landmark assets separately from the map background;
  levels 5 and 10 of every chapter use a boss marker instead of a fortress.
- Overlay level numbers, selection feedback, and completed/current/locked
  states in Android UI.
- Keep all selection authorization in the existing `CampaignSystem`.
- Establish one biome per chapter and apply it consistently to that chapter's
  `CampaignLevelDefinition` map definitions without changing terrain geometry
  or gameplay balance.
- Provide ten copy-ready asset-generation prompts (AGPs), one for each
  first-version chapter map.

### Out of scope

- New gameplay rules, level objectives, combat terrain geometry, rewards, or
  progression persistence.
- Changes to the `CampaignProgress` storage format or `DataStore`.
- Replacing `MapDefinition`, the map editor, or gameplay map rendering with
  campaign presentation data.
- A new Android navigation framework or a separate top-level route for every
  chapter.
- Adding new biome implementations in this first version. New biomes for
  chapters 6 and later are future work; the current assignments are
  placeholders for the initial map set.
- Generating, importing, or approving the final map images as part of this
  architecture plan.

## Existing behavior and related systems

- `CampaignScreen` now uses the chapter registry and exposes tabs for all
  completed chapters, the current chapter, and at most one locked preview.
  Chapter 1 renders its supplied map and ten markers; chapters without map art
  retain the level-list presentation for the selected chapter. The map fills
  its viewport with a center-crop transform shared by the landmark anchors.
- A transparent campaign frame overlays the map. The campaign label, chapter
  number, title, and completion progress appear left-aligned in its upper-left
  plaque. A progress bar reflects completed levels in the selected chapter;
  chapter tabs sit at the upper-right, and Start Level is at the bottom center.
- The screen reuses the existing gold button artwork: the full-size
  `loadout_start_match_button` for Start Level and the `xs_short` variant for
  Back, matching the Loadout screen's button dimensions and pressed-state
  resources.
- The current available level is selected by default. Tapping an available
  marker changes only screen-local selection; the explicit **Start Level**
  action calls `CampaignSystem.selectLevel` and opens Loadout after successful
  authorization. Locked and completed markers remain non-interactive.
- Fortress markers now use the approved transparent blue-banner Orc artwork
  from `assets_src/campaign/map-markers/campaign_map_marker_fortress.png`.
  Boss markers use the approved transparent horned-skull shrine artwork from
  `assets_src/campaign/map-markers/campaign_map_marker_boss.png`.
- The current available marker uses the transparent golden glow asset at
  `assets_src/campaign/map-markers/campaign_map_marker_available_glow.png`.
  The same image is scaled behind either a fortress or boss; completed and
  locked markers do not receive a glow.
- `CampaignMapView` renders the completed, available, and locked level-plaque
  backgrounds from separate PNGs. Number text remains live UI; completed and
  locked states use separate check and padlock badge PNGs. The original sprite
  sheets are preserved for regeneration.
- Completed and locked markers now use the approved transparent check and
  padlock badge artwork extracted from the status-icon sprite sheet. Available
  markers have no status badge.
- A two-icon sprite-sheet prompt for the completed check and locked padlock is
  available at `docs/systems/campaign-map-level-status-icons.agp`.
- `CampaignProgress` already persists `highestUnlockedLevel` and
  `completedLevels`.
- `CampaignChapterRegistry` reads canonical chapter metadata from
  `game-campaign/src/main/resources/campaign-chapters.json`.
- `CampaignMapLayoutRepository` validates normalized, global level anchors
  from `game-campaign/src/main/resources/campaign-map-layouts/<chapterId>.json`.
  Studio writes those same per-chapter JSON resources with hash conflict
  detection; Android uses them after the next build or app restart.
- `CampaignSystem.selectLevel(level)` already rejects locked and completed
  levels. `CampaignScreen` also marks completed levels as unavailable.
- Returning to campaign reloads persisted progress. Campaign completion,
  gold settlement, and milestone persistence remain owned by existing
  application and persistence components.
- `CampaignLevelDefinition` owns gameplay-level configuration and carries a
  `MapDefinition`. The `TerrainBiome` identifiers are defined in
  `game-contracts`.
- `MapDefinition` and the Studio map editor describe gameplay maps. They are
  not campaign-overview illustrations and must not be reused as a second
  representation of the same campaign map.
- Chapter identity, level grouping, and biome assignment are canonical in
  `game-campaign/src/main/resources/campaign-chapters.json` and loaded by
  `CampaignChapterRegistry`; chapters 6–10 retain temporary biome assignments
  until future biome work is approved.

The recommendation is to extend these owners rather than duplicate
progression, level authorization, or gameplay map contracts.

## Ownership and dependencies

| Responsibility | Owner |
|---|---|
| Static chapter identity, title, level range, canonical biome, and boss positions | `campaign-chapters.json` and `CampaignChapterRegistry` in `game-campaign` |
| Level definitions and their chapter-consistent biome | `CampaignLevelDefinition` / `CampaignLevelRegistry` in `game-campaign` |
| Campaign overview map key and normalized level anchors | `CampaignMapLayoutDefinition` / `CampaignMapLayoutRepository` in `game-campaign` |
| Durable progress and level-selection authorization | Existing `CampaignSystem` in `game-application` |
| Deriving visible chapters and marker states | Campaign screen presentation mapper in `android-app` |
| Resolving campaign asset keys to Android drawables | `CampaignMapPresentationCatalog` in `android-app` |
| Chapter switching, marker input, and rendering | Existing `CampaignScreen` and screen-owned campaign map view/controller |
| Campaign layout authoring and safe persistence | `CampaignTab` / `campaignMapCatalog.ts` in `studio-web` / `studio-server` |
| Committed map source images | `assets_src/campaign/chapter-maps/` |
| Committed campaign frame source image | `assets_src/campaign/ui/campaign_map_frame_overlay.png` |
| Committed level-plaque sheet and extracted state assets | `assets_src/campaign/ui/` |
| Android map image resources | `android-app/src/main/res/drawable-nodpi/` |

Dependency direction:

```text
CampaignScreen / map view (android-app)
  ├── CampaignSystem progress and selectLevel (game-application)
  ├── CampaignChapterRegistry (game-campaign)
  ├── CampaignMapLayoutRepository (game-campaign)
  └── Android campaign-map asset catalog, frame, and PNG assets

CampaignLevelDefinition (game-campaign)
  └── MapDefinition.biome set from its chapter's canonical biome

Studio CampaignTab (studio-web)
  └── campaignMapCatalog.ts (studio-server)
      ├── campaign-chapters.json
      └── campaign-map-layouts/<chapterId>.json
```

`game-campaign` must not contain Android resource IDs or `Context` references.
The Android presentation catalog resolves a stable background asset key to
its drawable and resolves landmark kind to a separate marker asset.
Normalized marker positions remain in the shared campaign layout resource,
not in the Android catalog. This is campaign presentation data, not a second
terrain or gameplay `MapDefinition` catalog.

## Landmark kinds

Every chapter has ten level locations. The chapter-relative fifth and tenth
levels are boss levels; the other eight use fortress landmarks. Represent this
with a typed `CampaignMapLandmarkKind` (`FORTRESS` or `BOSS`) assigned to each
level by its chapter definition, rather than a renderer condition based on
level-number arithmetic. The kind controls the campaign-map marker art only;
it does not imply that boss combat behavior or boss assets already exist in
the match simulation.

The Android map renders each landmark as an independent transparent marker
asset positioned at its level anchor. Marker kind and progress status are
separate inputs: the asset catalog chooses fortress versus boss artwork, while
the UI overlays the same completed/current/locked treatment for either kind.
The catalog should support an optional per-level asset override so a specific
fortress or future boss illustration can be replaced without editing the
background, coordinates, progress logic, or other level markers. A missing
boss marker must never silently show a fortress.

## Chapter definition and first-pass biome assignment

`game-campaign/src/main/resources/campaign-chapters.json` is the canonical
chapter catalog consumed by both Studio and `CampaignChapterRegistry`. A typed
chapter definition contains a stable ID, display title, ordered level range,
one `TerrainBiome`, and a typed landmark kind for each level in that range.
It does not contain progress, selected-screen state, Android resource IDs, or
mutable runtime data.

Current proposed mapping:

| Chapter | Title | Biome | Status |
|---:|---|---|---|
| 1 | The Green Frontier | `GREEN_FRONTIER` | First-pass assignment |
| 2 | The Broken Valley | `FROSTBOUND` | Retains the existing level 11–20 theme |
| 3 | The Ashen March | `ASHEN_MARCH` | First-pass assignment |
| 4 | The Whispering Marsh | `SUNKEN_MARSHES` | First-pass assignment |
| 5 | The Ironwood | `COPPERWOOD` | First-pass assignment |
| 6 | The Northern March | `FROSTBOUND` | Temporary; replace when its new biome is added |
| 7 | The Runic Wastes | `ASHEN_MARCH` | Temporary; replace when its new biome is added |
| 8 | The Red Peaks | `FROSTBOUND` | Temporary; replace when its new biome is added |
| 9 | The Final Warpath | `COPPERWOOD` | Temporary; replace when its new biome is added |
| 10 | The Last Siege | `ASHEN_MARCH` | Temporary; replace when its new biome is added |

These are presentation and level-theme assignments, not new gameplay
modifiers. Each level keeps its existing map geometry, placements, wind,
loadout availability, and balance. The chapter biome becomes the single
source for the `MapDefinition.biome` used by its levels and for the chapter
map's art direction.

When a new biome is introduced for a later chapter, it must be added through
the existing biome and asset workflow, then referenced by the chapter
definition. Do not store an unknown biome as an arbitrary string or add a
chapter-specific rendering fallback.

The chapter registry must validate that current chapter ranges cover every
registered campaign level exactly once, do not overlap, and contain ten levels
per chapter for the current campaign. The UI must not calculate chapter ranges
independently or assume that the current count of chapters will remain ten.

## Campaign progress projection

The campaign map presentation consumes an immutable `CampaignProgress` read
model and the chapter registry. It does not own or persist campaign
progression.

For valid progress:

1. The current chapter is the chapter containing `highestUnlockedLevel`.
2. A chapter is completed when all its levels are present in
   `completedLevels`.
3. The visible chapter set is every completed chapter, the current chapter,
   and the immediately following chapter if one exists.
4. Chapters later than the next chapter are omitted until progression reaches
   them.
5. On entering or resuming `CampaignScreen`, select the current chapter and
   its available level by default. Chapter and level selection remain
   screen-local and are not saved to `DataStore`.
6. If the campaign is complete, the last chapter remains the default and all
   completed chapters remain visible; there is no next-chapter preview.

The chapter selector presents all completed chapters plus the current and
preview chapter. Its visual placement is not a fixed contract: keep it compact
and clear of the map's level anchors. The current bottom strip is only the
existing implementation, not an approved final layout. A redesign concept
prompt is available at `docs/systems/campaign-map-screen-layout.agp`; review
the generated screenshot before changing screen composition. The full
chapter title and completion summary remain visible on the selected map page.

The chapter state is presentation-only:

- **Completed chapter:** its ten level markers are visibly completed and
  non-interactive.
- **Current chapter:** the level in `highestUnlockedLevel`, if not completed,
  is the only selectable level. Later levels are visibly locked.
- **Next chapter preview:** show its map and ten locked markers, with a clear
  preview/locked chapter treatment. No level is selectable.
- **Hidden future chapter:** no map page or selectable chapter tab is shown.

For each marker, completed status is derived from `completedLevels`; the
available marker is derived from `highestUnlockedLevel`; all other levels are
locked. Tapping the available marker calls the existing
`CampaignSystem.selectLevel(level)` and follows the current Loadout flow.
Completed and locked markers do not navigate. The application use case remains
the final authorization gate even when the presentation disables a marker.

The default chapter is recalculated from refreshed progress after returning
from a match. If progress cannot be loaded or validated, preserve the current
loading/failure/retry behavior and do not display a success-shaped map with
unlocked levels.

## Map artwork and marker presentation

Each first-version chapter receives one full-bleed, horizontal 16:9 map
background PNG, with a proposed source size of 1920 x 1080 pixels. The selected
art direction is a painterly atlas map viewed from above, with enough
topographic depth to read as a landscape and a clear route connecting ten
distinct, empty level locations. Each map has its own composition while
following one shared Urkaaaz art direction.

Each background AGP must require:

- exactly ten separate, prominent level locations, one for each level, with
  clear anchor space for a later marker overlay;
- no fortress, boss, or other building artwork baked into those locations;
- no existing-marker silhouette or status treatment embedded in the
  background; all landmark images must remain independently replaceable;
- the assigned chapter biome and its recognizable palette, terrain, and
  vegetation;
- no text, numbers, labels, UI, status colors, locks, or baked-in level
  completion state;
- legibility at the final landscape-screen size.

Fortress and boss landmarks are separate transparent Android marker assets,
not part of the map background. The screen places the selected asset at each
level's anchor, overlays its live level number, and applies the same status
treatment independently. A completed level uses a distinct completion mark;
the available level uses full image contrast; locked levels use a subdued
treatment at 72% opacity and a lock indicator. Do not surround landmark art
with circular frames or status rings. Fortress artwork renders at 72 dp (150%
of its former 48 dp size); boss artwork remains at 48 dp. The available
landmark receives a soft amber radial glow behind its transparent art. The
next chapter preview clearly signals that its landmarks are not yet
selectable. Marker labels and status must remain live UI content rather than
being baked into either asset.

For each approved image, author one global-level anchor per chapter level in
`campaign-map-layouts/<chapterId>.json`. `x` and `y` are normalized to the
source image and inclusive in the range 0–1. Coordinates identify level marker
centers and must be measured against the actual approved background, not
guessed from an AGP. Studio and Android use the same normalized positions and
center-crop transform. This prevents letterboxing from exposing another
screen's background. Keep all ten anchors visible and do not place them beneath
the header, chapter controls, or Back button.

Each level marker is a replaceable UI component with independent image,
number, status treatment, accessibility label, and touch target. Replacing a
fortress image with a boss image must not change its anchor, hit target,
progress state, or level-selection behavior. Marker touch targets should
remain at least 48 dp. If level locations are too close for independent touch
targets at supported landscape sizes, adjust their anchors or regenerate the
background; do not make the controls smaller or allow ambiguous overlapping
taps.

Map images are campaign presentation assets and do not enter `MapDefinition`,
`MapDefinitionRepository`, or the Studio Maps editor. They do not define
world coordinates, collision, placement, or match terrain.

### Chapter 1 map candidate and draft anchors

The latest user-provided image is already a top-down atlas map, not merely a
biome reference. It is the current Chapter 1 map candidate and is stored at
`assets_src/campaign/chapter-maps/campaign_chapter_01_green_frontier_map.png`.
Its source size is 1608 × 905 pixels, close to 16:9. Preserve the image and its
aspect ratio; do not generate a replacement map unless requested.

The following normalized coordinates are the current marker centers in
`game-campaign/src/main/resources/campaign-map-layouts/green_frontier.json`.
The points follow a broad route around the landscape and return to the central
basin for the final boss. They are separate from the map artwork; visual
review at supported landscape sizes is still required before treating them as
final.

| Level | Landmark | x | y | Visual area |
|---:|---|---:|---:|---|
| 1 | Fortress | 0.145 | 0.750 | Southwest meadow |
| 2 | Fortress | 0.255 | 0.545 | Western river-side clearing |
| 3 | Fortress | 0.365 | 0.365 | West-central upland |
| 4 | Fortress | 0.485 | 0.260 | North-central plateau |
| 5 | Boss | 0.645 | 0.330 | Upper-east plateau |
| 6 | Fortress | 0.845 | 0.245 | Northeast clearing |
| 7 | Fortress | 0.855 | 0.455 | East-central grassland |
| 8 | Fortress | 0.755 | 0.675 | Southeast plateau |
| 9 | Fortress | 0.565 | 0.750 | South-central clearing |
| 10 | Boss | 0.485 | 0.475 | Central basin |

These are presentation coordinates only. Levels 5 and 10 use boss markers;
the other eight use fortress markers. No marker, number, status, or hit target
is baked into the background image.

## Navigation and lifecycle

Keep the existing `AppScreen.Campaign` route. `CampaignScreen` hosts the
selected chapter page and switches its content internally; changing chapters
must not push one route per chapter onto `AppNavigator`'s Back stack.

The screen lifecycle is:

```text
CampaignScreen enters/resumes
  → load CampaignProgress
  → derive current, completed, and next-preview chapters
  → select the current chapter page
  → render map and ten marker states
  → user switches among visible chapter pages
  → user selects the one available level
  → CampaignSystem.selectLevel
  → existing Loadout navigation
  → returning to campaign reloads progress and selects the new current chapter
```

The screen owns only its transient selected chapter and input state. There is
no simulation tick, seed, replay behavior, or campaign-page persistence.
Pausing the Android screen cancels its screen-owned asynchronous work as in the
existing screen pattern; resuming reloads progress and reselects the current
chapter.

## Contracts and state

| Contract or state | Owner | Purpose |
|---|---|---|
| `CampaignChapterDefinition` | `game-campaign` | Immutable chapter ID, title, level range, biome, and per-level landmark kinds |
| `CampaignMapLandmarkKind` | `game-campaign` | Typed fortress/boss map landmark identifier |
| `CampaignChapterRegistry` | `game-campaign` | Ordered, validated chapter definitions and level-to-chapter/landmark lookup |
| `CampaignProgress` | `game-application` | Existing immutable persisted progress read model |
| Chapter catalog metadata | `game-campaign` | Shared chapter IDs, titles, ranges, biomes, and boss positions |
| Campaign map layout | `game-campaign` | Stable background asset key and normalized global-level anchors |
| Android map asset adapter | `android-app` | Drawable resolution and rendering of shared layout/marker assets |
| Campaign map authoring | `studio-web` / `studio-server` | Framed preview, normalized X/Y editing, validation, and safe layout persistence |
| Campaign map UI state | `android-app` | Selected chapter page and immutable marker/view status projection |
| `CampaignUiAction.SelectLevel` | `android-app` | Existing user intent to select a level |
| `CampaignSystem.selectLevel` result | `game-application` | Authoritative selected/rejected/failed level result |

No new gameplay `MatchEvent`, `MatchSnapshot`, `MatchCommand`, or simulation
state is required. The screen does not mutate `CampaignProgress` or
`CampaignLevelDefinition`.

## Errors and edge cases

- Unknown chapter IDs, gaps/overlaps in level coverage, duplicate level
  membership, invalid biome IDs, or invalid marker coordinates must fail
  validation; they must not silently map to another chapter or biome.
- Repository loading and validation failures continue to render the existing
  explicit error and retry state. They must not expose all chapters or unlock
  level markers.
- A rejected `CampaignSystem.selectLevel` result is rendered through the
  existing typed rejection path. A stale UI marker is never treated as proof
  of authorization.
- A chapter with no following chapter has no next-preview page.
- If all campaign levels are complete, all completed chapters remain
  navigable and the last chapter is the initial selection.
- Missing chapter map artwork intentionally shows that chapter's level list;
  no unrelated map or biome art is substituted.
- Anchor placement and marker touch targets are checked for every approved
  map at the smallest supported landscape viewport.

## Alternatives considered

| Variant | Assessment |
|---|---|
| One `CampaignScreen` with chapter pages and a canonical chapter registry | **Recommended.** Reuses the existing route and `CampaignSystem`, keeps chapter switching local, supports future chapters, and gives level progress one source of truth. |
| A separate `AppScreen` route for every chapter | Rejected. It couples each chapter page to navigation, can grow the Back stack while switching maps, and duplicates lifecycle/navigation work without adding a distinct application boundary. |
| Bake numbers, locks, and completion marks into each map image | Rejected. Progress changes would make the art stale and require generated image variants for combinations of level states. It is also less accessible and less testable. |
| Use gameplay `MapDefinition` or Studio map rendering for the campaign overview | Rejected. Gameplay maps have a different purpose and coordinate contract; reusing them would couple campaign presentation to terrain authoring and mix visual progression with match geometry. |
| Keep chapter metadata and level authorization entirely in Android | Rejected. It would duplicate chapter grouping and weaken the existing application authorization boundary. |

## Implementation plan and status

1. **Implemented.** Add immutable chapter definitions and a registry in `game-campaign`.
   Validate chapter IDs, titles, biome assignments, non-overlapping level
   coverage, the current ten-level chapter size, and per-level landmark kinds
   (boss at chapter positions 5 and 10; fortress elsewhere).
2. **Implemented.** Route each registered level's existing `MapDefinition.biome` through its
   chapter assignment. Preserve all authored map geometry, object placements,
   wind, and balance. Add tests proving biome consistency within chapters and
   no unintended geometry changes.
3. **Implemented.** Add a pure campaign-map presentation projection in `android-app` that maps
   `CampaignProgress` and the chapter registry to visible chapters, selected
   current chapter, chapter completion state, and ten level-marker states.
4. **Implemented for Chapter 1.** The shared campaign layout repository
   provides ten global level anchors; Android maps the background asset key
   to its drawable and renders the layout with fortress/boss markers.
   Chapter 2–10 backgrounds remain outstanding.
5. **Implemented.** Replace the single vertical list in `CampaignScreen` with the chapter
   selector, chapter map page, and dynamic fortress/boss marker overlays.
   Keep loading, storage errors, retry, Back behavior, and the current Loadout
   navigation. Chapters without map art explicitly retain the selected
   chapter's level-list presentation.
   Do not add a chapter route to `AppNavigator`.
6. **Implemented for available artwork.** Chapter 1's map, transparent frame, reusable
   fortress/boss markers, completed/available/locked plaques, status badges,
   and available-level glow are supplied. Copy-ready AGPs cover Chapter 1 and
   the reusable UI/marker assets; map AGPs and images for Chapters 2–10 remain
   outstanding.
7. **Implemented.** Kotlin/Android tests cover chapter visibility, marker
   states, shared layout decoding, and center-crop geometry. Studio tests cover
   API validation/conflicts and normalized coordinate transforms. Both the
   campaign map and Studio editor were visually reviewed; additional
   supported landscape sizes still need review.
8. **Implemented.** `./gradlew test assembleDebug --quiet`, Studio
   `npm test`, `npm run lint`, `npm run build`, and `git diff --check` pass for
   the current implementation.

## Implemented files and remaining art deliverables

Implemented code and tests:

- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignChapterDefinition.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignChapterCatalogRepository.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignChapterRegistry.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignMapLandmarkKind.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignMapLayoutDefinition.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignMapLayoutRepository.kt`
- `game-campaign/src/main/resources/campaign-chapters.json`
- `game-campaign/src/main/resources/campaign-map-layouts/green_frontier.json`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt`
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelRegistry.kt`
- `game-campaign/src/test/kotlin/com/urkaaaz/campaign/CampaignChapterRegistryTest.kt`
- `game-campaign/src/test/kotlin/com/urkaaaz/campaign/CampaignChapterCatalogRepositoryTest.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignScreen.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignViewModel.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignUiState.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignChapterProgressMapper.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignMapPresentationCatalog.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignMapView.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignMapViewport.kt`
- `android-app/src/main/res/values/strings.xml`
- `android-app/src/main/res/drawable-nodpi/campaign_map_marker_fortress.png`
- `android-app/src/main/res/drawable-nodpi/campaign_map_marker_boss.png`
- `android-app/src/main/res/drawable-nodpi/campaign_level_plate_completed.png`
- `android-app/src/main/res/drawable-nodpi/campaign_level_plate_available.png`
- `android-app/src/main/res/drawable-nodpi/campaign_level_plate_locked.png`
- `android-app/src/main/res/drawable-nodpi/campaign_level_status_completed.png`
- `android-app/src/main/res/drawable-nodpi/campaign_level_status_locked.png`
- `android-app/src/test/java/com/urkaaaz/screens/campaign/CampaignChapterProgressMapperTest.kt`
- `android-app/src/test/java/com/urkaaaz/screens/campaign/CampaignViewModelTest.kt`
- `android-app/src/test/java/com/urkaaaz/screens/campaign/CampaignMapViewportTest.kt`
- `game-campaign/src/test/kotlin/com/urkaaaz/campaign/CampaignMapLayoutRepositoryTest.kt`
- `studio/server/src/services/campaignMapCatalog.ts`
- `studio/server/src/api/campaign.ts`
- `studio/server/src/api/campaign.test.ts`
- `studio/server/src/services/availablePort.ts`
- `studio/web/src/tabs/campaign/CampaignTab.tsx`
- `studio/web/src/tabs/campaign/CampaignMapCanvas.tsx`

Remaining artwork and authoring deliverables:

- Chapter map images for chapters 2–10 under
  `assets_src/campaign/chapter-maps/` and
  `android-app/src/main/res/drawable-nodpi/`.
- The approved fortress marker is available at
  `assets_src/campaign/map-markers/campaign_map_marker_fortress.png` and
  `android-app/src/main/res/drawable-nodpi/campaign_map_marker_fortress.png`.
  The PNG was previewed at its 72 dp display size and replaces the temporary
  vector. The approved boss marker is at
  `assets_src/campaign/map-markers/campaign_map_marker_boss.png` and
  `android-app/src/main/res/drawable-nodpi/campaign_map_marker_boss.png`.
- Chapter map AGPs 02–10 remain outstanding. The fortress and boss marker
  prompts are `docs/systems/campaign-map-fortress-marker.agp` and
  `docs/systems/campaign-map-boss-marker.agp`. The level-plaque sprite sheet
  and its extracted state assets are now supplied under
  `assets_src/campaign/ui/`; their prompt is
  `docs/systems/campaign-map-level-plates.agp`. The existing Chapter 1 map AGP
  remains fallback-only unless a replacement is requested.
- The reusable golden available-marker glow uses
  `assets_src/campaign/map-markers/campaign_map_marker_available_glow.png`;
  its generation prompt is `docs/systems/campaign-map-available-marker-glow.agp`.
- The completed-check and locked-padlock badges are extracted from
  `assets_src/campaign/ui/campaign_level_status_icons_sheet.png` and imported
  as separate Android images. Their one-sheet prompt is
  `docs/systems/campaign-map-level-status-icons.agp`.
- The transparent campaign frame is implemented at
  `assets_src/campaign/ui/campaign_map_frame_overlay.png` and
  `android-app/src/main/res/drawable-nodpi/campaign_map_frame_overlay.png`.
  Its asset prompt is `docs/systems/campaign-map-frame-overlay.agp`.

`AppScreen`, `AppNavigator`, persistence, reward settlement, and match
simulation are expected to remain unchanged.

## Tests and acceptance criteria

### `game-campaign`

- Every registered level belongs to exactly one chapter.
- Chapter ranges do not overlap and all current chapters contain ten levels.
- Chapter IDs and level-to-chapter lookup are stable and deterministic.
- Every level in a chapter uses the chapter's biome while retaining its map
  geometry, placements, and other gameplay configuration.
- Every chapter-relative level 5 and level 10 is typed as a boss landmark;
  the remaining eight levels are fortress landmarks.
- Adding a future chapter does not require adding a chapter-specific UI branch
  or a new navigation route.

### Android presentation and integration

- Initial progress shows chapter 1 as current, chapter 2 as preview, and
  hides chapters 3 onward.
- Completing a chapter makes it visible as completed, advances the current
  chapter, and exposes only its immediate next chapter as preview.
- All previously completed chapters remain in the chapter selector.
- The next preview chapter displays ten locked markers and cannot start a
  level.
- The current page marks exactly the unlocked, incomplete level as selectable;
  completed and future levels remain disabled.
- Selecting an enabled marker goes through `CampaignSystem.selectLevel` and
  the existing Loadout flow; rejected or failed selections do not navigate.
- Campaign progress read failures never produce unlocked or success-shaped
  UI.
- Opening/resuming the campaign selects the current chapter; changing pages
  does not add entries to `AppNavigator`'s Back stack or persist a chapter.
- Every level in the selected chapter has exactly one finite, normalized
  anchor in bounds and mapped to its global campaign level ID. Overlapping
  rendered marker bounds produce a Studio warning but do not block saving.
- Every chapter renders eight fortress markers and boss markers at positions
  5 and 10. Marker art can be replaced independently without regenerating the
  map background or changing anchors, progress status, or hit targets.
- Marker hit targets remain at least 48 dp at supported landscape aspect
  ratios; Studio warns if their visible footprints overlap.
- Every approved map background has the expected chapter biome, ten distinct
  clear level locations, no baked fortress/boss marker art or text/UI, and a
  readable composition at device scale.
- No gameplay state, simulation result, or replay changes when navigating or
  rendering chapter maps.

## Open and deferred work

- The chapter-to-biome table above is accepted for the first visual set.
  Chapters 6–10 are temporary assignments; their future new biomes require
  their own biome implementation and art approval before the map art or level
  theme is changed.
- Chapter 1 uses the supplied map candidate and the implemented anchor
  coordinates above; verify marker placement and spacing in a visual preview
  at supported landscape sizes.
- `docs/systems/campaign-chapter-map-01.agp` is retained only as a fallback
  prompt if a new or revised Chapter 1 map is explicitly requested. The other
  nine chapter prompts and map images remain future asset work.
- The fortress marker uses the approved 512 × 512 transparent blue-banner
  asset and has been previewed at device size. The boss marker uses the
  approved 512 × 512 transparent horned-skull shrine and has been previewed
  at 48 dp. Their generation prompts are
  `docs/systems/campaign-map-fortress-marker.agp` and
  `docs/systems/campaign-map-boss-marker.agp`. Changing either image must not
  require map regeneration.
- New future chapters require a chapter definition, ten level definitions,
  one approved map background, and ten validated presentation anchors. They
  reuse the replaceable landmark assets unless a chapter-specific override is
  approved.
  Extending `CampaignLevelDefinition.MAX_LEVELS` and any associated content
  registries remains part of that later chapter addition.
