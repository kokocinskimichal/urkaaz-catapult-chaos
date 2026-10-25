# Urkaaaz agent guide

## Dokumentacja i punkt wejścia

Ten plik jest punktem wejścia dla agenta i jest wczytywany na początku każdej
sesji. Poniższy indeks obejmuje dokumentację projektu poza instrukcjami skilli
w `.github/skills/`. Do zadania czytaj dokumenty z odpowiednich sekcji zamiast
wczytywać cały zbiór.

Komenda `/loadContext` wczytuje bazowy kontekst projektu i dobiera dokumenty
do bieżącego zadania według [indeksu dokumentów kontekstu](docs/agent-context-index.md).

### Architektura i kontrakty

- [Architektura](docs/architecture/architecture.md)
- [Reguły implementacji](docs/architecture/implementation-rules.md)
- [Architektura definicji poziomów](docs/architecture/level-definition-architecture.md)
- [Mapa projektu i przepływów](docs/architecture/project-map.md)
- [Struktura projektu](docs/architecture/project-structure.md)
- [Architektura Urkaaaz Studio (propozycja)](docs/architecture/studio-architecture.md)

### Stan i wiedza projektu

- [Changelog](docs/project/CHANGELOG.md)
- [Bieżący stan](docs/project/current-state.md)
- [Skrzynka pomysłów](docs/project/idea-inbox.md)
- [Przekrojowy backlog pomysłów](docs/project/ideas.md)
- [Wiedza implementacyjna](docs/project/knowledge.md)
- [Postęp refaktoryzacji](docs/project/refactor-progress.md)
- [Słownik](docs/reference/GLOSSARY.md)

### Migracja

- [Indeks dokumentów migracji](docs/migration/README.md)
- [Plan migracji](docs/migration/roadmap.md)
- [M0 — inwentaryzacja](docs/migration/m0-inventory.md)
- [M1 — raport](docs/migration/m1-report.md)
- [M2 — kontrakty](docs/migration/m2-contracts.md) · [raport](docs/migration/m2-report.md)
- [M3 — domena](docs/migration/m3-domain.md) · [raport](docs/migration/m3-report.md)
- [M4 — symulacja](docs/migration/m4-simulation.md) · [raport](docs/migration/m4-report.md)
- [M5 — kampania](docs/migration/m5-campaign.md) · [raport](docs/migration/m5-report.md)
- [M6 — AI](docs/migration/m6-ai.md) · [raport](docs/migration/m6-report.md)
- [M7 — assety](docs/migration/m7-assets.md) · [raport](docs/migration/m7-report.md)
- [M8 — assety](docs/migration/m8-assets.md) · [raport](docs/migration/m8-report.md)
- [M9 — UI](docs/migration/m9-ui.md) · [raport](docs/migration/m9-report.md)
- [M10 — integracja lokalna](docs/migration/m10-local-integration.md) · [raport](docs/migration/m10-report.md)
- [M11 — porównanie](docs/migration/m11-comparison.md) · [raport](docs/migration/m11-report.md)

### Kampania i mapy

- [Migracja map kampanii do JSON](docs/campaign/map-json-migration-plan.md)
- [Prompt implementacji map JSON](docs/campaign/map-json-implementation-prompt.md)
- [Rozdział 1 — README map](docs/campaign/maps/chapter1/README.md)
- [Rozdział 1 — porównanie map](docs/campaign/maps/chapter1/COMPARISON.md)

### Edytor map

- [Projekt edytora](docs/map-editor/design.md)
- [Ryzyka](docs/map-editor/risks.md)
- [Migracja edytora webowego](docs/map-editor/web-migration-plan.md)
- [Prompt implementacji edytora webowego](docs/map-editor/web-implementation-prompt.md)
- [README modułu webowego](docs/map-editor/web/README.md)

### Systemy gry

- [Projekt systemu amunicji](docs/systems/ammunition-design.md)
- [Projekt systemu walki](docs/systems/combat-design.md)
- [System kampanii](docs/systems/campaign-system.md)
- [Plan kamieni milowych kampanii](docs/systems/campaign-milestones-plan.md)
- [Prompt kamieni milowych kampanii](docs/systems/campaign-milestones-implementation-prompt.md)
- [System postępu gracza](docs/systems/player-progression-system.md)
- [Prompt postępu gracza](docs/systems/player-progression-system-implementation-prompt.md)
- [System loadoutu](docs/systems/loadout-system.md)
- [Prompt loadoutu](docs/systems/loadout-system-implementation-prompt.md)
- [System reliktów](docs/systems/relic-system.md)
- [System czarów](docs/systems/spell-system.md)
- [Prompt systemu czarów](docs/systems/spell-system-implementation-prompt.md)
- [Prezentacja Fire Rain](docs/systems/fire-rain-presentation.md)
- [Prompt prezentacji Fire Rain](docs/systems/fire-rain-presentation-implementation-prompt.md)
- [Prompty animacji Fire Rain](docs/systems/fire-rain-animgen-prompts.md)
- [Plan redesignu HUD-u meczu](docs/systems/match-hud-redesign-implementation-plan.md)
- [Plan popupu wyniku meczu](docs/systems/match-result-popup-implementation-plan.md)
- [Prompt popupu wyniku meczu](docs/systems/match-result-popup-implementation-prompt.md)
- [Pomysły na jednostki](docs/systems/units/ideas.md)
- [Interakcje jednostek](docs/systems/units/interactions.md)

### UI i prompty

- [Analiza brakujących mechanik UI](docs/ui/mechanics-gap-analysis.md)
- [Prompt systemu definicji poziomów](docs/prompts/IMPLEMENT_LEVEL_DEFINITION_SYSTEM.md)
- [Prompt systemu reliktów](docs/prompts/IMPLEMENT_RELIC_SYSTEM.md)

## Project scope

Urkaaaz is a mobile Android game. The active repository is:

```text
~/workspace/urkaaaz
```

In project conversations, **legacy** always means the separate checkout
`~/workspace/orc-catapult`. Do not modify that checkout for Urkaaaz tasks. The
GitHub repository for this project is
`kokocinskimichal/urkaaz-catapult-chaos`.

Terminology: when the user says **mecz**, they mean the gameplay session itself
and its runtime simulation. Use **match screen** for the Android presentation
layer and **campaign level** for the selected level definition.

## Repository structure

| Module | Responsibility |
|---|---|
| `game-contracts` | Immutable snapshots, commands, events and scenario contracts |
| `game-domain` | Game rules and domain state |
| `game-simulation` | Deterministic simulation, physics and terrain state |
| `game-application` | Match session/gateway and campaign/loadout use cases |
| `game-campaign` | Campaign level and milestone definitions |
| `game-ai` | AI decisions based on game state |
| `android-app` | Android lifecycle, navigation, screens and rendering |

## Android screen architecture

`MainActivity` is the Android entry point. It owns only lifecycle, system bars,
the `AppNavigator` and the `ScreenHost`.

```text
MainActivity
 ├── AppNavigator
 └── ScreenHost
      └── active ScreenContent
```

Available screens:

- `StartScreen` — legacy-style start menu;
- `DashboardScreen` — legacy-style dashboard, wired to navigation;
- `CampaignScreen` — legacy-style selector backed by persisted
  `CampaignProgress`;
- `LoadoutScreen` — reads profile and campaign spell entitlements, preserves
  saved per-level choices, and starts only after successful validation/save;
- `ShopScreen` — placeholder;
- `MatchScreen` — working match screen;
- `ResultScreen` — placeholder.

The working match screen is split into:

- `MatchScreenController` — screen composition and refresh lifecycle;
- `MatchHudView` — timer, wind indicator and pause control;
- `AmmunitionPanelView` — ammunition selection and availability;
- `BattlefieldView` — touch input, camera and battlefield rendering;
- `MatchViewModel` — UI actions and presentation state.

Campaign presentation may reuse the legacy visual language, but campaign level
selection must use `game-campaign` contracts. Do not copy legacy campaign
progression or engine behavior into Android UI. A selected level is passed as a
typed campaign level into `MatchScreen` and then `MatchViewModel`.

Do not add new screen controls directly to `MainActivity`. Add a screen-owned
view/controller and connect it through `AppScreen` and `AppNavigator`.

## Layering rules

- Android UI may consume contracts, but the game engine must not import Android.
- UI sends `MatchUiAction` or application commands; it must not mutate domain
  state directly.
- Renderers receive immutable render state, not mutable domain objects.
- The `Match`/simulation is the single owner of gameplay state.
- Persistence and campaign state must not be mixed into runtime simulation state.
- Do not introduce a second parallel terrain, asset or navigation contract.
- Prefer existing helpers and patterns before adding new abstractions.

## Fortress and catapult presentation lock

The fortress/catapult layout is frozen at its current shared baseline. Fortress
sprites and their hitboxes are `672 × 504` world units, with a `+35` world-Y
presentation offset from the authored foundation. Catapult sprites are
`182.4` world units wide, positioned `110` units outward from each fortress
center and at `foundation + 35 - 200` on world Y. Render catapults before
fortresses and the terrain cap after fortresses, so the fortresses occlude the
catapults and the cap can conceal the fortresses' lower edges.

Do not change this baseline unless the user asks. Every later fortress movement
must apply the same world-space translation to its catapult, FHP and associated
mage placement; every fortress size change must apply the same scale factor to
their linked presentation and collision geometry while preserving the
catapult's `0.8` relative sprite-size multiplier. Keep these transforms global
across maps, biomes and levels, and update the simulation, Android renderer, web
map-editor preview, tests and knowledge files together.

Projectile launch and aiming origins are deliberately fortress-relative, not
derived from the relocated catapult sprite: X is `40` world units outward from
the fortress center, and Y is `360 * worldHeight / 4800` units above the
presented fortress foundation, clamped above local terrain when needed. The
trajectory preview, aiming circle and simulation must use the same anchor.

## Ammunition rules

- `AmmunitionCatalog` is the single source of truth for player, AI and
  simulation ammunition definitions.
- `AmmunitionType` remains a stable serialized identifier; do not duplicate
  ammunition balance values in Android or AI.
- Projectile, impact, explosion and lingering-area visuals use world-space
  coordinates and the battlefield camera transform. HUD reload and inventory
  controls remain screen-space.
- Ammunition logging is host-independent and must never change gameplay state.

## Animation asset naming

- Name every animation-frame PNG as
  `anim_<category>_<target>_<type>[_<variant>]_<frame:3>.png`.
- Use `character`, `spell` or `ammunition` as the category; use lowercase
  snake_case for the target and type, and omit the variant for neutral assets.
- Use `blue`/`red` for team variants, normalize character locomotion to
  `move`, combat to `attack`, and death to `death`.
- Start every sequence at `000`, use three digits for every frame, and keep
  indices contiguous. Examples:
  `anim_character_defender_move_blue_000.png`,
  `anim_spell_runic_bastion_creation_000.png`,
  `anim_ammunition_fire_rain_fragment_flight_000.png`.
- Keep animation target/action metadata inferable from this stable naming
  structure so Studio can group sequences without special-case legacy aliases.

## Validation gate

Before every commit, run:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Also review changed code against `docs/architecture/architecture.md`, especially:

- single responsibility;
- dependency direction;
- immutable snapshots;
- explicit command/event boundaries;
- no gameplay rules in Android UI;
- no Android dependencies in domain or simulation modules.

## Change workflow

1. Read the relevant module and its tests before editing.
2. Make the smallest complete change that preserves existing behavior.
3. Add or update focused tests for behavior and contracts.
4. Update directly related documentation.
5. Run the validation gate.
6. Only commit or push when the user explicitly asks, or says `wypchnij`.

## Zapisywanie pomysłów

Gdy użytkownik prosi o zapisanie pomysłu, dopisz go do
[`docs/project/idea-inbox.md`](docs/project/idea-inbox.md), krótko i bez
zmieniania jego statusu ani wdrażania. Podczas `/push` przenieś zapis do
odpowiedniej dokumentacji tematycznej; pomysły przekrojowe lub jeszcze
nieprzypisane trafiają do [`docs/project/ideas.md`](docs/project/ideas.md).

For prompts that request a new game or a substantial gameplay change, check
whether the core player loop, success and failure conditions, difficulty
progression, responsiveness, readability/input feedback, and scope priorities
are clear. If important details are missing, point that out before proceeding;
ask a focused question when the ambiguity materially affects behavior or scope.

The command `wypchnij` means: stage all changes, run the validation gate, create
a commit with the required Copilot co-author trailer, and push `develop`.

The same workflow is available as the project slash command `/push`, defined in
`.github/skills/push/SKILL.md`. Before committing, `/push` updates the relevant
knowledge Markdown files with durable facts about the changes being delivered.
