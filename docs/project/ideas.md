# Backlog pomysłów

Trwałe miejsce na pomysły przekrojowe lub takie, które nie mają jeszcze
odpowiedniego dokumentu tematycznego. Pomysł zapisany tutaj nie jest decyzją
projektową ani zgodą na implementację. Oznaczaj go jako propozycję i zachowuj
ewentualny status akceptacji podany przez użytkownika.

## Pomysły

- **Wspólne animacje postaci z kolorem drużyny nakładanym w runtime**
  (propozycja, bez zgody na implementację) — generować jedną neutralną
  sekwencję i używać jej dla BLUE oraz RED, nakładając barwę drużyny tylko na
  oznaczone maską fragmenty stroju. Zachować naturalne kolory skóry
  i wyposażenia; kierunek przeciwnika można uzyskać odbiciem animacji, z
  wyjątkami dla asymetrycznych postaci. Zacząć od pilota dla jednej jednostki,
  aby ograniczyć rozjazdy animacji i koszt generowania.

### Migracja gry na serwer

- **Propozycja migracji gry na serwer — do omówienia, bez decyzji o wdrożeniu.**
  Najpierw rozstrzygnąć, czy celem jest wyłącznie chmurowy profil/progresja
  (symulacja pozostaje lokalna), czy autorytatywny serwer meczu. Przy wariancie
  autorytatywnym plan zakłada zachowanie wspólnego rdzenia modułów gry,
  utrwalenie kontraktów komend/eventów/snapshotów i testów deterministyczności,
  następnie serwer JVM jako modularny monolit, serwerowe prowadzenie meczu,
  migrację profilu i ekonomii do trwałej persystencji oraz etapowe wdrożenie
  ze stagingiem, monitoringiem, kopiami zapasowymi i rollbackiem. Najpierw
  ustalić wymagania offline, multiplayera, opóźnień i ochrony przed
  manipulacją; nie zakładać mikroserwisów ani nie przenosić symulacji bez
  uzasadnienia.

### Testy e2e aplikacji (pomysł, tylko ustalenia)

Status: pomysł; nic nie zaimplementowano ani nie zaakceptowano. Repo ma dziś
tylko testy JVM (`src/test`), bez `androidTest`.

- Odpowiednik Selenium dla Androida to Appium (WebDriver). Alternatywy:
  Espresso, UI Automator, Maestro, Robolectric.
- Przepływy ekranów (wybór poziomu → loadout → start → wynik, zmiana języka)
  nadają się do Espresso lub Maestro na emulatorze.
- `BattlefieldView` rysuje na canvasie, więc narzędzia UI nie widzą jednostek
  ani pocisków; obsłużą tylko gesty i zwykłe widoki (HUD).
- Przebieg walki sprawdzać na trzech poziomach: (1) symulacja JVM ze stałym
  seedem (najlepsze miejsce, już istnieje), (2) test instrumentowany z
  zadanym scenariuszem i asercjami na `MatchViewModel`/`MatchSnapshot` oraz
  sterowanym zegarem zamiast czasu rzeczywistego, (3) nieliczne testy
  screenshotowe (kruche, zależne od GPU i rozdzielczości).
- Do ustalenia: wybór narzędzia, zakres scenariuszy, emulator w CI czy lokalnie.
