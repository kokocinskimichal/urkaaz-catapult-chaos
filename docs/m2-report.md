# M2 — Raport implementacji kontraktów

Status: `ACCEPTED`

Data wykonania: 2026-10-08

## Zakres wykonany

Zaimplementowano kontrakty w module `game-contracts` bez przenoszenia logiki
starego `GameEngine`.

Dodano:

- typowane identyfikatory: `MatchId`, `PlayerId`, `EntityId`, `CommandId`,
  `EventId`;
- `Team`, `MatchPhase`, `MatchStatus`, `MatchOutcome`;
- `MatchScenario` i konfigurację scenariusza;
- komendy `MatchCommand`;
- `CommandResult` i `MatchError`;
- eventy `MatchEvent`;
- `MatchSnapshot` i modele snapshotów;
- testy kontraktów.

## Pliki implementacji

```text
game-contracts/src/main/kotlin/com/urkaaaz/contracts/
├── MatchIdentity.kt
├── MatchScenario.kt
├── MatchCommands.kt
├── CommandResult.kt
├── MatchEvents.kt
└── MatchSnapshot.kt
```

## Komendy

Zdefiniowano:

```text
Start
SelectAmmo
Aim
Fire
DeployUnit
SendWave
CastSpell
Surrender
AdvanceSimulation
```

Każda komenda ma `commandId`, `matchId` i `playerId`. Komendy nie zawierają
referencji do `GameEngine`, domeny runtime, Androida ani UI.

Komendy inne niż `Start` są na tym etapie tylko kontraktami. Minimalny shell
M1 jawnie zgłasza `UnsupportedOperationException`, gdy otrzyma komendę,
której implementacja należy do późniejszych etapów.

## Snapshoty

Zdefiniowano:

```text
CatapultSnapshot
FortressSnapshot
UnitSnapshot
ProjectileSnapshot
TerrainSnapshot
ResourceSnapshot
EffectSnapshot
WindSnapshot
MatchSnapshot
```

Snapshoty nie zawierają implementacji silnika ani klas Androida. Zakończony
mecz musi posiadać jawny `MatchOutcome`.

## Eventy

Zdefiniowano eventy dla:

- rozpoczęcia meczu;
- zmiany tury;
- wyboru amunicji;
- wystrzelenia i trafienia pocisku;
- zastosowania obrażeń;
- użycia czaru;
- rozmieszczenia i pokonania jednostki;
- deformacji terenu;
- zakończenia meczu.

Każdy event zawiera `eventId`, `matchId` i czas symulacji.

## Testy

Dodano testy sprawdzające:

- tworzenie komend;
- odrzucanie ujemnego kroku symulacji;
- jawny błąd odrzuconej komendy;
- odrzucenie scenariusza bez graczy;
- wymóg wyniku dla zakończonego meczu;
- dane eventu opisującego zakończony fakt.

## Walidacja

Uruchomiono:

```bash
./gradlew :game-contracts:test \
  :game-domain:test \
  :game-application:test \
  :android-app:testDebugUnitTest \
  :android-app:assembleDebug \
  --no-build-cache \
  -Dkotlin.incremental=false
```

Wynik: `PASS`.

Dodatkowo potwierdzono:

```text
CORE_ANDROID_IMPORTS=PASS
CONTRACTS_GAME_ENGINE_DEPENDENCY=PASS
```

APK zaktualizowane po M2 zostało zainstalowane na emulatorze, a
`com.urkaaaz.MainActivity` nadal wyświetla `RUNNING`.

## Kryteria odbioru

| Kryterium | Status |
|---|---|
| komendy nie zawierają Androida | PASS |
| snapshoty nie zawierają implementacji silnika | PASS |
| komendy mają identyfikatory i właściciela semantycznego | PASS |
| eventy opisują zakończone fakty | PASS |
| odrzucenia mają jawny błąd | PASS |
| modele można testować bez Androida | PASS |
| core nie importuje Androida | PASS |
| kontrakty nie zależą od `GameEngine` | PASS |
| build i testy przechodzą na JDK 25 | PASS |

M2 zostało zaakceptowane przez użytkownika 2026-10-08. Można rozpocząć M3 —
przeniesienie domeny gry.
