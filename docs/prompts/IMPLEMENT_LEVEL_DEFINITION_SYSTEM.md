# Prompt implementacyjny: Level Definition System

## Cel

Zaimplementuj kompletny system definicji i przechowywania poziomów Urkaaaz
zgodnie z dokumentem `docs/architecture/level-definition-architecture.md`.

System ma dostarczać niemutowalne, deterministyczne i walidowalne definicje
poziomów kampanii. Definicja poziomu ma opisywać bazowy teren, fundamenty,
fortece, katapulty, dekoracje, początkowy anchor kamery i metadane generowania.
Ma być wejściem do `MatchScenario` oraz do początkowego stanu
`game-simulation`, ale nie może zawierać runtime state meczu.

Pracuj bezpośrednio na gałęzi `develop`. Nie twórz commita i nie wykonuj push.

## Obowiązkowe przygotowanie

Przed zmianami przeczytaj:

- `AGENTS.md`;
- `docs/architecture/architecture.md`;
- `docs/project/knowledge.md`;
- `docs/architecture/level-definition-architecture.md`;
- istniejące kontrakty w `game-contracts`;
- istniejące definicje i testy `game-campaign`;
- istniejące modele, snapshoty i testy `game-simulation`;
- istniejące mapowanie render state i testy `android-app`.

Przeszukaj repozytorium pod kątem istniejących typów i mechanizmów kampanii,
terenu, assetów, snapshotów i scenariuszy. Wykorzystaj istniejące rozwiązania
zamiast tworzyć drugi system. Nie kopiuj `GameEngine`, starej architektury ani
plików legacy do nowego projektu.

## Architektura i kierunek zależności

Zachowaj kierunek zależności:

```text
android-app
  → game-application
  → game-campaign
  → game-contracts

game-simulation
  → game-domain
  → game-contracts

game-campaign
  → game-contracts
  → istniejące kontrakty scenariuszy
```

Nie wprowadzaj zależności core od Androida. `game-contracts`,
`game-campaign`, `game-domain` i `game-simulation` nie mogą importować
`android.*`, `androidx.*`, `Context`, `View`, `Canvas`, bitmap ani
`R.drawable`.

`MatchScenario` pozostaje granicą pomiędzy definicją kampanii a uruchamianym
meczem. `MapDefinition` opisuje dane poziomu, a `MatchScenario` opisuje
scenariusz przekazywany do sesji i symulacji. Nie umieszczaj w
`MapDefinition` bieżącego HP, aktywnych pocisków, kraterów, timerów,
aktualnych jednostek, eventów ani innego runtime state.

Nie twórz równoległego subsystemu kampanii, terenu, assetów ani snapshotów.
Rozszerz istniejące typy i mechanizmy albo wyodrębnij współdzielony kontrakt,
jeżeli obecna struktura nie pozwala na poprawną integrację.

## Kontrakty `game-contracts`

Dodaj lub uzupełnij niemutowalne kontrakty:

- `MapDefinition`;
- `TerrainProfile`;
- `TerrainSample`;
- `ProtectedTerrainZone`;
- `FortressPlacement`;
- `CatapultPlacement`;
- `MapObjectPlacement`;
- `InitialCameraAnchor`;
- `MapGenerationMetadata`.

Kontrakty muszą:

- być niemutowalne;
- używać typowanych wartości domenowych;
- nie znać Androida;
- nie przechowywać bitmap, `Drawable`, `R.drawable` ani ścieżek zasobów
  Androida;
- nie zawierać niejawnych singletonów ani globalnego mutable state;
- umożliwiać deterministyczne odtworzenie poziomu;
- być zgodne z istniejącym stylem kontraktów i snapshotów repozytorium.

Nie używaj `Any`, jeżeli można zastosować konkretny typ. Nie dodawaj
niepotrzebnych `as`, `as?`, `as Any` ani `as unknown as`.

### `MapDefinition`

`MapDefinition` powinien zawierać co najmniej:

- stabilny `levelId`;
- wersję schematu;
- rozmiary świata;
- `TerrainProfile`;
- listę `ProtectedTerrainZone`;
- placementy fortec;
- placementy katapult;
- placementy obiektów mapy;
- `InitialCameraAnchor`;
- `MapGenerationMetadata`.

Definicja ma być opisem poziomu, nie mutable obiektem runtime.

### `TerrainProfile` i `TerrainSample`

Profil terenu:

- używa próbek co dokładnie **40 world units**;
- ma uporządkowane próbki;
- pokrywa wymagany zakres świata;
- nie pozwala na różnicę wysokości sąsiednich próbek większą niż **200 world
  units**;
- ma deterministyczną interpolację lub jawnie określony sposób odczytu
  wysokości.

Waliduj brak próbek, nieciągłość osi X, nieprawidłowy krok, wyjście poza
granice mapy oraz przekroczenie limitu nachylenia.

### Fundamenty

Zaimplementuj reguły fundamentów:

- zachowaj stałe zakresy X Demo zgodnie z uzgodnioną definicją poziomu;
- dopuść różne wysokości fundamentów fortec;
- zachowaj tolerancję **±80 world units** wokół fundamentu;
- wymagaj minimum **3 próbek przejścia**: przed fundamentem, pod środkiem
  fundamentu i za fundamentem;
- odrzuć fundament, jeżeli przejście jest za krótkie, zbyt strome albo
  niebezpieczne;
- zapewnij całkowitą odporność obszaru fundamentu na kratery zgodnie z
  `ProtectedTerrainZone`;
- zachowaj margines dekoracji **40 world units** od fundamentu.

Różne wysokości fortec są zamierzone. Nie normalizuj obu fundamentów do jednej
wysokości i nie zakładaj płaskiej mapy. Wymagaj jawnego potwierdzenia, że
fundament jest wystarczająco płaski w swoim dozwolonym obszarze.

W systemie nie ma maga. Nie dodawaj placementu maga, kontraktu maga,
specjalnego assetu maga ani placeholdera, który sugeruje jego obsługę.

### `ProtectedTerrainZone`

Strefa ochronna ma opisywać obszar zabezpieczony przed niedozwoloną deformacją.
Powinna być powiązana z fundamentem lub innym wymagającym obszarem i mieć
jawne granice oraz politykę kraterów. Symulacja ma respektować tę strefę;
renderer nie może udawać ochrony wyłącznie wizualnym warunkiem.

### `FortressPlacement`

Placement fortecy musi jawnie opisywać:

- właściciela/stronę;
- pozycję;
- wysokość fundamentu;
- rozmiar lub profil zajmowanego obszaru;
- neutralny `assetKey`;
- profil hitboxa albo istniejący identyfikator kolizji;
- powiązaną strefę ochronną.

Waliduj wymagane fortece, duplikaty właściciela, zakres świata, zgodność z
profilem terenu, fundamentem i strefą ochronną.

### `CatapultPlacement`

Placement katapulty musi jawnie opisywać:

- właściciela/stronę;
- pozycję;
- wysokość fundamentu;
- orientację;
- neutralny `assetKey`;
- obszar fundamentu lub powiązaną strefę ochronną.

Waliduj, że katapulta znajduje się na fundamencie i w dozwolonym zakresie.
Błąd ma być typowany jako `CatapultOutsideFoundation`, a nie ukrywany przez
przesunięcie obiektu w rendererze.

### `MapObjectPlacement`

Drzewa, skały i propsy muszą być reprezentowane przez `MapObjectPlacement`.

Domyślne zasady:

- obiekt jest dekoracyjny;
- kolizja jest wyłączona, dopóki nie zostanie jawnie włączona;
- typ obiektu, pozycja, warstwa, skala, obrót i `assetKey` są jawne;
- pozycja wizualna jest jawna i mieści się w granicach świata; nie musi
  odpowiadać wysokości terenu ani płaskiemu podłożu;
- obiekt nie może nakładać się na fundament, fortecę, katapultę ani inny
  obiekt, chyba że kontrakt jawnie zezwala na takie nakładanie;
- dekoracja nie może znajdować się w marginesie 40 world units od fundamentu;
- obiekt z kolizją musi mieć jawny profil kolizji.

Nie dodawaj specjalnych wyjątków w `BattlefieldView`, aby ukryć błędny
placement. Błędna mapa ma zostać odrzucona przez walidator.

### Asset keys

Używaj neutralnych, stabilnych `assetKey`, np.:

```text
decoration.canyon.tree
decoration.canyon.rock.large
fortress.blue.canyon
catapult.red.default
```

Nie przechowuj Android resource IDs, `R.drawable`, nazw klas drawable ani
ścieżek zależnych od Androida w modelach kontraktów.

Brak klucza ma być jawnym błędem `MissingAssetKey`. Nie stosuj cichego
fallbacku do losowego assetu. Mapowanie neutralnego klucza do bitmapy pozostaje
odpowiedzialnością `android-app` i istniejącego katalogu assetów.

### `InitialCameraAnchor`

`InitialCameraAnchor` musi obsługiwać trzy typowane warianty:

- `PLAYER_FORTRESS`;
- `ENEMY_FORTRESS`;
- `CENTER`.

Możesz przechowywać dodatkowe parametry, takie jak zoom lub offset, ale anchor
nie może mutować stanu symulacji. Waliduj, że wybrany anchor jest poprawny
dla definicji i że pozycja wyprowadzona z mapy mieści się w jej granicach.
Błędny wariant lub brak wymaganego celu zgłaszaj jako
`InvalidInitialCameraAnchor`.

### `MapGenerationMetadata`

Metadane muszą przechowywać co najmniej:

- `seed`;
- `generatorVersion`;
- `authoringVersion`;
- źródło lub identyfikator authoringu;
- opcjonalny fingerprint/checksumę.

Seed, wersja generatora i wersja authoringu muszą zapewniać deterministyczność.
Ta sama definicja i te same metadane muszą dawać ten sam profil oraz te same
placementy. Nie używaj bieżącego czasu, globalnego RNG ani przypadkowego
stanu procesu.

## Walidator

Zaimplementuj `MapDefinitionValidator` w odpowiednim module core, zgodnie z
istniejącym kierunkiem zależności. Walidator ma zwracać typowany
`MapValidationResult`, zawierający listę typowanych błędów i kontekst
diagnostyczny, jeżeli jest potrzebny.

Wymagane błędy:

- `TerrainSlopeExceeded`;
- `FoundationNotFlat`;
- `FoundationOutsideAllowedHeight`;
- `FoundationTransitionTooShort`;
- `DecorationOnFoundation`;
- `DecorationOnSteepSlope`;
- `ObjectOverlapsAnotherObject`;
- `MissingAssetKey`;
- `CatapultOutsideFoundation`;
- `InvalidInitialCameraAnchor`.

Dodaj również błędy dla pozostałych nieprawidłowości, jeżeli są potrzebne do
pełnej walidacji, w szczególności:

- pustej definicji;
- nieprawidłowego zakresu świata;
- złego kroku próbek;
- brakujących wymaganych fortec;
- duplikatów identyfikatorów;
- wyjścia placementu poza mapę;
- nieprawidłowej strefy chronionej;
- niespójnych metadanych deterministyczności.

Walidator nie może:

- przesuwać błędnych obiektów;
- zmieniać wysokości fundamentów;
- usuwać dekoracji;
- zastępować brakujących asset keys;
- mutować wejściowej `MapDefinition`.

Każdy błąd powinien wskazywać element, ścieżkę lub identyfikator, którego
dotyczy. Nie zwracaj wyłącznie `false`, jeżeli można zwrócić konkretny błąd
domenowy.

## Kampania i przechowywanie poziomów

Utwórz osobny plik Kotlin dla każdego poziomu w `game-campaign`. Nie
przechowuj wszystkich poziomów w jednym God Object ani w ogromnej tablicy
anonimowych danych.

Dodaj `CampaignLevelRegistry`, który:

- rejestruje dostępne poziomy;
- zwraca poziom po stabilnym identyfikatorze;
- integruje się z istniejącym `CampaignLevelDefinition`;
- tworzy lub dostarcza istniejący `MatchScenario`;
- nie przechowuje runtime state;
- zwraca jawny błąd dla nieznanego poziomu;
- nie duplikuje logiki odblokowywania, jeżeli istnieje już odpowiedni
  mechanizm kampanii.

Zachowaj integrację z istniejącym `CampaignLevelDefinition` i `MatchScenario`.
Nie twórz alternatywnego modelu poziomu używanego tylko przez nową ścieżkę.
Jeżeli istniejące typy wymagają rozszerzenia, zrób to kompatybilnie i dodaj
testy kontraktów.

## `LevelAuthoringRequest` i builder/factory

Dodaj `LevelAuthoringRequest` jako typowane wejście procesu authoringu.
Request ma opisywać intencję autora, np.:

- `levelId`;
- nazwę i biom;
- rozmiar świata;
- wymagane strony;
- oczekiwane dekoracje;
- początkowy anchor kamery;
- seed;
- wersję authoringu;
- notatki lub ograniczenia.

Nie umieszczaj w request runtime state, aktywnych pocisków, bieżącego HP,
kraterów ani mutable obiektów domeny.

Dodaj `LevelDefinitionBuilder` albo factory, która:

- buduje `MapDefinition` z `LevelAuthoringRequest`;
- wymaga kompletnych danych;
- nie ukrywa reguł walidacji;
- używa istniejących typów domenowych;
- zwraca jawny wynik/błąd dla niekompletnego authoringu;
- pozostaje testowalna bez Androida.

Builder nie może być God Objectem. Jeżeli potrzebne są osobne małe buildery
dla terenu, placementów lub dekoracji, zachowaj jeden jasny publiczny
przepływ i nie twórz równoległego API kampanii.

## Skill `/addLevel`

Zaktualizuj lub dodaj skill `/addLevel`, jeżeli repozytorium posiada odpowiedni
mechanizm skillów. Skill ma prowadzić autora przez:

1. odczyt zasad i istniejących kontraktów;
2. utworzenie `LevelAuthoringRequest`;
3. zbudowanie `MapDefinition`;
4. wybór próbek terenu co 40 world units;
5. ustawienie fundamentów i trzech próbek przejścia;
6. ochronę fundamentów przed kraterami;
7. dodanie fortec, katapult i dekoracji;
8. przypisanie neutralnych asset keys;
9. uruchomienie `MapDefinitionValidator`;
10. dodanie testów;
11. build;
12. preview;
13. akceptację.

Skill nie może instruować kopiowania `GameEngine`, dodawania gameplayu do UI
ani omijania walidatora.

## Integracja z symulacją i Androidem

### `game-simulation`

Podłącz `MapDefinition` do tworzenia początkowego terenu i rozmieszczenia
obiektów przez istniejący `MatchScenario` lub właściwy factory aplikacyjny.

Wymagania:

- symulacja konsumuje zwalidowaną definicję;
- nie mutuje definicji;
- stosuje `ProtectedTerrainZone` przy deformacji terenu;
- zachowuje deterministyczność;
- nie zawiera kodu Androida;
- nie duplikuje mapy w drugim modelu;
- ma testy początkowego stanu oraz odporności fundamentów na kratery.

### `android-app`

Podłącz definicję do istniejącego przepływu campaign level → match screen.
Użyj istniejącego mappera render state albo rozszerz go, zamiast tworzyć drugi
snapshot/render model.

Mapper Android:

- mapuje niemutowalne dane na render state;
- rozwiązuje neutralne asset keys przez istniejący katalog assetów;
- korzysta z world-space i transformacji kamery;
- mapuje `InitialCameraAnchor` na początkowy widok;
- nie zawiera reguł obrażeń, kraterów, kolizji ani walidacji gameplayu;
- nie przesuwa błędnych placementów;
- nie tworzy alternatywnej definicji mapy.

Żaden gameplay nie może trafić do `BattlefieldView`, `MatchScreenController`
ani mappera Android.

## Testy

Dodaj lub uzupełnij testy w każdym wymaganym module.

### `game-contracts`

Testuj:

- niemutowalność i kompletność kontraktów;
- konstrukcję `MapDefinition`;
- typowane warianty `InitialCameraAnchor`;
- metadane seed/generatorVersion/authoringVersion;
- brak zależności od Androida;
- zgodność z istniejącym `MatchScenario`.

### `game-campaign`

Testuj:

- każdy poziom jako osobną definicję;
- `CampaignLevelRegistry`;
- integrację z `CampaignLevelDefinition`;
- utworzenie `MatchScenario`;
- deterministyczność tych samych danych;
- poprawny błąd nieznanego poziomu;
- przejście przez `MapDefinitionValidator`.

### `game-simulation`

Testuj:

- utworzenie początkowego terenu z profilu;
- różne wysokości fundamentów;
- minimum trzy próbki przejścia;
- limit ±80 world units;
- ochronę fundamentów przed kraterami;
- brak mutacji `MapDefinition`;
- deterministyczność inicjalizacji;
- respektowanie jawnych kolizji obiektów.

### `android-app`

Testuj:

- mapowanie definicji na render state;
- neutralne asset keys zamiast resource IDs w kontraktach;
- `InitialCameraAnchor` dla `PLAYER_FORTRESS`, `ENEMY_FORTRESS` i `CENTER`;
- renderowanie dekoracji w world-space;
- przekazanie campaign level do match screen;
- brak gameplayu w mapperze i UI;
- istniejące granice `MainActivity`, ViewModel i navigatora.

## Dokumentacja

Zaktualizuj dokumentację bez kopiowania całej implementacji:

- `docs/architecture/level-definition-architecture.md` — jeśli kontrakty lub reguły wymagają
  doprecyzowania;
- `docs/project/knowledge.md` — bieżący stan wdrożenia;
- odpowiedni dokument `docs/` dla etapu migracji;
- dokumentację skilla `/addLevel`, jeżeli został dodany lub zmieniony.

Dokumentacja ma wskazywać właściciela odpowiedzialności, kierunek zależności,
kontrakt danych, sposób walidacji i sposób samodzielnego odtworzenia testów.

## Obowiązkowa zgodność implementacji ze skillem `/addLevel`

Skill `/addLevel` jest narzędziem deweloperskim, a nie częścią runtime systemu
poziomów. Mimo tego musi korzystać z dokładnie tych samych produkcyjnych
kontraktów, typów i reguł, aby nie powstał drugi, rozbieżny sposób authoringu.

Po implementacji systemu wymagaj, aby:

- skill `/addLevel` dodawał poziomy zgodnie z dokładnie tymi samymi
  kontraktami, typami i regułami co kod produkcyjny;
- skill używał istniejących `LevelAuthoringRequest`,
  `LevelDefinitionBuilder`, `MapDefinitionValidator`,
  `CampaignLevelDefinition`, `CampaignLevelRegistry` i `MatchScenario`;
- skill nie tworzył własnych, równoległych modeli definicji, builderów,
  validatorów, registry, scenariuszy ani snapshotów;
- każda twarda reguła z `docs/architecture/level-definition-architecture.md` była sprawdzana
  zarówno przez runtime validator, jak i przez workflow skilla;
- skill nie generował kodu, który omija validator, wpisuje dane bezpośrednio do
  symulacji albo obchodzi granice Clean Architecture;
- skill zatrzymywał się przy brakujących asset keys, nieprawidłowym terenie,
  fundamentach, nachyleniu, placementach, kolizjach lub złej kamerze;
- przykładowy poziom wygenerowany przez skill przechodził te same testy
  kontraktowe i walidacyjne co ręcznie utworzona definicja;
- każda zmiana kontraktów lub reguł validatora wymagała aktualizacji skilla
  oraz testu zgodności skilla z implementacją;
- został dodany test albo fixture zgodności, który sprawdza, że `/addLevel`
  generuje definicję akceptowaną przez produkcyjny validator i poprawnie
  rejestrowaną w kampanii;
- dokumentacja jasno rozróżnia skill jako narzędzie deweloperskie od runtime
  systemu poziomów, ale wymaga wspólnego źródła prawdy dla typów i reguł.

Test zgodności powinien przejść przez produkcyjny builder, produkcyjny
validator, produkcyjny registry i istniejący `MatchScenario`. Nie wolno
testować skilla wyłącznie przez porównanie tekstu promptu ani przez
równoległy mock, który nie korzysta z produkcyjnych kontraktów.

## Obowiązkowa zgodność z Clean Architecture

Przed zakończeniem sprawdź wszystkie punkty:

- kierunek zależności jest zachowany;
- `MatchScenario` jest granicą między kampanią i uruchomieniem meczu;
- `MapDefinition` nie zawiera runtime state;
- core nie importuje Androida;
- nie powstały równoległe subsystemy kampanii, terenu, assetów ani
  snapshotów;
- definicje poziomów są niemutowalne;
- gameplay pozostaje w domenie/symulacji;
- Android wyłącznie prezentuje i wysyła akcje/komendy;
- asset keys pozostają neutralne;
- walidacja jest typowana i jawna;
- błędna definicja nie jest naprawiana po cichu przez UI.

## Single Responsibility

Utrzymaj jedną odpowiedzialność dla każdego elementu:

- `LevelDefinition` — reprezentuje jeden poziom kampanii i jego publiczny
  kontrakt;
- `MapDefinition` — przechowuje niemutowalny opis geometrii i placementów;
- `LevelDefinitionBuilder` — buduje definicję z authoring requestu;
- `MapDefinitionValidator` — sprawdza poprawność definicji i zwraca błędy;
- `CampaignLevelRegistry` — rejestruje i wyszukuje poziomy oraz integruje je
  z istniejącym campaign API;
- mapper Android — mapuje kontrakty na render state i assety prezentacyjne;
- `game-simulation` — tworzy i zmienia runtime state na podstawie
  zwalidowanej definicji.

Nie dodawaj klas, które jednocześnie budują poziom, walidują go, rejestrują,
renderują i zmieniają stan symulacji.

## Clean Code

Obowiązują następujące reguły:

- używaj nazw domenowych, jednoznacznych i stabilnych;
- pisz małe funkcje o jednej odpowiedzialności;
- nie twórz God Objectów;
- nie używaj globalnych singletonów z mutable state;
- nie duplikuj reguł terenu, fundamentów, assetów ani snapshotów;
- błędy muszą być jawne i typowane;
- zachowaj niemutowalność;
- używaj typowanych kontraktów zamiast map i `Any`;
- nie używaj niepotrzebnych castów;
- nie ukrywaj błędów przez domyślne wartości;
- preferuj istniejące helpery i mechanizmy;
- zachowaj testowalność bez Androida;
- dodawaj komentarze tylko dla nieoczywistych decyzji.

## Końcowy review

Przed zakończeniem wykonaj ręczny review:

- dependency direction;
- Single Responsibility;
- brak gameplayu w UI;
- brak Androida w core;
- brak duplikacji;
- niemutowalność;
- obsługa błędów;
- typowanie kontraktów;
- deterministyczność;
- testowalność;
- zgodność z `docs/architecture/architecture.md`;
- zgodność z `docs/architecture/level-definition-architecture.md`;
- wykorzystanie istniejących typów i mechanizmów;
- brak kopiowania `GameEngine`;
- brak maga;
- brak resource IDs Androida w modelach.
- skill `/addLevel` generuje poziom przechodzący przez produkcyjny builder,
  validator, registry i `MatchScenario` bez obejść.

## Końcowa walidacja

Z katalogu głównego repozytorium uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Nie twórz commita i nie wykonuj push. W raporcie końcowym podaj zmienione
pliki, wynik testów, wynik `git diff --check` oraz informację, że praca
pozostała na gałęzi `develop`.
