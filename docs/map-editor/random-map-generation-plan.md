# Plan: Random Map w Urkaaaz Studio

**Status: zaimplementowano lokalnie i zwalidowano.** Random Map działa jako
narzędzie authoringu zakładki Maps; gra nadal ładuje konkretną, zapisaną
definicję mapy. Dokument zachowuje zaakceptowane decyzje i szczegóły
implementacji.

Prompt wykonawczy: [random-map-generation-implementation-prompt.md](random-map-generation-implementation-prompt.md).

## Cel

Dodać do Maps Studio generator losowych szkiców mapy. Jedno kliknięcie ma
wygenerować profil terenu, niezależne wysokości fundamentów BLUE/RED i nowy
zestaw zwykłych dekoracji. Użytkownik może zmieniać ustawienia generatora,
ponawiać losowanie, ręcznie poprawiać mapę i dopiero potem zapisać ją przez
istniejący dialog wyboru Chapter/Level.

Generator nie jest częścią runtime'u gry. Po zapisie gra dostaje zwykły,
zwalidowany `MapDefinition` z pełnymi próbkami i placementami; podczas meczu
żaden element mapy nie jest losowany.

## Stan obecny i istniejące granice

- Edytor znajduje się w `studio/web/src/tabs/maps/`.
- `domain/map.ts` definiuje `MapDocumentJson`, domyślną mapę, `sampleStep`,
  geometrię fortec/katapult, strefy chronione i helpery profilu.
- `domain/operations.ts` implementuje niemutowalne operacje mapy, generację
  Random Map i ciągłą edycję bez blokady fazowej.
- `state/store.ts` obsługuje reducer, komunikaty błędów oraz historię
  undo/redo; pojedyncza operacja jest jedną pozycją historii.
- `domain/assets.ts` udostępnia palety drzew, skał, propsów i Relics według
  biomu, korzystając z istniejącego indeksu Studio API.
- `format/codec.ts` jest granicą JSON. Zapisany format ma `schemaVersion: 1`,
  przechowuje teren, fortecy, dekoracje i `generationMetadata`; katapulty są
  wyprowadzane z fortec i nie są serializowane.
- `MapsTab.tsx` zapisuje wyłącznie po jawnej akcji **Save to repository**.
  Dialog wybiera Chapter i globalny Level, wyprowadza ID `chapterN-levelX`,
  waliduje dokument i zapisuje obie kopie map przez API z kontrolą hash.
- Kotlinowy `MapDefinitionValidator` pozostaje autorytatywnym walidatorem
  runtime. TypeScriptowy `validate` sprawdza wymagane przez generator
  ograniczenia profilu, sample step 20/40, delta 200, fundamenty, stref,
  assety i footprinty Relics zgodnie z runtime; testy map obejmują istniejące
  mapy oraz wyniki presetów.

## Uzgodnione decyzje produktowe

| Obszar | Decyzja |
|---|---|
| Biom | Losowanie zachowuje biom aktualnego szkicu. |
| Zakres ponownego losowania | Każde kliknięcie zastępuje teren, wysokości fundamentów i zwykłe dekoracje; zachowuje `mapId`, biom, wymiary, `sampleStep`, ustawienia kamery oraz istniejące Relics. |
| Historia | Całe losowanie jest jedną atomową operacją undo/redo. Błąd generowania nie zmienia dokumentu. |
| Teren | Łagodne, szerokie pagórki i doliny; środkowy korytarz mapy zachowuje niewielką zmienność. |
| Fundamenty | Wysokości BLUE i RED są losowane niezależnie, lecz w bezpiecznych granicach różnicy i walidatora. |
| Intensywność terenu | Płynny suwak 100–600 world units, krok 1; domyślnie 160, zachowując poprzednią wartość Medium. |
| Gęstość dekoracji | Wybór Low/Medium/High, domyślnie Medium. |
| Dekoracje | Losowa liczba w zakresie wybranego presetu; generowane są tylko drzewa, skały i propsy aktualnego biomu. Generator nie dodaje Relics. |
| Wysokość dekoracji | Każdy zwykły obiekt ma niezależne, ręcznie edytowalne `Y` w world-space. Zmiana terenu nie przesuwa obiektów; Random Map początkowo umieszcza je na powierzchni. |
| Blokada terenu | Usunąć `Lock terrain` jako kontrolkę, operację i blokadę/fazę. Teren i dekoracje pozostają edytowalne. |
| Cap | Zamiast blokady dodać przełącznik **Show cap**/**Hide cap**. Steruje wyłącznie podglądem; nie zmienia JSON, autosave mapy ani walidacji. Domyślnie cap jest ukryty. |
| Zapis | Losowanie i kontrolki nie zapisują plików repozytorium. Zapis nadal odbywa się przez istniejący dialog Chapter/Level i bezpieczne API. |

### Amplituda terenu i gęstość dekoracji

Amplituda jest bezpośrednią wartością suwaka **Terrain intensity** w
world units: od 100 do 600, co 1; domyślna wartość 160 zachowuje poprzednie
domyślne zachowanie Medium. Suwak skaluje losowe odchylenia od bazowej linii
między fundamentami; nie jest parametrem balansu ani konfiguracją runtime.

| Ustawienie | Wartość |
|---|---:|
| Amplituda terenu | 100–600 world units; domyślnie 160 |
| Gęstość dekoracji Low | 3–6 |
| Gęstość dekoracji Medium | 7–11 |
| Gęstość dekoracji High | 12–18 |

Bazowa linia terenu interpoluje wysokość pomiędzy fundamentami. Każda wartość
suwaka musi generować profil zgodny z walidatorem; środkowy korytarz zachowuje
ograniczoną zmienność. Zakresy dekoracji są liczbą łączną dla drzew, skał i
propsów; rozkład kategorii ma korzystać z dostępnych assetów biomu.

Robocza definicja korytarza to środkowa 1/3 szerokości mapy
(`x=2400..4800`) z odchyleniem maksymalnie około 40 world units od bazowej
linii między fundamentami. To ograniczenie dotyczy profilu terenu, nie
rozmieszczenia dekoracji; dekoracje nie mają kolizji gameplayowych.

Obecna mapa Chapter 1 Level 4 ma różnicę fundamentów 520 world units i 8
zwykłych dekoracji. Jako początkową górną granicę różnicy fundamentów generator
powinien przyjąć **520 world units** albo mniejszą wartość, jeśli walidacja
profilu lub podgląd wykażą potrzebę. Ta granica nie może być kopiowana do
balansu ani mylona z limitem 200 world units między sąsiednimi próbkami terenu.

Zakres amplitudy 100–600, zakresy dekoracji i korytarz ±40 są ustawieniami
authoringu generatora; nie są nowymi parametrami `game-balance.json`.

## Docelowy przepływ

```text
Terrain intensity + Decoration density
                   │
                   ▼
             Random Map click
                   │ nowy seed
                   ▼
     deterministyczny generator TypeScript
                   │ kandydat MapDefinition
                   ▼
 istniejący walidator Studio + walidacja reguł runtime
          ┌────────┴─────────┐
          │ poprawny         │ błąd
          ▼                  ▼
 jedna operacja store       jawny komunikat;
 + undo/redo                 szkic bez zmian
          │
          ▼
 ręczna edycja / kolejny reroll
          │
          ▼
 istniejący Save Chapter/Level + expectedHash
          │
          ▼
 obie kopie JSON, bez runtime RNG
```

## Model i algorytm generatora

1. Dodać czysty moduł domenowy w `studio/web/src/tabs/maps/domain/`, który
   przyjmuje bieżący dokument, seed, amplitudę terenu, preset gęstości i
   dostępne asset keys, a zwraca nowy kandydat albo typowany/jawny błąd.
2. Seed tworzy frontend przez Web Crypto przy każdym kliknięciu. Sama generacja
   używa jawnego, wersjonowanego PRNG i nie korzysta z `Math.random`.
   Identyczny seed, wejściowy dokument, amplituda i preset gęstości muszą dać
   identyczny wynik.
3. Próbkuj profil zgodny z istniejącym `sampleStep` (nowa mapa domyślnie 40;
   format dopuszcza także 20). Próbki mają dokładnie pokrywać `x=0..7200`,
   mieścić się w `0..4800`, mieć delta sąsiadów nie większą niż 200 i tworzyć
   szerokie, gładkie formy. W środkowej części mapy odchylenie od bazowej linii
   fundamentów pozostaje małe.
4. Losuj fundamenty BLUE i RED niezależnie w granicach świata oraz przyjętego
   limitu różnicy. Utrzymuj płaskie, chronione odcinki pod każdą fortecą,
   strefy obejmujące ich placementy i gładkie przejścia do terenu. Aktualizuj
   `fortresses`, `protectedTerrainZones` i wewnętrzne, pochodne katapulty
   atomowo. Nie zmieniaj X, szerokości, assetów ani hitboxów fortec; nie
   serializuj katapult do JSON.
5. Pozostaw istniejące Relics bez zmian: te same ID, assety, rozmiary,
   współrzędne i transformacje. Generator uwzględnia ich footprinty podczas
   próbkowania, tak aby nowy profil nie naruszył soil validation. Jeśli nie
   da się zbudować poprawnego kandydata, zgłoś błąd i pozostaw szkic bez zmian.
6. Wybierz zwykłe dekoracje z `paletteAssetsForBiome`/istniejącego indeksu dla
   aktualnego biomu. Nie wybieraj Relics. Losuj pozycje poza strefami
   fundamentów i granicami świata; przypisz unikalne, deterministyczne ID
   pochodne od seed + indeks. Wygeneruj ich początkowe `y` na powierzchni
   terenu; po generacji `y` pozostaje niezależną współrzędną world-space.
7. Ręczne operacje terenu nie zmieniają pozycji żadnych dekoracji. Panel
   zaznaczonego zwykłego obiektu udostępnia liczbową wysokość `Y`, a przeciąganie
   obiektu swobodnie zmienia jego X i Y. Relics są również ręcznie przesuwalne,
   ale operacja, która naruszyłaby ich footprint gleby, jest odrzucana.
8. Ustaw `generationMetadata.seed` i stabilny `generatorVersion`. Zachowaj
   `authoringVersion` oraz pozostałe niegenerowane pola dokumentu.
9. Przed dispatch do store zwaliduj cały kandydat. Jeśli walidacja lub
   generator zwróci błąd, nie zapisuj częściowych próbek ani obiektów.

## Zmiany UI i stanu edytora

- W istniejącym panelu Maps dodaj suwak **Terrain intensity** od 100 do 600
  world units z krokiem 1 i widocznym bieżącym wynikiem; początkowo ustaw 160.
- **Decoration density** pozostaje selectem Low/Medium/High, domyślnie Medium.
  Suwak i preset gęstości są ustawieniami edytora, a nie częścią
  `MapDefinition`, autosave mapy ani balansu gry.
- `Y` zwykłej dekoracji jest niezależną pozycją world-space. Zmiana terenu nie
  przesuwa dekoracji; przeciąganie i pole wysokości edytują tę pozycję ręcznie.
- Każde kliknięcie generuje nowy seed i podmienia szkic jako jedna operacja.
  Przycisk pozostaje dostępny po wcześniejszym losowaniu oraz po ręcznej edycji.
- Użyj istniejącego store i jego historii. Undo przywraca pełny dokument
  sprzed losowania; redo odtwarza dokładnie ten sam rezultat.
- Usuń blokady fazowe z operacji i UI: teren, fundamenty oraz dekoracje można
  edytować niezależnie od siebie. Usuń martwe `EditorPhase`/`LOCK_TERRAIN`/
  `MARK_READY_TO_EXPORT` elementy po sprawdzeniu wszystkich call sites.
- Usuń warunek renderujący cap wyłącznie po zablokowaniu terenu. Cap ma być
  kontrolowany przez osobny stan prezentacyjny, z przełącznikiem
  **Show cap**/**Hide cap**. Stan ten nie wpływa na dirty state, JSON,
  autosave ani wynik `validate`.
- Zachowaj istniejący Save dialog, konflikt hash i atomowy zapis obu plików.
  `Random Map` nie wywołuje `saveMap` ani endpointu zapisu.

## Zakres poza zadaniem

- losowanie lub zmiana biomu;
- losowanie Relics;
- nowy format map, zmiana `schemaVersion` lub nowe pola runtime;
- losowość w symulacji meczu;
- generowanie map podczas startu gry;
- zmiana balansu, gameplayu, dostępności map albo zasad kampanii;
- nowy endpoint/API, zapis serwerowy lub automatyczny zapis do repozytorium;
- zmiana fortec/katapult poza bezpieczną wysokością fundamentów;
- nowy asset manifest lub duplikat katalogu assetów;
- migawki, współdzielenie map, eksport do innego formatu.

## Testy i kryteria akceptacji

### Generator i kontrakt

- ten sam seed, biome, wejściowe Relics, amplituda i preset gęstości dają identyczny
  `MapDefinition`, metadata i kolejność dekoracji;
- różne seedy zmieniają co najmniej część profilu/fundamentów/dekoracji;
- biome, `mapId`, world size, `sampleStep`, kamera i Relics są zachowane;
- amplitudy 100, 160 i 600 dają niemalejący zakres różnorodności, a także
  wartości pośrednie suwaka generują poprawny profil; korytarz środka ma niski
  relief;
- sample spacing, krańce mapy, world bounds i delta `<= 200` są poprawne;
- fundamenty losują się niezależnie, ale przechodzą wszystkie reguły
  `MapDefinitionValidator`; chronione strefy i katapulty pozostają zsynchronizowane;
- liczba dekoracji mieści się w wybranym zakresie; asset keys są dostępne
  w wybranym biomie; ID nie kolidują z zachowanymi Relics;
- generator nie używa `Math.random` ani zegara systemowego wewnątrz PRNG;
- konflikt ograniczeń z Relics zwraca jawny błąd bez zmiany dokumentu.

### Operacje, UI i zapis

- zmiana terenu nie modyfikuje `y` zwykłych dekoracji; drag i pole `Y`
  pozwalają ręcznie ustawić ich world-space wysokość;
- Relics pozostają nieruchome i nie mogą zostać cicho wyniesione z gleby;
- Random Map, Undo i Redo traktują cały wygenerowany dokument atomowo;
- Random Map nie dotyka repozytorium; Save nadal wybiera Chapter/Level,
  używa bieżącego hash i zapisuje obie kopie;
- `Show cap`/`Hide cap` przełącza tylko warstwę renderingu i nie zmienia
  walidacji, JSON ani dirty status;
- nie ma już operacji, stanu ani tekstu UI blokującego teren przez
  `Lock terrain`; assety i teren można edytować w dowolnej kolejności;
- TypeScriptowy walidator wygenerowanych map jest zgodny z Kotlinowym
  `MapDefinitionValidator` w wymaganiach, które generator musi spełnić.

### Walidacja implementacji

```bash
cd studio
npm test
npm run lint
npm run build
cd ..
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Zmiana nie wymaga commita ani pusha bez osobnego polecenia użytkownika.

## Wynik implementacji

- Generator znajduje się w `studio/web/src/tabs/maps/domain/randomMap.ts`;
  operacja `GENERATE_RANDOM_MAP` zapisuje seed i `generatorVersion`.
- Suwak amplitudy ma zakres 100–600 i domyślną wartość 160; gęstość dekoracji
  domyślnie wynosi Medium. Wygenerowane dekoracje początkowo lądują na terenie,
  ale ich `Y` pozostaje bezwzględną współrzędną world-space.
  Istniejące Relics pozostają niezmienione i blokują tylko operacje naruszające
  ich soil footprint.
- `EditorPhase`, `Lock terrain` i fazowe blokowanie edycji zostały usunięte.
  Cap jest niezależnym stanem preview.
- Weryfikacja obejmuje 45 generacji na wszystkich 5 mapach JSON w katalogu
  kampanii, wszystkie 96 testów web Studio, 66 testów server Studio, lint,
  build i `./gradlew test assembleDebug --quiet`.
