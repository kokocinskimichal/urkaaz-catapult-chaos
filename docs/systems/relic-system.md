# Relics System

## 1. Cel i zakres

Relics System opisuje relikty występujące na mapie kampanii Urkaaaz.

W obecnym zakresie relikty są wyłącznie **dekoracyjne**:

- są elementami umieszczonymi wewnątrz warstwy `soil`;
- nie zmieniają terenu;
- nie mają kolizji gameplayowej;
- nie zadają obrażeń;
- nie wpływają na zasoby, AI, amunicję, wynik ani czas meczu;
- nie posiadają runtime state.

Relikty funkcjonalne i aktywne są przyszłym rozszerzeniem. Nie należy
rezerwować dla nich zachowania w obecnym runtime ani dodawać ich efektów do
`MapObjectPlacement`.

Dokument opisuje architekturę, granice odpowiedzialności i wymagania
walidacyjne oraz aktualnie dostępne assety reliktów `SMALL` i `MEDIUM`.

## 2. Klasyfikacja reliktów

### 2.1. Relikty dekoracyjne

Relikt dekoracyjny jest szczególnym zastosowaniem istniejącego
`MapObjectPlacement`:

```text
type = MapObjectType.DECORATION
collision = ObjectCollision.NONE
```

Relikt jest rozpoznawany przez niepusty `relicSize` oraz stabilny `assetKey`
w katalogu zasobów. Relikty `SMALL` są biomowe, a dostępne relikty `MEDIUM`
mają wspólny zestaw dla wszystkich biomów:

```text
relic.<biome>.small.<index>
relic.shared.medium.<name>
```

Wspólny zestaw zawiera 10 assetów dostarczonych w częściowym archiwum z
`Downloads`; brakujące pozycje z jego README nie są zastępowane placeholderami.

`assetKey` identyfikuje wygląd, ale sam w sobie nie jest regułą gameplayową.
Pozycja, skala i obrót pozostają danymi authoringu mapy.

### 2.2. Klasy rozmiaru i częstotliwość

Relikty dekoracyjne dzielą się na trzy klasy rozmiaru:

| Klasa | Charakterystyka | Częstotliwość |
|---|---|---|
| `SMALL` | mały, lokalny element dekoracyjny | najczęstszy |
| `MEDIUM` | większy element zajmujący wyraźny obszar `soil` | rzadszy |
| `LARGE` | duży, dominujący element dekoracyjny | najrzadszy, maksymalnie jeden na mapę |

Rozmiar wpływa na footprint, czytelność kompozycji i dobór miejsca, ale nie
zmienia klasy `MapObjectType`, kolizji ani wpływu na gameplay.

Częstotliwość nie powinna być losowana niezależnie podczas uruchamiania meczu.
Dla authoringu generowanego proceduralnie musi wynikać z jawnego seeda i
konfiguracji poziomu, a dla poziomów materializowanych — z zapisanych
placementów. Walidator musi odrzucić definicję zawierającą więcej niż jeden
relikt `LARGE`.

### 2.3. Relikty funkcjonalne — przyszłe rozszerzenie

Relikty funkcjonalne nie należą do obecnego zakresu. W przyszłości mogą:

- aktywować się pasywnie, warunkowo lub ręcznie;
- modyfikować reguły meczu;
- emitować eventy domenowe;
- posiadać runtime state i cykl życia;
- wymagać snapshotów, komend, UI oraz granicy z persystencją.

Nie należy implementować ich jako specjalnych dekoracji z ukrytymi efektami.
Powinny otrzymać osobny kontrakt i osobną decyzję architektoniczną,
wykorzystując istniejące granice `game-contracts`, `game-domain`,
`game-simulation`, `game-application` i `android-app`.

## 3. Właściciele odpowiedzialności

| Odpowiedzialność | Właściciel |
|---|---|
| Stabilny kontrakt placementu mapy | `game-contracts` |
| Authoring reliktu w definicji poziomu | `game-campaign` |
| Walidacja położenia i ograniczeń mapy | `game-campaign.MapDefinitionValidator` |
| Przeniesienie obiektu do snapshotu odczytowego | istniejący snapshot/map definition |
| Mapowanie do stanu renderowania | `android-app.RenderStateMapper` |
| Rysowanie assetu | renderer Androida |
| Wpływ na fizykę, AI, obrażenia i wynik | brak w zakresie reliktów dekoracyjnych |

Nie należy tworzyć osobnego modułu ani równoległego kontraktu reliktów
dekoracyjnych. Istniejący przepływ mapy jest wystarczający:

```text
LevelAuthoringRequest
    ↓
MapObjectPlacement
    ↓
MapDefinition
    ↓
MapDefinitionValidator
    ↓
MatchScenario / MatchSnapshot
    ↓
RenderState.mapObjects
    ↓
Battlefield renderer
```

## 4. Rozmieszczenie w definicji poziomu

Rozmieszczenie reliktów należy zapisywać w definicji konkretnego poziomu,
w tym samym pliku co pozostałe placementy mapy:

```text
game-campaign/
  src/main/kotlin/com/urkaaaz/campaign/
    chapterX/ChapterXLevelY.kt
```

Każdy relikt powinien być materializowany jako placement zawierający co
najmniej:

- stabilne `id`;
- klasę rozmiaru `SMALL`, `MEDIUM` albo `LARGE`;
- `assetKey`;
- pozycję świata;
- skalę i obrót;
- dane wymagane do obliczenia pełnego footprintu.

Definicja poziomu jest źródłem prawdy dla rozmieszczenia reliktów. Ten sam
poziom uruchomiony ponownie musi otrzymać te same relikty w tych samych
miejscach.

Jeżeli rozmieszczenie jest generowane narzędziem authoringowym, generator może
korzystać z seeda, ale jego wynik musi zostać zapisany w pliku poziomu przed
uruchomieniem gry. Runtime nie może losować, przesuwać, usuwać ani zmieniać
rozmiaru reliktów podczas startu poziomu.

Takie rozwiązanie zapewnia:

- deterministyczne replaye;
- powtarzalne testy walidatora;
- stabilny balans kampanii;
- możliwość ręcznej korekty placementów;
- jednoznaczny przegląd mapy w code review;
- zgodność reliktów z konkretną geometrią `soil`.

Nie należy tworzyć osobnego generatora reliktów uruchamianego przez
`MatchSession`, `Match` ani `DeterministicMatchSimulation`.

W rozdziałach 1–5 każdy z 50 materializowanych poziomów ma obecnie trzy
deterministyczne placementy `RelicSize.SMALL`. Nie są losowane podczas startu
meczu. Rozdział 5 (`SUNKEN_MARSHES`) korzysta jawnie z zestawu Green Frontier,
ponieważ legacy nie zawierało osobnego zestawu assetów dla tego biomu.
Wspólne assety `MEDIUM` są dostępne w katalogu i edytorze dla każdego biomu;
ich dodanie nie zmienia placementów istniejących poziomów.

## 5. Kontrakt danych

Relikt dekoracyjny korzysta z `MapObjectPlacement`, który zapewnia:

- stabilne `id`;
- typ `MapObjectType.DECORATION`;
- jawna klasa rozmiaru `RelicSize.SMALL`, `RelicSize.MEDIUM` albo
  `RelicSize.LARGE`;
- pozycję świata `x`, `y`;
- `scale`;
- `rotationDegrees`;
- stabilny `assetKey` (`relic.<biome>.small.<index>` albo
  `relic.shared.medium.<name>`);
- jawne `collision = ObjectCollision.NONE`;
- informację o dopuszczalnym nakładaniu przez `allowOverlap`;
- deterministyczny footprint wynikający z `RelicSize` i `scale`.

Nie należy dodawać do kontraktu pól opisujących efekt reliktu, aktywację,
obrażenia, bonusy ani zasoby.

### 4.1. Granice obecnego kontraktu

Sam punkt `(x, y)` nie wystarcza do udowodnienia, że cały relikt mieści się w
`soil` albo nie nachodzi na katapultę. Walidacja musi korzystać z
deterministycznych danych o footprintach assetów.

W aktualnej implementacji walidator używa konserwatywnego, typowanego
footprintu `RelicSize` i uwzględnia transformację obrotu przez prostokąt
ograniczający. Docelowo źródłem dokładniejszych danych może być wspólny,
stabilny katalog metadanych assetów, dostępny dla authoringu i walidatora.
Katalog powinien opisywać
przynajmniej:

- lokalny bounding box lub wielokąt assetu;
- punkt zakotwiczenia;
- zakres renderowania w osi X i Y;
- zasady wpływu skali i obrotu na footprint;
- informację, czy asset może być reliktem dekoracyjnym.

Nie należy powielać tych wymiarów ręcznie w każdym poziomie ani w kodzie
Androida.

## 6. Zasady walidacji mapy

`MapDefinitionValidator` jest właścicielem odrzucania nieprawidłowych
placementów. Walidacja odbywa się podczas budowania `MapDefinition`, przed
uruchomieniem meczu. Implementacja sprawdza pełny konserwatywny footprint,
granice świata, powierzchnię `soil`, fundamenty, katapulty, strome zbocza,
nakładanie z innymi obiektami oraz limit jednego reliktu `LARGE`.

### 5.1. Relikt musi mieścić się w `soil`

Cały footprint reliktu po zastosowaniu pozycji, skali i obrotu musi znajdować
się w obszarze soil:

```text
world bounds
    ┌─────────────────────────────┐
    │                             │
    │       pełny relikt          │
    │      ╭──────────╮           │
    │──────┴──────────┴──────     │  soil surface
    │        terrain             │
    └─────────────────────────────┘
```

Walidator musi odrzucić relikt, gdy jakakolwiek część jego footprintu:

- znajduje się poniżej lub powyżej dozwolonej powierzchni `soil`;
- wychodzi poza granice świata;
- jest zawieszona nad pustą przestrzenią;
- wchodzi w fundament albo chronioną strefę mapy, jeżeli reguły authoringu
  tego zabraniają.

Sprawdzenie nie może ograniczać się do środka assetu. Należy sprawdzić pełny
obrys po transformacji. Dla nieregularnych assetów preferowany jest
konserwatywny footprint, aby uniknąć sytuacji, w której wizualny fragment
reliktu wychodzi poza `soil`.

### 5.2. Relikt nie może nachodzić na katapultę

Relikt dekoracyjny nie może nachodzić na katapultę ani na jej obszar
fundamentu. Walidacja musi uwzględniać:

- pełny footprint reliktu po transformacji;
- szerokość i położenie katapulty;
- wysokość fundamentu;
- przypisaną chronioną strefę, jeśli istnieje;
- ustalony margines bezpieczeństwa dla czytelności i ochrony placementu.

Naruszenie tej reguły jest błędem definicji poziomu, niezależnie od
`allowOverlap`. `allowOverlap` nie może umożliwiać nachodzenia na katapultę,
fortecę ani chronioną strefę. Relikty `SMALL` są przechowywane jako środek
footprintu wewnątrz `soil`, a renderer rysuje je w world-space przed
fortecami i katapultami.

### 5.3. Pozostałe reguły

Walidator powinien zachować istniejące reguły dla obiektów mapy:

- `assetKey` nie może być pusty;
- `id` musi być stabilne i unikalne;
- `scale` musi być dodatnia;
- dekoracje nie mogą znajdować się na fundamentach ani w ich marginesie;
- dekoracje nie powinny być umieszczane na zbyt stromych zboczach;
- obiekty nie mogą nachodzić na siebie, chyba że jawnie dopuszcza to
  authoring i nie narusza to reguł katapult, fortec ani soil;
- definicja musi zachować deterministyczne metadane authoringu.

Błąd powinien wskazywać ścieżkę placementu i przyczynę odrzucenia, np.
`objects[4]`: relikt wychodzi poza soil albo `objects[4]`: relikt nachodzi na
katapultę `blue-catapult`.

## 7. Runtime i rendering

Relikt dekoracyjny jest częścią niemutowalnej definicji mapy. `Match` ani
symulacja nie powinny tworzyć jego kopii jako stanu gameplayowego.

W runtime:

- `MapDefinition` dostarcza placementy;
- snapshot może ujawniać je jako dane odczytowe;
- `RenderStateMapper` przekształca je do `MapObjectRenderState`;
- renderer rysuje relikty `SMALL` i `MEDIUM` w world-space z użyciem
transformacji kamery;
- dekoracja nie emituje eventów i nie przyjmuje komend.

Renderowanie nie może naprawiać błędnego placementu przez przycinanie,
przesuwanie ani automatyczne zmniejszanie reliktu. Nieprawidłowa mapa ma zostać
odrzucona wcześniej przez `MapDefinitionValidator`.

Assety reliktów powinny być rozwiązywane przez stabilny `assetKey`, a nie przez
bezpośrednie odwołania do zasobów Androida w kontraktach lub domenie.

## 8. Deterministyczność i authoring

Relikty dekoracyjne są danymi authoringu. Ich położenie, skala, obrót i
`assetKey` muszą być zapisane jawnie w definicji poziomu.

Jeżeli są generowane narzędziem authoringowym:

- generator musi korzystać z jawnego seeda;
- wynik musi być materializowany w definicji poziomu;
- runtime nie może losować reliktów podczas uruchamiania meczu;
- footprint użyty podczas authoringu i walidacji musi pochodzić z tej samej
  wersjonowanej definicji assetu.

Zmiana footprintu assetu jest zmianą danych authoringowych i może wymagać
ponownej walidacji istniejących poziomów.

## 9. Testowalność

Przyszła implementacja walidacji powinna posiadać testy kontraktowe dla:

1. reliktu w całości mieszczącego się w `soil`;
2. reliktu wystającego poza powierzchnię `soil`;
3. reliktu częściowo zawieszonego nad pustą przestrzenią;
4. reliktu nachodzącego na niebieską katapultę;
5. reliktu nachodzącego na czerwoną katapultę;
6. reliktu dotykającego marginesu ochronnego katapulty;
7. obróconego lub przeskalowanego reliktu, którego footprint wychodzi poza
   `soil`;
8. pustego lub nieznanego `assetKey`;
9. duplikatu `id`;
10. więcej niż jednego reliktu `LARGE` w jednej mapie;
11. poprawnego reliktu renderowanego przez `RenderStateMapper`.

Testy powinny sprawdzać odrzucenie definicji, a nie tylko wynik renderowania.

## 10. Przyszłe rozszerzenie: relikty funkcjonalne

Dodanie reliktów funkcjonalnych wymaga osobnego przeglądu architektury.
Minimalny zakres decyzji będzie obejmował:

- katalog typów i stabilne identyfikatory;
- sposób wyposażania lub przyznawania reliktu;
- model aktywacji;
- właściciela efektu;
- komendy i warunki odrzucenia;
- runtime state i lifecycle;
- snapshoty oraz eventy;
- kolejność względem fizyki, obrażeń, zasobów i AI;
- konflikty i priorytety efektów;
- persystencję odblokowań oraz granicę z kampanią;
- prezentację efektu w Androidzie.

Nie należy rozszerzać `MapObjectType.DECORATION` o zachowania gameplayowe.
Dekoracyjny relikt powinien pozostać wizualnym placementem mapy nawet po
dodaniu odrębnego systemu reliktów funkcjonalnych.

## 11. Kryteria akceptacji architektury

Architektura jest spełniona, gdy:

- relikty dekoracyjne używają istniejących kontraktów mapy;
- nie powstaje drugi równoległy system placementów;
- walidator odrzuca relikt, którego pełny footprint wychodzi poza `soil`;
- walidator odrzuca relikt nachodzący na katapultę lub jej strefę ochronną;
- relikt nie wpływa na stan, fizykę, AI ani wynik meczu;
- rendering korzysta z niemutowalnego stanu odczytowego;
- placementy są deterministyczne i możliwe do ponownej walidacji;
- przyszłe relikty funkcjonalne mają wyraźną granicę od dekoracji.
