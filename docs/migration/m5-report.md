# M5 — Raport implementacji

**Status:** `ACCEPTED`

## Zaimplementowano

- nowy moduł `game-campaign`;
- deterministyczne definicje poziomów kampanii 1–100;
- harmonogram biomów i wymiary terenu;
- progi odblokowania jednostek, amunicji i czarów;
- strojenie trudności przeciwnika, celowania, zdrowia, fortec i opóźnienia
  decyzji;
- początkową amunicję i klucze kroków tutoriala;
- konwersję definicji poziomu do neutralnego `MatchScenario`;
- testy blokad wczesnych poziomów, odblokowań kamieni milowych, wyboru biomu
  i konwersji scenariusza.

## Celowo wyłączono z zakresu

Persystencja odblokowań gracza, złoto, życia, stan sklepu, wybór ekranu,
androidowe nakładki tutoriala, wykonanie AI oraz wizualny teren i assety
pozostają w kolejnych etapach.

## Weryfikacja

Uruchom:

```bash
./gradlew :game-campaign:test :game-simulation:test :game-domain:test :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap zatrzymuje się na `READY_FOR_REVIEW` i wymaga jawnej akceptacji przed
rozpoczęciem M6.
