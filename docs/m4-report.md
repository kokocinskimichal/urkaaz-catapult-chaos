# M4 — Raport implementacji

**Status:** `ACCEPTED`

## Zaimplementowano

- jawny zegar symulacji w milisekundach;
- deterministyczny lot pocisku z grawitacją i wpływem wiatru;
- kolizję z terenem i rewizję kraterów;
- kolizję z fortecą i eventy obrażeń;
- projekcję do `MatchSnapshot` dla katapult, fortec, pocisków, terenu, fazy,
  statusu, aktywnej drużyny i wiatru;
- deterministyczne identyfikatory eventów i ich kolejność;
- testy symulacji obejmujące lifecycle, wiatr, snapshoty, impact i deformację
  terenu.

## Jeszcze niemigrowane

Stary silnik nie został skopiowany. Ruch jednostek, AI, czary, reguły
magazynów i przeładowania, zaawansowane siatki terenu, rendering, animacje,
persystencja i integracja z Androidem pozostają poza M4.

## Weryfikacja

Uruchom:

```bash
./gradlew :game-simulation:test :game-domain:test :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap został zaakceptowany. M5 może być realizowane niezależnie od dalszej
migracji elementów niewchodzących w zakres M4.
