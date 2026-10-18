# CampaignSystem

## 1. Cel i zakres

`CampaignSystem` ma przenieść przebieg kampanii z legacy do warstw nowego
projektu: od wejścia z Dashboardu, przez wybór odblokowanego poziomu i
przekazanie go do loadoutu oraz meczu, po obsługę wyniku i aktualizację
odblokowań. System koordynuje przypadki użycia kampanii; nie jest ekranem,
nawigatorem ani silnikiem meczu.

Wzorcem funkcjonalnym jest przepływ legacy:

```text
Dashboard → kampania → wybór poziomu → loadout → mecz
          → wynik → następny poziom albo powrót
```

System obejmuje wyłącznie dostępność poziomów i postęp kampanii. Złoto i
nagrody, życia, sklep oraz loadout pozostają odrębnymi systemami. CampaignSystem
może przekazać im wybrany poziom i sterować kolejnością ekranów przez wynik
przypadku użycia, ale nie przejmuje ich danych ani reguł.

Aktualna nawigacja prowadzi z `CampaignScreen` do `LoadoutScreen`. Lokalny
profil progresji i zapis loadoutu pozwalają uruchomić mecz po poprawnej
walidacji oraz zapisie. CampaignScreen nadal udostępnia wszystkie poziomy
testowo; autoryzacja wyboru poziomu, `highestUnlockedLevel` i zapis postępu
kampanii pozostają niedokończone.

## 2. Poza zakresem

- Stan runtime meczu, jego ticki, pauza, restart i reguły zwycięstwa.
- Złoto, obliczanie nagród, życia, sklep, zakupy i odblokowania przedmiotów.
- Wybór i zapis loadoutu; loadout jest osobnym systemem.
- Rendering, assety, Androidowe widoki, `AppNavigator` i zarządzanie lifecycle
  Activity.
- Konto online, synchronizacja chmurowa i wspólny postęp między urządzeniami.
- Migracja postępu bezpośrednio z instalacji legacy; kompatybilność zapisów
  legacy wymaga osobnej decyzji.

CampaignSystem nie kopiuje implementacji legacy ani nie umieszcza logiki
progresji w `MainActivity`.

## 3. Właściciel i zależności

Właścicielem przypadków użycia CampaignSystem jest `game-application`. To
warstwa koordynująca żądania UI, definicje poziomów, wynik meczu i zapis
postępu. Moduł pozostaje niezależny od Androida.

Istniejący `game-campaign` pozostaje jedynym właścicielem statycznych definicji,
`CampaignLevelRegistry`, map oraz walidacji `CampaignLevelDefinition`.
CampaignSystem korzysta z tych kontraktów, zamiast tworzyć drugi katalog
poziomów lub kopiować konfigurację kampanii.

Docelowy kierunek zależności:

```text
android-app
  ├── game-application (CampaignSystem i port persystencji)
  │     ├── game-campaign (definicje, rejestr i walidacja poziomów)
  │     ├── game-contracts (MatchFinished, MatchOutcome, MatchScenario)
  │     └── istniejące moduły meczu
  └── adaptery Androida (nawigacja i lokalny zapis)
```

Dodanie zależności `game-application → game-campaign` jest proponowane dla
przyszłej implementacji. Nie może powstać zależność odwrotna ani import
Androida w modułach core. Port repozytorium postępu należy do warstwy
aplikacyjnej; jego adapter z lokalną persystencją należy do `android-app`.

## 4. Wejścia i wyjścia

### Wejścia

- Niemutowalny postęp gracza: najwyższy odblokowany poziom, początkowo `1`.
- Żądanie otwarcia kampanii z Dashboardu.
- Żądanie wyboru numeru poziomu.
- Kontekst wybranego poziomu przekazany przez aplikację przy starcie meczu.
- Wynik ukończonego meczu. Kanonicznym źródłem przejścia jest istniejący
  `MatchEvent.MatchFinished` z `MatchOutcome`; `MatchSnapshot.outcome` pozostaje
  read modelem do prezentacji i nie może drugi raz uruchamiać tej samej zmiany
  postępu.
- Odczyt i zapis postępu przez jawny port repozytorium.

Nie ma potrzeby dodawać eventu symulacji ani zmieniać `MatchOutcome`; oba
kontrakty istnieją już w `game-contracts`.

### Wyjścia

- Niemutowalny widok postępu potrzebny do pokazania poziomów odblokowanych i
  zablokowanych.
- Jawny rezultat wyboru: zaakceptowany poziom jako istniejący
  `CampaignLevelDefinition` (oraz wynikowy `MatchScenario`, gdy potrzebuje go
  przypadek użycia startu meczu) albo typowane odrzucenie.
- Jawny rezultat ukończenia, zawierający zaktualizowany postęp i informację,
  czy istnieje następny poziom.
- Dane potrzebne adapterowi Androida do przejścia do loadoutu, meczu, wyniku
  albo z powrotem. Typy `AppScreen`, `Context` i inne Androidowe klasy nie
  przekraczają granicy modułu.

Nie należy wprowadzać nowego eventu gameplayowego dla odblokowania. Zapis
postępu jest skutkiem przypadku użycia aplikacji, a nie mutacją stanu
symulacji.

## 5. Stan i persystencja

CampaignSystem jest bezstanowym koordynatorem przypadków użycia: nie przechowuje
własnego runtime state wyboru, aktywnego meczu ani postępu. Wybrany poziom
przechodzi jawnie w kontekście żądania i jest przekazywany do kolejnych ekranów.

Jedynym stanem trwałym tego zakresu jest monotoniczny `highestUnlockedLevel`,
przechowywany lokalnie przez adapter repozytorium. Zapis nie należy do
symulacji ani do `CampaignUiState`. Implementacja przejściowo może użyć
Androidowej lokalnej persystencji, ale logika domenowa i kontrakt repozytorium
nie mogą zależeć od `Context` ani `SharedPreferences`.

Wartość musi należeć do zakresu `1..CampaignLevelDefinition.MAX_LEVELS`.
Nieprawidłowy lub nieczytelny zapis jest jawnie raportowany; nie należy
po cichu udawać postępu domyślnego ani udostępniać wszystkich poziomów.

## 6. Cykl życia i przepływ

CampaignSystem nie ma własnych operacji utworzenia, pauzy, restartu ani
zakończenia meczu. Lifecycle meczu pozostaje w `MatchSession` i istniejącym
gatewayu.

1. Dashboard wysyła intencję otwarcia kampanii.
2. Przypadek użycia odczytuje trwały postęp i przygotowuje listę na podstawie
   `CampaignLevelRegistry`.
3. Campaign screen renderuje stan i wysyła żądanie wyboru poziomu.
4. System odrzuca poziom nieznany lub zablokowany; zaakceptowaną definicję
   przekazuje do niezależnego loadoutu.
5. Loadout przekazuje wybraną definicję/scenariusz do match screen; match
   korzysta z istniejącej konfiguracji scenariusza.
6. Po `MatchFinished` warstwa aplikacyjna przekazuje wynik wraz z poziomem
   kontekstu do przypadku użycia ukończenia kampanii.
7. Tylko `BLUE_WIN` zapisuje odblokowanie następnego poziomu. Pozostałe wyniki
   (`RED_WIN`, `DRAW`, `SURRENDER`) nie zmieniają odblokowań.
8. Wynik przypadku użycia kieruje adapter Androida do prezentacji wyniku i
   następnej dostępnej akcji. Na ostatnim poziomie nie ma akcji „następny
   poziom”.

Powrót do ekranu kampanii ponownie odczytuje postęp z repozytorium; aplikacja
nie polega na lokalnej kopii w ViewModelu. Obsługa retry pozostaje własnością
match screen/gateway i nie zmienia postępu.

## 7. Determinizm, czas i replay

CampaignSystem nie uczestniczy w tickach symulacji, nie losuje i nie używa
czasu ściennego. Wynik dla tych samych definicji, postępu, żądania i wyniku
meczu jest taki sam.

Aktualizacja postępu musi być monotoniczna i idempotentna: ponowne dostarczenie
tego samego `MatchFinished` nie może cofnąć postępu ani powodować dodatkowych
skutków ubocznych. Właściwość zapewnia zapis najwyższego odblokowanego poziomu;
ekonomia i nagrody nie są uruchamiane przez ten system. Replay symulacji nie
zapisuje ani nie odtwarza persystencji kampanii.

## 8. Integracja i kolejność odpowiedzialności

```text
Dashboard UI
  → CampaignSystem: odczyt postępu
  → CampaignScreen: wybór
  → CampaignSystem: walidacja poziomu i definicja
  → osobny LoadoutSystem / ekran loadoutu
  → MatchScreen → LocalMatchGateway → MatchSession / symulacja
  → MatchFinished
  → CampaignSystem: odblokowanie po zwycięstwie
  → ResultScreen / CampaignScreen przez AppNavigator
```

Podział odpowiedzialności:

| Odpowiedzialność | Właściciel |
|---|---|
| Definicje, rejestr, walidacja map i `MatchScenario` | `game-campaign` |
| Przypadki użycia wyboru, postępu i ukończenia kampanii | `game-application.CampaignSystem` |
| Trwały zapis postępu | port aplikacyjny i adapter persystencji w `android-app` |
| Kliknięcia, nawigacja i wyświetlenie odblokowań | campaign screen i `AppNavigator` |
| Wynik, stan runtime, restart i symulacja meczu | istniejące moduły meczu |
| Loadout, złoto/nagrody, życia i sklep | ich odrębne systemy |

`MainActivity` pozostaje cienkim entry pointem. Może składać zależności i
przekazywać callbacki ekranom, ale nie odczytuje ani nie aktualizuje reguł
postępu.

## 9. Błędy, odrzucenia i przypadki brzegowe

- Nieznany numer lub brak definicji poziomu: jawny błąd/odrzucenie, bez
  zastępowania poziomem demonstracyjnym.
- Zablokowany poziom: jawne odrzucenie wyboru i brak nawigacji do meczu.
- Poziom poza zakresem rejestru: jawne odrzucenie; walidator poziomów pozostaje
  właścicielem poprawności definicji map.
- Błąd odczytu albo zapisu: jawny błąd przypadku użycia; UI nie może wyświetlić
  sukcesu ani użyć niezweryfikowanego postępu.
- Porażka, remis lub poddanie: brak odblokowania.
- Ukończenie ostatniego poziomu: zapis pozostaje w granicy maksimum, bez
  przepełnienia ani następnego poziomu.
- Powtórne ukończenie już odblokowanego poziomu: operacja jest idempotentna i
  nie cofa postępu.
- Niezgodny/stary wynik meczu: odrzucenie przez walidację kontekstu poziomu i
  meczu w warstwie aplikacyjnej; nie zmienia innego postępu.

Nie stosować szerokich `catch`, cichych fallbacków ani stanów sukcesopodobnych.

## 10. Android UI i assety

Używane są istniejące `DashboardScreen`, `CampaignScreen`, `LoadoutScreen`,
`MatchScreen`, `ResultScreen`, `AppScreen` i `AppNavigator`. Docelowy przepływ
ma odtworzyć kolejność znaną z legacy, ale bez przenoszenia jego implementacji
widoków lub logiki do `MainActivity`.

Campaign screen konsumuje niemutowalny stan postępu, a kliknięcia przekazuje
jako intencje do ViewModelu/przypadku użycia. Ekran nie waliduje definicji,
nie zapisuje odblokowań i nie mutuje stanu domenowego. Wybór poziomu pozostaje
typowanym `CampaignLevelDefinition`, zgodnie z istniejącą integracją match
screen.

Nie są wymagane nowe assety ani debug overlay. Istniejący legacy visual
language może pozostać bez zmian.

## 11. Plan testów i kryteria akceptacji

### Testy przyszłej implementacji

- Walidacja zakresu odblokowanego poziomu i zachowania dla pustego/niepoprawnego
  zapisu.
- Lista poprawnie oznacza poziomy odblokowane i zablokowane.
- Wybór poprawnego odblokowanego poziomu zwraca definicję z istniejącego
  rejestru; poziom zablokowany/nieznany jest jawnie odrzucony.
- `BLUE_WIN` odblokowuje kolejny poziom, zapis jest monotoniczny, ponowienie
  operacji idempotentne, a maksimum nie przekracza 100.
- `RED_WIN`, `DRAW` i `SURRENDER` nie odblokowują poziomu.
- Błędy repozytorium odczytu i zapisu nie są ukrywane ani mapowane na sukces.
- Integracja przekazuje tę samą definicję/scenariusz przez loadout i start
  meczu; istniejąca definicja pozostaje deterministyczna.
- Test adaptera persystencji potwierdza odtworzenie postępu po ponownym
  utworzeniu adaptera oraz obsługę nieprawidłowych danych.
- Test granicy Androida sprawdza wejście z Dashboardu, wybór, odmowę wejścia
  do zablokowanego poziomu, obsługę wyniku i nawigację dalej/powrót.
- Testy architektury potwierdzają brak Androida w `game-application` i brak
  logiki progresji w `MainActivity`/rendererze.
- Brak drugiego katalogu poziomów oraz brak wpływu progresji na runtime state
  meczu, replay i symulację.

### Kryteria akceptacji

- Postęp kampanii jest lokalny i trwały, początkowo od poziomu 1.
- Dostępność poziomów wynika z repozytorium postępu, a nie tymczasowego
  odblokowania wszystkich wpisów.
- Tylko zwycięstwo gracza odblokowuje kolejny poziom; porażka, remis i
  poddanie nie modyfikują odblokowań.
- Pełny przepływ UI zachowuje granice osobnego loadoutu, meczu i prezentacji
  wyniku.
- Definicje pochodzą z `CampaignLevelRegistry`, zapis z adaptera Androida,
  a reguły/use case’y z `game-application`.
- Brak importów Androida w core, gameplayu w UI i mutacji stanu meczu przez
  CampaignSystem.
- Złoto, nagrody, życia, sklep i loadout pozostają poza zakresem jego
  odpowiedzialności.

## 12. Otwarte decyzje

- Czy istniejące dane postępu z osobnej instalacji legacy trzeba importować.
  Obecny plan zakłada nowy lokalny zapis bez migracji między aplikacjami.
- Czy ekran wyniku ma zastąpić obecny overlay match screen, czy być osobnym
  ekranem; plan wymaga obsługi przejść, nie wyboru szczegółowego UX.
- Czy w przyszłości postęp obejmie więcej niż najwyższy odblokowany poziom
  (np. ukończenie każdego poziomu osobno); obecny zakres odtwarza monotoniczne
  odblokowanie kolejnego poziomu.

## 13. Proponowana lista plików do przyszłej implementacji

Lista jest orientacyjna; nie oznacza, że pliki mają powstać w ramach tego
dokumentu.

| Obszar | Proponowane pliki |
|---|---|
| Zależność aplikacyjna | `game-application/build.gradle.kts` |
| Use case i porty | `game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignSystem.kt`, `CampaignProgressRepository.kt` oraz niemutowalne typy wyniku/postępu w tym samym pakiecie |
| Testy use case | `game-application/src/test/kotlin/com/urkaaaz/application/campaign/CampaignSystemTest.kt` |
| Adapter postępu | nowy adapter repozytorium w `android-app/src/main/java/com/urkaaaz/campaign/` |
| Integracja UI | istniejące `MainActivity.kt`, `AppScreen.kt`, campaign screen/ViewModel oraz punkty wejścia/wyjścia match i result |
| Testy Androida | test integracji nawigacji i adaptera persystencji w `android-app/src/test/` |

`game-campaign` nie wymaga nowego katalogu poziomów ani drugiego modelu
`CampaignLevelDefinition`; istniejące testy definicji i rejestru pozostają
testami regresji dla dostarczanych scenariuszy.

## 14. Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji |
|---|---|---|---|---|
| `game-application` koordynuje use case’y; `game-campaign` dostarcza definicje; Android implementuje port zapisu | Wyraźny podział przepływu, danych i platformy | Odczyt/zapis lokalny i lookup rejestru; pomijalny koszt w tej skali | Wysoka: reguły można testować z repozytorium fake bez Androida | Średni: dodać zależność aplikacja→kampania i adapter |
| Umieścić cały system w `game-campaign` | Łatwo znaleźć reguły obok definicji, ale miesza statyczny authoring z orkiestracją i persystencją aplikacyjną | Taki sam runtime koszt | Dobra dla reguł czystych; gorsza granica przypadków użycia i repozytorium | Średni; może wymagać przeniesienia koordynacji do Androida/aplikacji |
| Koordynować kampanię w `android-app`/`MainActivity` | Początkowo proste do podłączenia, szybko skupia za dużo odpowiedzialności w UI | Taki sam runtime koszt | Niska: testowanie wymaga adapterów Androida i UI | Niski na start, wysoki przy rozwoju i refaktorze |

Rekomendowany jest pierwszy wariant. Oddziela flow aplikacyjne od
niemutowalnych definicji poziomów i od persystencji platformowej, pozostawia
istniejący `game-campaign` single source of truth oraz umożliwia testowanie
reguł bez Androida.
