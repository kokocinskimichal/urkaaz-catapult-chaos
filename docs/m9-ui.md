# M9 — Migracja Android UI

## Zakres pierwszego wycinka

M9 wprowadza pierwszy ekran nowego UI bez kopiowania starego `MainActivity`
ani `GameView`. Przepływ prezentacji wygląda następująco:

```text
MatchUiAction
    → MatchViewModel
    → MatchSession
    → MatchSnapshot
    → RenderStateMapper
    → BattlefieldView
```

Ekran zawiera status meczu, prosty widok pola bitwy i akcję rozpoczęcia meczu.
Renderer korzysta wyłącznie z `RenderState`, a nie z domeny, symulacji ani
mutowalnego stanu silnika.

## Celowe ograniczenia

Nawigacja kampanii, loadout, pełne sterowanie pociskiem, HUD, eventy audio,
renderer assetów i integracja lokalnego gatewaya są kolejnymi częściami M9/M10.
Ten etap ustanawia granicę UI–aplikacja i działający pionowy slice.
