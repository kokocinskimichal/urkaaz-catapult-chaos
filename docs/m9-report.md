# M9 — Raport migracji Android UI

**Status:** `ACCEPTED`

## Zaimplementowano

- `MatchUiAction` jako wejściowy kontrakt intencji UI;
- `MatchViewModel` tłumaczący intencje na `MatchCommand`;
- `RenderStateMapper` tłumaczący `MatchSnapshot` na model prezentacji;
- `BattlefieldView` rysujący wyłącznie model prezentacji;
- nowy ekran Androida z akcją rozpoczęcia meczu;
- test mapowania snapshotu do stanu UI;
- podłączenie `rock_flight_00` do `BattlefieldView` jako jawnego podglądu
  assetu;
- podłączenie tekstur terenu, fortec i katapult do `BattlefieldView`;
- zachowanie skopiowanych assetów `rock_flight` i `rock_impact` bez
  bezpośredniego uzależniania domeny od zasobów.

## Nie skopiowano

Stary `MainActivity`, `GameView`, logiki AI, logiki obrażeń, bezpośrednich
referencji do silnika, obsługi kampanii i pełnej nawigacji.

## Weryfikacja

Uruchom:

```bash
./gradlew :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap został zaakceptowany. Pełne podłączenie animacji do rzeczywistych
`ProjectileSnapshot` pozostaje kolejnym krokiem integracyjnym przed M10.
