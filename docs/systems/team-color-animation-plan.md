# Plan: dynamiczna podmiana koloru i tekstury animacji

Status: **pilot Defendera zaimplementowany; użytkownik zatwierdził rozszerzenie
mechanizmu na pozostałe jednostki. Demolisher ma wspólne idle, move i death**.
Maski są automatycznym draftem wymagającym przeglądu wizualnego. Rozwija pomysł
z [`ideas.md`](../project/ideas.md) („Wspólne
animacje postaci z kolorem drużyny nakładanym w runtime”).

## Cel

Zamiast osobnych sekwencji BLUE i RED mieć jedną neutralną sekwencję na
animację oraz maskę stroju. W runtime renderer wypełnia zamaskowany obszar
wybranym kolorem lub teksturą, zachowując cieniowanie. Skóra i wyposażenie
poza maską nie zmieniają barwy. Mechanizm ma pozwalać na dowolne palety, nie
tylko BLUE/RED.

## Decyzje

- Pilot: **Defender**, wszystkie 4 animacje (idle 60, move 60, attack 30,
  death 60 klatek; wszystkie 256×256). Demolisher używa wspólnego idle
  (48×256×256), move (60×256×256) i death (32×768×432). Szeroki kadr death
  zachowuje miejsce na poziomy rzut i eksplozję; renderer stosuje dla death
  skalę 2,42× zamiast 2×, aby wyrównać widoczny rozmiar postaci do idle/move;
  attack pozostaje do migracji.
- Maska jest **osobnym PNG na każdą klatkę**, a nie dodatkowym kanałem tekstury.
- Tekstura jest nakładana **prostą maską** na klatkę; bez map UV, więc wzór może
  lekko przesuwać się między klatkami.
- Zmiana dotyczy tylko assetów i rendererów Androida. Symulacja, domena i
  balans nie są ruszane.

## Kroki

1. **Neutralne klatki** — jedna sekwencja na animację, wyprowadzona z obecnych
   klatek BLUE. Stany o poziomym ruchu mogą zachować szeroki kadr, bez
   kadrowania do kwadratowej rozdzielczości.
2. **Maski** — osobne alpha-PNG w
   `android-app/src/main/assets/animations/defender/masks/<typ>/<NNN>.png`.
   Są wstępnie wygenerowane segmentacją niebieskich pikseli klatek BLUE;
   maskują także drobne niebieskie detale oraz niebieskie pole tarczy, które
   wcześniej prześwitywały w grze. Nie są jeszcze zatwierdzone artystycznie i
   mogą wymagać korekt.
3. **Konfiguracja tekstur** — `android-app/src/main/assets/animations/defender/palettes.json`
   przypisuje tekstury PNG do drużyn BLUE/RED. Przykładowe tekstury materiału są
   w tym samym katalogu. Dodanie nowej tekstury nie wymaga zmian w rendererze.
4. **Renderer** — wspólny loader łączy neutralną klatkę, maskę i teksturę,
   zachowuje luminancję źródła oraz przezroczystość. Wyniki są buforowane z
   limitem LRU 6 MiB. RED używa poziomego odbicia neutralnej klatki. Stan
   attack Demolishera i animacje pozostałych jednostek pozostają na razie przy
   istniejących assetach BLUE/RED.
5. **Testy** — zgodność liczby i rozmiaru klatek z maskami, zachowanie
   przezroczystości oraz test czystej transformacji pikseli.
6. **Studio** — Animations pobiera neutralne klatki, maski i tekstury przez API;
   przełącznik BLUE/RED pokazuje wynik, a Show mask ujawnia maskę na czerwono.
   Lista sekwencji z teksturą drużyny jest konfiguracją, niezależną od
   konkretnej jednostki.
7. **Dokumentacja** — konwencja nazw masek, format palet, `knowledge.md`,
   `CHANGELOG.md`.

## Ryzyka i ograniczenia

- Jakość automatycznych masek 350 klatek nie jest zatwierdzona; trzeba
  przejrzeć wszystkie klatki i poprawić błędnie zaklasyfikowane piksele.
- Prosta maska teksturowa nie podąża za deformacją stroju.
- Przemalowanie klatek zwiększa zużycie pamięci cache'u; trzeba go ograniczyć
  do używanych palet.
- RED jest teraz odbiciem klatek BLUE, więc subtelne różnice w animacji ataku
  (np. ręka trzymająca broń) nie są zachowane.
- Neutralna baza pochodzi z BLUE; niezamaskowane detale wyposażenia (np.
  metalowe elementy i skóra) pozostają bez zmian przy teksturze RED.
