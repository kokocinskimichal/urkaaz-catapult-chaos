# LoadoutSystem

## Status i cel

LoadoutSystem jest częściowo zaimplementowany. `game-application` ma kanoniczny
`MatchLoadout`, deterministyczne defaulty, normalizację zapisu, typowane akcje,
walidację i porty repozytorium/progresji. `MatchScenario` przenosi dostępność
poziomu oddzielnie od wyboru gracza, a `MatchSession` odrzuca niewybrane
jednostki i typy amunicji. Produkcyjne adaptery DataStore profilu i loadoutu
oraz bezpieczny przepływ zatwierdzenia działają. Ekran odczytuje i pokazuje
zwalidowany draft, a start pozostaje zablokowany przy błędzie lub do czasu
zapisania konfiguracji. Interaktywna edycja kategorii i tutoriale nadal są
niedokończone.

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
  stan przycisku startu oraz tutoriale loadoutu dla poziomów 6 i 8;
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

`architecture.md` już przewiduje `application.loadout` z
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
amunicję dla gracza. SpellSystem nadal nie obsługuje `CastSpell`.

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
| Draft, prezentacja, input i tutoriale ekranu | screen-owned ViewModel/controller w `android-app` |
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
seedem; zakupy i milestone'y kampanii pozostają jawnie poza zakresem.

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
- Zachować legacy milestone’y: przed loadoutem poziomu 6 Haste jest
  odblokowywany i dołączany do wyboru, a przed poziomem 8 odblokowywany i
  dołączany jest Heal oraz do wyboru dodawany jest Demolisher, jeśli poziom go
  udostępnia. Warunek tutoriala i jego dwa wskazania muszą pozostać
  screen-owned; reguła odblokowania należy do progresji, a nie do ViewModelu.
- Tutorial poziomu 6 wskazuje wybór jednostki i Haste, a poziomu 8 — wybór
  Demolishera i Heal. Koszty i efekty czarów należą do katalogu/spell systemu.

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

Obecny `LoadoutScreen` asynchronicznie odczytuje snapshot profilu i zapis
poziomu, pokazuje dostępność, draft, korekty oraz saldo/stock i uruchamia mecz
wyłącznie po udanym zatwierdzeniu. Lifecycle ekranu anuluje operacje w toku.
Ekran nie ma jeszcze interaktywnych edytorów kategorii, ikon, opisów ani
tutoriali poziomów 6/8. Widok korzysta z istniejących assetów aplikacji;
brak grafiki nie jest zastępowany losowym placeholderem.

Panel ma wysyłać akcje do screen-owned ViewModel/controller. Katalogi,
koszty, dostępność, filtrowanie i walidacja nie mogą być duplikowane w UI.
Informacje o jednostkach, amunicji i czarach są prezentacją istniejących
konfiguracji i lokalizacji.

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
- Milestone’y i UI: poziomy 6/8, wstępne dodanie właściwych wyborów,
  tutoriale i stan startu przy pustej jednostce/czarze.
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

- Interaktywne edytory jednostek, amunicji i czarów nie są jeszcze podłączone
  do widoku; ekran pokazuje i zatwierdza deterministyczny draft.
- Milestone'y Haste/Heal poziomów 6/8 wymagają autoryzacji przez
  `CampaignSystem`; nie są przyznawane na podstawie poziomu UI.
- CampaignScreen nadal udostępnia poziomy testowo; nie istnieje trwały zapis
  `highestUnlockedLevel`.
- Zakupy, nagrody i rozliczenie stocku po meczu pozostają poza zakresem.
- Integracja poziomów 6/8 z odblokowaniami i zakupami musi przejść przez
  przyszłego właściciela progresji; LoadoutSystem nie implementuje jej po cichu.
- `CastSpell` oraz efekty czarów nie są obsługiwane przez SpellSystem.
- Aplikacja nie ma adaptera tutoriali loadoutu. Assety ikon czarów nie
  występują w obecnym `android-app`; ekran obecnie używa tekstowych nazw.

Najważniejsze zaimplementowane pliki to
`game-application/.../loadout/MatchLoadout.kt`,
`LoadoutContracts.kt`, `LoadoutUseCases.kt`,
`game-contracts/.../MatchScenario.kt`, `game-application/.../MatchSession.kt`
oraz `android-app/.../screens/loadout/LoadoutScreen.kt`. Odpowiadające testy
pokrywają core, mapowanie scenariusza i odrzucenia komend.

## Powiązana dokumentacja

- `architecture.md` — granica `application.loadout` i kontrakt `MatchScenario`;
- `docs/systems/campaign-system.md` — kolejność Campaign → Loadout → Match;
- `docs/systems/spell-system.md` — odpowiedzialność LoadoutSystemu za wybór
  czarów; gameplay `CastSpell` pozostaje niedokończony.

Zakres wykonanej implementacji i jej blokery są podsumowane w sekcji statusu;
pozostałe punkty nadal opisują wymagania docelowe.
