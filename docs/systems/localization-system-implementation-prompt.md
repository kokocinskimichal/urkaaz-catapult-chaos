# Prompt implementacji LocalizationSystem

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

---

## Prompt

Zaimplementuj **LocalizationSystem** (wybór języka PL/EN/ES i lokalizacja UI)
zgodnie z:

- `docs/systems/localization-system.md` — zatwierdzoną architekturą,
  decyzjami, kontraktami, lifecycle, planem testów i kryteriami akceptacji;
- `docs/systems/tutorial-system.md` — wyłącznie w zakresie portu
  `LanguageSettings` konsumowanego przez TutorialSystem (nie implementuj
  tutoriala);
- `AGENTS.md`, `docs/architecture/architecture.md`,
  `docs/architecture/implementation-rules.md`,
  `docs/architecture/project-map.md` i `docs/project/knowledge.md` — aktualną
  architekturą i workflow;
- aktualnym kodem i testami. Dokumenty opisują zamiar, nie dowodzą, że
  kontrakt albo funkcja już istnieje.

Legacy (`~/workspace/orc-catapult`) jest tylko do odczytu i służy jako źródło
polskich tekstów (`values-pl/strings.xml`). Nie kopiuj kodu `LanguageManager`.

Nie kończ na analizie ani planie. Implementuj, testuj i aktualizuj
dokumentację w bieżącej sesji, o ile nie trafisz na rzeczywisty blocker
wymagający decyzji użytkownika.

### Zatwierdzone decyzje — nie pytaj ponownie

- Języki: polski (`pl`), angielski (`en`) i hiszpański (`es`, wariant
  neutralny, jeden katalog `values-es`; bez `es-ES`/`es-419`). Domyślny przy
  braku zapisu: **angielski**; język systemu nie jest brany pod uwagę.
- Język zmienia się **tylko na Dashboardzie**: przycisk pokazujący flagę
  aktualnie wybranego języka w nagłówku otwiera kompaktowy panel HUD
  (`HudPanelStyle`, `UiTypography`) z flagami Polski (`PL`), Wielkiej
  Brytanii (`EN`) i Hiszpanii (`ES`). Aktywna flaga jest podświetlona;
  dotknięcie innej zmienia język od razu. Każda flaga ma cel dotyku co
  najmniej `48 dp` i `contentDescription` z nazwą języka. Panel zamyka się po
  wyborze, dotknięciu poza nim lub systemowym Back.
- Dashboard RESET **nie** czyści języka. Język jest w osobnym pliku DataStore
  (nazwa `language_settings`, klucze `language.code`, `language.schema_version`),
  nie w głównym Preferences DataStore czyszczonym przez
  `DataStorePlayerProgressResetRepository`.
- Zastosowanie języka: własny wrapper `Context` w
  `MainActivity.attachBaseContext` (`Configuration.setLocale`) plus
  `recreate()`. **Nie używaj `AppCompatDelegate`**, nie zmieniaj `MainActivity`
  na `AppCompatActivity` ani motywu `Theme.Urkaaaz`. Nie deklaruj
  `android:localeConfig` ani wpisu w systemowych ustawieniach języka aplikacji.
- Język jest odczytywany raz, synchronicznie, przy starcie
  (`StartupLanguageReader`). To jedyne dopuszczone blokujące wywołanie DataStore
  w aplikacji; opisz je krótkim komentarzem.
- Brak zapisanej wartości = `EN` (`DEFAULT_NO_STORED_VALUE`, reguła kontraktu).
  Nieobsługiwana wartość lub nowszy schemat = jawny `Failure(UNSUPPORTED_VALUE)`
  bez nadpisywania danych. Błąd IO przy odczycie startowym: start w `EN`, jawny
  log, bez nadpisywania zapisu. Błąd IO przy zapisie: komunikat na Dashboardzie,
  bez `recreate()`.
- Po `recreate()` aplikacja wraca na Dashboard: bieżący ekran (zawsze
  Dashboard, bo tylko tam zmienia się język) zapisywany w
  `onSaveInstanceState` i odtwarzany w `onCreate`. Pozostałe ekrany nie są
  odtwarzane.
- Teksty UI: `values/strings.xml` pozostaje angielską bazą; dochodzą
  `values-pl/strings.xml` i `values-es/strings.xml`. **Nie** twórz `values-en`.
- Tytuły rozdziałów: presentation-side. `campaign-chapters.json` trzyma
  tylko stabilne `id`; pole `title` znika z JSON i z kontraktu `game-campaign`
  (`CampaignChapterRegistry`, `CampaignChapterCatalogRepository`, w tym
  walidacja unikalności/niepustości tytułu — zastąp ją walidacją unikalności
  `id`, jeśli jej brakuje). Podnieś `schemaVersion` pliku. Gra bierze tytuł z
  `strings.xml` przez jawne mapowanie `id → R.string` w katalogu prezentacji
  (jak `BossPresentationCatalog`, **bez** `Resources.getIdentifier`),
  konwencja kluczy `chapter_title_<id>`. Studio pokazuje `id`.
- Poziomy nie mają tytułów i nie są lokalizowane.
- Liczby widoczne dla gracza są formatowane według `Locale` aplikacji (po
  polsku i hiszpańsku z przecinkiem). Logi i pliki developerskie zostają przy
  `Locale.US`.
- Liczby mnogie przez `<plurals>` z formami wymaganymi przez polski
  (one/few/many/other) i hiszpański (one/many/other); teksty
  parametryzowane z pozycyjnymi argumentami.
- Lint `MissingTranslation` jest błędem; test parytetu kluczy i argumentów
  `values` ↔ `values-pl` ↔ `values-es`.
- Przegląd literałów tekstowych w kodzie Androida: literały widoczne dla gracza
  trafiają do zasobów; klucze assetów, identyfikatory i logi zostają.
- Polskie tłumaczenia: z legacy tam, gdzie znaczenie pasuje do aktualnego
  klucza; resztę tłumaczy agent. Hiszpańskie tłumaczenia (neutralny `es`)
  w całości tłumaczy agent, bo legacy nie ma hiszpańskiego. **Cała lista idzie
  do akceptacji użytkownika** (klucz, EN, PL, ES, źródło PL `legacy`/`nowe`,
  źródło ES `nowe`); bez niej lokalizacja nie jest gotowa.
- Teksty tutoriali pozostają w JSON tutoriali (`pl`/`en`/`es`) i nie trafiają
  do `strings.xml`; to zakres TutorialSystemu.
- Studio nie dostaje edytorów tłumaczeń. Nie lokalizuj Studio.
- Nie dodawaj nowego modułu Gradle ani assetów graficznych. Nie importuj
  Androida w `game-application`.
- Czcionka `cinzel_variable.ttf` zawiera polskie i hiszpańskie znaki
  (`áéíóúüñÁÉÍÓÚÜÑ¿¡` sprawdzone przez cmap); nie zmieniaj czcionki.

### Bramka zależności i zakresu

Zanim zaczniesz kod, sprawdź `git status`; nie nadpisuj cudzych zmian i nie
modyfikuj legacy. Przeczytaj przed edycją: `MainActivity`, `ScreenHost`,
`AppNavigator`, `DashboardScreen`/`DashboardViewModel`/`DashboardUiAction`/
`DashboardUiState`, `LocalPreferencesDataStore`, `DataStorePlayerProgress
ResetRepository`, `CampaignScreen`, `BossPresentationCatalog`,
`CampaignChapterRegistry`, `CampaignChapterCatalogRepository`,
`campaign-chapters.json`, `studio/server/src/services/campaignMapCatalog.ts`,
`studio/web/src/tabs/maps/MapsTab.tsx`, `studio/web/src/tabs/campaign/CampaignTab.tsx`
oraz istniejące testy tych obszarów.

Etapy 1–2 odblokowują TutorialSystem i **muszą** być ukończone jako pierwsze,
nawet jeśli dalsze etapy zostaną wstrzymane.

### Etap 1 — kontrakty i port (`game-application`)

Pakiet `com.urkaaaz.application.language`:

- `AppLanguage` (`PL`, `EN`, `ES`, tagi BCP-47 `pl`/`en`/`es`, `DEFAULT = EN`);
- port `LanguageSettings` (`suspend load()`, `suspend save(language)`);
- `LanguageLoadResult` (`Loaded(language, source)` z `STORED` /
  `DEFAULT_NO_STORED_VALUE`; `Failure(STORAGE_UNAVAILABLE | UNSUPPORTED_VALUE)`),
  `LanguageSaveResult` (`Saved`, `Unchanged`, `Failure`);
- use case `SetLanguage`; ten sam język → `Unchanged`, bez zapisu;
- niemutowalne kontrakty, jawne błędy, bez szerokich `catch`;
- testy JVM z fake'iem portu.

### Etap 2 — persystencja i start (`android-app`)

- `DataStoreLanguageSettings` w `persistence/`, osobny plik DataStore, klucze
  `language.*`, wersja schematu, mapowanie `IOException` na typowany wynik;
- `StartupLanguageReader` i `LocalizedContext` w
  `com.urkaaaz.localization`; `MainActivity.attachBaseContext` opakowuje
  `Context`;
- `MainActivity` nie dostaje innej logiki języka;
- testy: round-trip, brak klucza, nieobsługiwana wartość, nowszy schemat, błąd
  IO, `LocalizedContext.getString` dla `PL` i `EN`, język przetrwa Dashboard
  RESET.

### Etap 3 — selektor flag na Dashboardzie

- `DashboardUiAction.SelectLanguage`, stan UI z aktywnym językiem, wywołanie
  `SetLanguage` w `DashboardViewModel`;
- przycisk z flagą aktualnego języka w nagłówku otwiera osobny, kompaktowy
  panel z trzema flagowymi przyciskami: Polska (`PL`), Wielka Brytania (`EN`)
  i Hiszpania (`ES`); aktywna flaga jest podświetlona, a każdy cel dotyku ma
  co najmniej `48 dp` i poprawny `contentDescription`;
- panel nie zasłania głównych przycisków i zamyka się po wyborze, dotknięciu
  poza nim lub systemowym Back;
- dodaj małe drawable dla trzech flag, jeśli nie istnieją; przycisk języka
  i panel wyboru używają tych samych ikon;
- `Saved` → `recreate()` z odtworzeniem Dashboardu; `Unchanged` i `Failure` →
  bez `recreate()`, błąd jawnie widoczny;
- testy `DashboardViewModel` dla trzech wyników i odtworzenia Dashboardu oraz
  mapowania flag na języki, stanu aktywnej flagi i zamykania panelu.

### Etap 4 — tytuły rozdziałów

- usuń `title` z `game-campaign` (kontrakt, registry, repository, JSON,
  testy) i podnieś `schemaVersion`;
- katalog prezentacji rozdziałów w `android-app` z jawnym mapowaniem
  `id → R.string`; `CampaignScreen` używa go zamiast `chapter.title`;
- Studio: usuń `title` z kontraktów i walidacji, `MapsTab` i `CampaignTab`
  pokazują `id`; zaktualizuj testy;
- test: każdy `id` z katalogu rozdziałów ma wpis i tłumaczenia w obu językach;
  brak odwołań do `chapter.title`.

### Etap 5 — lokalizacja UI

- przegląd literałów tekstowych w `android-app` (ok. 240 kandydatów; większość
  to klucze assetów); przenieś teksty widoczne dla gracza do zasobów, a
  pozostałe literały uzasadnij w podsumowaniu;
- `values-pl/strings.xml` i `values-es/strings.xml` z listą tłumaczeń (PL:
  legacy + nowe; ES: nowe), `<plurals>` tam,
  gdzie są liczby; pozycyjne argumenty formatowania;
- tekst UI budowany z `Context` aktywności; brak `applicationContext.getString`
  dla tekstu UI;
- liczby widoczne dla gracza według `Locale` aplikacji (np. czas
  przeładowania w `AmmunitionPanelView`);
- lint `MissingTranslation` jako błąd, test parytetu kluczy, argumentów i
  `<plurals>`;
- przegląd wizualny trzech języków: panele o stałym rozmiarze (kafle Loadoutu,
  HUD, przyciski) zawijają lub obcinają tekst bez zmiany wymiarów;
- wypisz pełną listę tłumaczeń PL i ES do akceptacji; **nie deklaruj lokalizacji
  jako gotowej bez niej**.

### Etap 6 — testy

Dodaj testy z „Planu testów” w `localization-system.md`:

- `game-application`: `AppLanguage`, `SetLanguage` (`Saved`/`Unchanged`/
  `Failure`), fake portu (brak zapisu, nieobsługiwana wartość), brak importów
  Androida;
- `android-app`: adapter DataStore, RESET nie czyści języka, `LocalizedContext`,
  parytet zasobów, `DashboardViewModel`, katalog prezentacji rozdziałów, brak
  `chapter.title` i `applicationContext.getString` dla tekstu UI, odtworzenie
  Dashboardu;
- `game-campaign` i Studio: JSON i kontrakty bez `title`, unikalność `id`;
  `npm test && npm run lint` w `studio/`;
- symulacja, replay i snapshoty niezmienione (istniejące testy przechodzą).

Testy mają sprawdzać konkretne wartości i wyniki, nie tylko obecność klas.

### Etap 7 — dokumentacja i gotowość

Zaktualizuj:

- `docs/systems/localization-system.md`: oddziel plan od faktycznie
  wdrożonego stanu, usuń rozwiązane decyzje, opisz rzeczywisty kontrakt;
- `docs/systems/tutorial-system.md`: status portu `LanguageSettings`;
- `docs/project/current-state.md`, `docs/project/knowledge.md`,
  `docs/architecture/architecture.md` (persystencja), `docs/reference/GLOSSARY.md`;
- `AGENTS.md` i `docs/agent-context-index.md`: wpis do indeksu dokumentów
  lokalizacji i jej promptu.

Nie deklaruj ukończenia, jeśli którakolwiek z tych rzeczy nie działa:

- pierwsze uruchomienie pokazuje angielski UI;
- `PL` albo `ES` na Dashboardzie przełącza cały UI na wybrany język, zostaje na
  Dashboardzie i zachowuje wybór po restarcie;
- Dashboard RESET nie zmienia języka;
- tytuły rozdziałów w Kampanii są w wybranym języku;
- `values/`, `values-pl/` i `values-es/` mają ten sam zestaw kluczy;
- listy tłumaczeń PL i ES zostały zaakceptowane przez użytkownika;
- `TutorialContext` może pobrać `AppLanguage` przez `LanguageSettings`.

Jeśli lista tłumaczeń nie została jeszcze zaakceptowana, napisz to wprost i nie
deklaruj ukończenia etapu 5.

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

- zrealizowane etapy oraz ewentualne wstrzymane i powód;
- pełną listę tłumaczeń PL i ES do akceptacji;
- literały pozostawione w kodzie wraz z uzasadnieniem;
- zmienione pliki;
- wynik testów, builda, lintu, Studio i `git diff --check`;
- nierozwiązane decyzje lub blokery;
- potwierdzenie gałęzi oraz tego, że nie utworzono commita ani pushu.

---
