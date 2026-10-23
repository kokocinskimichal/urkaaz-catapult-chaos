# Prompt implementacyjny: webowy edytor map Urkaaaz

Skopiuj cały tekst od następnej linii do agenta implementującego. Prompt
realizuje kompletny plan z `docs/map-editor/web-migration-plan.md`.

---

Jesteś autonomicznym agentem implementującym kompletną, działającą lokalną
aplikację webową do tworzenia map dla projektu Urkaaaz! Catapult Chaos.

Wykonaj zadanie od początku do końca: analiza repozytorium, projekt kontraktu,
implementacja, migracja, testy, uruchomienie i dokumentacja. Nie zatrzymuj się
na szkielecie UI, makiecie ani ekranie demonstracyjnym. Nie pytaj o akceptację
po każdym etapie. Podejmuj decyzje zgodne z tym promptem, planem migracji i
istniejącą architekturą. Dokumenty dawnego edytora desktopowego są
nieaktualne; nie odtwarzaj usuniętego interfejsu ani jego adaptera assetów.

Nie commituj ani nie pushuj bez wyraźnego polecenia użytkownika. Webowy
edytor jest jedynym interfejsem authoringu map.

## 1. Dokumentacja i analiza przed zmianami

Pracuj w repozytorium:

```text
/Users/michalkokocinski/workspace/urkaaz
```

Najpierw przeczytaj:

```text
AGENTS.md
docs/architecture/architecture.md
docs/map-editor/web-migration-plan.md
docs/map-editor/design.md
docs/map-editor/risks.md
docs/project/knowledge.md
```

Następnie przeanalizuj:

```text
game-contracts
game-campaign
game-assets
map-editor-domain
map-editor-format
map-editor-rendering
android-app
```

Znajdź i wykorzystaj istniejące:

- `MapDefinition`;
- `TerrainProfile` i `TerrainSample`;
- `FortressPlacement`;
- `CatapultPlacement`;
- `ProtectedTerrainZone`;
- `MapObjectPlacement`;
- `MapDefinitionValidator`;
- `CampaignMapDefinitions`;
- `TerrainTilePlacement` i istniejące dane kafelków wyłącznie dla kompatybilności;
- `MapDocumentCodec`;
- `game-assets` i konwencję nazw assetów;
- testy kontraktów, kampanii, formatu oraz obecnego edytora.

Nie twórz drugiego równoległego modelu mapy, drugiego systemu biomów ani
drugiego ręcznie utrzymywanego walidatora. Zachowaj kompatybilność danych
legacy, ale używaj webowego workflow rysowania profilu.

## 2. Cel końcowy

Zaimplementuj lokalną aplikację uruchamianą w przeglądarce, w której autor
może:

1. utworzyć mapę o świecie `7200 × 4800`;
2. wybrać biom przypisany do mapy;
3. wyświetlić tło, soil, cap, teren, fortece i katapulty;
4. rysować górną krawędź terenu bez używania kafelków;
5. wygładzać i wyrównywać zaznaczone fragmenty profilu;
6. zmieniać wyłącznie wysokość fundamentu;
7. zachować stałą pozycję i szerokość fortec oraz katapult;
8. wydłużyć fundamenty od strony frontowej;
9. dodawać dekoracje po zablokowaniu terenu;
10. przesuwać, obracać i skalować dozwolone obiekty;
11. korzystać z zoomu, panoramowania i dopasowania widoku;
12. używać undo/redo na poziomie całych operacji;
13. importować i eksportować wersjonowany JSON;
14. automatycznie zapisywać wersję roboczą lokalnie;
15. widzieć błędy walidacji przy konkretnych elementach mapy;
16. otwierać istniejący eksport `~/Downloads/ch1l2` bez utraty danych;
17. eksportować dokument akceptowany przez istniejący loader map.

## 3. Twarde decyzje

Musisz zachować:

- JSON jako źródło prawdy dla mapy;
- istniejące pola i semantykę `MapDefinition`;
- świat `7200 × 4800`;
- proporcje i pozycje fortec oraz katapult;
- trwałe identyfikatory obiektów;
- `schemaVersion` i jawne migracje;
- rozdział domeny, formatu, renderowania i UI;
- brak zależności Androida w domenie i renderowaniu;
- wspólne assety z `game-assets`;
- możliwość otwierania starych map z próbkami co 40 jednostek;
- możliwość obsługi map z próbkami co 20 jednostek, jeśli obecny format je
  dopuszcza;
- blokadę terenu po rozpoczęciu dekorowania;
- autosave, który nie nadpisuje finalnego eksportu;
- walidację przed eksportem.

Nie implementuj:

- silnika gry;
- AI;
- fizyki pocisków;
- symulacji meczu;
- logowania użytkowników;
- serwera;
- współdzielenia map;
- zmiany pozycji fortec lub katapult;
- trybu symetrycznego;
- promienia, siły działania ani rozmiaru końcówki pędzla;
- Tauri w pierwszej wersji.

## 4. Stos i struktura aplikacji

Utwórz osobny frontend:

```text
map-editor-web/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── index.html
├── src/
│   ├── app/
│   ├── domain/
│   ├── format/
│   ├── rendering/
│   ├── assets/
│   ├── state/
│   ├── ui/
│   └── main.tsx
└── public/
```

Użyj:

- React;
- TypeScript z włączonym ścisłym typowaniem;
- Vite;
- Canvas 2D do renderowania mapy;
- SVG albo osobnej warstwy Canvas do interakcji z profilem;
- lokalnego importu i eksportu plików;
- test runnera zgodnego z wybranym setupem, preferuj Vitest, jeśli nie koliduje
  z repozytorium.

Nie dodawaj ciężkiego frameworka stanu, jeśli `useReducer` lub mały jawny
store wystarczy. Stan dokumentu musi być niemutowalny z perspektywy UI.

Dodaj skrypty:

```text
npm run dev
npm run build
npm run test
npm run lint
```

Jeśli repozytorium używa innego managera pakietów, zachowaj jego konwencję.
Nie commituj lockfile'a z przypadkowego managera.

## 5. Kontrakt między Kotlinem a frontendem

Najpierw zinwentaryzuj obecny JSON. Nie zmieniaj znaczenia istniejących pól
tylko dlatego, że frontend używa TypeScriptu.

Frontend ma posiadać jawne typy odpowiadające JSON:

- `MapDocumentJson`;
- `WorldJson`;
- `TerrainJson`;
- `SampleJson`;
- `FortressJson`;
- `CatapultJson`;
- `ProtectedZoneJson`;
- `ObjectJson`;
- `MetadataJson`.

Preferowana kolejność udostępnienia typów:

1. wygenerowanie typów TypeScript z jednego JSON Schema;
2. jeśli to niemożliwe bez wprowadzania kruchego toolingu, ręczne typy w
   frontendzie z testem zgodności pól z Kotlinowym codec;
3. żadnego drugiego, niezależnego formatu eksportu.

Walidacja nie może być cichą kopią reguł w komponentach. Wybierz i
udokumentuj jedną z poniższych technik, preferując pierwszą, która jest
realistyczna dla repozytorium:

1. wyodrębnij czystą walidację do modułu Kotlin Multiplatform z targetem JS i
   użyj jej w frontendzie;
2. wygeneruj przenośny kontrakt/reguły z jednego źródła i testuj zgodność
   frontend/backend;
3. jeśli istniejący Kotlinowy walidator pozostaje JVM-only, zapewnij:
   - pełną walidację interaktywnych ograniczeń w domenie webowej;
   - testy zgodności z `MapDefinitionValidator`;
   - eksportowany raport z jednoznacznym oznaczeniem błędów, których nie można
     zweryfikować w przeglądarce;
   - brak udawania, że walidacja webowa jest pełnym odpowiednikiem.

Nie rozwiązuj problemu przez `any`, ciche ignorowanie pól ani przez drugi
walidator bez testów zgodności.

## 6. Model domeny webowego edytora

Zaimplementuj jawny model:

```ts
type EditorPhase =
  | "TERRAIN_EDITING"
  | "TERRAIN_LOCKED"
  | "DECORATION_EDITING"
  | "READY_TO_EXPORT";

type EditorOperation =
  | { type: "DRAW_TERRAIN_PROFILE"; ... }
  | { type: "SMOOTH_TERRAIN_RANGE"; ... }
  | { type: "FLATTEN_TERRAIN_RANGE"; ... }
  | { type: "CHANGE_FOUNDATION_HEIGHT"; ... }
  | { type: "LOCK_TERRAIN" }
  | { type: "PLACE_OBJECT"; ... }
  | { type: "MOVE_OBJECT"; ... }
  | { type: "ROTATE_OBJECT"; ... }
  | { type: "SCALE_OBJECT"; ... }
  | { type: "REMOVE_OBJECT"; ... }
  | { type: "MARK_READY_TO_EXPORT" };
```

Każda operacja:

- ma walidowane wejście;
- zwraca nowy stan albo jawny błąd;
- jest odwracalna przez undo/redo;
- nie mutuje poprzedniego dokumentu;
- respektuje fazę edycji;
- zachowuje trwałe ID.

Cały gest rysowania profilu od `pointerdown` do `pointerup` jest jedną
operacją. Nie zapisuj osobnej historii dla każdego ruchu wskaźnika.

## 7. Rysowanie profilu terenu

Zaimplementuj edycję jako profil `x -> height`, nie jako malowanie 2D:

- użytkownik przeciąga po górnej krawędzi terenu;
- system zapisuje punkty world-space, nie screen-space;
- transformacja zoom/pan nie może wpływać na wynik geometrii;
- profil może być rysowany tylko w granicach świata;
- odcinki fundamentów są nietykalne podczas zwykłego rysowania;
- istniejący profil poza zaznaczonym zakresem pozostaje bez zmian;
- po zakończeniu gestu punkty są interpolowane do próbek `TerrainProfile`;
- próbki są deterministyczne i stabilne po round-trip JSON;
- próbkowanie nie może tworzyć nieciągłości na początku ani końcu świata.

Dodaj podgląd:

- aktualny profil;
- profil proponowany;
- zaznaczony zakres;
- chronione fundamenty;
- strefy błędów.

Dodaj operacje:

### Rysowanie

`DrawTerrainProfile` przyjmuje zakres X i punkty profilu. Waliduje granice,
chronione odcinki i maksymalną różnicę wysokości sąsiednich próbek.

### Wygładzanie

`SmoothTerrainRange` działa tylko na wybranym zakresie. Nie może zmienić
fundamentu ani wyjść poza dozwolony zakres wysokości.

### Wyrównanie

`FlattenTerrainRange` ustawia zakres na wysokość wybraną z profilu lub wskazaną
przez użytkownika. Nie może objąć fundamentu, jeśli nie jest to operacja
fundamentowa.

Nie dodawaj kafelków jako głównego elementu UI. Istniejące `terrainTiles`
możesz zachować przy imporcie i eksporcie dla kompatybilności, ale nowo
rysowany profil nie może wymagać wyboru kafelka.

## 8. Fundamenty

Fundament jest osobnym, chronionym odcinkiem profilu. Użytkownik może zmienić
wyłącznie jego wysokość.

Nie wolno zmieniać przez UI:

- szerokości fundamentu;
- pozycji fortecy;
- pozycji katapulty;
- poziomego położenia strefy względem ustalonego kontraktu.

Operacja `CHANGE_FOUNDATION_HEIGHT` musi atomowo:

1. ustawić wysokość właściwej fortecy;
2. ustawić wysokość katapulty tej samej drużyny;
3. wyrównać próbki terenu pod fundamentem;
4. zaktualizować zakres wysokości `ProtectedTerrainZone`;
5. utrzymać stałe poziome granice z wydłużeniem frontowym;
6. utworzyć lub zaktualizować łagodne przejścia po obu stronach;
7. przejść walidację;
8. zapisać jedną operację undo/redo.

Front oznacza kierunek do środka mapy:

- BLUE: wydłużenie fundamentu w prawo;
- RED: wydłużenie fundamentu w lewo.

Wartość wydłużenia musi być wyprowadzona z istniejącego kontraktu lub jawnie
zdefiniowana jako stała domenowa, a nie wpisana w rendererze.

Istniejące `SetFoundationHeight` można zastąpić lub rozszerzyć, ale nie wolno
pozostawić sytuacji, w której zmienia tylko pole fortecy.

## 9. Renderer i transformacje

Zaimplementuj stabilną transformację:

```text
world -> viewport -> screen
screen -> viewport -> world
```

Wymagania:

- świat zachowuje proporcje;
- zoom działa względem punktu kursora;
- pan nie zmienia danych mapy;
- `Fit to screen` pokazuje cały świat;
- HiDPI nie zmienia geometrii;
- kliknięcia są przeliczane do world-space przed operacją domenową.

Canvas renderuje warstwy w kolejności:

1. tło biomu;
2. midground;
3. soil;
4. profil terenu;
5. cap;
6. strefy chronione;
7. podświetlenia fundamentów;
8. dekoracje i relici;
9. fortece;
10. katapulty;
11. podgląd bieżącej operacji;
12. debug bounds i błędy.

Nie rysuj elementów gameplayowych w stałej skali ekranowej. Rozmiary muszą
wynikać z world-space i wspólnego kontraktu prezentacji.

## 10. UI

Zbuduj użyteczny interfejs, nie makietę:

### Pasek główny

- Nowa mapa;
- Otwórz;
- Zapisz szkic;
- Eksportuj;
- Cofnij;
- Ponów;
- Dopasuj;
- status zapisu i walidacji.

### Panel narzędzi

- Rysuj profil;
- Wygładź;
- Wyrównaj;
- Edytuj wysokość fundamentu;
- Zablokuj teren;
- Waliduj.

Nie pokazuj kontroli promienia, siły, końcówki ani symetrii.

### Panel właściwości

Dla fundamentu pokazuj tylko:

- drużynę;
- bieżącą wysokość;
- podgląd zmiany;
- zatwierdzenie/anulowanie.

Nie pokazuj pól pozycji ani szerokości jako edytowalnych.

Dla obiektu pokazuj:

- asset;
- x;
- y;
- skalę, jeśli dozwolona;
- rotację;
- usunięcie.

### Panel assetów

- filtr biomu;
- kategorie tree/rock/prop/relic/ambient;
- miniatury;
- kliknięcie lub przeciągnięcie na mapę;
- brak assetów z innego biomu.

## 11. Fazy edycji

### `TERRAIN_EDITING`

Dozwolone:

- rysowanie profilu;
- wygładzanie;
- wyrównywanie;
- zmiana wysokości fundamentu;
- undo/redo;
- import szkicu.

Niedozwolone:

- dekorowanie.

### `TERRAIN_LOCKED`

Dozwolone:

- dodawanie i edycja obiektów;
- walidacja;
- zapis szkicu;
- oznaczenie gotowości.

Niedozwolone:

- zmiana profilu terenu;
- zmiana fundamentów.

### `DECORATION_EDITING`

Teren jest całkowicie zablokowany. Każda próba zmiany profilu lub fundamentu
ma zwrócić jawny błąd.

### `READY_TO_EXPORT`

Eksport finalny jest możliwy wyłącznie po pełnej walidacji.

## 12. Import, eksport i autosave

Zachowaj wszystkie pola obecnego formatu JSON, w tym nieużywane przez aktualny
UI pola, jeśli są częścią kontraktu.

Zaimplementuj:

- import przez `<input type="file">`;
- eksport jako pobieranie pliku;
- deterministyczne sortowanie kolekcji;
- zachowanie `schemaVersion`;
- jawny błąd przyszłej wersji;
- migracje starszych wersji;
- autosave w `localStorage` lub IndexedDB;
- osobny klucz autosave dla mapy;
- przywracanie autosave po ponownym otwarciu;
- ostrzeżenie przed utratą niezapisanych zmian;
- brak nadpisywania finalnego eksportu przez autosave.

Dodaj test round-trip, który porównuje znaczenie danych, nie tylko kolejność
tekstu JSON.

## 13. Assety

Wykorzystaj istniejące `game-assets`. Nie twórz ręcznie drugiej kopii PNG-ów.

Przygotuj deterministyczny sposób udostępnienia assetów frontendowi:

- kopiowanie do `map-editor-web/public/assets` podczas builda;
- albo jawny skrypt generujący katalog webowy z `game-assets`;
- asset manifest może zostać dodany, jeśli rozwiązuje problem nazewnictwa,
  ale nie twórz równoległego źródła prawdy.

Każdy asset musi mieć:

- stabilny klucz;
- biom;
- kategorię;
- ścieżkę;
- fallback jawnie raportowany jako błąd lub ostrzeżenie, nigdy po cichu.

## 14. Walidacja

Waliduj:

- wymiary świata;
- próbki na początku i końcu świata;
- krok próbek;
- maksymalną różnicę wysokości;
- fundamenty;
- katapulty względem fundamentów;
- chronione strefy;
- bounds obiektów;
- poprawność assetów;
- trwałe ID;
- fazę edycji;
- konflikt dekoracji z regułami, które nadal obowiązują;
- round-trip danych.

Nie blokuj wyłącznie z powodu korytarzy ostrzału lub widoczności, zgodnie z
ustalonym zakresem. Nie przywracaj automatycznie odrzuconych wcześniej
ograniczeń dekoracji bez jawnej zmiany kontraktu.

Raport walidacji musi:

- wskazywać ścieżkę, np. `terrain.samples[42]`;
- wskazywać błąd w world-space;
- umożliwiać kliknięcie błędu i skupienie widoku;
- rozróżniać błąd blokujący od ostrzeżenia.

## 15. Testy

Dodaj testy jednostkowe i integracyjne dla:

- importu map z próbkami co 20 i 40;
- deterministycznego próbkowania profilu;
- rysowania początku, środka i końca świata;
- rysowania poza granicami;
- próby rysowania przez fundament;
- wygładzania;
- wyrównywania;
- jednej operacji undo/redo dla całego gestu;
- zmiany tylko wysokości fundamentu;
- synchronizacji forteca/katapulta/teren/strefa;
- wydłużenia BLUE w prawo;
- wydłużenia RED w lewo;
- zachowania stałej pozycji i szerokości;
- blokady terenu po fazie dekorowania;
- trwałych ID i nieponownego użycia usuniętego ID;
- round-trip JSON;
- importu `~/Downloads/ch1l2`, jeśli plik jest dostępny;
- eksportu akceptowanego przez Kotlinowy codec/loader;
- renderowania przy zoomie 0.5x, 1x, 2x;
- odwrotnej transformacji screen/world;
- HiDPI;
- braku utraty assetów.

## 16. Dokumentacja i skrypty

Zaktualizuj:

- `docs/map-editor/web-migration-plan.md`, jeśli implementacja ujawni
  konieczne korekty planu;
- `docs/map-editor/design.md`, opisując aktualny webowy workflow;
- `docs/project/knowledge.md`, dodając faktycznie wdrożone decyzje;
- README lub dokument uruchamiania, jeśli repozytorium posiada odpowiednią
  sekcję.

Dodaj instrukcję:

```text
npm install
npm run dev
npm run test
npm run build
```

Jeśli aplikacja wymaga skryptu przygotowującego assety, udokumentuj go i
podłącz do `npm run build`.

## 17. Kolejność realizacji

Wykonaj pracę w tej kolejności:

1. analiza istniejącego kontraktu i zapisanie decyzji technicznych;
2. utworzenie frontendu i konfiguracji builda;
3. typy JSON, import, eksport i round-trip;
4. niemutowalny store dokumentu i historia;
5. transformacja world/screen;
6. renderer mapy i assety;
7. rysowanie profilu;
8. wygładzanie i wyrównywanie;
9. fundamenty;
10. fazy i blokady;
11. dekoracje;
12. walidacja i raport błędów;
13. autosave;
14. testy integracyjne;
15. dokumentacja i instrukcja uruchamiania;
16. pełna walidacja.

Po każdym większym etapie uruchom najwęższy właściwy test. Nie czekaj do końca
z pierwszym uruchomieniem aplikacji.

## 18. Kryteria ukończenia

Zadanie jest ukończone dopiero, gdy:

- aplikacja webowa uruchamia się lokalnie;
- `npm run build` przechodzi;
- `npm run test` przechodzi;
- `npm run lint` przechodzi albo repozytorium nie używa lintingu i jest to
  udokumentowane;
- można utworzyć mapę bez kafelków;
- można narysować, wygładzić i wyrównać teren;
- można zmienić tylko wysokość fundamentu;
- fundamenty są dłuższe od strony frontowej;
- fortece i katapulty zachowują pozycję;
- można zablokować teren i dodać dekoracje;
- można cofnąć i ponowić każdą operację;
- można zapisać, zamknąć i ponownie otworzyć JSON;
- istniejące mapy pozostają kompatybilne;
- eksport blokuje błędy i pokazuje ich lokalizację;
- nie ma atrap, pustych przycisków, TODO ani cichych fallbacków;
- dokumentacja opisuje rzeczywisty sposób uruchomienia;
- istniejący Android i moduły Kotlinowe nadal przechodzą:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Jeśli frontend ma osobny zestaw komend, uruchom dodatkowo:

```bash
npm test
npm run build
```

Na końcu przedstaw:

1. listę zmienionych i dodanych plików;
2. opis architektury;
3. opis kontraktu JSON i migracji;
4. opis rysowania profilu;
5. opis synchronizacji fundamentów;
6. wynik wszystkich testów i buildów;
7. znane ograniczenia, jeśli którekolwiek pozostały.
