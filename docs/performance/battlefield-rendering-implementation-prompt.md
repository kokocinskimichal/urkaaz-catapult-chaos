# Prompt implementacyjny: optymalizacja renderowania pola bitwy

Skopiuj ten prompt do agenta implementującego. Najpierw przeczytaj
[`battlefield-rendering-optimization-plan.md`](battlefield-rendering-optimization-plan.md)
oraz aktualne instrukcje repozytorium i źródła wskazane poniżej.

## Zadanie

Zoptymalizuj pipeline bitmap i animacji Android battlefield. Usuń
bezpośrednio potwierdzony koszt dekodowania map decorations w każdej klatce,
a następnie ogranicz eager loading, dekodowanie i recoloring pełnych sekwencji
animacji. Zachowaj prezentację i gameplay bez zmian.

Profil Level 5 z 2026-11-03: aktywny mecz na emulatorze `sdk_gphone16k_arm64`,
`2400 × 1080`. Świeża próbka `gfxinfo` przez 10 sekund miała 245 klatek,
239 janky (97,55%), p50 40 ms, p90 77 ms, p95 97 ms i p99 150 ms.
`simpleperf` próbkował 9,98 s przy 99 Hz (735 próbek, brak utrat); największe
symbole CPU to PNG Paeth filter (27,48%), zlib inflate (21,50%) i Skia
RGBA swizzle (5,85%). Call graph wskazuje:

```text
BattlefieldView.onDraw
  → BattlefieldRenderer.render
    → BattlefieldRenderer.drawMapDecorations
      → BattlefieldAssetCatalog.mapDecoration
        → loadBitmap
          → BitmapFactory.decodeResource
```

Level 5 ma sześć dekoracji mapy. `drawMapDecorations` rozwiązuje je przy każdym
rysowaniu, a `mapDecoration` nie cache'uje wyniku `loadBitmap`. Zmierzony PSS
procesu wynosił `723 980 KiB`, native heap `660 845 KiB`. Są to wyniki
emulatora, nie telefonu docelowego. Szczegóły, ograniczenia i kryteria są w
planie optymalizacji.

## Obowiązkowy zakres

1. Najpierw usuń powtarzane dekodowanie bitmap statycznych. Użyj istniejącego
   `BattlefieldAssetCatalog` jako właściciela cache'u dla dekoracji, a także
   Relics, jeśli potwierdzisz analogiczny hot path. Cache musi być keyed po
   stabilnym kluczu zasobu i nie może zmieniać błędów brakujących wymaganych
   assetów.
2. Przenieś culling przed rozwiązanie bitmapy, jeśli można zachować obiekty
   częściowo widoczne przy granicy viewportu.
3. Usuń eager decode wszystkich grup animacji z konstrukcji katalogu.
   Dekoduj/preloaduj sekwencje tylko wtedy, gdy mogą być potrzebne dla
   bieżącego meczu, poza głównym wątkiem. Render nie może czekać na I/O.
4. Zapewnij deduplikację równoczesnych żądań, ograniczony cache ważony
   `Bitmap.allocationByteCount` i cleanup związany z lifecycle ekranu.
   Nie naprawiaj thrashingu samym zwiększeniem limitu cache'u do rozmiaru
   wszystkich pełnorozdzielczych klatek.
5. Zmierz `TeamTextureBitmapCache` i maski: nie wykonuj pełnego pikselowego
   recoloringu ani mask PNG decode w `onDraw`. Zachowaj dokładną paletę,
   luminancję, alpha i mirroring BLUE/RED.
6. Boss cache optymalizuj dopiero po głównym hotspocie. Profil nie wskazuje
   animacji bossa jako dominującego kosztu tego przebiegu.

Nie zmieniaj reguł ani ticka symulacji, komend/eventów, `MatchSnapshot`,
balansu, asset naming convention, liczby klatek lub ich czasu. Nie wprowadzaj
drugiego katalogu assetów ani zależności platformowych do modułów core. Nie
połykaj błędów i nie dodawaj cichych fallbacków. Przed kodowaniem przeczytaj
`AGENTS.md`, `docs/architecture/architecture.md` i testy dotykanych modułów.

## Testy i pomiar

- Dodaj test cache'u/dekodera, który potwierdza, że wielokrotne żądanie tej
  samej dekoracji nie wywołuje wielokrotnego decode. Jeżeli potrzebny jest
  seam testowy, ogranicz go do katalogu assetów; nie twórz ogólnego frameworka.
- Dodaj testy dla lazy loading, limitu pamięci i propagowania błędów assetów.
- Zachowaj dotychczasowe testy clocków animacji i rozszerz je tylko tam, gdzie
  zmienia się kontrakt ładowania; frame index, pause, restart i czas gry muszą
  pozostać bez zmian.
- Ponów pomiar w aktywnym Level 5 na tym samym emulatorze. Po rozgrzaniu
  rejestruj 10 sekund `gfxinfo` oraz `simpleperf` `cpu-clock` przy 99 Hz.
  Potwierdź brak ścieżki dekodowania dekoracji w call graph steady-state;
  porównaj FPS/frame times, CPU decode share i PSS z baseline'em.
- Zapisz krótki pre/post wynik w końcowym raporcie, wyraźnie oddzielając
  pomiar emulatora od urządzenia fizycznego.

## Walidacja i granice pracy

Uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
```

Jeśli zmieniasz `studio/`, uruchom również `cd studio && npm test && npm run lint`.
Nie commituj ani nie pushuj, chyba że użytkownik wyraźnie o to poprosi.
