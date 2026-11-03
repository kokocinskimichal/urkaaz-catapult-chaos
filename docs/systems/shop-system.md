# ShopSystem — sklep i ekonomia lokalna

## Status

**Zaimplementowano lokalnie.** ShopScreen, zakupy za OrCoins, profilowy stock,
schema balansu 4 i settlement kampanijnego stocku są podłączone. Produkcyjna
gwarancja rozliczenia po wymuszonym zamknięciu procesu pozostaje poza MVP;
niezaimplementowane czary specjalne można kupić, ale nie można ich rzucić.

Samodzielny [prompt implementacyjny](shop-system-implementation-prompt.md)
opisuje etapowe wdrożenie tego zakresu.

## 1. Cel i zakres

ShopSystem zapewnia lokalny sklep, w którym gracz wydaje OrCoins na jeden stock
limitowanej amunicji albo czaru specjalnego. Zakup od razu aktualizuje saldo i
stock profilu jako jedną transakcję. Przygotowanie kampanijnego meczu przekazuje
stock gracza do runtime'u, a udane użycia są rozliczane po każdym wyniku meczu
oraz przed jawnym przerwaniem lub restartem.

Zakres MVP:

- odczyt cen i definicji z istniejącego `GameBalanceConfig`;
- katalog pozycji sklepu z ceną, stanem posiadania i dostępnością;
- natychmiastowy zakup pojedynczej sztuki za OrCoins, bez koszyka;
- atomowa zmiana salda i zapasu profilu;
- użycie stocku profilu jako limitu udanych użyć w kampanijnym meczu BLUE;
- zachowanie niewykorzystanego stocku i rozliczanie wykorzystanego;
- wejście do sklepu z Dashboardu i Loadoutu oraz powrót do właściwego
  kontekstu;
- aktualizacja formularza Balance w Urkaaaz Studio, aby edytował nowy kontrakt
  balansu.

### Poza zakresem

- efekty gameplayowe zaklęć, które nie są jeszcze zaimplementowane w
  SpellSystemie;
- zakupy za prawdziwe pieniądze, konto gracza, backend i synchronizacja
  chmurowa;
- koszyk, wiele sztuk w jednym zakupie, limity stocku lub autoekwipunek;
- przejęcie odpowiedzialności za wybór i zapis loadoutu, nagrody kampanii,
  milestone'y albo runtime stanu meczu;
- generowanie produkcyjnego tła sklepu lub spłaszczenie UI do jednego obrazu;
- gwarancja rozliczenia po awarii procesu lub utracie zasilania przed
  zakończeniem transakcji stocku; pełna odporność na taki przypadek wymagałaby
  osobnego, trwałego mechanizmu rezerwacji stocku.

## 2. Zatwierdzone decyzje produktowe

| Obszar | Decyzja |
|---|---|
| Waluta | OrCoins w prezentacji; trwałym polem profilu pozostaje obecne `goldBalance`. |
| Zakup | Natychmiastowa transakcja jednej sztuki; bez koszyka. |
| Stock limitowanej amunicji | Jeden zakup dodaje jedną sztukę. Profilowy stock ogranicza liczbę udanych użyć w meczu; niewykorzystane sztuki pozostają w profilu. |
| Stock czaru specjalnego | Jeden zakup dodaje jedną sztukę. Udane rzucenie zużywa jedną sztukę po integracji danego efektu z runtime'em. |
| Sklep z Loadoutu | Powrót do tego samego przedmiotu. Zakup nie autoekwipuje przedmiotu. |
| Sklep z Dashboardu | Powrót do Dashboardu. |
| Rozliczenie meczu | Stock rozlicza się po każdym `MatchFinished`, niezależnie od wyniku, oraz przed jawnym stopem lub restartem, które nie emitują `MatchFinished`. |
| Limit zapasu | Brak limitu gameplayowego. Osiągnięcie `Int.MAX_VALUE` jest jawnym błędem przepełnienia, nie sukcesem. |
| Czar specjalny nieobsługiwany przez runtime | Może być kupiony zgodnie z decyzją użytkownika, ale nie może być prezentowany jako możliwy do rzucenia, dopóki SpellSystem go nie obsłuży. |

## 3. Właściciele i kierunek zależności

Nie powstaje nowy moduł Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| Katalog ofert, ceny wynikowe, walidacja żądania i use case zakupu | ShopSystem w `game-application`, proponowany pakiet `application.shop` |
| Trwały profil, saldo i stock | istniejący `PlayerProgressionSystem` w `game-application` |
| Atomowe lokalne odczyty i zmiany profilu | port progresji w `game-application`; adapter AndroidX DataStore w `android-app.persistence` |
| Definicje cen amunicji i statystyki runtime | istniejący `game-domain` / `GameBalanceConfig` |
| Limity zużycia w trwającym meczu | istniejący runtime `game-simulation`; dla amunicji `ResourceWallet`, dla zaklęć reguły SpellSystemu |
| Lifecycle meczu i przekazanie zdarzeń do rozliczenia | `MatchSession` / aplikacyjny koordynator w `game-application` |
| Widok sklepu, nawigacja, komunikaty i lifecycle ekranu | screen-owned UI w `android-app` |
| Edycja kontraktu balansu | istniejąca zakładka Balance w `studio/` |

```text
Dashboard / Loadout
        │ akcja i nawigacja
        ▼
ShopScreen + ShopViewModel (android-app)
        │ żądanie zakupu / odczyt katalogu
        ▼
ShopUseCases (game-application)
   ├── GameBalanceConfig
   └── PlayerProgressionRepository
          │ atomowa mutacja świeżego profilu
          ▼
   DataStore adapter (android-app)

Player profile stock
        │ niemutowalna konfiguracja wejściowa
        ▼
MatchScenario → MatchSession → SimulationConfig → symulacja
        │ udane ProjectileFired / SpellCast
        ▼
Match stock tracker → stock settlement → PlayerProgressionRepository
```

`android-app` zależy od aplikacyjnych kontraktów; domena, symulacja i
`game-application` nie importują Androida. UI nie wylicza ceny ani nie zmienia
profilu samodzielnie. Trwały profil nie staje się częścią runtime state
symulacji; jego niemutowalny snapshot jest wyłącznie wejściem do pojedynczego
meczu.

## 4. Istniejące rozwiązania i nakładanie odpowiedzialności

### PlayerProgressionSystem

`LoadoutProgressionSnapshot` już przechowuje `goldBalance`,
`ammunitionStocks`, `specialSpellStocks` i odblokowane czary. `PlayerProgressionRepository`
odczytuje i inicjalizuje profil oraz udostępnia atomowe `updateProfile` na
świeżym stanie. `ShopUseCases` używa tej granicy do zakupów i settlementu,
zamiast tworzyć drugi profil, snapshot salda albo repozytorium stocku.
Dotychczasowe `PlayerProgressionUseCases.purchaseSpell` nadal zwraca `Blocked`;
zakupy są własnością ShopSystemu, a nie ścieżki wyboru loadoutu.

`LoadoutProgressionPort` i `LoadoutUseCases` już kontrolują, czy amunicja lub
czar mają stock oraz zachowują wybór gracza. Wybór pozostaje własnością
LoadoutSystemu. Sklep aktualizuje stan profilu; nie zapisuje loadoutu i nie
wybiera zakupionego przedmiotu.

### RewardSystem i DataStore

`RewardSettlementUseCase` atomowo zapisuje przy BLUE_WIN saldo, postęp
kampanii i receipt przez `RewardSettlementPort`. `DataStoreRewardSettlementAdapter`
i `DataStorePlayerProgressionRepository` korzystają z tych samych pól profilu
oraz współdzielonego `PlayerProgressionDataStoreCodec`.

Zakupy, rozliczenie stocku i nagrody muszą aktualizować najnowszy stan wewnątrz
atomowego `DataStore.updateData`; zabroniony jest wzorzec read-modify-write na
wcześniej pobranym snapshotcie. Współdzielony profil codec i wspólny wzorzec
transakcyjnej mutacji zapobiegają utracie równoległej zmiany złota lub stocku.
Każda operacja rozliczenia meczu ma być idempotentna po stabilnym
identyfikatorze generacji meczu. Retry nie może odjąć tego samego stocku drugi
raz.

### Runtime amunicji i SpellSystem

`ResourceWallet` zmniejsza zapas przy udanym strzale i `ProjectileFired`
emituje się po konsumpcji. Kampanijny mecz BLUE otrzymuje profilowy stock przez
`PlayerInventoryConfig` w `MatchScenario`, który zostaje zachowany także w
konfiguracji replayu. AI RED oraz jawny tryb demo zachowują swoje odrębne,
dotychczasowe zasady inicjalizacji — profil gracza nie zmienia ich zapasów.

`MatchEvent.SpellCast` oznacza udaną aktywację. Obecny MatchSession odrzuca
zaklęcia bez runtime implementacji przez `SPELL_NOT_IMPLEMENTED`; SpellSystem
opisuje pozostałe efekty jako niezaimplementowane. Użytkownik zaakceptował
sprzedaż czarów specjalnych mimo tej luki. Sklep nie implementuje efektów ani
nie omija odrzucenia runtime. UI ma jasno sygnalizować, że taki zakupiony czar
nie jest obecnie możliwy do rzucenia; jego stock nie może być zużyty przez
odrzuconą próbę, bo odrzucona komenda nie emituje `SpellCast`.

### Nawigacja i prezentacja

`ShopScreen` zastępuje placeholder screen-owned ekranem z ViewModelem.
`AppScreen.Shop` i `AppNavigator` obsługują wejście z Dashboardu oraz trasę
z Loadoutu z zachowaniem identyfikatora wybranego przedmiotu; powrót z Loadoutu
odtwarza jego szczegóły, ale nie autoekwipuje.

Brief wizualny znajduje się w `docs/systems/shop-screen-mockup.agp`, a
`assets_src/shop/screen-concepts/shop_background_reference.png` jest
warstwą scenografii z osadzonym sprzedawcą, nie kompletnym ekranem runtime.
Główna referencja kompozycji to
`assets_src/shop/screen-concepts/shop_ui_reference.png`: nagłówek i saldo w
górnym pasie, sprzedawca/lada po lewej, a po prawej obramowany panel
katalogu z kartami i oddzielnymi szczegółami wybranej pozycji. Referencja
pokazuje trzecią zakładkę `ARTIFACTS` i miesza typy kart; to są błędne treści,
nie część zaakceptowanego projektu. Implementacja zachowuje dwie zakładki
`SPELLS`/`AMMUNITION` i filtruje karty do wybranej kategorii. Zrzut służy
wyłącznie do dopasowania kompozycji i stylu. Androidowy ekran ma składać tło,
panel, kontrolki i tekst jako osobne warstwy oraz używać istniejących
katalogów assetów. Nie wolno dodawać sprzedawcy drugi raz ani przedstawiać
konceptu jako działającej mechaniki zakupowej.

## 5. Konfiguracja i katalog ofert

### Amunicja

Jedynym źródłem definicji i cen amunicji pozostaje `game-balance.json`.
Wdrożona zmiana balansu:

- `schemaVersion` wynosi 4;
- definicja amunicji zawiera nullable, nieujemne pole `shopCostGold`;
- `null` oznacza, że typ amunicji nie jest sprzedawany;
- cena jest ceną jednego stocku i nie może być kopiowana do Androida, Studio
  UI ani osobnego sklepowego katalogu;
- zachować zatwierdzone ceny legacy: Powder Barrel — 250, Fire Rain — 350,
  Plague Cauldron — 500.

Zmiana jest obsługiwana przez domenowy `AmmunitionDefinition`, dekoder
`GameBalanceRepository`, JSON oraz Studio Balance (model, walidator backendu,
formularz i testy). Edytor Studio zachowuje pełny formularz, bez edycji
surowego JSON-a.

### Czary i dostępność

Cena czaru pochodzi z istniejącego pola `goldCost` w konfiguracji czarów.
Sklep może oferować stock czarów oznaczonych `special`; HASTE i HEAL nie są
ofertą, bo ich dostępność wynika z milestone'ów kampanii. Zakup czaru
specjalnego dodaje jedną sztukę do `specialSpellStocks`.

Katalog sklepu jest projekcją istniejących definicji balansu i snapshotu
profilu, a nie nowym katalogiem statystyk. Musi podawać co najmniej stabilny
identyfikator, kategorię, cenę, posiadany stock i status zakupu. Nazwy,
ikony i opisy pochodzą z istniejącej projekcji katalogu/assetów albo jawnych
metadanych prezentacyjnych; nie mogą wprowadzać drugiego źródła cen lub reguł.
Pozycje niedostępne w runtime nadal nie mogą sugerować możliwości użycia.

## 6. Kontrakty, wejścia i wyjścia

Kontrakty aplikacyjne są niemutowalne i należą do `game-application`;
konfiguracja startowa współdzielona z symulacją należy do `game-contracts`.

### Wejścia

- `GameBalanceConfig` z cenami i stabilnymi ID pozycji;
- `LoadoutProgressionSnapshot` z bieżącym saldem i stockami;
- komenda zakupu z identyfikatorem jednej pozycji;
- dostępność poziomu oraz zatwierdzony `PlayerLoadoutConfig`;
- profilowy stock BLUE jako niemutowalny snapshot wejściowy dla generacji
  meczu;
- udane eventy `ProjectileFired` (`shooterTeam == BLUE`) i `SpellCast`
  przypisane do gracza BLUE;
- `MatchFinished` albo jawna operacja kończąca/porzucająca/restartująca
  generację meczu.

### Wyjścia

- read model sklepu: saldo, pozycje, cena jednostkowa, stock, status oferty i
  możliwość zakupu;
- typowany wynik zakupu albo jawna przyczyna odmowy;
- atomowo zmieniony profil: `goldBalance` oraz dokładnie jeden zwiększony
  stock;
- niemutowalna konfiguracja stocku wejściowego dla meczu;
- typowany wynik rozliczenia stocku z informacją o błędzie persystencji lub
  już rozliczonym meczu.

### Zasady zakupu

Zakup wykonuje jedną mutację na aktualnym profilu. W tej samej transakcji
sprawdza: znane ID, status „na sprzedaż”, poprawną cenę, wystarczające saldo,
brak przepełnienia salda oraz brak przepełnienia stocku. Dopiero po spełnieniu
wszystkich warunków odejmuje koszt i dodaje jedną sztukę. Każde odrzucenie
pozostawia profil bez zmian.

Oczekiwane odmowy obejmują co najmniej: nieznany przedmiot, przedmiot
niesprzedawany, błędną konfigurację, niewystarczające OrCoins, błędny profil,
przepełnienie salda/stocku oraz niedostępny storage. UI nie może
interpretować błędu storage jako zakupu zakończonego sukcesem.

## 7. Stock meczu i lifecycle

Profil jest właścicielem trwałego stocku, natomiast symulacja jest właścicielem
pozostałych użyć w aktywnym meczu. `MatchScenario` przenosi niemutowalną,
graczowi przypisaną konfigurację początkowych stocków przez
`PlayerInventoryConfig`; trwały profil nie jest częścią `PlayerLoadoutConfig`
ani UI.

Przy tworzeniu kampanijnego scenariusza aplikacja przekazuje dla BLUE liczbę
stocków zgodną z profilem. Dla limitowanej amunicji w wybranym loadoucie jest
to dokładny limit udanych strzałów, a nie dodatkowy zapas wynikający z
`inventory.startingCount`. Poziomowa dostępność i wybór loadoutu nadal
ograniczają, czym wolno strzelać; profil nie rozszerza dostępności poziomu.
RED/AI i demo nie używają stocku profilu BLUE.

`ResourceWallet` już zużywa amunicję przy udanym strzale. Tracker aplikacyjny
liczy zdarzenia zużycia dla właściwego meczu, drużyny i shop-stock ID, z
deduplikacją po `EventId`. Odrzucony strzał nie tworzy `ProjectileFired`.
Dla czarów stock zużywa wyłącznie udany `SpellCast` czaru sprzedawanego za
stock; odrzucona komenda nie zmienia profilu. W przyszłości SpellSystem musi
odmówić rzucenia stockowanego czaru, gdy runtime nie ma pozostałej sztuki,
zanim wyemituje event sukcesu.

Lifecycle:

1. `LoadoutUseCases` odczytuje świeży profil, zachowuje wybór i sprawdza
   posiadany stock.
2. Aplikacja tworzy konfigurację meczu ze scenariuszem, loadoutem i stockiem
   BLUE; scenariusz/replay przechowuje te same wejściowe ilości, aby
   odtworzenie nie zależało od późniejszych zakupów.
3. Symulacja konsumuje runtime stock tylko po udanym użyciu. ShopSystem nie
   ingeruje w tick, seed, fizykę ani RNG.
4. `MatchFinished` dowolnego wyniku zamyka tracker i inicjuje rozliczenie
   wykorzystanych ilości. `Surrender` rozlicza się przez emitowany
   `MatchFinished`.
5. Przed `MatchSession.stop()` lub `restart()` tracker musi zostać
   sfinalizowany i przekazany do rozliczenia, zanim bieżąca symulacja zostanie
   odrzucona. Restart otwiera nową generację z aktualnym profilem; nie może
   odtworzyć stocku z poprzedniej próby.
6. Retry po błędzie zapisu korzysta z tego samego identyfikatora settlementu
   i nie może ponownie odjąć stocku. UI zachowuje stan oczekującego
   rozliczenia i raportuje błąd jawnie.

Sesja meczu przekazuje eventy do `RewardTracker` i osobnego
`MatchStockUsageTracker` przez `CompositeMatchEventObserver`. Stock nie jest
dodany do reguł `RewardTracker` ani śledzony przez `consumeEvents`; obserwator
otrzymuje eventy w `MatchSession` w miejscu, w którym zostały wygenerowane.

Rozliczenie po meczu nie chroni przed utratą lokalnego trackera przy
przymusowym ubiciu procesu przed zapisem. To jawne ograniczenie MVP; gwarancja
po takim przerwaniu wymagałaby osobnego trwałego reservation/journal przed
startem lub po każdym użyciu.

## 8. Integracja UI i assetów

- `ShopScreen` dostaje screen-owned ViewModel/controller; `MainActivity`
  pozostaje cienkim entry pointem.
- `AppScreen.Shop` i `AppNavigator` obsługują wejście z Dashboardu i Loadoutu.
- Back z Dashboardu wraca do Dashboardu; powrót z Loadoutu zachowuje ten sam
  wybrany przedmiot i jego szczegóły.
- Zakup odświeża stan saldo/stock, wraca do wybranego przedmiotu i nigdy nie
  autoekwipuje.
- UI pokazuje wyłącznie `SPELLS` i `AMMUNITION`, live OrCoins, cenę i posiadany
  stock jako tekst/komponenty, a nie tekst wypalony w tle.
- Niedziałające obecnie czary specjalne mogą być kupione zgodnie z decyzją
  użytkownika, lecz ich status musi jawnie informować o braku obsługi runtime.
- Używać istniejącej estetyki ekranów i oddzielnych warstw; `shop_background_reference.png`
  jest scenografią (także runtime drawable), a nie kompletnym ekranem ani
  miejscem na wypalanie tekstu i kontrolek.
- Błędy ładowania, profilu, zakupu i settlementu mają mieć widoczny, typowany
  stan; nie stosować cichych fallbacków.

## 9. Determinizm, replay i czas

Zakupy, ceny, profil i persystencja są aplikacyjne, nie zależą od seeda ani
czasu symulacji. Wejściowy stock gracza dla konkretnej generacji jest jednak
częścią konfiguracji meczu i musi być zachowany w `MatchReplayConfiguration`.
Przy tych samych scenariuszu, stocku, seedzie i komendach symulacja powinna
zużyć identyczne zasoby i emitować identyczne eventy.

Odtwarzanie replayu nie może kupować ani rozliczać stocku profilu. Replay
odtwarza tylko ustalony runtime i pozostaje bez skutków persystencyjnych.
Rozliczenie rzeczywistej generacji meczu wykonuje aplikacja po jej
zakończeniu, poza tickiem.

## 10. Błędy, limity i przypadki brzegowe

- Nieznane i niedostępne ID: jawna odmowa, bez zmiany profilu.
- `shopCostGold == null`: pozycja nie jest sprzedawana.
- Ujemna cena lub stock w konfiguracji/profilu: błąd konfiguracji/profilu,
  bez normalizowania do zera.
- Za małe saldo: jawny wynik `InsufficientGold`.
- Koszt większy od salda: brak jakiejkolwiek częściowej transakcji.
- `Int` overflow przy saldzie albo stocku: jawne odrzucenie.
- Uszkodzony lub nieobsługiwany schemat profilu/balansu: jawny błąd, bez
  nadpisywania danymi startowymi.
- Stock meczu większy niż `Int` lub sprzeczny z profilem: odrzucenie
  konfiguracji przed startem.
- Duplikat eventu lub retry rozliczenia: idempotentne pominięcie.
- Storage failure przy zakupie: brak sukcesu i brak częściowej zmiany.
- Storage failure przy rozliczeniu: zachować oczekujący settlement do retry;
  nie restartować kolejnej generacji z nierozliczonym stanem.
- Niezaimplementowany czar: runtime nadal zwraca `SPELL_NOT_IMPLEMENTED`;
  zakup i selekcja nie mogą potajemnie zamienić tej odmowy w udany cast.

## 11. Testy i kryteria akceptacji

### Testy aplikacyjne

- read model używa cen z jednego `GameBalanceConfig`, poprawnie rozróżnia
  pozycję dostępną i `null` / niesprzedawaną;
- zakup amunicji i czaru atomowo zmniejsza saldo i zwiększa dokładnie jeden
  stock;
- brak złota, nieznane ID, pozycja niesprzedawana, błędny profil i każdy
  przypadek overflow pozostawiają profil niezmieniony;
- dwie równoległe transakcje zakupu i nagrody nie gubią zmiany saldo/stocku;
- świeży odczyt Loadoutu odzwierciedla zakup bez niejawnego wyboru/ekwipowania;
- settlement odejmuje wyłącznie użycia BLUE i zachowuje resztę stocku;
- BLUE_WIN, RED_WIN, DRAW, timeout i surrender rozliczają stock;
- jawny stop oraz restart rozliczają użycie przed odrzuceniem starej
  symulacji;
- retry i duplikat `EventId`/settlementu nie powodują podwójnego pobrania;
- profile read/write oraz settlement nie maskują błędów storage jako sukcesu.

### Testy kontraktów, symulacji i replay

- scenariusz przenosi niemutowalny stock BLUE, waliduje zakres i zachowuje go
  w konfiguracji replay;
- limitowana amunicja BLUE pozwala wyłącznie na liczbę udanych strzałów
  wynikającą z profilu; niedostępny stock blokuje strzał bez eventu sukcesu;
- zużycie nie przekracza stocku, a RED/AI i demo nie dziedziczą profilu
  BLUE;
- odrzucony cast nie zużywa stocku; przyszły udany cast stockowanego czaru
  emituje dokładnie jeden `SpellCast` i zużywa jedną sztukę;
- replay z tym samym wejściowym stockiem i seedem daje te same zdarzenia;
  replay nie mutuje persystencji.

### Testy persystencji, Studio i Androida

- DataStore aktualizuje saldo i stock w jednym `updateData`, zachowuje
  niepowiązane klucze i współdzieli poprawny codec z rozliczeniem nagród;
- ponowne rozliczenie tego samego meczu jest idempotentne;
- validator Studio akceptuje schema 6, waliduje nullable `shopCostGold` i
  odrzuca wartość ujemną/niecałkowitą; formularz odczytuje, edytuje i zapisuje
  pole bez utraty innych danych;
- nawigacja z Dashboardu i Loadoutu wraca do poprawnego źródła; Loadout
  zachowuje szczegóły przedmiotu, a zakup nie autoekwipuje;
- Shop renderuje dwa katalogi, saldo, status posiadania, cenę, błędy i
  niedostępność runtime czarów jako osobne live UI;
- test architektury potwierdza brak zależności Androida z core i brak logiki
  cen/zakupów w UI.

### Kryteria akceptacji

1. Profil jest jedynym trwałym właścicielem salda i stocku; zakupy/settlementy
   są atomowe, jawnie typowane i bezpieczne przy współbieżnych aktualizacjach.
2. Poziom i zatwierdzony loadout nadal ograniczają wybór; runtime BLUE
   respektuje dokładny stock profilu, a niewykorzystane ilości pozostają.
3. Wszystkie zakończenia i jawne przerwania rozliczają stock raz; błąd zapisu
   jest widoczny i możliwy do ponowienia.
4. Studio i walidator balansu obsługują schema 6 oraz `shopCostGold`.
5. UI zachowuje wskazaną nawigację, nie autoekwipuje zakupu i nie sugeruje,
   że niezaimplementowane zaklęcie można już rzucić.
6. Replay pozostaje deterministyczny i nie zmienia trwałego profilu.
7. Testy modułów i adapterów pokrywają odmowy, overflow, duplikaty, awarie
   zapisu, wynik meczu i restart/stop.

## 12. Zrealizowana kolejność implementacji

1. **Wdrożono:** rozszerzono domenowy model balansu i loader o nullable
   `shopCostGold`, schema JSON 4, fixtures i walidację.
2. **Wdrożono:** zaktualizowano serwer i formularz Balance w Studio, testy
   oraz dokumentację bieżącego schematu.
3. **Wdrożono:** rozszerzono aplikacyjną granicę PlayerProgression o
   walidowaną, atomową mutację świeżego snapshotu i współdzielony profile
   codec z adapterem RewardSystemu.
4. **Wdrożono:** dodano katalog ofert, typowane wyniki i use case'y ShopSystemu
   z cenami z istniejącego balansu i saldem/stockiem z progresji.
5. **Wdrożono:** dodano player-scoped immutable inventory do
   `MatchScenario`/replay i przekazano je do `SimulationConfig`; RED/AI i demo
   zachowują własne zapasy.
6. **Wdrożono:** dodano aplikacyjny tracker oraz idempotentne rozliczenie
   użyć przy `MatchFinished`, `stop()` i `restart()` przed odrzuceniem
   symulacji.
7. **Wdrożono:** zastąpiono Shop placeholder ekranem i nawigacją z
   Dashboardu/Loadoutu; ekran odświeża profil, raportuje błędy i oznacza
   nieobsługiwane czary.
8. **Wdrożono:** dodano testy kontraktów, use case'ów, DataStore, symulacji,
   replay, nawigacji, UI i Studio oraz zaktualizowano powiązaną dokumentację.

## 13. Zaimplementowane pliki

### `game-domain` / balance

- `game-domain/src/main/resources/balance/game-balance.json`
- `game-domain/src/main/kotlin/com/urkaaaz/domain/AmmunitionDefinitions.kt`
- `game-domain/src/main/kotlin/com/urkaaaz/domain/BossBalance.kt` — bieżąca
  stała wersji balansu
- powiązane testy katalogu amunicji i konfiguracji balansu

### `game-application` / Shop i progresja

- `game-application/src/main/kotlin/com/urkaaaz/application/shop/ShopContracts.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/shop/ShopUseCases.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/shop/MatchStockUsageTracker.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/shop/MatchStockSettlementUseCase.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/reward/CompositeMatchEventObserver.kt`
- rozszerzony `application/progression/PlayerProgressionRepository.kt`
- `application/loadout/LoadoutContracts.kt` i `LoadoutUseCases.kt`
- `application/MatchSession.kt`, `LocalMatchGateway.kt`,
  konfiguracja replay i testy ich lifecycle

### Kontrakty i symulacja

- `game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchScenario.kt`
  (`PlayerInventoryConfig`)
- `game-simulation/src/main/kotlin/com/urkaaaz/simulation/SimulationConfig.kt`
- `game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt`
- testy stocku amunicji, kontraktu inventory i replay

### `android-app`

- `screens/shop/ShopScreen.kt` i `ShopViewModel.kt`
- `screens/loadout/LoadoutScreen.kt`, `LoadoutViewModel.kt` oraz
  współdzielony `LoadoutItemResources.kt`
- `navigation/AppScreen.kt`, `AppNavigator.kt` oraz wiring w `MainActivity.kt`
- `persistence/DataStorePlayerProgressionRepository.kt` i
  `PlayerProgressionDataStoreCodec.kt`
- `persistence/DataStoreRewardSettlementAdapter.kt`
- `drawable-nodpi/shop_background_reference.png` jako oddzielna warstwa tła
- testy DataStore, Shop/ViewModel, Loadout, match settlement i nawigacji

### Studio i dokumentacja

- `studio/server/src/services/balanceCatalog.ts` i testy walidatora/API
- `studio/web/src/tabs/balance/model.ts`,
  `studio/web/src/tabs/balance/BalanceForm.tsx` i testy
- `docs/architecture/studio-architecture.md`, `studio/README.md`,
  `docs/project/current-state.md`, `docs/project/knowledge.md`,
  `docs/project/CHANGELOG.md`
- `docs/systems/player-progression-system.md`, `reward-system.md`,
  `loadout-system.md`, `spell-system.md`, `docs/architecture/architecture.md`
  i `docs/architecture/project-map.md`

## 14. Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji |
|---|---|---|---|---|
| Reguły zakupu i odejmowania golda w Android UI | Niska: UI staje się właścicielem ekonomii i walidacji | Wystarczająca dla lokalnego UI | Niska: reguły wymagają Androida i ekranu | Niski pozornie, wysoki przez duplikację i regresje |
| Nowy, niezależny magazyn/profil ShopSystemu | Średnia: osobny punkt zapisu | Wystarczająca | Średnia | Wysoki: konflikt z PlayerProgression i RewardSystem o te same dane |
| Use case sklepu na istniejącym profilu, wspólny atomowy adapter i stock w konfiguracji meczu | Wysoka: zakup, profil, runtime i UI mają osobnych właścicieli | Wystarczająca; transakcje lokalne i mały katalog | Wysoka: use case'y, adaptery i runtime testowalne bez Androida | Umiarkowany: wymaga rozszerzenia portu profilu, balansu, scenariusza i integracji settlementu |

Zaakceptowany i wdrożony jest trzeci wariant. Zachowuje jedno źródło prawdy dla
salda i stocku, wykorzystuje istniejące porty i eventy oraz nie przenosi
gameplayu do UI. Najważniejszym kosztem było poprawne spięcie nowego stocku z
profilem, runtime, replayem i już istniejącym rozliczeniem nagród.

## 15. Otwarte ograniczenia

- Kupione czary specjalne pozostają nieużywalne, dopóki SpellSystem nie
  zaimplementuje ich efektów i ograniczenia stocku w runtime. Ich przyszłe
  efekty wymagają osobnego zakresu i akceptacji.
- Settlement wykonywany dopiero przy końcu meczu może utracić niezapisane
  użycia po przymusowym zamknięciu procesu; odporność na awarię nie należy do
  zatwierdzonego MVP.
- `ResultScreen` pozostaje placeholderem; błędy stock settlementu pokazuje
  istniejący match result popup/controller, bez tworzenia drugiego właściciela
  wyniku meczu.
- Jeżeli użytkownik zażąda sprzedaży nowych typów, nowych cen lub limitu
  stocku, jest to zmiana balansu/zakresu i wymaga osobnej decyzji; obecny
  dokument nie zakłada takich ofert.
