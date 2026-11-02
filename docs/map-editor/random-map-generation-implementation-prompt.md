# Prompt implementacyjny: Random Map w Urkaaaz Studio

> Status: implementacja została ukończona i zwalidowana lokalnie. Prompt
> pozostaje zapisem zakresu oraz kryteriów; wynik opisuje
> [`random-map-generation-plan.md`](random-map-generation-plan.md).

Zaimplementuj zaakceptowany plan z
[`random-map-generation-plan.md`](random-map-generation-plan.md) w istniejącej
zakładce **Maps** Urkaaaz Studio. To zmiana lokalnego narzędzia authoringu,
nie nowy runtime generator gry. Przeczytaj plan w całości; jego decyzje są
źródłem prawdy dla zakresu tego zadania.

Wykonaj analizę, implementację, testy i dokumentację. Nie commituj ani nie
pushuj. Zachowaj wszystkie istniejące zmiany w worktree, w szczególności nie
nadpisuj niezwiązanych plików.

## 1. Źródła i pliki do sprawdzenia

Przed edycją sprawdź `git status --short`, a następnie przeczytaj:

```text
AGENTS.md
docs/architecture/architecture.md
docs/map-editor/design.md
docs/map-editor/risks.md
docs/map-editor/web/README.md
docs/map-editor/random-map-generation-plan.md
docs/map-editor/web-migration-plan.md
```

Przeanalizuj istniejący kod i testy w:

```text
studio/web/src/tabs/maps/domain/map.ts
studio/web/src/tabs/maps/domain/operations.ts
studio/web/src/tabs/maps/domain/operations.test.ts
studio/web/src/tabs/maps/domain/assets.ts
studio/web/src/tabs/maps/state/store.ts
studio/web/src/tabs/maps/format/codec.ts
studio/web/src/tabs/maps/rendering/canvas.ts
studio/web/src/tabs/maps/MapsTab.tsx
studio/server/src/services/mapCatalog.ts
game-contracts/src/main/kotlin/com/urkaaaz/contracts/MapDefinition.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/MapDefinitionValidator.kt
game-campaign/src/test/kotlin/com/urkaaaz/campaign/MapDefinitionValidatorTest.kt
```

Sprawdź też bieżące mapy JSON, szczególnie
`game-campaign/src/main/resources/maps/chapter1-level4.json`: zawiera istniejące
różne wysokości fundamentów i Relics, które trzeba zachować. Reużyj istniejący
`MapDefinition`, codec, paletę assetów, store, historię undo/redo, canvas,
walidację i Save dialog. Nie twórz drugiego kontraktu mapy, API ani katalogu
assetów.

## 2. Zakres i zatwierdzone decyzje

- Dodaj płynny suwak **Terrain intensity** w zakresie 100–600 world units,
  krok 1, domyślnie 160 (dotychczasowe Medium). **Decoration density** pozostaje
  wyborem `Low`, `Medium`, `High`, domyślnie `Medium`.
- Dodaj **Random Map**. Każde kliknięcie losuje nowy seed i zastępuje w bieżącym
  szkicu profil terenu, wysokości fundamentów oraz wszystkie zwykłe dekoracje.
- Zachowaj `mapId`, biome, świat, sample step, początkową kamerę, ustawienia
  prezentacji i istniejące Relics. Randomizer zachowuje biome aktualnej mapy
  i nie generuje Relics.
- Jedno losowanie jest jedną atomową operacją store. Undo odtwarza cały
  poprzedni dokument, Redo dokładnie ten sam wygenerowany wynik. Błąd
  generowania pozostawia dokument i historię bez zmian.
- Przycisk pozostaje dostępny po losowaniu i ręcznych zmianach terenu.
- Teren można edytować niezależnie od obecności dekoracji. `Y` każdego obiektu
  jest niezależną współrzędną world-space; zmiana terenu nie zmienia `Y`
  dekoracji.
- Wygenerowane drzewa, skały i propsy początkowo umieszcza się na powierzchni.
  Potem autor może ręcznie zmieniać ich wysokość przez pionowe przeciąganie
  i liczbowy input `Y`. Relics również zachowują absolutne `Y`, ale walidator
  nadal wymaga, by ich footprint mieścił się w glebie.
- Istniejące Relics nie zmieniają ID, assetu, rozmiaru, X, Y, skali ani obrotu.
  Generowany profil musi zachować ich walidowany footprint w glebie; jeżeli
  nie da się utworzyć poprawnego kandydata, zwróć jawny błąd bez częściowej
  zmiany szkicu.
- Usuń `Lock terrain` jako przycisk, operację oraz blokadę/fazę. Teren,
  fundamenty i dekoracje pozostają edytowalne w dowolnej kolejności.
- Zastąp kontrolkę blokady przełącznikiem capu: **Show cap**/**Hide cap**.
  Domyślnie cap jest ukryty. Zmiana widoczności nie zmienia dokumentu, dirty
  state, autosave mapy, undo/redo, JSON ani walidacji.
- Save pozostaje jawne. Generator nigdy nie wywołuje `saveMap` ani endpointu
  zapisu.

## 3. Zgodność i twarde ograniczenia

- Nie zmieniaj `schemaVersion` ani struktury JSON `MapDefinition`.
- Generator zapisuje seed i stabilny `generatorVersion` w istniejącym
  `generationMetadata`; jeśli istnieje `authoringVersion`, zachowaj go.
- Używaj bieżącego `map.terrain.sampleStep`; obsłuż istniejące wartości 20 i
  40. Nowa mapa może pozostać przy obecnym domyślnym kroku 40.
- Profil obejmuje dokładnie `x=0..7200`, każda wysokość mieści się
  w `0..4800`, próbki są regularne, a różnica sąsiednich wysokości nie
  przekracza Kotlinowego `MapDefinitionValidator.MAX_TERRAIN_DELTA` (200).
- Utrzymaj pozycje, szerokości, assety i hitboxy fortec. Wysokości
  fundamentów losuj niezależnie z ograniczeniem różnicy maksymalnie 520
  world units albo mniejszej wartości, jeżeli wymaga tego walidator/preview.
  Limit 520 wynika z najwyższej różnicy w obecnym zwalidowanym Chapter 1,
  Level 4; nie kopiuj go do balansu.
- Zachowaj płaskie próbki fundamentów, poprawne `protectedTerrainZones`,
  referencje fortec do stref, przejścia walidowane w tolerancji fundamentu
  oraz zsynchronizowane wysokości pochodnych katapult. Nie dodawaj katapult
  do JSON-a.
- Utrzymuj szerokie, gładkie pagórki i doliny. Środkowa 1/3 świata
  (`x=2400..4800`) ma mieć małe odchylenie profilu od bazowej linii
  interpolowanej między fundamentami — roboczy limit to około 40 world units.
- Zwykłe dekoracje losuj wyłącznie z istniejących assetów drzew, skał i propsów
  dla aktualnego biomu przez `paletteAssetsForBiome`/istniejący indeks.
  Nie generuj Relics. Nie wybieraj nieznanych asset keys ani nie omijaj
  katalogu assetów.
- Nie używaj `Math.random`, losowości systemowej wewnątrz generatora ani
  globalnego PRNG. Nowy seed utwórz na granicy kliknięcia przez Web Crypto;
  cała funkcja domenowa musi używać jawnego, stabilnego PRNG.
- Nie dodawaj innych kontrolek wartości liczbowych poza suwakiem intensywności
  terenu określonym powyżej. Nie dodawaj parametrów balansu, losowości runtime,
  nowych endpointów, nowych modułów Gradle, Android UI ani zależności
  gameplayowych.
- Nie stosuj cichych fallbacków, sukcesopodobnego wyniku ani częściowego
  zapisu. Błędy asset index, generacji i walidacji muszą być widoczne.

## 4. Amplituda terenu i gęstość dekoracji

Amplituda **Terrain intensity** jest płynną, całkowitą wartością w zakresie
100–600 world units, z krokiem 1 i wartością początkową 160. Utrzymuj granice
i wartość domyślną jako nazwane stałe w domenie generatora, nie w komponencie
React ani `game-balance.json`. Każda wartość z zakresu musi być obsługiwana;
wynik nadal musi przechodzić walidację terenu. W korytarzu środka pozostaje
ograniczenie odchylenia około 40 world units.

Gęstość dekoracji pozostaje monotonicznym wyborem `Low < Medium < High`:

| Preset | Łączna liczba drzew, skał i propsów |
|---|---:|
| Low | 3–6 |
| Medium | 7–11 |
| High | 12–18 |

Sprawdź dolny i górny zakres amplitudy, wartość początkową oraz density
presety na mapach referencyjnych, w tym biomach o różnej liczbie assetów.
Amplituda i density presets są ustawieniami authoringowymi, nie konfiguracją
balansu ani runtime.

Ilość obiektów jest losowana w zakresie presetów gęstości. Rozdziel kategorie z puli
aktualnego biomu w sposób deterministyczny dla seed. Nie wymagaj dostępności
każdej kategorii — jeśli biome nie ma assetów danej kategorii, wykorzystaj
pozostałą pulę bez błędu; jeśli nie ma żadnych assetów, zgłoś jawny błąd.
Identyfikatory dekoracji wyprowadź deterministycznie z seeda i indeksu,
zachowując unikalność względem Relics.

## 5. Generator i store

1. Utwórz czysty moduł domenowy w `studio/web/src/tabs/maps/domain/`,
   np. `randomMap.ts`. Funkcja przyjmuje bieżący `MapDocumentJson`, jawny seed,
   amplitudę terenu, preset gęstości i dostępne asset keys; zwraca kompletny
   nowy kandydat albo jawny błąd.
2. Udostępnij testowalny PRNG z małym, stabilnym seedem (liczba całkowita
   bezpiecznie reprezentowalna w JSON/JavaScript). Zapisz ten seed i
   `generatorVersion` w metadata. Dwa wywołania z identycznym dokumentem,
   seedem, amplitudą, presetem gęstości i asset indexem muszą zwrócić
   semantycznie identyczny wynik.
3. Generuj próbki w istniejącym sample step. Użyj interpolowanej linii między
   fundamentami jako bazowej wysokości, gładkich, szerokich zmian poza
   korytarzem środka oraz ograniczonej zmienności w środkowej 1/3 świata.
   Clampuj kandydatów do granic świata i limitów walidatora przed
   skonstruowaniem wyniku.
4. Wybierz fundamenty niezależnie w bezpiecznym zakresie. Przy każdej
   fortecy zaktualizuj foundation height, pełną strefę ochronną i flat samples;
   zbuduj gładkie przejścia bez przekraczania delta 200. Wyprowadź wewnętrzne
   katapulty przez istniejące `catapultsForFortresses`, nie duplikuj geometrii.
5. Zachowaj w całości obiekty `relicSize != null`. Uwzględnij ich soil
   footprint podczas generowania profilu. Nie przesuwaj, nie skaluj ani nie
   zmieniaj assetu/ID Relic.
6. Wygeneruj nową listę zwykłych obiektów (`relicSize == null`) z assetów
   aktualnego biomu. Pozycje mieszczą się w granicach mapy, nie wchodzą
   w chronione fundamenty i początkowo są na powierzchni terenu. To ustawienie
   początkowe nie jest trwałym anchorem.
7. Waliduj pełnego kandydata. Jeśli output jest niepoprawny albo istniejące
   Relics blokują poprawny profil, zwróć jawny błąd i nie dispatchuj operacji.
   Nie naprawiaj mapy przez losowanie w pętli bez limitu ani cichy fallback.
8. Dodaj do modelu operacji jedną operację generowania zawierającą wszystkie
   potrzebne wejścia; `applyOperation` stosuje ją atomowo. Obecny reducer
   store ma już mechanizm jednej pozycji historii i zachowania dokumentu przy
   błędzie — rozszerz go bez tworzenia drugiej historii.
9. Terrain operations nie zmieniają `y` żadnego obiektu. `MOVE_OBJECT`
   zapisuje podane X/Y jako absolutne world-space współrzędne. Relics pozostają
   nieruchome podczas zmian terenu; jeżeli terrain operation narusza ich soil
   footprint, odrzuć atomową operację z komunikatem wskazującym Relic.
10. Zachowaj `mapId` w szkicu. Istniejący Save dialog nadal przypisuje
    `chapterN-levelX`, bierze aktualny `expectedHash`, waliduje i atomowo
    aktualizuje obie kopie map.

## 6. Usunięcie blokady i cap preview

Przed zmianami znajdź wszystkie odwołania do:

```text
EditorPhase
TERRAIN_LOCKED
DECORATION_EDITING
READY_TO_EXPORT
LOCK_TERRAIN
MARK_READY_TO_EXPORT
Lock terrain
```

Usuń fazowo blokujący workflow end-to-end:

- `Lock terrain` UI, statusy/hinty, `LOCK_TERRAIN` i warunki, które blokują
  rysowanie, zmianę fundamentów, dodawanie lub przesuwanie dekoracji;
- fazy `TERRAIN_LOCKED` i `DECORATION_EDITING`;
- `READY_TO_EXPORT`/`MARK_READY_TO_EXPORT`, jeżeli nie mają niezależnego
  aktywnego konsumenta; Save i tak waliduje przed zapisem;
- wartości `phase` w `EditorDocument`, `newDocument`, `load`, `resetMap`,
  rendererze i test fixtures, jeśli po audycie nie są już potrzebne.

Nie zostawiaj dead code, martwych kontrolek ani tekstów o konieczności
blokowania terenu. Każdy edytowalny teren/obiekt ma nadal przejść walidację;
usunięcie locka nie znosi reguł geometrii ani footprintów.

Cap:

- stan `showCap` należy do prezentacji `MapsTab`/view, nie do `MapDefinition`
  ani `EditorDocument`;
- domyślnie `false`;
- przy stanie ukrytym pokaż przycisk **Show cap**, przy widocznym **Hide cap**;
- przekaż jawny parametr do renderera; nie wyprowadzaj widoczności z fazy;
- toggle nie zmienia revision, dirty status, autosave mapy, undo/redo ani JSON;
- terenowe punkty kontrolne i narzędzia są dostępne niezależnie od capu.

## 7. Walidator i zgodność runtime

Kotlinowy `MapDefinitionValidator` jest autorytatywny. Przed zmianami porównaj
jego reguły z `domain/operations.ts`. Dla generatora TypeScript musi
odzwierciedlać co najmniej:

- world `7200 × 4800`;
- sample step `20` lub `40` i równe odstępy;
- pełne pokrycie `x=0..7200`;
- próbki w granicach świata;
- delta sąsiadów `<= 200` (obecny TS limit `400` jest rozbieżny);
- dokładnie jedną fortecę na team, prawidłowe asset keys i protected zone;
- przejście fundamentu: próbki w tolerancji ±80, minimum trzy próbki i
  maksymalna różnica lokalna zgodna z Kotlinowym validator;
- unikalne IDs i istniejące asset keys;
- zgodność Relic z biomem/rozmiarem oraz footprint w glebie;
- zoom i anchor kamery.

Nie kopiuj po cichu innego zestawu limitów do generatora. Zmień lub rozszerz
istniejącą funkcję `validate`, aby wskazywała ścieżki zgodne z codec, dodaj
testy tych reguł i istniejących map. Jeśli nie da się utrzymać zgodności bez
zmiany kontraktu mapy, zatrzymaj się i opisz blocker zamiast zmieniać
`schemaVersion` lub runtime.

## 8. Testy

### Czysta domena

- identyczne wejście/seed/amplituda/preset gęstości/asset index → identyczny
  map document;
- inny seed zmienia jedną lub więcej składowych randomizowanych;
- amplitudy 100, 160 i 600 dają niemalejące odchylenia terenu; każda wartość
  całkowita w zakresie suwaka jest obsługiwana, a density ranges pozostają
  monotoniczne;
- zachowaj biome, map ID, world, sample step, camera settings i Relics;
- dwa fundamenty mogą mieć różne wysokości, lecz wszystkie próby przechodzą
  pełną regułę fundamentów, chronionych stref i slope `<=200`;
- teren nie wychodzi poza świat, obejmuje końce i utrzymuje niski relief
  korytarza środka;
- zwykłe dekoracje pochodzą z asset indexu wybranego biomu, mają unikalne
  stabilne IDs, liczbę zgodną z presetem i poprawne world bounds;
- brak asset catalogu, nieznany/niekompletny biome lub niezgodny Relic daje
  jawny błąd i nie zmienia wejściowego dokumentu;
- terrain operations preserve absolute Y for every object; pointer drag and
  the numeric Y input manually update world-space height;
- Relic footprint pozostaje niezmienny; teren, który go narusza, nie jest
  zatwierdzany;
- cap visibility nie występuje w JSON ani operacjach mapy.

### Store i UI

- jedno losowanie to jeden undo; undo przywraca dokładnie wszystkie poprzednie
  dane, redo przywraca ten sam seed i wynik;
- kolejne losowanie podmienia ordinary decorations, zachowuje Relics i nie
  zapisuje plików;
- suwak amplitudy 100–600 (domyślnie 160) i preset gęstości
  (domyślnie Medium) działają przy następnym kliknięciu;
- podczas braku asset index/generowania błędów przycisk daje jawny komunikat;
- teren, fundamenty, placement i edycja dekoracji działają bez `Lock terrain`;
- Show/Hide cap przełącza tylko warstwę renderingu;
- Save nadal jest blokowany przy błędach, a poprawny save zachowuje bieżący
  map ID / przypisanie Chapter-Level / hash flow;
- usuń/zaktualizuj testy faz, żeby nie chroniły usuniętej blokady.

## 9. Dokumentacja, walidacja i ukończenie

Zaktualizuj po implementacji dokumenty o faktycznie wdrożonym zachowaniu:

- `docs/map-editor/design.md` — continuous editing, free world-space Y and
  separate cap visibility toggle;
- `docs/map-editor/risks.md` — zastąp decyzje, które wymagały blokowania
  terenu przed/po dekorowaniu;
- `docs/map-editor/web/README.md` — Random Map, suwak amplitudy, gęstość,
  Save i undo;
- `docs/project/knowledge.md` oraz `docs/project/current-state.md` — trwałe,
  zweryfikowane fakty i aktualny status.

Nie aktualizuj dokumentacji jakoby feature działał przed przejściem testów.
Uruchom:

```bash
cd studio
npm test
npm run lint
npm run build
cd ..
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Kryterium ukończenia: użytkownik może ustawić amplitudę suwakiem i wybrać
gęstość dekoracji, wielokrotnie generować poprawne mapy bez zapisu,
cofać/wznawiać losowanie, ręcznie edytować teren i dekoracje bez blokady
fazowej, zachować istniejące Relics,
pokazywać/chować cap oraz zapisać wybrany wynik dla konkretnego Chapter/Level
przez dotychczasowe API.
