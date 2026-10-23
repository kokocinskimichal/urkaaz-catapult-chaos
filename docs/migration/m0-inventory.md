# M0 — Inwentaryzacja starego projektu

Status: `READY_FOR_REVIEW`

Data wykonania: 2026-10-08

## 1. Zakres i zasada etapu

M0 nie zmienia implementacji gry. Jego celem jest ustalenie:

- jaki kod zawiera właściwe reguły gry;
- jaki kod jest mechanizmem Androida, UI albo renderingu;
- które elementy należy przepisać do nowej architektury;
- które elementy można przenieść jako dane lub konfigurację;
- które elementy należy odrzucić zamiast kopiować do nowego projektu;
- jakie assety są źródłami, a jakie są już zasobami runtime.

Nowy projekt `~/workspace/urkaaz` nie został jeszcze utworzony.

## 2. Baseline projektu

| Element | Wynik |
|---|---|
| Repozytorium | `kokocinskimichal/urkaaaz` |
| Branch | `develop` |
| Ostatni commit | `76cfdbd Organize application packages` |
| Moduł Gradle | `:app` |
| Namespace | `com.urkaaaz.game` |
| Compile SDK | 34 |
| Min SDK | 24 |
| Kotlin/JVM | 17 |
| Moduły Android | 1 (`app`) |
| Zmiany kodu względem HEAD | brak |
| Nowe dokumenty | `architecture.md`, `migration.md` |

### Walidacja baseline’u

| Sprawdzenie | Wynik |
|---|---|
| `./gradlew :app:testDebugUnitTest --quiet --no-build-cache -Dkotlin.incremental=false` | PASS |
| `./gradlew :app:assembleDebug --quiet --no-build-cache -Dkotlin.incremental=false` | PASS |
| Liczba przypadków testowych | 134 |
| `git diff --check` | PASS |

Pierwsze uruchomienie testów i buildu z domyślnym cache’em inkrementalnym zakończyło się błędem `EOFException` w cache’u Kotlin. Nie jest to błąd źródeł. Powtórzenie z wyłączonym cache’em inkrementalnym zakończyło się poprawnie. W M0 zapisujemy tę informację, aby późniejsze porównania nie myliły awarii cache’a z regresją kodu.

## 3. Inwentaryzacja struktury kodu

Kod produkcyjny zawiera 95 plików Kotlin:

| Pakiet | Liczba plików | Rola obecnie | Decyzja migracyjna |
|---|---:|---|---|
| `game` | 8 | aliasy, dane globalne, język, reset, wydarzenia | rozdzielić; dane i eventy przepisać, Androidowe utility przenieść do adapterów |
| `domain` | 7 | modele świata, meczu, celów, pocisku i snapshotu | przepisać jako czystą domenę |
| `engine` | 14 | orkiestracja meczu, fizyka, kolizje, obrażenia, teren, wiatr, jednostki | przepisać do `MatchSimulation` i wyspecjalizowanych systemów |
| `ammo` | 11 | typy amunicji, efekty i implementacje pocisków | przepisać jako reguły domenowe/symulacyjne |
| `spells` | 19 | czary, kontekst, system, progresja | rozdzielić reguły czarów od progresji i persystencji; przepisać |
| `units` | 11 | modele jednostek, zachowania, ruch i typy | przepisać do domeny oraz systemów jednostek |
| `ai` | 1 | decyzje AI bezpośrednio na `GameEngine` | przepisać na `MatchSnapshot` → `MatchCommand` |
| `input` | 2 | komendy gracza i celowanie | komendy przenieść do kontraktów; gesty zostawić w UI |
| `campaign` | 4 | definicje poziomów, progresja i nagrody | definicje przepisać jako scenariusze; progresję odseparować |
| `progression` | 6 | loadout, złoto, życia, amunicja, persystencja | rozdzielić use case’y, repozytoria i modele |
| `render` | 3 | asset loader, kontekst i renderery zależne od silnika | przepisać na renderowanie snapshotu |
| `ui` | 8 | aktywność, widok pola bitwy, HUD i kontrolery ekranów | rozbić na Activity, ViewModel/use case’y, BattlefieldView i renderery |
| `tutorial` | 1 | overlay Androida | pozostawić jako adapter UI korzystający ze stanu aplikacji |

### Największe koncentracje odpowiedzialności

| Plik | Przybliżona liczba linii | Problem migracyjny |
|---|---:|---|
| `engine/GameEngine.kt` | 2 132 | centralny właściciel stanu, reguł, czasu i orkiestracji |
| `ui/GameView.kt` | 2 701 | input, pętla klatek, kamera, odczyt silnika i rendering |
| `ui/MainActivity.kt` | 3 305 | lifecycle, nawigacja, konfiguracja meczu, HUD, AI, tutorial i persystencja |
| `engine/UnitMovementSystem.kt` | 677 | szeroki kontekst i sprzężenie z runtime state |
| `engine/UnitCombatSystem.kt` | 423 | walka jednostek powiązana z modelem starego silnika |
| `render/WorldRenderers.kt` | 499 | renderery przyjmują `GameEngine` zamiast read modelu |

## 4. Rejestr głównych reguł gameplayu

| Obszar | Obecne źródła | Właściciel docelowy | Decyzja |
|---|---|---|---|
| stan meczu i lifecycle | `GameEngine`, `MatchState`, `MatchController` | `Match` / `MatchSession` | przepisać, nie kopiować klasy centralnej |
| komendy gracza | `input/PlayerCommands.kt`, callbacki UI | `MatchCommand` | przepisać jako jawny kontrakt |
| odczyt stanu | `domain/GameSnapshot.kt`, publiczne pola silnika | `MatchSnapshot` | przepisać jako niemutowalny read model |
| wydarzenia | `GameplayEvents.kt`, komunikaty silnika | `MatchEvent` | przepisać jako fakty domenowe |
| balistyka i lot pocisku | `Projectile.kt`, `ProjectileSystem.kt`, `ProjectileCombatSystem.kt` | `ProjectileSimulation` | przepisać i przetestować deterministycznie |
| wiatr | `WindSystem.kt`, pola silnika | `WindSimulation` | przepisać |
| kolizje | `CollisionSystem.kt` | `CollisionResolver` / symulacja | przepisać |
| teren i kratery | `TerrainSystem.kt`, `ProjectileImpactSystem.kt` | `TerrainSimulation` | przepisać |
| obrażenia i cele | `DamageSystem.kt`, `ObjectiveSystem.kt` | reguły celów i obrażeń | przepisać |
| amunicja | pakiet `ammo` | `AmmunitionRule` / registry | przepisać |
| czary | pakiet `spells` | `SpellRule` / registry | przepisać |
| jednostki | pakiet `units`, systemy ruchu i walki | `UnitSimulation` | przepisać |
| zwycięstwo i porażka | `GameEngine`, cele i bossowie | `MatchOutcomeRules` | przepisać |
| kampania | pakiet `campaign` | `MatchScenario` + campaign application layer | rozdzielić konfigurację od progresji |
| AI | `ai/AiController.kt` | `AiAgent` | przepisać na snapshoty i komendy |
| tutorial | `tutorial/TutorialOverlayController.kt` | aplikacyjny tutorial + UI adapter | rozdzielić |
| celowanie dotykowe | `GameView`, `SlingshotAiming.kt` | UI input mapper | przepisać jako adapter do komendy |

## 5. Elementy do odrzucenia jako mechanizmy

Poniższych elementów nie należy kopiować do nowego projektu w obecnej postaci:

- `GameEngine` jako jedna klasa koordynująca cały mecz;
- `DomainAliases.kt` i aliasy ukrywające zależności do starego silnika;
- przekazywanie `GameEngine` do rendererów przez `RenderContext`;
- renderowanie bezpośrednio z mutowalnego stanu silnika;
- `GameView` jako jednoczesny właściciel pętli czasu, inputu, kamery i renderingu;
- `MainActivity` jako właściciel logiki kampanii, konfiguracji meczu, AI i HUD;
- callbacki umożliwiające systemom mutowanie szerokiego kontekstu silnika;
- publiczne mutowalne kolekcje runtime;
- Androidowe `Context`, `View`, `Canvas` i `Bitmap` w warstwie domeny/symulacji;
- tymczasowe fasady będące lustrzaną kopią API `GameEngine`.

## 6. Kontrakty migracyjne

Docelowy przepływ dla meczu:

```text
UI / AI / Tutorial
        │
        ▼
   MatchCommand
        │
        ▼
   MatchSession
        │
        ▼
 Match + MatchSimulation
        │
        ├── MatchSnapshot
        └── MatchEvent
                │
                ▼
        ViewModel / renderer / UI
```

Minimalna mapa obecnych wejść:

| Obecne wejście | Docelowy kontrakt |
|---|---|
| dotknięcie/przeciągnięcie katapulty | `MatchCommand.Aim` / `MatchCommand.Fire` |
| wybór amunicji | `MatchCommand.SelectAmmunition` |
| aktywacja czaru | `MatchCommand.CastSpell` |
| decyzja AI o strzale | ten sam `MatchCommand.Fire` |
| decyzja AI o jednostce | `MatchCommand.DeployUnit` / `MoveUnit` |
| upływ czasu | `MatchSession.advance(deltaTime)` |
| stan do renderowania | `MatchSnapshot` |
| trafienie, eksplozja, zwycięstwo | `MatchEvent` |

## 7. Inwentaryzacja testów

Aktualnie istnieje 15 plików testowych:

| Grupa | Testy |
|---|---|
| AI | `AiControllerTest` |
| amunicja/pociski | `AmmoSystemTest`, `ProjectileSystemTest`, `ProjectileAnimationSizingTest` |
| architektura | `ArchitectureBoundaryTest` |
| kolizje/teren | `CollisionSystemTest`, `TerrainSystemTest` |
| silnik | `GameEngineIntegrationTest`, `RealtimeGameEngineTest` |
| cele | `ObjectivesSystemTest` |
| czary | `SpellsSystemTest` |
| jednostki | `UnitsSystemTest` |
| celowanie | `SlingshotAimingTest` |
| loadout | `LoadoutModelsTest` |
| persystencja | `PersistenceTest` |

Testy te są baseline’em regresyjnym, ale nie stanowią jeszcze kompletnego kontraktu nowej architektury. W późniejszych etapach trzeba dodać testy domeny, snapshotów, komend, eventów i deterministycznego replaya.

## 8. Inwentaryzacja runtime resources

| Lokalizacja/typ | Liczba |
|---|---:|
| `drawable-nodpi` | 1 158 |
| `drawable` | 14 |
| layout XML | 19 |
| `values` i warianty | 5 |
| `raw` | 1 |
| font | 1 |
| pliki PNG w `app/src/main/res` | 1 157 |
| pliki XML w `app/src/main/res` | 40 |

Główne grupy do dalszego przypisania:

- teren i tła biomów;
- fortece i cele centralne;
- jednostki oraz animacje kierunkowe;
- animacje pocisków `flight` i `impact`;
- ikony amunicji i czarów;
- elementy HUD i dashboardu;
- tutorial i highlighty;
- assety bossów oraz Siege Sovereign;
- audio i font.

W M0 nie kopiujemy ani nie normalizujemy żadnego assetu. Ustalamy jedynie źródło, przeznaczenie i status.

## 9. Inwentaryzacja źródeł assetów

W `assets_src` znaleziono:

| Grupa | Liczba plików | Status |
|---|---:|---|
| `user_reference` | 1 211 | wymaga przypisania właściciela i walidacji przed kopiowaniem |
| `generated_character_animations` | 240 | źródła animacji postaci; walidować grupami |
| `generated_projectile_animations` | 121 | źródła animacji pocisków; walidować flight/impact |
| PNG | 1 556 | główny format źródeł graficznych |
| ZIP | 5 | paczki źródłowe do rozpakowania dopiero w etapie M8 |
| JPG | 3 | tła lub referencje; sprawdzić alpha i docelowy format |
| JSON | 1 | przeznaczenie do ustalenia przed migracją |

Najważniejsze rodziny źródeł:

- `green_frontier`, `frostbound`, `ashen_march`, `copperwood`, `sunken_marshes`;
- `terrain_decorations`, `terrain_soil_fill_variants`, `modular_crater_kit`;
- `fortress_chapter_02`, `fortress_chapter_05`;
- `central_objectives`;
- `defender_*`;
- `future_units`;
- `spells`, `dashboard_asset_pack`, `dashboard_buttons`;
- `generated_projectile_animations`.

## 10. Macierz decyzji migracyjnych

| Rodzina | Przenieść jako | Przepisać jako | Odrzucić |
|---|---|---|---|
| reguły meczu | dane domenowe, scenariusze | `Match`, `MatchSimulation`, use case’y | centralną klasę `GameEngine` |
| pociski | parametry i balans | system lotu, kolizji i impactu | bezpośredni dostęp UI do pocisku |
| teren | konfigurację biomu | symulację terenu i deformacji | renderowanie terenu w silniku |
| jednostki | typy, parametry i animacje | ruch, walka, lifecycle | callbackowy szeroki kontekst |
| czary | definicje, koszty, efekty | reguły aktywacji i zastosowania | zależność czaru od Androida |
| AI | parametry decyzyjne | agent snapshot → command | `choose*` przyjmujące `GameEngine` |
| kampania | definicje poziomów i nagrody | scenariusze/use case’y progresji | logikę kampanii w `MainActivity` |
| UI | layouty i zatwierdzone assety | ViewModel, mappery, kontrolery ekranów | `MainActivity` jako koordynator gameplayu |
| rendering | zweryfikowane bitmapy | renderery snapshotu | renderery przyjmujące `GameEngine` |
| asset pipeline | źródła i metadane | walidacja/import w M7–M8 | bezwarunkowe kopiowanie całego `res/` |

## 11. Otwarta lista przed M1

Poniższe kwestie wymagają decyzji lub doprecyzowania podczas akceptacji M0:

- czy wszystkie obecne tryby i mechaniki mają wejść do pierwszego vertical slice’a;
- które poziomy kampanii będą pierwszym scenariuszem referencyjnym;
- które assety należą do pierwszego slice’a, a które zostają tylko w rejestrze;
- czy `GameDataReset` i obecne repozytoria progresji są tymczasowymi adapterami, czy wymagają zachowania kompatybilności danych;
- czy istniejące pliki `.DS_Store` w katalogach assetów mają zostać usunięte przy M8 jako śmieci źródłowe.

## 12. Pakiet odbiorczy M0

Do akceptacji użytkownika należą:

- ten raport;
- `architecture.md`;
- `migration.md`;
- wyniki baseline testów i buildu zapisane w sekcji 2;
- mapa decyzji migracyjnych z sekcji 10;
- lista otwartych decyzji z sekcji 11.

### Kryteria `ACCEPTED`

M0 można oznaczyć jako `ACCEPTED`, gdy użytkownik potwierdzi:

- że zakres logiki gry jest kompletny;
- że podział `przenieść / przepisać / odrzucić` jest zgodny z zamierzoną architekturą;
- że grupy assetów są rozpoznane;
- że baseline testów i buildu jest wystarczający;
- że można rozpocząć M1 bez dopisywania kolejnych elementów do inwentaryzacji.

Do czasu tej akceptacji nie należy tworzyć `~/workspace/urkaaz` ani implementować kontraktów nowej architektury.
