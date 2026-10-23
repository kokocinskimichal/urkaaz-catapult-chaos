# M3 — Raport implementacji domeny

Status: `ACCEPTED`

Data wykonania: 2026-10-08

## Wykonane prace

Dodano czysty model domenowy w module `game-domain`:

```text
game-domain/src/main/kotlin/com/urkaaaz/domain/
├── DomainValues.kt
├── Ammunition.kt
├── Combatants.kt
├── Spells.kt
├── MatchState.kt
└── Match.kt
```

### Wartości i reguły

- `Health` z kontrolą zakresu obrażeń i leczenia;
- `ResourceWallet` z obsługą złota, many i amunicji;
- `Aim` z walidacją kierunku i siły;
- `WorldPosition`, `Velocity`, `WorldBounds`;
- `MatchState` z lifecycle meczu.

### Obiekty gameplayowe

- `Catapult`;
- `Fortress`;
- `Projectile`;
- `Unit`;
- `UnitType`.

### Amunicja

Odtworzono domenowe parametry:

```text
ROCK
SIEGE_BOMB
POWDER_BARREL
CLUSTER_BOMB
FIRE_RAIN
PLAGUE_CAULDRON
```

### Czary

Dodano:

- `SpellType`;
- `SpellCategory`;
- `SpellLoadout`.

Modele nie zawierają już identyfikatorów Android resource (`R.string`,
`R.drawable`).

## Testy

Dodano `DomainModelsTest`, który sprawdza:

- granice zdrowia;
- walidację celowania;
- pozycję wylotu pocisku;
- zużywanie amunicji;
- nielimitowaną amunicję;
- wpływ wiatru na przesunięcie pocisku;
- przejścia lifecycle meczu.

## Walidacja

Uruchomiono:

```bash
./gradlew :game-domain:test \
  :game-contracts:test \
  :game-application:test \
  :android-app:testDebugUnitTest \
  :android-app:assembleDebug \
  --no-build-cache \
  -Dkotlin.incremental=false
```

Wynik: `PASS`.

Dodatkowo:

```text
DOMAIN_ANDROID_IMPORTS=PASS
```

Nie znaleziono importów Androida w `game-domain`.

## Zakres odłożony do kolejnych etapów

M3 nie implementuje jeszcze:

- fizyki czasu rzeczywistego;
- kolizji;
- deformacji terenu;
- systemu obrażeń symulacji;
- AI;
- kampanii;
- renderingu;
- assetów;
- adaptera snapshotów;
- pełnego mapowania domeny na kontrakty.

Te elementy należą do M4 i kolejnych etapów.

M3 zostało zaakceptowane przez użytkownika 2026-10-08. Można rozpocząć M4 —
przeniesienie symulacji.
