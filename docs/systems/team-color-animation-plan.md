# Plan: dynamiczna podmiana koloru i tekstury animacji

Status: **pilot Defendera zaimplementowany; maski są automatycznym draftem
wymagającym przeglądu wizualnego**. Nie rozszerzać na inne jednostki bez osobnej
akceptacji. Rozwija pomysł z [`ideas.md`](../project/ideas.md) („Wspólne
animacje postaci z kolorem drużyny nakładanym w runtime”).

## Cel

Zamiast osobnych sekwencji BLUE i RED mieć jedną neutralną sekwencję na
animację oraz maskę stroju. W runtime renderer wypełnia zamaskowany obszar
wybranym kolorem lub teksturą, zachowując cieniowanie. Skóra i wyposażenie
poza maską nie zmieniają barwy. Mechanizm ma pozwalać na dowolne palety, nie
tylko BLUE/RED.

## Decyzje

- Pilot: **Defender**, wszystkie 4 animacje (idle 60, move 60, attack 30,
  death 60 klatek; wszystkie 256×256).
- Maska jest **osobnym PNG na każdą klatkę**, a nie dodatkowym kanałem tekstury.
- Tekstura jest nakładana **prostą maską** na klatkę; bez map UV, więc wzór może
  lekko przesuwać się między klatkami.
- Zmiana dotyczy tylko assetów i rendererów Androida. Symulacja, domena i
  balans nie są ruszane.

## Kroki

1. **Neutralne klatki** — jedna sekwencja na animację, wyprowadzona z obecnych
   klatek niebieskich (spójna rozdzielczość 256×256).
2. **Maski** — osobne alpha-PNG w
   `android-app/src/main/assets/animations/defender/masks/<typ>/<NNN>.png`.
   Są wstępnie wygenerowane segmentacją niebieskich pikseli klatek BLUE;
   maskują także drobne niebieskie detale oraz niebieskie pole tarczy, które
   wcześniej prześwitywały w grze. Nie są jeszcze zatwierdzone artystycznie i
   mogą wymagać korekt.
3. **Konfiguracja tekstur** — `android-app/src/main/assets/animations/defender/palettes.json`
   przypisuje tekstury PNG do drużyn BLUE/RED. Przykładowe tekstury materiału są
   w tym samym katalogu. Dodanie nowej tekstury nie wymaga zmian w rendererze.
4. **Renderer** — `TeamTextureBitmapCache` łączy neutralną klatkę, maskę i
   teksturę, zachowuje luminancję źródła oraz przezroczystość. Wyniki są
   buforowane z limitem LRU 6 MiB. RED używa poziomego odbicia neutralnej klatki.
5. **Testy** — zgodność liczby i rozmiaru klatek z maskami, zachowanie
   przezroczystości oraz test czystej transformacji pikseli.
6. **Studio** — Animations pobiera neutralne klatki, maski i tekstury przez API;
   przełącznik BLUE/RED pokazuje wynik, a Show mask ujawnia maskę na czerwono.
7. **Dokumentacja** — konwencja nazw masek, format palet, `knowledge.md`,
   `CHANGELOG.md`.

## Ryzyka i ograniczenia

- Jakość automatycznych masek 210 klatek nie jest zatwierdzona; trzeba
  przejrzeć wszystkie klatki i poprawić błędnie zaklasyfikowane piksele.
- Prosta maska teksturowa nie podąża za deformacją stroju.
- Przemalowanie klatek zwiększa zużycie pamięci cache'u; trzeba go ograniczyć
  do używanych palet.
- RED jest teraz odbiciem klatek BLUE, więc subtelne różnice w animacji ataku
  (np. ręka trzymająca broń) nie są zachowane.
- Neutralna baza pochodzi z BLUE; niezamaskowane detale wyposażenia (np.
  metalowe elementy i skóra) pozostają bez zmian przy teksturze RED.
