# Ryzyka edytora map

Dokument zawiera decyzje projektowe podejmowane podczas projektowania
osobnej aplikacji do ręcznego tworzenia map zależnych od biomu.

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

**Decyzja:** Obszary krytyczne są zablokowane przed edycją terenu.

**Konsekwencja:** Edytor musi wizualnie oznaczać te obszary i odrzucać
operacje rysowania lub wygładzania, które próbują je zmienić. Ich parametry
mogą być edytowalne wyłącznie przez dedykowane narzędzia zachowujące wspólny
kontrakt fortec, fundamentów i kotwic.

## Ryzyko 3: wygładzanie terenu zmienia pozycje dekoracji

**Pytanie:** Czy wygładzanie ma mieć pełne undo/redo, czy wystarczy zapis
poprzedniej wersji mapy?

**Decyzja:** Edycja przebiega dwuetapowo. Najpierw ustala się teren i
wygładza go, a dopiero po zatwierdzeniu geometrii odblokowywane jest
dodawanie dekoracji.

**Konsekwencja:** Dekoracje nie mogą być rozmieszczane na niezatwierdzonym
terenie. Zatwierdzenie terenu tworzy stabilną bazę dla późniejszych pozycji
assetów. Edytor nadal powinien zapewnić możliwość cofnięcia zmian w fazie
terenu albo zapisu wersji roboczej przed zatwierdzeniem.

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

## Ryzyko 7: kafelki terenu zamiast ręcznie rysowanej wysokości

**Rozważenie:** Kafelki mogą bardzo ułatwić składanie map i zapewnić spójny
wygląd biomu. Nie powinny jednak stać się jedynym źródłem geometrii. Sama
grafika kafelka nie wystarcza do fizyki, kolizji, wygładzania, fundamentów ani
walidacji płaskich fragmentów.

**Rekomendacja do rozstrzygnięcia:** użyć modelu hybrydowego. Edytor składa
teren z kafelków biomu, ale każdy kafelek ma opis geometrii i wysokości.
Z tych danych powstaje ciągły profil wysokości używany przez symulację, a
rendering korzysta z odpowiednich bitmap. Wtedy można wymieniać grafikę bez
zmiany fizyki, a wygładzanie działa na geometrii, nie na pikselach.

**Decyzja:** Wybieramy model hybrydowy: kafelki biomu oraz osobna geometria
wysokości używana przez fizykę i walidację.

**Konsekwencja:** Kafelek musi mieć określony profil wejściowy i wyjściowy,
wysokość, typ powierzchni oraz reguły łączenia z sąsiadami. Edytor nie może
uznać samego obrazu PNG za dowód poprawnej geometrii.

## Ryzyko 8: niezgodne krawędzie kafelków

**Pytanie:** Czy kafelki mają mieć jawne typy krawędzi i profili, a edytor ma
blokować niezgodne połączenia?

**Decyzja:** Tak. Kafelki mają jawne profile krawędzi, a sąsiedztwa muszą być
kompatybilne.

**Konsekwencja:** Katalog biomu powinien opisywać wejście i wyjście każdego
kafelka. Automatyczne wygładzanie może poprawiać drobne różnice wewnątrz
dozwolonego profilu, ale nie może ukrywać nieprawidłowego połączenia dwóch
różnych typów terenu.

## Ryzyko 9: różne rozmiary map i nieprzewidywalny koszt renderowania

**Pytanie:** Czy rozmiar świata i limity zawartości mają być definiowane
osobno dla każdego biomu?

**Decyzja:** Wszystkie mapy mają ten sam wymiar — aktualny rozmiar świata
używany przez grę.

**Konsekwencja:** Edytor może używać stałego viewportu i stałych granic
świata. Walidacja musi pilnować, aby kafelki, fundamenty i dekoracje mieściły
się w tym kanonicznym obszarze. Limity liczby dekoracji pozostają osobnym
tematem do ustalenia, jeśli pojawi się problem wydajności.

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

**Konsekwencja:** Autosave nie powinien nadpisywać ostatniego zatwierdzonego
JSON-a. Edytor musi rozróżniać szkic, zatwierdzoną geometrię terenu i finalny
eksport gotowy do użycia przez grę.

## Ryzyko 12: ewolucja formatu JSON

**Pytanie:** Czy starsze wersje JSON mają być jawnie migrowane do aktualnego
formatu?

**Decyzja:** Tak. Stosujemy jawne migracje według `schemaVersion`.

**Konsekwencja:** Edytor i gra muszą mieć określone migratory, testy każdej
obsługiwanej wersji oraz komunikat, gdy plik jest zbyt stary albo pochodzi z
nieobsługiwanej przyszłej wersji. Migracja nie może po cichu zmieniać
geometrii mapy bez zapisania wyniku jako nowej wersji.

## Ryzyko 13: zmiana terenu po dodaniu dekoracji

**Pytanie:** Czy ponowne odblokowanie edycji terenu ma powodować ponowną
walidację lub przesuwanie dekoracji?

**Decyzja:** Po dodaniu dekoracji zmiana terenu jest całkowicie zabroniona.

**Konsekwencja:** Edytor ma mieć trwałe przejście do fazy dekorowania.
Odblokowanie geometrii wymaga utworzenia nowej wersji/mapy albo jawnego
usunięcia dekoracji przed powrotem do edycji terenu. Nie wolno automatycznie
przesuwać assetów za zmienioną powierzchnią.

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
