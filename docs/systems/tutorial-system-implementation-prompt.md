# Prompt implementacji TutorialSystem

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

---

## Prompt

Zaimplementuj **TutorialSystem** zgodnie z:

- `docs/systems/tutorial-system.md` — zatwierdzoną architekturą, decyzjami,
  kontraktami, regułami prezentacji, planem testów i kryteriami akceptacji;
- `docs/reference/GLOSSARY.md` — definicjami Tutorial Hint, Tutorial
  Spotlight, Tutorial Target i regułami legacy (sekcja tutoriala);
- `AGENTS.md`, `docs/architecture/architecture.md`,
  `docs/architecture/implementation-rules.md`,
  `docs/architecture/project-map.md` i `docs/project/knowledge.md` — aktualną
  architekturą i workflow;
- `docs/systems/campaign-system.md` i `docs/systems/campaign-milestones-plan.md`
  — źródłem sygnałów milestone'ów (Haste, Heal);
- aktualnym kodem i testami. Dokumenty opisują zamiar, nie dowodzą, że
  kontrakt albo funkcja już istnieje.

Legacy (`~/workspace/orc-catapult`) jest tylko do odczytu i służy jako źródło
mechanik oraz tekstów. Nie kopiuj kodu legacy, `when (level)` ani numeracji
kroków.

Nie kończ na analizie ani planie. Implementuj, testuj i aktualizuj
dokumentację w bieżącej sesji, o ile nie trafisz na rzeczywisty blocker
wymagający decyzji użytkownika.

### Zatwierdzone decyzje — nie pytaj ponownie

- Treść to JSON w `game-application/src/main/resources/tutorial/`, plus
  zakładka **Tutorial** w Urkaaaz Studio.
- Wyzwalanie kontekstowe: `ScreenEntered`, `MatchStarted`,
  `MatchEventObserved`, `CampaignMilestoneApplied`. Każdy tutorial pokazuje
  się raz; maksymalnie jeden aktywny, reszta w kolejce wg `priority` i
  `requires`.
- `Skip` jest dostępny na każdym kroku i zapisuje status `SKIPPED`. Systemowy
  Back podczas aktywnego tutoriala wykonuje `Skip` i jest obsługiwany przez
  `TutorialHost` przed `ScreenContent.onBackPressed()`.
- Hint z `NEXT` pauzuje symulację. Kroki wymagające akcji gracza działają na
  żywym meczu z wstrzymanym AI. Po tutorialu AI wraca.
- Persystowane są wyłącznie statusy terminalne (`COMPLETED`/`SKIPPED`),
  wersja schematu i rewizja katalogu, w kluczach DataStore `tutorial.*`.
  Dashboard RESET kasuje je razem z resztą DataStore.
- Demo (`TEST`, poziom 0) jest odizolowane: bez tutoriali i bez zapisu.
- Teksty `pl`/`en`/`es` w JSON; walidacja wymaga wszystkich trzech języków.
- Język: port `LanguageSettings` należy do osobnego systemu lokalizacji;
  TutorialSystem tylko go konsumuje.
- Rozszerzalność: nowe mechaniki i eventy pochodzą od ich kanonicznych
  właścicieli; TutorialSystem nie tworzy własnego katalogu dostępności ani
  gameplayowych eventów. `targetId` identyfikuje typ/znaczenie celu, a
  opcjonalny stabilny `targetInstanceId` wskazuje konkretną dynamiczną
  instancję.
- Studio edytuje wyłącznie zarejestrowane mechanic/event/target IDs.
  `targetInstanceId` jest wyłącznie runtime i nie jest wpisywany w JSON.
- Powtórka tutoriala meczu uruchamia się przy najbliższym meczu spełniającym
  wyzwalacz; Dashboard pokazuje informację „uruchomi się w następnym meczu”.
- `TutorialParameters` w `game-campaign` jest usuwany; poziom nie wskazuje
  tutoriala, tutoriale wskazują poziomy przez trigger.
- Sklep jest placeholderem: `shop.intro` w katalogu ma `enabled = false`.
- Podgląd rozmieszczenia Hintu w Studio i ilustracje Hintu są poza v1.
- Nie dodawaj nowego modułu Gradle. Nie importuj Androida w `game-application`.
- Pierwsza wersja nie dodaje assetów graficznych; używa istniejących stylów
  HUD (`HudPanelStyle`, `UiTypography`) i rysowania w kodzie.
- Treść katalogu: w tym zadaniu wolno dostarczyć tylko `match.basics` jako
  szkielet potwierdzający architekturę (patrz „Zakres treści”). Pozostałe
  tutoriale z katalogu w dokumencie wymagają osobnej fazy treści i akceptacji
  użytkownika; nie wymyślaj ich tekstów.

### Zakres treści

Dostarcz katalog z: pełną listą `targets` dla ekranów, które mają je
rejestrować, oraz tutorialem `match.basics` (cel, katapulta, celowanie, trzy
strzały ćwiczeń, wiatr, amunicja, zdrowie, timer). Teksty `pl`/`en` oprzyj na
legacy (`values-pl`, `values-en`), przeredaguj je w zwięzłej formie, przetłumacz
na `es` (legacy nie ma hiszpańskiego) i wypisz
je w podsumowaniu do akceptacji. Pozostałe tutoriale (`dashboard.welcome`,
`campaign.map`, `loadout.basics`, `match.units`, `match.mana`, `match.boss`,
`match.haste`, `match.heal`, `match.sapper`, `match.demolisher`) dodaj do
katalogu dopiero po akceptacji ich treści; `shop.intro` może istnieć jako
`enabled = false`.

### Preflight

Przed edycją sprawdź bieżący stan repozytorium i:

- potwierdź użycie istniejącego `LanguageSettings` i jego Android adaptera;
  TutorialSystem nie dodaje drugiego portu ani ustawienia języka;
- zinwentaryzuj istniejące zmiany `git status`; zachowaj cudze i integruj
  zmiany bez ich nadpisywania;
- sprawdź aktualne symbole, pakiety i zależności, bo ścieżki w tym promptcie
  oraz dokumentacji są wskazówkami, a nie dowodem stanu kodu.

Nie blokuj wdrożenia na lokalizacji: kontrakt `LanguageSettings` i adapter
DataStore należą do istniejącego systemu lokalizacji. Jeśli w aktualnym
checkoutcie brakuje któregokolwiek z nich, nie twórz duplikatu; zgłoś
konkretny blocker wraz z dowodami i kontynuuj niezależne etapy.

### Workflow i ochrona pracy użytkownika

- Przeczytaj istniejący kod i testy przed edycją: `LocalMatchGateway`,
  `AiMatchCoordinator`, `MatchViewModel`, `MatchScreenController`,
  `BattlefieldCameraController`, `AppScreen`/`ScreenHost`,
  `DataStore*Repository`, `DataStorePlayerProgressResetRepository`,
  `CampaignLevelDefinition`, Studio (`studio/server`, `studio/web`).
- Reuse wzorców: ładowanie JSON przez `kotlinx-serialization-json` jak
  `GameBalanceRepository`/`progression`; namespace DataStore jak `campaign.*`;
  typowane wyniki błędów jak `PlayerProgressResetFailure`.
- Nie modyfikuj legacy, nie commituj i nie pushuj.
- Zachowaj fortress/catapult presentation lock z `AGENTS.md`; Spotlight celów
  pola bitwy używa tych samych hitboxów co renderer.

### Etap 1 — kontrakty i engine (`game-application`)

Pakiet `com.urkaaaz.application.tutorial`:

- niemutowalne kontrakty: `TutorialCatalog`, `TutorialTargetDefinition`,
  `TutorialDefinition`, `TutorialTrigger`, `TutorialStep`,
  `TutorialStepCompletion`, `TutorialRuleSet`, `TutorialSignal`,
  `TutorialContext`, `TutorialUiState`, `TutorialEffect`, `TutorialEvent`,
  jawne typy błędów/odrzuceń (`Rejected` z powodem);
- walidacja katalogu przy konstrukcji z ścieżką pola: duplikaty ID, nieznany
  cel, brak `pl`/`en`/`es`, cykl w `requires`, pusty krok, `PlayerAction.count < 1`;
- `TutorialCatalogRepository` ładujący `tutorial-catalog.json` z zasobów,
  błędy jawne, brak szerokich `catch`;
- `TutorialEngine` jako jedyny właściciel runtime state (aktywny tutorial,
  indeks kroku, licznik akcji, kolejka). Deterministyczny: bez zegara i
  losowości. Wyjście: `TutorialUiState`, `TutorialEffect`, `TutorialEvent`;
  engine nie wywołuje niczego poza portami;
- kontrakt rozszerzalności: `MatchStarted` konsumuje stabilne
  `availableMechanicIds` wyprowadzone z kanonicznego ownera, a
  `MatchEventObserved` może przenosić opcjonalny stabilny
  `targetInstanceId`. Engine przekazuje go w `TutorialUiState`; nowy tutorial
  nie wymaga gałęzi per poziom/mechanika ani tutorialowego eventu gameplayu;
- `targetId` jest stabilnym, znaczeniowym identyfikatorem typu celu
  z rejestru/katalogu;
  `targetInstanceId` identyfikuje konkretną dynamiczną encję i nie jest
  zapisywany w definicji tutoriala;
- porty: `TutorialProgressRepository`, `MatchTutorialPort`,
  istniejący `LanguageSettings` (nie kopiować ani nie implementować ustawienia
  lub adaptera w TutorialSystemie);
- reguły: wybór po sygnale, jednokrotność, `requires`, `priority`, `Next`,
  `Skip`, `PlayerAction`, `MatchEventCount`, ignorowanie sygnałów w Demo,
  `Rejected(NO_ACTIVE_TUTORIAL)`, przerwanie bez statusu przy restarcie lub
  końcu meczu, kolejność efektów przed pierwszym tickiem;
- usuń `TutorialParameters` i pole `tutorial` z `CampaignLevelDefinition` oraz
  powiązane asercje w `CampaignLevelDefinitionTest`; sprawdź wszystkie użycia
  grepem.

### Etap 2 — persystencja i reset

- `DataStoreTutorialProgressRepository` w `android-app/.../persistence/`
  (klucze `tutorial.*`, wersja schematu, rewizja katalogu);
- nieznany `tutorialId` w zapisie ignorowany jawnie z logiem; nowszy schemat
  → `Rejected(UNSUPPORTED_SCHEMA)` bez kasowania danych; błąd zapisu jako
  typowany wynik;
- test, że `DataStorePlayerProgressResetRepository` czyści `tutorial.*` bez
  zmiany adaptera resetu;
- Demo nie czyta ani nie zapisuje.

### Etap 3 — mostek meczowy (wymaga bramki lokalizacji)

- `LocalMatchGateway.suspendAi()`/`resumeAi()` nad
  `AiMatchCoordinator.pause()/resume()`; testy, że wstrzymane AI nie wydaje
  komend i nie przesuwa seedowanych decyzji, oraz że `start()`/`stop()`
  zerujące pauzę wymagają ponownego wydania efektów;
- `MatchViewModel`: zbiór powodów pauzy `USER`/`TUTORIAL`, wznowienie dopiero
  gdy zbiór jest pusty; istniejące `TogglePause` zachowuje zachowanie;
- `BattlefieldCameraController.focusOnWorldRect(...)` (publiczne), prostokąt
  w ekranie = `world * scale + offset` z `CameraTransform`;
- adapter `MatchTutorialAdapter` implementujący `MatchTutorialPort`; mapuje
  typowane eventy ownerów do `TutorialSignal` i przekazuje
  `targetInstanceId`, jeśli event wskazuje konkretną encję;
- dostępność przyszłych mechanik trafia do `availableMechanicIds` z
  kanonicznego ownera; nie dodawaj równoległego tutorialowego katalogu
  dostępności ani kodu per nowy typ mechaniki;
- blokada paneli: panel aktywny, gdy mechanika jest dostępna w
  `MatchScenario` **i** nie jest zablokowana przez tutorial; źródło prawdy o
  dostępności pozostaje `MatchScenario`;
- ograniczenie AI do wprowadzonych mechanik przez
  `setAiAllowedUnitIds`/`aiAllowedAmmunitionIds`.

### Etap 4 — overlay i ekrany Android

- pakiet `com.urkaaaz.tutorial`: `TutorialHost`, `TutorialOverlayView`,
  `TutorialSpotlightView`, `TutorialHintView`, `TutorialTargetRegistry`,
  `TutorialHintPlacer` (czysta funkcja);
- opcjonalna, domyślna metoda `TutorialTargetSource` w `ScreenContent`, aby
  istniejące ekrany się nie zmieniały; rejestracja celów w `onResume`,
  wyrejestrowanie w `onPause`;
- `TutorialTargetRegistry` rozwiązuje `(targetId, targetInstanceId?)` na
  podstawie bieżącego immutable render state i transformacji kamery. Dla
  targetu dynamicznego brak/usunięcie/niewidoczność wskazanej instancji nigdy
  nie może wskazać innej encji tego samego typu; czekaj na target albo zwróć
  jawne `Rejected(TARGET_NOT_VISIBLE)`;
- `MainActivity` tylko tworzy `TutorialHost` (lifecycle); żadnych pól
  tutoriala w `MainActivity`;
- reguły prezentacji: cel w kadrze (`FocusTarget`, w razie braku
  `Rejected(TARGET_NOT_VISIBLE)`), Hint osobny i nigdy nie zasłania celu
  (`Rejected(HINT_PLACEMENT_IMPOSSIBLE)`), otwór Spotlightu równy dokładnie
  prostokątowi celu bez marginesu i feather poza celem, dotyk przepuszczany
  tylko przez cel kroku akcji, `48 dp` minimum, `contentDescription`,
  wskaźnik `n/m`, `Skip` na każdym kroku;
- Dashboard: sekcja **Tutorials** (lista, status, powtórka z informacją
  dla tutoriali meczu) i rejestracja celów; Campaign, Loadout, Match —
  rejestracja celów; Sklep nie rejestruje;
- język zmienia się wyłącznie przez `recreate()` na Dashboardzie, więc engine
  nie obserwuje zmian języka; po `recreate()` aktywny tutorial startuje od
  pierwszego kroku.

### Etap 5 — Urkaaaz Studio

- `studio/server/src/contracts/tutorials.ts`,
  `studio/server/src/services/tutorialCatalog.ts`, endpointy
  `GET /api/tutorials`, `POST /api/tutorials/validate`, `PUT /api/tutorials`
  z `expectedHash` (SHA-256, 409 przy rozbieżności), walidacją i atomowym
  zapisem oraz automatycznym podbiciem rewizji;
- `studio/web/src/tabs/tutorials/`: lista, edycja kroków (cel z listy
  `targets`, teksty `pl`/`en`/`es`, ukończenie, reguły), podgląd tekstu i lista
  problemów walidacji; wzoruj się na zakładkach Balance/Progress;
- wybór targetów, eventów i mechanik tylko z list zarejestrowanych przez
  aplikację. `targetInstanceId` jest runtime-only i nie trafia do edytora ani
  JSON; dodanie nowej mechaniki nie wymaga specjalnej gałęzi UI Studio;
- walidacja TS zgodna z Kotlin; test kontraktowy na tych samych przykładach;
- Studio nie edytuje postępu gracza.

### Etap 6 — testy

Dodaj testy z „Planu testów” w `tutorial-system.md`:

- `game-application`: walidacja katalogu; wybór, priorytety, `requires`,
  jednokrotność, kolejka; przejścia kroków; efekty i ich kolejność; blokada
  paneli; reguły AI; determinizm; brak mutacji (fake'i portów); persystencja;
  **replay regression** (mecz z tutorialem i bez daje identyczny replay
  komend gracza);
- `android-app`: `TutorialHintPlacer`, geometria Spotlightu (także po
  transformacji kamery), `TutorialTargetRegistry`, pokrycie każdego
  `targetId` z katalogu przez swój ekran, rozróżnienie powodu pauzy, reset
  `tutorial.*`, brak pól tutoriala w `MainActivity`, dokładne rozwiązywanie
  dynamicznego `targetInstanceId` bez fallbacku do innej encji;
- rozszerzalność: dodanie identyfikatora mechaniki i typowanego eventu przez
  jego ownera, definicji JSON i zarejestrowanego target providera pozwala
  uruchomić nowy tutorial bez zmiany `TutorialEngine`; testuj także brak
  targetu i usuniętą instancję;
- Studio: `npm test && npm run lint` w `studio/`;
- architektura: brak importów Android w `game-application`, brak zależności
  modułów gameplayowych od tutoriala, brak pola `tutorial` w `game-campaign`.

Testy mają sprawdzać konkretne wartości i wyniki, nie tylko obecność klas.

### Etap 7 — dokumentacja i gotowość

Zaktualizuj:

- `docs/systems/tutorial-system.md`: oddziel plan od faktycznie wdrożonego
  stanu, usuń rozwiązane decyzje, opisz rzeczywisty kontrakt;
- `docs/project/current-state.md`, `docs/project/knowledge.md`,
  `docs/architecture/architecture.md` (sekcja 28 i persystencja),
  `docs/reference/GLOSSARY.md` (status TutS);
- `.github/skills/addSystem/SKILL.md` — bez zmian, chyba że ścieżki się
  rozjadą.

Nie deklaruj ukończenia, jeśli którakolwiek z tych rzeczy nie działa:

- tutorial ekranu i `match.basics` uruchamiają się raz i można je pominąć;
- Spotlight pasuje do celu bez marginesu, a Hint go nie zasłania;
- panele wprowadzane przez tutorial są nieaktywne do jego przejścia lub
  pominięcia;
- AI nie wyprzedza tutoriala, a po nim wraca;
- replay komend jest identyczny z tutorialem i bez;
- Demo nie zapisuje ani nie pokazuje tutoriali;
- Dashboard RESET czyści postęp tutoriala;
- `TutorialParameters` jest usunięty;
- Studio waliduje i zapisuje katalog z `expectedHash`.
- mechanika może otrzymać tutorial przez istniejący kontrakt dostępności/event
  i dane JSON; target dynamiczny wskazuje dokładnie instancję, której dotyczył
  sygnał, bez cichego wskazania innego obiektu.

Jeśli dowolny etap zostanie wstrzymany przez rzeczywisty blocker, opisz go
konkretnie wraz z dowodami, wpływem na zakres i etapami, które udało się
zrealizować; nie deklaruj ukończenia całego systemu.

### Pełna walidacja

Uruchom z katalogu głównego repozytorium:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

oraz w `studio/`:

```bash
npm test && npm run lint
```

Napraw błędy wprowadzane przez tę zmianę i powtórz pełną bramkę. Nie
przypisuj temu zadaniu awarii z niezależnych, wcześniejszych zmian bez
sprawdzenia. Zachowaj cudze modyfikacje i opisz dokładnie każdy blokujący
konflikt.

Nie commituj ani nie pushuj. W podsumowaniu wypisz:

- zrealizowane etapy oraz etapy wstrzymane i powód;
- teksty `match.basics` (pl/en/es) do akceptacji;
- zmienione pliki;
- wynik testów, builda, Studio i `git diff --check`;
- nierozwiązane decyzje lub blokery (w tym lokalizacja);
- potwierdzenie gałęzi oraz tego, że nie utworzono commita ani pushu.

---
