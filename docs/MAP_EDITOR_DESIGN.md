# Projekt aplikacji do tworzenia map

## Cel

Osobna aplikacja desktopowa służąca do ręcznego tworzenia map Urkaaaz.
Autor składa teren z kafelków konkretnego biomu, ustala fundamenty fortec,
zatwierdza geometrię, a następnie rozmieszcza assety tego samego biomu.
Wynikiem jest wersjonowany JSON, który docelowo zastąpi ręczne definicje map
w Kotlinie.

Edytor nie jest drugim silnikiem gry. Ma korzystać ze wspólnych kontraktów,
assetów i walidacji, a logikę gameplayu pozostawić modułom gry.

## Ustalone decyzje

- JSON jest jedynym źródłem prawdy dla definicji map.
- Każda mapa jest na stałe związana z jednym biomem.
- Wszystkie mapy mają obecny, kanoniczny wymiar świata.
- Edytor jest osobną aplikacją desktopową.
- Teren jest składany z kafelków, ale każdy kafelek ma również dane geometrii.
- Obowiązuje jedna kanoniczna siatka i stała skala kafelków.
- Kafelki mają jawne profile krawędzi i można łączyć tylko kompatybilne
  sąsiedztwa.
- Najpierw ustala się i wygładza teren, potem zatwierdza geometrię, a dopiero
  później dodaje dekoracje.
- Po rozpoczęciu fazy dekorowania teren jest zablokowany.
- Obszary krytyczne są chronione przed przypadkową edycją.
- Eksport JSON jest blokowany przy błędach walidacji.
- Wersje JSON są migrowane przez jawne migratory.
- Wersja robocza jest zapisywana automatycznie, a eksport finalny jest osobną
  operacją.
- Obiekty mają trwałe, unikalne identyfikatory.
- Na początku assety są odnajdywane przez konwencję nazw plików. Manifest
  assetów zostanie dodany później.
- Nazwy assetów są traktowane jako stabilne.
- Edytor nie waliduje korytarzy ostrzału ani widoczności między fortecami.

## Proponowany stos technologiczny

- Kotlin/JVM;
- Compose Multiplatform Desktop;
- Compose `Canvas` i Skia do renderowania mapy;
- `kotlinx.serialization` do JSON;
- `StateFlow` oraz niemutowalny stan edytora;
- komendy domenowe do undo/redo;
- `java.nio.file` do plików i autosave;
- JUnit/Kotlin test zgodny z istniejącym repozytorium;
- Compose Desktop native distributions do tworzenia `.dmg`, `.msi` i `.deb`.

Compose Desktop pozwala współdzielić język, Gradle i modele z projektem gry,
zapewnia wygodny drag-and-drop, zoom, panele właściwości i lokalny filesystem.
Web/Tauri pozostaje możliwą alternatywą, ale na tym etapie dodałby drugi
ekosystem i osobny frontend.

## Moduły

```text
game-contracts
    Współdzielone typy MapDefinition, terrainu, fortec i obiektów.

game-assets
    Jedno źródło prawdy dla PNG-ów oraz katalog assetów.

game-campaign
    Walidator map i reguły kampanii.

map-editor-domain
    Stan edytora, fazy edycji, komendy, undo/redo i reguły przejść.

map-editor-format
    JSON, schemaVersion, migracje, import i eksport.

map-editor-rendering
    Renderowanie kafelków, terenu, dekoracji, siatki i debug overlay.

map-editor-assets
    Adapter między wspólnym game-assets a widokiem desktopowym.

map-editor-desktop
    Compose Desktop, okno aplikacji, menu, panele i obsługa myszy.
```

`map-editor-assets` nie przechowuje własnej kopii PNG-ów. Wszystkie aplikacje
czytają assety z `game-assets`. Jeśli Android wymaga plików w
`res/drawable-nodpi`, Gradle może wygenerować lub skopiować je podczas buildu,
ale ręcznie utrzymywane źródło pozostaje jedno.

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

## Fazy edycji

### 1. Wybór biomu i nowej mapy

Autor wybiera biom przed utworzeniem mapy. Biom jest zapisywany w JSON-ie i
nie może zostać zmieniony po dodaniu dekoracji.

Edytor tworzy mapę o kanonicznym wymiarze świata, z domyślnymi fortecami,
fundamentami i pustym układem kafelków.

### 2. Edycja terenu

Autor może:

- układać kafelki terenu;
- podnosić i obniżać dozwolone fragmenty;
- zastępować kafelki innym profilem;
- wygładzać przejścia;
- ustawiać wysokość fundamentów przez dedykowane narzędzie.

Obszary fundamentów, podejść, kotwic i chronionych stref są oznaczone
wizualnie i zablokowane przed zwykłym rysowaniem.

Wygładzanie działa na geometrii kafelków i profilu wysokości, nie na
pikselach bitmapy. Kafelki z niekompatybilnymi profilami krawędzi nie mogą
zostać połączone.

### 3. Zatwierdzenie terenu

Przed zatwierdzeniem edytor pokazuje raport:

- poprawność wymiarów świata;
- poprawność fundamentów i podejść;
- zgodność krawędzi kafelków;
- ciągłość profilu wysokości;
- brak niedozwolonych obszarów lub przerw.

Po zatwierdzeniu teren przechodzi do stanu `TERRAIN_LOCKED`.

### 4. Dekorowanie

Odblokowuje się panel assetów wybranego biomu. Autor może przeciągać drzewa,
skały, propsy i relici na mapę oraz ustawiać ich:

- dokładne `x` i `y`;
- skalę, jeśli jest dozwolona;
- rotację;
- wariant assetu.

Pozycja dekoracji jest sprawdzana względem profilu terenu. Dekoracje
środowiskowe mogą być umieszczane tylko na płaskich fragmentach zgodnie ze
wspólnym walidatorem gry.

Po dodaniu dekoracji teren nie może zostać odblokowany. Powrót do edycji
terenu wymaga utworzenia nowej wersji mapy albo usunięcia wszystkich dekoracji.

### 5. Eksport

Eksport finalny jest możliwy tylko wtedy, gdy walidator nie zgłasza błędów.
Edytor generuje:

- finalny plik JSON;
- raport walidacji;
- opcjonalnie podgląd mapy.

Autosave wersji roboczej nie może nadpisywać ostatniego zatwierdzonego eksportu.

## Model domenowy edytora

```kotlin
enum class EditorPhase {
    TERRAIN_EDITING,
    TERRAIN_LOCKED,
    DECORATION_EDITING,
    READY_TO_EXPORT,
}

data class EditorDocument(
    val schemaVersion: Int,
    val map: MapDefinition,
    val phase: EditorPhase,
    val revision: Long,
)
```

UI nie modyfikuje `MapDefinition` bezpośrednio. Każda zmiana jest komendą:

```kotlin
sealed interface EditorCommand {
    data class PlaceTerrainTile(...) : EditorCommand
    data class SmoothTerrain(...) : EditorCommand
    data class SetFoundationHeight(...) : EditorCommand
    data class LockTerrain : EditorCommand
    data class PlaceMapObject(...) : EditorCommand
    data class MoveMapObject(...) : EditorCommand
    data class RemoveMapObject(...) : EditorCommand
}
```

Komendy zwracają nowy niemutowalny stan. Historia komend umożliwia undo/redo
co najmniej w fazie edycji terenu.

## Geometria kafelków

Każdy kafelek powinien mieć dane niezależne od bitmapy:

```kotlin
data class TerrainTileGeometry(
    val tileId: String,
    val widthWorldUnits: Float,
    val heightWorldUnits: Float,
    val leftProfile: EdgeProfile,
    val rightProfile: EdgeProfile,
    val heightSamples: List<Float>,
)

enum class EdgeProfile {
    FLAT,
    SLOPE_UP,
    SLOPE_DOWN,
    RIDGE,
    VALLEY,
}
```

Pierwszy prototyp może używać stałego profilu wysokości zdefiniowanego dla
każdego kafelka. Później można dodać ręczne punkty kontrolne, ale nadal muszą
one zostać znormalizowane do kanonicznej siatki świata.

## Interfejs użytkownika

```text
+-------------------------------------------------------------------+
| Menu: New | Open | Save draft | Validate | Export                 |
+----------------------+--------------------------------------------+
|                      |                                            |
|  Narzędzia           |                                            |
|  - kafelki           |                 MAPA                       |
|  - wysokość          |       zoom / pan / siatka / debug         |
|  - wygładzanie       |                                            |
|  - forteca           |                                            |
|                      |                                            |
|----------------------+--------------------------------------------|
| Biom / assety        | Panel właściwości zaznaczonego elementu  |
| warianty / podgląd   | x, y, rotacja, skala, ID, profil, błędy  |
+----------------------+--------------------------------------------+
```

Mapa powinna mieć osobne warstwy renderowania:

1. tło biomu;
2. kafelki i geometria terenu;
3. strefy chronione oraz siatka;
4. dekoracje;
5. fortece, katapulty i kotwice;
6. debugowe granice i raport błędów.

## JSON

Minimalny szkic:

```json
{
  "schemaVersion": 1,
  "mapId": "chapter-1-level-1",
  "biome": "green_frontier",
  "world": {
    "width": 2560,
    "height": 4320
  },
  "terrain": {
    "tiles": [],
    "heightSamples": []
  },
  "fortresses": [],
  "objects": [],
  "metadata": {
    "terrainLocked": true
  }
}
```

JSON powinien zachowywać stabilną kolejność tablic i deterministyczne
formatowanie, aby zmiany były czytelne w Git. ID obiektów nie mogą zależeć od
kolejności w tablicy.

## Walidacja

Edytor współdzieli lub wywołuje walidator map z `game-campaign`. Walidowane są:

- `schemaVersion`;
- biom i istniejące asset keys;
- kanoniczny wymiar świata;
- profile krawędzi kafelków;
- ciągłość geometrii;
- granice świata;
- fundamenty i chronione strefy;
- pozycje obiektów;
- płaskość terenu pod dekoracjami;
- overlap oraz dozwolone kolizje;
- unikalność ID.

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

## Plan implementacji

### Etap 1: wspólne assety i format

- wydzielić `game-assets`;
- ustalić jedno źródło PNG-ów;
- dodać katalog assetów oparty na konwencji nazw;
- zdefiniować pierwszy JSON i `schemaVersion`;
- dodać import/eksport oraz test round-trip.

### Etap 2: domena edytora bez UI

- fazy edycji;
- komendy;
- undo/redo;
- blokady obszarów krytycznych;
- przejście `TERRAIN_EDITING` → `TERRAIN_LOCKED`;
- walidacja dekoracji.

### Etap 3: podstawowy interfejs desktopowy

- okno Compose Desktop;
- viewport mapy;
- zoom i pan;
- wybór kafelka;
- układanie kafelków;
- zapis i odczyt wersji roboczej.

### Etap 4: geometria i wygładzanie

- profile krawędzi;
- kontrola kompatybilności sąsiadów;
- generowanie profilu wysokości;
- narzędzie wygładzania;
- podgląd stref chronionych;
- zatwierdzanie terenu.

### Etap 5: dekoracje

- katalog assetów biomu;
- drag-and-drop;
- edycja pozycji, rotacji i wariantu;
- debug bounds;
- blokada zmiany terenu po dodaniu dekoracji.

### Etap 6: integracja z grą

- loader JSON w `game-campaign`;
- zastąpienie definicji Kotlinowych plikami JSON;
- testy zgodności istniejących map;
- build Androida z assetami z `game-assets`.

## Poza pierwszą wersją

- manifest assetów z anchorami i metadanymi;
- automatyczne generowanie dekoracji;
- walidacja torów pocisków;
- proceduralne generowanie terenu;
- współpraca wielu osób;
- synchronizacja z chmurą;
- automatyczne migracje nazw assetów;
- eksport do innych formatów.
