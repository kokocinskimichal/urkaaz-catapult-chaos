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
- `DashboardScreen` — placeholder;
- `CampaignScreen` — placeholder;
- `LoadoutScreen` — placeholder;
- `ShopScreen` — placeholder;
- `MatchScreen` — working match screen;
- `ResultScreen` — placeholder.

The working match screen is split into:

- `MatchScreenController` — screen composition and refresh lifecycle;
- `MatchHudView` — timer, wind indicator and pause control;
- `AmmunitionPanelView` — ammunition selection and availability;
- `BattlefieldView` — touch input, camera and battlefield rendering;
- `MatchViewModel` — UI actions and presentation state.

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

The command `wypchnij` means: stage all changes, run the validation gate, create
a commit with the required Copilot co-author trailer, and push `develop`.

The same workflow is available as the project slash command `/push`, defined in
`.github/skills/push/SKILL.md`.
