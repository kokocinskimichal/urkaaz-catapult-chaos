# Prompt implementacyjny — poprawki po audycie architektury

Wprowadź i zweryfikuj poprawki wynikające z audytu bieżącej architektury
Urkaaaz. Zaimplementuj zmiany w kodzie; nie kończ na planie ani samym opisie.

## Kontekst audytu

- Granice Gradle między kontraktami, domeną, symulacją, aplikacją i Androidem
  istnieją; moduły core nie importują Androida.
- W kilku miejscach drużyna jest rozpoznawana po tekście ID, zamiast przez
  jawne, typowane przypisanie.
- `DeterministicMatchSimulation.kt` ma ponad 4 tys. linii i skupia wiele
  odpowiedzialności; bieżące zmiany dodają tam także obsługę kolizji przeciwnych
  pocisków.
- Faktyczny graf zależności Gradle nie odpowiada w pełni
  `docs/architecture/project-map.md`.
- `MatchViewModel` korzysta bezpośrednio z portów logowania z
  `game-simulation`.
- W repozytorium pozostają minimalne klasy `game-domain/Match.kt` i
  `game-simulation/MatchSimulation.kt`, które mogą sugerować nieaktywną ścieżkę
  architektury.

## Ochrona bieżących zmian

Przed edycją sprawdź `git status` oraz diff. Zachowaj wszystkie zastane,
niezacommitowane zmiany i zintegruj je z poprawkami; nie cofaj, nie stashuj ani
nie nadpisuj zmian innych osób. W szczególności sprawdź bieżące zmiany w
`game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchEvents.kt`,
`game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt`
i `game-simulation/src/test/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulationTest.kt`.
Nie commituj ani nie pushuj.

## Zakres prac

### 1. Jawne, typowane przypisanie drużyny

- Przeszukaj produkcyjny kod pod kątem heurystyk takich jak
  `startsWith("red")`, `startsWith("blue")` i `contains("ai")`.
- Obejmij co najmniej `MatchSession`, `RenderStateMapper` i
  `SapperBombFlightClock`.
- Wykorzystaj istniejące przypisania drużyn w scenariuszu lub snapshotach.
  Jeśli ścieżka nie ma takiego przypisania, dodaj minimalną jawną
  granicę/konfigurację zamiast zgadywać po ID. Brak wymaganej informacji ma
  być obsłużony jawnie.
- Zachowaj zachowanie standardowych scenariuszy i dodaj testy z niestandardowymi
  ID, które potwierdzają, że drużyna wynika z przypisania, a nie z nazwy ID.

### 2. Wydzielenie kolizji przeciwnych pocisków

- Wydziel logikę kolizji przeciwnych pocisków z
  `DeterministicMatchSimulation` do spójnego komponentu należącego do
  `game-simulation`.
- Zachowaj deterministyczne porządkowanie kolizji, obsługę substepów oraz
  interakcję z istniejącą logiką lotu, apexu, terenu, fragmentacji i eventów.
- Nie zmieniaj reguł gameplayu ani zachowania istniejącej ścieżki bez kolizji,
  chyba że test wykaże błąd wynikający bezpośrednio z ekstrakcji.
- Dodaj lub dostosuj testy kolizji, deterministyczności i przypadków
  granicznych.
- Nie przenoś reguł symulacji do Androida ani warstwy aplikacyjnej.

### 3. Ograniczenie sprzężenia UI z logowaniem symulacji

- Usuń zależność `MatchViewModel` od typów `game-simulation` używanych
  wyłącznie do logowania/no-op.
- Ustal właściwe miejsce składania sinków w istniejącej architekturze.
  Adapter Androida może implementować port, ale ViewModel nie powinien
  zarządzać szczegółami infrastruktury logowania.
- Nie dodawaj nowego modułu ani równoległego kontraktu bez wykazanej potrzeby.
  Nie uzależniaj symulacji od Androida ani `game-application`.

### 4. Zgodność grafu zależności i jego kontrola

- Porównaj zależności wszystkich modułów z `settings.gradle.kts` i ich
  `build.gradle.kts`.
- Zaktualizuj `docs/architecture/project-map.md`, aby opisywał rzeczywisty,
  uzasadniony graf.
- Nie usuwaj poprawnych zależności tylko po to, aby dopasować kod do
  nieaktualnego diagramu. Usuń zależność wyłącznie po sprawdzeniu wszystkich
  konsumentów i potwierdzeniu, że nie jest potrzebna.
- Dodaj lekką automatyczną kontrolę granic architektury zgodną z obecnymi
  konwencjami testów, bez wprowadzania nowego narzędzia. Kontrola ma co
  najmniej blokować importy Androida/UI w core oraz wskazywać niedozwolone
  kierunki zależności.

### 5. Status klas `Match` i `MatchSimulation`

- Sprawdź wszystkie produkcyjne i testowe referencje.
- Jeśli klasy są nieużywanym szkieletem, usuń je wraz z nieaktualnymi testami
  albo jawnie oznacz ich rolę zgodnie z obecnym kierunkiem architektury.
- Wybierz najmniejszą bezpieczną zmianę; nie twórz drugiej ścieżki obsługi
  meczu.

## Zasady implementacji

- Przed edycją przeczytaj implementacje, testy i kontrakty dotyczące każdej
  zmiany.
- Zachowaj kierunek zależności, pojedynczego właściciela stanu gameplayu,
  komendy/eventy jako granice zmian stanu oraz niemutowalne snapshoty.
- Nie dodawaj cichych fallbacków, stringowej interpretacji identyfikatorów ani
  szerokich wyjątków.
- Aktualizuj bezpośrednio powiązaną dokumentację i testy.
- Nie wykonuj niezwiązanych refaktorów ani zmian balansu lub assetów.

## Kryteria akceptacji

- Produkcyjny kod nie wyznacza drużyny z tekstowej konwencji ID.
- Testy obejmują niestandardowe ID i jawne przypisania drużyn.
- Obsługa kolizji pocisków jest odseparowana od głównej klasy symulacji,
  a dotychczasowe testy regresyjne pozostają zielone.
- `MatchViewModel` nie importuje portów logowania z `game-simulation`.
- Dokumentacja grafu odpowiada zależnościom Gradle, a kontrola architektury jest
  uruchamiana przez istniejący zestaw testów.
- Status szkieletu `Match`/`MatchSimulation` jest jednoznaczny.
- Przed zakończeniem uruchom z katalogu głównego:

  ```bash
  ./gradlew test assembleDebug --quiet
  git diff --check
  git status --short
  ```

- Na koniec podaj zwięzłe podsumowanie zmian, testów oraz pozostałych
  ograniczeń. Nie commituj i nie pushuj.
