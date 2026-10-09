---
name: addBiome
description: Add a new biome to Urkaaaz, wire its terrain theme, gameplay modifiers, assets, documentation, tests, and generate a copy-ready terrain AGP.
argument-hint: "[optional biome name]"
---

# /addBiome — dodawanie nowego biomu

Używaj tego skilla, gdy użytkownik wywoła `/addBiome` albo poprosi o dodanie
nowego biomu do gry Urkaaaz! Catapult Chaos.

## Kontrakt rozmowy

Zadawaj pytania pojedynczo, nigdy nie łącz kilku pytań w jednej wiadomości:

1. Zapytaj o nazwę biomu. Jeśli nazwa znajduje się w zatwierdzonym katalogu
   biomów w `GLOSSARY.md` lub `AGENT.md`, użyj jej bez ponownego proponowania.
2. Zapytaj o główny klimat i paletę biomu, oferując krótką podpowiedź
   dopasowaną do nazwy.
3. Zapytaj o główny materiał podłoża i jego warianty: trawa, ziemia, skała,
   błoto, popiół, lód, piasek, kryształ lub materiał magiczny.
4. Zapytaj o charakterystyczne dekoracje i tło.
5. Zapytaj, czy biom ma mieć wpływ gameplayowy. Jeśli tak, dopytaj osobno
   o każdy konkretny hazard, modifier albo obstacle oraz jego reguły.
6. Dopytaj tylko o brakujące parametry konieczne do implementacji, takie jak
   zakres poziomów, chapter, seed, ruch jednostek, widoczność, obrażenia,
   tarcie, deformowalność terenu lub zasady kolizji.

Jeżeli użytkownik podał informację wcześniej, zachowaj ją jako źródło prawdy
i nie pytaj o nią ponownie. Nie dodawaj gameplayowego efektu na podstawie
samej estetyki biomu.

## Interaktywny workflow generowania assetów

Nie generuj całego zestawu assetów w jednym AGP. Generatory obrazów często
łączą elementy w jeden arkusz albo gubią kontrakt alpha. Pracuj etapami,
zatrzymując się po każdym etapie i prosząc użytkownika o akceptację. Następny
AGP wolno przygotować dopiero po zatwierdzeniu poprzedniego wyniku.

Kolejność etapów:

1. `background` — jeden osobny, nieprzezroczysty PNG. Generuj go na podstawie
   tła Green Frontier jako referencji kompozycji: zachowaj podobny układ
   horyzontu, głębię planów, balans lewej i prawej strony, czytelne miejsce
   dla dwóch zamków/fortec — jednego po lewej i jednego po prawej — oraz
   spokojny obszar dla torów pocisków. Zmień materiały,
   paletę, atmosferę i elementy biomu; nie kopiuj pikseli, znaków fortec ani
   konkretnych obiektów z referencji.
2. `fortress pair` — jedna wspólna AGP dla całej pary, która produkuje dwa
   osobne PNG-i: forteca lewa i forteca prawa.
   Dla pierwszych 10 chapterów wszystkie fortece mają ten sam rozmiar,
   proporcje, skalę i niezmienne miejsca na maga oraz katapultę. Fortece mają
   być podobne funkcjonalnie, ale stylizowane materiałami i dekoracjami
   zgodnymi z motywem biomu. Użytkownik dołączy w jednym AGP referencyjne
   grafiki obu istniejących fortec; wspólny prompt musi wyraźnie wskazywać
   osobną referencję lewej i prawej fortecy jako input. Nie zmieniaj kotwic,
   punktów gameplayowych ani miejsca wejścia/wyjścia pocisków. Jeśli konstrukcja
   fortec jest lustrzanym odbiciem, flagi na obu fortecach muszą być skierowane
   w tę samą stronę świata/ekranu; nie wolno ich odwracać razem z budynkiem.
   Paleta konstrukcji fortec Copperwood musi pozostać leśno-miedziana: ciemne
   brązowe drewno, ciepły kamień, miedź, rdzawy pomarańcz i stonowana zieleń
   mchu. Oznaczenia stron są obowiązkowe: lewa forteca używa niebieskich
   akcentów (flagi, proporce i małe elementy heraldyczne), a prawa forteca
   używa czerwonych akcentów (flagi, proporce i małe elementy heraldyczne).
   Czerwień ma występować tylko jako kolor oznaczeń prawej fortecy, nie jako
   dominujący kolor jej murów lub całej konstrukcji.
   Każda forteca musi być eksportowana jako transparentny canvas `1024 × 768 px`
   i musi mieć w AGP dokładnie wskazane, co do pojedynczego piksela, punkty
   gameplayowe liczone od pełnego canvasa: środek platformy lewej katapulty
   `(284, 592)`, środek platformy prawej katapulty `(745, 592)`, dolna kotwica
   lewego maga `(473, 225)` oraz dolna kotwica prawego maga `(585, 225)`.
   Te współrzędne są wyliczone z kanonicznego układu Demo i są niezmienne dla
   wszystkich biomów, chapterów i poziomów. Nie wolno ich wyznaczać od obrysu
   widocznej grafiki ani zmieniać przez lustrzane odbicie. Generator ma zostawić
   dokładnie te miejsca czytelne i używalne, a walidacja końcowa odrzuca eksport
   różniący się choćby o 1 px.
3. `ground base tile` — jeden osobny kafelek ziemi. Wymuś idealnie zgodne
   krawędzie lewe/prawe oraz ciągłość z sąsiednimi kafelkami.
4. `buried detail tiles` — 8 osobnych kafelków wygenerowanych na podstawie
   zaakceptowanego kafelka bazowego. Każdy ma mieć inny zabawny, zakopany
   element przesunięty od środka, ale nie może dotykać żadnej krawędzi.
5. `terrain cap` — jeden osobny, powtarzalny PNG z alpha i seamless edges.
   Cap ma mieć kontrolowany pionowy gradient alpha: górna krawędź pozostaje
   czytelna i w większości nieprzezroczysta, a ku dołowi zwiększa się
   **przezroczystość**, czyli wartość kanału alpha stopniowo maleje do `0`.
   Nie interpretuj sformułowania „zwiększa alpha ku dołowi” jako zwiększania
   krycia — celem jest coraz silniejsze przenikanie capu w ziemię. Dolna część
   nie może kończyć się twardą poziomą linią ani stałym półprzezroczystym
   pasem. Gradient powinien być walidowany osobno od kolorów RGB.
6. `tree family` — 8 osobnych PNG-ów. `Tree` oznacza kategorię rozmiaru i
   kotwicy; element może być drzewem albo podobną dekoracją.
7. `rock family` — 8 osobnych PNG-ów w jednej skali i z jedną kotwicą.
8. `prop family` — 8 osobnych PNG-ów w jednej skali i z jedną kotwicą.
9. `ambient family` — 8 osobnych PNG-ów lekkich elementów unoszących się
   w powietrzu, np. liści, pyłków lub świetlików. Ambient nie ma kotwicy
   terenowej, kolizji ani wpływu na gameplay i nie może zasłaniać korytarza
   rozgrywki. Na początku są statyczne, rozmieszczane deterministycznie na
   podstawie seeda poziomu; animację można dodać później jako osobną decyzję.

Dla każdego etapu wygeneruj dokładnie tylko żądany etap, podaj listę plików,
wymiary, tryb obrazu i wynik walidacji, a następnie poproś o jednoznaczne
`zatwierdzam`, `popraw` albo wybór konkretnych plików. Nie importuj ani nie
zastępuj assetów przed akceptacją. Po akceptacji każdego etapu natychmiast:
zweryfikuj pliki, zarchiwizuj je w `assets_src/user_reference/`, skopiuj do
`res/drawable-nodpi/`, podłącz do renderera, zbuduj aplikację i pokaż użytkownikowi
efekt do sprawdzenia na żywo. Przy poprawce regeneruj tylko wskazany etap lub
pliki, a następnie ponownie podmień je w grze. Zachowaj zaakceptowany asset jako
referencję kolejnych etapów.

Po zaakceptowaniu i zaimportowaniu etapu `background` biom musi natychmiast stać
się aktywnym biomem poziomu demonstracyjnego: `DEMO_LEVEL` ma mapować na aktualnie
dodawany `TerrainBiome`, a renderer trybu DEMO ma używać właśnie tego tła.
Zmień centralne mapowanie biomu, test mapowania poziomu demonstracyjnego oraz
niezbędne zasoby/rendering, następnie uruchom testy i build przed rozpoczęciem
następnego etapu. Nie zostawiaj DEMO przypisanego do poprzedniego biomu.

### Sprawdzony workflow bazowego kafelka seamless

Zaakceptowany kafelek Copperwood pokazał, że opis „seamless” w zwykłym AGP jest
niewystarczający. Dla każdego bazowego kafelka powtarzalnego stosuj poniższy
workflow jako twardy kontrakt:

1. Użyj dostarczonego obrazu jako `Reference Image Input` i zachowaj jego
   materiał, paletę, gęstość detali oraz kompozycję. Najpierw koryguj źródło,
   zamiast generować inną teksturę.
2. W AGP wpisz dosłownie `HARD REQUIREMENTS`, `MUST-HAVE TILEABILITY` oraz
   `MUST-HAVE VALIDATION BEFORE DELIVERY`. Generator nie może dostarczyć pliku,
   który nie spełnia któregokolwiek punktu.
3. Wymuś prawdziwy tryb `seamless/tileable/wrap/periodic`, a nie tylko
   deklarację, że tekstura ma wyglądać na bezszwową. Wymuś test offsetu 50%
   w poziomie i pionie oraz usunięcie szwów metodą wrap/clone-blend.
4. Wymuś dokładny kwadrat `1024 × 1024 px` (alternatywnie `512 × 512 px` tylko
   gdy jest to jawnie zaakceptowane), PNG i prawdziwe `RGBA`, nie `RGB`,
   JPEG ani paletową transparentność.
5. Zażądaj sprawdzenia powtórzenia `2 × 2` i `4 × 4` przed eksportem.
   Walidacja lokalna musi sprawdzić format, wymiary, kanał alpha oraz zgodność
   przeciwległych krawędzi piksel po pikselu; dopiero potem należy ocenić
   wizualny brak szwu na podglądzie powtórzenia.
6. Bazowy kafelek nie zawiera żadnego humorystycznego detalu. Po akceptacji,
   archiwizacji i podłączeniu bazowego kafelka dopiero przedstaw 10 propozycji
   humorystycznych detali. Po wyborze kilku detali przygotuj osobny AGP dla
   wariantów, które muszą zachować zaakceptowany kafelek identycznie i nanosić
   wyłącznie wybrany detal poza centrum, losowo oraz bez dotykania krawędzi.

Nie akceptuj ani nie importuj kafelka, jeśli raport walidacji nie potwierdza
`1024 × 1024`, `RGBA`, poprawnej powtarzalności `2 × 2`/`4 × 4` i braku
widocznego szwu. Ten workflow jest obowiązkowy także dla przyszłych biomów.

Przed przygotowaniem AGP dla każdej kategorii assetów przedstaw dokładnie
10 propozycji humorystycznych dodatków i zatrzymaj się na wyborze użytkownika.
Dotyczy to co najmniej:

- kafelków ziemi i zakopanych elementów;
- pary fortec generowanej jednym wspólnym AGP;
- skał;
- drzew lub elementów kategorii `tree`;
- propsów;
- ambientów;
- capu, jeśli humorystyczny detal nie niszczy jego powtarzalności.

Propozycje muszą być krótkie, wizualne i możliwe do wygenerowania bez tekstu.
Po wyborze użytkownika AGP ma zawierać wyłącznie zaakceptowane dodatki i nie
może samodzielnie dodawać innych żartów. Jeśli użytkownik wybierze kilka
propozycji, połącz tylko te wybrane. Jeśli nie wybierze żadnej, użyj neutralnego
assetu bez humorystycznego dodatku.

### Nazewnictwo zakopanych elementów

Używaj krótkiej nazwy **Relic** dla pojedynczego zakopanego elementu
dekoracyjnego oraz **Relics** dla całego zestawu danego biomu. W kodzie używaj
`BuriedRelic`, a dla assetów wzorca `terrain_<biome>_relic_01.png`.
Relics muszą być osobnymi transparentnymi overlayami nanoszonymi na niezmienny
bazowy kafelek ziemi. Są deterministyczne, nie mają kolizji ani wpływu na
gameplay i nie mogą być baked into repeating soil tile.

Każdy `BuriedRelic` ma kategorię rozmiaru:

- `SMALL` — detal mieszczący się na jednym kafelku; wszystkie obecne Relics są
  na razie `SMALL`;
- `MEDIUM` — większy detal zajmujący jeden lub dwa sąsiednie kafelki;
- `LARGE` — jeden spójny overlay obejmujący wiele kafelków, np. fragment
  szkieletu smoka albo olbrzyma.

Kategorie zmieniają wyłącznie skalę, anchor, zasięg renderowania i placement.
Nie wolno im zmieniać heightmapy, kolizji, fizyki ani Gameplay Corridor.
`MEDIUM` i `LARGE` wymagają spójnego overlayu świata, a nie niezależnych
fragmentów, które mogłyby ujawnić szwy między kafelkami.

W przyszłości część Relics może być oznaczona jako `SpecialRelic`. Taki Relic
nie jest wymagany do zwycięstwa: po rzeczywistym odsłonięciu przez krater
uruchamia przypisaną, jawnie zdefiniowaną nagrodę. Trafienie obok nie wystarcza,
a nagroda nie może zostać przyznana więcej niż raz. Zwykłe Relics pozostają
`DecorativeRelic` i nie wpływają na gameplay. Zakres nagrody (tylko bieżąca
bitwa albo trwała kampania) trzeba ustalić przed implementacją mechaniki.

Każdy etap transparentny musi wymagać prawdziwego RGBA, niezależnego kanału
alpha, alpha 0 poza obiektem, braku tła i halo oraz minimum 50 px
przezroczystego marginesu, preferowane 96 px. Żaden element nie może dotykać
krawędzi, być ucięty, nachodzić na inny element ani być połączony z drugim
assetem w jednym obrazie. ZIP nie jest wymagany do importu ani działania gry;
twórz go tylko wtedy, gdy użytkownik poprosi o archiwum lub eksport.

Contact sheet może być tworzony wyłącznie jako kontrolny podgląd po akceptacji
etapu. Nie jest assetem produkcyjnym, nie trafia do gry ani do eksportu.

Każdy etapowy AGP musi zawierać osobne sekcje:

- `THEMATICS` — co przedstawia asset i jaką rolę ma w biomie lub grze;
- `VISUAL STYLE` — styl Urkaaaz, paleta, oświetlenie, skala, perspektywa,
  poziom szczegółowości i relacja do zaakceptowanych wcześniejszych assetów;
- `HUMOROUS ACCENTS` — subtelne, czytelne żarty pasujące do świata gry, bez
  tekstu, napisów, logotypów i zasłaniania funkcji gameplayowych.

Humorystyczne akcenty mają być drugoplanowe i opcjonalne: nie mogą zmieniać
kotwicy, krawędzi seamless, czytelności, kolizji ani przeznaczenia assetu.
Jeżeli etap nie pasuje do żartu, AGP ma jawnie określić, że humor pozostaje
wyłącznie w kształcie, pozie lub drobnym detalu materiału.

### Grounded asset crop i kotwica

Dla każdego assetu zakotwiczonego w terenie, w szczególności drzewa, skały i
propsa, stosuj kontrolowany crop pustego canvasa przed eksportem. Usuń
nadmiarowy transparentny obszar, ale nie przycinaj grafiki ciasno do obrysu.
Pozostaw minimum 96 px pełnej przezroczystości od każdej krawędzi, zachowaj
cały obiekt i jego półprzezroczyste krawędzie oraz ustaw widoczną podstawę
blisko wspólnej dolnej kotwicy rodziny. Wszystkie assety danej rodziny muszą
mieć zgodny canvas, skalę i dolną kotwicę. AGP musi wymagać walidacji cropu,
marginesów i położenia podstawy przed pokazaniem grafiki.

## Następne etapy: Siege Lord i final boss

Po zaakceptowaniu całego zestawu biomu przygotuj 5 odmiennych propozycji
wizualnych Siege Lorda, opisując sylwetkę, rolę, paletę, skalę i czytelność.
Nie generuj grafiki ani AGP przed wyborem użytkownika.

Po wyborze przygotuj osobny AGP wyłącznie dla pierwszej klatki Siege Lorda.
Wymuś osobny PNG, prawdziwe RGBA/alpha, transparentność, duży margines,
brak clippingu, stabilną dolną kotwicę, ustalony kierunek patrzenia i rozmiar
zgodny z istniejącymi jednostkami/bossami. Po zatwierdzeniu pierwszej klatki
zapytaj osobno o pełną animację i dopiero wtedy generuj kolejne klatki.

Następnie powtórz ten sam proces dla final bossa: 5 propozycji, wybór,
AGP pierwszej klatki, akceptacja i dopiero potem pełna animacja. Nie zakładaj
automatycznie liczby klatek, wymiarów ani zachowania animacji.

## Wymagany workflow implementacji

1. Przeczytaj `GAME_DESIGN.md`, `GLOSSARY.md`, `AGENT.md`,
   `CampaignLevelDefinition.kt`, `GameEngine.kt`, `GameView.kt`, istniejące
   definicje terrainów i testy.
2. Znajdź najbardziej podobny istniejący Terrain Theme lub biome i użyj jego
   wzorca. Nie twórz równoległego systemu materiałów ani drugiego kontraktu
   prezentacji terenu.
3. Dodaj biom do centralnego modelu danych, zachowując rozdział:
   - biome/theme opisuje materiały, paletę, tło, dekoracje i profil;
   - Ground heightmap pozostaje źródłem prawdy dla fizyki;
   - gameplay modifier i hazard są jawne, testowalne i niezależne od grafiki.
   - Fortress Hitbox Preview (FHP), grafika fortecy, kotwica maga i kotwica
     katapulty tworzą jeden sprzężony kontrakt prezentacji i kolizji po
     ustaleniu ich prawidłowych miejsc; każda późniejsza zmiana pozycji, skali,
     przesunięcia, kotwicy lub położenia fortecy musi być zastosowana
     jednocześnie do wszystkich elementów.
4. Podepnij biom do właściwych poziomów, chapteru, wariantu lub generatora
   seedów. Zachowaj deterministyczność map i istniejące zasady:
   - płaskie fundamenty fortec;
   - płaskie podejścia przed fortecami;
   - wszystkie assety zakotwiczone w terenie (drzewa, skały, propsy i przyszłe
     dekoracje grounded) wyłącznie na lokalnie płaskim terenie; nigdy na zboczach;
   - HUD-Safe Terrain Boundary;
   - czytelne Gameplay Corridors i tory pocisków.
5. Dodaj materiały oraz dekoracje w warstwach background, midground i grounded.
   Każdy asset musi mieć wspólną skalę, kotwicę, oświetlenie i kontrast.
   Używaj Needle-Slice Terrain Adherence dla elementów zakotwiczonych w
   deformowalnej powierzchni.
   Przy wizualizacji lub zmianie fortec zawsze zachowaj wspólną transformację
   sprite'u, FHP, maga i katapulty, także dla obu stron lustrzanej pary. Wszystkie
   późniejsze korekty pozycji maga są globalne dla wszystkich biomów, chapterów i poziomów;
   nie twórz ustawień maga zależnych od biomu. Przed
   finalizacją transformacji dopasuj osobno kotwicę maga i katapulty do grafiki.
6. Jeśli biom ma hazard lub modifier, dodaj pełną implementację w silniku,
   rendering/feedback w `GameView.kt`, HUD/tutorial tylko jeśli potrzebne,
   lokalizację i testy. Nie ukrywaj błędów ani nie dodawaj cichych fallbacków.
7. Dodaj i zarchiwizuj assety w `assets_src/user_reference/`, a finalne pliki
   umieść w istniejących katalogach `res/drawable-nodpi/` lub zgodnie z
   istniejącym kontraktem terrainów. Sprawdź RGBA, niezależny kanał alpha,
   przezroczystość, rozmiary, marginesy, brak clippingu i brak skażenia tła.
   Domyślnie żądaj osobnych plików PNG, nigdy jednego kolażu, atlasu ani
   sprite sheetu zawierającego wiele elementów. Wymagaj dużych, jednoznacznych
   odstępów między elementami: minimum 50 px całkowicie przezroczystego
   marginesu wokół izolowanych sprite'ów, a gdy generator pozwala — 96 px lub
   więcej. Żaden element nie może dotykać krawędzi ani nachodzić na inny.
8. Zaktualizuj `GAME_DESIGN.md`, jeśli biom lub jego modifier jest
   zaimplementowaną regułą gry. Zaktualizuj `GLOSSARY.md` i `AGENT.md`,
   dodając nazwę, terminologię, zakres poziomów oraz status implementacji.
9. Dodaj testy obejmujące co najmniej:
   - deterministyczny wybór biomu dla poziomu i seeda;
   - poprawny materiał/profil terenu;
   - fundamenty i korytarze bezpieczeństwa;
   - każdy gameplayowy hazard/modifier;
   - brak wpływu biomu na reguły, których użytkownik nie zatwierdził.
10. Uruchom:

    ```bash
    ./gradlew :app:testDebugUnitTest :app:assembleDebug --quiet
    ```

11. Po każdym zaakceptowanym etapie:
    - sprawdź kompletność nazw, wymiarów, RGBA, alpha i marginesów;
    - zarchiwizuj wersję źródłową;
    - zaimportuj pliki do `res/drawable-nodpi/`;
    - podłącz etap do renderera i zbuduj aplikację;
    - pokaż użytkownikowi efekt do weryfikacji na żywo.
    - jeśli etapem jest `background`, ustaw `DEMO_LEVEL` na aktualny biom,
      podłącz jego tło do renderera DEMO i zweryfikuj to testem.
12. Dopiero po akceptacji całego biomu:
    - dodaj testy wyboru assetów dla biomu;
    - pokaż użytkownikowi listę finalnie zaimportowanych plików.
    - opcjonalnie utwórz ZIP, tylko jeśli użytkownik tego zażąda.

## AGP dla biomu

Po implementacji przygotuj jeden kompletny, copy-ready AGP w fenced text block.
AGP ma być podawany etapami, a nie jako jeden prompt do wygenerowania całego
zestawu. Każdy prompt obejmuje tylko aktualny etap i zawiera następujące
elementy. Wyjątkiem jest
etap `fortress pair`: lewa i prawa forteca muszą być opisane w jednym wspólnym
AGP, ale nadal muszą zostać wyeksportowane jako dwa osobne pliki PNG:
`fortress_<biome>_left.png` i `fortress_<biome>_right.png`.

- nazwę biomu, przeznaczenie i zakres assetów;
- tematyka, styl wizualny i humorystyczne akcenty dla aktualnego etapu;
- `Reference Image Input` w każdym AGP; dla fortec zawsze zaznacz, że
  użytkownik dołączy referencje lewej i prawej fortecy;
- dla lustrzanych fortec zaznacz, że flagi mają zachować ten sam kierunek na
  obu stronach, niezależnie od odbicia konstrukcji;
- dla Copperwood powtórz dokładną paletę konstrukcji: ciemne brązowe drewno,
  ciepły kamień, miedź, rdzawy pomarańcz i stonowana zieleń mchu; lewa forteca
  musi mieć niebieskie flagi/proporce/oznaczenia, a prawa czerwone
  flagi/proporce/oznaczenia; czerwień nie może zdominować konstrukcji;
- dla `fortress pair` podaj obowiązkowo dokładne punkty gameplayowe na pełnym
  canvasie `1024 × 768 px`: lewa platforma katapulty `(284, 592)`, prawa
  platforma katapulty `(745, 592)`, dolna kotwica lewego maga `(473, 225)` oraz
  dolna kotwica prawego maga `(585, 225)`; wymagaj zgodności co do 1 px i nie
  używaj współrzędnych względnych względem widocznego obrysu;
- dokładne nazwy i wymiary każdego osobnego pliku PNG; sprite sheety i kolaże
  są zabronione, chyba że istniejący renderer wyraźnie wymaga konkretnego
  arkusza;
- tile'e płaskie, łagodne i strome w lewo/prawo, ridge, valley, crater rim oraz
  smooth foundation edges, jeśli biom ich potrzebuje;
- zasady seamless edges, wspólny anchor, skalę, światło z lewego górnego rogu
  i kompatybilność z Needle-Slice Terrain Adherence;
- warstwy tła, midground, grounded decorations i zasady Gameplay Corridor;
- prawdziwe RGBA z niezależnym kanałem alpha dla każdego transparentnego pliku,
  pełna przezroczystość poza obiektem, minimum 50 px (preferowane 96 px)
  wolnego marginesu, brak clippingu, skażenia tła, siatki i łączenia obiektów;
- osobne pliki gotowe do natychmiastowego importu; ZIP jest opcjonalnym
  eksportem tworzonym wyłącznie na żądanie użytkownika;
- pełny negative prompt oraz obiektywne kryteria akceptacji;
- docelowe ścieżki Androida i nazwy plików.

### Uniwersalny gate walidacyjny AGP

Każdy AGP, niezależnie od kategorii assetu, musi zawierać osobną sekcję
`MUST-HAVE` z bezwzględnymi wymaganiami technicznymi i wizualnymi oraz osobną
sekcję `MUST-HAVE VALIDATION BEFORE DELIVERY`. Generator nie może pokazać,
wyeksportować ani zwrócić grafiki przed przejściem wszystkich wymienionych
testów. Jeśli choć jeden test nie przejdzie, wynik należy odrzucić, poprawić
lub wygenerować ponownie i zwalidować od początku. Nie wolno pokazywać
niezwalidowanego preview ani traktować deklaracji generatora jako dowodu
spełnienia wymagań.

Sekcja `MUST-HAVE` musi określać co najmniej wymagane wymiary, format, tryb
koloru, kanał alpha, strukturę eksportu, nazwy plików, marginesy, anchor,
seamless/tileability albo siatkę sprite sheetu — zależnie od assetu.
Sekcja `MUST-HAVE VALIDATION BEFORE DELIVERY` musi określać obiektywne testy
tych wymagań oraz jednoznaczną regułę: najpierw walidacja, dopiero potem
pokazanie grafiki użytkownikowi.

Nie twórz drugiego standardu AGP. Stosuj definicje `Terrain Theme`,
`Terrain Material Tile`, `Sprite Separation and Alpha` i
`Shared Terrain Presentation Contract` z `GLOSSARY.md`.

### Proaktywna rekomendacja kompozycji overlay

Jeżeli kilka wariantów ma wspólną bazę i różni się tylko pojedynczymi
elementami, zaproponuj użytkownikowi z wyprzedzeniem workflow:

`zaakceptowany asset bazowy → osobne transparentne overlaye → kompozycja po
stronie aplikacji lub kontrolowanego skryptu → walidacja finalnych wariantów`.

Preferuj ten workflow zamiast generowania całych złożonych obrazów, gdy:

- wspólne tło lub kafelek musi zachować identyczne seamless edges;
- warianty różnią się detalami, dekoracjami albo humorystycznymi obiektami;
- generator ma tendencję do tworzenia kolaży, sprite sheetów lub niespójnych
  stylowo nakładek;
- pozycjonowanie, margines, alpha i kolejność warstw powinny być deterministyczne.

Wyjaśnij tę możliwość przed rozpoczęciem generowania wariantów i zaproponuj ją
także dla kolejnych biomów, nawet jeśli użytkownik początkowo prosi o gotowe
pełne kafelki. Overlay musi być osobnym plikiem RGBA z przezroczystym tłem,
bez baked backgroundu; bazowy asset pozostaje niezmienny, a pozycje i końcowa
kompozycja są kontrolowane po stronie projektu.

### Proaktywne rekomendacje projektowe

Nie ograniczaj się do literalnego wykonania pierwszego pomysłu użytkownika.
Jeżeli znasz rozwiązanie prostsze, stabilniejsze, tańsze w utrzymaniu,
łatwiejsze do walidacji albo dające lepszy efekt wizualny, przedstaw je
użytkownikowi przed implementacją. Wyjaśnij krótko:

- co jest lepsze i dlaczego;
- jaki problem obecnego podejścia rozwiązuje;
- jakie są koszty lub kompromisy;
- czy zmiana wpływa na zakres, runtime albo workflow akceptacji.

Dotyczy to w szczególności generowania assetów, kompozycji warstw, kontraktów
alpha, seamless terrain, skalowania, kotwic gameplayowych, wydajności,
deterministycznego rozmieszczania i testowalności. Nie zmieniaj samodzielnie
istotnej decyzji użytkownika, ale zawsze ją zakwestionuj i zaproponuj lepszą
alternatywę, gdy istnieją ku temu konkretne przesłanki.

### Stały kontrakt eksportu assetów

Każdy generowany AGP musi zawierać poniższe wymagania, nawet jeśli użytkownik
nie przypomni ich osobno:

- eksportuj każdy asset jako osobny plik PNG, nigdy jako jeden połączony obraz;
- transparentne assety muszą być prawdziwym RGBA, z niezależnym kanałem alpha;
- tło poza sprite'em musi mieć alpha 0, bez czerni, bieli, szachownicy,
  koloru referencyjnego ani półprzezroczystej obwódki;
- zostaw minimum 50 px całkowicie przezroczystej przestrzeni, preferowane
  96 px lub więcej, między niezależnymi elementami i od krawędzi;
- nie dopuszczaj do clippingu, stykania się, nachodzenia ani przekraczania
  granic pliku przez żaden element;
- jeśli użytkownik zażąda ZIP-a, wygeneruj go z jednym katalogiem głównym
  i osobnymi nazwanymi PNG-ami; nie wkładaj do niego arkuszy preview,
  opisów ani referencji;
- dołącz obiektywne kryteria akceptacji: liczba plików, nazwy, wymiary,
  tryb RGBA, obecność alpha, czyste marginesy i poprawna struktura ZIP-a.

## Finalizacja

W końcowej odpowiedzi podaj:

- zaakceptowane decyzje biomu;
- zmienione warstwy implementacji i dokumentacji;
- wynik testów/builda;
- pełny AGP w jednym bloku do skopiowania;
- jawne elementy pozostawione jako przyszłe lub niezaimplementowane.
