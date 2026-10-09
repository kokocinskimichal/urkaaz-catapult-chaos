# Plan migracji Urkaaaz do nowego projektu

## 1. Cel migracji

Migration dotyczy utworzenia nowego projektu w:

```text
~/workspace/urkaaz
```

Nowy projekt ma zostać zbudowany według `architecture.md`, a następnie
uzupełniony logiką gry wybraną ze starego projektu.

Nie przenosimy starego projektu plik po pliku. Przenosimy:

- zachowanie gry;
- reguły domenowe;
- algorytmy symulacji;
- konfigurację gameplayu;
- potrzebne dane kampanii;
- wybrane testy po dostosowaniu do nowych granic;
- assety po inwentaryzacji i walidacji.

Nie przenosimy automatycznie:

- `GameEngine` jako jednej klasy;
- `MainActivity` jako kontrolera całej aplikacji;
- `GameView` jako połączenia inputu, pętli gry i renderingu;
- `RenderContext` jako mostu do UI;
- starych dispatcherów i aliasów;
- publicznych mutowalnych kolekcji;
- kodu Androidowego, który miesza lifecycle z gameplayem;
- nieużywanych, zduplikowanych albo historycznych assetów.

---

## 2. Zasady migracji

### 2.1. Stary projekt jest źródłem referencyjnym

Stary projekt pozostaje niezmieniony i służy jako:

- źródło kodu do analizy;
- źródło assetów;
- źródło konfiguracji;
- punkt odniesienia dla zachowania;
- źródło testów regresyjnych.

Nie należy wykonywać refaktoru starego projektu równolegle z migracją do nowego.

### 2.2. Migracja funkcjonalna, nie plikowa

Każdy przenoszony fragment należy sklasyfikować jako:

| Kategoria | Działanie |
|---|---|
| Reguła domenowa | przepisać do `domain` |
| Algorytm symulacji | przepisać do `simulation` |
| Przypadek użycia | przepisać do `application` |
| Kontrakt danych | zdefiniować w `contracts` |
| UI Androida | odtworzyć w nowym UI |
| Adapter platformy | napisać ponownie w `platform` |
| Kod łączący stare mechanizmy | odrzucić |
| Kod nieużywany | nie przenosić |

### 2.3. Jeden etap = jedna bramka

Etap jest zakończony dopiero wtedy, gdy:

- kod kompiluje się;
- testy właściwe dla etapu przechodzą;
- nie ma nieopisanych zależności wstecz;
- nie skopiowano starego mechanizmu tylko dlatego, że był wygodny;
- assety mają właściciela i dokumentację;
- można wskazać, co zostało przeniesione i czego celowo nie przeniesiono.

Zielony build jest bramką do następnego etapu, a nie definicją ukończenia
migracji.

---

## 3. Protokół weryfikacji przez użytkownika

Każdy etap migracji musi być możliwy do sprawdzenia niezależnie od kolejnych
etapów. Nie przechodzimy dalej tylko dlatego, że projekt się kompiluje.

### 3.1. Obowiązkowy pakiet odbiorczy etapu

Po zakończeniu każdego etapu należy przygotować jeden pakiet odbiorczy
zawierający:

```text
stage-report/
├── README.md
├── changed-files.txt
├── commands.txt
├── test-results.txt
├── manual-checklist.md
├── screenshots/
├── logs/
└── decisions.md
```

Pakiet może być zapisany w katalogu artefaktów sesji albo w repozytorium, jeśli
zostanie to uzgodnione. Nie należy tworzyć przypadkowych plików raportowych
w kodzie aplikacji.

### 3.2. Zawartość `README.md`

Raport etapu musi zawierać:

- nazwę etapu;
- cel;
- zakres wykonanych zmian;
- zakres celowo niewykonany;
- listę nowych i usuniętych komponentów;
- listę zmienionych pakietów;
- zależności od poprzednich etapów;
- znane ograniczenia;
- sposób uruchomienia demonstracji;
- kryterium akceptacji.

### 3.3. Dowody techniczne

Każdy etap musi zawierać:

- wynik kompilacji właściwego modułu;
- wynik testów właściwych dla etapu;
- `git diff --check`;
- listę zmienionych plików;
- wynik testów architektonicznych, jeśli etap zmienia granice pakietów;
- wynik wyszukiwań potwierdzających usunięcie starej ścieżki;
- informację o liczbie testów;
- informację o ostrzeżeniach, jeśli występują.

Przykładowy zapis `commands.txt`:

```text
./gradlew :game-domain:test
./gradlew :game-simulation:test
./gradlew :android-app:assembleDebug
git diff --check
```

### 3.4. Scenariusz ręczny

Każdy etap musi mieć scenariusz, który użytkownik może wykonać samodzielnie.
Scenariusz powinien określać:

1. jak uruchomić aplikację albo test;
2. jakie dane wejściowe podać;
3. co dokładnie powinno pojawić się na ekranie lub w logu;
4. jaki wynik oznacza sukces;
5. jaki wynik oznacza regresję.

Dla etapów bez UI scenariuszem może być:

- uruchomienie konkretnego testu;
- uruchomienie małego programu demonstracyjnego;
- odczyt snapshotu;
- porównanie pliku wynikowego;
- porównanie eventów;
- sprawdzenie deterministycznego replaya.

### 3.5. Artefakty wizualne

Etapy obejmujące UI, rendering albo assety muszą dodatkowo dostarczyć:

- screenshot przed zmianą, jeśli jest dostępny;
- screenshot po zmianie;
- screenshot na docelowej rozdzielczości;
- screenshot dla stanu brzegowego;
- krótki opis różnic;
- kontakt sheet lub animację dla sprite sheetów;
- informację, czy asset jest poprawnie przezroczysty.

Nie wystarczy informacja „renderowanie działa”.

### 3.6. Odbiór etapu

Etap ma trzy stany:

```text
NOT_STARTED
READY_FOR_REVIEW
ACCEPTED
```

Przejście `READY_FOR_REVIEW → ACCEPTED` wymaga:

- przejścia bramki technicznej;
- przejścia scenariusza ręcznego;
- sprawdzenia artefaktów przez użytkownika;
- zapisania decyzji odbioru.

Jeżeli użytkownik zgłosi problem, etap wraca do `READY_FOR_REVIEW` po poprawce.
Nie należy rozpoczynać kolejnego etapu, jeśli poprzedni nie ma stanu
`ACCEPTED`, chyba że jest to niezależny etap eksploracyjny wyraźnie oznaczony
w raporcie.

### 3.7. Formularz akceptacji

Każdy raport powinien kończyć się formularzem:

```text
Etap:
Status techniczny: PASS / FAIL
Scenariusz ręczny: PASS / FAIL / NOT_APPLICABLE
Assety i screenshoty: PASS / FAIL / NOT_APPLICABLE
Regresje: NONE / LISTED
Decyzja: ACCEPTED / RETURNED
Uwagi:
```

---

## 4. Macierz weryfikacji etapów

| Etap | Dowód techniczny | Weryfikacja użytkownika | Wymagany artefakt |
|---|---|---|---|
| M0 | baseline starego projektu | potwierdzenie listy komponentów i assetów | inventory report |
| M1 | pusty build i testy | uruchomienie pustej aplikacji | screenshot startowy |
| M2 | testy kontraktów i modeli | ręczne przejrzenie komend/snapshotów | contract examples |
| M3 | testy domeny | uruchomienie scenariuszy reguł gry | event/snapshot samples |
| M4 | testy deterministycznej symulacji | porównanie wyników krok po kroku | simulation trace |
| M5 | testy scenariuszy kampanii | uruchomienie wybranych poziomów | scenario matrix |
| M6 | testy AI na snapshotach | obserwacja decyzji AI w meczu | AI decision log |
| M7 | raport assetów | przegląd listy kopiowanych i odrzuconych plików | asset inventory |
| M8 | test zasobów i loaderów | screenshoty/contact sheety | asset preview |
| M9 | testy UI i build APK | ręczne przejście ekranów i meczu | screenshots/video |
| M10 | test lokalnej sesji meczu | pełny mecz mobilny | gameplay capture |
| M11 | testy porównawcze | przegląd różnic starej/nowej wersji | regression report |
| M12 | testy granic i brak starej ścieżki | przegląd finalnej struktury | final architecture report |

---

## 5. Minimalny standard dowodu dla etapów domenowych

Etapy M2–M6 nie muszą mieć ekranu Androida. Użytkownik musi jednak dostać
czytelny, uruchamialny dowód działania.

### Przykład: komenda strzału

Raport powinien pokazać:

```text
Input:
  command = Fire
  team = LEFT
  ammo = ROCK
  angle = 48
  power = 72

Result:
  accepted = true

Events:
  ProjectileLaunched(projectileId=...)

Snapshot:
  projectile.state = FLYING
  ammo.remaining = ...
```

### Przykład: odrzucona komenda

```text
Input:
  command = Fire
  reloadRemaining = 1.4

Result:
  accepted = false
  reason = ReloadInProgress

State changed:
  false
```

Raport powinien pokazywać zarówno przypadek poprawny, jak i odrzucony.

---

## 6. Minimalny standard dowodu dla symulacji

Dla każdego systemu symulacji należy zapisać:

- seed;
- stan początkowy;
- listę komend;
- liczbę kroków;
- delta time;
- eventy;
- snapshot końcowy;
- oczekiwany wynik.

Przykład:

```text
seed: 12345
delta: 0.033333
commands:
  - Aim(LEFT, 48, 72)
  - Fire(LEFT)
ticks: 180

expected:
  - projectile launched
  - projectile impacted terrain
  - terrain deformation emitted
  - snapshot hash: ...
```

Ten sam input uruchomiony drugi raz musi dać ten sam wynik, o ile nie zmieniono
wersji reguł.

---

## 7. Minimalny standard dowodu dla assetów

Każda grupa assetów musi mieć:

1. listę źródeł;
2. listę plików skopiowanych;
3. listę plików odrzuconych z powodem;
4. mapowanie starej nazwy na nową;
5. informację o formacie i alpha;
6. screenshot lub contact sheet;
7. test ładowania;
8. potwierdzenie użycia przez właściwy renderer.

Dla animacji dodatkowo:

- liczba klatek;
- rozmiar wspólnego canvasu;
- FPS;
- punkt zakotwiczenia;
- sposób wyboru klatki;
- test pierwszej, środkowej i ostatniej klatki.

---

## 8. Sposób pracy po każdym etapie

Po każdym etapie kolejność działań jest stała:

1. zakończyć zakres etapu;
2. uruchomić testy automatyczne;
3. przygotować pakiet odbiorczy;
4. uruchomić scenariusz ręczny;
5. przedstawić użytkownikowi zmienione pliki i dowody;
6. poczekać na `ACCEPTED` albo uwagi;
7. dopiero po akceptacji rozpocząć następny etap.

Nie należy:

- łączyć kilku niezaakceptowanych etapów w jeden duży diff;
- ukrywać zmian w automatycznie wygenerowanych plikach;
- uznawać samego builda za dowód poprawności;
- kopiować assetów bez preview;
- przenosić kolejnej grupy logiki przed odbiorem poprzedniej;
- oznaczać etapu jako zakończonego tylko dlatego, że testy regresyjne starego
  projektu nadal przechodzą.

---

## 9. Etap M0 — Zamrożenie i inwentaryzacja starego projektu

### Cel

Ustalić, co rzeczywiście istnieje w starym projekcie i które elementy
reprezentują logikę gry, a które są tylko mechanizmem implementacyjnym.

### Zadania

- [ ] Zapisać commit bazowy starego projektu.
- [ ] Potwierdzić czysty albo jawnie opisany working tree.
- [ ] Uruchomić istniejące testy i build.
- [ ] Zapisać liczbę przechodzących testów.
- [ ] Zidentyfikować wszystkie pakiety produkcyjne.
- [ ] Zidentyfikować wszystkie klasy zawierające reguły gameplayu.
- [ ] Zidentyfikować wszystkie klasy Androidowe.
- [ ] Zidentyfikować wszystkie klasy odpowiedzialne za rendering.
- [ ] Zidentyfikować wszystkie klasy odpowiedzialne za AI.
- [ ] Zidentyfikować wszystkie konfiguracje kampanii, loadoutów i balansu.
- [ ] Zidentyfikować wszystkie miejsca generowania losowości.
- [ ] Zidentyfikować wszystkie zegary, timery i pętle aktualizacji.
- [ ] Zidentyfikować wszystkie źródła assetów.
- [ ] Zidentyfikować assety używane runtime i assety nieużywane.
- [ ] Zidentyfikować assety referencyjne użytkownika i assety generowane.

### Wyniki

Powinny powstać wewnętrzne rejestry migracji:

```text
stare źródło → kategoria → nowy właściciel → decyzja
```

Przykład:

```text
GameEngine.fire()
→ reguła domenowa
→ domain.match / simulation.projectile
→ przenieść po rozbiciu
```

```text
GameView.drawCatapult()
→ rendering Android
→ android-app.rendering.CatapultRenderer
→ odtworzyć na podstawie snapshotu
```

### Bramka M0

- [ ] Znamy wszystkie źródła logiki gry.
- [ ] Znamy wszystkie źródła assetów.
- [ ] Każdy duży komponent ma decyzję: przenieść, przepisać, odtworzyć albo odrzucić.
- [ ] Stary projekt nadal przechodzi baseline.

---

## 10. Etap M1 — Utworzenie nowego projektu

### Cel

Utworzyć czysty projekt w `~/workspace/urkaaz`, bez kopiowania starego
pakietu aplikacji jako gotowej struktury.

### Docelowa struktura

```text
urkaaz/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
├── game-domain/
├── game-simulation/
├── game-application/
├── game-contracts/
├── android-app/
├── tools/
├── tests/
├── architecture.md
└── migration.md
```

Na początku dopuszczalne jest rozpoczęcie od jednego modułu Androidowego
i logicznych pakietów. Docelowe granice modułów nie mogą jednak zostać
rozmyte.

### Zadania

- [ ] Utworzyć nowe repozytorium/projekt w `~/workspace/urkaaz`.
- [ ] Ustawić namespace nowego projektu.
- [ ] Ustawić minimalny i docelowy SDK.
- [ ] Ustawić wersję Kotlin/AGP zgodną z zaakceptowaną konfiguracją.
- [ ] Utworzyć moduł aplikacji Android.
- [ ] Utworzyć lub przygotować moduły `domain`, `simulation`, `application`
  i `contracts`.
- [ ] Skonfigurować testy JVM dla domeny i symulacji.
- [ ] Skonfigurować testy Androidowe wyłącznie dla warstwy Android.
- [ ] Ustawić lint, formatting i static analysis.
- [ ] Dodać podstawowe testy zależności pakietów.
- [ ] Dodać `architecture.md` i `migration.md` do nowego projektu.
- [ ] Utworzyć minimalną pustą aplikację uruchamiającą się na emulatorze.

### Bramka M1

- [ ] Nowy projekt buduje się bez kodu ze starego projektu.
- [ ] Test JVM działa bez Androida.
- [ ] Test Android działa na pustym ekranie.
- [ ] Domena nie ma zależności od Androida.

---

## 11. Etap M2 — Kontrakty i modele bazowe

### Cel

Zdefiniować granicę, przez którą UI będzie komunikowało się z meczem.

### Modele

Należy zdefiniować:

```text
MatchId
PlayerId
Team
MatchPhase
MatchScenario
MatchCommand
CommandResult
MatchEvent
MatchSnapshot
MatchError
```

### Komendy

Minimalny zestaw:

```text
SelectAmmo
Aim
Fire
DeployUnit
SendWave
CastSpell
Surrender
AdvanceSimulation
```

### Snapshoty

Snapshoty powinny używać osobnych modeli:

```text
CatapultSnapshot
FortressSnapshot
UnitSnapshot
ProjectileSnapshot
TerrainSnapshot
ResourceSnapshot
EffectSnapshot
WindSnapshot
MatchStatusSnapshot
```

Nie wolno używać w snapshotach bezpośrednio:

- `GameEngine`;
- `Match`;
- mutowalnych klas runtime;
- klas zależnych od Androida;
- publicznych kolekcji silnika.

### Bramka M2

- [ ] Komendy nie zawierają Androida.
- [ ] Snapshoty nie zawierają implementacji silnika.
- [ ] Każda komenda ma określony właściciel wykonania.
- [ ] Każdy event ma jasno opisane znaczenie.
- [ ] Modele można testować i serializować niezależnie od Androida.

---

## 12. Etap M3 — Przeniesienie domeny gry

### Cel

Przenieść faktyczne reguły gry do czystych klas domenowych.

### Kolejność

#### M3.1. Podstawowe typy

- [ ] `Team`.
- [ ] `GameMode`.
- [ ] `MatchPhase`.
- [ ] identyfikatory.
- [ ] typy amunicji.
- [ ] typy jednostek.
- [ ] typy zaklęć.
- [ ] typy biomów.
- [ ] wartości i jednostki czasu.

#### M3.2. Stan meczu

- [ ] Stan graczy.
- [ ] stan tur/fazy.
- [ ] wynik.
- [ ] stan zasobów.
- [ ] stan wybranej amunicji.
- [ ] cooldowny.
- [ ] seed losowości.

#### M3.3. Katapulty i fortece

- [ ] Pozycje i parametry katapult.
- [ ] kąt i moc.
- [ ] zdrowie fortec.
- [ ] tarcze.
- [ ] centralny cel.
- [ ] boss/siege sovereign.
- [ ] warunki zniszczenia.

#### M3.4. Amunicja i pociski

- [ ] Typy pocisków.
- [ ] parametry obrażeń.
- [ ] promień eksplozji.
- [ ] efekty dodatkowe.
- [ ] stan lotu.
- [ ] stan po kolizji.
- [ ] rozbijanie i projekty wieloczęściowe.

#### M3.5. Jednostki

- [ ] Typy jednostek.
- [ ] zdrowie i obrażenia.
- [ ] ruch.
- [ ] garrison.
- [ ] fale.
- [ ] ataki.
- [ ] śmierć i stany czasowe.

#### M3.6. Zaklęcia i efekty

- [ ] Koszt many.
- [ ] wyposażenie.
- [ ] cooldown.
- [ ] czas trwania.
- [ ] tarcze.
- [ ] pułapki.
- [ ] bastiony.
- [ ] chmury.
- [ ] buffy/debuffy.

### Zasada migracji logiki

Nie kopiować metod w całości, jeśli łączą kilka odpowiedzialności.

Przykładowo obecna metoda obsługująca strzał może zawierać jednocześnie:

- walidację;
- pobranie amunicji;
- obliczenie prędkości;
- utworzenie pocisku;
- dźwięk;
- animację;
- zmianę HUD-u.

W nowym projekcie należy rozdzielić to na:

```text
FireCommandValidator
ProjectileFactory
BallisticsModel
ProjectileSimulation
MatchEventEmitter
```

### Bramka M3

- [ ] Reguły domenowe działają bez Androida.
- [ ] Nie ma `Canvas`, `View`, `Context`, `R` ani `Activity`.
- [ ] Nie ma callbacków UI w domenie.
- [ ] Każda reguła ma test jednostkowy.
- [ ] Wyniki są deterministyczne dla tego samego stanu i seeda.

---

## 13. Etap M4 — Przeniesienie symulacji

### Cel

Przenieść algorytmy wykonywane w czasie gry bez przenoszenia starego
monolitycznego silnika.

### M4.1. Stały krok czasu

- [ ] Ustalić częstotliwość symulacji.
- [ ] Oddzielić czas symulacji od czasu renderowania.
- [ ] Zdefiniować `SimulationStep`.
- [ ] Obsłużyć akumulację czasu.
- [ ] Ustalić kolejność systemów.

### M4.2. Kolejność systemów

Docelowa kolejność jednego kroku:

```text
1. przyjęcie zaakceptowanych komend
2. aktualizacja timerów i cooldownów
3. aktualizacja wiatru
4. ruch pocisków
5. kolizje pocisków
6. deformacja terenu
7. obrażenia i eksplozje
8. aktualizacja jednostek
9. aktywacja i wygasanie zaklęć
10. warunki zwycięstwa
11. generowanie eventów
12. utworzenie snapshotu
```

Kolejność musi być jawna i przetestowana.

### M4.3. Systemy

- [ ] `ProjectileSimulation`.
- [ ] `CollisionSimulation`.
- [ ] `TerrainSimulation`.
- [ ] `UnitSimulation`.
- [ ] `SpellSimulation`.
- [ ] `ResourceSimulation`.
- [ ] `VictoryConditionEvaluator`.
- [ ] `WindSimulation`.

### Bramka M4

- [ ] Ten sam input daje ten sam wynik.
- [ ] Kolejność systemów jest udokumentowana.
- [ ] Testy obejmują lot, kolizje, teren, jednostki i efekty.
- [ ] Renderowanie nie jest potrzebne do wykonania symulacji.

---

## 14. Etap M5 — Przeniesienie scenariuszy kampanii

### Cel

Przenieść konfigurację poziomów, ale nie mieszać progresji z symulacją.

### Do przeniesienia

- [ ] Definicje poziomów.
- [ ] Biomy.
- [ ] Warianty terenu.
- [ ] Mnożniki zdrowia.
- [ ] Dostępne jednostki.
- [ ] Dostępna amunicja.
- [ ] Dostępne zaklęcia.
- [ ] Początkowe zasoby.
- [ ] Konfiguracja przeciwnika.
- [ ] Warunki zwycięstwa.
- [ ] Parametry tutoriala.

### Do pozostawienia poza domeną meczu

- [ ] Odblokowania gracza.
- [ ] złoto;
- [ ] życia;
- [ ] sklep;
- [ ] zapis ukończonych poziomów;
- [ ] wybór ekranów;
- [ ] komunikaty UI.

Poziom kampanii powinien tworzyć `MatchScenario`, który przekazywany jest
do fabryki meczu.

---

## 15. Etap M6 — Migracja AI

### Cel

Przenieść AI tak, aby było uczestnikiem meczu, a nie częścią Androidowego UI.

```text
MatchSnapshot
      ↓
AiAgent
      ↓
MatchCommand
```

### Zadania

- [ ] Oddzielić decyzję AI od wykonywania komendy.
- [ ] Przenieść wybór amunicji.
- [ ] Przenieść wybór celu.
- [ ] Przenieść wybór jednostki.
- [ ] Przenieść reakcje na zdrowie fortecy.
- [ ] Przenieść temperament i poziom trudności.
- [ ] Usunąć zależność AI od `GameEngine`.
- [ ] Usunąć zależność AI od `MainActivity`.
- [ ] Dodać testy AI na sztucznych snapshotach.

AI powinno wysyłać takie same komendy jak człowiek.

---

## 16. Etap M7 — Inwentaryzacja assetów

### Cel

Ustalić, które assety są częścią gry i powinny zostać odtworzone w nowym
projekcie, a które były tylko artefaktami starego procesu.

### Kategorie assetów

```text
assets
├── terrain
├── fortresses
├── catapults
├── units
├── projectiles
├── spells
├── effects
├── ui
├── icons
├── audio
├── fonts
├── backgrounds
└── tutorial
```

### Dla każdego assetu zapisać

- [ ] ścieżkę źródłową;
- [ ] typ;
- [ ] format;
- [ ] rozdzielczość;
- [ ] density/scaling;
- [ ] przezroczystość;
- [ ] wariant/biom;
- [ ] użycie runtime;
- [ ] nazwy zasobów;
- [ ] właściciela referencji w kodzie;
- [ ] informację o źródle i licencji;
- [ ] decyzję: kopiować, przetworzyć, odtworzyć albo odrzucić.

### Nie kopiować automatycznie

- assetów oznaczonych jako tymczasowe;
- previewów i contact sheetów;
- starych eksportów zduplikowanych nazwą;
- plików z nieużywanych wariantów;
- plików generowanych podczas debugowania;
- assetów spoza aktualnej wersji gry;
- plików bez znanego źródła, jeśli ich status prawny jest niejasny.

---

## 17. Etap M8 — Kopiowanie assetów

### Zasada

Asset kopiujemy dopiero wtedy, gdy istnieje jego docelowy właściciel w nowym
projekcie.

Nie należy najpierw kopiować całego `res/` i dopiero później szukać użyć.

### Mapowanie katalogów

Przykładowo:

```text
stary projekt                                  nowy projekt
────────────────────────────────────────────   ─────────────────────────────────────
app/src/main/res/drawable-nodpi/terrain        android-app/src/main/res/drawable-nodpi/terrain
app/src/main/res/drawable-nodpi/units          android-app/src/main/res/drawable-nodpi/units
app/src/main/res/drawable-nodpi/projectiles    android-app/src/main/res/drawable-nodpi/projectiles
app/src/main/res/drawable/                     android-app/src/main/res/drawable/
app/src/main/res/mipmap-*                      android-app/src/main/res/mipmap-*
app/src/main/res/values/                       android-app/src/main/res/values/
app/src/main/res/raw/                          android-app/src/main/res/raw/
```

Docelowe nazwy powinny być uporządkowane według właściciela:

```text
terrain_copperwood_*.png
fortress_left_*.png
fortress_right_*.png
unit_sapper_*.png
projectile_rock_flight_*.png
projectile_rock_impact_*.png
spell_ice_trap_*.png
ui_ammo_rock_*.png
```

Nie zmieniać nazw bez aktualizacji wszystkich referencji i testu zasobów.

### Proces kopiowania pojedynczej grupy

1. [ ] Wyszukać wszystkie użycia grupy w starym projekcie.
2. [ ] Określić, czy asset jest ładowany statycznie czy dynamicznie.
3. [ ] Sprawdzić format, kanał alpha, rozmiar i orientację.
4. [ ] Sprawdzić, czy asset nie zawiera tła, etykiet lub siatki eksportowej.
5. [ ] Skopiować źródło do nowej lokalizacji.
6. [ ] Zachować metadane źródłowe, jeśli są wymagane.
7. [ ] Zaktualizować mapowanie zasobów w nowym kodzie.
8. [ ] Dodać test obecności i poprawnej nazwy zasobu.
9. [ ] Sprawdzić asset na urządzeniu/emulatorze.
10. [ ] Dopiero wtedy oznaczyć grupę jako przeniesioną.

### Animacje

Dla sprite sheetów i animacji należy zachować:

- kolejność klatek;
- liczbę klatek;
- rozmiar canvasu;
- punkt zakotwiczenia;
- skalę runtime;
- przezroczystość;
- nazwy prefiksów;
- rozróżnienie flight/impact;
- zachowanie animacji przy zmianie FPS.

Dla pocisków osobno zweryfikować:

```text
rock_flight
rock_impact
siege_bomb_flight
siege_bomb_impact
powder_barrel_flight
powder_barrel_impact
void_orb_flight
void_orb_impact
cluster_bomb_flight
cluster_bomb_impact
fire_rain_flight
fire_rain_impact
```

Nie przenosić animacji tylko dlatego, że istnieje plik. Najpierw potwierdzić,
że odpowiada typowi pocisku używanemu przez nową domenę.

### Assety UI

Assety UI należy kopiować dopiero po podziale ekranów:

- menu;
- kampania;
- loadout;
- sklep;
- HUD meczu;
- tutorial;
- ekran wyniku;
- debug/photo mode.

Ikony UI nie powinny być używane jako dane domenowe. Domena zwraca typ, np.
`AmmoType.ROCK`, a adapter UI wybiera odpowiedni drawable.

### Audio

Audio powinno być mapowane z eventów:

```text
ProjectileLaunched → launch sound
ProjectileImpacted → impact sound
UnitDestroyed → death sound
SpellActivated → spell sound
MatchFinished → result sound
```

Silnik generuje event, ale nie ładuje `SoundPool` ani nie odtwarza dźwięku.

---

## 18. Etap M9 — Migracja Android UI

### Cel

Podłączyć nowy silnik do Androida przez kontrakty, bez przenoszenia starej
struktury UI.

### Kolejność

1. [ ] Utworzyć root aplikacji.
2. [ ] Utworzyć nawigację.
3. [ ] Utworzyć ekran menu.
4. [ ] Utworzyć ekran kampanii.
5. [ ] Utworzyć ekran loadoutu.
6. [ ] Utworzyć `MatchViewModel`.
7. [ ] Utworzyć `BattlefieldView`.
8. [ ] Utworzyć `RenderStateMapper`.
9. [ ] Utworzyć osobne renderery terenu, fortec, jednostek i pocisków.
10. [ ] Podłączyć input do `MatchUiAction`.
11. [ ] Podłączyć `MatchUiAction` do `MatchCommand`.
12. [ ] Podłączyć snapshoty do HUD-u.
13. [ ] Podłączyć eventy do dźwięków i efektów.
14. [ ] Podłączyć progresję i persistence.

### Czego nie kopiować z `MainActivity`

- logiki gameplayu;
- logiki AI;
- konfiguracji wszystkich systemów;
- bezpośrednich wywołań silnika;
- obsługi każdej kontrolki w jednej klasie;
- prywatnych kolekcji stanu meczu.

### Czego nie kopiować z `GameView`

- bezpośredniego odczytu silnika;
- metod rysujących i modyfikujących stan w jednym miejscu;
- logiki wyboru amunicji;
- logiki obrażeń;
- logiki zwycięstwa;
- odwołań do systemów domenowych;
- ukrytych callbacków do Activity.

---

## 19. Etap M10 — Integracja lokalna

### Cel

Uruchomić pełną grę mobilną bez sieci:

```text
Android UI
  → LocalMatchGateway
  → MatchSession
  → Match
  → MatchSnapshot
  → Android UI
```

### Zadania

- [ ] Utworzyć lokalny gateway.
- [ ] Uruchomić jeden mecz w pamięci.
- [ ] Obsłużyć start i stop.
- [ ] Obsłużyć pauzę Androida.
- [ ] Obsłużyć wznowienie.
- [ ] Obsłużyć restart poziomu.
- [ ] Obsłużyć zakończenie.
- [ ] Obsłużyć zapis nagrody i progresji.
- [ ] Obsłużyć tutorial.
- [ ] Obsłużyć debug/photo mode jako funkcje UI.

### Bramka M10

- [ ] Kampania działa przez nową sesję meczu.
- [ ] UI nie posiada referencji do implementacji silnika.
- [ ] Renderer działa wyłącznie na snapshotach.
- [ ] AI używa komend.
- [ ] Restart meczu nie tworzy pozostałości poprzedniego stanu.

---

## 20. Etap M11 — Porównanie ze starą wersją

### Cel

Potwierdzić, że migracja przeniosła logikę, a nie zmieniła gry.

### Scenariusze regresyjne

- [ ] start każdego poziomu;
- [ ] wybór każdej amunicji;
- [ ] każdy typ pocisku;
- [ ] lot i kolizja;
- [ ] eksplozja;
- [ ] deformacja terenu;
- [ ] każda jednostka;
- [ ] garrison;
- [ ] fala;
- [ ] każde zaklęcie;
- [ ] tarcza;
- [ ] pułapka;
- [ ] bastion;
- [ ] chmura plagi;
- [ ] boss;
- [ ] wiatr;
- [ ] zwycięstwo;
- [ ] przegrana;
- [ ] restart;
- [ ] tutorial;
- [ ] zapis progresji.

### Porównanie

Dla powtarzalnego seeda porównywać:

- snapshot po każdym ticku;
- pozycje pocisków;
- obrażenia;
- zdrowie;
- stan terenu;
- zasoby;
- eventy;
- wynik końcowy.

Różnice muszą być:

- zaakceptowane i udokumentowane;
- albo naprawione.

Nie akceptować różnic tylko dlatego, że nowa implementacja jest „czytelniejsza”.

---

## 21. Etap M12 — Usunięcie kodu starej architektury

Ten etap dotyczy nowego projektu, nie modyfikowania starego projektu
referencyjnego.

### Nie przenosić do finalnej wersji

- `GameEngine` jako God Object;
- stare adaptery;
- aliasy kompatybilności;
- delegatory do starego API;
- bezpośrednie zależności UI-silnik;
- nieużywane assety;
- debugowe eksporty;
- nieużywane test helpery;
- stare mechanizmy lifecycle.

### Bramka M12

- [ ] Każdy plik w nowym projekcie ma właściciela architektonicznego.
- [ ] Nie ma nieużywanych mostów migracyjnych.
- [ ] Nie ma klas nazwanych `Legacy`, `Compatibility`, `Bridge` bez uzasadnienia.
- [ ] Nie ma publicznego stanu runtime.
- [ ] Wszystkie assety mają właściciela i referencję.
- [ ] Testy architektoniczne przechodzą.

---

## 22. Rejestr migracji logiki

| Stare źródło | Kategoria | Nowy właściciel | Decyzja | Status |
|---|---|---|---|---|
| `GameEngine` | agregat i systemy | `Match` + `simulation` | rozbić | plan |
| `GameView` | UI/rendering/input | `BattlefieldView` + renderery | odtworzyć | plan |
| `MainActivity` | lifecycle i koordynacja | `Activity` + ViewModely | rozbić | plan |
| `AiController` | decyzje AI | `AiAgent` | przepisać | plan |
| `GameSnapshot` | dane odczytowe | `MatchSnapshot` | ujednolicić | plan |
| `PlayerCommand` | komendy wejściowe | `MatchCommand` | ujednolicić | plan |

Każdy wpis powinien zostać rozszerzony o konkretne pliki, testy i decyzję
dotyczącą zachowania.

---

## 23. Rejestr assetów

| Grupa | Źródło | Lokalizacja docelowa | Sposób migracji | Status |
|---|---|---|---|---|
| teren | stary `res/` | `android-app/res/drawable-nodpi` | kopiowanie po walidacji | plan |
| fortece | stary `res/` | `android-app/res/drawable-nodpi` | kopiowanie i mapowanie | plan |
| jednostki | stary `res/` | `android-app/res/drawable-nodpi` | grupami po typach | plan |
| pociski | stary `res/` | `android-app/res/drawable-nodpi` | osobno flight/impact | plan |
| zaklęcia | stary `res/` | `android-app/res/drawable-nodpi` | mapowanie po typie | plan |
| UI | stary `res/` | `android-app/res/drawable` | po ekranach | plan |
| audio | stary `res/` | `android-app/res/raw` | po eventach | plan |

---

## 24. Końcowa definicja migracji

Migracja jest zakończona, gdy:

- nowy projekt działa jako samodzielna aplikacja;
- logika gry działa bez Androida;
- UI komunikuje się z meczem wyłącznie przez kontrakty;
- snapshoty są niemutowalne;
- komendy są jedynym wejściem do zmiany stanu;
- AI działa na snapshotach;
- kampania działa przez `MatchScenario`;
- wszystkie wymagane assety zostały skopiowane i zweryfikowane;
- nie ma kopiowanych mechanizmów starej architektury;
- porównanie ze starą wersją pokrywa wszystkie kluczowe scenariusze;
- testy domeny, symulacji, aplikacji i UI przechodzą;
- przyszłe zastąpienie `LocalMatchGateway` przez gateway zewnętrzny nie wymaga
  zmian w domenie ani rendererach.

Niniejszy dokument opisuje plan. Nie oznacza żadnego etapu jako wykonany.
