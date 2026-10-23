# M10 — Integracja lokalna

Android komunikuje się z meczem przez:

```text
Android UI
  → LocalMatchGateway
  → MatchSession
  → DeterministicMatchSimulation
  → MatchSnapshot
  → Android UI
```

Gateway nie zawiera reguł gry. Odpowiada wyłącznie za lokalne połączenie
aplikacji z sesją, a reguły pozostają w domenie i symulacji.
