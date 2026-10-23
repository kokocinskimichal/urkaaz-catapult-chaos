# Struktura projektu Urkaaaz

Ten dokument opisuje strukturę katalogu `~/workspace/urkaaz` po zakończeniu
etapu M1. Projekt zawiera na razie pusty szkielet architektury oraz minimalny
przepływ `MatchCommand -> MatchSession -> MatchSnapshot`. Logika właściwej
gry zostanie dodana w kolejnych etapach migracji.

## 1. Struktura katalogów

```text
urkaaz/
├── android-app/
├── game-application/
├── game-contracts/
├── game-domain/
├── game-simulation/
├── tools/
├── tests/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradlew
├── gradlew.bat
├── architecture.md
├── migration.md
├── M0_INVENTORY.md
├── M1_REPORT.md
└── docs/
```

Katalogi `build/`, `.gradle/` i `.idea/` są generowane lokalnie przez Gradle
lub Android Studio i nie należą do źródeł projektu.

## 2. `android-app`

`android-app` jest jedynym modułem zależnym od Androida.

```text
android-app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/urkaaaz/MainActivity.kt
    │   └── res/
    │       └── values/
    └── test/
```

Obecnie moduł zawiera:

- `MainActivity`;
- manifest aplikacji;
- nazwę aplikacji i motyw;
- pusty ekran;
- test modułu Android;
- połączenie z `MatchSession`.

Docelowo tutaj znajdą się:

- ekrany Androida;
- ViewModely;
- `BattlefieldView`;
- renderery Canvas;
- obsługa dotyku;
- kamera;
- assety;
- nawigacja;
- adapter lokalnego gatewaya;
- adaptery persystencji.

`android-app` nie powinien zawierać reguł gry, fizyki, obrażeń ani logiki
zwycięstwa.

## 3. `game-contracts`

Ten moduł zawiera kontrakty komunikacji pomiędzy aplikacją a silnikiem.

Obecnie definiuje:

```kotlin
MatchCommand
MatchSnapshot
MatchStatus
```

Docelowo mogą tu trafić:

```text
MatchCommand
MatchSnapshot
MatchEvent
MatchOutcome
PlayerId
EntityId
```

Podstawowy przepływ będzie wyglądał tak:

```text
UI lub AI
    │
    ▼
MatchCommand
    │
    ▼
silnik gry
    │
    ├── MatchSnapshot
    └── MatchEvent
```

Moduł nie może znać:

- Androida;
- `Activity`;
- `View`;
- `Canvas`;
- `Context`;
- implementacji silnika;
- plików graficznych.

## 4. `game-domain`

To czysta domena gry. Zawiera podstawowe pojęcia i reguły, które nie wymagają
jeszcze obsługi czasu, renderingu ani platformy.

Obecnie znajduje się tutaj minimalna klasa `Match`.

Docelowo moduł może zawierać:

```text
Match
Player
Team
Fortress
Catapult
Projectile
Terrain
Unit
Spell
Ammunition
Objective
Wind
Health
Mana
Damage
```

Warstwa domenowa opisuje, czym są elementy gry i jakie mają podstawowe
ograniczenia. Przykłady:

- forteca ma zdrowie;
- gracz należy do określonej strony;
- pocisk ma typ;
- czar ma koszt;
- jednostka ma stan;
- mecz ma status.

`game-domain` nie może znać:

- Androida;
- `GameView`;
- `Canvas`;
- bitmap;
- animacji;
- `SharedPreferences`;
- `GameEngine`.

## 5. `game-simulation`

Ten moduł wykonuje symulację meczu i zmienia stan domeny zgodnie z regułami.

Obecnie zawiera minimalną klasę `MatchSimulation`.

Docelowo znajdą się tutaj między innymi:

```text
ProjectileSimulation
CollisionSystem
TerrainSimulation
WindSimulation
DamageSystem
UnitSimulation
SpellSimulation
AmmunitionSimulation
ObjectiveSystem
MatchClock
VictoryRules
```

Warstwa symulacji odpowiada za pytania:

- gdzie znajduje się pocisk po upływie czasu;
- czy pocisk zderzył się z terenem;
- jakie obrażenia otrzymał cel;
- czy powstał krater;
- czy aktywował się efekt czaru;
- czy jednostka zmieniła stan;
- czy mecz się zakończył.

Przykładowy przepływ:

```text
MatchCommand
    ▼
MatchSimulation
    ▼
zmiana stanu Match
    ▼
MatchSnapshot + MatchEvent
```

Moduł pozostaje niezależny od Androida.

## 6. `game-application`

To warstwa aplikacyjna. Koordynuje domenę i symulację, ale nie powinna
zawierać szczegółów fizyki ani kodu platformy.

Obecnie zawiera `MatchSession`, który jest punktem wejścia do jednego lokalnego
meczu:

```text
MainActivity
    ▼
MatchSession
    ▼
MatchSimulation
    ▼
Match
```

Docelowo mogą tutaj trafić:

```text
MatchSession
MatchCommandHandler
StartMatchUseCase
AdvanceMatchUseCase
DispatchCommandUseCase
ObserveMatchUseCase
CampaignMatchFactory
LocalMatchGateway
```

Warstwa aplikacyjna będzie koordynować:

- rozpoczęcie meczu;
- wysłanie komendy;
- wykonanie kroku czasu;
- odebranie snapshotu;
- przekazanie eventu do UI;
- utworzenie meczu ze scenariusza kampanii.

Nie powinna zawierać kodu `Activity`, `View`, `Canvas` ani bezpośredniego
renderingu.

## 7. Kierunek zależności

Aktualny i docelowy kierunek zależności:

```text
android-app
    ├── game-application
    │       ├── game-simulation
    │       │       ├── game-domain
    │       │       └── game-contracts
    │       └── game-contracts
    └── game-contracts
```

Najważniejsza zasada:

```text
game-domain      ──X──> android-app
game-simulation  ──X──> android-app
game-application ──X──> android-app
```

Silnik może być później użyty przez różne hosty:

```text
Android app ───────┐
                   ├── game-application
Test runner ───────┤       │
                   │       ▼
Future server ─────┘  game-simulation
                           │
                           ▼
                      game-domain
```

Serwer nie jest obecnie implementowany. Ta możliwość wynika wyłącznie
z braku zależności core od Androida.

## 8. `tools`

Katalog na narzędzia pomocnicze, które nie są częścią runtime aplikacji.

Docelowo mogą się tu znaleźć:

- walidatory assetów;
- importery sprite sheetów;
- narzędzia porównywania replayów;
- generatory danych;
- narzędzia testów deterministycznych;
- skrypty analizy konfiguracji.

## 9. `tests`

Katalog przeznaczony na testy przekrojowe, które nie należą do pojedynczego
modułu.

Przykłady:

- pełny przepływ komendy;
- deterministyczny replay;
- porównanie starej i nowej wersji;
- scenariusz kampanii;
- równoważność snapshotów.

Testy modułowe pozostają przy kodzie:

```text
game-domain/src/test/
game-application/src/test/
android-app/src/test/
```

## 10. Pliki Gradle i konfiguracji

### `build.gradle.kts`

Definiuje pluginy projektu:

- Android Gradle Plugin `9.3.1`;
- Kotlin JVM `2.4.20`.

### `settings.gradle.kts`

Definiuje nazwę projektu, repozytoria i moduły:

```text
game-contracts
game-domain
game-simulation
game-application
android-app
```

### `gradle.properties`

Zawiera wspólne ustawienia Gradle, Kotlina i Androida.

### `local.properties`

Zawiera lokalną ścieżkę do Android SDK:

```text
sdk.dir=/Users/michalkokocinski/android-sdk
```

Plik jest zależny od komputera i nie powinien być commitowany.

### `gradlew` i `gradlew.bat`

Są wrapperami uruchamiającymi przypisaną wersję Gradle. Projekt używa obecnie
Gradle `9.7.0`, który działa na JDK `25.0.2`.

## 11. Dokumentacja w katalogu głównym

### `architecture.md`

Opisuje docelową architekturę, komponenty, komunikację, komendy, snapshoty,
eventy i granicę pomiędzy UI a silnikiem.

### `migration.md`

Opisuje plan migracji etapami M0–M12.

### `M0_INVENTORY.md`

Zawiera inwentaryzację starego projektu, gameplayu, testów i assetów oraz
decyzje `przenieść / przepisać / odrzucić`.

### `M1_REPORT.md`

Zawiera raport odbiorczy M1, wyniki testów, buildu, instalacji i uruchomienia
aplikacji na emulatorze.

## 12. Czego jeszcze nie ma

W projekcie nie ma jeszcze:

- starego `GameEngine`;
- assetów;
- kampanii;
- balistyki;
- fizyki;
- AI;
- czarów;
- amunicji;
- jednostek;
- terrainu;
- HUD-u;
- właściwego renderingu;
- logiki ze starego `MainActivity`;
- skopiowanej struktury starego projektu.

To jest zamierzone. M1 ma potwierdzić tylko, że nowa struktura projektu
buduje się, testuje i uruchamia na Androidzie oraz że kierunek zależności jest
poprawny. Rzeczywiste kontrakty gry rozpoczną się w M2.
