# M11 — Raport porównania regresyjnego

**Status:** `READY_FOR_REVIEW`

## Zaimplementowano

- deterministyczny trace symulacji;
- powtórzenie identycznej sekwencji komend i kroków czasu;
- porównanie pełnych snapshotów i eventów;
- obserwowalność lotu pocisku, wiatru, impactu i deformacji terenu;
- macierz porównawcza funkcji przeniesionych i jeszcze brakujących;
- jawne rozróżnienie regresji od zakresu, który nie został jeszcze
  zmigrowany.

## Wynik

Wykonany wycinek regresyjny jest powtarzalny: dwa identyczne przebiegi dają
identyczne snapshoty i eventy. Nie oznacza to jeszcze pełnej zgodności z
oryginalną grą, ponieważ część systemów nie została jeszcze przeniesiona.

## Weryfikacja

Uruchom:

```bash
./gradlew :game-simulation:test :game-campaign:test :game-ai:test :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --no-build-cache -Dkotlin.incremental=false
```

Etap zatrzymuje się na `READY_FOR_REVIEW`. Pełna akceptacja M11 wymaga
uzupełnienia brakujących systemów albo formalnego zaakceptowania ich zakresu.
