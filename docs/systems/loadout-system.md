# LoadoutSystem

## Status i cel

Główny przepływ LoadoutSystemu jest zaimplementowany. `game-application` ma
kanoniczny `MatchLoadout`, deterministyczne defaulty, normalizację zapisu,
typowane akcje, walidację, porty repozytorium/progresji oraz read-only
projekcję katalogów balansu dla UI. `MatchScenario` przenosi dostępność
poziomu oddzielnie od wyboru gracza, a `MatchSession` odrzuca niewybrane
jednostki i typy amunicji. Produkcyjne adaptery DataStore profilu i loadoutu
oraz zatwierdzenie przed nawigacją działają. Ekran umożliwia edycję trzech
kategorii, pokazuje statusy i liczniki, a wspólna podstrona szczegółów
zachowuje draft i pozycję przewinięcia; systemowy Back zamyka ją przed
opuszczeniem Loadoutu. CampaignSystem przyznaje Haste/Heal po ukończeniu
właściwych poziomów; tutoriale pozostają poza zakresem LoadoutSystemu i będą
własnością przyszłego TutorialSystem. Brakujące ilustracje zaklęć są jawne.

Ten dokument opisuje docelową architekturę LoadoutSystemu oraz migrację
zachowania loadoutu z aplikacji legacy. Niewdrożone elementy są oznaczone
poniżej jako blokery, a nie działające funkcje.

LoadoutSystem ma umożliwiać graczowi wybór jednostek, amunicji i czarów dla
wybranego poziomu kampanii, zwalidować ten wybór, zapisać go lokalnie i
przekazać jako niemutowalną konfigurację uruchomienia meczu. Zachowanie
wyboru, domyślnych wartości i ekranu ma zachować parytet z legacy, ale
odpowiedzialności muszą trafić do obecnych modułów Urkaaaz.

Nie jest planowany import zapisów z aplikacji legacy. Nowa aplikacja zapisuje
loadout w swoim repozytorium; zachowuje reguły wyboru i oczyszczania
nieaktualnych danych, ale nie odczytuje preferencji innej aplikacji.

## Zakres

W zakresie:

- wybór od 1 do 3 unikalnych typów jednostek, z zachowaniem kolejności;
- wybór trzech slotów amunicji, z Rock na stałe w slocie pierwszym i
  maksymalnie dwoma dodatkowymi typami;
- wybór do 3 unikalnych czarów;
- deterministyczne wartości domyślne i walidacja względem poziomu, odblokowań
  oraz zapasów;
- odczyt i zapis zatwierdzonego wyboru przez port repozytorium;
- przekazanie zatwierdzonego wyboru do konfiguracji meczu;
- ekran loadoutu zachowujący legacy flow, informacje o zawartości, liczniki,
  stan przycisku startu oraz domyślne wybory wynikające z nagród kampanii;
- typowane wyniki walidacji oraz jawna informacja o korektach odczytanego
  zapisu.

Poza zakresem:

- definicje i balans jednostek, amunicji oraz czarów — pozostają w ich
  istniejących katalogach i konfiguracjach;
- odblokowania, waluta, zakupy, nagrody i trwałe zapasy — pozostają własnością
  systemu progresji/sklepu; LoadoutSystem wywołuje ich przypadki użycia, gdy
  legacy flow tego wymaga;
- zużywanie zapasów i egzekwowanie zasad podczas meczu — należy do
  runtime’u meczu;
- wybór loadoutu przeciwnika AI — pozostaje poza zapisanym loadoutem gracza;
- postęp kampanii, nawigacja, Android lifecycle oraz logika gameplayowa czarów;
- import zapisów/preferencji z aplikacji legacy.

## Istniejące rozwiązania i ryzyko duplikacji

`docs/architecture/architecture.md` już przewiduje `application.loadout` z
`MatchLoadout`, `LoadoutValidator` i `LoadoutRepository`, a
`campaign-system.md` opisuje przepływ `Campaign → Loadout → Match`. To
zatwierdzony kierunek, który należy uzupełnić, a nie zastępować nowym modułem
ani drugim równoległym systemem.

Kanoniczny `MatchLoadout` znajduje się teraz w
`game-application/application/loadout`. Wcześniejszy, nieużywany
`game-domain.SpellLoadout` został usunięty, aby nie tworzyć równoległego
modelu wyboru czarów. Projekcja `PlayerLoadoutConfig` w `game-contracts`
przenosi zatwierdzony wybór przez granicę meczu.

`CampaignLevelDefinition` dostarcza dostępne jednostki, amunicję i czary.
`MatchScenario` rozdziela tę dostępność od zatwierdzonego wyboru, a
`CampaignLevelRegistry` i mapper poziomu przenoszą obie informacje.
`LocalMatchGateway`/`MatchSession` przyjmują scenariusz; jego wybór jest
przekazywany do `SimulationConfig`, a warstwa sesji egzekwuje jednostki i
amunicję dla gracza. Wspólny kontrakt `CastSpell` trafia do SpellSystemu;
efekt gameplayowy mają tylko czary oznaczone w katalogu jako zaimplementowane.

Tryb demo używa tego samego ekranu i walidatora loadoutu, ale udostępnia
wszystkie jednostki, typy amunicji i czary z katalogów, niezależnie od
dostępności kampanii, odblokowań i zapasów gracza. Zapis demo ma osobny klucz
DataStore i wersję schematu, niezależne od wyborów kampanii. Zatwierdzony
wybór trafia do scenariusza demo z mapą i wiatrem demo, a wybrane jednostki,
amunicja i czary pozostają ograniczone przez loadout w runtime meczu.

## Właściciel i kierunek zależności

Właścicielem przypadków użycia, walidacji, defaultów i portu repozytorium jest
`game-application`. To warstwa aplikacyjna koordynuje dostępność kampanii,
postęp gracza, zatwierdzenie wyboru i utworzenie konfiguracji meczu. System
nie wymaga nowego modułu Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| Definicje i balance jednostek, amunicji, czarów | istniejące konfiguracje `game-domain` |
| Dostępność zawartości na poziomie | `game-campaign` / `CampaignLevelDefinition` |
| `MatchLoadout`, use case’y, walidacja i port zapisu | `game-application` |
| Dostępność gracza, odblokowania, złoto i trwały stock | aplikacyjny system progresji/sklepu |
| Adapter lokalnego zapisu | `android-app` |
| Draft, prezentacja i input ekranu | screen-owned ViewModel/controller w `android-app` |
| Tutoriale i ich stan | przyszły, przekrojowy TutorialSystem |
| Egzekwowanie zatwierdzonego wyboru podczas meczu | `MatchSession` i domena/symulacja przez kontrakt meczu |
| Zakupy, zużycie stocku podczas meczu i gameplay czarów | właściwe systemy progresji oraz runtime’u meczu |

`game-application` może zależeć od kontraktów, kampanii i typów domenowych,
ale nie od Androida. Adapter Androida implementuje port repozytorium i
prezentuje stan; nie ustala reguł dostępności ani nie mutuje domeny.
Zatwierdzony wybór musi dotrzeć do meczu jako niemutowalny kontrakt albo
konfiguracja scenariusza. Nie wolno wprowadzać zależności
`game-simulation → game-application`; przejście z aplikacyjnego `MatchLoadout`
do projekcji kontraktowej należy wykonać na granicy aplikacji.

## Kontrakty wejściowe i wyjściowe

### Wejścia

- Typowana definicja wybranego poziomu, w tym dostępne typy jednostek,
  amunicji i czarów.
- Niemutowalny odczyt progresji: odblokowane czary, zapas amunicji i czarów
  specjalnych oraz saldo, jeśli ekran inicjuje zakup zgodnie z legacy.
- Zapisany wybór gracza odczytany z portu `LoadoutRepository`.
- Interaktywne akcje ekranu: wybór/odznaczenie jednostki, amunicji lub czaru
  oraz zatwierdzenie albo powrót.

Poziom określa, co jest dozwolone w danym meczu; lokalny snapshot progresji
określa, co gracz odblokował i posiada. Obie informacje są wymagane.
`PlayerProgressionUseCases` dostarcza profil inicjalizowany potwierdzonym
seedem. `CampaignAwareLoadoutProgressionPort` łączy profil z kampanijnymi
odblokowaniami; zakupy pozostają poza zakresem.

### Wyniki

- Niemutowalny, zwalidowany `MatchLoadout`, z typowanymi jednostkami,
  dokładnie trzema slotami amunicji i wybranymi czarami.
- Jawny wynik walidacji/zatwierdzenia: zaakceptowany loadout albo typowane
  odrzucenie z polami/kategoriami wymagającymi poprawy.
- Wynik odczytu zapisanego wyboru z informacją, czy odrzucono nieznane,
  zablokowane, niedostępne, zduplikowane lub nadmiarowe pozycje i czy użyto
  defaultu.
- Zapis zatwierdzonego wyboru przez port repozytorium.
- Niemutowalny, kontraktowy wybór gracza do konfiguracji meczu, odrębny od
  listy zawartości dostępnej na poziomie.

`MatchScenario`/konfiguracja uruchomienia musi przenosić zarówno dostępność
poziomu, jak i zatwierdzony wybór gracza bez utożsamiania pól
`available*` z loadoutem. Dla jednostek należy dodać analogiczną do amunicji i
czarów informację o dostępności, jeśli wymaga jej runtime. Kontrakt meczu nie
może importować typu z `game-application`; aplikacja mapuje zatwierdzony
loadout na stabilne identyfikatory kontraktu. Runtime odrzuca komendy wyboru
amunicji, rekrutacji lub czaru, jeśli typ jest dostępny na poziomie, ale nie
należy do loadoutu gracza.

### Typowane odrzucenia

Wynik powinien rozróżniać co najmniej: nieznany identyfikator, pozycję
niedostępną na poziomie, brak odblokowania, brak zapasu, duplikat, przekroczenie
limitu oraz niepoprawną strukturę slotów amunicji. Błąd zatwierdzenia nie
zapisuje wyboru ani nie przechodzi do meczu. UI mapuje typowane przyczyny na
lokalizowane komunikaty; nie interpretuje wyjątków ani dowolnych tekstów
błędów.

## Reguły parytetu z legacy

### Jednostki

- Lista ma od 1 do 3 unikalnych typów jednostek; zachowuje kolejność wyboru.
- Default to pierwsze maksymalnie trzy dostępne typy w kolejności
  `SiegeUnitType.entries` z legacy, zachowanej przez bieżącą kolejność
  `UnitConfigurations.all`.
- Nie można odznaczyć ostatniej jednostki. Niedostępnych jednostek nie można
  dodać.
- Wartości kosztów i statystyk pozostają w centralnych konfiguracjach
  jednostek; loadout nie kopiuje balansu.

### Amunicja

- Loadout ma dokładnie 3 sloty. `ROCK` zajmuje slot 1 i pozostaje zawsze
  dostępny oraz nieograniczony.
- Sloty 2 i 3 mogą zawierać unikalne, dostępne typy amunicji albo być puste;
  brak stocku blokuje wybór amunicji ograniczonej.
- UI pokazuje `ROCK` jako stale wybraną kartę w tej samej karuzeli co pozostałe
  typy. Wybranie typu dodaje go do pierwszego wolnego slotu 2/3, a ponowne
  wybranie usuwa go z jego slotu; interfejs nie wymaga wskazania slotu.
- Default to `ROCK` oraz pierwsze maksymalnie dwa inne dostępne typy w
  kanonicznej kolejności katalogu; pozostałe sloty są puste.
- Zapisane typy są filtrowane według aktualnego poziomu i stocku. Amunicja
  specjalna jest konsumowana podczas meczu przez runtime, nie przez ekran
  loadoutu.

### Czary i progresja

- Loadout może zawierać maksymalnie 3 unikalne czary; przy zatwierdzaniu meczu
  musi zawierać co najmniej jeden, tak jak wymaga legacy przycisk startu.
- Default to maksymalnie trzy dostępne, odblokowane czary w kolejności
  katalogu.
- Zwykły czar musi być dostępny na poziomie. Dostępny, ale zablokowany czar
  może być kupiony przez istniejący/przyszły use case progresji za cenę z
  definicji czaru; LoadoutSystem nie odejmuje złota samodzielnie.
- Czary specjalne legacy (`SpellType.special`) mogą być wyposażane niezależnie
  od `CampaignLevelDefinition.availableSpells`, ale tylko gdy progresja
  raportuje dodatni stock. Kliknięcie specjalnego czaru bez stocku prowadzi
  do sklepu; selektor nie kupuje go bezpośrednio.
- CampaignSystem przyznaje Haste po zwycięstwie na poziomie 5 (dostępność od
  poziomu 6) i Heal po zwycięstwie na poziomie 7 (dostępność od poziomu 8).
  Kampanijne czary są preferowane tylko w nowym domyślnym loadoucie; zapisany
  wybór pozostaje nienaruszony.
- Tutoriale poziomów 6/8 nie należą do LoadoutSystemu. Ich reguły, stan i UI
  wdroży późniejszy TutorialSystem.

### Zapis i odtwarzanie wyboru

Legacy zapisuje wybór po zatwierdzeniu rozpoczęcia meczu, oddzielnie od
odblokowań i zapasów. Nowy system również zapisuje tylko zatwierdzony wybór;
draft pozostaje w stanie ekranu. Przy otwarciu następnego loadoutu zapis jest
filtrowany według bieżących dostępności i uprawnień. Jeśli po filtrowaniu
jednostki lub czary są puste, używany jest deterministyczny default, a wynik
odczytu informuje o normalizacji. Wybór amunicji zawsze zachowuje Rock w
pierwszym slocie. Nieznane lub niepoprawne dane nie mogą powodować cichego
sukcesu ani awarii ekranu.

Nie należy przenosić preferencji ani kluczy `SharedPreferences` legacy.
Format i adapter lokalny nowej aplikacji są szczegółem `android-app`; zmiany
schematu w obrębie nowej aplikacji muszą mieć własną, jawną wersję migracji.

## Stan, cykl życia i przepływ

LoadoutSystem nie posiada runtime state meczu ani nie działa w tickach.
Trwałym stanem jest wyłącznie zatwierdzony wybór; złoto, odblokowania i stock
pozostają poza nim. Tymczasowy draft należy do `LoadoutViewModel` i znika po
opuszczeniu/odtworzeniu ekranu, o ile nie został zatwierdzony.

```text
CampaignLevelDefinition + snapshot progresji
  → LoadoutSystem: odczyt, filtrowanie i default
  → LoadoutScreen/ViewModel: prezentacja i draft
  → akcje UI → LoadoutSystem: walidacja aktualnych danych
  → zapis zatwierdzonego MatchLoadout
  → projekcja kontraktowa w MatchScenario / konfiguracji meczu
  → MatchSession → symulacja
```

1. Campaign przekazuje typowany poziom do ekranu loadoutu.
2. Aplikacyjny use case odczytuje progresję i zapis, filtruje nieaktualne
   wybory, wylicza defaulty i zwraca stan wraz z informacją o korektach.
3. ViewModel utrzymuje wyłącznie roboczy wybór prezentacyjny i wysyła
   typowane akcje aplikacyjne; nie mutuje domeny ani progresji.
4. Zakup odblokowywanego czaru przechodzi przez przypadek użycia progresji.
   Wynik odmowy zakupu (np. brak złota) pozostaje jawny i nie zmienia draftu.
5. Potwierdzenie ponownie waliduje dostępność/stock, zapisuje wybór i emituje
   typowaną konfigurację startu meczu. Nawigacja następuje dopiero po
   powodzeniu.
6. MatchSession przekazuje konfigurację do jedynego runtime’u. Restart meczu
   odtwarza ten sam zatwierdzony wybór; nowy poziom ponownie waliduje go wobec
   nowej definicji i progresji.
7. Powrót bez zatwierdzenia odrzuca draft i nie zmienia zapisu.

`MainActivity` nie przejmuje selektorów ani reguł. Ekran i jego controller
muszą być podłączone przez istniejące `AppScreen`, `AppNavigator` i
`ScreenHost`. `AppScreen.Loadout` przenosi numer wybranego poziomu, a
`AppScreen.Match` i MatchScreen mogą przenieść zatwierdzony wynik. Przejście do meczu jest dostępne po poprawnym odczycie profilu, walidacji
draftu i udanym zapisie. Błąd każdego z tych etapów blokuje nawigację.

## Determinizm, konflikty i granice progresji

LoadoutSystem nie używa losowości ani zegara ściennego. Defaulty, kolejność
wyboru, filtrowanie i wynik walidacji są deterministyczne dla tego samego
poziomu, snapshotu progresji i zapisu. Kolejność enumów/katalogu jest jawna i
testowana. Wynik meczu/replay nie zapisuje ani nie odtwarza loadoutu.

Poziom kampanii jest źródłem ograniczeń poziomu, a progresja źródłem
odblokowań i stocku. W konflikcie po odczycie wybór zostaje ponownie
zwalidowany przy zatwierdzaniu; aktualnie niedostępnej pozycji nie wolno
przekazać do meczu. Zakup jest atomowym przypadkiem użycia progresji, nie
mutacją z UI. Stock amunicji/czarów zużywa runtime i rozlicza go z progresją
przez jawny wynik/event, nie przez repozytorium loadoutu.

## Integracja z istniejącym meczem

Docelowa granica konfiguracji meczu ma rozdzielać:

- **dostępność** z `CampaignLevelDefinition`/scenariusza;
- **zatwierdzony wybór** gracza;
- **początkowe zasoby/stock** z konfiguracji progresji;
- **zużycie i stan runtime** należące do symulacji.

Nie używać `initialUnits` jako substytutu wybranego loadoutu ani listy
dostępności. Kontrakt scenariusza musi przenosić wybrane typy gracza osobno.
Przy komendach `SelectAmmo`, `DeployUnit` i `CastSpell` runtime weryfikuje
zarówno dostępność scenariusza, jak i wyposażenie gracza. Niepoprawna komenda
zwraca jawny błąd/odrzucenie i nie zużywa zasobów.

`MatchSession` nadal składa pozostałą konfigurację symulacji lokalnie, ale
otrzymuje `MatchScenario` przez gateway i przekazuje jego zatwierdzony loadout
do `SimulationConfig`. Wybrane jednostki i amunicja są egzekwowane przed
wywołaniem symulacji. Nie ma jeszcze obsługi castowania czarów; przekazanie
listy czarów jest kontraktem dla przyszłego SpellSystemu, nie deklaracją
działającego gameplayu.

## Android UI i assety

`LoadoutScreen` asynchronicznie odczytuje snapshot profilu i zapis poziomu,
pokazuje saldo, stock, korekty oraz edytowalne karty jednostek, amunicji i
czarów. Karta `ROCK` jest stale wybrana i zajmuje slot 1; kliknięcie innych
kart amunicji dodaje je do pierwszego wolnego slotu 2/3, a ponowne kliknięcie
usuwa typ z jego slotu.
Katalogi jednostek, amunicji i czarów są osobnymi poziomymi karuzelami w
trzech panelach landscape; pokazują po trzy kafelki i przewijają się w lewo
oraz w prawo niezależnie. Amunicja używa tej samej karuzeli co jednostki i
czary — bez osobnego wyboru aktywnego slotu ani dodatkowej karuzeli.
Wewnętrzny kontrakt zachowuje trzy sloty z `ROCK` na stałe w slocie 1.
Wszystkie karuzele pokazują najpierw zaznaczone i dostępne opcje, a potem
niedostępne, zablokowane i bez stocku; kolejność katalogowa wewnątrz grup
pozostaje stabilna. Kafelki we wszystkich kategoriach mają identyczną,
stałą szerokość i wysokość; dłuższe etykiety są ograniczane wewnątrz kafelka.
Wybrany kafelek wyróżnia świecące złote obramowanie i ciemny, złoto obwiedziony
znacznik wyboru w prawym górnym rogu zamiast widocznego napisu `SELECTED`;
przycisk `ⓘ` pozostaje osobno po lewej stronie.
Przycisk startu prowadzi do meczu wyłącznie po udanym zatwierdzeniu. Szczegóły
otwierają się wewnątrz ekranu; powrót zachowuje draft i scroll. Lifecycle
ekranu anuluje operacje w toku. `LoadoutCatalog` w `game-application`
udostępnia UI niemutowalne projekcje `UnitConfigurations`,
`AmmunitionCatalog` i `SpellType`, więc Android nie kopiuje wartości balansu
ani nie zależy bezpośrednio od domeny. `LoadoutAssetCatalog` mapuje istniejące
ikony jednostek i amunicji; brak ilustracji zaklęć jawnie zwraca `Missing` i
nie blokuje ekranu ani buildu.

Panel ma wysyłać akcje do screen-owned ViewModel/controller. Katalogi,
koszty, dostępność, filtrowanie i walidacja nie mogą być duplikowane w UI.
Informacje o jednostkach, amunicji i czarach są prezentacją istniejących
konfiguracji i lokalizacji.

### Stan wdrożenia i pozostałe prace

0. **[Wdrożone] Ustawić landscape jako orientację aplikacji.** Cała gra, w tym
   Loadout, jest zablokowana w landscape. Główny ekran Loadoutu używa trzech
   kolumn: jednostki, amunicja i czary; przewijanie jest lokalne dla każdej
   karuzeli elementów, a podsumowanie i akcja START MATCH pozostają w dolnym
   pasku.
   Referencyjny prompt AGP używa proporcji 16:9 i zrzutu użytkownika jako
   wskazówki kompozycyjnej, nie jako źródła danych gry.
1. **[Wdrożone] Przyjąć screenshot jako zatwierdzony kierunek wizualny.**
   Zachować jego pełnoekranowe tło, ciężkie ciemne panele, metalowo-drewniane
   wykończenia, złote zaznaczenia, karty z ilustracją i stałe podsumowanie
   nad przyciskiem startu. Screenshot jest referencją stylu i kompozycji, nie
   źródłem nazw, balansu, cen ani stanów profilu.
2. **[Wdrożone częściowo] Złożyć ekran z istniejących zasobów Urkaaaz.**
   Ekran używa istniejącego tła menu, paneli HUD oraz ikon jednostek i
   amunicji. Pozostaje dopasowanie tła/paneli do konkretnego biomu i kontrola
   wizualna na urządzeniu. Docelowe elementy to tło biomu poziomu,
   `dashboard_hud_top_panel.png`, `dashboard_hud_portrait_frame.png`,
   `dashboard_back_button.png` i `bg_hud_panel.xml`, o ile kontrola wizualna
   potwierdzi dopasowanie. Nie generować ponownie tła, przycisku powrotu,
   ramek portretów ani ilustracji jednostek/amunicji, które już istnieją.
3. **[Wdrożone] Dodać interaktywne selektory do istniejącego ekranu.**
   Wiersze kart
   jednostek, slotów amunicji i zaklęć wywołują istniejące
   `LoadoutViewModel.edit(LoadoutEditAction)`. UI pokazuje wybrany draft,
   limity wyboru, dostępność, blokady oraz stock; sam nie wylicza defaultów
   ani nie powtarza walidacji. `ROCK` jest stałym slotem pierwszym.
4. **[Wdrożone] Dodać wspólny ekran szczegółów przedmiotu loadoutu.**
   Każda karta
   jednostki, amunicji i zaklęcia ma mały, osobny przycisk `ⓘ`; kliknięcie
   głównej części karty nadal wybiera/odznacza, a `ⓘ` otwiera pełnoekranową
   podstronę szczegółów. Podstrona używa jednej kompozycji z inną ilustracją,
   opisem i właściwościami zależnie od typu. Jest częścią przepływu Loadout,
   nie nową globalną trasą `AppNavigator`: Loadout zachowuje draft, pozycję
   przewinięcia i stan progresji, a strzałka/systemowy Back zamyka szczegóły
   i wraca dokładnie do poprzedniego widoku. Opis i wartości pochodzą z
   mapowania prezentacyjnego po stabilnym ID; liczby pobiera się z
   autorytatywnych definicji, a UI nie kopiuje balansu ani reguł. Szczegóły
   nie uruchamiają zakupów ani zaklęć. Dla niedostępnej pozycji pokazują
   przyczynę blokady; zablokowany czar lub specjalny czar bez stocku może
   otworzyć wyłącznie wewnętrzny placeholder sklepu, bez transakcji i bez
   utraty draftu.
5. **[Oczekuje na assety] Dodać brakujące ilustracje kart zaklęć.** Użyć
   [`loadout-spell-card-art.agp`](loadout-spell-card-art.agp) do wygenerowania
   osobnych ilustracji dla istniejących `SpellType`. Użyte w karcie obrazy
   nie zawierają tekstu, ramki, stanu zaznaczenia ani kłódki; te elementy
   pochodzą z UI. Zestaw i status zaklęć nadal wynikają z katalogu,
   poziomu i progresji. Grafiki nie oznaczają, że runtime `CastSpell` jest
   zaimplementowany.
6. **[Wdrożone] Dodać warstwę mapowania assetów Androida.** Mapowanie
   stabilnych ID na `R.drawable` pozostaje w `android-app` (screen-owned
   `LoadoutAssetCatalog`). Istniejące mapowania są testowane; brakująca
   ilustracja zwraca jawne `Missing` i pozostawia natywną kartę tekstową.
   Nie przenosić `R` ani bitmap do modułów domenowych.
7. **[Wdrożone] Dokończyć stany i przepływ ekranu.** Zachować czytelny stan
   ładowania, błędu, braku odblokowania, braku stocku, zapisywania i korekty zapisu.
   Podsumowanie pokazuje liczniki z draftu, a przycisk startu jest aktywny
   tylko dla poprawnego draftu. Gold i stock pochodzą z profilu; zapasy nie są
   wpisywane do grafiki. Po nieudanym zapisie nawigacja nie następuje.
8. **[Milestone'y kampanii wdrożone; tutoriale odroczone.]**
   Haste/Heal są przyznawane przez `CampaignSystem` na podstawie zwycięstwa,
   nie numeru poziomu. Milestone może preferować czar przy budowaniu nowego
   domyślnego loadoutu. Tutoriali nie implementować tutaj; ich właścicielem
   będzie przyszły TutorialSystem.
9. **[Testy logiki wdrożone; testy widoku i kontrola wizualna pozostały]
   Zamknąć testy i kontrolę wizualną.** Testy ViewModelu sprawdzają blokadę
   startu przy awarii zapisu, typowany status odrzuconej akcji, status
   zablokowanego czaru i zachowanie draftu przy Back z detali. Testy katalogu
   sprawdzają projekcje konfiguracji oraz obecne/brakujące mapowania assetów.
   Pozostają testy dotykowych interakcji widoku i przegląd ekranu na urządzeniu
   o małej i dużej wysokości.

Zakupy pozostają osobną granicą progresji/sklepu. Loadout może pokazać czar
zablokowany lub specjalny bez stocku i jawny wynik odmowy, ale nie może
prezentować transakcji jako udanej, dopóki aplikacyjny use case zakupu nie
zwraca rzeczywistego wyniku. Implementacja sklepu i rozliczania nagród nie
jest ukrytym warunkiem poprawności lokalnych selektorów.

### Plan assetów i użycie

| Element ze screenshotu | Stan w Urkaaaz / decyzja | Użycie w Loadout |
|---|---|---|
| Pejzaż w tle | Są `background_*.png` dla biomów oraz `dashboard_background.png`; nie generować drugiego pejzażu. | Dobierać istniejące tło do biomu poziomu; położyć ciemny scrim, żeby karty miały czytelny kontrast. |
| Drewniano-metalowy nagłówek i przycisk powrotu | Są `dashboard_hud_top_panel.png` i `dashboard_back_button.png`. | Użyć na pasku tytułu/nawigacji, zachowując responsywną geometrię i natywny tekst. |
| Ramy portretów i panele sekcji | Są `dashboard_hud_portrait_frame.png`, `bg_hud_panel.xml` i `bg_menu_soft_focus.xml`. | Wykorzystać jako wspólny język ramek, powierzchni i przyciemnienia; stany wyboru zrobić przez Android drawable, tint i obramowanie. |
| Karty jednostek | Są ikony `defender_icon.png`, `sapper_icon.png`, `demolisher_icon.png`, `goblin_raging_boar_icon.png`, `goblin_slingmaster_icon.png` oraz istniejące sprite'y jednostek. | Wygenerować pięć osobnych ilustracji pełnego kafelka zgodnie z `loadout-unit-card-art.agp`, zachowując ikony jako referencje tożsamości. Nie zmieniać sprite'ów bitewnych ani animacji. |
| Karty amunicji | Są `ammo_icon_siege_bomb.png`, `ammo_icon_powder_barrel.png`, `fire_rain_icon.png`, `plague_cauldron_icon.png` oraz `anim_ammunition_rock_flight_000.png`. | Mapować do `SIEGE_BOMB`, `POWDER_BARREL`, `FIRE_RAIN`, `PLAGUE_CAULDRON` i `ROCK`; `ROCK` zawsze w slocie 1. Nie generować osobnej grafiki ROCK. |
| Karty zaklęć | W `android-app` nie ma ilustracji kart zaklęć; nazwy i efekty zaklęć widoczne na referencji nie odpowiadają katalogowi Urkaaaz. | Wygenerować 11 osobnych ilustracji dla istniejących zaklęć zgodnie z `loadout-spell-card-art.agp`; tekst, etykiety, blokady i zaznaczenie pozostają natywnymi warstwami. |
| Wejście do szczegółów | Selektory kart Loadoutu dopiero powstaną; wybór musi pozostać odrębny od otwarcia opisu. | Dodać mały przycisk `ⓘ` na każdej karcie. Główna powierzchnia wybiera, `ⓘ` otwiera podstronę szczegółów; powrót nie zmienia wyboru ani draftu. |
| Podstrona szczegółów | Brak wspólnego ekranu informacji o jednostce, amunicji i zaklęciu. | Użyć jednej responsywnej kompozycji z ikoną/ilustracją, nazwą, krótkim opisem, właściwościami i stanem dostępności. Dane i liczby mapować po typowanym ID; nie powielać balansu w Androidzie. |
| Licznik golda | Gold jest częścią snapshotu progresji i obecnie jest prezentowany przez ekran. | Pokazywać rzeczywiste saldo jako tekst/dane UI. Nie wpisywać stałej kwoty ani nie generować ikony monety, jeśli istniejący asset wystarczy. |
| Plus przy goldzie / zakup | Nie ma działającej transakcji/sklepu w tej granicy. | **Pominąć** plus i CTA zakupu. Nie sugerować, że kliknięcie może kupić walutę lub odblokować zawartość. |
| Zaznaczenia, kłódki, stock i liczniki | Są to stany danych, nie stałe grafiki. | Renderować z aktualnego `LoadoutUiState` i progresji; nie kopiować wartości `1,250`, `8 / 10`, `3 / 10` ani zaznaczeń ze screenshotu. |
| Podsumowanie i `START MATCH` | Liczniki draftu i zatwierdzenie istnieją w logice Loadout. | Odtworzyć jako sticky summary i natywny przycisk. Tekst oraz enabled/disabled stan nie mogą być częścią bitmapy. |

**Pominąć** fikcyjne jednostki `ROCK THROWER`, `BUCKET BRUTE`,
`WARBAND GRUNT`; fikcyjne typy amunicji `FIRE ROCK`, `SPIKE BALL`; fikcyjne
zaklęcia `FIRE STRIKE`, `WIND BLAST`, `EARTH SHIELD`; stałe ilości golda/stocku;
CTA plus/zakup; oraz dekoracyjne ikony podsumowania, jeśli nie mapują się na
realny stan gry. Zamiast nich używać identyfikatorów i stanów z katalogów
Urkaaaz. Kategorie `UNITS`, `AMMUNITION`, `SPELLS`, trzy sloty amunicji,
wybór do 3 jednostek/zaklęć, lock/selected oraz sticky summary odpowiadają
rzeczywistemu kontraktowi Loadout.

Mockup referencyjny ani screenshot nie trafia do `drawable` jako jedna
bitmapa. Generować należy wyłącznie brakujące ilustracje kart zaklęć; każda
jest osobnym plikiem 4:3 bez tekstu i ramki. Assety tła, jednostek, amunicji,
przycisków i paneli są ponownie używane. Każdy nowy obraz sprawdzić pod kątem
właściwego zaklęcia, czytelności w małej karcie, zgodności stylu i braku
elementów UI przed importem.

## Plan testów i kryteria akceptacji

### Testy

- Konstrukcja modeli: limity, unikalność, kolejność jednostek, trzy sloty,
  wymuszony Rock, puste sloty i maksymalna liczba czarów.
- Defaulty: stabilna kolejność oraz dokładne wyniki dla częściowej i pełnej
  dostępności każdego rodzaju.
- Walidacja: rozróżnienie niedostępności poziomu, braku odblokowania, braku
  stocku, duplikatów, limitów i błędnej struktury; nieudany wybór nie zmienia
  zapisu ani progresji.
- Repozytorium: zapis/odczyt trzech kategorii, kolejność, normalizacja
  nieznanych i niedostępnych zapisanych pozycji oraz typowany raport korekt.
- Zatwierdzenie: ponowna walidacja świeżej dostępności; brak nawigacji/zapisu
  po odmowie; poprawna konfiguracja kontraktowa po sukcesie.
- Kampanijne nagrody i UI: autoryzowane odblokowanie Haste/Heal, ich
  dostępność oraz preferencja dla nowego domyślnego loadoutu. Tutorial UI
  pozostaje poza zakresem.
- Integracja meczu: komendy dla pozycji wybranej i niewybranej; brak
  możliwości użycia pozycji niedostępnej mimo obecności w loadoucie; brak
  zużycia zasobów po odrzuceniu.
- Granice architektury: brak Androida w core, brak gameplayu/progresji w UI,
  brak mutacji runtime przez repository loadoutu i brak drugiego katalogu
  balansu.

### Kryteria akceptacji

- Jedna zwalidowana konfiguracja obejmuje jednostki, amunicję i czary.
- Reguły limitów, slotów, defaultów i filtracji odpowiadają zatwierdzonemu
  parytetowi legacy.
- Nieprawidłowe akcje interaktywne są jawnie odrzucane; korekty odczytu są
  widoczne jako typowany wynik.
- Zapis jest odrębny od progresji, stocku oraz runtime meczu; nie ma importu
  zapisów z legacy.
- `MatchScenario`/konfiguracja meczu przenosi wybrany loadout odrębnie od
  dostępności poziomu, a runtime go egzekwuje.
- Ekran używa istniejącego nawigatora i screen-owned stanu, bez logiki
  loadoutu w `MainActivity`.

## Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji |
|---|---|---|---|---|
| Reguły i persystencja w `MainActivity` | niska; miesza ekran, progresję i wybór | bez istotnej różnicy | niska; wymaga Androida | pozornie niski, rośnie przy dalszym rozwoju |
| Właściciel w `game-campaign` | miesza wybór gracza z definicją poziomu | bez istotnej różnicy | średnia | niska na początku, ale tworzy niewłaściwą zależność |
| Use case’y i port w `game-application`, adapter/UI w `android-app` | wysoka; jedna granica odpowiedzialności | bez istotnej różnicy | wysoka, core bez Androida | umiarkowany; wymaga jawnego kontraktu do meczu |

Rekomendowany jest trzeci wariant. Wykorzystuje istniejącą strukturę
`application.loadout`, pozostawia poziom i progresję ich właścicielom,
zachowuje testowalność reguł i nie wymusza nowego modułu. Główny koszt to
uzupełnienie kontraktu uruchomienia meczu i podłączenie go do obecnego
`MatchSession`.

## Otwarte prace i blokery

- Tutoriale poziomów 6/8 pozostają odroczone do przyszłego TutorialSystem.
- Zablokowany czar lub specjalny czar bez stocku otwiera wewnętrzny
  placeholder sklepu i wraca do tego samego draftu; rzeczywisty sklep/zakupy
  pozostają niezaimplementowane.
- Ilustracje kart zaklęć można podpiąć po dodaniu wygenerowanych plików;
  obecnie jawny wynik katalogu assetów to `Missing`.
- Wymagana jest kontrola wizualna na urządzeniu oraz dobór tła i paneli do
  biomu wybranego poziomu.
- Odblokowania i trwały `CampaignProgress` należą do CampaignSystemu,
  udokumentowanego w `campaign-system.md`.
- Zakupy, nagrody i rozliczenie stocku po meczu pozostają poza zakresem.
- Integracja poziomów 6/8 z odblokowaniami i zakupami musi przejść przez
  przyszłego właściciela progresji; LoadoutSystem nie implementuje jej po cichu.
- `CastSpell` oraz efekty czarów nie są obsługiwane przez SpellSystem.
- Nie implementować `CastSpell` ani zakupów w ramach podłączenia assetów;
  grafika zaklęcia nie jest deklaracją aktywnego efektu gameplayowego.

### Pomysł na przyszłość: czasowe bonusy amunicji

To pomysł, nie zaimplementowana ani zatwierdzona funkcja/balans. System
progresji mógłby przyznawać po określonym działaniu gracza czasowe uprawnienie
do wybranego typu amunicji, na przykład efektywnie nieograniczony zapas przez
15 minut.

- `AmmunitionType.unlimited` i katalog balansu pozostają niezmienną bazową
  definicją; bonus nie powinien modyfikować globalnej cechy typu.
- Progresja powinna być właścicielem przyznania i wygaśnięcia bonusu oraz
  dostarczać Loadout i konfiguracji meczu niemutowalny snapshot aktywnych
  uprawnień.
- Walidacja Loadoutu i runtime meczu muszą stosować tę samą efektywną regułę
  zapasu, tak aby czasowy unlimited nie zużywał ograniczonego stocku.
- Do ustalenia pozostają: moment rozpoczęcia czasu, zaufane źródło zegara,
  odnowienie/nakładanie bonusów, zachowanie zapisanego Loadoutu po wygaśnięciu
  oraz to, czy bonus wygasa natychmiast w trwającym meczu, czy dopiero po jego
  zakończeniu.

Najważniejsze zaimplementowane pliki to
`game-application/.../loadout/MatchLoadout.kt`,
`LoadoutContracts.kt`, `LoadoutUseCases.kt`,
`game-contracts/.../MatchScenario.kt`, `game-application/.../MatchSession.kt`
oraz `android-app/.../screens/loadout/LoadoutScreen.kt`. Odpowiadające testy
pokrywają core, mapowanie scenariusza i odrzucenia komend.

## Powiązana dokumentacja

- `docs/systems/loadout-system-implementation-prompt.md` — kopiowalny prompt
  do end-to-end dokończenia LoadoutSystemu; nowe grafiki są dodawane osobno.
- `docs/architecture/architecture.md` — granica `application.loadout` i kontrakt `MatchScenario`;
- `docs/systems/campaign-system.md` — kolejność Campaign → Loadout → Match;
- `docs/systems/player-progression-system.md` — profil, stock i granica
  zakupów;
- `docs/systems/spell-system.md` — odpowiedzialność LoadoutSystemu za wybór
  czarów; gameplay `CastSpell` pozostaje niedokończony.
- `docs/systems/loadout-screen-mockup.agp` — aktualizowany prompt referencyjny
  ekranu zgodny z istniejącą zawartością Urkaaaz.
- `docs/systems/loadout-item-detail-mockup.agp` — prompt podstrony szczegółów
  jednostki, amunicji lub zaklęcia otwieranej z przycisku `ⓘ`.
- `docs/systems/loadout-spell-card-art.agp` — kopiowalny prompt zestawu
  brakujących ilustracji kart zaklęć.

Zakres wykonanej implementacji i jej blokery są podsumowane w sekcji statusu;
pozostałe punkty nadal opisują wymagania docelowe.
