# Plan optymalizacji renderowania pola bitwy

> Status: implementacja pipeline'u assetów zakończona. Pomiar emulatora
> potwierdza usunięcie głównego hotspota i spadek pamięci, ale cele FPS oraz
> p95 nie są spełnione stabilnie; pozostały koszt wskazuje emulatorowy
> RenderThread/Skia GL.

## Cel

Usunąć powtarzające się dekodowanie bitmap i kosztowne przygotowanie klatek z
hot path renderowania Android battlefield. Zachować obecny wygląd, rytm animacji,
kontrakty render state i deterministyczną symulację. Priorytetem jest Level 5,
ale rozwiązanie ma obejmować wszystkie mapy i animowane efekty.

## Pomiar bazowy

Pomiar wykonano na emulatorze `sdk_gphone16k_arm64`, w landscape przy
`2400 × 1080`, podczas aktywnego meczu Chapter 1, Level 5:

| Pomiar | Wynik |
|---|---:|
| `dumpsys gfxinfo`, świeże 10 s | 245 klatek, 239 janky (97,55%) |
| Frame time | p50 40 ms, p90 77 ms, p95 97 ms, p99 150 ms |
| UI thread / draw | 239 slow UI-thread frames, 239 slow draw-command frames |
| `simpleperf`, 9,98 s, 99 Hz | 735 próbek, 0 utraconych |
| Najczęstsze symbole CPU | `png_read_filter_row_paeth4_neon` 27,48%, `inflate_fast_chunk_` 21,50%, `SkSwizzler` 5,85% |
| `dumpsys meminfo` | 723 980 KiB PSS, 660 845 KiB native heap |

Call graph prowadził przez
`BattlefieldView.onDraw` → `BattlefieldRenderer.render` →
`BattlefieldRenderer.drawMapDecorations` →
`BattlefieldAssetCatalog.mapDecoration` → `loadBitmap` →
`BitmapFactory.decodeResource`. Chapter 1, Level 5 ma sześć dekoracji mapy;
renderer rozwiązuje ich bitmapy przy każdym rysowaniu. Przy zmierzonym tempie
około 24,5 FPS oznacza to do około 147 dekodowań dekoracji na sekundę. To jest
główny, bezpośrednio potwierdzony hotspot.

Pozostałe źródła kosztu wymagają optymalizacji po usunięciu tego hotspota:

- `BattlefieldAssetCatalog` dekoduje z góry całe grupy efektów podczas
  konstrukcji katalogu. Fire Rain, Fortress Shield, Haste, Runic Bastion i
  eksplozja Demolishera zajmują łącznie około 232 MiB surowych bitmap
  `ARGB_8888` przy pełnej rozdzielczości klatek, bez pozostałych pocisków,
  tekstur biomów i animacji postaci.
- Animacje drużynowe dekodują pełne sekwencje neutralne; RED tworzy też
  odbite bitmapy. Maski mają cache 4 MiB, a recolorowane klatki 6 MiB, co jest
  mniejsze od części sekwencji. Może to powodować ponowne dekodowanie masek i
  ponowne przeliczanie pikseli podczas pętli animacji.
- Boss ma osobny cache LRU 4 MiB dla klatek idle i ataku. W zebranym profilu
  nie był to dominujący łańcuch CPU; nie należy zaczynać od bezwarunkowego
  zwiększenia tego cache'u.

Wyniki są baseline'em emulatora, nie gwarancją dla każdego urządzenia.
`gfxinfo` używa progów klatek systemu Android; należy równolegle mierzyć
docelowe 30 aktualizacji na sekundę, zgodne z bieżącym tickiem ekranu meczu.

## Wyniki po wdrożeniu

Pomiar wykonano po rozgrzaniu assetów na tym samym emulatorze
`sdk_gphone16k_arm64`, w Level 5 przy `2400 × 1080`. Urządzenia fizycznego nie
profilowano.

| Pomiar | Przed | Po |
|---|---:|---:|
| PSS | 723 980 KiB | 280 364 KiB (−61,3%) |
| Native heap | 660 845 KiB | 229 232 KiB (−65,3%) |
| Pierwsza próbka `gfxinfo` (10 s) | 245 klatek / 24,5 FPS | 285 klatek / 28,5 FPS |
| `gfxinfo` p50 / p90 / p95 / p99 | 40 / 77 / 97 / 150 ms | 19 / 24 / 25 / 73 ms |
| Powtórzona próbka po zmianie | — | 287 klatek / 28,7 FPS; 36 / 48 / 53 / 57 ms |
| Końcowa próbka finalnego builda | — | 286 klatek / 28,6 FPS; 20 / 32 / 44 / 53 ms |

Po rozgrzaniu call graph głównego wątku nie zawierał już dekodowania
`BitmapFactory.decodeResource` dla dekoracji ani `TeamTexturePixelTransform.recolor`.
Dominujący pozostały koszt profilu wskazywał na `RenderThread` i emulatorowy
QEMU/Skia GL buffer submission. Wyniki frame time różniły się między próbkami:
29 FPS nie zostało osiągnięte w żadnej z trzech próbek; p95 przekroczył 33 ms
w dwóch, a p99 raz przekroczył limit 66 ms. Wysoki odsetek janky z `gfxinfo`
nie jest bezpośrednio porównywalny z celem gry 30 FPS, ponieważ system
klasyfikuje klatki według progów 60 Hz. Dalsza optymalizacja renderowania GPU
wymaga osobnego pomiaru, najlepiej również na urządzeniu fizycznym.

Weryfikacja Level 5 ujawniła też skakanie animacji Ancient War Oak przy
asynchronicznym ładowaniu. Renderer wcześniej pokazywał statyczny fallback,
gdy bieżąca klatka idle/ataku była jeszcze pending. Katalog żąda teraz dwóch
kolejnych klatek z wyprzedzeniem, a renderer utrzymuje ostatnią gotową klatkę
tego samego bossa także podczas przejścia idle/atak. Zachowuje metadane układu
tej klatki do czasu załadowania nowego typu animacji; zegar pozostaje bez zmian.

## Zakres i granice

### W zakresie

1. Cache bitmap dekoracji mapy i Relics po stabilnym kluczu assetu lub
   rozpoznanym zasobie Androida; w steady state `onDraw` nie może dekodować
   ponownie tych samych bitmap.
2. Sprawdzenie widoczności obiektu przed kosztownym rozwiązaniem assetu, jeśli
   culling można wykonać bez zmiany widocznego obrazu.
3. Lazy loading i kontrolowane, lifecycle-aware preloading sekwencji animacji,
   aby konstrukcja `BattlefieldView` nie dekodowała nieużywanych efektów.
4. Przeniesienie dekodowania i przygotowania wariantów team texture poza
   `onDraw` oraz render thread. Cache ma być ograniczony rozmiarem bitmap
   (`allocationByteCount`) i nie może stale wyrzucać kolejnych klatek aktywnej
   sekwencji.
5. Redukcja rozdzielczości klatek tylko wtedy, gdy zmierzony rozmiar ekranowy
   na urządzeniu uzasadnia zmianę; zachować alpha, art bounds, kotwicę i
   czytelność.
6. Pomiar tej samej ścieżki przed zmianą i po niej, z rozdzieleniem kosztu
   startowego od steady state.

### Poza zakresem

- Reguły gameplayu, `MatchSnapshot`, eventy, tick symulacji, balans i replay.
- Zmiana liczby klatek, czasu animacji, pauzy lub synchronizacji efektów z
  eventami gameplayowymi.
- Zmiana naming convention, generowanie lub usuwanie źródłowych assetów.
- Duży refaktor rendererów, nowy równoległy katalog assetów albo globalny
  cache bez jawnego właściciela.
- Zwiększanie cache'u bossa do rozmiaru wszystkich pełnorozdzielczych klatek
  bez pomiaru budżetu pamięci na urządzeniu docelowym.

## Kolejność prac

### 1. Usunąć powtarzane dekodowanie statycznych assetów

- Użyć `BattlefieldAssetCatalog` jako właściciela cache'u bitmap, bez
  dekodowania dekoracji w każdej iteracji `drawMapDecorations`.
- Objąć tym samym rozwiązaniem `relicBitmap`, jeśli profil potwierdzi
  powtarzane dekodowanie Relics.
- Rozważyć cache identyfikatora zasobu wraz z bitmapą, aby nie wykonywać
  `Resources.getIdentifier` w hot path.
- Wykonać culling przed rozwiązaniem bitmapy, ale zachować asset bounds,
  rotację i rysowanie obiektów widocznych częściowo przy krawędzi viewportu.

### 2. Ograniczyć eager loading i dekodowanie animacji

- Usunąć dekodowanie pełnych, nieużywanych sekwencji z inicjalizatorów pól
  katalogu.
- Ładować grupy animacji na żądanie i przygotowywać je poza głównym wątkiem,
  z deduplikacją równoczesnych żądań tego samego assetu.
- Render ma korzystać wyłącznie z gotowych, niezmiennych bitmap; nie może
  blokować na `Future`, `Deferred` ani dekoderze.
- Pending presentation musi być jawnym stanem. Brak lub błąd wymaganego assetu
  nadal ma być jawny; nie zastępować go cichym sukcesopodobnym fallbackiem.
- Nie dopuścić, by opóźnienie I/O zmieniało simulation time, kolejność eventów
  lub gameplayową chwilę aktywacji efektu.

### 3. Zoptymalizować kolorowane sekwencje postaci i bossy

- Zmierzyć liczbę wywołań `TeamTextureBitmapCache.recolor` oraz dekodowań masek
  na sekwencję i drużynę.
- Przygotować lub recolorować potrzebne klatki poza renderowaniem, współdzielić
  gotowe warianty i dobrać ograniczony cache do rzeczywistych rozmiarów.
- Boss LRU stroić dopiero po ponownym profilu. Nie przechowywać bez potrzeby
  całej pełnorozdzielczej biblioteki klatek w RAM.

### 4. Powtórzyć profil i sprawdzić regresje

Powtórzyć pomiar na tym samym emulatorze i rozdzielczości, w aktywnym Level 5,
zarówno w pierwszych sekundach ładowania, jak i po rozgrzaniu assetów. Dodatkowo
sprawdzić biomy z różną liczbą dekoracji, Relics, animację postaci, bossa,
Fortress Shield, Fire Rain i Runic Bastion.

## Kryteria akceptacji

- `mapDecoration`/`relicBitmap` nie dekodują ponownie tego samego klucza przy
  kolejnych klatkach. Test cache'u weryfikuje liczbę wywołań dekodera i jawne
  zachowanie dla brakującego zasobu.
- Po rozgrzaniu assetów call graph steady-state nie zawiera ścieżki
  `drawMapDecorations` → `BitmapFactory.decodeResource`; dekodowanie i
  recoloring nie wykonują się na głównym wątku ani render thread.
- Sekwencje nieużywane w meczu nie są dekodowane przy tworzeniu
  `BattlefieldView`. Cache jest ograniczony i nie rośnie monotonicznie podczas
  pięciominutowego meczu.
- Zachowane są rozmiary, alpha, art bounds, orientacja, kolejność renderowania,
  frame index, timing, pauza, restart i widoczne zachowanie istniejących
  animacji.
- Na tym samym emulatorze aktywny Level 5 osiąga co najmniej 29 FPS w
  steady-state próbce 10 s, p95 frame time nie przekracza 33 ms, a p99 nie
  przekracza 66 ms. PNG decode nie jest dominującym kosztem CPU.
- PSS i peak bitmap-cache bytes są zapisane przed i po zmianie; celem jest co
  najmniej 20% niższy PSS względem baseline'u `723 980 KiB`, bez OOM ani
  wzrostu pamięci wraz z czasem meczu. Jeśli pomiar emulatora nie odzwierciedla
  budżetu docelowego urządzenia, wynik trzeba oznaczyć i uzasadnić.
- Przechodzą testy modułów, `./gradlew test assembleDebug --quiet` oraz
  `git diff --check`. Jeśli Studio zostanie dotknięte, uruchomić też `cd studio
  && npm test && npm run lint`.

## Ważne pliki

- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldView.kt`
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldRenderer.kt`
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldAssetCatalog.kt`
- `android-app/src/main/java/com/urkaaaz/ui/TeamTextureBitmapCache.kt`
- `android-app/src/main/java/com/urkaaaz/ui/BossPresentationClock.kt`
- `android-app/src/main/java/com/urkaaaz/ui/MatchScreenController.kt`
