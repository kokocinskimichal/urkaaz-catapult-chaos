# Campaign Map Presentation

## Status

**Implemented.** The campaign has a canonical ten-chapter registry, typed
fortress/boss landmarks, persisted progression, and a full-screen map
carousel. It displays completed chapters, the current chapter, and at most
one next-chapter preview. Chapters 1–4 use their authored map backgrounds;
Chapters 5–10 temporarily reuse the Green Frontier artwork and anchors as an
Android presentation fallback. The strict layout repository and Studio still
report missing authored artwork for Chapters 5–10. Campaign metadata and
authored map anchors remain shared JSON resources in `game-campaign`. Chapter
3 is Ironwood/Copperwood, Chapter 4 is Ashen March/Ashen March, and Chapter 5
is Whispering Marsh/Sunken Marshes. Their level ranges and boss encounters are
unchanged.

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

The campaign screen must make it easy to understand:

- which chapter is currently active;
- which level is the next available one;
- which levels and chapters have been completed;
- which levels are locked;
- that the next chapter can be previewed without unlocking its levels.

### In scope

- Show one full-screen chapter map page at a time.
- Show all completed chapters, the current chapter, and at most one next
  chapter as a locked preview.
- Use an authored biome-themed background where available and a clearly
  documented presentation fallback where it is not.
- Navigate between visible chapter maps with side arrows or a horizontal
  swipe; do not show chapter thumbnail cards.
- Keep the compact campaign header, chapter title, chapter progress, global
  `N / 10` position, level markers, and Start Level action as live UI.
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

- `CampaignScreen` shows one full-bleed chapter map at a time. Completed
  chapters, the current chapter, and at most the next locked preview are
  navigable with the imported left/right arrow assets or a horizontal swipe.
  The page indicator uses the chapter's global number out of ten, not its
  index among currently visible pages.
- The map uses the shared `CampaignMapViewport` center-crop transform for
  both the image and normalized marker anchors. Chapters 1–4 resolve their
  authored backgrounds. A missing map for Chapters 5–10 reuses the Green
  Frontier background and local anchor positions, remapping anchor level IDs
  to the selected chapter's global level range. Marker kinds and states still
  come from the canonical chapter and progress projections.
- The Android fallback is presentation-only. `CampaignMapLayoutRepository`
  remains strict, does not synthesize map JSON, and reports missing artwork to
  Studio; it never masks invalid layout data or storage failures.
- The HUD is a compact 52 dp-high dark translucent header, with Back adjacent
  to a wider chapter panel. Campaign label, chapter number/title and `N / 10`
  share the upper rows; progress occupies a thin bottom row. Side navigation
  arrows and the bottom-centered Start Level button stay outside this header.
  CampaignScreen does not use the decorative outer frame, chapter thumbnails,
  or parchment selector.
- Back and Start Level continue to reuse Loadout's approved button art.
  Header text and progress remain live Android UI; arrows use the transparent
  `campaign_map_nav_arrow_left/right.png` assets with 48 dp or larger touch
  targets and localized accessibility descriptions.
- The campaign frame overlay remains in the repository because Test Mode and
  Studio's Campaign preview still use it. It is not layered by CampaignScreen.
- The current available level is selected by default. Tapping an available
  marker changes only screen-local selection; **Start Level** calls
  `CampaignSystem.selectLevel` and opens Loadout only after successful
  authorization. Locked and completed markers remain non-interactive.
- Fortress markers use the approved transparent blue-banner Orc artwork from
  `assets_src/campaign/map-markers/campaign_map_marker_fortress.png`. Boss
  markers use the approved transparent horned-skull shrine artwork from
  `assets_src/campaign/map-markers/campaign_map_marker_boss.png`.
- The current available marker uses the transparent golden glow asset at
  `assets_src/campaign/map-markers/campaign_map_marker_available_glow.png`.
  Completed and locked markers do not receive a glow.
- `CampaignMapView` renders completed, available, and locked plaque
  backgrounds from separate PNGs. Number text remains live UI; completed and
  locked states use separate check and padlock badges. Original level-plaque
  and status-icon sheets remain as regeneration sources.
- The old chapter thumbnails, thumbnail frames, and parchment selector were
  removed from Android runtime resources and their exported source copies.
  Original sprite sheets and square chapter emblems remain archived under
  `assets_src/campaign/`; they are not loaded by the current campaign screen.
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
| Shared campaign frame source image (Test Mode / Studio) | `assets_src/campaign/ui/campaign_map_frame_overlay.png` |
| Committed level-plaque sheet and extracted state assets | `assets_src/campaign/ui/` |
| Android map image resources | `android-app/src/main/res/drawable-nodpi/` |

Dependency direction:

```text
CampaignScreen / map view (android-app)
  ├── CampaignSystem progress and selectLevel (game-application)
  ├── CampaignChapterRegistry (game-campaign)
  ├── CampaignMapLayoutRepository (game-campaign)
  └── Android map, marker, and carousel-arrow assets

CampaignLevelDefinition (game-campaign)
  └── MapDefinition.biome set from its chapter's canonical biome

Studio CampaignTab (studio-web)
  └── campaignMapCatalog.ts (studio-server)
      ├── campaign-chapters.json
      └── campaign-map-layouts/<chapterId>.json
```

`game-campaign` must not contain Android resource IDs or `Context` references.
The Android presentation catalog resolves a stable background asset key to
its drawable. Fortress landmarks use their shared marker asset; boss landmarks
with a campaign level profile use that profile's presentation key to resolve a
boss sprite and, when available, a campaign-marker idle animation.
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
For boss landmarks, `CampaignLevelDefinition` supplies the encounter profile
ID and the balance profile supplies its presentation key. Android resolves the
static sprite and optional `campaignMarkerIdleAnimation` through
`BossPresentationCatalog`; generated campaign-only idles do not alter the
battlefield renderer. Boss landmarks without a profile or campaign sprite keep
the generic boss marker. A missing boss marker must never silently show a
fortress.

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
| 2 | Frostbound Vale | `FROSTBOUND` | Retains the existing level 11–20 theme |
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

The carousel presents all completed chapters plus the current and preview
chapter. It shows a single full-screen map, compact campaign header, global
chapter index, and side arrows. Horizontal swipe and arrows select only an
adjacent page from the current visible chapter set; neither changes progress
or adds routes to `AppNavigator`. Page and level selections remain
screen-local. The current design and implementation constraints are recorded
in `campaign-map-carousel-reimplementation-plan.md` and
`campaign-map-carousel-implementation-prompt.md`.

The chapter state is presentation-only:

- **Completed chapter:** its ten level markers are visibly completed and
  non-interactive.
- **Current chapter:** the level in `highestUnlockedLevel`, if not completed,
  is the only selectable level. Later levels are visibly locked.
- **Next chapter preview:** show its map and ten locked markers. No level is
  selectable.
- **Hidden future chapter:** no map page is shown until progression reaches it.

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
with circular frames or status rings. Fortress artwork renders at 72 dp;
boss artwork renders at 72 dp, scaled to 150% of its former 48 dp size. The
available landmark receives a soft amber radial glow behind its transparent
art. The next chapter preview clearly signals that its landmarks are not yet
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

### Chapter 1 approved map and anchors

The approved Chapter 1 background is stored at
`assets_src/campaign/chapter-maps/campaign_chapter_01_green_frontier_map.png`
and imported unchanged into
`android-app/src/main/res/drawable-nodpi/campaign_chapter_01_green_frontier_map.png`.
Its source dimensions are 1672 × 941 pixels. It has ten empty fortress-site
placeholders along one continuous, non-branching route; the larger sites at
levels 5 and 10 identify the boss encounters. No marker, number, status, or hit
target is baked into the image.

The current marker anchors in
`game-campaign/src/main/resources/campaign-map-layouts/green_frontier.json`
were updated in Studio for the approved background. The user can continue
repositioning fortresses and boss markers there; artwork imports must not
overwrite the saved coordinates. The table lists the current JSON values,
rounded to six decimal places.

| Level | Landmark | x | y |
|---:|---|---:|---:|
| 1 | Fortress | 0.903377 | 0.290434 |
| 2 | Fortress | 0.713245 | 0.349762 |
| 3 | Fortress | 0.470632 | 0.369351 |
| 4 | Fortress | 0.227210 | 0.397992 |
| 5 | Boss | 0.485104 | 0.501726 |
| 6 | Fortress | 0.862666 | 0.547523 |
| 7 | Fortress | 0.842425 | 0.700161 |
| 8 | Fortress | 0.637212 | 0.752593 |
| 9 | Fortress | 0.388053 | 0.757004 |
| 10 | Boss | 0.141640 | 0.739510 |

These are presentation coordinates only. Levels 5 and 10 use boss markers;
the other eight use fortress markers. No marker, number, status, or hit target
is baked into the background image.

### Chapter 2 approved map and anchors

The supplied Chapter 2 background is stored at
`assets_src/campaign/chapter-maps/campaign_chapter_02_broken_valley_map.png`
and imported unchanged into
`android-app/src/main/res/drawable-nodpi/campaign_chapter_02_broken_valley_map.png`.
Its source dimensions are 1717 × 916 pixels. Its ten site positions are stored
as normalized anchors for global levels 11–20 in
`game-campaign/src/main/resources/campaign-map-layouts/broken_valley.json`;
the larger boss sites correspond to local levels 5 and 10. The source and
runtime images must remain byte-identical.

### Chapter 3 and 4 approved maps and anchors

The supplied Copperwood map for Chapter 3 is stored at
`assets_src/campaign/chapter-maps/campaign_chapter_03_ironwood_map.png`
and imported unchanged into
`android-app/src/main/res/drawable-nodpi/campaign_chapter_03_ironwood_map.png`.
Its source dimensions are 1672 × 941 pixels. Its ten site positions are stored
as normalized anchors for global levels 21–30 in
`game-campaign/src/main/resources/campaign-map-layouts/ironwood.json`; boss
sites remain 25 and 30.

The original Ashen March map is now Chapter 4's artwork at
`assets_src/campaign/chapter-maps/campaign_chapter_04_ashen_march_map.png`
and `android-app/src/main/res/drawable-nodpi/campaign_chapter_04_ashen_march_map.png`.
It retains its original normalized site positions, remapped to global levels
31–40 in `game-campaign/src/main/resources/campaign-map-layouts/ashen_march.json`;
boss sites remain 35 and 40. Both source/runtime pairs must remain byte-identical.

Transparent 256 × 256 navigation arrow artwork is available at
`assets_src/campaign/ui/campaign_map_nav_arrow_left.png` and
`campaign_map_nav_arrow_right.png`, with byte-identical Android copies in
`android-app/src/main/res/drawable-nodpi/`. These are standalone directional
icons for the proposed full-map chapter carousel; importing them does not
change the current chapter-navigation UI or behavior.

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
- Missing chapter map artwork for Chapters 5–10 uses the explicit
  presentation-only Green Frontier fallback. Invalid layout data and storage
  failures remain errors rather than falling back.
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
4. **Implemented for available artwork.** The shared campaign layout repository
   provides ten global level anchors; Android maps background asset keys to
   drawables and renders fortress/boss markers for Chapters 1–4. Chapters 5–10
   currently use the Green Frontier map and anchors as an Android-only
   presentation fallback.
5. **Implemented.** `CampaignScreen` renders the completed/current/next-preview
   chapter pages as a full-screen map carousel with side arrows and horizontal
   swipe. It keeps loading, storage errors, retry, Back, level authorization,
   and Loadout navigation. It does not add a chapter route to `AppNavigator`.
6. **Implemented.** Chapter maps, reusable fortress/boss markers,
   completed/available/locked plaques, status badges, available-level glow,
   and carousel arrows are supplied. The old CampaignScreen perimeter frame,
   thumbnail cards, thumbnail frames, and parchment selector are removed.
   The reusable campaign frame asset remains for Test Mode and Studio.
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
  `android-app/src/main/res/drawable-nodpi/campaign_map_marker_boss.png`; it
  remains the fallback for boss landmarks without a mapped boss profile.
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
- The transparent campaign frame remains at
  `assets_src/campaign/ui/campaign_map_frame_overlay.png` and
  `android-app/src/main/res/drawable-nodpi/campaign_map_frame_overlay.png`
  for Test Mode and Studio Campaign preview. CampaignScreen does not render
  it; its asset prompt remains historical for those other consumers.

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
- All previously completed chapters remain navigable in the map carousel.
- The next preview chapter displays ten locked markers and cannot start a
  level.
- Arrows and horizontal swipes navigate only between the visible chapter
  pages; the page indicator shows the chapter's global position out of ten.
- Chapters 5–10 use the Green Frontier background and normalized anchors only
  as a presentation fallback. The selected chapter retains its own global
  marker levels, boss positions, title, and progression state.
- The current page marks exactly the unlocked, incomplete level as selectable;
  completed and future levels remain disabled.
- Selecting an enabled marker goes through `CampaignSystem.selectLevel` and
  the existing Loadout flow; rejected or failed selections do not navigate.
- Campaign progress read failures never produce unlocked or success-shaped
  UI.
- Opening/resuming the campaign selects the current chapter; changing pages
  does not add entries to `AppNavigator`'s Back stack or persist a chapter.
- CampaignScreen has no decorative perimeter frame, chapter thumbnail strip,
  or list fallback for chapters 5–10.
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
- Chapter 1 uses the approved imported map and the current Studio-authored
  anchors listed above. Assistants must not change fortress or boss anchors
  without an explicit request.
- `docs/systems/campaign-chapter-map-01.agp` is a superseded historical prompt.
  Use the general `/campaignMapAgp` skill for future chapter-map prompts. The
  other nine chapter map images remain future asset work.
- The fortress marker uses the approved 512 × 512 transparent blue-banner
  asset and has been previewed at device size. The boss marker uses the
  approved 512 × 512 transparent horned-skull shrine and has been previewed
  at 72 dp, the current boss-landmark display size. Their generation prompts are
  `docs/systems/campaign-map-fortress-marker.agp` and
  `docs/systems/campaign-map-boss-marker.agp`. Changing either image must not
  require map regeneration.
- New future chapters require a chapter definition, ten level definitions,
  one approved map background, and ten validated presentation anchors. They
  reuse the replaceable landmark assets unless a chapter-specific override is
  approved.
  Extending `CampaignLevelDefinition.MAX_LEVELS` and any associated content
  registries remains part of that later chapter addition.
