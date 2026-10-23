# Balance Simulation (`game-balance`)

Headless, deterministic AI-vs-AI matches on the real engine (no Android).
BLUE is the reference player (ELITE AI), RED uses the campaign level's profile.
Every seed is played twice with the initial wind mirrored.

## Components
- `BalanceMatchRunner` / `BalanceMatchConfig` — one match, 33 ms steps, 10 min cap.
- `BalanceMetrics`, `BalanceReport` — per-match metrics, aggregates, md/csv/json.
- `DifficultyTargets` — BLUE win rate 90% (level 1) → 55% (level 31, equal-skill ELITE) → 45% (level 100), ±10 pp.
- `GameBalance` (domain) — injectable unit/ammunition catalogs and supply;
  `GameBalance.default` equals the shipped game.
- `BalanceTuner`, `RedHealthScan` — parameter search tools.

## Tasks
- `./gradlew :game-balance:balanceBaseline -PbalanceArgs="seeds dir levels"`
- `./gradlew :game-balance:balanceTune -PbalanceArgs="candidates seeds dir levels randomSeed"`
- `./gradlew :game-balance:balanceRedHealth -PbalanceArgs="seeds"`

## Levers (shipped)
CADET/VETERAN aim error and reaction time (`AiAgent.kt`), ammunition reload (halved). Global unit/ammunition multipliers barely move the curve.
