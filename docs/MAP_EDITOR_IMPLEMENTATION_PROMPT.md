# Prompt implementacyjny: pełna aplikacja edytora map

Skopiuj cały tekst poniżej do agenta implementującego aplikację.

---

Jesteś autonomicznym agentem implementującym kompletną, działającą aplikację
desktopową do tworzenia map dla projektu Urkaaaz! Catapult Chaos.

Masz wykonać całość bez zatrzymywania pracy na pytania, prośby o akceptację
ani proponowanie kolejnych etapów. Podejmuj rozsądne decyzje zgodne z
istniejącą architekturą i dokumentacją. Jeśli szczegół nie jest opisany,
wybierz rozwiązanie najprostsze, typowane, testowalne i zgodne z istniejącymi
wzorcami repozytorium. Nie zostawiaj atrap, pustych ekranów, TODO ani
niepodłączonych klas, jeśli dana funkcja należy do zakresu.

## Kontekst i dokumentacja źródłowa

Pracujesz w repozytorium:

```text
/Users/michalkokocinski/workspace/urkaaz
```

Najpierw przeczytaj:

```text
AGENTS.md
architecture.md
docs/MAP_EDITOR_DESIGN.md
docs/MAP_EDITOR_RISKS.md
docs/PROJECT_KNOWLEDGE.md
```

Następnie przeanalizuj istniejące moduły:

```text
game-contracts
game-campaign
game-simulation
android-app
```

W szczególności znajdź i wykorzystaj istniejące:

- `MapDefinition`;
- `MapObjectPlacement`;
- `TerrainProfile`;
- `TerrainSample`;
- definicje fortec, fundamentów i stref chronionych;
- `MapDefinitionValidator`;
- `CampaignMapDefinitions`;
- `BattlefieldAssetCatalog`;
- istniejącą konwencję nazw assetów;
- istniejące testy kontraktów map i walidatora.

Nie twórz drugiego równoległego kontraktu mapy, drugiego walidatora ani
drugiego systemu biome/terrain. Jeśli istniejący model wymaga rozszerzenia,
zaprojektuj kompatybilną zmianę i zaktualizuj wszystkie użycia.

## Cel końcowy

Zaimplementuj osobną aplikację desktopową, w której użytkownik może:

1. utworzyć mapę o kanonicznym, obecnym wymiarze świata gry;
2. wybrać jeden biom, który zostaje przypisany do mapy;
3. składać teren z kafelków tego biomu;
4. edytować geometrię i wysokości terenu;
5. wygładzać teren przed zatwierdzeniem;
6. ustawiać wysokości fundamentów fortec dedykowanymi narzędziami;
7. zatwierdzić geometrię terenu;
8. po zatwierdzeniu przeciągać assety biomu na mapę;
9. ustalać dokładne położenie, wysokość, rotację i dozwoloną skalę obiektów;
10. walidować mapę;
11. zapisywać autosave wersji roboczej;
12. importować i eksportować wersjonowany JSON;
13. blokować eksport mapy z błędami;
14. otwierać istniejący JSON i kontynuować jego edycję;
15. korzystać z tych samych źródłowych assetów, których używa gra.

Edytor jest narzędziem authoringu. Nie implementuj w nim drugiego silnika
walki, AI, fizyki pocisków ani pełnej symulacji meczu.

## Twarde decyzje projektowe

Musisz zachować wszystkie poniższe zasady:

- JSON jest jedynym źródłem prawdy dla definicji map;
- Kotlinowe modele kontraktów pozostają typami domenowymi, ale mapy docelowo
  są ładowane z JSON;
- każda mapa jest trwale przypisana do jednego biomu;
- wszystkie mapy używają obecnego, kanonicznego wymiaru świata;
- aplikacja jest osobnym programem desktopowym;
- assety nie mogą być kopiowane do osobnego, ręcznie utrzymywanego katalogu
  edytora;
- PNG-y mają mieć jedno źródło prawdy we wspólnym module `game-assets`;
- Android i edytor mają korzystać z tego samego katalogu assetów;
- pierwsza wersja asset catalogu rozpoznaje pliki przez konwencję:
  `terrain_<biome>_<category>_<variant>.png`;
- manifest assetów nie jest wymagany w pierwszej wersji;
- teren jest składany z kafelków, ale grafika kafelka nie jest geometrią;
- każdy kafelek ma dane geometrii, wysokości i profili krawędzi;
- obowiązuje jedna kanoniczna siatka i stała skala kafelków;
- niekompatybilne profile krawędzi nie mogą być łączone;
- najpierw edytuje się i wygładza teren;
- dopiero po zatwierdzeniu terenu można dodawać dekoracje;
- po rozpoczęciu fazy dekorowania teren jest całkowicie zablokowany;
- powrót do edycji terenu wymaga utworzenia nowej wersji/mapy albo usunięcia
  wszystkich dekoracji;
- fundamenty, podejścia, kotwice i strefy krytyczne są chronione przed
  zwykłym rysowaniem terenu;
- eksport jest blokowany przy błędach walidacji;
- JSON ma `schemaVersion` i jawne migracje;
- autosave nie nadpisuje ostatniego finalnego eksportu;
- każdy obiekt ma trwałe, unikalne ID;
- usunięte ID nie mogą być ponownie użyte w tej samej mapie;
- nazwy assetów są na tym etapie traktowane jako stabilne;
- edytor nie blokuje eksportu z powodu korytarzy ostrzału ani widoczności
  między fortecami;
- walidacja terenowa, obiektowa i dekoracyjna musi wykorzystywać istniejące
  reguły gry, a nie ich kopię w UI.

## Wymagany stos technologiczny

Użyj:

- Kotlin/JVM;
- Compose Multiplatform Desktop;
- Compose `Canvas`/Skia do renderowania mapy;
- `kotlinx.serialization` do JSON;
- `StateFlow` i niemutowalnego stanu;
- komend domenowych dla zmian i undo/redo;
- `java.nio.file` dla importu, eksportu i autosave;
- istniejącego systemu Gradle;
- JUnit/Kotlin test zgodnie z konwencją repozytorium.

Nie wprowadzaj webowego frontendu, Tauri, Electron ani osobnego frameworka UI.
Nie dodawaj zależności bez uzasadnienia. Jeśli Compose Multiplatform nie jest
jeszcze skonfigurowany, skonfiguruj go w sposób minimalny i zgodny z obecną
wersją Kotlin/Gradle.

## Docelowy podział modułów

Utwórz lub dostosuj moduły:

```text
game-assets
map-editor-domain
map-editor-format
map-editor-rendering
map-editor-assets
map-editor-desktop
```

Odpowiedzialności:

### `game-assets`

- jedno źródło PNG-ów;
- katalog `src/main/resources/terrain/...`;
- katalog assetów;
- rozpoznawanie biomu, kategorii i wariantu;
- brak zależności od Androida i Compose.

Przenieś istniejące źródłowe assety dekoracji z Androida do tego modułu.
Nie utrzymuj ręcznie drugiej kopii w `android-app`. Jeśli Android wymaga
`res/drawable-nodpi`, skonfiguruj deterministyczne kopiowanie/generowanie
podczas buildu albo adapter zasobów. Zachowaj działanie istniejącego Androida.

### `map-editor-domain`

- `EditorPhase`;
- `EditorDocument`;
- stan zaznaczenia i viewportu, jeśli nie zależy od UI;
- komendy;
- redukcja komend do nowego stanu;
- undo/redo;
- blokady faz;
- generowanie trwałych ID;
- przejścia faz;
- raporty domenowe i błędy operacji.

### `map-editor-format`

- serializowalny format JSON;
- `schemaVersion`;
- import;
- eksport deterministycznego JSON;
- autosave;
- migracje;
- raport nieobsługiwanej wersji;
- testy round-trip.

### `map-editor-rendering`

- niezależne od Compose renderowanie modelu mapy;
- transformacja world/screen;
- kafelki terenu;
- wysokości i profile krawędzi;
- dekoracje;
- fortece i kotwice;
- siatka;
- strefy chronione;
- debug bounds;
- wizualizacja błędów.

### `map-editor-assets`

- adapter katalogu `game-assets`;
- lista assetów dla wybranego biomu;
- ładowanie bitmap dla desktopu;
- brak przechowywania kopii assetów.

### `map-editor-desktop`

- główne okno Compose Desktop;
- menu plików;
- viewport;
- toolbar;
- panel wyboru biomu;
- panel kafelków;
- panel assetów;
- panel właściwości;
- raport walidacji;
- drag-and-drop;
- skróty klawiaturowe;
- dialogi importu/eksportu.

Zachowaj kierunek zależności:

```text
game-assets       <- map-editor-assets <- map-editor-desktop
game-contracts    <- map-editor-domain
game-campaign     <- map-editor-domain / map-editor-format
map-editor-domain <- map-editor-rendering <- map-editor-desktop
map-editor-format <- map-editor-desktop
```

Logika domenowa nie może importować Compose ani Androida.

## Model formatu JSON

Zaprojektuj stabilny, czytelny i deterministycznie format. Minimalnie:

```json
{
  "schemaVersion": 1,
  "mapId": "chapter-1-level-1",
  "biome": "green_frontier",
  "world": {
    "width": 0,
    "height": 0,
    "tileSize": 0
  },
  "terrain": {
    "tiles": [],
    "heightSamples": []
  },
  "fortresses": [],
  "protectedZones": [],
  "objects": [],
  "metadata": {
    "phase": "DECORATION_EDITING",
    "terrainLocked": true
  }
}
```

Dopasuj pola do istniejących kontraktów zamiast tworzyć niezgodne duplikaty.
Jeżeli obecny `MapDefinition` nie ma wystarczających pól dla kafelków,
dodaj rozszerzenie kontraktu albo osobny serializowalny komponent, który jest
jawnie mapowany do `MapDefinition`. Nie ukrywaj danych w niejawnych stringach.

Format musi zapisywać:

- biom;
- kanoniczne wymiary świata;
- kafelki i ich położenie na siatce;
- geometrię kafelków;
- profil wysokości;
- fundamenty i fortece;
- chronione strefy;
- obiekty mapy;
- asset keys;
- `x`, `y`, skalę i rotację;
- trwałe ID;
- fazę dokumentu;
- wersję schematu.

Tablice i pola zapisuj w stabilnej kolejności, aby JSON był wygodny do diffów
Git. Nie zapisuj obiektów według przypadkowej kolejności hash mapy.

## Geometria terenu

Zaimplementuj typy podobne do:

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

Używaj dokładnych typów i waliduj:

- stałą szerokość i wysokość kafelków;
- zgodność z kanoniczną siatką;
- zgodne profile lewego/prawego sąsiada;
- ciągłość wysokości;
- brak przerw;
- granice świata;
- brak modyfikacji stref chronionych;
- zgodność wysokości fundamentów.

Wygładzanie ma działać na profilu geometrii i punktach wysokości. Nie
przetwarzaj bitmapy jako źródła geometrii. Operacja wygładzania musi być
odwracalna przez undo przed zatwierdzeniem terenu.

## Fazy i reguły przejść

Zaimplementuj:

```kotlin
enum class EditorPhase {
    TERRAIN_EDITING,
    TERRAIN_LOCKED,
    DECORATION_EDITING,
    READY_TO_EXPORT,
}
```

Reguły:

- nowa mapa zaczyna w `TERRAIN_EDITING`;
- układanie, wymiana i wygładzanie kafelków działa tylko w tej fazie;
- `LockTerrain` działa wyłącznie, gdy teren przechodzi walidację;
- po zablokowaniu teren nie może być zmieniany;
- dodanie pierwszej dekoracji przełącza dokument do `DECORATION_EDITING`;
- usuwanie/przenoszenie dekoracji działa tylko po zatwierdzeniu terenu;
- eksport może przejść do `READY_TO_EXPORT` tylko po pełnej walidacji;
- żadna komenda UI nie może ominąć reguł fazy.

## Komendy i undo/redo

Zaimplementuj jawne komendy, między innymi:

```kotlin
sealed interface EditorCommand {
    data class PlaceTerrainTile(...) : EditorCommand
    data class ReplaceTerrainTile(...) : EditorCommand
    data class SmoothTerrain(...) : EditorCommand
    data class SetFoundationHeight(...) : EditorCommand
    data object LockTerrain : EditorCommand
    data class PlaceMapObject(...) : EditorCommand
    data class MoveMapObject(...) : EditorCommand
    data class RotateMapObject(...) : EditorCommand
    data class ScaleMapObject(...) : EditorCommand
    data class RemoveMapObject(...) : EditorCommand
}
```

Komendy mają:

- jawnie sprawdzać fazę;
- zwracać typowany sukces albo błąd;
- nie mutować istniejącego stanu;
- zachowywać trwałe ID;
- odrzucać operacje na obszarach chronionych;
- nadawać nowe ID bez ponownego używania usuniętych ID.

Historia undo/redo powinna działać dla operacji terenu i obiektów, ale nie
może umożliwić cofnięcia blokady terenu w sposób naruszający decyzję:
po wejściu w fazę dekorowania teren pozostaje zablokowany. Jeśli użytkownik
cofa operację dodania dekoracji, może usunąć dekorację, ale nie może uzyskać
niejawnego odblokowania terenu.

## Assety

Wydziel wspólny moduł `game-assets` i przenieś do niego istniejące assety.
Zachowaj pełne nazwy biomów zgodne z obecną konwencją, w tym:

```text
terrain_green_frontier_tree_00.png
terrain_green_frontier_rock_00.png
terrain_green_frontier_prop_00.png
terrain_frost_tree_00.png
terrain_copperwood_tree_00.png
terrain_ashen_march_tree_00.png
```

Wykrywanie nazw powinno obsługiwać:

```text
terrain_<biome>_<category>_<variant>.png
```

Kategorie minimum:

- `tree`;
- `rock`;
- `prop`;
- `relic`;
- `cap`;
- `tile`.

Brak assetu nie może prowadzić do cichego sukcesu ani błędnej bitmapy.
Pokazuj jawny błąd katalogu i zaznacz brakujący asset.

## Interfejs

Zaimplementuj funkcjonalny interfejs:

```text
+-------------------------------------------------------------------+
| New | Open | Save draft | Validate | Export | Undo | Redo          |
+----------------------+--------------------------------------------+
| Narzędzia terenu    |                                            |
| Kafelki             |                                            |
| Wysokość            |                 MAPA                       |
| Wygładzanie         |          zoom / pan / siatka               |
| Fundament           |                                            |
|---------------------+--------------------------------------------|
| Biom i assety       | Panel właściwości zaznaczonego elementu   |
| warianty            | x, y, wysokość, rotacja, skala, ID, błędy  |
+----------------------+--------------------------------------------+
```

Wymagania UX:

- zoom kółkiem myszy;
- pan środkowym/przytrzymanym przyciskiem;
- siatka opcjonalna;
- snap do kanonicznej siatki;
- zaznaczanie obiektu;
- przeciąganie assetów;
- bezpośrednia edycja właściwości;
- skróty `Ctrl/Cmd+Z`, `Ctrl/Cmd+Shift+Z`;
- skrót zapisu szkicu;
- czytelny stan fazy;
- czerwone błędy i żółte ostrzeżenia;
- podgląd stref chronionych;
- debugowe granice assetów;
- blokowanie kontrolek niedostępnych w danej fazie.

Renderowanie mapy ma stosować warstwy:

1. tło biomu;
2. kafelki terenu;
3. profil/siatka;
4. chronione strefy;
5. dekoracje;
6. fortece, fundamenty i kotwice;
7. debug bounds;
8. zaznaczenie i błędy.

## Walidacja

Wykorzystaj istniejący `MapDefinitionValidator` lub wydziel wspólny,
bezandroidowy fragment, jeśli obecny validator jest zbyt mocno związany z
kampanią. Nie duplikuj reguł w Compose.

Waliduj:

- `schemaVersion`;
- istniejący biom;
- kanoniczny wymiar świata;
- zgodność kafelków;
- profile krawędzi;
- ciągłość wysokości;
- fundamenty;
- chronione strefy;
- bounds;
- unikalność ID;
- istniejące asset keys;
- pozycję `x`/`y`;
- zgodność dekoracji z terenem;
- płaskość terenu przy dekoracji;
- overlap i dozwolone kolizje;
- poprawność fazy dokumentu.

Nie waliduj w tym edytorze:

- jakości korytarza ostrzału;
- widoczności między fortecami;
- strategii AI;
- wyniku symulacji meczu.

Każdy błąd ma zawierać:

- kod;
- ścieżkę do pola/obiektu;
- poziom `ERROR` albo `WARNING`;
- czytelny opis;
- współrzędne, jeśli dotyczą mapy.

Eksport blokuj dla każdego `ERROR`. Ostrzeżenia mogą być eksportowane, ale
muszą być pokazane w raporcie.

## Import, autosave i migracje

Zaimplementuj:

- `Open JSON`;
- `Save draft`;
- `Export final JSON`;
- autosave po każdej większej operacji;
- odtwarzanie ostatniego szkicu po awarii;
- zachowanie ostatniego finalnego eksportu;
- migracje `schemaVersion`;
- odrzucenie nieobsługiwanej przyszłej wersji;
- testy każdej migracji.

Autosave zapisuj do osobnego pliku, np.:

```text
<map>.draft.json
```

Nie nadpisuj finalnego eksportu przez autosave.

Eksport powinien być deterministyczny i atomowy: zapisz plik tymczasowy,
zwaliduj wynik, a dopiero potem zamień go na docelowy.

## Integracja z Androidem i istniejącą grą

Nie psuj istniejącego Androida. Skonfiguruj wspólne assety tak, aby:

- źródłowe PNG-i znajdowały się tylko w `game-assets`;
- Android korzystał z wygenerowanego/adaptowanego outputu;
- desktop korzystał bezpośrednio z resources `game-assets`;
- `BattlefieldAssetCatalog` korzystał z tego samego katalogu i konwencji;
- istniejące renderowanie mapowych dekoracji nadal działało;
- istniejąca kotwica katapulty i kolejność renderowania nie zostały naruszone.

Jeśli pełne zastąpienie Kotlinowych definicji kampanii przez JSON wymaga
większej migracji, zbuduj loader i adapter teraz, zachowując kompatybilność
istniejących testów. Nie utrzymuj jednak dwóch ręcznie edytowanych wersji tej
samej mapy.

## Testy obowiązkowe

Dodaj testy jednostkowe i integracyjne dla:

- wykrywania assetów po nazwie;
- filtrowania assetów po biomie;
- round-trip JSON;
- deterministycznego formatowania JSON;
- migracji każdej wersji;
- odrzucenia nieznanej przyszłej wersji;
- trwałych i niepowtarzanych ID;
- wszystkich przejść faz;
- blokady terenu po rozpoczęciu dekorowania;
- blokady modyfikacji stref krytycznych;
- zgodności profili krawędzi;
- wykrywania przerwanego profilu wysokości;
- wygładzania terenu;
- undo/redo;
- blokady dekoracji na zboczu;
- bounds i overlap;
- blokady eksportu przy błędach;
- autosave i odtwarzania szkicu;
- uruchamiania podstawowego okna Compose Desktop.

Nie usuwaj istniejących testów. Aktualizuj je tylko wtedy, gdy zmienia się
kontrakt, i zachowaj testy regresyjne dla Androida oraz symulacji.

## Sposób pracy bez zatrzymywania

Wykonuj pracę w tej kolejności, ale nie zatrzymuj się między etapami:

1. audyt repozytorium i aktualnego Gradle;
2. wydzielenie `game-assets`;
3. kontrakt formatu i serializacja;
4. domena edytora;
5. geometria kafelków;
6. walidacja;
7. Compose Desktop;
8. renderowanie i interakcje;
9. assety i drag-and-drop;
10. autosave/import/eksport;
11. integracja Androida;
12. testy;
13. dokumentacja;
14. pełna walidacja.

Po każdym logicznym kroku wykonuj lokalne testy. Jeśli test nie przechodzi,
napraw przyczynę i kontynuuj. Nie raportuj problemu jako blokady, jeśli można
go rozwiązać w ramach zadania.

Nie pytaj użytkownika o wybór biblioteki, nazewnictwo klasy ani kolejny krok.
Nie kończ pracy po utworzeniu samych modeli. Aplikacja ma się uruchamiać,
renderować mapę i obsługiwać podstawowy pełny przepływ:

```text
New map
→ choose biome
→ place terrain tiles
→ edit/smooth geometry
→ lock terrain
→ place decoration
→ validate
→ save draft
→ export final JSON
```

## Dokumentacja

Zaktualizuj:

- `docs/MAP_EDITOR_DESIGN.md`, jeśli implementacja wymaga doprecyzowania;
- `docs/MAP_EDITOR_RISKS.md`, jeśli decyzja techniczna rozwiązuje lub zmienia
  opisane ryzyko;
- `docs/PROJECT_KNOWLEDGE.md` o trwałe fakty dotyczące modułu assetów,
  formatu JSON, loadera i edytora;
- README lub dokumentację uruchomienia, jeśli powstaje nowa komenda Gradle.

Dodaj instrukcję:

- jak uruchomić edytor;
- jak uruchomić testy;
- gdzie znajdują się assety;
- gdzie zapisywany jest autosave;
- jak wyeksportować mapę;
- jak gra ładuje JSON.

## Walidacja końcowa

Uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Jeśli pojawi się osobny task dla desktopu, uruchom także:

```bash
./gradlew <desktop-test-task> <desktop-runner-or-build-task> --quiet
```

Nie twórz commitów ani nie wypychaj zmian, chyba że użytkownik wyraźnie
zażąda commit/push. Na końcu podaj krótko:

- co zostało zaimplementowane;
- jakie moduły i pliki powstały;
- jak uruchomić edytor;
- wynik testów i buildu;
- jawne ograniczenia, których nie dało się usunąć bez zmiany decyzji
  projektowych.

Nie pozostawiaj niezweryfikowanych twierdzeń o sukcesie. Jeśli jakiś element
jest niekompletny z powodu rzeczywistego ograniczenia środowiska, wskaż
konkretny plik, komendę i przyczynę.

---
