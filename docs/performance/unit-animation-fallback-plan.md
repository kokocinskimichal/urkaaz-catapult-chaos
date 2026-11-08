# Plan: ciągłość sprite'ów jednostek przy asynchronicznym ładowaniu

## Status i diagnoza

Debugowy wpis `UnitAnimationFallback` potwierdził przyczynę na emulatorze
Pixel 8, 2026-11-08. W pierwszym przypadku renderer nie miał ani klatki
animacji, ani bitmapy zapasowej:

```text
type=DEFENDER team=BLUE state=MOVING animation=move
frame=33/60 frameStatus=PENDING
fallbackStatus=PENDING fallbackAsset=drawable:defender_left
```

W kolejnych przypadkach klatka była wcześniej gotowa, ale nie znajdowała się
już w cache:

```text
frame=0/60 frameStatus=CACHE_MISS_AFTER_READY
fallbackStatus=PENDING fallbackAsset=drawable:defender_left
```

Jednostka pozostaje w stanie meczu; jej sprite zostaje zastąpiony przez
renderowany przez `BattlefieldRenderer` kolorowy okrąg. Pierwsza brakująca
klatka była klatką 33, a nie początkiem sekwencji. Samo wstępne załadowanie
klatki 0 nie rozwiąże więc wszystkich pierwszych żądań. Powtarzające się
`CACHE_MISS_AFTER_READY` są zgodne z wyrzucaniem klatek z ograniczonego cache'u.
To są pomiary emulatora; urządzenia fizycznego jeszcze nie sprawdzano.

## Cel

Nie pokazywać awaryjnego kolorowego okręgu podczas zwykłego oczekiwania na
klatkę. Przy pierwszym renderowaniu jednostki używać przygotowanego sprite'a
zapasowego, a przy późniejszych brakach utrzymać ostatnią poprawną prezentację
tej samej jednostki. Nie zmieniać symulacji, animacyjnych zegarów ani ich
timingu.

## Rozwiązanie

### 1. Mały zestaw startowy assetów

- Przed `MatchCommand.Start` wyznaczyć osobne zestawy: BLUE z zatwierdzonego
  `PlayerLoadoutConfig` oraz RED z `MatchScenario.availableUnits`.
  W trybach testowych i demo użyć faktycznie dozwolonych zestawów z istniejącej
  konfiguracji, nie tworzyć równoległego katalogu dostępności ani nie
  przygotowywać niepotrzebnych kombinacji typ/drużyna.
- W `BattlefieldAssetCatalog` przygotować dla tych typów i drużyn po jednej
  bezpiecznej bitmapie startowej: istniejący sprite zapasowy, a dla typów,
  które go nie mają, reprezentatywną gotową klatkę idle.
- Zachować ten ograniczony zestaw dostępny przez generację meczu. Nie
  dekodować klatek na głównym wątku ani nie zwiększać globalnego cache'u do
  rozmiaru pełnych sekwencji.
- Pokazać ekran ładowania ze spinnerem i postępem tylko do czasu gotowości
  tego małego zestawu. Dopiero potem uruchomić sesję meczu. Błąd dekodowania
  pozostaje jawny i blokuje start z możliwością retry/powrotu.

### 2. Ostatnia gotowa prezentacja per jednostka

- W warstwie renderowania przechowywać ostatni poprawny `UnitDrawableFrame`
  dla jednostki, razem z bitmapą i metadanymi potrzebnymi do jej narysowania:
  skalą, kotwicą, przezroczystym dolnym paddingiem oraz przesunięciem X.
- Ograniczyć hold do 4 MiB przez access-order LRU ważony
  `Bitmap.allocationByteCount`; po evikcji pierwszym zabezpieczeniem pozostaje
  przypięty sprite startowy.
- Jeśli żądana klatka jest `PENDING`, `CACHE_MISS_AFTER_READY` lub
  `QUEUE_REJECTED`, narysować tę prezentację zamiast awaryjnego okręgu.
  Podmienić ją na nową dopiero po uzyskaniu gotowej klatki.
- Dla jednostki bez wcześniejszej klatki użyć przygotowanego assetu startowego.
  Nie łączyć starej bitmapy z geometrią nowej sekwencji; zachować razem bitmapę
  i jej metadane.
- Kluczować stan po `matchGeneration` i `entityId`, kontrolując też typ oraz
  drużynę. Usuwać go po zniknięciu jednostki i przy restarcie meczu, aby klatka
  nie przechodziła na inną jednostkę.
- Nie dodawać crossfade ani nie resetować zegarów animacji w pierwszej wersji.
  Priorytetem jest brak zniknięcia bez zmiany tempa i synchronizacji.

### 3. Diagnostyka

- Zachować debugowy tag `UnitAnimationFallback` dla faktycznego narysowania
  okręgu.
- Dodać ograniczony log `unit_sprite_frame_held` przy użyciu ostatniej gotowej
  klatki, z jednostką, sekwencją, indeksem, statusem oraz kluczem assetu.
- Nie logować kolejnych klatek w pętli renderowania, gdy przyczyna się nie
  zmieniła. Nie logować tych zdarzeń w release.

## Testy

- `BitmapFrameSequence` rozróżnia `PENDING`, `QUEUE_REJECTED`,
  `CACHE_MISS_AFTER_READY`, `MISSING_RESOURCE` i brak sekwencji; błąd dekodowania
  nadal jest jawny.
- Test stanu renderowania potwierdza, że oczekująca klatka używa poprzedniej
  prezentacji tylko dla tej samej jednostki, drużyny, typu i generacji meczu.
- Nowa jednostka używa przygotowanego assetu startowego; restart, śmierć lub
  usunięcie jednostki nie pozostawia starej klatki w renderze.
- Testy sprawdzają, że log fallbacku nie powtarza się co klatkę, ale pojawia
  się ponownie po odzyskaniu sprite'a i nowym braku.

## Kryteria akceptacji

- W reprodukcji poziomu 1 lub 13 nie pojawia się kolorowy okrąg, gdy jednostka
  przechodzi między stanami lub cache nie ma bieżącej klatki.
- Pierwszy render używa przygotowanego assetu; późniejsze braki utrzymują
  ostatnią poprawną prezentację do czasu załadowania nowej klatki.
- Logcat pozwala odróżnić cold `PENDING`, cache miss po wcześniejszej gotowości,
  odrzucenie kolejki, brak zasobu i użycie ostatniej klatki.
- Zużycie pamięci pozostaje ograniczone do małego zestawu startowego i
  istniejącego cache'u. Nie wczytuje się wszystkich klatek postaci.
- Nie zmieniają się `MatchSnapshot`, stan gameplayu, komendy/eventy, ruch,
  indeksowanie animacji, pauza, śmierć ani lifecycle sesji.
- Dodatkowy cache ostatnich prezentacji jest ograniczony do 4 MiB; preload
  obejmuje tylko potrzebne typy i warianty BLUE/RED.
- Przechodzą `./gradlew test assembleDebug --quiet` i `git diff --check`.

## Stan implementacji — 2026-11-08

Wdrożono preload przypiętych fallbacków przed startem meczu, overlay ze
spinnerem/postępem oraz retry/powrotem przy błędach assetów. Zestaw jest
wyznaczany osobno dla BLUE i RED. Renderer trzyma kompletną ostatnią
prezentację jednostki w 4 MiB access-order LRU; gdy klatka jest pending albo
została wyrzucona z cache'u, wykorzystuje poprzednią prezentację, a dla nowej
jednostki — przypięty fallback.

`./gradlew test assembleDebug --quiet` i `git diff --check` przechodzą.
Na emulatorze Pixel 8 uruchomiono poziom 13; logcat pokazał zarówno `PENDING`,
jak i `CACHE_MISS_AFTER_READY` obsłużone wpisem `unit_sprite_frame_held` przy
`fallbackStatus=READY`. W tej reprodukcji nie pojawił się
`unit_sprite_fallback`; urządzenia fizycznego nie sprawdzano.
