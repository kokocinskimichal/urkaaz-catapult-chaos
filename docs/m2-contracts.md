# M2 — Kontrakty i modele bazowe

Status: `READY_FOR_REVIEW`

## 1. Cel etapu

M2 definiuje formalną granicę komunikacji pomiędzy UI, AI, warstwą
aplikacyjną i silnikiem gry.

Na tym etapie nie przenosimy jeszcze właściwej logiki gameplayu. Tworzymy
stabilny język komunikacji, który później będzie używany przez lokalny mecz,
AI, tutorial oraz przyszłe adaptery zewnętrzne.

## 2. Zakres M2

Implementacja M2 obejmuje:

- kontrakty komend wejściowych;
- wyniki obsługi komend;
- eventy opisujące fakty, które zaszły;
- niemutowalne snapshoty stanu;
- podstawowe identyfikatory i typy wartości;
- scenariusz meczu jako dane wejściowe;
- testy kontraktów niezależne od Androida;
- dokumentację znaczenia każdego kontraktu.

## 3. Moduł docelowy

Podstawowym właścicielem kontraktów jest:

```text
game-contracts/
```

Kontrakty mogą być używane przez:

```text
android-app
game-application
game-simulation
test runner
future server adapter
```

`game-contracts` nie może zależeć od żadnego z tych modułów.

## 4. Modele bazowe

### 4.1. Identyfikatory

```text
MatchId
PlayerId
EntityId
CommandId
EventId
```

Identyfikatory powinny być typowane i niemutowalne. Nie należy przekazywać
losowych `String` w miejscach, w których można użyć konkretnego typu.

### 4.2. Typy meczu

```text
Team
MatchPhase
MatchStatus
MatchOutcome
```

Przykładowe fazy meczu:

```text
CREATED
PLAYER_TURN
PROJECTILE_IN_FLIGHT
RESOLVING_IMPACT
AI_TURN
FINISHED
```

Dokładny zestaw wartości powinien odpowiadać regułom gry, a nie potrzebom
renderingu.

### 4.3. Scenariusz meczu

`MatchScenario` opisuje dane potrzebne do utworzenia meczu:

```text
MatchScenario
├── battlefield
├── teams
├── initial resources
├── available ammunition
├── available spells
├── initial units
├── wind configuration
└── victory rules
```

Scenariusz jest konfiguracją wejściową. Nie przechowuje mutowalnego stanu
runtime.

## 5. `MatchCommand`

`MatchCommand` opisuje intencję gracza, AI albo innego wejścia aplikacji.
Nie opisuje bezpośredniej operacji na obiekcie silnika.

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

Przykładowe znaczenie:

```text
Fire
├── playerId
├── catapultId
├── direction
├── power
└── selected ammunition
```

Komenda może zawierać wyłącznie dane potrzebne do wyrażenia intencji.
Nie może zawierać:

- `GameEngine`;
- `Match`;
- `Context`;
- `View`;
- `Canvas`;
- callbacków do UI;
- mutowalnych kolekcji runtime;
- referencji do obiektów renderera.

Każda komenda musi mieć jednego właściciela wykonania:

| Komenda | Właściciel |
|---|---|
| `SelectAmmo` | `MatchCommandHandler` / reguły zasobów |
| `Aim` | `MatchCommandHandler` / stan tury |
| `Fire` | `MatchSimulation` |
| `DeployUnit` | `UnitSimulation` |
| `SendWave` | `UnitSimulation` / reguły kampanii |
| `CastSpell` | `SpellSimulation` |
| `Surrender` | `MatchOutcomeRules` |
| `AdvanceSimulation` | `MatchSession` / zegar symulacji |

## 6. `CommandResult`

`CommandResult` informuje, czy komenda została przyjęta.

Powinien rozróżniać co najmniej:

```text
Accepted
Rejected
```

Odrzucenie musi zawierać jawny powód, np.:

```text
MatchNotRunning
NotPlayersTurn
InsufficientResource
InvalidAim
NoAmmunition
UnknownEntity
AlreadyFinished
CommandNotAllowed
```

Nie wolno zwracać sukcesu w przypadku nieobsłużonej lub nieprawidłowej
komendy.

## 7. `MatchEvent`

`MatchEvent` opisuje fakt, który już zaszedł. Event nie jest żądaniem i nie
powinien być używany do sterowania silnikiem.

Przykładowe eventy:

```text
MatchStarted
TurnChanged
AmmunitionSelected
ProjectileFired
ProjectileHitTerrain
ProjectileHitEntity
DamageApplied
SpellCast
UnitDeployed
UnitDefeated
TerrainDeformed
MatchWon
MatchLost
MatchSurrendered
```

Każdy event powinien określać:

- co zaszło;
- którego meczu dotyczy;
- kiedy zaszło w czasie symulacji;
- jakich identyfikatorów dotyczy;
- jakie dane są potrzebne UI, replayowi lub logowaniu.

Event nie powinien zawierać referencji do mutowalnych obiektów silnika.

## 8. `MatchSnapshot`

`MatchSnapshot` jest niemutowalnym odczytem stanu meczu. UI i AI mogą go
odczytywać, ale nie mogą przez niego zmienić meczu.

Główne części snapshotu:

```text
MatchSnapshot
├── matchId
├── phase
├── activeTeam
├── catapults
├── fortresses
├── units
├── projectiles
├── terrain
├── resources
├── effects
├── wind
└── status
```

Modele szczegółowe:

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

Snapshoty nie mogą używać bezpośrednio:

- `GameEngine`;
- `Match`;
- klas runtime z możliwością mutacji;
- publicznych kolekcji silnika;
- klas Androida;
- bitmap i obiektów Canvas.

Kolekcje w snapshotach powinny być niemutowalne z punktu widzenia odbiorcy.

## 9. Komunikacja komponentów

```text
BattlefieldView / HUD / AI
              │
              ▼
        MatchCommand
              │
              ▼
        MatchSession
              │
              ▼
     CommandHandler + Simulation
              │
              ├── CommandResult
              ├── MatchEvent
              └── MatchSnapshot
                         │
                         ▼
                  ViewModel / AI
```

UI nie wywołuje bezpośrednio:

```text
Match
MatchSimulation
ProjectileSystem
TerrainSystem
UnitSystem
SpellSystem
```

AI korzysta z tej samej ścieżki co gracz:

```text
MatchSnapshot
    ▼
AiAgent
    ▼
MatchCommand
```

Nie będzie osobnej ścieżki AI mutującej silnik bezpośrednio.

## 10. Zasady implementacyjne

- kontrakty są niemutowalne;
- kontrakty nie zależą od Androida;
- kontrakty nie zawierają logiki renderowania;
- kontrakty nie przechowują referencji do runtime state;
- każda komenda ma jasno określonego wykonawcę;
- każdy event opisuje jeden zakończony fakt;
- każdy błąd odrzucenia jest jawny;
- snapshot jest bezpiecznym read modelem;
- modele mogą być testowane poza Androidem;
- modele mogą zostać później serializowane bez zmiany semantyki.

## 11. Weryfikacja M2

Po implementacji M2 należy dostarczyć:

1. Testy konstrukcji wszystkich modeli bazowych.
2. Testy poprawnego tworzenia komend.
3. Testy odrzuceń z określonym powodem.
4. Testy eventów i ich danych.
5. Testy niemutowalności snapshotów.
6. Test sprawdzający brak importów `android.*` i `androidx.*`.
7. Test sprawdzający brak zależności od `GameEngine`.
8. Test kompilacji i testów wszystkich modułów.
9. Dokument kontraktów gotowy do przeglądu.

## 12. Bramka odbioru

M2 można oznaczyć jako `READY_FOR_REVIEW`, gdy:

- wszystkie modele mają jasno określone znaczenie;
- wszystkie komendy mają właściciela wykonania;
- każdy event ma opis semantyczny;
- odrzucone komendy zwracają jawny błąd;
- snapshoty nie udostępniają mutowalnego runtime state;
- `game-contracts` nie zależy od Androida;
- testy przechodzą bez emulatora;
- nie skopiowano logiki starego `GameEngine`.

Po akceptacji M2 można rozpocząć M3 — przeniesienie domeny gry.
