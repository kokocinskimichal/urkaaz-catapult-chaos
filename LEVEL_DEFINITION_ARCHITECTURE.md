# Architektura definicji poziomów

## Cel dokumentu

Definicja poziomu opisuje kompletny, stabilny kontrakt kampanii: rozmiar i
profil terenu, położenie fundamentów, fortec, katapult i obiektów mapy,
początkowy punkt widoku oraz metadane potrzebne do odtworzenia i walidacji
poziomu.

Definicja poziomu nie jest stanem runtime meczu. Jest niemutowalnym opisem
scenariusza, z którego `game-campaign` i `game-simulation` tworzą początkowy
stan symulacji. Android może używać definicji do prezentacji i podglądu, ale
nie może samodzielnie generować alternatywnej geometrii poziomu.

## Podział odpowiedzialności modułów

### `game-contracts`

Moduł kontraktów przechowuje typy stabilne między warstwami:

- `MapDefinition`;
- `TerrainProfile`;
- `ProtectedTerrainZone`;
- `FortressPlacement`;
- `CatapultPlacement`;
- `MapObjectPlacement`;
- `InitialCameraAnchor`;
- `MapGenerationMetadata`;
- typy identyfikatorów, klucze assetów i wartości jednostek świata.

Kontrakty powinny być niemutowalne, serializowalne tam, gdzie wymaga tego
replay lub narzędzie authoringu, oraz niezależne od Androida. Nie mogą
zawierać `View`, `Canvas`, `Context`, bitmap ani logiki renderera.

### `game-campaign`

Moduł kampanii jest właścicielem:

- konkretnych definicji poziomów;
- kolejności i identyfikatorów kampanii;
- przypisania poziomu do scenariusza, biomu i początkowego wiatru;
- authoringu oraz reguł wyboru poziomu;
- walidacji definicji przed udostępnieniem jej aplikacji.

Kampania może dostarczać gotową `MapDefinition`, lecz nie powinna przechowywać
zmiennego stanu runtime, aktualnych kraterów, pozycji aktywnych pocisków ani
postępu gracza.

### `game-simulation`

Symulacja konsumuje zwalidowaną `MapDefinition` i na jej podstawie tworzy
początkowy teren oraz rozmieszczenie obiektów. Jest właścicielem zmian w czasie:

- ruchu i kolizji;
- obrażeń;
- kraterów i deformacji terenu;
- jednostek, pocisków i efektów;
- aktualnego stanu meczu.

Symulacja nie modyfikuje definicji poziomu. `TerrainProfile` jest wejściowym
profilem bazowym, a teren runtime jest jego kopią lub stanem wyprowadzonym z
definicji.

### `android-app`

Android odpowiada za:

- wybór poziomu;
- podgląd mapy;
- mapowanie world-space na ekran;
- prezentację dekoracji i obiektów przez asset keys;
- przekazanie wybranej definicji do `MatchScreen`.

Android nie powinien zawierać ręcznie wpisanych wysokości terenu, pozycji
fortec, zasad ochrony fundamentów ani drugiego walidatora mapy. Wszystkie te
wartości pochodzą z `MapDefinition` i są sprawdzane poza warstwą UI.

## Główny kontrakt `MapDefinition`

`MapDefinition` jest kompletnym opisem mapy kampanii. Powinna zawierać co
najmniej:

- stabilny identyfikator poziomu i wersję schematu;
- rozmiary świata;
- `TerrainProfile`;
- listę `ProtectedTerrainZone`;
- rozmieszczenie fortec przez `FortressPlacement`;
- rozmieszczenie katapult przez `CatapultPlacement`;
- listę `MapObjectPlacement`;
- `InitialCameraAnchor`;
- `MapGenerationMetadata`.

Kontrakt powinien pozwalać odtworzyć identyczny poziom na podstawie samej
definicji i metadanych. Jeżeli teren jest generowany z ziarna, ziarno musi być
jawne i stabilne.

## Kontrakty geometrii i rozmieszczenia

### `TerrainProfile`

`TerrainProfile` opisuje bazowy profil wysokości terenu. Profil jest próbkowany
w stałym kroku **40 world units**. Każda próbka reprezentuje wysokość gruntu
w danym punkcie osi poziomej.

Wymagania:

- próbki muszą być uporządkowane rosnąco po osi X;
- odstęp między kolejnymi próbkami wynosi 40 world units;
- różnica wysokości między sąsiednimi próbkami nie może przekraczać 200 world
  units;
- pierwszy i ostatni punkt muszą pokrywać zakres wymagany przez mapę;
- interpolacja pomiędzy próbkami musi być deterministyczna;
- profil bazowy nie może zawierać przypadkowych zmian generowanych w UI.

Limit różnicy 200 world units ogranicza skrajne uskoki, pomaga utrzymać czytelny
kontur mapy i zapewnia bezpieczne rozmieszczenie obiektów na przejściach.

### Fundamenty

Forteca i katapulta nie są wyłącznie punktami na mapie. Każdy placement musi
mieć określoną podstawę, której wysokość może być różna od wysokości podstawy
drugiego obiektu. Różne wysokości fundamentów są poprawne i nie należy
normalizować ich do jednej wspólnej wartości.

Reguły fundamentów:

- wysokość fundamentu jest jawna w placement albo wyprowadzona z profilu w
  sposób deterministyczny;
- fundament może leżeć na innym poziomie niż fundament przeciwnej fortecy;
- do sprawdzenia przejścia pod fundamentem używa się **trzech próbek**:
  punktu przed fundamentem, punktu pod jego środkiem i punktu za fundamentem;
- wszystkie trzy próbki muszą spełniać regułę bezpiecznego przejścia;
- sprawdzany zakres uwzględnia tolerancję **±80 world units** wokół
  fundamentu;
- walidacja nie może uzależniać poprawności od dokładnie jednej próbki profilu.

Strefa fundamentu powinna pozostać odporna na późniejsze kratery. Oznacza to,
że początkowa geometria musi mieć zapas i nie może opierać się na wąskim,
pojedynczym punkcie podparcia. `ProtectedTerrainZone` blokuje albo ogranicza
deformację terenu w obszarze, który musi zachować przejezdność, widoczność lub
stabilność obiektu.

### `ProtectedTerrainZone`

`ProtectedTerrainZone` opisuje obszar, w którym deformacja terenu jest
ograniczona. Strefa może chronić:

- fundament fortecy;
- fundament katapulty;
- przejście pomiędzy fundamentem i profilem terenu;
- obszar wymagany przez scenariusz lub tutorial.

Strefa powinna zawierać stabilny identyfikator, zakres osi X, dolną i górną
granicę oraz politykę reakcji na krater. Polityka musi być interpretowana przez
symulację, a nie przez renderer.

### `FortressPlacement`

`FortressPlacement` określa co najmniej:

- stronę lub właściciela;
- pozycję i wysokość fundamentu;
- rozmiar obszaru zajmowanego przez fortecę;
- klucz grafiki fortecy;
- profil hitboxa albo identyfikator profilu kolizji;
- powiązaną `ProtectedTerrainZone`.

Pozycja fortecy jest częścią scenariusza poziomu. Nie wolno ustawiać jej
ponownie na podstawie przypadkowego obliczenia w `BattlefieldView`.

### `CatapultPlacement`

`CatapultPlacement` określa:

- stronę lub właściciela;
- pozycję i wysokość fundamentu;
- orientację początkową;
- klucz grafiki;
- obszar zajmowany na mapie;
- powiązaną ochronę terenu, jeżeli katapulta wymaga stabilnej podstawy.

Katapulta jest obiektem prezentowanym i źródłem pocisków. Zgodnie z bieżącą
architekturą nie jest celem obrażeń fortecy; hitboxy obrażeń dotyczą fortec i
innych obiektów wskazanych przez kontrakt symulacji.

### `MapObjectPlacement`

`MapObjectPlacement` opisuje dekoracyjne lub funkcjonalne obiekty mapy:

- stabilny identyfikator;
- typ obiektu;
- pozycję, warstwę i skalę w world-space;
- opcjonalny obrót;
- `assetKey`;
- flagę kolizji lub interakcji, jeżeli obiekt ją posiada.

Obiekt dekoracyjny nie powinien sam z siebie zmieniać reguł gry. Obiekty
funkcjonalne muszą mieć jawnie określoną odpowiedzialność w domenie lub
symulacji.

### `InitialCameraAnchor`

`InitialCameraAnchor` określa początkowy punkt widoku dla match screen:

- pozycję world-space;
- opcjonalny cel kamery;
- początkowy zoom;
- ograniczenia kadrowania;
- identyfikator scenariusza widoku, jeżeli poziom wymaga specjalnego startu.

Anchor jest wskazówką dla kontrolera kamery. Nie może zmieniać geometrii ani
stanu symulacji.

### `MapGenerationMetadata`

`MapGenerationMetadata` przechowuje informacje pozwalające odtworzyć i
zdiagnozować definicję:

- wersję schematu;
- źródło definicji;
- seed generatora, jeśli użyto generowania;
- wersję generatora;
- nazwę autora lub narzędzia authoringu;
- znaczniki oraz notatki techniczne;
- checksumę albo fingerprint danych, jeśli jest wymagany przez replay.

Metadane nie mogą być używane jako ukryty kanał gameplayu. Zmiana danych
wpływających na symulację musi zmienić wersję lub fingerprint definicji.

## Dekoracje i asset keys

Dekoracje są wskazywane przez stabilne `assetKey`, a nie przez bezpośrednie
ścieżki plików Androida. Przykładowe klucze mogą reprezentować:

- tło biomu;
- warstwę nieba;
- skały i drzewa;
- elementy ruin;
- warianty gruntu;
- dekoracje fundamentów;
- obiekty scenariusza.

Map definition przechowuje klucz, wariant, world-space placement i parametry
skali. `android-app` mapuje klucz na `BattlefieldAssetCatalog`. Taki podział
pozwala zmienić źródło bitmap bez zmiany kontraktu kampanii.

Asset key musi być:

- stabilny między buildami;
- jednoznaczny;
- niezależny od nazwy pliku lokalnego;
- możliwy do sprawdzenia przez walidator assetów;
- niepusty dla obiektu, który ma być renderowany.

Brak assetu powinien być błędem walidacji albo jawnym błędem ładowania, a nie
cichym zastąpieniem przypadkowym assetem.

## `MapDefinitionValidator`

`MapDefinitionValidator` wykonuje walidację przed udostępnieniem poziomu
aplikacji lub symulacji. Walidator powinien zwracać listę jawnych błędów, a nie
tylko `Boolean`.

### Przykładowe błędy

- `EMPTY_LEVEL_ID` — brak stabilnego identyfikatora poziomu;
- `UNSUPPORTED_SCHEMA_VERSION` — nieobsługiwana wersja schematu;
- `INVALID_WORLD_BOUNDS` — szerokość lub wysokość świata jest niedodatnia;
- `TERRAIN_PROFILE_EMPTY` — profil nie ma próbek;
- `TERRAIN_SAMPLE_SPACING` — próbki nie są oddalone o 40 world units;
- `TERRAIN_HEIGHT_DELTA` — sąsiednie wysokości różnią się o więcej niż 20;
- `TERRAIN_PROFILE_OUT_OF_BOUNDS` — profil nie pokrywa zakresu mapy;
- `MISSING_FORTRESS` — brakuje wymaganej fortecy;
- `DUPLICATE_FORTRESS_OWNER` — strona ma więcej niż jedną niedozwoloną fortecę;
- `MISSING_CATAPULT` — brakuje wymaganej katapulty;
- `PLACEMENT_OUT_OF_BOUNDS` — obiekt wychodzi poza świat;
- `FOUNDATION_TRANSITION_UNSAFE` — przejście fundamentu nie spełnia warunku
  trzech próbek;
- `FOUNDATION_TOLERANCE_EXCEEDED` — obszar ±80 world units ma niebezpieczną
  różnicę wysokości;
- `FOUNDATION_ZONE_MISSING` — placement nie ma wymaganej strefy ochronnej;
- `PROTECTED_ZONE_INVALID` — strefa ochronna ma błędny zakres;
- `DECORATION_ASSET_KEY_EMPTY` — dekoracja nie ma asset key;
- `DUPLICATE_PLACEMENT_ID` — identyfikator placementu się powtarza;
- `CAMERA_ANCHOR_OUT_OF_BOUNDS` — początkowy anchor jest poza mapą;
- `MISSING_GENERATION_SEED` — definicja generowana wymaga ziarna;
- `ASSET_KEY_UNKNOWN` — asset key nie istnieje w katalogu zasobów.

Walidator powinien wskazywać ścieżkę do błędnego elementu, np.
`terrainProfile.samples[4]` albo `fortresses[0].foundationHeight`, oraz
wartość oczekiwaną i otrzymaną, jeśli ma to znaczenie diagnostyczne.

Wynik walidacji może mieć postać:

```kotlin
data class MapValidationResult(
    val errors: List<MapValidationError>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
```

Nie należy uruchamiać meczu z definicją, dla której `isValid` jest fałszywe.

## Rola skilla `/addLevel`

Skill `/addLevel` jest procesem authoringu nowego campaign level. Powinien:

1. odczytać istniejące kontrakty, reguły architektury i podobne poziomy;
2. utworzyć definicję w `game-campaign`;
3. dobrać lub wygenerować podczas authoringu profil terenu z próbkami co 40
   world units;
4. ustawić fundamenty, strefy ochronne, fortecy, katapulty i kamerę;
5. przypisać dekoracje przez stabilne asset keys;
6. wygenerować `MapGenerationMetadata`;
7. uruchomić `MapDefinitionValidator`;
8. dodać test kontraktu i test scenariusza;
9. zbudować aplikację i przygotować podgląd;
10. zebrać akceptację przed uznaniem poziomu za gotowy.

Skill nie powinien dodawać logiki poziomu do `MainActivity`, duplikować
generatora terenu podczas runtime ani omijać walidatora. Poziom ma być opisany danymi i
kontraktami, a nie specjalnymi warunkami w rendererze.

## `LevelAuthoringRequest`

`LevelAuthoringRequest` jest wejściem do procesu tworzenia poziomu. Powinien
opisywać intencję autora, nie gotowy runtime state:

```kotlin
data class LevelAuthoringRequest(
    val levelId: String,
    val displayName: String,
    val biomeId: String,
    val worldWidth: Int,
    val worldHeight: Int,
    val fortressOwners: List<String>,
    val requestedDecorations: List<String>,
    val initialCameraAnchor: InitialCameraAnchor?,
    val terrainSeed: Long?,
    val notes: String = ""
)
```

Request może być rozszerzony o wymagania tutoriala, difficulty, wiatr,
obiekty funkcjonalne i ograniczenia authoringu, ale nie powinien zawierać
obiektów mutowalnych symulacji, aktualnych HP, pocisków ani kraterów.

Proces authoringu przekształca request w `MapDefinition`, a następnie przekazuje
go do walidatora. Sam request nie jest kontraktem uruchamianym przez
`game-simulation`.

## Przepływ od authoringu do akceptacji

```text
LevelAuthoringRequest
  → authoring / ręczna definicja w game-campaign
  → MapDefinition
  → MapDefinitionValidator
  → testy kontraktów i scenariusza
  → build i testy projektu
  → podgląd Android match screen
  → akceptacja poziomu
  → publikacja w katalogu kampanii
```

### 1. Walidacja statyczna

Sprawdzić kompletność definicji, geometrię profilu, limity wysokości,
fundamenty, strefy ochronne, zakresy placementów, camera anchor i asset keys.

### 2. Testy

Testy powinny potwierdzać:

- stabilność i kompletność kontraktu;
- poprawność poziomu dla obu stron;
- trzy próbki przejścia pod fundamentem;
- tolerancję ±80 world units;
- limit różnicy sąsiednich próbek 20;
- ochronę przed kraterem;
- deterministyczne utworzenie początkowego stanu symulacji;
- poprawne mapowanie asset keys;
- poprawny początkowy anchor kamery.

### 3. Build

Z katalogu głównego uruchomić:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

### 4. Podgląd

Podgląd powinien używać tej samej definicji, która zostanie przekazana do
match screen. Nie wolno tworzyć osobnej, uproszczonej mapy tylko na potrzeby
preview. Należy sprawdzić:

- czy fundamenty nie wiszą ani nie zapadają się w teren;
- czy forteca i katapulta są na oczekiwanych stronach;
- czy przejścia przy fundamentach są czytelne;
- czy dekoracje mają właściwą skalę i world-space;
- czy camera anchor pokazuje właściwy fragment mapy;
- czy krater w pobliżu strefy chronionej nie niszczy wymaganej podstawy.

### 5. Akceptacja

Poziom jest zaakceptowany dopiero po:

- przejściu walidatora bez błędów;
- przejściu testów;
- przejściu builda;
- pozytywnym podglądzie;
- potwierdzeniu, że definicja nie wprowadza logiki do Android UI ani
  alternatywnego stanu runtime.

## Przykład definicji Kotlin

Poniższy przykład pokazuje kształt danych, a nie pełną implementację
konkretnych klas. Nazwy pól powinny zostać dopasowane do finalnych kontraktów
repozytorium.

```kotlin
val canyonLevel = MapDefinition(
    levelId = "canyon-01",
    schemaVersion = 1,
    worldWidth = 2_400,
    worldHeight = 1_200,
    terrain = TerrainProfile(
        sampleStep = 40,
        samples = listOf(
            TerrainSample(x = 0, height = 720),
            TerrainSample(x = 40, height = 735),
            TerrainSample(x = 80, height = 750),
            TerrainSample(x = 120, height = 742),
            TerrainSample(x = 160, height = 760),
            // ... kolejne próbki co 40 world units ...
            TerrainSample(x = 2_400, height = 780)
        )
    ),
    protectedTerrainZones = listOf(
        ProtectedTerrainZone(
            id = "blue-foundation-zone",
            minX = 80,
            maxX = 240,
            minHeight = 700,
            maxHeight = 840,
            craterPolicy = CraterPolicy.PRESERVE_FOUNDATION
        ),
        ProtectedTerrainZone(
            id = "red-foundation-zone",
            minX = 2_160,
            maxX = 2_320,
            minHeight = 700,
            maxHeight = 840,
            craterPolicy = CraterPolicy.PRESERVE_FOUNDATION
        )
    ),
    fortresses = listOf(
        FortressPlacement(
            owner = Team.BLUE,
            x = 160,
            foundationHeight = 760,
            assetKey = "fortress.blue.canyon",
            hitboxProfile = "fortress.default",
            protectedZoneId = "blue-foundation-zone"
        ),
        FortressPlacement(
            owner = Team.RED,
            x = 2_240,
            foundationHeight = 780,
            assetKey = "fortress.red.canyon",
            hitboxProfile = "fortress.default",
            protectedZoneId = "red-foundation-zone"
        )
    ),
    catapults = listOf(
        CatapultPlacement(
            owner = Team.BLUE,
            x = 280,
            foundationHeight = 760,
            facing = Facing.RIGHT,
            assetKey = "catapult.blue.default"
        ),
        CatapultPlacement(
            owner = Team.RED,
            x = 2_120,
            foundationHeight = 780,
            facing = Facing.LEFT,
            assetKey = "catapult.red.default"
        )
    ),
    objects = listOf(
        MapObjectPlacement(
            id = "canyon-rock-01",
            type = MapObjectType.DECORATION,
            x = 920,
            y = 690,
            scale = 1.0f,
            assetKey = "decoration.canyon.rock.large"
        ),
        MapObjectPlacement(
            id = "canyon-tree-01",
            type = MapObjectType.DECORATION,
            x = 1_520,
            y = 640,
            scale = 0.85f,
            assetKey = "decoration.canyon.tree"
        )
    ),
    initialCameraAnchor = InitialCameraAnchor(
        x = 160,
        y = 760,
        zoom = 1.0f
    ),
    generationMetadata = MapGenerationMetadata(
        generatorVersion = "level-authoring-1",
        seed = 20261011L,
        source = "campaign.canyon",
        author = "Urkaaaz level authoring"
    )
)
```

Powyższa definicja zachowuje rozdział między danymi poziomu a stanem meczu:
nie zawiera aktualnego zdrowia, aktywnych pocisków, kraterów ani postępu
gracza. Po przejściu `MapDefinitionValidator` może zostać użyta jako wejście
do utworzenia początkowego stanu deterministycznej symulacji.
