# M6 — Raport implementacji AI

**Status:** `ACCEPTED`

## Zaimplementowano

- nowy moduł `game-ai`;
- deterministyczne decyzje oparte wyłącznie na `MatchSnapshot`;
- wybór amunicji według dostępnych zasobów i temperamentu;
- wybór fortecy przeciwnika oraz parametrów celowania;
- generowanie sekwencji `SelectAmmo`, `Aim`, `Fire`;
- wybór jednostki z uwzględnieniem kosztu i temperamentu;
- automatyczne przejście do temperamentu `DESPERATE` przy niskim zdrowiu;
- testy sztucznych snapshotów bez Androida i bez silnika gry.

## Celowo poza zakresem

AI nie wykonuje komend, nie steruje bezpośrednio symulacją, nie posiada
referencji do `GameEngine` i nie zawiera logiki UI. Planowanie fal jednostek,
pełna trajektoria balistyczna, współpraca z kampanią oraz integracja z
`MatchSession` pozostają kolejnymi krokami.

## Weryfikacja

Uruchom:

```bash
./gradlew :game-ai:test :game-campaign:test :game-simulation:test :game-domain:test :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap zatrzymuje się na `READY_FOR_REVIEW` i wymaga jawnej akceptacji przed M7.
