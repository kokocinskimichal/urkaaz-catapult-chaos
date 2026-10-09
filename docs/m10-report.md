# M10 — Raport integracji lokalnej

**Status:** `ACCEPTED`

## Zaimplementowano

- `LocalMatchGateway` jako lokalną granicę aplikacji;
- podłączenie `MatchSession` do deterministycznej symulacji;
- obsługę komend `Start`, `SelectAmmo`, `Fire` i `AdvanceSimulation`;
- lifecycle `pause`, `resume`, `stop` i `restart`;
- przyciski rozpoczęcia, strzału i kroku czasu w Android UI;
- test lokalnego meczu w pamięci;
- test restartu bez pozostałości poprzedniego stanu.

## Ograniczenia

M10 nie kończy jeszcze pełnej kampanii, persystencji nagród, tutoriala ani
pełnego HUD-u. Te elementy wymagają dalszej integracji. Renderer nadal pokazuje
podgląd assetu i proste obiekty; docelowe klatki pocisków będą mapowane z
`ProjectileSnapshot` w kolejnym kroku.

## Weryfikacja

Uruchom:

```bash
./gradlew :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap został zaakceptowany. Pełna kampania, persystencja, tutorial i dalsze
mapowanie assetów pozostają kolejnymi krokami integracji.
