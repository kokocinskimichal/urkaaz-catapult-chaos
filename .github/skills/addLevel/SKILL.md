---
name: addLevel
description: Add a validated Urkaaaz campaign level with deterministic terrain, protected foundations, typed placements, registry wiring, tests, preview, and documentation.
argument-hint: "[optional chapter] [optional level number]"
---

# /addLevel — dodawanie poziomu kampanii

Używaj tego skilla, gdy użytkownik wywoła `/addLevel` albo poprosi o dodanie
nowego poziomu kampanii Urkaaaz. Skill prowadzi od opisu wysokopoziomowego do
zwalidowanej definicji poziomu, ale nie zapisuje implementacji bez wyraźnej
akceptacji użytkownika.

Pracuj bezpośrednio na bieżącej gałęzi `develop`. Nie twórz commita i nie
wykonuj push bez osobnego, wyraźnego polecenia użytkownika.

## Kontrakt rozmowy

Zadawaj pytania pojedynczo. Nigdy nie łącz kilku pytań w jednej wiadomości.
Jeżeli użytkownik podał informację wcześniej, zachowaj ją jako źródło prawdy i
nie pytaj o nią ponownie. Jeżeli argumenty `/addLevel` zawierają chapter lub
numer poziomu, przyjmij je i nie pytaj o te dane ponownie.

Najpierw podsumuj zakres, który rozumiesz, oraz wskaż brakujące dane. Nie
zapisuj kodu, plików poziomu, rejestru ani dokumentacji przed końcowym
potwierdzeniem użytkownika.

### Kolejność pytań

1. Zapytaj o wysokopoziomowy opis terenu: kształt, wysokości, przejścia,
   charakter mapy i oczekiwany gameplayowy rytm terenu.
2. Zapytaj o biom i jego język wizualny.
3. Zapytaj o dekoracje: drzewa, skały, propsy, ich rozmieszczenie i ewentualne
   obiekty funkcjonalne.
4. Zapytaj o chapter, jeżeli nie podano go jako argumentu.
5. Zapytaj o numer poziomu, jeżeli nie podano go jako argumentu.
6. Zapytaj o `initialCameraAnchor`: `PLAYER_FORTRESS`,
   `ENEMY_FORTRESS` albo `CENTER`.
7. Zapytaj o seed, `generatorVersion` i `authoringVersion`, jeżeli nie można
   ich ustalić z istniejącego standardu repozytorium.
8. Zapytaj o opcjonalne nadpisania balansu poziomu, wiatru lub innych
   istniejących konfiguracji. Nie pytaj o cele misji ani tutorial — ten skill
   ich nie generuje.
9. Podsumuj kompletny `LevelAuthoringRequest`, geometrię, placementy, asset
   keys, anchor i nadpisania.
10. Poproś o jednoznaczną akceptację planu przed zapisaniem zmian.

Jeżeli brakuje danych technicznych, dopytuj o jedną konkretną wartość naraz.
Nie wymyślaj potajemnie parametrów wpływających na gameplay. Wartości
proponowane przez agenta muszą zostać oznaczone jako propozycje i zaakceptowane
przed implementacją.

## Dane wejściowe

Minimalne dane wymagane do authoringu:

- wysokopoziomowy opis terenu;
- biom;
- chapter;
- numer poziomu;
- opis dekoracji;
- jawny `initialCameraAnchor`;
- seed;
- `generatorVersion`;
- `authoringVersion`.

Opcjonalne dane:

- nadpisania balansu, wyłącznie jeśli istniejący kontrakt je obsługuje;
- warianty assetów;
- dodatkowe jawne obiekty kolizyjne/gameplayowe;
- wymagania dotyczące początkowego kadrowania;
- notatki authoringu.

Nie przyjmuj jako danych definicji poziomu:

- bieżącego HP;
- aktywnych pocisków;
- runtime state jednostek;
- aktualnych kraterów;
- timerów;
- eventów;
- postępu gracza;
- celów misji;
- tutoriala.

Poziom nie zawiera maga. Nie dodawaj maga, placementu maga, jego assetu,
kontraktu ani placeholdera sugerującego obsługę maga.

## Obowiązkowe przygotowanie

Przed zmianami przeczytaj:

- `AGENTS.md`;
- `architecture.md`;
- `docs/PROJECT_KNOWLEDGE.md`;
- `LEVEL_DEFINITION_ARCHITECTURE.md`;
- `docs/prompts/IMPLEMENT_LEVEL_DEFINITION_SYSTEM.md`;
- `.github/skills/addUnit/SKILL.md`;
- `.github/skills/addBiome/SKILL.md`;
- istniejące kontrakty i testy `game-contracts`;
- istniejące definicje i testy `game-campaign`;
- istniejące modele i testy `game-simulation`;
- istniejący mapper render state i testy `android-app`.

Przeszukaj repozytorium pod kątem istniejących typów, katalogów assetów,
rejestrów, `CampaignLevelDefinition`, `MatchScenario`, snapshotów i
mechanizmów walidacji. Użyj istniejących typów i kontraktów. Nie kopiuj
`GameEngine`, legacy `MainActivity`, legacy `GameView` ani starej architektury.

## Twarde reguły świata i geometrii

### Standard świata i fundamenty

- Używaj wspólnego standardu świata istniejącego w repozytorium.
- Zachowaj stałe pozycje X fundamentów Demo; nie przesuwaj ich zależnie od
  losowania lub rendererowego heurystycznego dopasowania.
- Lewa i prawa forteca mogą mieć różne wysokości.
- Różnica wysokości fundamentów musi mieścić się w zakresie `±80 world units`
  względem obowiązującego standardu i sprawdzanego przejścia.
- Każdy fundament musi mieć minimum trzy próbki przejścia do terenu:
  próbkę przed, pod środkiem i za fundamentem.
- Fundament musi być wystarczająco płaski; walidator odrzuca zbyt strome lub
  niebezpieczne przejście.
- Fundamenty są całkowicie odporne na kratery. Ochrona musi być zapisana w
  `ProtectedTerrainZone` i respektowana przez symulację, nie tylko renderer.

### Profil terenu

- Profil materializuj jawnie w `TerrainProfile`.
- Próbki `TerrainSample` umieszczaj co dokładnie **40 world units**.
- Maksymalna różnica wysokości sąsiednich próbek wynosi **20 world units**.
- Próbki muszą być uporządkowane i pokrywać wymagany zakres mapy.
- Główna geometria poziomu nie może być losowana w runtime.
- Seed służy do deterministycznego authoringu/generowania danych, ale wynikowa
  geometria ma być jawnie zapisana w definicji.
- Ta sama definicja oraz te same metadane muszą dawać ten sam wynik.

### Dekoracje i propsy

Drzewa, skały i propsy zapisuj jako `MapObjectPlacement`. Domyślnie są
dekoracyjne i niekolizyjne. Kolizję albo funkcję gameplayową oznaczaj jawnie,
z użyciem istniejących typów.

Każdy placement dekoracji musi:

- mieć neutralny `assetKey`;
- mieć dolną kotwicę dopasowaną do terenu;
- nie wisieć nad terenem;
- nie zapadać się w teren;
- nie nakładać się na fundament, fortecę, katapultę ani inny obiekt;
- znajdować się poza fundamentem i poza marginesem **40 world units** od
  fundamentu;
- nie znajdować się na niedozwolonym stromym zboczu.

Nie naprawiaj błędnego placementu przez przesunięcie w UI. Błędna definicja ma
zostać odrzucona przez `MapDefinitionValidator`.

## Asset keys

Asset keys muszą być neutralne, stabilne i niezależne od Androida, np.:

```text
decoration.canyon.tree
decoration.canyon.rock.large
fortress.blue.canyon
catapult.red.default
```

W `game-contracts`, `game-campaign` i `game-simulation` nie wolno używać:

- `R.drawable`;
- Android resource IDs;
- bitmap;
- `Drawable`;
- ścieżek zależnych od Androida;
- importów Androida.

Brak asset key jest błędem `MissingAssetKey`. Nie stosuj automatycznych
fallbacków, losowych assetów ani cichych zamienników. Rozwiązanie neutralnego
klucza na bitmapę pozostaje odpowiedzialnością istniejącego katalogu assetów w
`android-app`.

## Architektura plików i modułów

### `game-contracts`

Dodaj lub rozszerz istniejące niemutowalne kontrakty:

- `MapDefinition`;
- `TerrainProfile`;
- `TerrainSample`;
- `ProtectedTerrainZone`;
- `FortressPlacement`;
- `CatapultPlacement`;
- `MapObjectPlacement`;
- `InitialCameraAnchor`;
- `MapGenerationMetadata`.

Kontrakty nie mogą zawierać runtime state, Androida, bitmap ani globalnego
mutable state.

### `game-campaign`

Utwórz osobny plik Kotlin dla każdego poziomu. Grupuj pliki według chapteru,
zachowując czytelne nazwy i stabilne identyfikatory.

Dodaj lub rozszerz `CampaignLevelRegistry`, który:

- rejestruje poziomy;
- wyszukuje poziom po identyfikatorze;
- integruje się z istniejącym `CampaignLevelDefinition`;
- przekazuje dane do istniejącego `MatchScenario`;
- podłącza biom;
- respektuje istniejące reguły odblokowań;
- udostępnia dane AI przez istniejący kontrakt;
- nie przechowuje runtime state;
- zwraca jawny błąd dla nieznanego poziomu.

Nie twórz drugiego systemu kampanii ani drugiego rejestru, jeżeli istniejący
mechanizm można rozszerzyć.

### `game-simulation`

Symulacja ma konsumować zwalidowaną definicję przez istniejące
`MatchScenario` lub właściwą istniejącą granicę aplikacyjną. Ma:

- utworzyć początkowy teren z jawnego profilu;
- utworzyć placementy bez mutowania `MapDefinition`;
- respektować `ProtectedTerrainZone`;
- zachować deterministyczność;
- nie losować głównej geometrii w runtime;
- nie znać Androida;
- nie zawierać logiki UI.

### `android-app`

Użyj istniejącego przepływu campaign level → match screen oraz istniejącego
mappera render state. Mapper:

- mapuje kontrakty na niemutowalny render state;
- rozwiązuje neutralne asset keys przez katalog assetów;
- mapuje world-space i `InitialCameraAnchor`;
- nie waliduje ani nie zmienia gameplayu;
- nie przesuwa obiektów w celu ukrycia błędnej definicji;
- nie tworzy drugiego snapshotu ani drugiej mapy.

## `InitialCameraAnchor`

Anchor musi być jawnie jednym z:

- `PLAYER_FORTRESS`;
- `ENEMY_FORTRESS`;
- `CENTER`.

Nie wprowadzaj pustego defaultu. Jeżeli anchor jest niepoprawny albo nie może
zostać rozwiązany w granicach mapy, zwróć `InvalidInitialCameraAnchor`.

## `MapGenerationMetadata` i deterministyczność

Każdy poziom musi jawnie posiadać:

- `seed`;
- `generatorVersion`;
- `authoringVersion`.

Nie korzystaj z bieżącego czasu, globalnego RNG ani ukrytego stanu procesu.
Seed i wersje służą do identyfikacji i reprodukcji authoringu. Główna geometria
ma być zapisana jawnie; runtime nie losuje profilu terenu.

## Walidator

Użyj lub dodaj `MapDefinitionValidator` z typowanym `MapValidationResult`.
Nie zwracaj wyłącznie `Boolean`. Walidator ma zwracać jawne, diagnostyczne
błędy wraz z identyfikatorem lub ścieżką elementu, którego dotyczą.

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

Dodaj także niezbędne błędy dla pustej definicji, błędnego zakresu świata,
nieprawidłowego kroku próbek, brakujących fortec, duplikatów placementów,
wyjścia poza mapę, nieprawidłowych stref ochronnych i niespójnych metadanych.

Walidator nie może:

- przesuwać obiektów;
- zmieniać wysokości;
- usuwać dekoracji;
- dodawać fallbackowych assetów;
- mutować wejścia.

## `LevelAuthoringRequest` i builder/factory

Użyj istniejącego lub dodaj typowany `LevelAuthoringRequest` obejmujący
minimum:

- `levelId`;
- nazwę;
- biom;
- chapter;
- numer poziomu;
- opis terenu;
- dekoracje;
- `initialCameraAnchor`;
- seed;
- `generatorVersion`;
- `authoringVersion`;
- opcjonalne nadpisania balansu.

Dodaj lub użyj `LevelDefinitionBuilder`/factory, który:

- buduje `MapDefinition`;
- materializuje profil terenu;
- ustawia placementy;
- nie ukrywa reguł walidacji;
- zwraca typowane błędy niekompletnego authoringu;
- jest testowalny bez Androida.

Builder nie może być God Objectem. Odpowiedzialności budowania, walidacji,
rejestracji, mapowania i symulacji muszą pozostać rozdzielone.

## Workflow implementacji

Wykonuj kroki w tej kolejności:

1. Odczytaj wymagane dokumenty, kontrakty i testy.
2. Przygotuj `LevelAuthoringRequest` na podstawie zaakceptowanego opisu.
3. Zidentyfikuj istniejące typy, registry, biom, odblokowania, AI i
   `MatchScenario`, które należy rozszerzyć.
4. Zaproponuj konkretne pliki do zmiany oraz podsumowanie danych poziomu.
5. Zatrzymaj się i poproś użytkownika o akceptację planu.
6. Po akceptacji dodaj lub rozszerz kontrakty w `game-contracts`.
7. Dodaj builder/factory i `MapDefinitionValidator`.
8. Utwórz osobny plik Kotlin poziomu w odpowiednim chapterze
   `game-campaign`.
9. Podłącz poziom do `CampaignLevelDefinition`, `CampaignLevelRegistry`,
   biomu, odblokowań, AI i `MatchScenario`.
10. Podłącz wejście do `game-simulation`, bez mutacji definicji i bez losowania
    głównej geometrii w runtime.
11. Podłącz mapper render state w `android-app`, bez gameplayu w UI.
12. Wygeneruj testy i dokumentację.
13. Uruchom testy modułowe i sprawdź preview.
14. Zatrzymaj się przed finalnym zapisaniem zmian, jeżeli użytkownik nie
    zaakceptował preview i kompletnego poziomu.
15. Po akceptacji wykonaj pełną bramkę walidacji, ale nadal nie commituj i nie
    pushuj bez osobnego polecenia.

Automatycznie podłącz biom, odblokowania, AI i testy tylko przez istniejące
kontrakty i rejestry. Nie generuj celów misji ani tutoriala. Jeśli ich
integracja jest potrzebna później, pozostaw jawny, udokumentowany punkt
rozszerzenia zamiast tworzyć ukryty fallback.

## Wymagane testy

### `game-contracts`

Dodaj testy:

- konstrukcji i niemutowalności `MapDefinition`;
- `TerrainProfile` i `TerrainSample`;
- trzech wariantów `InitialCameraAnchor`;
- metadanych seed/version;
- braku zależności od Androida;
- zgodności z `MatchScenario`.

### `game-campaign`

Dodaj testy:

- osobnego poziomu i stabilnego identyfikatora;
- `CampaignLevelRegistry`;
- integracji z `CampaignLevelDefinition`;
- biomu i reguł odblokowania;
- AI i utworzenia `MatchScenario`;
- deterministyczności;
- błędu nieznanego poziomu;
- wszystkich reguł `MapDefinitionValidator`.

### `game-simulation`

Dodaj testy:

- jawnego profilu co 40 world units;
- odrzucenia różnicy próbek większej niż 20;
- różnych wysokości fortec;
- limitu ±80 world units;
- minimum trzech próbek przejścia;
- odporności fundamentów na kratery;
- dopasowania dolnej kotwicy obiektu do terenu;
- zakazu niedozwolonych nakładań;
- braku mutacji `MapDefinition`;
- deterministycznej inicjalizacji.

### `android-app`

Dodaj testy:

- mapowania definicji na render state;
- neutralnych asset keys;
- braku `R.drawable` w kontraktach;
- trzech wartości `InitialCameraAnchor`;
- world-space dekoracji;
- przekazania campaign level do match screen;
- braku gameplayu w mapperze i UI;
- istniejących granic `MainActivity`, ViewModel i navigatora.

## Podgląd i akceptacja

Podgląd musi używać tej samej definicji, która trafi do
`MatchScenario`/match screen. Nie twórz uproszczonej, osobnej mapy preview.

Przed akceptacją sprawdź:

- fundamenty nie wiszą i nie zapadają się;
- lewa i prawa forteca mogą mieć różne wysokości w dozwolonym zakresie;
- przejścia fundamentów mają co najmniej trzy próbki;
- dekoracje nie leżą na fundamentach, stromych zboczach ani w marginesie 40;
- placementy nie nakładają się;
- asset keys są obecne i rozwiązywalne;
- anchor kamery pokazuje właściwy fragment mapy;
- symulacja respektuje odporność fundamentów na kratery;
- nie ma maga, celów misji ani tutoriala dodanych poza zakresem.

Po preview zadaj pojedyncze pytanie o akceptację. Bez odpowiedzi
akceptującej nie zapisuj zmian implementacyjnych.

## Zgodność z Clean Architecture

Skill musi wymuszać:

- prawidłowy kierunek zależności;
- brak Androida w `game-contracts`, `game-campaign`,
  `game-domain` i `game-simulation`;
- `MatchScenario` jako granicę przekazania danych do meczu;
- brak runtime state w `MapDefinition`;
- gameplay wyłącznie w domenie/symulacji;
- neutralne asset keys i brak resource IDs w core;
- brak równoległych systemów kampanii, terenu, assetów lub snapshotów;
- niemutowalne kontrakty i jawne błędy.

### Single Responsibility

Utrzymuj osobną odpowiedzialność dla:

- definicji poziomu — opis jednego poziomu kampanii;
- `MapDefinition` — niemutowalny opis geometrii i placementów;
- buildera/factory — budowanie definicji z requestu;
- `MapDefinitionValidator` — walidacja i błędy;
- `CampaignLevelRegistry` — rejestracja i wyszukiwanie poziomów;
- mappera Android — mapowanie kontraktów na render state;
- symulacji — utworzenie i zmiana runtime state.

Nie twórz klas, które jednocześnie authorują, walidują, rejestrują, renderują
i mutują symulację.

### Clean Code

- Używaj nazw domenowych.
- Pisz małe funkcje.
- Nie twórz God Objectów.
- Nie używaj globalnych singletonów z mutable state.
- Nie duplikuj typów, stałych ani reguł.
- Nie stosuj cichych fallbacków.
- Zwracaj jawne, typowane błędy.
- Zachowaj niemutowalność.
- Nie używaj `Any` ani niepotrzebnych castów.
- Reuse istniejące typy, katalogi, kontrakty i rejestry.
- Nie kopiuj `GameEngine`.
- Zachowaj testowalność bez Androida.

## Dokumentacja

Po implementacji, a przed końcowym review, zaktualizuj wyłącznie dokumentację
związaną ze zmianą:

- `LEVEL_DEFINITION_ARCHITECTURE.md`, jeśli kontrakt wymaga doprecyzowania;
- `docs/PROJECT_KNOWLEDGE.md`, aby opisać bieżący stan;
- odpowiedni raport/dokument etapu w `docs/`;
- ten skill, jeśli workflow authoringu wymaga utrwalenia nowej reguły.

Nie kopiuj całego kodu do dokumentacji. Opisz właścicieli odpowiedzialności,
kierunek przepływu, kontrakty, walidację, testy i ograniczenia.

## Końcowy review

Przed zakończeniem ręcznie sprawdź:

- dependency direction;
- Single Responsibility;
- brak gameplayu w UI;
- brak Androida w core;
- brak duplikacji;
- niemutowalność;
- jawne błędy i brak cichych fallbacków;
- brak `Any` i niepotrzebnych castów;
- deterministyczność;
- testowalność;
- brak runtime state w `MapDefinition`;
- `MatchScenario` jako granicę meczu;
- reuse istniejących typów, katalogów, kontraktów i registry;
- brak maga;
- brak generowania celów misji i tutoriala;
- brak Android resource IDs w modelach core;
- zgodność z `architecture.md` i `LEVEL_DEFINITION_ARCHITECTURE.md`.

## Końcowa walidacja

Z katalogu głównego repozytorium uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Nie twórz commita i nie wykonuj push. W końcowej odpowiedzi podaj:

- dokładną ścieżkę skilla;
- listę zmienionych plików;
- wynik testów i builda;
- wynik `git diff --check`;
- potwierdzenie gałęzi `develop`;
- potwierdzenie, że nie wykonano commita ani push.
