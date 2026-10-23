# M11 — Macierz porównawcza

## Cel

M11 porównuje zachowanie nowej implementacji z zakresem starej gry. Porównanie
jest wykonywane na poziomie komend, snapshotów, eventów i wyników, a nie przez
kopiowanie `GameEngine` do nowego projektu.

## Wykonany wycinek

| Obszar | Nowa wersja | Porównanie |
|---|---|---|
| start meczu | `MatchStarted`, `TurnChanged`, snapshot | deterministyczny trace |
| lot pocisku | pozycja i prędkość po ticku | deterministyczny trace |
| wiatr | zmiana prędkości poziomej | test snapshotu |
| kolizja z terenem | impact i krater | test eventów i rewizji |
| restart | nowy identyfikator i pusty stan | test gatewaya |
| AI | snapshot → komendy | test agenta |
| poziomy kampanii | poziom → `MatchScenario` | test progów i biomów |

## Różnice jawnie odłożone

Pełna zgodność ze starą wersją nie jest jeszcze możliwa dla jednostek,
garrisonu, fal, wszystkich zaklęć, bossów, persystencji, tutoriala i pełnego
renderingu. Te elementy nie zostały jeszcze przeniesione do nowego projektu.

Każda taka różnica jest traktowana jako brak zakresu, a nie jako zaakceptowana
zmiana mechaniki.

## Reprodukowalność

`SimulationReplayRegressionTest` wykonuje tę samą sekwencję komend i kroków
czasu dwukrotnie, a następnie porównuje pełne listy snapshotów i eventów.
