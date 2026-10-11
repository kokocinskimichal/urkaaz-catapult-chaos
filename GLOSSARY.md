# Urkaaaz! Catapult Chaos — Glossary

## General Rules / Ogólne zasady

1. **Tutorial Target musi być widoczny.** Każdy element opisywany przez Tutorial Hint musi
   znajdować się w aktualnym kadrze, a Tutorial Spotlight musi obejmować dokładnie ten element.
   Nie wolno opisywać obiektu znajdującego się poza ekranem ani podświetlać dużego, przypadkowego
   obszaru zawierającego inne elementy.
2. **Tutorial Hint i Tutorial Spotlight są osobnymi elementami.** Hint przekazuje instrukcję,
   a Spotlight wskazuje element, którego instrukcja dotyczy. Ich położenie powinno być dynamiczne
   i nie może zasłaniać Tutorial Target.
3. **Fortress Foundation jest zawsze płaska, ale przejście jest płynne.** Podłoże pod każdą
   fortecą musi być poziome na całej szerokości fundamentu, niezależnie od profilu terenu, oraz
   chronione przed kraterami. Teren po obu stronach fundamentu musi łagodnie łączyć się z jego
   poziomem bez nagłego uskoku.
4. **Panele są odblokowywane po wyjaśnieniu.** Unit Panel i Spell Panel pozostają nieaktywne
   do momentu, aż odpowiednia mechanika zostanie przedstawiona w tutorialu. Wcześniej poznane
   panele pozostają aktywne na kolejnych poziomach.
5. **Przeciwnik nie wyprzedza tutoriala.** AI może korzystać wyłącznie z mechanik, które zostały
   już wprowadzone w danym etapie kampanii. W trakcie aktywnego tutoriala nie używa niepoznanych
   czarów ani jednostek.
6. **Każdy Chapter ma dziesięć poziomów.** Ostatni poziom Chapter, czyli poziom 10, 20, 30 itd.,
   jest Boss Level z Bossem zbliżającym się do Player Fortress.
7. **Poziomy w Chapterze nie są identyczne.** Każdy poziom powinien mieć własny profil terenu,
   cel misji, ograniczenie, zachowanie przeciwnika albo inny modyfikator rozgrywki.
8. **Interfejs obsługuje polski i angielski.** Polski jest językiem domyślnym. Zmiana języka
   odbywa się przez małą ikonę flagi w prawym górnym rogu Dashboardu.
9. **Demo jest izolowane.** Przycisk `TEST` uruchamia obecnie Demo na poziomie 21 z pełną
   zawartością testową; demo nie może
   zmieniać kampanii, własności czarów, waluty ani postępu gracza.
10. **Tutorial Highlight odpowiada grafice.** Ramka wskazująca element musi mieć dokładnie rozmiar
    i położenie jego widocznej grafiki, bez sztucznego marginesu, przesunięcia ani przybliżonego
    hitboxa. Dotyczy to wszystkich wskazywanych elementów interfejsu i pola bitwy.
11. **Fortress Hitbox Preview (FHP), grafika, mag i katapulta są sprzężonym kontraktem fortecy.**
    Po ustaleniu prawidłowych kotwic maga i katapulty każde przesunięcie, skalowanie, opuszczenie,
    zmiana kotwicy lub położenia fortecy na mapie musi aktualizować wszystkie cztery elementy.
    Wszystkie przyszłe zmiany pozycji maga są wspólne dla wszystkich biomów, chapterów i poziomów;
    nie wolno tworzyć osobnych ustawień maga zależnych od biomu.
    Do tego czasu kotwice maga i katapulty pozostają osobnym etapem dopasowania. Każdy AGP
    fortecy musi podawać te kotwice co do piksela na kanonicznym canvasie `1024 × 768 px`.

## Tutorial

| Term | Meaning |
| --- | --- |
| **Tutorial Hint** | Jasne okienko zawierające tekst instrukcji oraz przycisk `NEXT` lub `START BATTLE`. |
| **Fortress Hitbox Preview (FHP)** | Półprzezroczysty podgląd uproszczonego kształtu trafienia fortecy. Po ustaleniu kotwic maga i katapulty FHP, grafika, mag i katapulta mają wspólną transformację: pozycję, skalę, przesunięcie i kotwicę. Wszystkie korekty pozycji maga są globalne dla wszystkich biomów, chapterów i poziomów. |
| **Fortress Presentation Template** | Kontrakt poziomu testowego Demo i źródło prawdy dla wszystkich biomów, chapterów i poziomów. Obejmuje skalę `0.55`, opuszczenie grafiki `0.16`, wysokość fortecy, pozycję fortec względem świata oraz identyczne kotwice katapult i magów; bieżąca korekta kotwic magów wynosi `+45/-45` w osi X i `-42` w osi Y; grafika fortecy może pozostać właściwa dla danego chapteru, ale jej układ nie może zmieniać geometrii template'u. |
| **Fortress Gameplay Anchor Pixels** | Niezmienne punkty na canvasie fortecy `1024 × 768 px`, wyliczone z kanonicznego układu Demo. Punkt środkowy platformy katapulty to `(284, 592)` dla lewej i `(745, 592)` dla prawej fortecy. Punkt dolnej kotwicy maga to `(473, 225)` dla lewej i `(585, 225)` dla prawej fortecy. Każdy AGP fortecy musi wymagać tych współrzędnych dokładnie, bez tolerancji projektowej; przezroczysty margines i widoczne granice grafiki nie zmieniają układu całego canvasa. |
| **Demo** | Izolowany poziom `0`, uruchamiany przyciskiem `TEST` na ekranie głównym. Korzysta z biomu Copperwood i pełnego testowego loadoutu; służy do wspólnego ustawiania oraz weryfikacji fortec, FHP, maga i katapult bez wpływu na postęp kampanii. |
| **Tutorial Spotlight** | Przyciemniona warstwa rozgrywki z ramką wokół opisywanego elementu. |
| **Tutorial Target** | Dokładny element opisywany przez Tutorial Hint i obejmowany przez Tutorial Spotlight. |

## Battlefield and HUD

| Term | Meaning |
| --- | --- |
| **Battlefield** | Cały obszar gry zawierający teren, fortece, katapulty, jednostki, pociski i efekty. |
| **JS (world unit)** | Stała jednostka projektowa świata. Pozycje obiektów, kolizje i geometria rozgrywki używają JS, a dopiero kamera i zoom przeliczają je na piksele konkretnego urządzenia. |
| **Fixed Design Coordinate System** | Stały układ współrzędnych, w którym szerokość świata pozostaje taka sama na każdym urządzeniu. Zmiana rozdzielczości wpływa na kadr i zoom, ale nie na pozycje obiektów ani relacje między nimi. |
| **Player Fortress** | Twierdza po lewej stronie, kontrolowana przez gracza. |
| **Enemy Fortress** | Twierdza po prawej stronie, kontrolowana przez przeciwnika. |
| **Catapult** | Wyrzutnia pocisków wraz z mechaniką celowania i ustawiania siły strzału. |
| **Ammunition Selector** | Kontrolka na dole pośrodku ekranu służąca do wyboru typu amunicji. |
| **Ammo Button** | Widoczny przycisk w Ammunition Selector, pokazujący aktywną amunicję i otwierający Ammo Menu. |
| **Ammo Menu** | Rozwinięta lista typów amunicji otwierana z Ammunition Selector. |
| **Campaign Ammunition** | Amunicja odblokowywana przez postęp kampanii i dostępna bez limitu zapasu. Rock i Siege Bomb należą do tej klasy. |
| **Special Ammunition** | Amunicja kupowana w Shopie jako pojedynczy zapas. Zapas zmniejsza się dopiero po udanym użyciu. |
| **Ammunition Loadout** | Konfiguracja trzech slotów amunicji przed poziomem: Rock zajmuje stały pierwszy slot, a gracz wybiera dwa dodatkowe odblokowane typy. |
| **Rock Instant Reload** | Rock nie ma czasu przeładowania, ale każda katapulta może mieć tylko jeden aktywny pocisk; kolejny Rock można wystrzelić po rozstrzygnięciu poprzedniego. |
| **Unit Panel** | Lewy dolny panel HUD zawierający Supply, przyciski jednostek i przycisk fali. |
| **Spell Panel** | Prawy dolny panel HUD zawierający przyciski czarów i Manę. |
| **Supply Gauge** | Wskaźnik zasobu używanego do rekrutowania jednostek oblężniczych. |
| **Mana Gauge** | Wskaźnik zasobu używanego do aktywowania czarów. |
| **Wave Button** | Przycisk wysyłający zrekrutowane jednostki z garnizonu do walki. |
| **Unit Loadout** | Ekran konfiguracji przed bitwą, na którym gracz wybiera maksymalnie trzy odblokowane jednostki dostępne w danym poziomie. Tylko wybrane jednostki są widoczne w HUD-zie i mogą zostać zrekrutowane. |
| **Game Balance Configuration** | Planowana konfiguracja parametrów balansu, oddzielona od stanu rozgrywki. Składa się z osobnych sekcji dla jednostek, amunicji, czarów, bossów i poziomów oraz obsługuje walidację i wersjonowanie. |
| **Balance Simulation** | Planowany zestaw automatycznych rozgrywek używający wersjonowanej konfiguracji balansu do pomiaru zwycięstw, czasu gry, zużycia zasobów i skuteczności jednostek oraz loadoutów. |
| **Fortress Health Bars** | Paski HUD-u w lewym i prawym górnym rogu pokazujące zdrowie obu fortec. |
| **Central Objective** | Specjalny, zniszczalny obiekt umieszczony na płaskim centralnym wzgórzu poziomów 5, 15, 25 itd. Nie zastępuje fortec, ale jest dodatkowym celem ostrzału. |
| **Ancient War Oak** | Jedna z implementacji Siege Lord; środkowy przeciwnik poziomu 5 w Green Frontier, przedstawiony jako lekko zabawny dąb wojenny z szeroką koroną i czytelnym pniem. Jego grafika jest renderowana w skali 30% dotychczasowej wielkości, bez zmiany parametrów celu, zdrowia ani kolizji. |
| **Frozen Troll** | Central Objective poziomu 15 i kolejnych specjalnych poziomów Frostbound; uwięziony w lodzie troll, którego sylwetka i głowa pozostają widoczne dla obu graczy. |
| **Special Objective Level** | Co piąty poziom każdego Chapteru. Ma względnie płaskie podejścia, jedno szerokie płaskie wzgórze na środku i aktywny Central Objective. |
| **Boss Level Terrain Profile** | Zasada generowania ostatniego poziomu Chapteru (`10, 20, 30...`): teren pozostaje deformowalny i lekko pofałdowany, ale używa łagodnego profilu wysokości oraz obniżonych mas górskich, aby nie tworzyć dużych gór zasłaniających walkę z bossem. |
| **Battle Timer** | Odliczanie na górze pośrodku ekranu ograniczające czas całej bitwy. |
| **Exit Level Button** | Przycisk HUD-u przerywający bieżący poziom i wracający do Campaign Map bez zmiany postępu. |

## Screens / Ekrany

| Term | Meaning |
| --- | --- |
| **Start Menu** | Ekran główny gry. Pozwala rozpocząć wejście do kampanii albo uruchomić TEST Mode. |
| **Dashboard** | Ekran centralny między Start Menu a Campaign Map. Pokazuje Lives, OrCoins oraz przejścia do Campaign Map i Shop. |
| **Campaign Map** | Ekran wyboru poziomów trwałej kampanii. Każdy Chapter ma własną mapę w swoim biomie, z dziesięcioma kolejnymi punktami połączonymi jedną ścieżką. Mapa pokazuje punkty ukończone, aktualny, zablokowane, specjalne i Boss Node. |
| **Chapter Map** | Tematyczna mapa jednego Chapteru zawierająca dziesięć kolejnych poziomów. Jest ekranem wyboru, a nie swobodnie eksplorowaną przestrzenią. |
| **Boss Node** | Wyraźnie wyróżniony punkt poziomu 10 danego Chapteru, oznaczający finałowego bossa i nagrodę za ukończenie rozdziału. |
| **Spell Loadout** | Ekran konfiguracji przed bitwą. Gracz wybiera maksymalnie trzy posiadane czary, które będą dostępne w danym poziomie. |
| **Campaign Spell** | Czar odblokowywany przez postęp kampanii. Po odblokowaniu ma nieskończoną dostępność w kolejnych poziomach i nie zużywa zapasu. |
| **Special Spell** | Czar kupowany w Shopie jako ograniczony zapas pojedynczych użyć. Zapas zmniejsza się dopiero po udanej aktywacji, nie przy wyborze loadoutu. |
| **Spell Stock** | Liczba posiadanych użyć Special Spell. Jest niezależna od odblokowania Campaign Spell i musi być widoczna w Shopie, loadoucie oraz HUD-zie. |
| **Temporary Spell Booster** | Planowany booster dający wybranemu Special Spell nielimitowane użycia przez 30 minut czasu rzeczywistego. Timer działa również offline; po wygaśnięciu czar wraca do normalnego zapasu i nie staje się Campaign Spell. |
| **Booster** | Tymczasowy bonus meta-gry. Nie zmienia na stałe odblokowania ani klasy czaru, amunicji lub jednostki. |
| **Battlefield** | Ekran aktywnej bitwy. Zawiera teren, fortece, katapulty, jednostki, pociski, efekty, HUD i interakcje gracza. |
| **Game Over / Result Screen** | Ekran wyniku po zakończeniu bitwy. Pokazuje zwycięzcę lub rezultat oraz umożliwia restart, powrót do Campaign Map albo przejście do następnego poziomu. |
| **Language Button** | Mała ikonka flagi w prawym górnym rogu Dashboardu. Przełącza język interfejsu między polskim (🇵🇱) i angielskim (🇬🇧). |
| **Selected Language Flag** | Flaga aktualnie wybranego języka wyświetlana na Language Button. |

## Meta-game resources / Zasoby meta-gry

| Term | Meaning |
| --- | --- |
| **Life** | Jedna próba rozpoczęcia kampanijnego poziomu. Przegrana próba zużywa jedno Life; TEST Mode nie zużywa Lives. |
| **Lives** | Wspólny dla kampanii zasób prób. Maksymalnie gracz posiada 10 Lives, a brakujące Lives odnawiają się automatycznie co 30 minut. |
| **OrCoins** | Trwała waluta gracza używana między innymi do odblokowywania czarów i przyszłych zakupów w Shop. |
| **Shop** | Planowany ekran zakupów za OrCoins. Dashboard zawiera przejście do Shop, nawet gdy konkretne oferty nie są jeszcze wdrożone. |
| **Precision Challenge** | Planowany event na płaskiej mapie treningowej. Halloweenowa wersja, **Pumpkin Trial**, używa nieruchomych dyń w różnych rozmiarach i odległościach zamiast tarcz; jest sezonowym theme, a nie stałym biomem kampanii. |
| **Pumpkin Trial** | Sezonowa halloweenowa wersja Precision Challenge. Mniejsze dynie są trudniejsze i dają więcej punktów; wynik zależy od rozmiaru, odległości oraz strefy trafienia. |
| **Marksman Points** | Tymczasowe punkty eventu Precision Challenge. Nie są OrCoins i służą wyłącznie do zdobywania eventowych losów. |
| **Event Draw Ticket** | Los zdobywany za Marksman Points. Większy wynik daje więcej losów, z limitem na event; losy są zdobywane przez grę, nie kupowane za prawdziwe pieniądze. |
| **Ad Reward** | Opcjonalna nagroda za obejrzenie reklamy, planowana jako dodatkowe Life. Nie jest aktywna bez skonfigurowanego dostawcy reklam. |
| **Default Language** | Język ustawiany przy pierwszym uruchomieniu gry. Obecnie jest nim polski (PL). |
| **Supported Languages** | Języki dostępne w bieżącej wersji: polski (PL) i angielski (EN). |
| **Player Avatar** | Ikona reprezentująca gracza na Dashboardzie. Kliknięcie otwiera Avatar Picker, w którym można wybrać i zapisać jeden z dostępnych portretów. |
| **Avatar Picker** | Modalna ramka wyboru avatara otwierana z Player Avatar; pokazuje dostępne portrety i pozwala zmienić aktywny avatar. |
| **Avatar Frame** | Ozdobna ramka nakładana na Player Avatar. Ramki będą nagrodami za ukończenie Eventów. |
| **Player Profile** | Przyszły ekran otwierany po kliknięciu Player Avatar, zawierający statystyki, wybrany avatar i kolekcję Avatar Frames. |
| **Event Reward** | Nagroda za ukończenie specjalnego Eventu; w tym projekcie może być nią Avatar Frame. |

## Terrain

### Planned Biome Catalog

The following 50 biome concepts were selected for future terrain themes. This is a design
catalog, not a claim that all of them are currently implemented in the game:

1. Szmaragdowa Dżungla
2. Popielne Pustkowie
3. Lodowa Korona
4. Sunken Marshes
5. Czerwony Kanion
6. Księżycowy Las
7. Pola Lawy
8. Zatopione Ruiny
9. Dolina Gigantów
10. Mchowe Wzgórza
11. Burzowe Wybrzeże
12. Złota Sawanna
13. Kraina Grzybów
14. Miedziany Las
15. Szklana Pustynia
16. Mglista Wyżyna
17. Czarne Bory
18. Koralowe Morze
19. Niebiańskie Wyspy
20. Podziemne Jeziora
21. Forteca Mrozu
22. Krwawe Mokradła
23. Las Kamiennych Drzew
24. Wydmy Wichru
25. Dolina Błyskawic
26. Fioletowa Knieja
27. Żelazne Mokradła
28. Kryształowe Jaskinie
29. Płonące Klify
30. Zielone Ruiny
31. Solne Równiny
32. Wielka Tajga
33. Morze Chmur
34. Zatrute Ogrody
35. Obsydianowe Pustkowie
36. Dolina Jesiennych Liści
37. Kraina Zorzy
38. Krwawy Księżyc
39. Bambusowa Dolina
40. Dolina Popiołowych Drzew
41. Bursztynowe Klify
42. Mroźne Mokradła
43. Kopalnie Orków
44. Arena Kości
45. Święty Gaj
46. Kraina Cieni
47. Tęczowe Źródła
48. Kwarcowe Równiny
49. Twierdza Burzowego Żelaza
50. Serce Pustki

**Miedziany Las** is assigned to Chapter III (levels 21–30). It is currently
a visual-only Terrain Theme: rust-red trunks, copper foliage, warm light,
brown soil, dry leaves, roots, stones, and distant rust-colored hills. It adds
no hazard, collision rule, movement modifier, damage rule, or projectile rule.

**Sunken Marshes** is assigned to Chapter V (levels 41–50). Its first
implemented layer is a visual-only background of dark turquoise-green wetlands,
crooked cypresses, reeds, rotten piers, fallen logs and purple mist. It adds no
hazard, collision rule, movement modifier, damage rule, or projectile rule.

| Term | Meaning |
| --- | --- |
| **Terrain** | Cały układ podłoża i otoczenia na polu bitwy. |
| **Ground Surface** | Zniszczalna powierzchnia, na której stoją fortece, katapulty i jednostki. |
| **Terrain Heightmap** | Aktualny profil wysokości definiujący Ground Surface, zmieniający się po uderzeniach. |
| **HUD-Safe Terrain Boundary** | Zasada mapy, według której cała generowana powobrierzchnia terenu znajduje się nad dolnym panelem ikon sterowania; HUD nie może zasłaniać terenu. |
| **Background-Bounded Zoom** | Ograniczenie oddalenia kamery do rozmiaru malowanego tła mapy; zoom nie może pokazywać pustej przestrzeni poza panoramą. |
| **Crater** | Zagłębienie wyryte w Ground Surface przez uderzenie pocisku lub eksplozję. |
| **Destructible Terrain** | Teren, który może zmieniać się pod wpływem amunicji i eksplozji. |
| **Terrain Obstacle** | Obiekt mapy, taki jak drzewo, skała lub dom, wpływający na wygląd pola albo tor pocisku. |
| **Tree** | Obecna przeszkoda dekoracyjna; może mieć różne warianty wizualne, ale obecnie nie blokuje pocisków. |
| **Rock** | Obecna przeszkoda dekoracyjna; może urozmaicać krajobraz, ale obecnie nie blokuje pocisków. |
| **House** | Obecny typ przeszkody z przygotowanym renderingiem; może reprezentować budynek na mapie. |
| **Current Terrain Obstacles** | Obecnie zdefiniowane typy przeszkód: Tree, Rock i House. W aktualnym układzie Chapter I drzewa i skały są rozmieszczone jako obiekty dekoracyjne, a ich kolizja jest wyłączona. |
| **Destructible Obstacle** | Przyszła przeszkoda możliwa do zniszczenia przez pocisk, np. wieża, barykada lub drewniana palisada. |
| **Blocking Obstacle** | Przyszła przeszkoda, która fizycznie blokuje lub zmienia tor pocisku. |
| **Explosive Obstacle** | Przyszła przeszkoda, która po trafieniu wybucha i zadaje obrażenia w promieniu. |
| **Defensive Obstacle** | Przyszła przeszkoda zapewniająca jednostkom lub fortecy osłonę przed częścią obrażeń. |
| **Terrain Hazard** | Przyszły niebezpieczny element terenu, np. lawa, bagno, ogień lub trująca chmura, wpływający na jednostki albo ruch. |
| **Obstacle Variant** | Wariant wizualny lub gameplayowy tego samego typu przeszkody, używany do różnicowania terenów w Chapterze. |
| **Cover** | Obiekt lub konstrukcja terenowa blokująca, zmieniająca kierunek albo osłabiająca atak. |
| **Terrain Modifier** | Zasada poziomu zmieniająca kształt terenu, widoczność, ruch, celowanie lub zachowanie pocisków. |
| **Objective-First AI Targeting** | Zachowanie AI na Special Objective Level: przeciwnik preferuje trajektorie i typy amunicji, które zadają obrażenia Central Objective, zamiast automatycznie celować wyłącznie w Player Fortress. |
| **Fortress Foundation** | Zawsze płaski i chroniony obszar podłoża pod twierdzą, który zapobiega tworzeniu kraterów pod jej podstawą. |
| **Grass Edge** | Widoczna granica między Ground Surface a górną warstwą tła pola bitwy. |
| **Terrain Material** | Materiał wizualny powierzchni, np. trawa, ziemia, kamień, błoto, popiół lub skażona ziemia. |
| **Terrain Material Tile** | Kafelek materiału terenu dobierany na podstawie kształtu powierzchni, np. płaski, łagodny stok, stromy stok, dolina lub grzbiet. |
| **Terrain Tile Variant** | Jedna z kilku grafik tego samego typu Terrain Material Tile, zachowująca ten sam profil i punkt bazowy, ale różniąca się detalami. |
| **Terrain Theme** | Spójny zestaw tła, materiałów, krawędzi trawy, kraterów, dekoracji, palety światła i efektów dla Chapteru lub grupy poziomów. |
| **Canonical Biome Contract** | Wspólny kontrakt prezentacji, którego wzorcem jest Copperwood (biom 3): opaque soil base, cap `384×72`, cap baseline, wspólna geometria `48/56`, skala soilu `384 JS`, kontakt fortecy z capem, kolejność warstw, anchory i walidacja assetów. Inne biomy zmieniają materiały i palety, ale nie te parametry bez jawnie opisanej decyzji. |
| **Terrain Decoration** | Element otoczenia wzbogacający wygląd mapy, np. krzew, kwiat, ruina, pień, flaga, kałuża lub ślad kół. |
| **Ambient Asset** | Planowany, wizualny element atmosferyczny biomu, np. deszcz, śnieg, mgła, popiół, liście, pył lub iskry. Jest deterministyczny, niekolizyjny i nie wpływa na fizykę ani rozgrywkę. |
| **Ambient Elements** | Zbiorcza nazwa dla Ambient Assets używanych w Demo i przyszłych Terrain Theme. |
| **Relic** | Pojedynczy zakopany element dekoracyjny nanoszony jako osobny transparentny overlay na bazowy kafelek ziemi, np. moneta, czaszka, kubek, klucz albo kamień. Relics są wizualne, nie mają kolizji ani wpływu na fizykę. W kodzie używaj `BuriedRelic`, a w assetach wzorca `terrain_<biome>_relic_01.png`. |
| **Relic Size** | Kategoria rozmiaru `BuriedRelic`: `SMALL` mieści się na pojedynczym kafelku, `MEDIUM` może zajmować jeden lub dwa sąsiednie kafelki, a `LARGE` jest jednym spójnym overlayem obejmującym wiele kafelków. Kategoria zmienia wyłącznie prezentację i skalę; nie zmienia heightmapy, kolizji ani Gameplay Corridor. |
| **Special Relic** | Specjalny `BuriedRelic`, który po faktycznym odsłonięciu przez krater może uruchomić przypisaną nagrodę. Jest opcjonalnym celem poziomu, nie może być wymagany do zwycięstwa ani odkrywany wielokrotnie. Nagroda, warunek odsłonięcia i stan odebrania muszą być jawne oraz testowalne. |
| **Decorative Relic** | Zwykły `BuriedRelic` bez efektu gameplayowego. Służy wyłącznie do wzbogacenia wyglądu terenu i pozostaje wizualny. |
| **Relic Discovery** | Stan `Special Relic` osiągany dopiero wtedy, gdy deformacja terenu faktycznie odsłoni jego obszar. Samo trafienie obok albo wizualne pojawienie się krateru bez przecięcia obszaru Relic nie uruchamia nagrody. |
| **Relics** | Zestaw zakopanych elementów dekoracyjnych danego biomu. Każdy Relic ma kategorię `SMALL`, `MEDIUM` albo `LARGE`; obecne Relics są `SMALL`. Relics pozostają oddzielone od powtarzalnego materiału ziemi, są rozmieszczane deterministycznie i nie mogą naruszać seamless krawędzi bazowego kafelka. |
| **Aerial Bomber** | Proponowana jednostka: goblin w balonie lecący między fortecami i zrzucający małe bomby; bez implementacji. |
| **Wind Disruptor** | Proponowana jednostka: mały ork na latawcu tworzący lokalne, krótkotrwałe turbulencje; bez globalnej zmiany fizyki pocisków i bez implementacji. |
| **Burrowing Assault Unit** | Proponowana jednostka: ork na gigantycznym krecie używający kontrolowanego stanu `UNDERGROUND`; nie może dowolnie deformować heightmapy ani fundamentów. |
| **Sapper** | Zaimplementowany szybki rajder (`260` prędkości), maksymalnie jeden aktywny naraz. Zwykłe jednostki mogą go atakować przy kontakcie, ale nie blokują go całkowicie: kontakt spowalnia go do 65% normalnej prędkości, a ich ataki zadają mu 45% normalnych obrażeń. Dociera do celu, rzuca bombę z dystansu i wraca do własnej fortecy po kolejną. Po powrocie odzyskuje pełne HP i bombę; otrzymuje także obrażenia od katapulty, czarów i efektów obszarowych. |
| **Demolisher** | Zaimplementowana powolna (`110` prędkości), jednorazowa jednostka samobójcza. Detonuje przy kontakcie z Defenderem, Sapperem, Goblinem na Dziku, innym Demolisherem, fortecą, Bastionem lub przyszłą przeszkodą; jednostka wywołująca detonację otrzymuje pełne obrażenia, a pozostałe cele obrażenia zależne od odległości. |
| **Goblin on a Raging Boar** | Jednostka dostępna w Demo: szybki goblin na gigantycznym dziku, koszt `10 SUPPLY`, `420 HP`, `52` obrażenia, prędkość `240`, maksymalnie jeden w garrisonie. Ma 16-klatkową animację biegu; specjalna szarża i dostępność w kampanii pozostają niezaimplementowane. |
| **Goblin Slingmaster** | Zaimplementowana krucha jednostka dystansowa z ogromną procą, odblokowywana od poziomu 13. Atakuje widoczne cele kamieniami i ma celowo niską wytrzymałość oraz wolniejszy rytm ataku. |
| **Attack Type** | Jawna, rozszerzalna kategoria głównego ataku jednostki używana przez targeting, ruch, walkę, synergię, eventy i animację. Obecne typy to `MELEE`, `RANGED`, `CONTACT_EXPLOSIVE` oraz `SIEGE_MISSION`; przyszłe kategorie mogą obejmować np. atak z powietrza albo atak podziemny. |
| **Melee Attack** | Atak wymagający podejścia do celu i kontaktu. Defender i Goblin na Raging Boar należą do tej kategorii i uczestniczą w Melee Synergy. |
| **Ranged Attack** | Atak wykonywany z dystansu wobec widocznego celu. Goblin Slingmaster należy do tej kategorii i nie uczestniczy w Melee Synergy. |
| **Contact Explosive Attack** | Jednorazowy atak uruchamiany przez kontakt, który zadaje obrażenia obszarowe i niszczy jednostkę atakującą. Dotyczy Demolishera. |
| **Siege Mission Attack** | Specjalna misja jednostki skierowana przeciw fortecy, niezależna od standardowego ataku jednostka-na-jednostkę. Dotyczy obecnie Sapera. |
| **Unit Interaction Matrix** | Kanoniczna macierz relacji między Defenderem, Sapperem, Demolisherem, Goblinem na Dziku i Goblinem Slingmasterem. Pełny opis skopiowany z legacy znajduje się w `docs/UNIT_INTERACTIONS.md`. |
| **Melee Synergy** | Wspólny bonus walki wszystkich żywych jednostek walczących wręcz z tym samym celem. Początkowo obejmuje Defenderów i Gobliny na Dzikach; używa progresji `1.00x`, `1.35x`, `1.70x`, maksymalnie `2.00x`, i może zostać rozszerzony na kolejne profile walki wręcz. |
| **Damage Range** | Zakres obrażeń jednostki losowany przy każdym udanym ataku. Początkowo wynosi `90–110%` bazowej wartości obrażeń; wynik jest całkowity, ma minimum `1` i jest generowany przez seedowane źródło losowości symulacji. |
| **Attack Group Formation** | Zasady prezentacji i pozycjonowania członków grupy atakującej: jednostki nie stoją dokładnie jedna na drugiej, mogą częściowo na siebie nachodzić, ale żadna nie może zasłaniać prawie całej drugiej ani stać od niej w zbyt dużej odległości. |
| **Attack Group Joining** | Dynamiczna zasada grupy: kompatybilna sojusznicza jednostka, która dołącza do aktywnej walki i wybiera ten sam cel, staje się członkiem istniejącej grupy zamiast tworzyć równoległą grupę. |
| **Orc Banner Bearer** | Proponowana powolna jednostka wsparcia z chorągwią odwagi, dająca lokalny i czasowy bonus pobliskim sojusznikom; bez globalnych zmian statystyk i bez implementacji. |
| **Goblin Illusionist** | Proponowana krucha jednostka tworząca jedną lub dwie krótkotrwałe kopie wizualne; kopie nie zadają obrażeń, nie kolidują i nie zwiększają liczby jednostek; bez implementacji. |
| **Catapult Overdrive** | Proponowany czar dający drużynie rzucającej przez ograniczony czas zerowy czas ładowania jej katapulty. Koszt, czas trwania, stacking i limit strzałów pozostają nieustalone; czar niezaimplementowany. |
| **Gameplay Corridor** | Obszar terenu pozostawiony czytelny dla torów pocisków, jednostek i celowania; dekoracje nie mogą go zasłaniać bez wyraźnej reguły poziomu. |
| **Crater Material** | Wizualna warstwa wnętrza krateru, np. ciemna ziemia, odkryty kamień, popiół, żar lub skażenie. |
| **Crater Rim** | Obrzeże krateru zawierające wyrzuconą ziemię, rozciętą trawę, kamienie i ślady eksplozji. |
| **Impact Residue** | Trwały lub czasowy ślad pozostawiony na terenie przez konkretną amunicję albo efekt. |
| **Crater Record** | Dane świata opisujące krater: pozycję, promień, głębokość, materiał, źródło, wiek, intensywność i seed wariantów. |
| **Impact Cluster** | Połączony wizualnie zbiór bliskich kraterów, który zapobiega nakładaniu się chaotycznych obrzeży. |
| **Needle-Slice Terrain Adherence** | Zasada dopasowania grafiki do deformowalnego terenu: asset jest zbudowany z wielu cienkich pionowych kolumn („igieł”), a każda kolumna może zostać niezależnie rozciągnięta lub przesunięta do aktualnej wysokości profilu terenu. Dzięki temu magia, krater, ślad uderzenia i inne elementy zakotwiczone w tej warstwie nie tworzą płaskich mostów, łuków ani wiszących fragmentów. |
| **Needle-Slice Compatible Asset** | Asset przygotowany do użycia z Needle-Slice Terrain Adherence. Nie zawiera jednego płaskiego, wstępnie wyrenderowanego profilu gruntu; jego pionowe kolumny mają wystarczającą ciągłość, przezroczyste marginesy i wspólny punkt bazowy, aby renderer mógł dopasować je do dowolnego lokalnego kształtu terenu. |
| **Shared Terrain Presentation Contract** | Wspólna konfiguracja wizualna obowiązująca dla wszystkich Terrain Theme: wysokość Grass Cap, skala drzew, kotwiczenie i obrót kamieni na zboczach, skala dekoracji oraz zasady kompozycji. Biom może zmieniać materiały i paletę, ale nie powinien zmieniać tych proporcji bez świadomej decyzji. |
| **Flat Tree Footprint** | Odcinek terenu pod całą szerokością drzewa musi być lokalnie płaski. Drzewa mogą występować w dowolnej części mapy, ale nie na odsłoniętych zboczach ani na ostrym szczycie, jeśli wysokość zmienia się w obrębie ich obrysu. |
| **Fortress Approach Flat** | Płaski odcinek terenu przed każdą fortecą, o długości około jednej czwartej jej aktualnej szerokości. Przejście z tego odcinka do reszty mapy musi być łagodne; generator nie może tworzyć gwałtownych wzniesień ani ostrych ramp. Poziom fundamentu jest wyznaczany lokalnie dla każdej fortecy. |
| **Ambient Animation** | Delikatna animacja środowiska, np. poruszająca się trawa, mgła, dym, liście lub ogniki, niezwiązana bezpośrednio z atakiem. |
| **Art Forge (AF)** | Ogólna nazwa generatora grafiki używanego w projekcie do przygotowywania spójnych grafik terenu, jednostek, budynków, pocisków, animacji, efektów i elementów interfejsu. |
| **Art Generation Prompt (AGP)** | Ustrukturyzowana instrukcja dla AF opisująca rodzaj grafiki, styl, wariant, przezroczystość, skalę, światło, punkt zakotwiczenia i ograniczenia integracji. |
| **AGP dla terenu** | Skrócona komenda oznaczająca przygotowanie promptu dla AF do wygenerowania określonego zestawu grafik terenu. Na przykład „AGP dla terenu zimowego” oznacza prompt dla zimowego Terrain Theme, obejmujący odpowiednie materiały, profile kafelków, warianty, krawędzie i wymagania techniczne. |
| **AGP Copy-Ready Block** | Jeden kompletny blok tekstu do skopiowania do Art Forge. Zawiera prompt główny, wymagania techniczne, zasady palety i kompozycji, pełny negative prompt oraz obiektywne kryteria akceptacji. |
| **Reference Image Input** | Obowiązkowa sekcja każdego AGP informująca, że użytkownik dołączy grafikę referencyjną. Załączona grafika jest nadrzędnym źródłem stylu, palety, materiałów, oświetlenia, skali i kompozycji; AF nie powinien zastępować jej ogólnym stylem ani kopiować z niej niepowiązanej geometrii. |
| **Sprite Separation and Alpha** | Obowiązkowa sekcja AGP dla wielu grafik, arkuszy sprite'ów i kolekcji tekstur. Wymaga prawdziwego kanału RGBA alpha, bardzo dużych przezroczystych odstępów, braku kontaktu lub nachodzenia elementów, braku skażenia krawędzi oraz braku widocznej siatki, linii komórek, ramek, etykiet, szachownicy i tła. Podany układ wierszy i kolumn jest wyłącznie techniczny i nie może być narysowany na obrazie. |
| **Spell Icon AGP** | Kanoniczny, zapisany w `SPELL_ICON_AGP.md` prompt dla ikon wszystkich czarów. Przy generowaniu kolejnych ikon należy zachować jego wspólny kontrakt RGBA/alpha, styl orczego fantasy siege, światło z lewego górnego rogu, stałą skalę i przezroczyste tło. |
| **Spell Icon Category Palette** | Obowiązujący kod kolorystyczny ikon czarów: czerwony/pomarańczowy/złoty dla Offensive, szaroniebieski/turkusowy/srebrny dla Defensive, błękitny/cyjan/granatowy dla Battlefield Control, zielony/bursztynowy/złoty dla Support oraz fioletowy/brązowy/mosiężny/złoty dla Siege. |
| **Simplified Runic Spell Icon Direction** | Zatwierdzony kierunek ikon: identyczne matowe ciemne kamienne krążki, centralne geometryczne glify z 3–5 grubych linii, monochromatyczna tonacja zależna od kategorii, subtelna poświata wyłącznie na glifie, minimalna liczba detali i brak realistycznych przedmiotów. Pełne wymagania znajdują się w sekcji „Zatwierdzony kierunek uproszczenia ikon” pliku `SPELL_ICON_AGP.md`. |
| **Imported Spell Icon Set v2** | Zestaw dziesięciu ikon wcześniejszych czarów dostarczony jako `assets_src/user_reference/Urkaaaz_Catapult_Chaos_runic_icons_v2_FINAL_512_RGBA.zip`; zainstalowany w `app/src/main/res/drawable-nodpi/` jako pliki 512×512 RGBA z rzeczywistą przezroczystością. |
| **Berserker Icon** | Dostarczona osobno ikona Berserkera, zarchiwizowana jako `assets_src/user_reference/berserker_icon_source.png` i znormalizowana do `app/src/main/res/drawable-nodpi/berserker_icon.png` w rozmiarze 512×512 RGBA. |
| **/addSpell** | Projektowy workflow dodawania czaru: pojedyncze pytania o nazwę, kategorię i działanie, implementacja w modelu, silniku, HUD, sklepie/loadoucie, lokalizacji i testach, a następnie pełny copy-ready AGP dla runicznej ikony z RGBA, alpha, transparentnością i odstępem 50 px w arkuszu. |
| **/addBiome** | Projektowy workflow dodawania biomu: pojedyncze pytania o nazwę, klimat, materiały, dekoracje i opcjonalne reguły gameplayowe, następnie implementacja Terrain Theme, podpięcie do poziomów, assety, dokumentacja, testy i pełny copy-ready AGP terenu. |
| **Variant Rule** | Ogólna zasada Art Forge: każdy powtarzany typ assetu powinien mieć kilka kompatybilnych wariantów o wspólnej skali, stylu, palecie, świetle i punkcie zakotwiczenia. Materiały kafelkowe muszą dodatkowo mieć sprawdzone, pasujące krawędzie. |

## Campaign

| Term | Meaning |
| --- | --- |
| **Chapter** | Dziesięciopoziomowy fragment kampanii z własnym motywem, głównym celem, progresją i nagrodą. Chapter I obejmuje poziomy 1–10. |
| **Siege Sovereign** | Końcowy boss kampanii i najwyższa ranga centralnego przeciwnika. Jest nadrzędny wobec Siege Lord i stanowi główne zagrożenie finałowego poziomu. |
| **Boss** | Ogólne określenie potężnego przeciwnika zbliżającego się do Player Fortress, którego trzeba zatrzymać przed dotarciem do twierdzy. W finale kampanii konkretnym tytułem Bossa jest Siege Sovereign. |
| **Siege Lord** | Specjalny centralny przeciwnik występujący na poziomach 5, 15, 25 i kolejnych poziomach kampanii kończących się cyfrą 5. Jest silniejszy i ważniejszy niż zwykła jednostka, ale mniej rozbudowany niż pełny Boss. Ancient War Oak jest implementacją Siege Lord dla poziomu 5. |
| **Boss Level** | Ostatni poziom Chapter, czyli poziom 10, 20, 30 itd. Gracz walczy z mobilnym Siege Sovereign zamiast traktować Enemy Fortress jako główny cel. Zwycięża, jeśli pokona Siege Sovereign, zanim ten dotrze do Player Fortress i ją zniszczy. |
| **Campaign Map** | Ekran wyboru poziomów trwałej kampanii. |
| **TEST Mode** | Wewnętrzna bitwa z pełną zawartością, omijająca ograniczenia kampanii. |
