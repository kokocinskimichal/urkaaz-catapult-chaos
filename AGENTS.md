# Urkaaaz agent guide

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
| `game-application` | Match session and local application gateway |
| `game-campaign` | Campaign definitions and progression rules |
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
- `CampaignScreen` — legacy-style campaign level selector, wired to navigation;
- `LoadoutScreen` — reads and validates a saved draft, then starts a match
  only after successful persistence; interactive editors remain incomplete;
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

## Validation gate

Before every commit, run:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Also review changed code against `architecture.md`, especially:

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
