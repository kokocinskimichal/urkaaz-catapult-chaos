# Edytor map Urkaaaz

## Cel

Edytor map Urkaaaz jest zakładką Maps w lokalnej aplikacji Studio, opartej na
React i Vite. Autor edytuje profil terenu, fundamenty i dekoracje w JSON zgodnym
z kontraktem map gry. Edytor desktopowy Swing i osobna aplikacja
`map-editor-web` zostały usunięte.

Edytor nie jest drugim silnikiem gry. Ma korzystać ze wspólnych kontraktów,
assetów i walidacji, a logikę gameplayu pozostawić modułom gry.

## Ustalone decyzje

- JSON jest jedynym źródłem prawdy dla definicji map.
- Każda mapa jest na stałe związana z jednym biomem.
- Wszystkie mapy mają obecny, kanoniczny wymiar świata.
- Edytor działa lokalnie w Studio; backend odczytuje i zapisuje mapy repozytorium.
- Teren jest profilem wysokości próbkowanym w osi X; edycja węzła wygładza
  sąsiednie próbki bez naruszania chronionych fundamentów.
- Teren, fundamenty i dekoracje pozostają edytowalne w dowolnej kolejności;
  `Y` dekoracji jest niezależną współrzędną world-space i można ją zmieniać
  ręcznie, a Relics muszą pozostać w glebie.
- Widoczność capu jest niezależnym stanem podglądu **Show cap**/**Hide cap**;
  nie zapisuje się w definicji mapy.
- **Random Map** generuje deterministyczny szkic z seedem, presety intensywności
  terenu i gęstości dekoracji oraz jawny zapis wybranego wyniku opisuje
  [`random-map-generation-plan.md`](random-map-generation-plan.md).
- Obszary krytyczne są chronione przed przypadkową edycją.
- Zapis JSON jest blokowany przy błędach walidacji.
- Wersje JSON są migrowane przez jawne migratory.
- Wersja robocza jest zapisywana automatycznie w `localStorage`; zapis mapy do
  repozytorium jest osobną operacją.
- Obiekty mają trwałe, unikalne identyfikatory.
- Indeks assetów jest budowany przez Studio API ze źródłowego indeksu terenu
  oraz zatwierdzonych drawable Androida; pliki są serwowane bez kopiowania.
- Nazwy assetów są traktowane jako stabilne.
- Edytor nie waliduje korytarzy ostrzału ani widoczności między fortecami.

## Stos technologiczny

- React i TypeScript;
- Vite;
- Canvas 2D do renderowania mapy;
- Studio API do odczytu i zapisu map oraz lokalny autosave;
- testy Vitest.

## Zgodność projekcji z renderowaniem meczu

Canvas odwzorowuje osie świata niezależnie na rozmiar viewportu, tak jak
renderer meczu: `x / 7200 * viewportWidth` oraz
`y / 4800 * viewportHeight`. Nie należy stosować jednolitego skalowania z
letterboxingiem, bo zmienia ono proporcje terenu względem widoku gry. Fortece
mają rozmiar `672 × 504` jednostek świata, a katapulty szerokość `182.4`
jednostki; ich kotwice i offsety pozostają wspólne z kontraktem prezentacji.
Dekoracje i relikty zachowują rozmiary ekranowe używane przez renderer gry.

## Moduły

```text
game-contracts
    Współdzielone typy MapDefinition, terrainu, fortec i obiektów.

game-assets
    Jedno źródło prawdy dla PNG-ów oraz katalog assetów.

game-campaign
    Walidator map i reguły kampanii.

studio/web/src/tabs/maps/domain
    MapDefinition projection, operacje, losowanie oraz walidacja UI.

studio/web/src/tabs/maps/format
    Jawny codec MapDefinition JSON.

studio/web/src/tabs/maps/rendering + state
    Canvas, transformacje, draft i undo/redo.
```

Edytor wykorzystuje wspólne źródło assetów `game-assets`; Studio udostępnia je
frontendowi przez dynamiczny indeks i API assetów.

## Źródło assetów

Docelowo:

```text
game-assets/
├── src/main/resources/
│   └── terrain/
│       ├── green_frontier/
│       ├── frost/
│       ├── copperwood/
│       └── ashen_march/
└── src/main/kotlin/
    └── AssetCatalog.kt
```

Pierwsza wersja korzysta z nazw zgodnych ze wzorcem:

```text
terrain_<biome>_<category>_<variant>.png
```

Przykłady:

```text
terrain_green_frontier_tree_00.png
terrain_copperwood_rock_03.png
terrain_frost_prop_05.png
```

Katalog assetów odpowiada za:

- wyszukanie plików dla wybranego biomu;
- rozpoznanie kategorii i wariantu;
- filtrowanie assetów niedostępnych dla danego biomu;
- dostarczenie bitmapy do renderera;
- późniejszą obsługę manifestu z anchorem i geometrią.

## Ciągły workflow authoringu

Maps Studio nie ma fazy `TERRAIN_LOCKED` ani przycisku **Lock terrain**.
Autor może w każdej chwili zmieniać profil, fundamenty i dekoracje, a każda
operacja przechodzi przez niemutowalny store i historię undo/redo. Chronione
fundamenty nadal są wyłączone ze zwykłych operacji profilu; ich wysokość
zmienia się wyłącznie dedykowaną, atomową operacją.

Nowe mapy zachowują biom w JSON-ie. **Random Map** zastępuje zwykły profil
terenu, wysokości fundamentów i zwykłe dekoracje w bieżącym szkicu; zachowuje
biom, map ID, ustawienia kamery oraz istniejące Relics. Użytkownik może dalej
ręcznie edytować wynik i dopiero jawnie zapisać go przez istniejący dialog
Chapter/Level. Generator jest authoring-only i nie działa w runtime meczu.

Wygenerowane drzewa, skały i propsy początkowo umieszcza się na powierzchni,
ale później ich `Y` jest bezwzględną współrzędną world-space. Ręczna zmiana
profilu nie przesuwa żadnych obiektów; wysokość dekoracji zmienia się przez
pionowy drag albo liczbowy input `Y`. Relics także można ręcznie przesuwać,
lecz operacja naruszająca ich footprint gleby jest odrzucana i zgłasza błąd.

Przycisk **Show cap**/**Hide cap** steruje wyłącznie widocznością capu w
podglądzie. Cap i presety generatora nie są częścią `MapDefinition`, autosave
mapy ani walidacji. Save zawsze wykonuje walidację pełnego JSON-a przed
zapisem obu kopii.

## Model domenowy edytora

W aktualnym webowym edytorze stan lokalny zawiera `MapDocumentJson`,
`selectedId` i `revision`. Nie zawiera `EditorPhase`, cap visibility ani
ustawień generatora; faza UI nie trafia do MapDefinition/JSON.

UI nie mutuje `MapDefinition` bezpośrednio. Każda zmiana przechodzi przez
typowaną operację w `studio/web/src/tabs/maps/domain/operations.ts`, która
zwraca nowy niemutowalny dokument. Generowanie Random Map jest jedną operacją
z własnym seedem; undo/redo obejmuje cały wynik. Nie ma `EditorPhase` ani
warunku blokującego teren po dodaniu dekoracji.

## Profil terenu

Fizyczną powierzchnię mapy opisuje `terrain.sampleStep` oraz pełne
`terrain.heightSamples` w istniejącym `MapDefinition`; teren nie jest
serializowany jako kafelki. Ręczne węzły profilu i generator Random Map
zmieniają te same próbki świata, a walidator sprawdza krok, granice i delta
wysokości. Cap oraz soil są wyłącznie warstwami renderowania.

## Interfejs użytkownika

```text
+-------------------------------------------------------------------+
| Menu: New | Open | Save draft | Validate | Export                 |
+----------------------+--------------------------------------------+
|                      |                                            |
|  Narzędzia           |                                            |
|  - profil terenu     |                 MAPA                       |
|  - wygładzanie       |       zoom / pan / siatka / debug         |
|  - Random Map        |                                            |
|  - fundamenty/cap    |                                            |
|                      |                                            |
|----------------------+--------------------------------------------|
| Biom / presety       | Panel właściwości zaznaczonego elementu  |
| Assety / podgląd     | x, y, rotacja, skala, ID, profil, błędy  |
+----------------------+--------------------------------------------+
```

Podgląd używa jednej transformacji viewportu dla tła i wszystkich warstw mapy.
Rolka myszy zmienia zoom; przeciągnięcie pustego obszaru przesuwa viewport, a
`Shift` + przeciągnięcie umożliwia panowanie także nad obiektami. Zoom i pan
są stanem widoku edytora, niezależnym od definicji mapy.

Mapa powinna mieć osobne warstwy renderowania:

1. tło biomu i soil;
2. wypełnienie terenu i profil;
3. strefy chronione oraz kontrol points;
4. katapulty i fortece;
5. zwykłe dekoracje;
6. cap, Relics i zaznaczone transform handles;
7. współrzędne/debug overlay.

## Format JSON

Poniższy szkic pokazuje nazwy pól, ale nie jest kompletną, zwalidowaną mapą.
Pełny produkcyjny kontrakt opisuje
[`map-json-migration-plan.md`](../campaign/map-json-migration-plan.md).

```json
{
  "schemaVersion": 1,
  "mapId": "chapter-1-level-1",
  "biome": "green_frontier",
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
    "seed": 12345,
    "generatorVersion": "studio-random-map/1"
  }
}
```

JSON powinien zachowywać stabilną kolejność tablic i deterministyczne
formatowanie, aby zmiany były czytelne w Git. ID obiektów nie mogą zależeć od
kolejności w tablicy. Stan capu, presety i stan UI nie trafiają do
MapDefinition.

## Walidacja

Edytor waliduje szkic w TypeScript; Kotlinowy
`MapDefinitionValidator` pozostaje autorytatywnym walidatorem runtime.
Walidowane są:

- `schemaVersion`;
- biom i istniejące asset keys;
- kanoniczny wymiar świata;
- sample step, pełne pokrycie świata i limit wysokości sąsiednich próbek;
- granice świata;
- fundamenty, ich lokalne przejścia i chronione strefy;
- pozycje/transformacje obiektów i unikalne ID;
- dostępność assetów dla biomu;
- soil footprint Relics.

Zwykłe dekoracje muszą mieścić się w granicach mapy, ale nie wymagają
kontaktowania z terenem ani płaskiego podłoża. Edycja profilu automatycznie
przesuwa je o lokalną deltę powierzchni. Relics pozostają w stałej pozycji;
terenowa operacja naruszająca ich footprint jest odrzucana.

Korytarze ostrzału i widoczność między fortecami pozostają poza zakresem
walidatora edytora.

## Migracje

Każdy format JSON ma jawny `schemaVersion`. Migrator:

1. odczytuje wersję;
2. odrzuca nieznaną przyszłą wersję;
3. wykonuje kolejne migracje do aktualnej wersji;
4. zapisuje wynik jako nowy szkic lub eksport;
5. nie zmienia po cichu oryginalnego pliku.

Migracje muszą mieć testy dla każdej obsługiwanej wersji.

## Aktualny status implementacji

Maps Studio jest jedynym interfejsem authoringu i zapisuje pełny, zwalidowany
JSON przez lokalne API. Edycja terenu i dekoracji jest ciągła; nie ma fazowego
locka terenu. Dekoracje mają niezależne, ręcznie edytowalne `Y`, które nie
zmienia się wraz z profilem terenu; Relics pozostają w glebie zgodnie z
walidacją. **Show cap**/
**Hide cap** steruje wyłącznie warstwą podglądu.

Generator **Random Map** jest operacją authoringową w
`studio/web/src/tabs/maps/domain/randomMap.ts`. Używa seedowanego PRNG i
suwaka Terrain intensity 100–600 world units oraz presetów Decoration
density; generuje próbki, bezpieczne wysokości fundamentów i zwykłe dekoracje.
Losowanie jest jedną pozycją undo/redo, zachowuje biome, map ID i Relics, a
pliki repozytorium zmieniają się dopiero po jawnej akcji Save. Szczegóły
algorytmu, ograniczeń i walidacji opisuje
[`random-map-generation-plan.md`](random-map-generation-plan.md).

## Poza pierwszą wersją

- manifest assetów z anchorami i metadanymi;
- walidacja torów pocisków;
- współpraca wielu osób;
- synchronizacja z chmurą;
- automatyczne migracje nazw assetów;
- eksport do innych formatów.

## Stan implementacji

Edytor znajduje się w `studio/web/src/tabs/maps`, a instrukcje uruchomienia są
w `docs/map-editor/web/README.md`. Zachowuje wersjonowany kontrakt JSON,
autosave w `localStorage`, edycję profilu terenu i dekoracji oraz walidację
TypeScript po stronie frontendu i serwera. `PUT /api/maps/{id}` zapisuje obie
kopie map tylko wtedy, gdy zgadza się hash stanu odczytanego przez klienta.
Nie ma osobnego desktopowego interfejsu ani modułu asset-adaptera Kotlin.
