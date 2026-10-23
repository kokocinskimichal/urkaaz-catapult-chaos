# Plan dostosowania HUD-u meczu

**Status:** Zmiana HUD-u zaimplementowana z natywnie rysowanymi ramkami
zastępczymi; wygenerowane assety HUD-u pozostają do dodania.

## Cel

Dostosować istniejący HUD Androida do zaakceptowanej koncepcji z
`match-hud-redesign-v2.agp`: zachować czytelne pole bitwy, uporządkować
informacje w górnym pasku i dolnym doku oraz pokazać Supply, amunicję, czary i
manę zgodnie z faktycznym stanem meczu i zatwierdzonym loadoutem.

To zmiana warstwy prezentacji. Symulacja pozostaje jedynym właścicielem stanu
rozgrywki, a widoki otrzymują dane z niemutowalnego `RenderState` i wysyłają
akcje przez istniejące callbacki / `MatchUiAction`.

## Ustalenia projektowe

### Górny pasek

- Jedna smukła kompozycja z trzema wyrównanymi strefami:
  - po lewej: zdrowie niebieskiej fortecy;
  - pośrodku: timer, wiatr i jeden przycisk pauzy w niskim, płaskim module;
  - po prawej: zdrowie czerwonej fortecy.
- Timer pozostaje głównym elementem środkowej strefy; wiatr jest informacją
  drugorzędną.
- Karty HP zajmują po 70% dostępnego miejsca swoich bocznych stref i pozostają
  dosunięte do zewnętrznych krawędzi; pozostała przestrzeń oddziela je od
  środkowego modułu.
- Nie dodawać nowego panelu wiatru, dodatkowych celów, wyników ani innych
  nieistniejących danych.
- Wskaźniki przeładowania pozostają przy katapultach na polu bitwy, bez
  duplikowania ich w górnym pasku.

### Dolny dok

Jeden kompaktowy dok z trzema sąsiadującymi grupami, w tej kolejności:

1. **Supply, jednostki i rozkazy — lewa strona**
   - Supply na skrajnym lewym brzegu grupy, w bursztynowo-złotej oprawie i z
     ikoną skrzyni.
   - Pokazywać bieżącą wartość i pojemność; zachować zwarty, czytelny wskaźnik,
     np. pierścień lub segmenty.
   - Umieścić podpisy `SUPPLY` nad wskaźnikiem oraz `UNITS` nad wyborem
     jednostek i akcją wysłania fali.
   - Wyświetlać najwyżej trzy jednostki z zatwierdzonego loadoutu i poziomu;
     przy każdej podawać rzeczywisty koszt Supply oraz stan dostępności.
   - Zachować wybór jednostki do wystawienia i akcję `SEND WAVE`. Nie dodawać
     przycisku ataku.

2. **Amunicja — środek**
   - Wyświetlać najwyżej trzy rzeczywiste pozycje z zatwierdzonego loadoutu i
     dostępności poziomu.
   - Umieścić podpis `AMMUNITION` pośrodku górnej części panelu.
   - Zachować wybór amunicji i aktualne źródła informacji o zapasie oraz
     przeładowaniu.
   - Nie pokazywać slotów zastępczych ani amunicji niedostępnej w meczu.

3. **Czary i mana — prawa strona**
   - Umieścić podpis `SPELLS` nad slotami czarów, a `MANA` nad kulą many.
   - Wyświetlać najwyżej trzy czary z zatwierdzonego loadoutu i dostępności
     poziomu. Nie sugerować, że czar bez runtime'u można rzucić.
   - Kulę many umieścić na skrajnym prawym brzegu sekcji czarów.
   - Pokazywać wartość liczbową many oraz maksymalną pojemność pośrodku kuli.
   - Poziom ciemnoniebieskiego płynu w kuli ma odpowiadać proporcji aktualnej many:
     pełny zbiornik przy 100%, około połowy przy 50% i pusty przy 0%.
     Zmiana poziomu może mieć krótkie, subtelne falowanie, po którym tafla się
     uspokaja; animacja nie może odrywać poziomu od bieżącej wartości.
   - Oprawa kuli i płyn to oddzielne warstwy. Wypełnienie, licznik i animacja
     są renderowane dynamicznie, nie wypalane w jednym PNG ani nie generowane
     jako osobne grafiki dla kolejnych wartości many.
   - Każdy czar, w tym `FORTRESS_SHIELD`, zajmuje zwykły slot i korzysta z
     tego samego układu oraz wzorca interakcji. Koszt, dostępność i status
     prezentować na podstawie danych danego czaru; szczegóły efektu pokazywać
     tylko wtedy, gdy są zdefiniowane i dostępne w stanie prezentacyjnym.

Lewa grupa Supply/jednostki/rozkazy i prawa grupa czary/mana mają mieć tę samą
szerokość. Środkowa grupa amunicji może być węższa.

### Przejściowe komunikaty czarów

- Nie wyświetlać stałego panelu `READY` ani nie rezerwować dla niego miejsca.
- Po próbie rzucenia czaru pokazać w dotychczasowym miejscu nad dokiem sam
  tekst rzeczywistego wyniku: powodzenie lub przyczynę odrzucenia.
- Utrzymać komunikat przez 2,5 sekundy, a następnie płynnie go wygasić.
- Nie rysować tła, ramki ani cienia panelu; użyć wyłącznie czytelnego tekstu z
  subtelnym cieniem dla kontrastu. Widok ma być ukryty poza czasem komunikatu.
- Nie zmieniać geometrii ani położenia doku. Komunikat nie może zasłaniać
  celowania, toru pocisku ani istotnej akcji na polu bitwy.

### Oprawa i assety

- Wygenerować nowy zestaw spójnych ramek HUD-u zamiast opierać projekt na
  przypadkowych, istniejących tłach paneli. Ramki mają być osobnymi,
  przezroczystymi elementami bez tekstów, liczb i danych dynamicznych.
- Dołączać i zatwierdzać assety pojedynczo, w kolejności z checklisty niżej.
- Dla many przygotować osobną oprawę pustej kuli. Płyn, jego poziom, tafla i
  licznik pozostają dynamiczne; tekstura kaustyczna jest opcjonalną warstwą
  dekoracyjną po ocenie pierwszej wersji kuli. Nie generować wariantów poziomu
  napełnienia.
- Zachować istniejące ilustracje jednostek, amunicji i czarów oraz istniejące
  grafiki pola bitwy. Nie przerabiać fortec, katapult, terenu ani pocisków.

#### Checklista nowych assetów HUD-u

Wszystkie ramki powinny mieć przezroczyste tło, wspólny materiał ciemnego
metalu i kontrolowany brązowo-złoty rant. Nie mogą zawierać tekstu, cyfr,
ikon stanu ani wypełnionych wartości. Ramki horyzontalne przygotować tak, aby
ich środek dało się rozciągać bez deformowania narożników. Cień pod głównymi
panelami HUD-u jest lekki i renderowany programowo; nie wypalać go w assetach.

| Kolejność | Asset | Zakres i wymagania | Status |
|---|---|---|---|
| 1 | `match_hud_universal_frame.png` | Jedna wspólna ramka Nine-patch użyta raz wokół całego dolnego doku oraz osobno wokół obu kart HP i środkowego panelu timera, wiatru i pauzy. Trzy grupy doku rozdzielają delikatne linie rysowane przez UI, nie osobne ramki. Środek przezroczysty; oddzielne wypełnienie `#1A1A1A` o kryciu 80%. | Zatwierdzone źródło PNG i Android Nine-patch zintegrowane; dolny dock ma jedną ramkę, a podziały doku i pauzy są rysowane przez UI |
| 2 | `match_hud_slot_frame_normal.png` | Lekka, cienka oprawa pojedynczego slotu HUD, przygotowana do Nine-patch: stałe narożniki i rozciągliwy środek; bez masywnego rantu i poświaty. | Źródłowy PNG i Android Nine-patch przygotowane; nie są używane w UI |
| 3 | `match_hud_slot_frame_selected.png` | Złota, czytelna oprawa wybranego slotu, dopasowana do wariantu neutralnego. Stany niedostępne można nadal pokazywać przez przygaszenie w UI; osobny asset nie jest potrzebny. | Do wygenerowania |
| 4 | `match_hud_supply_crate_icon.png` | Mała, czytelna ikona skrzyni Supply w złoto-bursztynowym stylu. | Do wygenerowania |
| 5 | `match_hud_mana_orb_shell.png` | Przezroczysta szklana oprawa kuli z refleksami i subtelną głębią. Bez niebieskiego wypełnienia, wartości many, tekstu i tła; płyn jest rysowany dynamicznie pod assetem. | Dostarczona przez użytkownika złota, filigranowa oprawa została zmniejszona z 1254×1254 do PNG 512×512 i zintegrowana; widok kuli ma 80×80 dp, licznik jest wyśrodkowany, a płyn ma ciemniejszy kolor |
| 6 (opcjonalnie) | `match_hud_mana_caustics.png` | Bardzo subtelna, przezroczysta tekstura smug/kaustyk do animowania wewnątrz płynu, jeśli po ocenie samej kuli gradient i cień tafli nadal będą wyglądały płasko. Nie zawiera kuli ani poziomu napełnienia. | Do decyzji po assetcie 5 |

Źródła nowych grafik umieszczać w `assets_src/match/hud/`, a zatwierdzone
wersje produkcyjne integrować dopiero po osobnym przeglądzie wizualnym.

#### Istniejące assety do ponownego użycia

Nie generować ponownie ikon już obecnych w
`android-app/src/main/res/drawable-nodpi/`:

- jednostki: `defender_icon.png`, `sapper_icon.png`, `demolisher_icon.png`,
  `goblin_raging_boar_icon.png`, `goblin_slingmaster_icon.png`;
- amunicja: `rock_flight_00.png`, `ammo_icon_siege_bomb.png`,
  `ammo_icon_powder_barrel.png`, `fire_rain_icon.png`,
  `plague_cauldron_icon.png`;
- rozkazy i wiatr: `send_wave_icon.png` oraz istniejące ikony
  `wind_{low,medium,high}_{left,right}.png`;
- czary: dostępne ilustracje `loadout_spell_*_card.png` w tym samym katalogu;
- zwykłe/pressed przyciski: istniejąca rodzina `ui_button_dark_*` i
  `ui_button_red_*`.

Teksty, wartości HP/many/Supply, wskaźniki wypełnienia, fale płynu, stany
wybrane/niedostępne oraz symboliczna kontrolka Pause pozostają elementami
dynamicznymi UI, nie osobnymi bitmapami. Światowe grafiki fortec, katapult,
amunicji i biome'ów także pozostają bez zmian.

## Stan obecny i punkty integracji

| Obszar | Obecny właściciel / stan | Planowana zmiana |
|---|---|---|
| Kompozycja ekranu meczu | `MatchScreenController` | Uporządkować warstwy w górny pasek i dock; pokazywać wynik próby czaru jako tekstową, przejściową informację nad dokiem bez stałego panelu. |
| Timer, wiatr, pauza, mana i czary | `MatchHudView` | Zawęzić odpowiedzialność lub wydzielić screen-owned komponenty dla górnego paska i sekcji czarów; zastąpić dedykowany przycisk czaru wspólnym układem slotów i wspólną akcją rzucania czaru, bez wyróżniania konkretnego czaru. |
| Wybór jednostek / Supply / fala | `UnitPanelView`, `SupplyGaugeView` | Przeorganizować do lewej grupy doku, ograniczyć wybór do loadoutu i maksymalnie trzech pozycji, pokazać koszt pochodzący z katalogu balansu. |
| Wybór amunicji | `AmmunitionPanelView` | Zastąpić pojedynczy widoczny selektor trzema lub mniejszą liczbą rzeczywistych slotów wyboru; zachować kontrakt wyboru i dane zapasu/przeładowania. |
| Dane dla widoków | `MatchViewModel`, `RenderStateMapper`, `RenderState` | Wystawiać wyłącznie potrzebne dane prezentacyjne z snapshotu i zatwierdzonego loadoutu, w tym listy pozycji, limity oraz stany. Nie tworzyć drugiego źródła prawdy. |
| Zdrowie fortec i przeładowanie | `RenderState`; zdrowie i wskaźniki przeładowania rysuje też `BattlefieldRenderer` | Dodać lub przeorganizować górne odczyty zdrowia bez usuwania obecnych wskaźników świata, dopóki projekt nie zatwierdzi ich zastąpienia. |
| Grafiki HUD-u | `android-app/src/main/res/drawable-nodpi/`, źródła w `assets_src/` | Dodać zatwierdzony, modularny zestaw ramek, ikonę Supply i warstwy kuli many; istniejące ikony jednostek/amunicji/czarów pozostawić bez zmian. |

## Etapy realizacji

Układ i przepływy Androida są zaimplementowane. Zatwierdzona wspólna ramka HUD-u
została wygenerowana i zintegrowana; pozostałe niezależne assety z tabeli nadal
mogą być przygotowywane osobno. Nowe grafiki powinny podmieniać wyłącznie
warstwy wizualne, bez zmiany układu, danych ani przepływu akcji.

### 1. Zatwierdzenie projektu i assetów

- Uzupełnić / wykorzystać `match-hud-redesign-v2.agp` do wygenerowania dwóch
  statycznych stanów: normalnego oraz koncepcyjnego wyboru celu.
- Sprawdzić oba obrazy pod kątem kolejności sekcji, skali, czytelności,
  widoczności pola bitwy oraz położenia wskaźnika many.
- Przygotować osobny prompt assetowy dla nowych ramek i warstw kuli many.
- Pokazać ramki oraz kulę w użyciu i uzyskać akceptację przed integracją
  produkcyjnych grafik.
- Nie implementować selekcji celu dla nowych czarów na podstawie samego
  mockupu; tryb wyboru celu na mockupie pozostaje koncepcyjnym stanem UI i nie
  zmienia zasad targetowania czarów w runtime.

**Warunek przejścia:** zaakceptowane mockupy oraz zatwierdzony zestaw assetów
albo jawna decyzja, które elementy tymczasowo rysować natywnie.

### 2. Przygotowanie danych prezentacyjnych

- Oprzeć listy jednostek, amunicji i czarów o zatwierdzony loadout oraz
  dostępność kampanii, zachowując istniejące filtrowanie.
- Przenieść koszty jednostek do istniejącego źródła balansu
  (`UnitConfigurations` / projekcja `LoadoutCatalog`) zamiast utrzymywać
  lokalne koszty w `UnitPanelView`.
- Wartości maksymalne Supply i many pobierać z istniejących konfiguracji lub
  kontraktów; jeżeli snapshot nie niesie wymaganej wartości, rozszerzyć
  projekcję prezentacyjną, a nie dodawać niezależną regułę balansu do Androida.
- Uzupełnić `RenderState` / mapowanie tylko o brakujące, niemutowalne dane:
  sloty, koszty, limity i stany dostępności. Nie kopiować obiektów domenowych
  do rendererów.
- Zachować istniejący przepływ akcji i komend dla pauzy, wyboru amunicji,
  wystawienia jednostki, wysłania fali oraz rzucania czarów. Sloty czarów mają
  być prezentowane jednolicie; szczegóły aktywacji wynikają z definicji
  konkretnego czaru, a nie z osobnego statusu w HUD-zie.
- Uogólnić akcję UI rzucania czaru tak, by przenosiła identyfikator wybranego
  czaru i była mapowana na istniejącą komendę `MatchCommand.CastSpell`.
  Usunąć rozgałęzienie prezentacji/akcji zależne od `FORTRESS_SHIELD`; ten
  czar ma przechodzić przez ten sam wybór slotu, callback i ścieżkę walidacji
  co każdy inny wyposażony czar.

### 3. Implementacja ram i układu

- Złożyć ekran w screen-owned komponenty: górny pasek i dolny dock; wynik próby
  czaru wyświetlać w przejściowej warstwie tekstowej bez stałego panelu.
- Ułożyć górne trzy strefy i trzy grupy doku zgodnie z ustalonym układem.
- Ograniczyć wszystkie grupy do maksymalnie trzech rzeczywistych opcji, bez
  poziomego przewijania i fikcyjnych slotów.
- Użyć ramek jako tła/oprawy, a tekst, wartości, liczniki i stany pozostawić
  dynamicznym elementom Androida.
- Zachować istniejące assety gry oraz renderowanie pola bitwy, kamery,
  celowania i trajektorii. Nie zmieniać punktów aim ani geometrii świata.

### 4. Implementacja dynamicznej many i stanów

- Narysować płyn wewnątrz stałej maski kuli z poziomem opartym o
  `currentMana / maximumMana`; ograniczyć wartość do poprawnego zakresu.
- Animować zmianę wyłącznie w prezentacji: krótkie, subtelne falowanie po
  zmianie wartości i spokojna tafla po zakończeniu; bez zegara ściennego jako
  źródła many i bez mutacji stanu gry.
- Aktualizować poziom przy każdym nowym `RenderState`, również po rzuceniu
  czaru, regeneracji, pauzie, wznowieniu i restarcie.
- Pokazywać statusy i szczegóły czarów wyłącznie na podstawie dostępnych
  danych; informacja zwrotna pozostaje w tym samym pasku nad dokiem.
- Wyłączyć lub ukryć elementy debugowe w normalnej prezentacji produkcyjnej,
  zachowując ich istniejące działanie w trybie debug.

### 5. Testy i wizualna kontrola

- Dodać testy mapowania stanu i list dostępnych pozycji dla różnych loadoutów
  oraz poziomów; sprawdzić limity maksymalnie trzech pozycji.
- Sprawdzić, że koszty jednostek, limity zasobów i statusy czarów pochodzą z
  istniejących danych, a niedostępne opcje nie są prezentowane jako aktywne.
- Dodać testy obliczenia poziomu many dla 0%, wartości pośredniej i 100% oraz
  prezentacji statusów slotów czarów; nie testować animacji przez zmianę
  zegara symulacji.
- Zweryfikować, że przyciski nadal wysyłają istniejące akcje i nie mutują
  stanu meczu bezpośrednio; aktywacja każdego slotu przekazuje właściwy
  identyfikator do wspólnej akcji rzucania czaru.
- Przejrzeć ekran w docelowej orientacji i rozdzielczościach: brak nachodzenia
  na pole bitwy, celowanie, trajektorię, jednostki i katapulty; czytelne
  etykiety, kontrast oraz bezpieczne obszary dotykowe.
- Po implementacji uruchomić obowiązkową bramkę projektu:

  ```bash
  ./gradlew test assembleDebug --quiet
  git diff --check
  git status --short
  ```

## Kryteria akceptacji

- Górny pasek pokazuje BLUE HP, timer/wiatr/pauzę i RED HP w trzech
  spójnych strefach; środkowy moduł jest płaski i kompaktowy.
- Dolny dok ma kolejność: Supply/jednostki/rozkazy, amunicja, czary/mana.
- Boczne grupy Supply/jednostek i czarów/many mają równą szerokość; grupa
  amunicji pozostaje wyśrodkowana między nimi.
- Maksymalnie trzy pozycje widać w każdej z grup jednostek, amunicji i czarów;
  każda pochodzi z aktualnego poziomu i zatwierdzonego loadoutu.
- Supply jest wyraźnie odróżnione od many; oba wskaźniki pokazują wartość oraz
  pojemność.
- Kula many jest na skrajnym prawym brzegu sekcji czarów, a poziom płynu
  dokładnie odzwierciedla bieżący udział many w jej pojemności.
- Kula nie wymaga wariantów bitmapy dla kolejnych poziomów; ramka, płyn i
  dynamiczna warstwa prezentacyjna są rozdzielone.
- Poza krótką informacją po próbie czaru nad dokiem nie ma panelu `READY` ani
  zarezerwowanego miejsca na komunikaty; tekst znika płynnie po 2,5 sekundy.
- Nie pojawiają się wymyślone wartości, akcje, czary, cooldowny, czasy trwania,
  zasoby ani interakcje wyboru celu.
- Żaden czar nie otrzymuje specjalnego układu slotu ani wyróżnionego sposobu
  prezentacji wyłącznie ze względu na jego tożsamość.
- HUD nie zmienia symulacji, balansu, zasad celowania ani kompozycji świata.

## Poza zakresem

- Dodawanie nowych efektów czarów, AI czarów lub mechaniki ręcznego wyboru
  celu.
- Zmiana balansu, regeneracji many, pojemności zasobów, kosztów jednostek,
  amunicji, zdrowia fortec lub reloadu.
- Zmiana istniejącego układu fortec/katapult, ich geometrii, aim origin,
  kamery, trajektorii albo zasad symulacji.
- Generowanie fikcyjnych slotów lub prezentowanie funkcji, których runtime
  jeszcze nie obsługuje.
- Integracja produkcyjnych assetów przed akceptacją mockupu i samych assetów.
