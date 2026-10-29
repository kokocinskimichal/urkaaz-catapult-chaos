# PlayerProgressionSystem

## Status

**Częściowo zaimplementowany.** `game-application` udostępnia asynchroniczny
port i use case'y profilu oparte na istniejącym `LoadoutProgressionSnapshot`.
`android-app` zapisuje wersjonowany profil i zatwierdzony loadout w AndroidX
DataStore, a ekran rozpoczyna mecz dopiero po odczycie, walidacji i udanym
zapisie. CampaignSystem autoryzuje progresję i milestone'y; przy wygranej
RewardSettlementUseCase zapisuje je atomowo wraz ze złotem i receipt.
Read model loadoutu łączy odblokowania czarów z profilem. Zakupy i rozliczanie stocku
pozostają poza wdrożonym zakresem. RewardSystem przyznaje złoto za wygrane
poziomy przez atomowy adapter, a profil pozostaje właścicielem salda.

## 1. Cel i zakres

`PlayerProgressionSystem` jest aplikacyjnym właścicielem trwałego profilu
gracza, który LoadoutSystem odczytuje przy budowaniu i zatwierdzaniu loadoutu.
W pierwszym zakresie system zapewnia:

- inicjalizację nowego lokalnego profilu z wartościami potwierdzonymi w
  legacy;
- odczyt odblokowanych czarów, zapasów ograniczonej amunicji, zapasów czarów
  specjalnych i salda wymaganego przez istniejący kontrakt loadoutu;
- zachowanie zmian schematu profilu w nowej aplikacji przez wersjonowany,
  lokalny zapis;
- jawny wynik odczytu/odmowy, bez udawania dostępności przy błędzie;
- kontraktowy punkt integracji dla LoadoutSystemu.

System umożliwia zweryfikowanie wyboru oraz rozpoczęcie meczu z zatwierdzonym
loadoutem. Nie stanowi pełnej ekonomii ani pełnego zapisu kampanii.

## 2. Poza zakresem

- Odblokowywanie poziomów i zapis `highestUnlockedLevel` — pozostają
  odpowiedzialnością `CampaignSystem`.
- Zakupy, sklep oraz transakcje specjalnych przedmiotów. Istniejący port
  zakupowy zwraca jawny wynik `Blocked`; nie może symulować sukcesu. Naliczanie
  złota za wygraną jest wyłączną odpowiedzialnością RewardSystem.
- Rozliczanie zużytej amunicji lub czarów po meczu ani synchronizacja zapasów
  z `MatchFinished`. Runtime stocku pozostaje poza tym zakresem.
- Stan runtime meczu, replay, aktywacja czarów i ich efekty.
- Synchronizacja chmurowa, konto, wiele profili, import zapisów/preferencji z
  legacy oraz współdzielenie postępu między urządzeniami.
- Pełny redesign lub migracja legacy UI sklepu.

`CampaignLevelDefinition.startingAmmunition` jest początkową ilością dla
runtime'u jednego meczu. Nie jest trwałym zapasem profilu i nie może być
używane jako jego wartość domyślna.

## 3. Właściciel i granice modułów

| Odpowiedzialność | Właściciel |
|---|---|
| Profil, snapshot progresji, use case'y i port repozytorium profilu | `game-application` |
| Walidacja wyboru, normalizacja i zapis `MatchLoadout` | istniejący LoadoutSystem w `game-application` |
| Definicje poziomu i dostępność jednostek, amunicji oraz czarów na poziomie | `game-campaign` / `CampaignLevelDefinition` |
| Odblokowanie poziomów i postęp po wyniku | `CampaignSystem` |
| Lokalny, trwały adapter profilu i loadoutu | `android-app` |
| Draft i prezentacja statusu odczytu/zapisu | screen-owned ViewModel/controller w `android-app` |
| Zużycie zasobów meczu | runtime meczu |

Nie powstaje nowy moduł Gradle. Moduły core nie importują Androida. `MainActivity`
może składać zależności i przekazywać je do ekranów, ale nie przechowuje profilu
ani nie podejmuje decyzji o odblokowaniach.

## 4. Istniejące kontrakty i ryzyko duplikacji

`game-application/application/loadout/LoadoutContracts.kt` zawiera już:

- `LoadoutProgressionSnapshot` z `unlockedSpellTypes`,
  `ammunitionStocks`, `specialSpellStocks`, `goldBalance` oraz pochodnym
  `defaultSpellTypes`;
- `LoadoutProgressionPort` do odczytu snapshotu oraz żądania zakupu czaru;
- `LoadoutRepository` do odczytu i zapisu wyboru dla poziomu;
- typowane wyniki dla blokady progresji i błędów zapisu.

`LoadoutUseCases` konsumuje te porty, ponownie weryfikuje świeży snapshot przy
zatwierdzaniu i zwraca `PlayerLoadoutConfig`. Należy rozszerzyć lub
asynchronicznie dostosować te kontrakty, a nie tworzyć drugi snapshot
progresji, drugi katalog odblokowań ani osobne API startu meczu.

`CampaignSystem` w `game-application` jest właścicielem trwałego postępu,
autoryzacji poziomu i milestone'ów opisanych w
`docs/systems/campaign-system.md`. `CampaignLevelDefinition` nadal definiuje
dostępność zawartości na poziomie. Profil nie przechowuje kopii
kampanijnych odblokowań.

## 5. Wejścia i wyjścia

### Wejścia

- poziom kampanii i jego `LoadoutAvailability`;
- lokalny, wersjonowany profil albo informacja, że profil jeszcze nie istnieje;
- żądanie inicjalizacji nowego profilu z zatwierdzonym zestawem startowym;
- odczyt lub zapis `StoredLoadoutSelection` przez osobny `LoadoutRepository`;
- akcje loadoutu, które wykorzystują istniejący `LoadoutUseCases`.

Profil i dostępność poziomu muszą pozostać rozdzielone. Poziom mówi, co wolno
wybrać na tym poziomie; profil mówi, co gracz odblokował lub posiada. Dostępne
jednostki i typy amunicji nadal pochodzą z definicji poziomu, nie z nowego
katalogu progresji.

### Wyjścia

- niemutowalny `LoadoutProgressionSnapshot` lub jego typowane rozszerzenie;
- jawny status profilu: gotowy, inicjalizowany, uszkodzony albo niedostępny;
- typowany wynik odczytu/zapisu oraz ewentualna przyczyna blokady;
- zwalidowany `MatchLoadout` i `PlayerLoadoutConfig` po udanym zatwierdzeniu
  przez LoadoutSystem.

Błąd profilu lub zapisu loadoutu nie może wygenerować wyniku sugerującego, że
mecz można uruchomić.

## 6. Stan, zapis i inicjalizacja profilu

Trwałym stanem jest jeden lokalny profil aplikacji. Jego minimalna zawartość
wynika z `LoadoutProgressionSnapshot`: odblokowane zwykłe czary, stock
ograniczonej amunicji, stock czarów specjalnych i saldo. Schemat przechowuje
stabilne identyfikatory tekstowe, a adapter mapuje je na istniejące katalogi;
nie kopiuje balansu ani definicji.

`defaultSpellTypes` jest pochodnym polem read modelu, nie zapisanym profilem.
`CampaignAwareLoadoutProgressionPort` dodaje do snapshotu odblokowania
CampaignProgress i preferencje nowych domyślnych loadoutów. Nie zapisuje
kampanijnych nagród do `profile.unlocked_spells`.

Pierwszy profil jest inicjalizowany atomowo wyłącznie wtedy, gdy brak danych
profilu. Potwierdzony seed legacy zawiera `FORTRESS_SHIELD`, saldo `0` oraz
pusty stock ograniczonej amunicji i czarów specjalnych (czyli zero sztuk).
Nieograniczone typy amunicji pozostają semantyką istniejącego katalogu i nie
są zapisywane jako stock. Wartości wynikają z legacy
`SpellProgress`, `GoldProgress` i `AmmunitionProgress`; `startingAmmunition`
poziomu nie jest używane. Ponowny odczyt nie nadpisuje profilu.

Profil i loadout mają oddzielne namespace'y oraz schematy wersji `1` w jednym
DataStore. To pierwszy schemat nowej aplikacji, więc nie ma jeszcze starszej
wersji do migracji; nieznana wersja i częściowe/uszkodzone dane są raportowane
jako jawny failure, bez zastępowania ich seedem. Nie wykonuje się migracji
kluczy ani zapisów z osobnej instalacji legacy.

Lokalny zapis używa AndroidX Preferences DataStore i atomowych aktualizacji.
`DataStorePlayerProgressionRepository` inicjalizuje/odczytuje profil, a
`DataStoreLoadoutRepository` odczytuje i zapisuje wyłącznie zatwierdzony wybór.
`DataStoreCampaignProgressRepository` zapisuje niezależny, wersjonowany
CampaignProgress. Adaptery współdzielą backend, zachowując osobne klucze i
wersje. Typy DataStore, Flow i Context nie przekraczają granicy `android-app`.

Przycisk `RESET` w prawym górnym rogu Dashboardu resetuje lokalne dane gracza
po potwierdzeniu. `PlayerProgressResetRepository` atomowo czyści wspólny
Preferences DataStore, obejmując kampanię, profil (w tym złoto i odblokowania),
stocki, zapisy loadoutu oraz paragony rozliczeń. Błąd zapisu jest zwracany jako
`STORAGE_UNAVAILABLE`; UI nie pokazuje wtedy sukcesu. Reset nie obejmuje
ustawień Androida ani zewnętrznych danych.

Zatwierdzony loadout jest osobną kategorią danych i pozostaje własnością
`LoadoutRepository`. Adaptery mogą korzystać ze wspólnego mechanizmu DataStore,
ale nie łączą semantycznie wyboru wyposażenia z odblokowaniami, stockiem ani
postępem kampanii.

## 7. Asynchroniczność i lifecycle

DataStore jest asynchroniczny. Porty persystencji i aplikacyjne use case'y
wykonujące odczyt lub zapis muszą zostać dostosowane do `suspend` albo
równoważnego, jawnego kontraktu asynchronicznego. Nie wolno blokować wątku UI
przez `runBlocking` ani ukrywać opóźnienia za synchronicznym cache'em
ViewModelu.

Screen-owned controller/ViewModel zarządza stanem `Loading`, gotowym snapshotem
oraz jawnym błędem. Zadanie odczytu i zatwierdzenia jest powiązane z lifecycle
ekranu i anulowane przy jego zniszczeniu. Powrót do ekranu ponownie odczytuje
trwały stan; ViewModel nie staje się drugim właścicielem profilu.

Profil nie ma cyklu życia meczu: pauza, restart i zakończenie meczu nie resetują
go. Odtworzenie aplikacji odczytuje ten sam zapis. Stan runtime amunicji nadal
jest inicjalizowany przez aktualny scenariusz i nie jest automatycznie
rozliczany z trwałym stockiem w tym zakresie.

## 8. Przepływ danych i integracja

```text
CampaignLevelDefinition
  → LoadoutAvailability
  → LoadoutUseCases.open [async]
      ├── PlayerProgressionSystem
      │     → PlayerProgressionRepository
      │     → Android DataStore adapter
      └── LoadoutRepository
            → Android DataStore adapter
  → Loadout screen: gotowy draft / widoczna korekta / jawny błąd
  → LoadoutUseCases.commit [świeży snapshot + walidacja + async save]
  → PlayerLoadoutConfig
  → MatchScreen → LocalMatchGateway → MatchSession
```

1. Ekran otrzymuje typowy poziom z `AppScreen.Loadout`.
2. Asynchroniczny use case odczytuje snapshot profilu i zapis loadoutu.
3. LoadoutSystem filtruje i normalizuje zapis według poziomu oraz snapshotu;
   raport korekt pozostaje widoczny dla warstwy prezentacji.
4. Interaktywne edycje nadal korzystają z jednego `LoadoutUseCases`; reguły
   nie są kopiowane do Android UI.
5. Zatwierdzenie odczytuje świeży snapshot, waliduje wybór, zapisuje go i
   dopiero po udanym zapisie emituje `PlayerLoadoutConfig`.
6. Istniejąca nawigacja przekazuje kontrakt do MatchScreen i MatchSession.
7. Błąd odczytu, niepoprawny profil, brak wymaganej pozycji albo błąd zapisu
   pozostawia start zablokowany.

`CampaignScreen` odczytuje trwały `CampaignProgress` i umożliwia wybór tylko
odblokowanych poziomów. `CampaignAwareLoadoutProgressionPort` łączy snapshot
profilu z nagrodami kampanii; błąd kampanii jawnie blokuje loadout.

## 9. Milestone'y i integracja z czarami

Haste jest przyznawany po zwycięstwie na poziomie 5 i dostępny od poziomu 6;
Heal — po zwycięstwie na poziomie 7 i dostępny od poziomu 8.
`CampaignRewardRegistry` jest wspólnym katalogiem nagród i milestone'ów;
`CampaignMilestoneRegistry` nadal zawiera definicje progów czarów.
RewardSettlementUseCase zapisuje milestone-ID atomowo z ukończeniem,
odblokowaniem i złotem, a
`CampaignAwareLoadoutProgressionPort` łączy wynik z profilem wyłącznie w
read modelu. `defaultSpellTypes` jest pochodną wyboru nowego domyślnego
loadoutu, nie polem trwałego profilu. Zapisany wybór dla poziomu pozostaje
zachowany. Zakupy nadal należą do systemu sklepu/progresji.

Przekazanie czarów w `PlayerLoadoutConfig` nie oznacza obsługi `CastSpell`.
SpellSystem nie jest implementowany przez ten system.

## 10. Błędy, odrzucenia i konflikty

- Brak profilu: utwórz go tylko z potwierdzonym zestawem startowym.
- Nieznany identyfikator w istniejącym profilu: zgłoś typowaną korektę albo
  błąd schematu zgodny z regułami normalizacji; nie odblokowuj nieznanego
  elementu.
- Ujemny stock, ujemne saldo, nieobsługiwana wersja lub niepoprawny payload:
  zgłoś jawny błąd i zablokuj start do czasu bezpiecznej naprawy.
- Błąd I/O: obsłuż wyłącznie oczekiwane błędy storage jako jawny failure;
  nie używaj szerokiego `catch` ani pustego/default snapshotu.
- Poziom nie udostępnia typu: odrzuca go LoadoutSystem mimo jego obecności w
  profilu.
- Profil nie udostępnia typu: odrzuca go LoadoutSystem mimo dostępności na
  poziomie.
- Zmiana profilu między otwarciem a zatwierdzeniem: zatwierdzenie ponownie
  odczytuje snapshot i odrzuca już niedozwolony wybór.
- Błąd zapisu loadoutu: bez `PlayerLoadoutConfig`, bez nawigacji do meczu.
- Zakup czaru w tej fazie: jawny `Blocked`, bez zmiany złota/odblokowań.

## 11. Determinizm i transakcje

System nie działa w ticku, nie używa seeda ani czasu symulacji. Dla tego samego
wersjonowanego profilu i poziomu jego snapshot oraz wynik walidacji są
deterministyczne. DataStore odpowiada za atomową aktualizację jednego zapisu;
nie jest źródłem kolejności symulacji ani częścią replayu.

PlayerProgressionSystem nie obserwuje bezpośrednio `MatchFinished`.
`CampaignSystem` przekazuje kanoniczny wynik meczu i tracker rewardów do
`RewardSettlementUseCase`, który atomowo aktualizuje postęp kampanii, receipt
i istniejące saldo profilu. RewardSystem nie rozlicza stocku, a zapasów z
loadoutu nie wolno deklarować jako rozliczonych po meczu.

## 12. Plan testów i kryteria akceptacji

### Testy

- Konstrukcja snapshotu: nieujemne saldo/stock, stabilne identyfikatory i
  poprawna obsługa pustych zapasów.
- Inicjalizacja nowego profilu z dokładnie potwierdzonym zestawem legacy;
  powtórny odczyt nie seeduje drugi raz ani nie resetuje progresji.
- Odczyt istniejącego profilu oraz migracja każdej obsługiwanej wersji
  zachowują znane wartości.
- Nieobsługiwana wersja, uszkodzone dane i błąd DataStore zwracają jawny
  status; nigdy nie zamieniają się w pełny odblokowany profil.
- `LoadoutProgressionPort` dostarcza snapshot z profilem i kampanijnymi
  odblokowaniami; niedostępna kampania blokuje odczyt, a zakupy pozostają
  jawnie zablokowane, jeśli StoreSystem nie należy do zakresu. RewardSystem
  aktualizuje istniejące saldo wyłącznie po zatwierdzonym rozliczeniu.
- Asynchroniczny LoadoutSystem otwiera ekran, filtruje zapis, ponownie
  waliduje świeży stan, zapisuje zatwierdzony wybór i zwraca konfigurację
  tylko po sukcesie.
- LoadoutRepository odtwarza zapis po ponownym utworzeniu adaptera oraz
  zachowuje jednostki, trzy sloty amunicji i czary bez zapisu draftu.
- Błąd odczytu/zapisu, brak stocku, brak odblokowania i niezgodność poziomu
  pozostawiają start zablokowany.
- Adapter testuje zapis/odczyt, aktualizację atomową, migrację schematu oraz
  zachowanie przy braku/corruption danych.
- Lifecycle UI anuluje zadania przy usunięciu ekranu i nie wysyła
  `PlayerLoadoutConfig` po anulowaniu lub błędzie.
- Test granicy architektonicznej: DataStore/Android nie trafia do core;
  UI nie przyznaje odblokowań i nie mutuje profilu bez use case'u.
- CampaignSystem testuje poziom początkowy, walidację wyboru, BLUE_WIN,
  wyniki bez postępu, idempotencję, wspólne rozliczenie nagród i read model
  czarów dla LoadoutSystemu.

### Kryteria akceptacji

- Nowy profil używa wyłącznie wartości startowych potwierdzonych z legacy.
- Nie istnieje fake produkcyjny ani sukcesopodobny fallback progresji lub
  persystencji.
- Odczyt i zapis DataStore są asynchroniczne; wątek UI nie jest blokowany.
- Loadout zapisuje wyłącznie zatwierdzone wybory; progresja i zapis wyboru
  pozostają rozdzielone.
- Start kampanijnego meczu następuje tylko po prawidłowym snapshotcie,
  zatwierdzeniu i udanym zapisie.
- Wszystkie moduły core pozostają niezależne od Androida; nie powstaje nowy
  moduł ani drugi katalog balansu/progresji.
- Dokumentacja jasno oznacza, że zakupy, rozliczenie stocku i gameplay
  czarów pozostają poza tym zakresem; odblokowania kampanijne są własnością
  CampaignSystemu.

## 13. Pliki aktualnej implementacji

Najważniejsze pliki:

| Obszar | Kandydaci |
|---|---|
| Port profilu i resetu | `game-application/.../progression/PlayerProgressionRepository.kt`, `PlayerProgressionUseCases.kt`, `PlayerProgressResetRepository.kt` |
| Porty i use case'y loadoutu | `game-application/.../loadout/LoadoutContracts.kt`, `LoadoutUseCases.kt` |
| Adaptery DataStore | `android-app/.../persistence/DataStorePlayerProgressionRepository.kt`, `DataStorePlayerProgressResetRepository.kt`, `DataStoreLoadoutRepository.kt` |
| Wspólny DataStore | `android-app/.../persistence/LocalPreferencesDataStore.kt` |
| Kompozycja zależności i ekran | `android-app/.../MainActivity.kt`, `screens/dashboard/DashboardScreen.kt`, `screens/loadout/LoadoutScreen.kt`, `LoadoutViewModel.kt` |
| Testy | core progression/loadout/reset oraz `android-app/.../persistence/DataStoreRepositoriesTest.kt` i `screens/loadout/LoadoutViewModelTest.kt` |

## 14. Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji |
|---|---|---|---|---|
| Dodać wyłącznie adaptery do LoadoutScreen, bez aplikacyjnego właściciela profilu | Niska: logika i storage stają się platformowym szczegółem ekranu | Wystarczająca lokalnie | Niska; reguły zależą od Androida | Niski teraz, wysoki przy zakupach/odblokowaniach |
| Umieścić progresję w `game-campaign` | Miesza definicje poziomów z profilem gracza | Bez istotnej różnicy | Średnia | Średni i tworzy niewłaściwą odpowiedzialność |
| `game-application` jako właściciel use case'ów i portów, DataStore adapter w Androidzie | Wysoka; profil, loadout, poziom i platforma mają jawne granice | Wystarczająca dla małego lokalnego profilu | Wysoka; reguły i błędy testowane bez Androida | Umiarkowany: asynchroniczne porty, adapter i wiring |

Rekomendowany jest trzeci wariant. Rozszerza istniejące porty zamiast
zastępować je drugim systemem, zachowuje separację Campaign/Loadout/Profile i
pozwala testować politykę bez platformy. DataStore wymaga asynchronicznego
kontraktu, co jest niewielkim kosztem wobec unikania blokowania UI i fałszywie
synchronicznego cache'u.

## 15. Otwarte blokery

- Campaign milestone'y są wdrożone i nie są przyznawane przez sam numer
  otwartego poziomu. Tutoriale poziomów 6/8 pozostają poza zakresem i należą
  do przyszłego TutorialSystem.
- Ekran pokazuje i zatwierdza deterministyczny draft, ale interaktywne
  kontrolki edycji loadoutu pozostają do wdrożenia.
- Należy ustalić późniejszy właścicielski przepływ zakupów/nagród i rozliczeń
  stocku po meczu przed przedstawieniem tych funkcji jako produkcyjnych.
- `CastSpell` i efekty czarów pozostają niezależnym blokerem gameplayu.
