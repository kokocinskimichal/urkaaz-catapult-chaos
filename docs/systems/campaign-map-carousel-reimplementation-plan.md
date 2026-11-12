# Plan przeimplementowania ekranu kampanii na karuzelę map

## Cel

Zastąpić bieżący ekran kampanii z dekoracyjną ramką i paskiem miniatur jednym
pełnoekranowym widokiem mapy. Nawigacja między rozdziałami ma przewijać całe
mapy, a nie wybierać małe karty. Zachować istniejące zasady progresji, wybór
poziomu, nawigację do Loadoutu i statusy markerów.

To plan zmian w istniejącym `CampaignScreen`, nie nowy ekran, moduł,
kontrakt mapy gameplayowej ani system progresji.

Powiązany prompt wykonawczy: [`campaign-map-carousel-implementation-prompt.md`](campaign-map-carousel-implementation-prompt.md).

**Status: implemented.** The current CampaignScreen uses the full-map
carousel, arrow/swipe navigation, and Green Frontier presentation fallback
described below. Historical asset cleanup and remaining authored-map work are
recorded in the implementation prompt and campaign presentation documentation.

## Uzgodniony kierunek UX

- Mapa wypełnia ekran; usunąć dekoracyjną zewnętrzną ramkę z CampaignScreen.
- Pokazywać jedną mapę rozdziału naraz, bez kart i miniaturek pozostałych map.
- Rozdziały przewijać poziomym gestem lub lewą/prawą strzałką.
- Zachować obecną politykę widoczności rozdziałów: wszystkie ukończone,
  bieżący rozdział i najwyżej jeden następny rozdział jako podgląd. Karuzela
  nie pokazuje ani nie odblokowuje dalszych rozdziałów.
- Wskaźnik rozdziału pokazuje jego pozycję w całej kampanii, np. `2 / 10`,
  niezależnie od liczby stron aktualnie dostępnych w karuzeli.
- Zachować kompaktowy HUD: Back, tytuł kampanii i rozdziału, postęp rozdziału,
  wskaźnik pozycji i `START LEVEL`.
- Zachować markery poziomów na istniejących kotwicach oraz stany ukończony,
  dostępny i zablokowany. `START LEVEL` pozostaje dostępny tylko dla wybranego
  poziomu autoryzowanego przez `CampaignSystem`.
- Użyć nowo zaimportowanych
  `campaign_map_nav_arrow_left.png` i `campaign_map_nav_arrow_right.png`.
- Rozdziały 1–4 zachowują własne mapy. Dla rozdziałów 5–10 ekran tymczasowo
  używa tła Green Frontier, aż zostaną przygotowane ich mapy.
- Styl HUD-u pozostaje lekki: małe, ciemne, półprzezroczyste powierzchnie,
  jasny tekst i oszczędne brązowe akcenty. Bez pergaminowej ramy, chorągwi,
  kompasu ani miniatur rozdziałów.

## Granice i zachowane zachowanie

- `CampaignSystem` nadal jest jedynym autorytetem wyboru poziomu. Karuzela,
  strzałki i gesty zmieniają wyłącznie prezentowany rozdział.
- `CampaignProgress`, jego DataStore, nagrody, odblokowania i format zapisu
  pozostają bez zmian.
- Ukończone poziomy nie stają się ponownie grywalne; poziom podglądu i wszystkie
  zablokowane poziomy pozostają nieklikalne.
- Kliknięcie dostępnego markera wybiera poziom, a `START LEVEL` nadal wywołuje
  `CampaignSystem.selectLevel` przed otwarciem Loadoutu.
- Błędy odczytu progresji i layoutu pozostają jawne. Brak planowanej mapy
  rozdziałów 5–10 jest obsługiwanym fallbackiem prezentacji; nieprawidłowy
  layout lub błąd odczytu nie może być maskowany fallbackiem.
- `CampaignMapLayoutRepository` pozostaje ścisłym repozytorium istniejących
  layoutów. Fallback Green Frontier jest wyłącznie warstwą Android UI; nie
  tworzy `MapDefinition`, nie zmienia geometrii poziomów gameplayowych i nie
  zmienia tego, co Studio uznaje za mapę gotową do edycji.
- Nie zmieniać `AppScreen`, `AppNavigator`, `MainActivity` ani granicy
  Dashboard → Campaign → Loadout.
- Zachować obecne ładowanie progresji, retry po błędzie, accessibility opisów
  markerów i lifecycle `ScreenContent`.

## Plan implementacji

### 1. Rozszerzyć rozwiązywanie map na warstwie prezentacji

Zachować bez zmian mapowanie istniejących layoutów:

- `green_frontier` → Green Frontier;
- `broken_valley` → Broken Valley;
- `ashen_march` → Ashen March.

Gdy `CampaignMapLayoutRepository` zwróci `MapArtworkMissing` dla rozdziałów
4–10, przygotować prezentację z:

- tłem `campaign_chapter_01_green_frontier_map`;
- wymiarami mapy Green Frontier;
- współrzędnymi dziesięciu kotwic z layoutu Green Frontier;
- numerami poziomów przemapowanymi po indeksie lokalnym na zakres wybranego
  rozdziału.

Typ i status każdego markera nadal pochodzą z `CampaignChapterDefinition` i
`CampaignChapterProgressMapper`, a nie z layoutu Green Frontier. W szczególności
lokalne pozycje bossów 5 i 10 pozostają bossami dla każdego rozdziału.

Fallback stosować wyłącznie do jawnego wyniku `MapArtworkMissing` dla
rozdziałów 5–10. `INVALID_CHAPTER`, `INVALID_DATA` i
`STORAGE_UNAVAILABLE` nadal prowadzą do istniejącego stanu błędu. Nie wpisywać
fallbackowych layoutów do zasobów kampanii ani do Studio.

Utrzymać `CampaignMapPresentation` jako read-only model prezentacyjny. Dodać
lub rozszerzyć resolver w `CampaignMapPresentationCatalog` tylko w zakresie
potrzebnym do mapowania tła, wymiarów i kotwic; nie tworzyć drugiego katalogu
poziomów ani kopii logiki progresji.

### 2. Dodać nawigację pełnoekranowej karuzeli

- Wykorzystać `CampaignMapProgressionState.pages`, które już zawiera ukończone
  rozdziały, bieżący rozdział i najwyżej jeden podgląd.
- Strzałka lub gest wybiera sąsiednią stronę z tej listy przez istniejący
  `CampaignViewModel.selectChapter`; nie pozwala przeskoczyć poza jej granice.
- Ukrywać albo wyłączać lewą strzałkę na pierwszej dostępnej stronie i prawą
  na ostatniej.
- Wskaźnik strony i tytuł wyliczać z wybranego rozdziału oraz
  `CampaignChapterRegistry`, nie z indeksu listy stron. Przykładowo podgląd
  Chapter 2 pokazuje `2 / 10`.
- Dodać poziomy swipe nad obszarem mapy. Krótki tap pozostaje wyborem
  dostępnego markera; swipe nie może uruchomić kliknięcia markera.
- Użyć czytelnego progu i dominującego kierunku gestu, bez dodawania pan/zoom
  mapy ani nowego mechanizmu nawigacji.
- Strzałki mają co najmniej 48 dp obszaru dotyku, osobne opisy dostępności
  oraz stan wyłączony/niewidoczny na granicach.
- Dodać lokalizowane etykiety strony i strzałek w EN/PL/ES.

### 3. Przebudować istniejący CampaignScreen

- Zostawić jedną warstwę mapy na pełny ekran, `CampaignMapView`, istniejący
  top bar i przycisk Start Level.
- Usunąć warstwę `campaign_map_frame_overlay` z CampaignScreen.
- Usunąć Chapter Strip z miniaturek; nie zastępować go kolejnym panelem
  wyboru rozdziałów.
- Umieścić mały, półprzezroczysty panel nagłówka przy górnej krawędzi:
  `BACK`, `CAMPAIGN`, numer i nazwa rozdziału oraz dotychczasowy postęp.
- Dodać `2 / 10` i strzałki do HUD-u, zachowując obszary markerów wolne od
  paneli.
- Umieścić `START LEVEL` w dolnej, środkowej części ekranu. Pokazywać go tylko
  wtedy, gdy bieżąca strona ma dostępny poziom; zachować istniejący stan
  włączenia dla wybranego poziomu.
- Przy zmianie strony aktualizować tytuł, progress, markery i mapę jako jedną
  spójną prezentację. Nie pozostawiać markera ani selekcji z poprzedniej mapy.
- Zachować istniejące tło ekranu jako bezpieczne tło ładowania/błędu, jeśli
  jest nadal używane przez te stany; nie zastępować nim mapy strony.

### 4. Usunąć kod i zasoby poprzedniego selektora

Z `CampaignScreen.kt` usunąć po przeniesieniu wszystkich potrzebnych zachowań:

- `chapterTabs`, `chapterTabsScroll`, `chapterSelectorBackplate` i
  `chapterSelectorPanel`;
- `createChapterStrip()` i `renderChapterTabs()`;
- stałe `CHAPTER_SELECTOR_*` i `UNSELECTED_CHAPTER_THUMBNAIL_ALPHA`;
- importy `HorizontalScrollView` i elementy używane wyłącznie przez
  miniatury/listę rozdziałów;
- `renderChapterLevelList()`: wszystkie strony mają pełnoekranową mapę,
  a znany brak artworku jest obsługiwany przez fallback;
- `chapterStatus()`, jeśli po usunięciu kart nie ma już konsumenta.

Z `CampaignMapPresentationCatalog` usunąć metody przeznaczone wyłącznie dla
starego Chapter Strip:

- `chapterSelectorBackgroundDrawable()`;
- `chapterTabThumbnailDrawable()`;
- `chapterTabFrameDrawable()`.

Zaktualizować `CampaignMapPresentationCatalogTest`: usunąć testy miniatur,
pergaminu i ramek kart; dodać testy rozwiązywania pełnoekranowych map i
fallbacku rozdziałów 5–10.

Po sprawdzeniu wszystkich lokalizacji usunąć nieużywane stringi z EN/PL/ES:

- `campaign_chapter_tab_current`, `campaign_chapter_tab_completed`,
  `campaign_chapter_tab_preview`, `campaign_chapter_tab_accessibility`;
- `campaign_chapter_status_*`;
- `campaign_map_art_pending` oraz teksty poziomów używane wyłącznie przez
  `renderChapterLevelList()`.

Pozostawić stringi nagłówka, postępu, Back, Start Level, błędów, retry i
accessibility, które nadal mają konsumentów.

#### Assety do usunięcia po audycie referencji

Usunąć z Android runtime resources stare grafiki Chapter Strip:

- `campaign_chapter_selector_parchment.png`;
- `campaign_chapter_01_thumbnail.png`,
  `campaign_chapter_02_thumbnail.png`,
  `campaign_chapter_03_thumbnail.png`;
- `campaign_chapter_thumbnail_frame_current.png` i
  `campaign_chapter_thumbnail_frame_other.png`.

Usunąć również ich odpowiadające wyeksportowane pliki z `assets_src`, jeśli
nie są już potrzebne jako źródła do ponownego generowania. Zachować oryginalne
arkusze sprite jako archiwalne źródła regeneracji, zgodnie z obecnym sposobem
przechowywania assetów.

Po globalnym wyszukaniu referencji usunąć nieużywane Android runtime kopie
`campaign_chapter_01_icon.png` i `campaign_chapter_02_icon.png`, jeśli nie
mają innego konsumenta. Oryginalne emblematy i arkusze w
`assets_src/campaign/chapter-icons/` pozostawić jako archiwum, chyba że osobna
decyzja obejmie czyszczenie archiwalnych źródeł.

#### Assety, których nie wolno usuwać w tej zmianie

- `campaign_map_frame_overlay.png` należy usunąć tylko z kompozycji
  CampaignScreen. Zasób pozostaje używany przez `TestLevelPickerScreen` oraz
  Studio Campaign preview/API.
- Zostawić wszystkie trzy tła map, ich layouty, marker fortecy i bossa,
  dostępny glow, tabliczki i ikony statusów.
- Zostawić nowo zaimportowane `campaign_map_nav_arrow_left.png` i
  `campaign_map_nav_arrow_right.png`, podłączyć je do nowej karuzeli.
- Zostawić przyciski Back i Start Level z Loadout, ich selektory normal/pressed
  oraz font/typografię UI.
- Nie usuwać wspólnego `bg_main_menu`, jeśli nadal pokrywa stan ładowania,
  retry lub błąd.
- Nie usuwać animacji markerów bossów; `CampaignBossMarkerImageView` nadal
  może je wyświetlać.
- Nie usuwać assetów Chapter Strip z `assets_src`, jeśli są zachowywane jako
  zatwierdzone źródła archiwalne; usuwać tylko to, co audyt potwierdzi jako
  niepotrzebne.

### 5. Zaktualizować dokumentację po implementacji

- Zaktualizować ten dokument do rzeczywistego stanu wdrożenia albo przenieść
  go do planów wykonanych.
- Zaktualizować `docs/systems/campaign-map-presentation.md`,
  `docs/project/current-state.md` i `docs/project/knowledge.md`.
- Zaktualizować `AGENTS.md` oraz `docs/agent-context-index.md`, aby aktywne
  instrukcje opisywały pełnoekranową karuzelę i fallback Green Frontier.
- Oznaczyć briefy pergaminu, miniatur i ramek miniatur jako poprzedni,
  nieaktywny design; usunąć je z aktywnych ścieżek do bieżącego UX, ale
  zachować jako historyczne źródła, jeśli nadal są przydatne.
- Zachować studio map editor i jego kontrakty. `mapAvailable` w Studio nadal
  oznacza istnienie autorskiego layoutu mapy, nie tymczasowy fallback Androida.

## Testy i walidacja

### Testy jednostkowe

- `CampaignChapterProgressMapperTest`: nie zmieniać obecnej polityki stron;
  zachować ukończone, bieżący i najwyżej jeden następny rozdział. Upewnić
  się, że podgląd nadal ma wyłącznie zablokowane poziomy.
- `CampaignViewModelTest`: nawigacja do poprzedniej/następnej dostępnej strony,
  granice karuzeli, wybór markerów i zachowanie selekcji poziomu.
- `CampaignMapPresentationCatalogTest`: właściwe tła rozdziałów 1–4,
  fallback tła i kotwic Green Frontier dla rozdziałów 5–10, remapowanie
  lokalnych kotwic na globalne numery poziomów oraz jawne błędy dla
  nieprawidłowego layoutu.
- `CampaignMapViewportTest`: markery pozostają wyrównane z kotwicami po
  CenterCrop dla oryginalnych i fallbackowych map.
- Testy accessibility/locale: nazwy Back, strzałek, strony i stanów mapy
  istnieją dla PL/EN/ES.

### Testy ekranu i regresji

- Strona Campaign pokazuje jedną mapę bez zewnętrznej ramki i bez miniaturek.
- Gest poziomy i strzałki zmieniają wyłącznie widoczną stronę; tap dostępnego
  markera wybiera poziom, a tap locked/completed niczego nie wybiera.
- Indeks strony, tytuł, postęp i markery należą do tej samej strony po każdej
  zmianie rozdziału.
- `START LEVEL` otwiera Loadout tylko dla autoryzowanego, dostępnego poziomu;
  zablokowany podgląd nie może rozpocząć meczu.
- Błąd progresji lub layoutu pozostaje jawny; tylko `MapArtworkMissing` dla
  rozdziałów 5–10 używa mapy Green Frontier.
- Test Mode nadal pokazuje własny ekran i ramkę.
- Studio Campaign preview/API nadal odczytuje ramkę i assety map.

Przed uznaniem implementacji za gotową uruchomić:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Wykonać też globalne wyszukanie usuwanych nazw drawable, stringów i metod,
aby nie pozostawić odwołań do starych assetów.

## Kryteria akceptacji

1. CampaignScreen pokazuje jedną pełnoekranową mapę bez ozdobnej zewnętrznej
   ramki i bez paska miniatur.
2. Rozdziały przewija się gestem lub strzałkami, w granicach stron
   dozwolonych przez istniejącą progresję.
3. Rozdziały 5–10 używają mapy Green Frontier wyłącznie jako tymczasowego
   tła prezentacyjnego; ich tytuły, numeracja i statusy progresji pozostają
   właściwe dla danego rozdziału.
4. Kotwice markerów są zgodne z użytym obrazem; typ boss/forteca pochodzi
   z definicji rozdziału.
5. Nie zmienia się autoryzacja `CampaignSystem`, trwały progress ani przepływ
   Start Level → Loadout.
6. Usunięte są nieużywane elementy Chapter Strip, jego stringi i runtime
   assety, ale zachowane są zasoby współdzielone przez Test Mode i Studio.
7. Testy, build i `git diff --check` przechodzą.
