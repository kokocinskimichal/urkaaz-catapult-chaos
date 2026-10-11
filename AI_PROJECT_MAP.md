# Urkaaaz — mapa projektu i przepływów

## Zakres

Urkaaaz jest modułową grą Android z lokalną, deterministyczną symulacją meczu.
Kod silnika pozostaje niezależny od Androida, a aplikacja Android jest hostem
prezentacji i wejścia użytkownika. Bieżąca gałąź robocza: `develop`.

W dokumentacji słowo **mecz** oznacza sesję rozgrywki i jej symulację. Warstwa
Android jest nazywana **match screen**, a wybrana definicja kampanii —
**campaign level**.

## Moduły i właściciele odpowiedzialności

| Moduł | Odpowiedzialność | Główne elementy |
|---|---|---|
| `game-contracts` | stabilne, niemutowalne kontrakty komunikacji | komendy, snapshoty, eventy, scenariusze, identyfikatory |
| `game-domain` | reguły i stan domeny gry | `Match`, fortece, jednostki, amunicja, obrażenia, konfiguracje |
| `game-simulation` | deterministyczne wykonywanie meczu | ticki, pociski, kolizje, teren, wiatr, jednostki, eventy |
| `game-application` | cykl życia i lokalny gateway meczu | `MatchSession`, `LocalMatchGateway` |
| `game-campaign` | definicje poziomów i konfiguracja kampanii | `CampaignLevelDefinition`, biom, wiatr, scenariusz |
| `game-ai` | decyzje AI na podstawie snapshotu | `AiAgent`, rekrutacja, dobór kontr, wysyłanie fal |
| `android-app` | lifecycle, nawigacja, input i renderowanie | `MainActivity`, ekrany, ViewModel, HUD, battlefield |

## Kierunek zależności

```text
android-app
  ├── game-application
  │     ├── game-simulation
  │     │     ├── game-domain
  │     │     └── game-contracts
  │     └── game-contracts
  ├── game-campaign
  └── game-contracts

game-ai ──> game-contracts
game-ai nie zna Androida
```

Moduły domeny, symulacji, aplikacji, kontraktów, kampanii i AI nie mogą
importować Androida. `android-app` jest jedynym właścicielem kodu platformy.

## Przepływ wejścia i stanu

```text
dotknięcie / gest / przycisk
  → MatchUiAction
  → MatchViewModel
  → LocalMatchGateway
  → MatchSession
  → DeterministicMatchSimulation
  → MatchSnapshot + MatchEvent
  → RenderStateMapper
  → MatchHudView / BattlefieldView
```

UI nie odsyła zmodyfikowanego snapshotu do silnika. Zmiany gameplayu przechodzą
przez komendy i granice domeny/aplikacji.

## Cykl życia meczu

1. `CampaignScreen` wybiera typowany `CampaignLevelDefinition`.
2. `MatchScreen` przekazuje campaign level do `MatchViewModel`.
3. `LocalMatchGateway` tworzy `MatchSession` z konfiguracją poziomu, w tym
   wiatrem i scenariuszem.
4. `MatchSession` uruchamia i aktualizuje deterministyczną symulację.
5. Snapshot jest mapowany na niemutowalny stan prezentacji.
6. Ekran meczu obsługuje lifecycle, pauzę, restart, celowanie, amunicję,
   jednostki, HUD i debug overlay.
7. Wynik meczu jest prezentowany przez istniejący uproszczony overlay; pełny
   ekran wyniku i zapis progresji pozostają niedokończone.

## Android i nawigacja

```text
MainActivity
  ├── AppNavigator
  └── ScreenHost
       └── aktywny ScreenContent
```

Dostępne ekrany:

- `StartScreen` — styl startowego menu;
- `DashboardScreen` — dashboard;
- `CampaignScreen` — wybór poziomu kampanii;
- `LoadoutScreen` — placeholder;
- `ShopScreen` — placeholder;
- `MatchScreen` — działający ekran meczu;
- `ResultScreen` — placeholder.

`MainActivity` posiada tylko lifecycle, system bars, navigator i host ekranu.
Nowe kontrolki trafiają do ekranu lub kontrolera ekranu, nie bezpośrednio do
`MainActivity`.

## Match screen

`MatchScreenController` składa i odświeża:

- `MatchHudView` — timer, wiatr, pauza;
- `AmmunitionPanelView` — wybór i dostępność amunicji;
- `UnitPanelView` — rekrutacja i wysyłanie jednostek;
- `BattlefieldView` — gesty, kamera i wejście pola bitwy;
- `BattlefieldRenderer` — kolejność i operacje rysowania;
- `BattlefieldCameraController` — zoom i pan;
- `AimGestureController` — lifecycle celowania;
- `RenderStateMapper` — mapowanie snapshotu na stan renderowania.

Efekty świata, pociski, trafienia i eksplozje korzystają z układu world-space
i transformacji kamery. HUD i kontrolki reload/inventory pozostają
screen-space.

## Ważne przepływy gameplayowe

### Strzał

```text
gest slingshot
  → komenda wystrzału
  → walidacja w domenie
  → symulacja pocisku w stałym ticku
  → wiatr / kolizja / obrażenia / event
  → snapshot i render
```

### Jednostki

Jednostki są tworzone przez `UnitFactory` z centralnych
`UnitConfigurations.kt`. Rekrutacja zużywa supply, jednostki ustawiają się w
garrisonie, mogą zostać wysłane falą, poruszają się w symulacji i wykonują
dedykowane interakcje: walka Defenderów i Raging Boars, atak Slingmastera,
detonacja Demolishera oraz atak fortecy przez Sapera.

### Amunicja

`AmmunitionCatalog` jest jedynym źródłem definicji amunicji dla gracza, AI i
symulacji. `AmmunitionType` pozostaje stabilnym identyfikatorem serializowanym.
Logowanie amunicji jest host-independent i nie może zmieniać stanu gry.

### Kampania i wiatr

`CampaignLevelDefinition.initialWind` przechodzi przez ViewModel, gateway i
sesję do `SimulationConfig`. `WindSystem` posiada stan dynamicznych zmian
wiatru; Android wyłącznie renderuje bieżący snapshot.

## Kluczowe pliki

| Ścieżka | Rola |
|---|---|
| `architecture.md` | zasady architektury i kontraktów |
| `migration.md` | plan etapów M0–M12 |
| `docs/PROJECT_KNOWLEDGE.md` | bieżąca wiedza implementacyjna |
| `android-app/src/main/java/com/urkaaaz/MainActivity.kt` | cienki entry point |
| `android-app/src/main/java/com/urkaaaz/navigation/AppNavigator.kt` | nawigacja |
| `android-app/src/main/java/com/urkaaaz/ui/MatchScreenController.kt` | składanie match screen |
| `android-app/src/main/java/com/urkaaaz/ui/BattlefieldRenderer.kt` | renderowanie pola |
| `android-app/src/main/java/com/urkaaaz/ui/RenderStateMapper.kt` | snapshot → render state |
| `game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt` | sesja meczu |
| `game-application/src/main/kotlin/com/urkaaaz/application/LocalMatchGateway.kt` | lokalny gateway |
| `game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt` | wykonanie symulacji |
| `game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchSnapshot.kt` | read model |
