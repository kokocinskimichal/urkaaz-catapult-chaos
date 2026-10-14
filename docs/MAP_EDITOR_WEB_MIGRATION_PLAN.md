# Plan migracji edytora map do aplikacji webowej

## Status dokumentu

Etapy 1–7 mają pierwszą lokalną implementację w `map-editor-web`. Swing
pozostaje utrzymany równolegle. Webowy edytor używa tego samego wersjonowanego
JSON i zachowuje `terrainTiles` przy imporcie/eksporcie; przeglądarkowa
walidacja jest raportem interaktywnym, a Kotlinowy validator nadal pozostaje
źródłem autorytatywnym.

Ten dokument jest planem działania. Nie oznacza rozpoczęcia implementacji ani
zmiany obecnego edytora. Aktualny edytor Swing pozostaje bez zmian do czasu
osobnej decyzji o rozpoczęciu prac.

## Cel

Zastąpić toporny interfejs Swing lokalną aplikacją uruchamianą w przeglądarce.
Nowy interfejs ma ułatwiać ręczne rysowanie profilu terenu, zachować proporcje
świata gry i zapewniać wygodny podgląd biomu, fortec, katapult oraz dekoracji.

Edytor webowy nie będzie drugim silnikiem gry. Wersjonowany JSON pozostanie
źródłem prawdy dla map, a reguły domenowe i walidacja nie mogą zostać
zastąpione luźną logiką w komponentach UI.

## Rekomendowany stos

- React;
- TypeScript;
- Vite;
- Canvas 2D do renderowania mapy i assetów;
- warstwa SVG albo interaktywna warstwa Canvas do rysowania profilu;
- lokalny import i eksport plików przez przeglądarkę;
- późniejsze opcjonalne opakowanie przez Tauri, jeśli potrzebny będzie
  samodzielny instalator desktopowy.

Nie rekomenduje się na tym etapie budowania całego UI w Kotlin/JS ani
przenoszenia domeny gry do JavaScriptu. Frontend powinien korzystać z jawnego,
wersjonowanego kontraktu JSON. Jeśli potrzebne będzie współdzielenie kodu
walidacji, należy najpierw przygotować stabilny kontrakt lub osobny moduł
Kotlin Multiplatform, zamiast kopiować reguły ręcznie.

## Zasady architektoniczne

1. `MapDefinition` i format JSON pozostają kontraktem mapy.
2. Frontend nie mutuje bezpośrednio stanu domenowego; każda zmiana przechodzi
   przez operację edytora.
3. Rendering, interakcja i zapis pliku są osobnymi warstwami.
4. Canvas nie jest źródłem danych — służy tylko do prezentacji.
5. Edytor musi zachować kanoniczny świat `7200 × 4800` oraz rzeczywiste
   proporcje fortec i katapult.
6. Stare mapy z próbkami co 40 jednostek muszą się otwierać bez konwersji
   niszczącej ich geometrię.
7. Próbkowanie profilu jest szczegółem zapisu, a nie ograniczeniem ruchu
   kursora podczas rysowania.

## Docelowy model edycji terenu

Kafelki nie są głównym narzędziem edycji. Nowy interfejs ma operować na
jednowymiarowym profilu górnej krawędzi terenu:

- oś X reprezentuje położenie w świecie;
- wartość Y reprezentuje wysokość terenu;
- użytkownik rysuje profil od lewej do prawej;
- edytor interpoluje narysowaną linię do próbek `TerrainProfile`;
- wynik jest sprawdzany względem maksymalnego nachylenia i granic świata;
- cały zakończony gest rysowania jest jedną operacją undo/redo.

Podstawowe operacje:

- `Rysuj profil` — swobodne rysowanie linii terenu;
- `Wygładź fragment` — wygładzenie zaznaczonego odcinka;
- `Wyrównaj fragment` — ustawienie zaznaczonego odcinka na jednej wysokości;
- `Cofnij` i `Ponów`.

Nie planuje się parametrów typowych dla malowania powierzchni 2D:

- promienia pędzla;
- siły działania;
- rozmiaru końcówki;
- trybu symetrycznego.

## Fundamenty

Fundamenty mają być widoczne jako osobne, chronione odcinki profilu.
Użytkownik może zmienić **wyłącznie ich wysokość**:

- szerokość fundamentu pozostaje ustalona;
- pozycja fortecy pozostaje ustalona;
- pozycja katapulty pozostaje ustalona;
- teren pod fundamentem zostaje wyrównany;
- wysokość katapulty jest synchronizowana z fundamentem;
- strefa chroniona jest synchronizowana z geometrią fundamentu;
- przejścia terenu po bokach są tworzone i walidowane jako część operacji.

Fundament ma być nieco dłuższy po stronie frontowej, czyli w kierunku środka
mapy:

- niebieska forteca: wydłużenie w prawo;
- czerwona forteca: wydłużenie w lewo.

Zwykłe rysowanie profilu nie może zmieniać odcinka fundamentu. Zmiana
fundamentu powinna być osobną operacją domenową, np.
`ChangeFoundationHeight`.

## Docelowy interfejs

### Układ

- główny obszar mapy zajmuje większość okna;
- lewy panel zawiera narzędzia edycji i fazę mapy;
- prawy panel pokazuje właściwości zaznaczonego fundamentu lub obiektu;
- dolny pasek pokazuje status walidacji, zoom i komunikaty;
- panel assetów jest filtrowany według biomu i kategorii.

### Interakcje

- lewy przycisk myszy rysuje profil albo umieszcza zaznaczony obiekt;
- przeciąganie mapy przesuwa widok;
- scroll lub gest trackpada zmienia zoom;
- kliknięcie fundamentu wybiera go do zmiany wysokości;
- kliknięcie obiektu zaznacza go i pokazuje jego właściwości;
- klawisze `Ctrl/Cmd+Z` i `Ctrl/Cmd+Shift+Z` obsługują historię;
- `Escape` anuluje bieżącą operację;
- podgląd zmiany jest widoczny przed zatwierdzeniem.

### Fazy

1. **Teren** — rysowanie profilu i edycja fundamentów.
2. **Zablokowany teren** — dekorowanie mapy.
3. **Gotowe do eksportu** — walidacja i zapis finalny.

Po rozpoczęciu dekorowania zwykłe rysowanie terenu jest zablokowane, tak jak
w obecnym modelu domenowym.

## Plan prac

### Etap 0 — decyzja i kontrakt

- zaakceptować kierunek webowy;
- ustalić, czy aplikacja ma działać wyłącznie lokalnie, czy również jako
  hostowana strona;
- ustalić, czy pierwsza wersja ma zastąpić Swing, czy działać równolegle;
- opisać docelowy kontrakt operacji rysowania profilu;
- nie zmieniać jeszcze kodu.

**Kryterium zakończenia:** zaakceptowany zakres i decyzja o trybie uruchamiania.

### Etap 1 — przygotowanie kontraktu i migracji

- zidentyfikować wszystkie pola JSON używane przez obecny edytor;
- sprawdzić import map z próbkami co 20 i 40 jednostek;
- określić migrację starszych dokumentów;
- ustalić sposób udostępniania assetów webowej aplikacji;
- zaprojektować operacje domenowe:
  `DrawTerrainProfile`, `SmoothTerrainRange`,
  `FlattenTerrainRange`, `ChangeFoundationHeight`;
- nie usuwać jeszcze kafelków z formatu JSON.

**Kryterium zakończenia:** dokumentowany kontrakt wejścia, wyjścia i operacji.

### Etap 2 — szkielet aplikacji webowej

- utworzyć osobny frontend React/TypeScript/Vite;
- dodać routing lub pojedynczy ekran edytora;
- dodać stan dokumentu, fazy i historii operacji;
- dodać lokalny import/eksport JSON;
- dodać ekran błędów i komunikaty walidacyjne;
- użyć przykładowej mapy bez podłączania pełnej edycji.

**Kryterium zakończenia:** można otworzyć, wyświetlić i zapisać dokument bez
utraty danych.

### Etap 3 — renderer mapy

- odwzorować świat `7200 × 4800`;
- renderować tło, soil, cap, teren, fortece i katapulty;
- zachować proporcje ustalone dla gry;
- dodać zoom, pan i dopasowanie widoku;
- dodać warstwę debug profilu, fundamentów i stref chronionych;
- dodać skalowanie HiDPI.

**Kryterium zakończenia:** mapa webowa wizualnie odpowiada podglądowi gry i
nie zmienia geometrii przy zoomie.

### Etap 4 — rysowanie profilu

- dodać tryb rysowania odcinka profilu;
- pokazywać linię podglądu przed zatwierdzeniem;
- interpolować linię do próbek terenu;
- ograniczyć profil do świata i maksymalnego sąsiedniego nachylenia;
- zachować nietknięte fundamenty i strefy chronione;
- zapisywać cały gest jako jedną operację historii;
- dodać wygładzanie i wyrównywanie zaznaczonego zakresu.

**Kryterium zakończenia:** autor może stworzyć mapę bez użycia kafelków.

### Etap 5 — fundamenty

- zaznaczać fundamenty jako osobne elementy profilu;
- umożliwić zmianę wyłącznie wysokości;
- automatycznie aktualizować teren pod fundamentem;
- synchronizować fortecę, katapultę, strefę chronioną i przejścia;
- wydłużyć strefę fundamentu po stronie frontowej;
- dodać walidację i undo/redo dla zmiany wysokości.

**Kryterium zakończenia:** żadna zwykła operacja rysowania nie może uszkodzić
fundamentu, a zmiana jego wysokości nie może pozostawić niespójnych danych.

### Etap 6 — dekoracje i assety

- dodać panel assetów według biomu;
- umieścić dekoracje przez kliknięcie lub przeciągnięcie;
- obsłużyć pozycję, skalę i rotację zgodnie z kontraktem;
- zachować trwałe identyfikatory;
- pokazywać kotwice i granice obiektów w trybie debug;
- nie przywracać automatycznie ograniczeń dekoracji, których projekt świadomie
  nie wymaga.

**Kryterium zakończenia:** dekoracje są widoczne, edytowalne i eksportują się
bez utraty właściwości.

### Etap 7 — walidacja, eksport i kompatybilność

- uruchamiać walidację przed eksportem;
- pokazywać błędy przy konkretnym fragmencie mapy;
- zachować autosave lokalny;
- testować round-trip: JSON → aplikacja → JSON;
- testować stare eksporty `ch1l2` i istniejące definicje kampanii;
- przygotować raport migracji i ewentualne ostrzeżenia.

**Kryterium zakończenia:** eksport webowy jest akceptowany przez istniejący
loader i walidator map.

### Etap 8 — decyzja o zastąpieniu Swing

- porównać wygodę rysowania z obecnym Swing;
- wykonać test na kilku mapach kampanii;
- sprawdzić import, eksport, zoom, historię i fundamenty;
- zdecydować, czy Swing pozostaje jako narzędzie awaryjne;
- dopiero po akceptacji usunąć lub zdeprecjonować stare UI.

## Testy i walidacja

Testy muszą obejmować:

- deterministyczne próbkowanie profilu;
- zachowanie początku i końca świata;
- limit sąsiedniego nachylenia;
- brak możliwości rysowania po fundamencie;
- zmianę wyłącznie wysokości fundamentu;
- wydłużenie fundamentu w prawidłowym kierunku;
- synchronizację fortecy, katapulty i strefy;
- undo/redo całego gestu;
- round-trip JSON;
- import istniejącego `Chapter1Level2`;
- eksport bez utraty assetów i identyfikatorów;
- działanie przy zoomie i na ekranie HiDPI.

## Ryzyka

| Ryzyko | Ograniczenie |
|---|---|
| Drugi frontend może rozjechać się z Kotlinem | JSON i jawny kontrakt pozostają źródłem prawdy |
| Canvas może utrudnić precyzyjne wskazywanie profilu | osobna warstwa geometrii i podgląd przed zatwierdzeniem |
| Assety Kotlin/JVM nie są automatycznie dostępne w przeglądarce | przygotować jawny katalog webowy lub pipeline kopiowania |
| Przeglądarka może ograniczać zapis plików | użyć eksportu pobieranego przez użytkownika; File System Access API traktować jako opcję |
| Web UI może działać inaczej na różnych ekranach | testy HiDPI, zoom i stała transformacja świata |
| Tauri zwiększy zakres pierwszej wersji | odłożyć wrapper do osobnego etapu |

## Poza zakresem pierwszej wersji

- serwer, logowanie i współdzielenie map;
- edycja gameplayu poza geometrią mapy;
- zmiana pozycji fortec lub katapult;
- symetryczne generowanie map;
- pełny system manifestu assetów;
- automatyczne zastąpienie wszystkich Kotlinowych definicji kampanii;
- Tauri, jeśli wersja przeglądarkowa będzie wystarczająca.
