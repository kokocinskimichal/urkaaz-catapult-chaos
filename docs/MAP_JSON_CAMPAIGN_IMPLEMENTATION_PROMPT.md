# Prompt implementacyjny: mapy kampanii JSON

> Status: implementacja tego promptu znajduje się w repozytorium. Wynik i
> aktualne przypisanie map opisano w
> [`MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md`](MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md).

Skopiuj cały prompt z następnej linii do agenta implementującego. Ma on
zrealizować plan z
[`MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md`](MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md).

---

Jesteś autonomicznym agentem implementującym migrację map kampanii Urkaaaz
z Kotlinowych definicji do bundlowanych plików JSON. Wykonaj całość
implementacji, testów i dokumentacji w bieżącym repozytorium. Nie kończ na
planie, szkielecie loadera ani prototypie.

Nie pytaj o decyzje już ustalone poniżej. Jeśli pojawi się rzeczywisty konflikt
między repozytorium a tym promptem, zachowaj istniejące zachowanie gameplayu,
nie zgaduj i zgłoś konkretny blocker. Nie commituj ani nie pushuj.

## Źródła prawdy i analiza

Najpierw przeczytaj:

```text
AGENTS.md
architecture.md
docs/MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md
docs/PROJECT_KNOWLEDGE.md
game-contracts/src/main/kotlin/com/urkaaaz/contracts/MapDefinition.kt
game-contracts/src/main/kotlin/com/urkaaaz/contracts/WorldDimensions.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelRegistry.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignMapDefinitions.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/MapDefinitionValidator.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/DemoLevelDefinition.kt
map-editor-web/src/domain/map.ts
map-editor-web/src/format/codec.ts
map-editor-format/src/main/kotlin/com/urkaaaz/mapeditor/format/MapEditorFormat.kt
```

Prześledź też wszystkie użycia `MapDefinition`, catapult placements,
initial camera anchor, biome, terrain tiles, decorations, campaign registry
i scenariusza w `game-application`, `game-simulation`, `game-ai` oraz
`android-app`.

Przed edycją sprawdź `git status --short`. Repozytorium może zawierać
niezacommitowane zmiany użytkownika. Zachowaj je; nie cofaj, nie nadpisuj
i nie formatuj plików niezwiązanych z zadaniem.

## Ustalone decyzje — nie zmieniaj ich

- Nie migruj ani nie eksportuj automatycznie obecnych Kotlinowych map.
- Użyj istniejącego eksportu webowego
  `~/Downloads/web-map.json` jako mapy demo. Nie generuj zastępczej mapy
  i nie bierz geometrii z definicji Kotlinowych.
- Oryginalnego pliku z Downloads nie modyfikuj ani nie usuwaj. Skopiuj
  zaakceptowaną wersję do zasobów aplikacji.
- Tymczasowo wszystkie poziomy kampanii używają jawnego przypisania do tej
  samej mapy demo. To etap integracji, nie docelowa relacja map do poziomów.
- Docelowo każdy poziom ma własny JSON. Nie twórz pozostałych map kampanii
  w ramach tego zadania; użytkownik będzie je dodawał później.
- Pliki map są dołączone do aplikacji i działają offline.
- Gra ma jeden kontrakt mapy i jedną autorytatywną walidację. Nie twórz
  drugiego domenowego modelu mapy ani drugiego ręcznie utrzymywanego
  walidatora.
- Świat zawsze ma `7200 × 4800` jednostek. JSON zawiera te wartości
  informacyjnie; walidator wymaga zgodności z `WorldDimensions`. Nie ustawiaj
  rozmiaru symulacji na podstawie JSON-a.
- Heightmapa `heightSamples` jest jedynym źródłem fizycznego kształtu terenu.
- Każdy biom ma jeden kafelek soil; nie serializuj ręcznych placementów soil.
  Renderer dobiera soil z biomu.
- Mapa zawiera protected zones, fortresses i wyłącznie wizualne decorations.
  Nie zapisuje reguł gameplayu.
- Katapulty są na stałe powiązane z fortecami. Nie zapisuj catapult placements
  w mapie; zachowaj istniejący kontrakt gry, który wyznacza pozycję
  i transformację katapulty z fortecy.
- Początkowa kamera nie należy do mapy. Zachowaj globalną regułę kamery;
  ewentualne wyjątki pozostają w konfiguracji poza mapą.
- Metadane mapy są opcjonalne i zawierają wyłącznie `seed` oraz
  `generatorVersion`.
- Kampania zachowuje swoje reguły Kotlinowe: AI, poziom trudności, jednostki,
  amunicję, zaklęcia, zasoby, wiatr, tutorial i reguły zwycięstwa.

## Zakres docelowego JSON-a

Uzgodniony format produkcyjny:

```json
{
  "schemaVersion": 1,
  "mapId": "campaign-level-001",
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
    "seed": 10001,
    "generatorVersion": "map-editor-web/..."
  }
}
```

`heightSamples` i placement arrays w przykładzie są skrótem strukturalnym,
nie poprawną gotową mapą. Eksport produkcyjny musi zawierać pełną geometrię
i potrzebne placementy.

### Teren i granice

- Wymagaj dokładnie `world.width = 7200` i `world.height = 4800`.
- Zachowaj `terrain.sampleStep` oraz pełne, uporządkowane
  `terrain.heightSamples`.
- Próbki muszą obejmować pełny zakres X od `0` do `7200`.
- Zachowaj istniejące reguły nachyleń, fundamentów i chronionych stref.
- Nie używaj `world.tileSize` — to duplikuje `terrain.sampleStep`.
- Nie serializuj `terrain.tiles` ani drugiej geometrii w kafelkach.

### Fortecy

Każda mapa musi zawierać dokładnie jedną fortecę BLUE i jedną RED. Zachowaj
placement i wymagane pola istniejącego kontraktu, w tym `x`,
`foundationHeight`, `halfWidth`, `assetKey`, `hitboxProfile` i
`protectedZoneId`, jeśli są wymagane przez aktualny model.

Nie usuwaj ani nie zmieniaj ustalonych transformacji assetu, hitboxa, maga,
fortecy lub katapulty. Jeśli obecny model rozdziela je między kilka typów,
zachowaj ich wspólny kontrakt i wyprowadź dane runtime bez dublowania ich
w JSON-ie.

### Chronione strefy

Zachowaj identyfikator strefy, zakres X, zakres wysokości i politykę
deformacji podłoża. Każda forteca musi referować poprawną strefę. JSON jest
źródłem ich geometrii; nie twórz drugiej listy stref w kampanii.

### Dekoracje

`decorations` zawiera tylko elementy wizualne. Każdy element zawiera:

- unikalne `id`;
- `assetKey`;
- światowe `x` i `y`;
- `scale`;
- `rotationDegrees`;
- opcjonalne `relicSize` dla zakopanego Relic.

Dla drzewa, skały i propsa `y` jest kotwicą podstawy na powierzchni terenu.
Dla Relic `y` jest pozycją wewnątrz ziemi. Zachowaj walidację kotwic
i footprintów z istniejącego validatora. Dekoracje nie dostają kolizji ani
zachowania gameplayowego.

Jeśli wejściowy dokument zawiera obiekt gameplayowy, nie usuwaj go po cichu
ani nie przedstawiaj jako dekoracji. Zgłoś konkretny problem; mapa demo,
która jest źródłem tego zadania, ma puste `objects`.

### Dane, które trzeba usunąć z mapy

- `catapults`;
- `initialCameraAnchor`;
- `world.tileSize`;
- `terrain.tiles` i wszelkie soil tile placements;
- ogólna lista `objects` mogąca zawierać elementy gameplayowe;
- stan edytora (`phase`, `terrainLocked`);
- `author`, `source`, `authoringVersion`, fingerprint i inne metadane poza
  opcjonalnymi `seed` i `generatorVersion`.

## Wymagania architektoniczne

### Jeden kontrakt

- Uczyń zaakceptowany JSON źródłem danych mapy.
- Użyj istniejącego `MapDefinition` jako jedynego typowanego kontraktu mapy.
  Możesz go refaktoryzować, żeby odzwierciedlał zaakceptowane pola, ale
  zaktualizuj wszystkie call sites i testy.
- Kotlinowy codec może mapować nazwy z formatu JSON, ale jest wyłącznie
  granicą serializacji — nie dodawaj drugiej trwałej definicji domenowej.
- Dopasuj eksport/import `map-editor-web` do tego samego JSON-a. Nie utrzymuj
  osobnego formatu runtime i osobnego eksportu edytora.
- Użyj ścisłego dekodowania: nieznany przyszły `schemaVersion`, nieznany enum,
  błędny typ lub brak wymaganych danych musi dawać czytelny błąd.

### Jeden walidator

- Wykorzystaj `MapDefinitionValidator` jako autorytatywne miejsce walidacji
  semantycznej.
- Dodaj walidację stałych wymiarów względem `WorldDimensions`, rozpoznanego
  biomu i referencji assetów, jeśli brak ich w istniejącym validatorze.
- Nie duplikuj tych samych reguł w `CampaignLevelDefinition`,
  `MatchViewModel` ani rendererze.
- Walidacja UI edytora może dawać wcześniejszy feedback, ale nie zastępuje
  walidacji przed rozpoczęciem meczu.
- Błąd musi wskazywać ścieżkę JSON, np. `terrain.heightSamples[42]`.

### Granice modułów i loader

- Bundluj mapy w zasobach dostępnych offline.
- Dodaj map repository/loader, które zwraca `MapDefinition` albo jawny błąd.
- Utrzymaj parser i loader niezależne od Android UI oraz bez importów Androida
  w contracts, campaign, application, domain lub simulation.
- Android może dostarczać źródło zasobów przez cienki adapter.
- Zweryfikuj, że plik jest faktycznie spakowany w APK i można go odczytać
  w runtime. Nie zakładaj, że dostęp do `~/Downloads` istnieje po instalacji.
- Nie wstawiaj awaryjnie innej mapy, gdy JSON jest brakujący lub błędny.
  Przejściowe użycie mapy demo musi być jawne w przypisaniach poziomów.

## Zadania implementacyjne

### Etap 1 — sprawdzenie i przygotowanie eksportu demo

1. Otwórz `~/Downloads/web-map.json`, sprawdź parsowalność, kompletność próbek,
   biome, fortress placements i protected zones.
2. Nie modyfikuj oryginalnego pliku.
3. Normalizuj go do zaakceptowanego kontraktu:
   - zachowaj biome, świat, heightmapę, fortecy i protected zones;
   - odrzuć stałe w grze catapults z JSON-a, nie zmieniając ich kontraktu
     runtime;
   - usuń `world.tileSize` i puste `terrain.tiles`;
   - zmień puste `objects` na `decorations`;
   - przesuń `seed` i `generatorVersion` do `generationMetadata`;
   - usuń stan edytora i pozostałe niedozwolone metadane;
   - ustaw jawny przejściowy `mapId` `demo-map`.
4. Przepuść znormalizowaną mapę przez Kotlinowy codec i validator.
5. Skopiuj zwalidowaną wersję do bundlowanych zasobów aplikacji.

Nie poprawiaj ani nie przerysowuj geometrii demo bez potrzeby. Jeśli plik nie
spełnia kontraktu i nie można go bezstratnie dopasować, zgłoś konkretny błąd
zamiast podmieniać go inną mapą.

### Etap 2 — kontrakt, codec i eksport edytora

- Zaktualizuj `MapDefinition`, typy JSON i serializację zgodnie z powyższym
  formatem.
- Usuń z mapowego kontraktu catapults, initial camera anchor i terrain tile
  placements; usuń lub zmień ogólną mapową listę `objects` na `decorations`.
- Zachowaj `MapObjectPlacement` tylko jeśli nadal jest adekwatny; jeśli typ
  pozostaje, nie może przenosić gameplayowych reguł do mapy.
- Zaktualizuj `map-editor-web` typy, `normalizeMap`, `encode` i `decode`, aby
  nowy eksport nie generował zakazanych pól i round-trip nie gubił danych.
- Dodaj testy zgodności eksportera webowego z Kotlinowym codec.
- Nie implementuj automatycznej migracji stu Kotlinowych map.

### Etap 3 — loader zasobów i walidacja

- Zaimplementuj odczyt pliku po stabilnym `mapId`/przypisaniu poziomu.
- Zwracaj jawny rezultat sukcesu albo błąd; usuń ciche fallbacki.
- Waliduj po dekodowaniu, przed zbudowaniem scenariusza.
- Testuj brak pliku, uszkodzony JSON, złą wersję schematu, zły biome, błędne
  wymiary, braki w heightmapie, błędne protected zones, fortecy i dekoracje.
- Dodaj test pakowania i odczytu bundlowanego pliku.

### Etap 4 — integracja demo ze wszystkimi poziomami

- Jawnie przypisz mapę `demo-map` do wszystkich poziomów w konfiguracji
  przejściowej.
- Nie umieszczaj samej geometrii, biomu ani dekoracji ponownie w
  `CampaignLevelDefinition`.
- Zachowaj istniejące parametry kampanii dla wszystkich 100 poziomów.
- Usuń powielony biom z `TerrainVariant`/`DemoLevelDefinition`; biome pobieraj
  z `MapDefinition`.
- Zawsze buduj świat gry z `WorldDimensions`, po walidacji zgodności wymiarów
  mapy.
- Doprowadź `MapDefinition` przez scenariusz i application gateway do
  symulacji i renderowania.
- Wyznaczaj catapults przez istniejący hard contract fortecy.
- Stosuj globalną regułę kamery, a wyjątki (jeśli istnieją) trzymaj poza
  mapą.

### Etap 5 — usunięcie Kotlinowych źródeł map

Dopiero po działającym, zwalidowanym meczu demo:

- usuń rejestr `CampaignMapDefinitions` i chapterowe pliki definiujące
  geometrię map;
- usuń nieużywane authoring/building code map oraz aktualizuj testy, które
  zależały od dawnych map;
- zachowaj `MapDefinitionValidator`, campaign settings, kontrakt fortecy,
  kontrakt katapulty i inne elementy nadal używane przez runtime;
- potwierdź przez wyszukiwanie, że runtime nie ładuje geometrii map z Kotlin.

Nie usuwaj starego kodu przed przejściem testów i sprawdzeniem mapy w
uruchomionej aplikacji.

### Etap 6 — pozostawienie ścieżki na kolejne mapy

- Mapy dodawane później mają własny JSON dla każdego poziomu kampanii.
- Utrzymuj tymczasowe przypisania do `demo-map` wyłącznie dla poziomów,
  których map użytkownik jeszcze nie dostarczył.
- Po dodaniu nowej mapy przypisz ją do jej poziomu i zwaliduj.
- Nie generuj brakujących map ani nie twórz placeholderów poza już
  zaakceptowaną mapą demo.
- Zapewnij test, że docelowo poziom bez mapy nie uruchomi się przez przypadek
  na innej mapie; przejściowe przypisanie musi być jawne.

## Testy wymagane do ukończenia

- Map JSON dekoduje się dokładnie do `MapDefinition`; encode/decode round-trip
  zachowuje semantykę wszystkich dozwolonych pól.
- `web-map.json` po normalizacji przechodzi `MapDefinitionValidator`.
- Wymiary z JSON są równe `WorldDimensions`, ale symulacja nadal bierze
  rozmiar ze stałego kontraktu gry.
- Próbki terenu są jedynym źródłem fizycznej powierzchni.
- Catapult placement nie pochodzi z mapy; runtime pozycjonuje katapultę zgodnie
  z istniejącym kontraktem fortecy.
- Renderer wybiera soil z biomu i nie wymaga `terrain.tiles`.
- Relics i dekoracje zachowują właściwe world-space anchors.
- Wszystkie poziomy używają jawnie `demo-map` podczas początkowej integracji,
  bez zmian pozostałych reguł kampanii.
- JSON jest dostępny offline w spakowanej aplikacji.
- Błędny/missing JSON powoduje czytelny błąd, nie cichy fallback.
- Testy nie wymagają porównania/parity z usuniętymi Kotlinowymi mapami, bo
  nie są one migrowane.
- `map-editor-web`: testy, build i lint.
- Android/Kotlin: wszystkie testy i assemble przechodzą.

## Walidacja końcowa

Uruchom:

```bash
./gradlew test assembleDebug --quiet
cd map-editor-web && npm test && npm run build && npm run lint
git diff --check
git status --short
```

Nie commituj, nie pushuj i nie zmieniaj branchy.

## Dokumentacja i raport

Zaktualizuj dokumentację, aby opisywała implementację, a nie dawne założenia:

- `docs/MAP_JSON_CAMPAIGN_MIGRATION_PLAN.md`;
- `docs/PROJECT_KNOWLEDGE.md`;
- `docs/README.md`;
- instrukcję map-editor-web, jeśli zmieni się eksport lub kontrakt.

Na końcu podaj:

1. zmienione pliki i odpowiedzialność nowych komponentów;
2. docelowy schemat JSON i sposób przypisania map;
3. potwierdzenie użycia `~/Downloads/web-map.json` jako demo;
4. potwierdzenie usunięcia Kotlinowych źródeł map bez ich automatycznej
   konwersji;
5. opis zachowanych reguł kampanii, biomu, świata, fortec i katapult;
6. wyniki testów/buildów;
7. każdy blocker lub pozostałe ograniczenie.
