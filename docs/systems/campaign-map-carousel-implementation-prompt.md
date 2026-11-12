# Prompt implementacji ekranu kampanii jako karuzeli map

> Status: implemented. Zachowano jako zapis zakresu, kryteriów i walidacji
> wdrożenia.

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

## Prompt

Przeimplementuj **istniejący Android CampaignScreen** jako pełnoekranową
karuzelę map kampanii. Wykonaj kod, testy, cleanup nieużywanego kodu i
assetów oraz aktualizację powiązanej dokumentacji. Nie kończ na samym planie.

Szczegółowy plan jest zapisany w
[`campaign-map-carousel-reimplementation-plan.md`](campaign-map-carousel-reimplementation-plan.md).
Przed edycją sprawdź bieżący kod, testy, użycie zasobów i working tree.
Dokumentacja opisuje intencję, ale kod repozytorium pozostaje źródłem prawdy
dla bieżących zachowań.

### Potwierdzone decyzje — nie pytaj ponownie

- Ekran pokazuje jedną mapę rozdziału na cały ekran. Usuń z CampaignScreen
  zewnętrzną dekoracyjną ramkę i pasek miniaturek rozdziałów.
- Nawigacja między mapami działa przez lewą/prawą strzałkę i poziomy swipe.
  Użyj już zaimportowanych assetów:
  `assets_src/campaign/ui/campaign_map_nav_arrow_left.png`,
  `assets_src/campaign/ui/campaign_map_nav_arrow_right.png` oraz ich kopii
  w `android-app/src/main/res/drawable-nodpi/`.
- Zachowaj obecną politykę dostępnych stron: wszystkie ukończone rozdziały,
  bieżący rozdział i najwyżej jeden następny rozdział jako zablokowany
  podgląd. Nie ujawniaj ani nie odblokowuj dalszych rozdziałów przez
  karuzelę.
- Wskaźnik pokazuje numer rozdziału w całej kampanii, np. `2 / 10`, a nie
  indeks bieżącej listy widocznych stron.
- Rozdziały 1–4 używają własnych map. Dla rozdziałów 5–10 ekran tymczasowo
  używa tła i kotwic Green Frontier do czasu przygotowania dedykowanych map.
- Fallback dla rozdziałów 5–10 jest wyłącznie warstwą Android presentation.
  Nie twórz fałszywych layoutów kampanii, `MapDefinition` ani map
  gameplayowych i nie zmieniaj Studio.
- Zachowaj kompaktowy HUD: Back, `CAMPAIGN`, numer/nazwę rozdziału, postęp,
  wskaźnik rozdziału i `START LEVEL`. Używaj lekkiego, ciemnego,
  półprzezroczystego tła z oszczędnymi brązowymi akcentami.
- Zachowaj markery, kotwice i statusy poziomów. Poziomy 5 i 10 rozdziału
  pozostają markerami bossów zgodnie z `CampaignChapterDefinition`.
- `CampaignSystem` pozostaje autorytetem wyboru poziomu. Karuzela nie może
  zmienić progressu, unlocków, nagród ani przejścia Campaign → Loadout.
- Nie commituj, nie pushuj, nie przełączaj gałęzi i nie odrzucaj cudzych
  zmian.

### Kontekst i pliki do sprawdzenia

Przeczytaj:

- `AGENTS.md`;
- `docs/architecture/architecture.md`,
  `docs/architecture/implementation-rules.md` i
  `docs/architecture/project-map.md`;
- `docs/systems/campaign-map-carousel-reimplementation-plan.md`,
  `docs/systems/campaign-map-presentation.md` i
  `docs/systems/campaign-system.md`;
- `game-campaign/src/main/resources/campaign-chapters.json` oraz
  `game-campaign/src/main/resources/campaign-map-layouts/`;
- `CampaignScreen`, `CampaignViewModel`, `CampaignUiState`,
  `CampaignUiAction`, `CampaignChapterProgressMapper`, `CampaignMapView`,
  `CampaignMapViewport` i `CampaignMapPresentationCatalog` wraz z ich testami;
- `CampaignSystem`, `CampaignMapLayoutRepository`,
  `CampaignChapterRegistry` oraz ich testy;
- `TestLevelPickerScreen` i Studio Campaign preview/API przed usuwaniem
  jakichkolwiek wspólnych assetów.

Sprawdź `git status --short` i zachowaj istniejące zmiany użytkownika.
Nie przywracaj, nie nadpisuj i nie usuwaj zmian spoza zadania.

### 1. Rozwiązywanie map i tymczasowy fallback

- Zachowaj aktualne mapowanie rozdziałów 1–4:
  Green Frontier, Broken Valley, Ironwood i Ashen March.
- Dla rozdziałów 5–10 obsłuż wyłącznie
  `CampaignMapLayoutRead.MapArtworkMissing` jako warunek użycia fallbacku
  Green Frontier.
- Utwórz prezentację z background drawable Green Frontier, jego wymiarami
  i dziesięcioma kotwicami Green Frontier.
- Przypisz kotwice po indeksie lokalnym do globalnego zakresu poziomów
  bieżącego rozdziału. Typ landmarku, dostępność, ukończenie i boss
  presentation key pochodzą z chapter/progression contracts; nie kopiuj ich
  z rozdziału 1.
- Zachowaj boss landmarki w lokalnych pozycjach 5 i 10 zgodnie z definicją
  rozdziału.
- Nie zmieniaj semantyki `CampaignMapLayoutRepository`. Nie przechwytuj
  `INVALID_CHAPTER`, `INVALID_DATA`, `STORAGE_UNAVAILABLE` ani innych błędów
  jako fallbacku; pokaż istniejący jawny stan błędu.
- Nie zapisuj fallbackowych layoutów do JSON resources, Android assets ani
  Studio.
- Użyj istniejącego `CampaignMapPresentation` i resolvera, rozszerzając
  je tylko w wymaganym zakresie. Nie dodawaj drugiego registry ani
  równoległego kontraktu map.

### 2. Karuzela rozdziałów i gesty

- Nawiguj wyłącznie pomiędzy stronami z
  `CampaignMapProgressionState.pages`. Zachowaj ich kolejność.
- Strzałki wybierają sąsiednią stronę, respektują granice i używają
  istniejącego `CampaignViewModel.selectChapter`.
- Ukryj lub wyłącz strzałkę bez sąsiedniej strony.
- Dodaj poziomy swipe nad obszarem mapy bez nowej zależności UI. Ustal
  jawny próg i rozpoznawaj dominujący poziomy kierunek.
- Tap w dostępny marker nadal go wybiera. Tap w ukończony lub zablokowany
  marker niczego nie wybiera. Swipe nie może odpalić kliknięcia markera.
- Po zmianie rozdziału razem aktualizuj mapę, tytuł, wskaźnik, postęp,
  markery i selekcję. Nie pokazuj chwilowo markerów ani wybranego levelu
  należącego do poprzedniej strony.
- Wskaźnik rozdziału wylicz z canonical `CampaignChapterRegistry`, nie
  z indeksu widocznych stron.
- Każda strzałka ma minimum 48 dp obszaru dotyku, opis dostępności i
  prawidłowy stan na granicy karuzeli. Dodaj lokalizowane etykiety strony
  i strzałek w EN/PL/ES.
- Zachowaj obsługę Back i `ScreenContent` lifecycle.

### 3. Przebudowa CampaignScreen

- Przebuduj istniejący `CampaignScreen`; nie twórz nowego ekranu ani trasy.
- Renderuj wybraną mapę jako pełnoekranowe tło przez istniejący
  `CampaignMapView` i współdzieloną transformację `CampaignMapViewport`.
  Markery muszą pozostać dopasowane do mapy po `CENTER_CROP`.
- Usuń z CampaignScreen warstwę `campaign_map_frame_overlay` i Chapter Strip.
  Nie zastępuj ich kartami, nowym selektorem ani ciężką ramą.
- Umieść kompaktowy header w górnym lewym obszarze: `BACK`, `CAMPAIGN`,
  rozdział, nazwę rozdziału i bieżący `X / 10 COMPLETED` z cienkim paskiem.
- Pokaż numer rozdziału `/ 10` i strzałki karuzeli bez zasłaniania markerów.
- Zachowaj `START LEVEL` w dolnej środkowej części ekranu. Pokazuj go tylko,
  gdy strona ma dostępny poziom; włącz go tylko dla aktualnie wybranego
  dostępnego levelu. Nie zmieniaj wywołania `CampaignSystem.selectLevel`
  przed otwarciem Loadoutu.
- Zachowaj istniejące stany ładowania, błędu, retry i selekcji. Błędy
  progresji lub nieprawidłowego layoutu nie mogą wyglądać jak sukces ani
  odblokowywać poziomów.
- Zostaw `bg_main_menu`, jeśli jest nadal potrzebne jako bezpieczne tło
  podczas ładowania/błędu; nie może zasłaniać mapy poprawnej strony.

### 4. Usuń martwy kod, stringi i assety

Po przeniesieniu potrzebnych zachowań usuń elementy Chapter Strip z
`CampaignScreen.kt`:

- pola `chapterTabs`, `chapterTabsScroll`, `chapterSelectorBackplate`,
  `chapterSelectorPanel`;
- `createChapterStrip()`, `renderChapterTabs()` i `chapterStatus()`,
  jeśli nie mają już konsumentów;
- stałe `CHAPTER_SELECTOR_*` oraz
  `UNSELECTED_CHAPTER_THUMBNAIL_ALPHA`;
- importy `HorizontalScrollView` i komponenty używane wyłącznie przez
  miniatury;
- `renderChapterLevelList()`: znany brak mapy rozdziałów 5–10 obsługuje
  fallback prezentacyjny, a błędne dane pozostają jawnym błędem.

Z `CampaignMapPresentationCatalog` usuń metody przeznaczone tylko dla
miniatur/pergaminu:

- `chapterSelectorBackgroundDrawable()`;
- `chapterTabThumbnailDrawable()`;
- `chapterTabFrameDrawable()`.

Usuń testy tych metod i zastąp je testami fallbacku map oraz assetów
karuzeli. Usuń nieużywane stringi z wszystkich locale EN/PL/ES po
repozytoryjnym sprawdzeniu referencji:

- `campaign_chapter_tab_current`,
  `campaign_chapter_tab_completed`,
  `campaign_chapter_tab_preview`,
  `campaign_chapter_tab_accessibility`;
- `campaign_chapter_status_*`;
- `campaign_map_art_pending` oraz stringi używane wyłącznie przez
  `renderChapterLevelList()` (`campaign_level*`, `campaign_boss_level*`).

Pozostaw nagłówek, progres, Back, Start Level, błędy, retry i opisy
dostępności, które nadal są używane.

Usuń nieużywane Android runtime assety starego Chapter Strip po
repozytoryjnym wyszukaniu referencji:

- `campaign_chapter_selector_parchment.png`;
- `campaign_chapter_01_thumbnail.png`,
  `campaign_chapter_02_thumbnail.png`,
  `campaign_chapter_03_thumbnail.png`;
- `campaign_chapter_thumbnail_frame_current.png` i
  `campaign_chapter_thumbnail_frame_other.png`.

Z odpowiadających `assets_src` usuń wyłącznie wyeksportowane grafiki, które
nie będą już potrzebne jako zatwierdzone źródło lub archiwum. Zachowaj
oryginalne arkusze sprite oraz archiwalne chapter icons zgodnie z
repozytoryjną konwencją. Usuń runtime kopie
`campaign_chapter_01_icon.png` / `campaign_chapter_02_icon.png` tylko po
potwierdzeniu, że nie mają innych konsumentów.

**Nie usuwaj `campaign_map_frame_overlay.png` z repozytorium.** Usuń tylko
jego warstwę z CampaignScreen. Android `TestLevelPickerScreen` i Studio
Campaign preview/API nadal korzystają z assetu. Zostaw tła map, layouty,
markery, statusy, boss animation assets, przyciski Back/Start oraz nowe
strzałki.

### 5. Testy i dokumentacja

Dodaj lub zaktualizuj testy:

- `CampaignViewModelTest`: poprzednia/następna strona, granice karuzeli,
  wybór bieżącej strony i brak zmiany poziomu przy zablokowanych markerach;
- `CampaignMapPresentationCatalogTest`: mapy 1–4, fallback dla 5–10,
  globalne numery kotwic, jawne błędy dla nieprawidłowych layoutów;
- `CampaignChapterProgressMapperTest`: nie zmieniaj polityki stron ani
  stanów ukończone/bieżące/następny podgląd;
- `CampaignMapViewportTest`: markery pozostają wyrównane po CenterCrop na
  mapie źródłowej i fallbackowej;
- testy gestów/ekranu, jeśli istnieją odpowiednie harnessy: swipe vs marker
  tap, strzałki i stan Start Level;
- weryfikację stringów/opisów dostępności EN/PL/ES.

Zaktualizuj bezpośrednio powiązane dokumenty:

- `docs/systems/campaign-map-presentation.md`;
- `docs/project/current-state.md` i `docs/project/knowledge.md`;
- ten plan oraz linki w `AGENTS.md` i `docs/agent-context-index.md`.

Oznacz briefy starego pergaminowego selektora i miniaturek jako nieaktywne
lub historyczne; nie pozostawiaj ich jako aktualnej instrukcji UI.
Nie zmieniaj Studio map editor, `mapAvailable`, level progression,
`AppScreen`, `AppNavigator` ani `MainActivity`.

### Walidacja i ograniczenia zmian

Uruchom z katalogu repozytorium:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Wykonaj globalne wyszukanie usuwanych drawable, metod i stringów przed
usunięciem assetów. Sprawdź `git status --short` przed i po pracy, aby
zachować wszystkie zastane zmiany. Nie commituj ani nie pushuj.

### Kryteria akceptacji

1. CampaignScreen pokazuje jedną pełnoekranową mapę bez zewnętrznej ramki
   i bez miniaturek rozdziałów.
2. Strzałki i swipe przechodzą tylko między stronami aktualnie dozwolonymi
   przez istniejącą progresję; indeks na HUD-zie pozostaje globalny `N / 10`.
3. Rozdziały 5–10 renderują tło/kotwice Green Frontier z własnymi tytułami,
   numerami, markerami i progresją.
4. Statusy oraz wybór leveli pozostają zgodne z persisted
   `CampaignProgress`; locked/completed nie dają się uruchomić.
5. Start Level nadal przechodzi przez `CampaignSystem.selectLevel` i dopiero
   po sukcesie otwiera Loadout.
6. Nieprawidłowe layouty i awarie odczytu pozostają jawne; fallback dotyczy
   tylko braku mapy w rozdziałach 5–10.
7. Usunięty jest martwy kod i runtime Chapter Strip assets; zasoby
   współdzielone przez Test Mode i Studio są zachowane.
8. Testy, debug build i `git diff --check` przechodzą.
9. Zmiany pozostają niezatwierdzone i nie są pushowane.
