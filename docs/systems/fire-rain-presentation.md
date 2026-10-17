# Fire Rain Presentation

## 1. Cel i zakres

Fire Rain Presentation opisuje kompletną, nową prezentację amunicji
`FIRE_RAIN`: lot pojemnika, jego rozpad przy wierzchołku toru, lot sześciu
fragmentów oraz osobne efekty trafienia z płomieniem wygasającym razem z
obszarem ognia.

Prezentacja nie zmienia fizyki ani liczby pocisków. Zatwierdzone zasady
gameplayu: ticki płonącego pola FIRE_RAIN ranią jednostki obu drużyn, jego
bezpośredni impact zachowuje dotychczasowe zasady, a lot FIRE_RAIN używa
połowy prędkości początkowej i połowy minimalnej prędkości referencyjnej
fragmentacji. Czas `LingeringArea` został wydłużony z 3 do 9 sekund, przy
zachowaniu ticków co 500 ms. Prezentacja odwzorowuje czas symulacji i wygasza
płomień w ostatnich 500 ms, nie przedłużając efektu.

Symulacja nadal odpowiada za rozpad przy wierzchołku: pocisk główny
kontynuuje lot i tworzy sześć niezależnych pocisków. Na wyraźną decyzję
użytkownika warstwa prezentacji ukrywa wizualnie pocisk główny po evencie
`ProjectileSplit`; nie usuwa go z symulacji. Widoczne pozostaje sześć
mniejszych fragmentów. Po trafieniu pociski nadal używają istniejącego
zachowania `LingeringArea`.

Prezentacja runtime jest zaimplementowana w istniejącym pipeline Androida:
wszystkie cztery fazy są ładowane i rysowane, mapper przekazuje eventy rozpadu
i aktywne pola, a lokalny zegar prezentacji obsługuje retencję, pauzę oraz
restart meczu. Pozostaje ręczna ocena wizualna w działającym meczu, w tym
przy pan/zoom.

### Zakres objęty dokumentem

- neutralny, współdzielony zestaw assetów dla obu drużyn;
- lot głównego pojemnika;
- krótki błysk i efekt rozpadu;
- wspólna animacja lotu używana przez sześć mniejszych fragmentów;
- animacja trafienia, pętla płomieni i ich wygasanie;
- mapowanie istniejących snapshotów i eventów na prezentację world-space;
- lifecycle wyłącznie prezentacyjnego stanu animacji;
- jawna walidacja assetów bez cichego podstawiania animacji ROCK.

Ikona amunicji, kontrolki HUD i balans są poza zakresem. Zatwierdzona zmiana
reguły celu ticków płonącego pola jest wąskim wyjątkiem gameplayowym.
Użytkownik zatwierdził użycie `fire_rain_icon.png` jako referencji wizualnej
w promptach AnimGen. Nie należy ręcznie tworzyć klatek bazowych ani sekwencji
animacji; animacje ma wygenerować AnimGen. Istniejące klatki FIRE_RAIN nie
będą używane jako źródło nowego zestawu.

## 2. Istniejące rozwiązania i nakładanie odpowiedzialności

`BattlefieldAssetCatalog` wymaga kompletnych sekwencji lotu pojemnika,
błysku rozpadu, lotu fragmentu i pola ognia FIRE_RAIN. `BattlefieldRenderer`
rysuje wszystkie cztery fazy przez istniejącą kamerę. Pozostałe typy amunicji
zachowują wspólny `ProjectileAnimation` i pojedynczy aktywny impact.
`RenderStateMapper` nadal wybiera ostatni ogólny impact z batcha eventów;
FIRE_RAIN nie polega na tym impactcie, ponieważ każde jego pole jest osobnym
`EffectSnapshot`.

`MatchSnapshot` publikuje listę `EffectSnapshot` z pozycją, promieniem,
pozostałym czasem, identyfikatorem efektu i źródłowego pocisku. Mapper
przekazuje aktywne pola FIRE_RAIN, a renderer rysuje je niezależnie w
world-space; faza wygasania korzysta z pozostałego czasu snapshotu.

Katalog waliduje kompletność, rozmiar i alpha klatek wszystkich faz FIRE_RAIN
i zgłasza błąd wskazujący fazę oraz indeks przy brakującym lub niepoprawnym
zasobie. Nieznany typ amunicji zgłasza błąd zamiast używać animacji ROCK.

Nie należy dodawać drugiego katalogu amunicji, drugiego mappera snapshotów ani
równoległego renderera z własną kamerą. Istniejący `BattlefieldAssetCatalog`,
`RenderStateMapper`, `BattlefieldView` i `BattlefieldRenderer` są właściwą
granicą integracji.

## 3. Właściciele odpowiedzialności

| Odpowiedzialność | Właściciel |
|---|---|
| Liczba pocisków, rozpad i pozycje | `game-simulation` |
| Tożsamość pocisku, fragmentacji i aktywnego efektu | istniejące snapshoty i eventy w `game-contracts` |
| Mapowanie danych gameplayowych na render state | `android-app.RenderStateMapper` |
| Ładowanie i walidacja klatek | istniejący `android-app.BattlefieldAssetCatalog` |
| Postęp i retencja animacji prezentacyjnych | stan widoku Androida, keyed by projectile/effect IDs |
| Rysowanie i transformacja world-space | `android-app.BattlefieldRenderer` z kamerą battlefield |
| Obrażenia ticków pola i czas efektu | definicja amunicji w `game-domain` oraz tick w `game-simulation`; Fire Rain tickuje jednostki obu drużyn |
| Bezpośredni impact | istniejąca polityka kolizji; bez zmiany zasad |

Nie powstaje nowy moduł Gradle. Gameplay nie jest przenoszony do Androida, a
renderer nie otrzymuje mutowalnych obiektów domenowych.

## 4. Wejścia i wyjścia

### Wejścia

System korzysta z już istniejących danych:

- `ProjectileSnapshot` — pozycja, typ amunicji, identyfikator i drużyna;
- `ProjectileSplit` — identyfikator pocisku głównego, ID sześciu fragmentów
  oraz world-space pozycja rozpadu;
- `EffectSnapshot` — pozycja, promień, pozostały czas, `effectId` i
  `sourceProjectileId`;
- identyfikator meczu z `MatchSnapshot`, aby resetować stan widoku przy
  rozpoczęciu nowego meczu.

Każdy nowy `EffectSnapshot` FIRE_RAIN jest źródłem osobnej animacji zapłonu i
pola ognia. Jego `sourceProjectileId` łączy trafienie z pociskiem głównym lub
fragmentem. Dzięki temu nie trzeba dodawać eventu gameplayowego wyłącznie do
animacji trafienia ani próbować parować `ProjectileHitTerrain` z osobnym
`TerrainDeformed`, który nie zawiera ID pocisku.

Nie dodaje się nowych eventów gameplayowych ani pól do `MatchSnapshot`.

### Wyjścia

`RenderStateMapper` rozszerza istniejący Androidowy render state o:

- listę eventów rozpadu przeznaczonych do krótkiej animacji;
- aktywne pola ognia odwzorowane z istniejących `EffectSnapshot`;
- dane meczu potrzebne widokowi do wykrycia restartu.

Te modele są wyłącznie read modelami Androida. Nie są odsyłane do symulacji
ani zapisywane jako stan kampanii.

## 5. Zestaw assetów

Nowe assety mają być jednym zestawem neutralnym, używanym przez BLUE i RED.
Assety nie zależą od koloru drużyny ani od modyfikatorów UI.

| Faza | Wyzwalacz | Użycie |
|---|---|---|
| Lot pojemnika | aktywny pocisk `FIRE_RAIN` przed `ProjectileSplit` | nowa pętla lotu; po evencie pocisk główny jest ukrywany wyłącznie wizualnie |
| Rozpad | `ProjectileSplit` | krótka jednorazowa animacja w world-space pozycji eventu |
| Lot fragmentu | ID fragmentu z `ProjectileSplit` obecne w snapshotach pocisków | jedna wspólna animacja ciemnego, kamiennego odłamka z pomarańczowym żarem, odtwarzana niezależnie dla każdego fragmentu |
| Trafienie i ogień | nowy `EffectSnapshot` | osobny zapłon, pętla ciągłego płomienia (klatki 2–12) bez klatek dogasania, a ostatnie 500 ms wygaszają pole zgodnie z `remainingMilliseconds` |

Jeden zestaw klatek fragmentu jest współdzielony przez sześć fragmentów;
pozycje i niezależne tory ruchu pochodzą z symulacji, nie z animacji. Styl
ma pasować do istniejących sprite'ów Urkaaaz. Ikona FIRE_RAIN może być
dołączona jako referencja obrazu, jeśli wybrany model AnimGen obsługuje taki
tryb wejścia. Nie wolno ręcznie rysować ani generować klatek bazowych. Jeśli
dostępny model wymaga osobnej klatki pierwszej i nie obsługuje obrazu
referencyjnego, należy zatrzymać się i uzyskać decyzję użytkownika zamiast
przygotowywać lub przerabiać obraz wejściowy.

Gotowe, fazowe prompty AnimGen są zapisane w
`docs/systems/fire-rain-animgen-prompts.md`. Lot pojemnika jest
wygenerowany, zaakceptowany i zaimportowany jako 24 klatki PNG 512×512 RGBA
`fire_rain_flight_00`–`23`; oryginalny ZIP i MP4 są zachowane w
`assets_src/ammunition/fire-rain/container-flight/`. Błysk rozpadu jest
wygenerowany, zaakceptowany i zaimportowany jako 12 klatek PNG 512×512 RGBA
`fire_rain_split_flash_00`–`11`; jego ZIP i MP4 są zachowane w
`assets_src/ammunition/fire-rain/split-flash/`. Użytkownik zaakceptował dla
docelowego małego rozmiaru renderowania fakt, że klatki lotu dotykają
krawędzi, a błysk zaczyna się pełną ikoną i rozszerza do krawędzi bez
wygaśnięcia w wyeksportowanym zakresie. Nie należy tych klatek przycinać ani
retuszować. W obu fazach ZIP jest źródłem klatek runtime; MP4 pozostaje
oryginalnym podglądem i nie jest assetem runtime. Użytkownik
dostarczył osobny obraz odłamka wygenerowany w innym narzędziu: 1254×1254 RGBA
z przezroczystością. Obraz został wysłany do AnimGen jako klatka początkowa,
bez pełnej ikony amunicji. Zatwierdzone zadanie za 45 kredytów zakończyło się
sukcesem i dostarczyło 24 sekwencyjne klatki RGBA 512×512. ZIP jest poprawny;
żadna klatka nie dotyka krawędzi, a minimalny margines przezroczystości wynosi
162 px. Użytkownik zaakceptował podgląd; klatki są zaimportowane jako
`fire_rain_fragment_flight_00.png`–`23.png`. Oryginalny ZIP i MP4 są zachowane
w `assets_src/ammunition/fire-rain/fragment-flight/`; tylko PNG są przeznaczone
do runtime. Klatki lotu pojemnika, błysku rozpadu i lotu fragmentu są ładowane i rysowane
przez wspólny renderer. Użytkownik dostarczył i wybrał osobny przezroczysty
obraz pola ognia 1254×1254, mimo wyższego płomienia i widocznych odłamków;
został on wysłany do AnimGen jako pierwsza klatka. Prompt animacji każe szybko
obniżyć płomień do zwartego pola. Zatwierdzona generacja za 45 kredytów
zakończyła się sukcesem. Użytkownik odrzucił pierwszy podgląd, bo klatki 01 i
02 dotykały górnej krawędzi; nie zostały zaimportowane. Poprawiona generacja,
z przezroczystym marginesem wokół pierwszej klatki i
`powder_barrel_impact_09` jako dodatkową referencją, kosztowała kolejne 45
kredytów. Podgląd przechodzi od wybuchu do niskiego pola ognia i wygaszenia,
ale klatki 03 i 04 nadal dotykają krawędzi płótna; użytkownik odrzucił ten
podgląd i klatki nie zostały zaimportowane. Następnie polecił nie używać
klatki Powder Barrel w kolejnej próbie. Następna, 24-klatkowa generacja użyła
wyłącznie powiększonej klatki
początkowej Fire Rain i kosztowała 45 kredytów. Wszystkie pliki są
sekwencyjnymi PNG RGBA 512×512; minimalny przezroczysty margines wynosi
138 pikseli, a ostatnia klatka jest całkowicie przezroczysta. Użytkownik
zaakceptował podgląd; klatki są zaimportowane jako
`fire_rain_field_00.png`–`23.png`, a oryginalny ZIP i MP4 są zachowane w
`assets_src/ammunition/fire-rain/fire-field/`. Wszystkie sekwencje są
podłączone do renderera; ścisła walidacja katalogu wymaga dokładnej liczby
klatek 512×512 z alpha i nie stosuje fallbacku do ROCK ani starych klatek
FIRE_RAIN.

Oryginalne, zatwierdzone źródła przyszłych animacji powinny zostać zachowane
poza zasobami runtime, proponowana lokalizacja:

```text
assets_src/ammunition/fire-rain/
```

Zatwierdzone klatki runtime powinny trafić do:

```text
android-app/src/main/res/drawable-nodpi/
```

Nazwy zasobów muszą być jednoznaczne dla każdej fazy, małymi literami,
sekwencyjne i zgodne z limitem nazwy Android drawable. Nie wolno zastępować
brakującej klatki inną animacją ani przycinać postaci/efektu przez krawędź
canvasu. Dokładne prefiksy, liczby klatek, wymiary, FPS i okno wygaszenia
pozostają otwarte do przeglądu przygotowanych animacji; nie są ustalane przez
ten dokument.

## 6. Przepływ danych i rysowania

```text
MatchSnapshot + MatchEvent
        ↓
RenderStateMapper
        ↓
RenderState: projectiles + split cues + active effects
        ↓
BattlefieldView: retencja animacji kluczowana stabilnymi ID
        ↓
BattlefieldRenderer + BattlefieldAssetCatalog
        ↓
world-space render przez wspólną transformację kamery
```

`MatchViewModel` nadal mapuje snapshot i batch eventów przez istniejący
`RenderStateMapper`. Nie wylicza trajektorii fragmentów ani nie interpretuje
obrażeń.

Widok przechwytuje ID pocisku głównego i listę fragmentów z `ProjectileSplit`.
ID pocisku głównego pozostaje ukryte wizualnie po rozpadzie; sześć ID
fragmentów zachowuje prezentacyjną rolę tak długo, jak odpowiadające im ID
istnieją w snapshotach. Dzięki temu animacje fragmentów pozostają właściwe
także po skonsumowaniu jednorazowego eventu. Retencja nie przechowuje pozycji
ani prędkości pocisków.

Każdy aktywny efekt ognia jest odświeżany z snapshotu kluczowanego przez
`effectId`;
pozycja i renderowany rozmiar pochodzą odpowiednio z `x`, `y` i `radius`.
Jednoczesne trafienia nie nadpisują się, ponieważ każdy efekt ma osobny ID.
Wygaszenie zmienia wyłącznie alfę/klatkę prezentacji i nie przesuwa ani nie
przedłuża gameplayowego efektu.

Wszystkie fazy, w tym rozpad, fragmenty, impact i pola ognia, są rysowane w
world-space i korzystają z transformacji battlefield dokładnie raz. HUD,
ikona FIRE_RAIN oraz kontrolki pozostają bez zmian w screen-space.
Pole ognia jest rysowane po terrain cap, aby cap go nie zakrywał, i przed
jednostkami oraz pociskami, które pozostają na wierzchnich warstwach.
Klatki pola mają duże marginesy przezroczystości, dlatego renderer rysuje
płótno w rozmiarze 4× średnicy promienia gameplayowego i kotwiczy widoczną
dolną krawędź na 374/512 wysokości płótna przy powierzchni gruntu.

## 7. Runtime state i lifecycle

Stan prezentacyjny może zawierać jedynie:

- ID eventów rozpadu i postęp ich krótkich sekwencji;
- ID fragmentów rozpoznane z eventu rozpadu i nadal obecne w snapshotach;
- postęp zapłonu/pętli/wygaszenia przypisany do `effectId`;
- ID meczu potrzebne do wykrycia restartu.

Snapshot pozostaje jedynym źródłem prawdy o aktywnych pociskach i efektach.
Stan widoku nie może przechowywać kopii ich pozycji, obrażeń, czasu
gameplayowego ani własnej listy płonących pól.

Przy utworzeniu widoku stan jest pusty. Event rozpadu rozpoczyna sekwencję
raz dla swojego ID; kolejne snapshoty aktualizują położenie istniejących
pocisków. Zniknięcie pocisku usuwa jego oznaczenie fragmentu, a zniknięcie
`EffectSnapshot` usuwa związany z nim zegar i retencję. Zmiana `matchId`
czyści wszystkie wpisy, by restart nie odziedziczył klatek poprzedniego
meczu. Odłączenie widoku wstrzymuje zegary, ale zachowuje ID na wypadek
ponownego podłączenia; stan lokalny jest zwalniany razem z trwale zniszczonym
widokiem.

Podczas pauzy lokalne zegary animacji są zatrzymane; wznowienie kontynuuje
od zapamiętanej klatki. Restart czyści zegary niezależnie od stanu
poprzedniego meczu.

## 8. Czas i deterministyczność

Prezentacja nie uczestniczy w symulacji ani nie steruje replayem. Reguła
ticków Fire Rain — jednostki obu drużyn otrzymują obrażenia, a bezpośredni
impact pozostaje bez zmian — jest wykonywana deterministycznie w
`game-simulation`. Pozycje, kolejność rozpadu, liczba pocisków, połowa
prędkości FIRE_RAIN i dziewięciosekundowy czas pola pozostają źródłem prawdy
po stronie symulacji.

Lokalny zegar renderowania może sterować klatkami, ale nie może zmieniać
`EffectSnapshot.remainingMilliseconds`, trajektorii ani eventów. Stabilne ID
zapewniają niezależny start i usuwanie jednoczesnych animacji. Przy
odtworzeniu tego samego snapshotu/eventu renderer powinien rozpocząć tę samą
fazę od pierwszej klatki, niezależnie od pozostałych pocisków.

## 9. Assety i błędy

Katalog assetów powinien jawnie walidować wymagany zestaw FIRE_RAIN:

- każda faza ma zatwierdzoną i niepustą sekwencję;
- indeksy klatek są ciągłe i w kolejności;
- pliki są prawidłowymi PNG o uzgodnionych wymiarach i kanale alpha;
- canvas, skala, ground anchor i bezpieczne marginesy są spójne między
  klatkami;
- wymagane fazy nie są przypadkowo podmienione klatkami ROCK lub starymi
  animacjami FIRE_RAIN.

Brakujący albo błędny zestaw ma zgłosić jawny błąd walidacji i nie renderować
animacji FIRE_RAIN. Nie wolno używać bieżącego sukcesopodobnego fallbacku na
ROCK ani po cichu pomijać brakujących klatek. Błąd powinien wskazywać
konkretną fazę, prefiks i indeks klatki.

## 10. Integracja z istniejącymi systemami i warianty

### Rekomendowany wariant: rozszerzenie wspólnego pipeline'u

Rozszerzyć istniejący katalog, mapper i renderer oraz dodać wąski tracker
prezentacyjny, jeśli niezależne zegary keyed by ID okażą się potrzebne.
Zachowuje to jeden asset catalog, wspólną kamerę i obecne granice
`MatchSnapshot`/`MatchEvent`.

### Alternatywa: niezależny renderer FIRE_RAIN

Zapewniłby izolację samego rysowania, ale powieliłby mapowanie, retencję
eventów, ładowanie assetów lub transformację kamery. Ma wyższy koszt
integracji i ryzyko niespójnego world-space.

### Alternatywa: ogólny system faz animacji dla całej amunicji

Mógłby później ujednolicić animacje wielu typów, lecz teraz rozszerza zakres
bez potwierdzonej potrzeby. Grozi stworzeniem drugiego abstrakcyjnego
katalogu lub kontraktu nad istniejącym `ProjectileAnimation`.

Rekomendowane jest rozszerzenie istniejącego pipeline'u, ponieważ rozwiązuje
rzeczywisty brak prezentacji FIRE_RAIN i pozwala testować przejście event →
render state → klatka bez wpływu na inne typy amunicji.

## 11. Testy i kryteria akceptacji

Testy `FireRainPresentationClockTest`, `FireRainAnimationAssetsTest` i
`RenderStateMapperTest` pokrywają kontrakty assetów, mapowania eventów,
retencji ID, restartu meczu i pauzy. Kryteria zachowania:

- każda nowa sekwencja jest kompletna, ciągła, dekodowalna i zachowuje
  alpha, wspólną skalę oraz stabilny anchor;
- niekompletne assety kończą się jawnym błędem zamiast fallbackiem do ROCK;
- mapper przekazuje wszystkie eventy rozpadu i wszystkie aktywne pola ognia,
  także gdy wiele z nich powstanie w jednym ticku;
- sześć ID z `ProjectileSplit` otrzymuje klatki fragmentów, a pocisk główny
  pozostaje w symulacji, lecz jest ukryty w prezentacji;
- po zniknięciu pocisku/efektu retencja jest usuwana, a restart nowego meczu
  czyści poprzedni stan;
- każde aktywne `EffectSnapshot` FIRE_RAIN jest rysowane w jego world-space
  pozycji i promieniu; efekt trwa zgodnie ze snapshotem, jego końcowe 500 ms
  to fade, a prezentacja nie wydłuża gameplayowego czasu;
- ticki płonącego pola ranią jednostki obu drużyn, ale bezpośredni impact
  zachowuje dotychczasową politykę;
- pan, zoom i resize stosują wspólną transformację kamery do wszystkich faz;
- powtarzane ID, eventy i snapshoty nie duplikują ani nie restartują bez
  powodu już odtwarzanych animacji;
- żaden gameplayowy moduł nie importuje Androida, a renderowanie nie mutuje
  symulacji ani domeny.

Kryterium wizualne do ręcznej oceny: gracz widzi pojemnik w locie, czytelny
rozpad, sześć niezależnych torów fragmentów bez widocznego pocisku głównego
po rozpadzie, osobny zapłon każdego utworzonego pola oraz płomienie
wygasające w ostatnich 500 ms czasu wskazanego przez snapshot.

## 12. Zaimplementowane pliki

Integracja wykorzystuje istniejące granice odpowiedzialności:

- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldAssetCatalog.kt` —
  rejestracja faz i jawna walidacja zasobów;
- `android-app/src/main/java/com/urkaaaz/ui/RenderStateMapper.kt` —
  mapowanie wszystkich eventów i efektów FIRE_RAIN;
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldView.kt` —
  lifecycle oraz retencja animacji keyed by IDs;
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldRenderer.kt` —
  rysowanie faz world-space;
- `android-app/src/main/java/com/urkaaaz/ui/FireRainPresentationClock.kt` —
  tylko jeśli istniejący stan BattlefieldView nie wystarczy do niezależnego
  postępu i czyszczenia animacji;
- `android-app/src/test/java/com/urkaaaz/ui/FireRainPresentationClockTest.kt` —
  retencja, reset meczu, niezależne efekty i pauza;
- `android-app/src/test/java/com/urkaaaz/RenderStateMapperTest.kt` —
  mapowanie wielu eventów rozpadu i aktywnych pól;
- `android-app/src/test/java/com/urkaaaz/ui/FireRainAnimationAssetsTest.kt` —
  kompletność i poprawność wygenerowanych sekwencji;
- `android-app/src/main/res/drawable-nodpi/` — nowe, zatwierdzone klatki;
- `assets_src/ammunition/fire-rain/` — zatwierdzone oryginały i podglądy;
- `docs/PROJECT_KNOWLEDGE.md` — po implementacji, trwałe fakty o
  zatwierdzonych assetach i zachowaniu renderera.

Dokument nie zatwierdza nowych nazw kontraktów gameplayowych ani zmian
`MatchSnapshot`.

## 13. Plan implementacji

Poniższe etapy są planem historycznym; status rezultatów odzwierciedla
obecną implementację. Generacje i importy wymagające zgody zostały
zatwierdzone przez użytkownika.

### Generowanie przez AnimGen

Animacje mają być generowane wyłącznie przez oficjalne AnimGen Public API
(`https://api.animgen.com/v1`), a nie traktowane jako gotowe lub już
dostarczone assety. Dla `FIRE_RAIN` powstaje jeden neutralny zestaw — bez
osobnych wariantów BLUE/RED. Każda faza jest osobnym zadaniem AnimGen
opartym o zatwierdzony prompt i ikonę FIRE_RAIN jako referencję wizualną,
jeśli live model obsługuje tryb referencyjnych obrazów.

Przed generacją należy sprawdzić aktualny katalog modeli i ich obsługiwane
ustawienia; nie zakładać z góry identyfikatora modelu, rozdzielczości,
proporcji, przezroczystości ani limitów. Przygotować klatki jako osobny
eksport (`frames_zip`, jeśli jest dostępny dla wybranego modelu), aby
zweryfikować każdą klatkę i zintegrować zatwierdzoną sekwencję.

Każde płatne zadanie wymaga świeżej wyceny i jawnej zgody użytkownika przed
wywołaniem tworzącym animację. Generować jedno zadanie naraz. Po zakończeniu
pobrać i ocenić wynik, pokazać podgląd oraz uzyskać osobną zgodę na import.
Zmiana promptu lub ustawień oznacza nowe zadanie i nową wycenę. Nie kupować
kredytów ani nie ponawiać niejednoznacznego płatnego żądania jako nowego
zadania bez bezpiecznego wznowienia oryginalnego.

Klucz API musi pochodzić z prywatnego środowiska lub systemowego Keychain;
nie wolno prosić o wklejenie go do rozmowy, drukować go ani zapisywać w
repozytorium. Prompty, identyfikatory zadań i dane robocze pozostają w
prywatnym katalogu sesji. Do AnimGen wolno wysyłać wyłącznie zatwierdzone
źródło wizualne oraz prompt danej fazy. Opisany workflow nie uruchamia teraz
generacji ani nie zatwierdza wydatku.

| Etap | Prace i zależności | Rezultat / bramka |
|---|---|---|
| 1. Specyfikacja animacji | Przygotować i zatwierdzić fazowe prompty; bez ręcznego tworzenia klatek. | Prompty zapisane w `fire-rain-animgen-prompts.md`. |
| 2. Generacja w AnimGen i przegląd | Wygenerować, obejrzeć i uzyskać zgodę na import każdej fazy. | Cztery zaakceptowane sekwencje zaimportowane; pole ognia wygenerowano bez referencji Powder Barrel. |
| 3. Walidacja i archiwizacja assetów | Zachować oryginały i walidować zestaw runtime. | Oryginały są w `assets_src`; test PNG i katalog sprawdzają kompletność, wymiary i alpha. |
| 4. Katalog i kontrakty prezentacyjne | Mapować eventy/efekty do render state i rygorystycznie ładować sekwencje. | Zaimplementowane bez zmian kontraktów gameplayowych. |
| 5. Tracker i lifecycle | Obsłużyć ID, reset, cleanup i pauzę/wznowienie. | Zegar prezentacji i lifecycle widoku są zaimplementowane oraz objęte testami. |
| 6. Rysowanie faz | Rysować pojemnik, fragmenty, błysk i pola przez wspólną kamerę. | Zaimplementowane; pozostaje ręczny przegląd wizualny w aktywnym meczu przy pan/zoom. |
| 7. Integracja i regresja | Uruchomić testy i aktualizować wiedzę projektu. | Pełne `./gradlew test assembleDebug --quiet` oraz `git diff --check` przeszły; ręczna ocena wizualna w aktywnym meczu pozostaje do wykonania. |

### Zależności między etapami

Etap 1 blokuje generację z etapu 2. Etap 2 blokuje import i końcową walidację
z etapu 3. Nazwy faz uzgodnione w etapie 1 są kontraktem dla katalogu
assetów z etapu 4. Mapper, tracker i renderer mogą być implementowane po
zamknięciu tego kontraktu, ale finalny test assetów i przegląd całości
wymagają ukończenia etapu 3.

### Kryteria ukończenia implementacji

- Pojemnik leci, rozpada się w punkcie eventu; po rozpadzie pocisk główny
  pozostaje w symulacji, ale jest ukryty wizualnie, a widocznych jest sześć
  mniejszych fragmentów z symulacyjnymi pozycjami.
- Każdy utworzony efekt FIRE_RAIN ma własny zapłon, pętlę i wygaszenie
  sterowane obecnym `EffectSnapshot`; równoczesne trafienia nie nadpisują się.
- Wszystkie fazy korzystają z world-space i wspólnej kamery; HUD oraz ikona
  amunicji pozostają bez zmian.
- Brakujące lub niepoprawne assety są wykrywane jawnie, bez klatek ROCK ani
  cichego pomijania.
- Gameplay, liczba pocisków, obrażenia, czas efektów i replay nie zależą od
  stanu ani zegara Androida.
- Każda płatna generacja i każdy import mają osobną zgodę użytkownika.

### Referencje AnimGen

- [AnimGen API quickstart](https://animgen.com/docs/en/api/quickstart)
- [Modele i możliwości](https://animgen.com/docs/en/api-reference/list-models)
- [Wycena animacji](https://animgen.com/docs/en/api-reference/quote-animations)
- [Tworzenie animacji](https://animgen.com/docs/en/api-reference/create-animations)
- [Odpytanie zadania i pobieranie wyników](https://animgen.com/docs/en/api/polling-and-downloads)

## 14. Otwarte decyzje i niedokończony zakres

- integracja prezentacji błysku rozpadu, lotu fragmentu i pola ognia; źródło
  pola ognia, osobna wycena, generacja, preview i import;
- integracja Androidowego mappera, trackera i renderera dla wszystkich faz;
- liczba klatek, wymiary, FPS, skala world-space i wspólny ground anchor;
- dokładne parametry eksportu wynikające z możliwości zaakceptowanego modelu
  AnimGen.

Te decyzje należy zamknąć w etapie 1 przed przygotowaniem i akceptacją
animacji. Generacja oraz import pozostają osobnym, późniejszym zadaniem; ten
dokument nie rozpoczyna ani nie wycenia płatnej generacji.
