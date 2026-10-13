# Prompt: implementacja systemu reliktów

Zaimplementuj kompletny system reliktów dekoracyjnych w repozytorium Urkaaaz.

Pracuj autonomicznie i doprowadź zadanie do końca bez zatrzymywania się na
pytania do użytkownika. Podejmuj decyzje zgodne z `architecture.md`,
`AGENTS.md` oraz `docs/systems/relic-system.md`.

## Zakres

Obecnie implementuj wyłącznie relikty dekoracyjne `SMALL`.

Nie implementuj jeszcze:

- reliktów `MEDIUM`;
- reliktów `LARGE`;
- reliktów funkcjonalnych;
- nagród, odkrywania przez krater i persystencji;
- aktywacji reliktów przez gracza.

Relikty dekoracyjne:

- są częścią definicji konkretnego poziomu;
- nie wpływają na teren, fizykę, kolizje, obrażenia, zasoby, AI ani wynik;
- nie mają własnego runtime state;
- używają `MapObjectType.DECORATION` i `ObjectCollision.NONE`;
- są renderowane w world-space pod transformacją kamery;
- muszą być deterministyczne i zapisane w definicji poziomu.

## Architektura

Zachowaj odpowiedzialności modułów:

- `game-contracts` — niemutowalne kontrakty placementów i rozmiaru reliktu;
- `game-campaign` — authoring, materializacja i walidacja poziomów;
- `game-application` — przekazywanie danych mapy;
- `android-app` — mapowanie i rendering;
- `game-domain` i `game-simulation` — bez logiki dekoracyjnych reliktów.

Nie twórz drugiego równoległego systemu placementów. Wykorzystaj istniejący
`MapObjectPlacement`, rozszerzając go tylko wtedy, gdy jest to zgodne
wstecznie i architektonicznie uzasadnione.

Wprowadź typowaną reprezentację rozmiaru:

- `SMALL`;
- `MEDIUM`;
- `LARGE`.

W tym zadaniu używaj wyłącznie `SMALL`.

Każdy relikt musi mieć jawne:

- stabilne `id`;
- typ `MapObjectType.DECORATION`;
- rozmiar;
- `assetKey`;
- pozycję world-space;
- skalę;
- obrót;
- dane do obliczenia pełnego footprintu.

## Assety

Skopiuj zatwierdzone assety małych reliktów z legacy checkoutu:

`~/workspace/orc-catapult`

Nie modyfikuj legacy.

Odszukaj assety `SMALL` dla pięciu biomów:

1. Green Frontier;
2. Copperwood;
3. Frostbound Pass;
4. Ashen March;
5. Sunken Marshes.

W legacy nazwy i katalogi mogą się różnić. Użyj istniejącego katalogu lub
mapowania legacy jako źródła prawdy. Nie generuj nowych grafik i nie dodawaj
assetów `MEDIUM` ani `LARGE`.

Skopiuj assety do:

`android-app/src/main/res/drawable-nodpi/`

Zachowaj stabilne nazwy i utwórz jeden katalog mapowania assetów, zamiast
rozproszonych list w rendererze.

## Poziomy kampanii

Zaktualizuj wszystkie definicje poziomów rozdziałów 1–5, czyli łącznie
poziomy 1–10 w każdym z:

```text
game-campaign/src/main/kotlin/com/urkaaaz/campaign/chapter1/
game-campaign/src/main/kotlin/com/urkaaaz/campaign/chapter2/
game-campaign/src/main/kotlin/com/urkaaaz/campaign/chapter3/
game-campaign/src/main/kotlin/com/urkaaaz/campaign/chapter4/
game-campaign/src/main/kotlin/com/urkaaaz/campaign/chapter5/
```

Każdy poziom ma otrzymać deterministyczne placementy małych reliktów
odpowiednich dla biomu.

Relikty zapisuj bezpośrednio w odpowiednim `ChapterXLevelY.kt`. Nie losuj ich
podczas uruchamiania meczu.

Jeżeli potrzebujesz generatora:

- użyj seeda poziomu;
- generuj podczas authoringu;
- zapisz wynik w pliku poziomu;
- nie uruchamiaj generatora w `MatchSession`, `Match` ani
  `DeterministicMatchSimulation`.

Relikty nie mogą być umieszczone:

- na fundamentach fortec;
- w marginesie ochronnym fundamentów;
- na katapultach;
- w obrębie pełnego footprintu katapult;
- w chronionych strefach;
- poza światem;
- poza powierzchnią `soil`;
- na stromych zboczach;
- w Gameplay Corridor, jeżeli reguły poziomu tego zabraniają.

## Walidator

Rozszerz `MapDefinitionValidator`, aby sprawdzał pełny footprint reliktu po
zastosowaniu pozycji, skali i obrotu.

Odrzuć relikt, jeśli:

1. wychodzi poza granice świata;
2. wychodzi poza powierzchnię `soil`;
3. jest częściowo zawieszony nad pustą przestrzenią;
4. nachodzi na katapultę;
5. nachodzi na fundament lub strefę ochronną katapulty;
6. łamie reguły dekoracji;
7. ma pusty albo nieznany `assetKey`;
8. ma niepoprawny rozmiar;
9. narusza limit rozmiaru.

`allowOverlap` nie może umożliwiać nachodzenia na katapultę, fortecę,
fundament ani chronioną strefę.

Błędy walidacji muszą zawierać ścieżkę placementu i przyczynę odrzucenia.

## Rendering

Podłącz relikty do istniejącego przepływu:

```text
MapDefinition -> MatchSnapshot -> RenderState -> BattlefieldRenderer
```

Wykorzystaj istniejącą obsługę `mapObjects`, jeśli jest wystarczająca.

Relikty muszą:

- być renderowane w world-space;
- skalować się z kamerą i zoomem;
- zachowywać pozycję względem `soil`;
- nie mieć kolizji;
- nie wpływać na celowanie, pociski ani jednostki;
- nie być przycinane, przesuwane ani automatycznie skalowane przez renderer.

## Testy

Dodaj lub zaktualizuj testy dla:

- kontraktu `SMALL` i poprawnego `assetKey`;
- deterministycznego rozmieszczenia;
- wszystkich reguł footprintu i soil;
- nachodzenia na obie katapulty;
- fundamentów i chronionych stref;
- pustego, nieznanego i zduplikowanego placementu;
- obrotu i skali footprintu;
- wszystkich poziomów rozdziałów 1–5;
- mapowania do `RenderState`;
- renderowania assetów pięciu biomów;
- braku wpływu na fizykę i gameplay.

Wszystkie poziomy rozdziałów 1–5 muszą przejść walidację. W tym zadaniu nie
dodawaj reliktów `MEDIUM` ani `LARGE`.

## Dokumentacja

Zaktualizuj tylko bezpośrednio powiązaną dokumentację:

- `docs/systems/relic-system.md`;
- `docs/PROJECT_KNOWLEDGE.md`;
- `GLOSSARY.md`, jeśli nowe typy lub nazwy nie są jeszcze opisane.

Nie twórz ogólnych notatek ani planów. Dokumentuj wyłącznie faktycznie
zaimplementowane zachowanie.

## Walidacja końcowa

Uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Sprawdź również:

- brak zmian w `~/workspace/orc-catapult`;
- brak assetów `MEDIUM` i `LARGE`;
- wszystkie mapy rozdziałów 1–5 przechodzą walidację;
- brak losowania reliktów w runtime;
- brak Androidowych zależności w `game-contracts`, `game-domain`,
  `game-simulation` i `game-campaign`.

Nie twórz commita ani nie wykonuj pushowania, chyba że użytkownik wyda osobną
komendę `/push`.
