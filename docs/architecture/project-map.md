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
| `game-domain` | typowane modele i reguły domenowe | fortece, jednostki, amunicja, czary, obrażenia i konfiguracja balansu |
| `game-simulation` | deterministyczne wykonywanie meczu | ticki, pociski, kolizje, teren, wiatr, jednostki, eventy |
| `game-application` | cykl życia meczu, TutorialSystem, lokalny gateway, loadout, Shop, reward tracking, LifeSystem i przypadki użycia kampanii | `MatchSession`, `LocalMatchGateway`, `application.tutorial`, `application.loadout`, `application.shop`, `application.progression`, `application.campaign`, `application.reward` |
| `game-campaign` | definicje poziomów, nagród, milestone'ów i konfiguracja kampanii | `CampaignLevelDefinition`, `CampaignRewardRegistry`, `CampaignMilestoneRegistry`, biom, wiatr, scenariusz |
| `game-ai` | decyzje AI na podstawie snapshotu | `AiAgent`, rekrutacja, dobór kontr, wysyłanie fal |
| `android-app` | lifecycle, nawigacja, input i renderowanie | `MainActivity`, ekrany, ViewModel, HUD, battlefield |

## Kierunek zależności

```text
android-app -> game-assets, game-application, game-campaign, game-contracts, game-simulation
game-application -> game-ai, game-campaign, game-contracts, game-domain, game-simulation
game-campaign -> game-assets, game-contracts, game-domain
game-ai -> game-contracts, game-domain
game-simulation -> game-contracts, game-domain
game-domain -> game-contracts
game-assets -> game-contracts
game-contracts -> (none)
```

`android-app` zależy bezpośrednio od `game-simulation` wyłącznie dla adapterów
logowania implementujących host-independent porty symulacji. `MatchViewModel`
nie importuje tych portów; tworzy je `MatchScreenController` jako composition
root. `game-ai` używa typowanych definicji balansu i wspólnej fizyki z domeny,
a `game-campaign` korzysta z domenowych definicji czarów oraz katalogu assetów.

Moduły domeny, symulacji, aplikacji, kontraktów, kampanii, AI i assetów nie mogą
importować Androida ani UI. Kierunek zależności produkcyjnych jest sprawdzany
przez `ArchitectureBoundaryTest` w `game-contracts`.

Runtime state meczu należy obecnie do `DeterministicMatchSimulation`, a
`MatchSession` w `game-application` zarządza jego lifecycle. Dawne minimalne
szkielety `game-domain/Match.kt` i `game-simulation/MatchSimulation.kt` zostały
usunięte; nie istnieje równoległa ścieżka obsługi meczu.

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

1. `CampaignScreen` przekazuje numer wybranego poziomu do `LoadoutScreen`.
2. `LoadoutScreen` asynchronicznie odczytuje profil i zapis per-level przez
   `LoadoutUseCases`; błędy lub nieprawidłowe dane blokują start.
3. Po świeżej walidacji i zapisie `PlayerLoadoutConfig` oraz
   `PlayerInventoryConfig` przechodzą do `MatchScreen` i scenariusza kampanii.
4. `LocalMatchGateway` tworzy `MatchSession` z konfiguracją poziomu i
   opcjonalnym scenariuszem, w tym wiatrem i loadoutem.
5. `MatchSession` odrzuca niedostępne/niewybrane jednostki i amunicję gracza,
   a następnie uruchamia i aktualizuje deterministyczną symulację.
6. Snapshot jest mapowany na niemutowalny stan prezentacji.
7. Ekran meczu obsługuje lifecycle, pauzę, restart, celowanie, amunicję,
   jednostki, HUD i debug overlay.
8. Uporządkowane eventy z `MatchSession` zasilają RewardTracker; przy
   `MatchEvent.MatchFinished` wynik trafia do `CampaignSystem` i
   `RewardSettlementUseCase`.
9. Tylko `BLUE_WIN` atomowo zapisuje nagrodę złota, ukończenie, następny poziom,
   milestone'y i receipt. ShopSystem niezależnie rozlicza użyty stock po każdym
   `MatchFinished` oraz przed jawnym stopem/restartem; niewykorzystane stocki
   zostają. Demo, porażka, remis i poddanie nie dają nagrody kampanii.
10. Kampanijny non-win atomowo zużywa jedno życie przez LifeSystem i zapisuje
    receipt `MatchId`; Demo/Test i wygrana nie zużywają życia. Nieudany zapis
    blokuje zamknięcie popupu wyniku do retry.
11. Etykiety trafieniowych bonusów są prezentacyjne i tymczasowe; popup meczu
    pokazuje zapisany wynik nagrody, a błąd zapisu można ponowić bez
    restartowania meczu. Powrót do CampaignScreen odczytuje trwały postęp,
    oznacza ukończone levele i wyłącza ich wybór; pełny `ResultScreen`
    pozostaje placeholderem.

## Android i nawigacja

```text
MainActivity
  ├── AppNavigator
  └── ScreenHost
       └── aktywny ScreenContent
```

Dostępne ekrany:

- `SplashScreen` — krótkie runiczne intro, które automatycznie otwiera Dashboard;
- `DashboardScreen` — dashboard;
- `CampaignScreen` — karuzela pełnoekranowych map kampanii i wybór poziomu;
- `LoadoutScreen` — edytuje jednostki, sloty amunicji i czary; wewnętrzne
  szczegóły zachowują draft/scroll, a start wymaga udanego zapisu;
- `ShopScreen` — zakupy za OrCoins, dostępny z Dashboardu i Loadoutu;
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

Jednostki są tworzone przez `UnitFactory` z typowanego `UnitCatalog` wczytanego
z `game-balance.json`. Rekrutacja zużywa supply, jednostki ustawiają się w
garrisonie, mogą zostać wysłane falą, poruszają się w symulacji i wykonują
dedykowane interakcje: walka Defenderów i Raging Boars, atak Slingmastera,
detonacja Demolishera oraz atak fortecy przez Sapera.

### Amunicja

`AmmunitionCatalog` budowany z `game-balance.json` jest wspólnym źródłem
profilów dla gracza, AI i symulacji. `AmmunitionType` pozostaje wyłącznie
stabilnym identyfikatorem serializowanym.
Logowanie amunicji jest host-independent i nie może zmieniać stanu gry.

### Kampania i wiatr

`CampaignLevelDefinition.initialWind` przechodzi przez ViewModel, gateway i
sesję do `SimulationConfig`. `WindSystem` posiada stan dynamicznych zmian
wiatru; Android wyłącznie renderuje bieżący snapshot.

`CampaignSystem` w `game-application` waliduje dostęp do poziomów i koordynuje
postęp. `RewardSettlementUseCase` rozlicza wynik przez port
`game-application`; `DataStoreRewardSettlementAdapter` atomowo zapisuje saldo
profilu, kampanię i receipts w `android-app`. `CampaignRewardRegistry` w
`game-campaign` łączy nagrody poziomowe z milestone'ami Haste (ukończenie 5,
dostępny od 6) i Heal (ukończenie 7, dostępny od 8).
`CampaignAwareLoadoutProgressionPort` łączy kampanijne odblokowania z profilem
wyłącznie w read modelu. Domyślne wybory milestone'ów stosują się do nowego
loadoutu, nie nadpisują istniejącego. Zastosowane milestone IDs są
przekazywane jako sygnały do TutorialSystemu, który nie zapisuje ich w
CampaignProgress. `ShopUseCases` korzysta z cen w `GameBalanceConfig` i atomowego
portu profilu. `DataStorePlayerProgressionRepository` oraz
`DataStoreRewardSettlementAdapter` współdzielą profile codec, a transakcje
stocku używają tego samego DataStore. LifeSystem w `application.progression`
zarządza życiami między meczami przez osobny read model i port atomowej mutacji
profilu; `CampaignLevelStartUseCase` świeżo rewaliduje start, a non-win flow
rozlicza receipt bez zmiany `CampaignProgress`.

### TutorialSystem

`TutorialEngine` w `game-application.application.tutorial` konsumuje sygnały
ekranów, MatchEventy przez `TutorialMatchEventMapper`, fakty scenariusza i
nowo zastosowane milestone IDs. Runtime/content source to
`game-application/src/main/resources/tutorial/tutorial-catalog.json`;
Dashboard RESET czyści statusy terminalne `tutorial.*` we wspólnym Preferences
DataStore. Androidowy `TutorialHost` opakowuje `ScreenHost`, renderuje Hint i
Spotlight, a screen-owned target providers rozwiązuje na podstawie render
state/kamery. Dynamiczne targety przenoszą konkretny `targetInstanceId`.
Urkaaaz Studio ma oddzielną zakładkę Tutorials do typed editing, walidacji i
hash-guarded save. W v1 zatwierdzona treść obejmuje tylko `match.basics`;
pozostałe screen i mechanic tutorials czekają na akceptację.

## Kluczowe pliki

| Ścieżka | Rola |
|---|---|
| `docs/architecture/architecture.md` | zasady architektury i kontraktów |
| `docs/migration/roadmap.md` | plan etapów M0–M12 |
| `docs/project/knowledge.md` | bieżąca wiedza implementacyjna |
| `android-app/src/main/java/com/urkaaaz/MainActivity.kt` | cienki entry point |
| `android-app/src/main/java/com/urkaaaz/navigation/AppNavigator.kt` | nawigacja |
| `android-app/src/main/java/com/urkaaaz/ui/MatchScreenController.kt` | składanie match screen |
| `android-app/src/main/java/com/urkaaaz/ui/BattlefieldRenderer.kt` | renderowanie pola |
| `android-app/src/main/java/com/urkaaaz/ui/RenderStateMapper.kt` | snapshot → render state |
| `game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt` | sesja meczu |
| `game-application/src/main/kotlin/com/urkaaaz/application/LocalMatchGateway.kt` | lokalny gateway |
| `game-application/src/main/kotlin/com/urkaaaz/application/tutorial/TutorialEngine.kt` | deterministyczny runtime TutorialSystemu |
| `game-application/src/main/resources/tutorial/tutorial-catalog.json` | treść i target IDs tutoriali |
| `android-app/src/main/java/com/urkaaaz/tutorial/TutorialHost.kt` | host tutoriala, overlay i adapter sygnałów |
| `android-app/src/main/java/com/urkaaaz/persistence/DataStoreTutorialProgressRepository.kt` | wersjonowany postęp tutoriala |
| `studio/web/src/tabs/tutorials/TutorialsTab.tsx` | edytor katalogu tutoriali |
| `game-application/src/main/kotlin/com/urkaaaz/application/loadout/` | loadout, walidacja, jawne porty progresji/zapisu i read-only projekcja katalogów dla Android UI |
| `game-application/src/main/kotlin/com/urkaaaz/application/progression/PlayerProgressionUseCases.kt` | inicjalizacja i walidacja snapshotu profilu |
| `game-application/src/main/kotlin/com/urkaaaz/application/progression/LifeSystem.kt` | życie, regeneracja, granty i read model |
| `game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignLevelStartUseCase.kt` | świeża autoryzacja startu kampanii z gate LifeSystemu |
| `game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignMatchCompletionUseCase.kt` | routing BLUE_WIN/non-win rozliczeń kampanii i żyć |
| `game-application/src/main/kotlin/com/urkaaaz/application/shop/ShopUseCases.kt` | katalog ofert i atomowe zakupy |
| `game-application/src/main/kotlin/com/urkaaaz/application/shop/MatchStockUsageTracker.kt` | zliczanie udanych użyć stocku w generacji meczu |
| `game-application/src/main/kotlin/com/urkaaaz/application/shop/MatchStockSettlementUseCase.kt` | idempotentne rozliczenie użyć stocku |
| `android-app/src/main/java/com/urkaaaz/persistence/DataStorePlayerProgressionRepository.kt` | wersjonowany adapter profilu |
| `android-app/src/main/java/com/urkaaaz/persistence/PlayerProgressionDataStoreCodec.kt` | wspólny codec profilu schema 2 dla życia, zakupów, nagród i settlementu stocku |
| `android-app/src/main/java/com/urkaaaz/persistence/DataStoreLifeStateRepository.kt` | migracja i atomowe transakcje LifeState w profilu |
| `android-app/src/main/java/com/urkaaaz/persistence/SystemWallClock.kt` | lokalny adapter wall-clock dla LifeSystem |
| `android-app/src/main/java/com/urkaaaz/persistence/DataStoreLoadoutRepository.kt` | wersjonowany adapter loadoutu |
| `game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt` | wykonanie symulacji |
| `game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchSnapshot.kt` | read model |
