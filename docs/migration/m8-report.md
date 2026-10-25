# M8 — Raport kopiowania assetów

**Status:** `ACCEPTED`

## Zaimplementowano

- utworzono docelowy katalog `android-app/src/main/res/drawable-nodpi`;
- skopiowano 32 klatki `anim_ammunition_rock_flight`;
- skopiowano 32 klatki `anim_ammunition_rock_impact`;
- zachowano nazwy, format PNG, kanał alfa i `nodpi`;
- dodano manifest źródło → cel z właścicielem assetów;
- nie skopiowano assetów bez właściciela w nowej architekturze.

## Weryfikacja

Uruchom:

```bash
test "$(find android-app/src/main/res/drawable-nodpi -name 'anim_ammunition_rock_flight_*.png' | wc -l)" -eq 32
test "$(find android-app/src/main/res/drawable-nodpi -name 'anim_ammunition_rock_impact_*.png' | wc -l)" -eq 32
./gradlew :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap zatrzymuje się na `READY_FOR_REVIEW` i wymaga jawnej akceptacji przed M9.
