# M1 — Utworzenie nowego projektu

Status: `ACCEPTED`

Data wykonania: 2026-10-08

## Zakres

Utworzono czysty projekt w `~/workspace/urkaaz`. Nie skopiowano kodu
produkcyjnego, assetów ani starej struktury aplikacji.

## Utworzona struktura

```text
urkaaz/
├── game-contracts/
├── game-domain/
├── game-simulation/
├── game-application/
├── android-app/
├── tools/
├── tests/
├── architecture.md
├── migration.md
└── M0_INVENTORY.md
```

Moduły core nie mają zależności od Androida:

```text
android-app
    -> game-application
        -> game-simulation
            -> game-domain
        -> game-contracts
```

## Minimalny vertical shell

Szkielet zawiera wyłącznie minimalne kontrakty i przepływ kontrolny:

```text
MatchCommand.Start
    -> MatchSession
    -> MatchSimulation
    -> Match
    -> MatchSnapshot
    -> MainActivity
```

Nie jest to jeszcze logika gry. `Match`, `MatchSimulation` i `MatchSession`
potwierdzają tylko poprawne granice modułów i kierunek zależności.

## Walidacja techniczna

Polecenie użyte do walidacji:

```bash
cd ~/workspace/urkaaz
./gradlew testDebugUnitTest assembleDebug --no-build-cache \
  -Dkotlin.incremental=false
```

Wynik:

- testy JVM: `PASS`;
- test modułu Android: `PASS`;
- build debug APK: `PASS`;
- artefakt: `android-app/build/outputs/apk/debug/android-app-debug.apk`;
- moduły `game-contracts`, `game-domain`, `game-simulation` i
  `game-application` nie zawierają importów `android.*` ani `androidx.*`.
- JDK uruchamiające Gradle: `25.0.2`;
- Gradle Wrapper: `9.7.0`;
- Android Gradle Plugin: `9.3.1`;
- Kotlin JVM: `2.4.20`;
- Android Kotlin jest dostarczany przez wbudowaną obsługę AGP 9.

## Weryfikacja ręczna

Po instalacji APK na emulatorze lub urządzeniu:

```bash
./gradlew installDebug --no-build-cache -Dkotlin.incremental=false
adb shell am start -n com.urkaaaz.android/com.urkaaaz.MainActivity
```

Oczekiwany rezultat:

- uruchamia się pusty ekran aplikacji;
- aplikacja wyświetla `RUNNING`;
- nie są ładowane żadne assety starego projektu;
- nie ma jeszcze gameplayu, kampanii ani renderingu pola bitwy.

Ręczne uruchomienie APK zostało wykonane na emulatorze `Pixel_8(AVD) - 17`
przy użyciu JDK 25.
Aktywność `com.urkaaaz.MainActivity` była aktywna na pierwszym planie, a tekst
`RUNNING` został potwierdzony przez `uiautomator`.

## Bramka M1

| Kryterium | Status |
|---|---|
| czysty projekt bez kodu starej aplikacji | PASS |
| moduły kontraktów, domeny, symulacji i aplikacji | PASS |
| moduł Android | PASS |
| test JVM bez Androida | PASS |
| test modułu Android | PASS |
| domena bez Androida | PASS |
| pusty APK debug | PASS |
| ręczne uruchomienie na emulatorze | PASS |

M1 spełnia techniczne kryteria odbioru i został zaakceptowany przez użytkownika
2026-10-08. Następnym etapem jest M2 — kontrakty i modele bazowe właściwej gry.
