# Plan migracji map kampanii do JSON

## Status

Migracja została zaimplementowana. Znormalizowany
`game-campaign/src/main/resources/maps/demo-map.json` pochodzi z
`~/Downloads/web-map.json`; oryginalny plik pozostaje nietknięty. Android
dołącza tę samą mapę offline w
`android-app/src/main/assets/maps/demo-map.json`. Dedykowane JSON-y mają
obecnie Chapter 1, Levels 3 i 4; dla pozostałych poziomów brakujący plik
oznacza przejściowy fallback do `demo-map`. Studio Maps zapisuje kampanijną
mapę przez wybór Chapter i globalnego Level; ID `chapterN-levelX` jest
zapisywane atomowo w obu lokalizacjach. Runtime wybiera mapę poziomu o takim
ID, jeśli plik istnieje. Konfiguracja gameplayowa kampanii pozostaje w Kotlinie.
Stare Kotlinowe
geometrie kampanii zostały usunięte.

Jeden kontrakt `MapDefinition`, ścisły `MapDefinitionRepository` i
`MapDefinitionValidator` obsługują runtime. Eksport webowy i Kotlinowy codec
edytora zapisują uzgodniony schemat JSON. Katapulty są wyprowadzane z fortec,
a rozmiar symulacji pochodzi ze stałych `WorldDimensions`.

## Cel

Zastąpić Kotlinowe definicje geometrii map kampanii plikami JSON
generowanymi w edytorze map. Gra ma ładować mapę z tego samego kontraktu,
którego używa edytor, i walidować ją jednym autorytatywnym walidatorem.
Nie powstanie drugi model geometrii mapy w definicji kampanii.

Docelowo każdy poziom ma własny plik JSON. Przejściowo jedna nowa mapa demo
zostanie jawnie przypisana do wszystkich poziomów, aby sprawdzić integrację
przed stopniowym przygotowaniem pozostałych map.

## Ustalone decyzje

- Nie konwertujemy ani nie eksportujemy automatycznie obecnych map Kotlinowych.
  Pierwszą mapą będzie wskazany eksport webowy; kolejne mapy będą tworzone jako
  JSON-y.
- Pierwsza mapa demo zastąpi tymczasowo mapy wszystkich poziomów. Po jej
  przetestowaniu mapy poziomów będą dodawane stopniowo.
- Docelowo relacja pozostaje 1:1: każdy poziom kampanii ma własny JSON.
- Pliki map są dołączone do aplikacji i dostępne offline.
- Rozmiar świata jest stały: `7200 × 4800`. Wartości pozostają w JSON-ie jako
  opis mapy, ale muszą być równe `WorldDimensions`; nie konfigurują rozmiaru
  meczu.
- Heightmapa (`heightSamples`) jest jedynym źródłem fizycznego kształtu terenu.
- Każdy biom ma jeden kafelek soil. JSON nie przechowuje placementów kafelków;
  renderowanie dobiera soil na podstawie biomu.
- JSON określa fortecy, ale nie katapulty. Pozycja i transformacja katapulty
  wynikają ze stałego kontraktu fortecy w grze.
- Mapa zawiera teren, chronione strefy, położenie fortec i dekoracje.
  Gameplayowe reguły i obiekty pozostają poza mapą.
- Kamera jest opisana przez `initialCameraAnchor` i `initialCameraZoom`;
  ustawienia te wpływają wyłącznie na początkowe kadrowanie.
- Metadane generowania zawierają seed i wersję generatora; nowo authorowane
  poziomy mogą podać także `authoringVersion`.
- Eksport `~/Downloads/web-map.json` jest wejściowym plikiem mapy demo. Nie
  odczytujemy go z katalogu Downloads w runtime; po walidacji trafia do
  bundlowanych zasobów aplikacji.

## Proponowany kontrakt JSON

Poniższy przykład pokazuje strukturę, nie kompletną mapę. W prawdziwym pliku
`heightSamples` musi zawierać pełny profil świata, a strefy, fortecy i
dekoracje muszą zawierać zatwierdzone placementy.

```json
{
  "schemaVersion": 1,
  "mapId": "campaign-level-001",
  "biome": "green_frontier",
  "initialCameraAnchor": "PLAYER_FORTRESS",
  "initialCameraZoom": 1.0,
  "world": {
    "width": 7200,
    "height": 4800
  },
  "terrain": {
    "sampleStep": 40,
    "heightSamples": []
  },
  "protectedZones": [],
  "fortresses": [],
  "decorations": [],
  "generationMetadata": {
    "seed": 10001,
    "generatorVersion": "map-editor-web/...",
    "authoringVersion": "map-editor-web/0.1.0"
  }
}
```

### Pola mapy

| Pole | Znaczenie i reguły |
|---|---|
| `schemaVersion` | Wersja formatu JSON; nieznana nowsza wersja jest jawnie odrzucana. |
| `mapId` | Stabilny identyfikator mapy; docelowo identyfikuje mapę jednego poziomu kampanii. |
| `biome` | Stabilny identyfikator biomu; jest źródłem wyboru soil i tematu renderowania. |
| `initialCameraAnchor` | `PLAYER_FORTRESS`, `ENEMY_FORTRESS` albo `CENTER`; wskazuje początkowy obszar widoku. |
| `initialCameraZoom` | Początkowe powiększenie kamery w zakresie `1..4`; nie zmienia geometrii. |
| `world.width`, `world.height` | Muszą być dokładnie `7200` i `4800`; nie zmieniają rozmiaru meczu. |
| `terrain.sampleStep` | Odstęp próbek heightmapy, zgodny z obsługiwanymi wartościami edytora i walidatora. |
| `terrain.heightSamples` | Próbki wysokości obejmujące pełną szerokość świata; jedyna geometryczna prawda o powierzchni terenu. |
| `protectedZones` | Chronione zakresy, np. fundamenty; zawierają granice i politykę deformacji. |
| `fortresses` | Pozycje fortec BLUE i RED oraz wymagane dane assetu, fundamentu, hitboxa i strefy ochronnej. |
| `decorations` | Tylko wizualne elementy mapy; nie zawierają reguł gameplayowych ani kolizji. |
| `generationMetadata` | Seed i `generatorVersion`; nowo authorowane mapy zapisują też `authoringVersion`. Nie zmieniają runtime geometrii już zapisanej w próbkach. |

Pojedyncza dekoracja zawiera:

- unikalne `id`;
- `assetKey` wskazujący istniejący asset;
- pozycję świata `x`, `y`;
- `scale` i `rotationDegrees`;
- opcjonalne `relicSize` dla zakopanego Relic.

Dla drzewa, skały lub propsa `x` i `y` określają pozycję wizualną obiektu;
walidator wymaga, by ta pozycja mieściła się w granicach świata, ale nie
wymaga kontaktu z powierzchnią terenu ani płaskiego podłoża. Dla zakopanego
Relic `y` oznacza jego pozycję wewnątrz ziemi, a jego obrys musi mieścić się w
soil. Przypisanie assetu do biomu nie zmienia definicji ani fizyki mapy.

### Dane poza mapą

Pozostają w konfiguracji poziomu lub scenariuszu:

- AI, trudność i parametry przeciwnika;
- dostępne jednostki, amunicja i zaklęcia;
- zasoby początkowe, wiatr i tutorial;
- reguły zwycięstwa oraz postęp kampanii;
- wyjątki od początkowej kamery;
- kontrakt pozycji i transformacji katapulty względem fortecy.

Stan edytora, np. faza edycji lub `terrainLocked`, również nie należy do
runtime'owej definicji mapy.

## Docelowy przepływ danych

```text
plik JSON mapy
    -> loader/codec
    -> MapDefinition
    -> jeden MapDefinitionValidator
    -> CampaignLevelDefinition + MapDefinition
    -> MatchScenario / LocalMatchGateway
    -> symulacja i renderowanie
```

- JSON jest źródłem geometrii i dekoracji mapy.
- `MapDefinition` pozostaje jedynym typowanym kontraktem mapy używanym przez
  grę.
- Codec jest wyłącznie granicą serializacji; nie utrzymuje drugiej domenowej
  definicji mapy.
- Definicja kampanii zawiera reguły poziomu i wskazuje mapę, ale nie kopiuje
  terenu, biomu, fortec ani dekoracji.
- Loader nie może po cichu zastąpić błędnej lub brakującej mapy inną mapą.
  Przejściowe przypisanie wszystkich poziomów do demo jest jawne.
- Android nie wprowadza własnych reguł geometrii ani zależności od Kotlinowego
  authoringu map.

## Etapy implementacji

### 1. Ujednolicić format edytora i gry

- Zatwierdzić powyższy zestaw pól jako jedyny produkcyjny format mapy.
- Dopasować nazwy i semantykę TypeScriptowego eksportu oraz Kotlinowego
  codec/runtime modelu.
- Usunąć z produkcyjnego eksportu placementy `catapults`, `terrainTiles`,
  kotwicę kamery i stan edytora.
- Użyć `decorations` zamiast ogólnej listy gameplayowych `objects`.
- Nie przechowywać kafelków soil ani drugiej heightmapy w `terrainTiles`.
- Zachować kompatybilność nazw assetów z istniejącym katalogiem gry.

**Kryterium wyjścia:** ten sam plik wyeksportowany z edytora da się
bezstratnie wczytać do `MapDefinition`; round-trip nie gubi żadnego pola
kontraktu.

### 2. Zastosować walidację mapy

- Rozszerzyć istniejący `MapDefinitionValidator` o walidację granic świata
  względem `WorldDimensions`.
- Wymagać kompletnego i uporządkowanego profilu terenu, obsługiwanego
  `sampleStep` oraz poprawnego pokrycia szerokości `0..7200`.
- Zachować walidację nachyleń, fundamentów i stref ochronnych.
- Walidować dwie fortecy, ich assety, pozycje i referencje do chronionych stref.
- Walidować dekoracje, ich assety, granice świata i transformacje; Relics muszą
  mieścić się pod powierzchnią zgodnie z rozmiarem.
- Jawnie zgłaszać nieznany biom, brak assetu, nieobsługiwaną wersję schematu
  i uszkodzony JSON.
- Uruchamiać autorytatywną walidację po wczytaniu mapy. Walidacja w edytorze
  może pomagać autorowi, ale nie zastępuje walidacji gry.

**Kryterium wyjścia:** wadliwa mapa nie rozpoczyna meczu i przyczyna błędu
zawiera ścieżkę pola JSON.

### 3. Wczytać bundlowane JSON-y

- Dodawać mapy do zasobów dołączanych do aplikacji offline.
- Dodać repozytorium map z jawnym API pobierającym mapę po identyfikatorze
  poziomu.
- Zwracać poprawny `MapDefinition` albo czytelny błąd; bez sukcesopodobnych
  fallbacków.
- Utrzymać loader i codec niezależne od Android UI oraz bez zależności silnika
  od Androida.
- Sprawdzić w buildzie, że pliki JSON z zasobów rzeczywiście trafiają do
  pakietu aplikacji.

**Kryterium wyjścia:** test JVM i test uruchomionej aplikacji wczytują tę samą
mapę z bundlowanych zasobów.

### 4. Wygenerować i uruchomić mapę demo

- Przyjąć `~/Downloads/web-map.json` jako zawartość mapy demo; nie generować
  zastępczej mapy i nie brać geometrii ze starych map Kotlinowych.
- Dopasować ten eksport do zaakceptowanego formatu:
  zachować jego biom, świat, heightmapę, fortecy i chronione strefy; pominąć
  `catapults` (wyznacza je gra), `world.tileSize`, placementy soil tiles oraz
  stan edytora. Puste `objects` zastąpić `decorations`; metadane ograniczyć do
  seeda i wersji generatora.
- Ustawić przejściowe `mapId` na jawne ID mapy demo.
- Wczytać plik do `MapDefinition` i przejść walidację przed użyciem.
- Jawnie przypisać ten sam plik demo do wszystkich poziomów na czas testów
  integracyjnych.
- Przekazać biome z mapy do prezentacji; nie utrzymywać równoległego biomu
  w `TerrainVariant` ani w `DemoLevelDefinition`.
- Użyć stałych wymiarów meczu z `WorldDimensions`; JSON nie może ich nadpisywać.
- Wyznaczać katapulty przez istniejący kontrakt fortecy.
- Zweryfikować mecz od ekranu kampanii przez loader do symulacji i renderera.

**Kryterium wyjścia:** wszystkie poziomy uruchamiają się na nowej mapie demo,
jej biom i dekoracje są widoczne, a geometrię wykorzystuje symulacja.

### 5. Przełączyć kampanię na JSON

- Usunąć mapę z `CampaignLevelDefinition`; zachować tam tylko konfigurację
  kampanii, która nie należy do mapy.
- Przekazać `MapDefinition` z repozytorium do budowania scenariusza i sesji
  meczu.
- Zmienić `CampaignLevelRegistry`, `DemoLevelDefinitions` i `MatchViewModel`,
  aby nie pobierały map z Kotlinowych authoring files.
- Usunąć `CampaignMapDefinitions` i Kotlinowe definicje geometrii poziomów,
  gdy runtime korzysta już wyłącznie z JSON-a.
- Usunąć nieużywane mapowe pola z konfiguracji poziomu, w szczególności
  powielony biom i rozmiar terenu.
- Przenieść wyjątki kamery poza mapę zgodnie z ustaloną polityką.

**Kryterium wyjścia:** żadna ścieżka runtime nie odczytuje geometrii mapy
z Kotlinowych definicji.

### 6. Dodawać mapy kampanii stopniowo

- Zachować demo jako jawnie wskazaną mapę dla poziomów, które nie mają jeszcze
  własnego JSON-a.
- Dla każdego nowego poziomu wygenerować osobny JSON z `mapId` odpowiadającym
  poziomowi.
- Zaktualizować jawne przypisanie poziomu do pliku i zwalidować go przed
  udostępnieniem.
- Po przygotowaniu wszystkich map usunąć przejściowe przypisania do demo.
- Po finalizacji brak mapy dla poziomu ma być błędem, nie cichym fallbackiem.

**Kryterium wyjścia:** każdy poziom ma własną, poprawną mapę JSON i kampania
nie zależy od przejściowego demo.

## Testy i kryteria odbioru

- Codec: poprawny JSON, błędny JSON, nieobsługiwana wersja schematu i
  bezstratny round-trip.
- Walidator: wymiary świata, pokrycie heightmapy, sample step, nachylenia,
  fundamenty, chronione strefy, fortecy, dekoracje i Relics.
- Repozytorium: poprawne mapy, brak pliku, uszkodzony plik, nieznany biom
  i nieznany asset.
- Kampania: przejściowo wszystkie poziomy używają jawnie demo; po uzupełnieniu
  każdy poziom wskazuje własny plik.
- Scenariusz: świat zawsze ma wymiary `WorldDimensions`; katapulty są
  wyznaczane z fortec; wartości kampanii (AI, amunicja, zaklęcia, wiatr,
  tutorial) pozostają niezmienione.
- UI i rendering: biome pochodzi z mapy; dekoracje są rysowane w world-space;
  kamera nie jest odczytywana z JSON-a mapy.
- Weryfikacja mapy demo na urządzeniu/emulatorze przed usunięciem starych
  definicji.

Walidacja implementacji:

```bash
./gradlew test assembleDebug --quiet
cd map-editor-web && npm test && npm run build && npm run lint
git diff --check
```

## Ryzyka i zabezpieczenia

| Ryzyko | Zabezpieczenie |
|---|---|
| JSON edytora i runtime mają różne nazwy pól | Jeden zaakceptowany schemat i test zgodności eksportu z loaderem. |
| Mapa zawiera drugi opis fizycznego terenu | `heightSamples` pozostają jedyną geometrią; soil wynika z biomu. |
| Katapulta rozjeżdża się z fortecą | Brak pola katapulty w JSON; pozycję wyznacza kontrakt gry. |
| Brak mapy zostaje ukryty przez demo | Demo jest używane tylko przez jawne przypisanie przejściowe; później brak pliku jest błędem. |
| Stare mapy Kotlinowe znikają przed testem nowego runtime | Usunąć je dopiero po przejściu testów mapy demo i integracji meczu. |
| Asset key nie istnieje lub biom nie ma soil | Walidacja katalogu assetów przy ładowaniu, bez domyślnego zastępowania. |

## Poza zakresem

- automatyczna konwersja starych Kotlinowych map do JSON;
- przechowywanie definicji gameplayu w mapie;
- zmiana rozmiaru świata;
- ręczne rozmieszczanie soil tiles;
- pozycje katapult i kamera w JSON-ie mapy;
- nowe hazardy, cele lub jednostki startowe mapowe;
- zdalne pobieranie map, logowanie lub serwer.
