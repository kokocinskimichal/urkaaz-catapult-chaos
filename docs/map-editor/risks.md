# Ryzyka edytora map

Dokument zapisuje ryzyka i decyzje dla authoringu map w zakładce Maps
Urkaaaz Studio.

## Ryzyko 1: dwa źródła prawdy dla map

**Pytanie:** Czy JSON z edytora ma stać się jedynym źródłem prawdy dla map,
a obecne definicje map zapisane w Kotlinie mają zostać docelowo usunięte lub
wygenerowane z JSON-a?

**Decyzja:** JSON jest jedynym źródłem prawdy.

**Konsekwencja:** Kotlinowy model mapy powinien pozostać kontraktem domenowym,
ale definicje poziomów powinny być ładowane z wersjonowanych plików JSON.
Nie utrzymujemy ręcznie równoległych definicji tej samej mapy w Kotlinie i
JSON-ie.

## Ryzyko 2: przypadkowa zmiana obszarów krytycznych

**Pytanie:** Czy edycja terenu może zmieniać fundamenty fortec, kotwice
katapult, podejścia i korytarze ostrzału?

**Decyzja:** Obszary krytyczne, w tym fundamenty fortec, pozostają chronione
przed zwykłą edycją profilu.

**Konsekwencja:** Edytor musi wizualnie oznaczać te obszary i odrzucać
operacje rysowania lub wygładzania, które próbują je zmienić. Ich parametry
mogą być edytowalne wyłącznie przez dedykowane narzędzia zachowujące wspólny
kontrakt fortec, fundamentów i kotwic.

## Ryzyko 3: wygładzanie terenu zmienia pozycje dekoracji

**Decyzja:** Teren i dekoracje są edytowalne w dowolnej kolejności. `Y`
każdego dekoracyjnego obiektu jest niezależną współrzędną world-space;
zmiana profilu nie przesuwa obiektów. `Y` można zmienić ręcznie, a każda
zmiana jest operacją undo/redo. Relics zachowują pozycję; operacja
naruszająca ich soil footprint jest jawnie odrzucana.

**Konsekwencja:** Nie ma globalnego locka ani fazy dekorowania. Walidacja
geometrii i Relics nadal blokuje niepoprawny zapis; edycja profilu nie
przepisuje współrzędnych dekoracji.

## Ryzyko 4: zmiana biomu po dodaniu dekoracji

**Pytanie:** Czy biom można zmienić po zatwierdzeniu terenu i dodaniu
dekoracji?

**Decyzja:** Mapa jest na stałe związana z jednym biomem.

**Konsekwencja:** Biom wybiera się przed dodawaniem assetów i zapisuje jako
niezmienną właściwość mapy. Zmiana biomu oznacza utworzenie nowej mapy albo
jawne użycie osobnego procesu konwersji; edytor nie będzie po cichu podmieniać
assetów ani interpretować istniejących dekoracji w kontekście innego biomu.

## Ryzyko 5: uszkodzony lub niezgodny JSON

**Pytanie:** Czy eksport ma zawsze tworzyć wersjonowany plik JSON z
automatyczną walidacją i blokować zapis mapy niespełniającej kontraktów gry?

**Decyzja:** Tak. JSON jest wersjonowany, a eksport jest blokowany przy
błędach walidacji.

**Konsekwencja:** Edytor i gra muszą używać wspólnego schematu oraz wspólnego
walidatora. Raport eksportu powinien wskazywać konkretny obiekt, właściwość i
powód odrzucenia.

## Ryzyko 6: brak manifestu assetów

**Pytanie:** Czy każdy biom ma mieć manifest assetów opisujący rozmiar,
anchor i dozwoloną skalę, a edytor ma blokować użycie pliku bez opisu?

**Decyzja:** Na początku wystarczy konwencja nazewnicza plików. Manifest
assetów zostanie dodany później.

**Konsekwencja:** Pierwsza wersja edytora może wyprowadzać kategorię, biom i
wariant z nazwy pliku oraz używać ustalonych domyślnych anchorów i skal.
To świadomie pozostawia ryzyko niepełnej metadanej; przed dodaniem bardziej
zróżnicowanych assetów należy wprowadzić manifest bez zmiany formatu map.

## Ryzyko 7: druga geometria terenu w kafelkach

**Pytanie:** Czy profil wysokości i placementy soil tiles mają być
serializowane równolegle?

**Decyzja:** `terrain.heightSamples` jest jedyną geometrią fizyczną mapy.
Soil i cap są dobierane z biomu i nie mają osobnych placementów.

**Konsekwencja:** Random Map i ręczna edycja zmieniają wyłącznie próbki
istniejącego profilu. Bitmapa ani kafelek nie mogą stać się drugim źródłem
wysokości lub reguł kolizji.

## Ryzyko 8: nieciągły profil po generacji lub edycji

**Pytanie:** Jak uniknąć ostrych uskoków przy proceduralnym profilu terenu?

**Decyzja:** Zachować istniejący sample step 20 lub 40 i autorytatywny limit
`MapDefinitionValidator.MAX_TERRAIN_DELTA` równy 200 world units.

**Konsekwencja:** Generator używa szerokich, gładkich form; operacje ręczne
nie mogą po cichu tworzyć profilu niewalidowalnego. Błąd walidacji wskazuje
konkretną próbkę, a Kotlinowy walidator pozostaje runtime gate.

## Ryzyko 9: różne rozmiary map i nieprzewidywalny koszt renderowania

**Pytanie:** Czy rozmiar świata i limity zawartości mają być definiowane
osobno dla każdego biomu?

**Decyzja:** Wszystkie mapy mają ten sam wymiar — aktualny rozmiar świata
używany przez grę.

**Konsekwencja:** Edytor może używać stałego viewportu i stałych granic
świata. Walidacja musi pilnować, aby teren, fundamenty i dekoracje mieściły
się w tym kanonicznym obszarze. Random Map ma ograniczone presety gęstości
z maksimum 18 zwykłych dekoracji; ręczne mapy nie dostają globalnego limitu
poza obecnym kontraktem i kontrolą wydajności.

## Ryzyko 10: niestabilne identyfikatory obiektów

**Pytanie:** Czy edytor ma automatycznie nadawać każdemu obiektowi trwałe,
unikalne ID i nie używać ponownie usuniętego ID w tej samej mapie?

**Decyzja:** Tak. Każdy obiekt ma trwałe, unikalne ID.

**Konsekwencja:** ID nie może zależeć od kolejności obiektów w tablicy ani od
pozycji na mapie. Przeniesienie, zmiana assetu lub skali zachowuje ID, a
usunięcie i ponowne dodanie obiektu tworzy nowe ID.

## Ryzyko 11: utrata pracy podczas awarii edytora

**Pytanie:** Czy aplikacja ma automatycznie zapisywać lokalną wersję roboczą
po większych operacjach oraz tworzyć osobny eksport finalny po zatwierdzeniu
mapy?

**Decyzja:** Tak. Wersja robocza jest zapisywana automatycznie, a eksport
finalny jest osobną operacją.

**Konsekwencja:** Autosave nie powinien nadpisywać ostatniego repozytoryjnego
JSON-a. Edytor rozróżnia lokalny szkic od jawnie zapisanego Chapter/Level;
nie przechowuje osobnej fazy zatwierdzenia geometrii.

## Ryzyko 12: ewolucja formatu JSON

**Pytanie:** Czy starsze wersje JSON mają być jawnie migrowane do aktualnego
formatu?

**Decyzja:** Tak. Stosujemy jawne migracje według `schemaVersion`.

**Konsekwencja:** Edytor i gra muszą mieć określone migratory, testy każdej
obsługiwanej wersji oraz komunikat, gdy plik jest zbyt stary albo pochodzi z
nieobsługiwanej przyszłej wersji. Migracja nie może po cichu zmieniać
geometrii mapy bez zapisania wyniku jako nowej wersji.

## Ryzyko 13: zmiana terenu po dodaniu dekoracji

**Decyzja:** Zmiana terenu pozostaje dozwolona i nie zmienia world-space `Y`
żadnych dekoracji. Autor ręcznie ustawia wysokość.

**Konsekwencja:** Każda operacja terrain musi atomowo przeliczyć pozycje
Relics pod kątem soil footprintów, ale nie może przesuwać obiektów. Jeśli
zmiana terenu naruszyłaby footprint Relic, operacja jest odrzucana bez zmiany
szkicu.

## Ryzyko 14: zmiana nazw lub usunięcie assetów

**Pytanie:** Czy edytor ma zapisywać wersję/hash assetu albo kopiować assety
do paczki mapy, aby wykrywać późniejsze zmiany?

**Decyzja:** Zakładamy, że nazwy plików assetów pozostają stałe.

**Konsekwencja:** JSON będzie odwoływał się do assetów po nazwie i nie będzie
na początku przechowywał kopii ani hashy. Zmiana nazwy pliku jest świadomie
ryzykiem kompatybilności i wymaga później narzędzia do aktualizacji
referencji, jeśli problem zacznie występować.

## Ryzyko 15: niespójna skala kafelków

**Pytanie:** Czy biom ma używać jednej kanonicznej siatki kafelków i stałej
skali, do której każdy kafelek musi pasować bez dowolnego skalowania?

**Decyzja:** Tak. Obowiązuje jedna kanoniczna siatka i stała skala kafelków.

**Konsekwencja:** Kafelki muszą mieć zgodne wymiary, punkty łączenia i
jednostki świata. Edytor nie powinien pozwalać na swobodne skalowanie
pojedynczego kafelka, ponieważ rozjechałoby to geometrię, dekoracje i
walidację krawędzi.

## Ryzyko 16: poprawna geometria, ale zablokowany tor pocisku

**Pytanie:** Czy eksport ma sprawdzać korytarze ostrzału i minimalną
widoczność między fortecami?

**Decyzja:** Nie sprawdzamy tego w edytorze.

**Konsekwencja:** Walidacja edytora obejmuje teren, kafelki, fundamenty,
obiekty i dekoracje, ale nie ocenia jakości toru pocisku ani widoczności.
Ryzyko projektowe pozostaje po stronie autora mapy i może wymagać później
osobnego podglądu symulacji, jeśli pojawią się problemy w rozgrywce.

## Ryzyko 17: platforma edytora

**Pytanie:** Czy edytor ma być aplikacją webową czy natywną aplikacją
desktopową?

**Decyzja:** Edytor jest lokalną aplikacją webową uruchamianą w przeglądarce.
Osobny edytor desktopowy Swing został usunięty.

**Konsekwencja:** Interfejs korzysta z przeglądarkowego Canvas i importu/
eksportu plików. Android pozostaje klientem runtime map i nie przejmuje
odpowiedzialności za authoring.
