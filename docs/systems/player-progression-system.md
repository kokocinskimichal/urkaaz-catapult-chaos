# PlayerProgressionSystem

## Status

**Częściowo zaimplementowany.** `game-application` udostępnia asynchroniczny
port i use case'y profilu oparte na istniejącym `LoadoutProgressionSnapshot`.
`android-app` zapisuje wersjonowany profil i zatwierdzony loadout w AndroidX
DataStore, a ekran rozpoczyna mecz dopiero po odczycie, walidacji i udanym
zapisie. Zakupy, autoryzowane milestone'y kampanii, rozliczanie stocku i
CampaignSystem pozostają poza wdrożonym zakresem.

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
- Zakupy, nagrody, naliczanie złota, sklep oraz transakcje specjalnych
  przedmiotów. Do czasu wdrożenia właściciela zakupów istniejący port zakupowy
  zwraca jawny wynik `Blocked`; nie może symulować sukcesu.
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
  `ammunitionStocks`, `specialSpellStocks` i `goldBalance`;
- `LoadoutProgressionPort` do odczytu snapshotu oraz żądania zakupu czaru;
- `LoadoutRepository` do odczytu i zapisu wyboru dla poziomu;
- typowane wyniki dla blokady progresji i błędów zapisu.

`LoadoutUseCases` konsumuje te porty, ponownie weryfikuje świeży snapshot przy
zatwierdzaniu i zwraca `PlayerLoadoutConfig`. Należy rozszerzyć lub
asynchronicznie dostosować te kontrakty, a nie tworzyć drugi snapshot
progresji, drugi katalog odblokowań ani osobne API startu meczu.

`CampaignSystem` ma już dokument architektury i własność `highestUnlockedLevel`.
`CampaignLevelDefinition` definiuje dostępność zawartości danego poziomu.
System progresji profilu nie zastępuje żadnej z tych odpowiedzialności.

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
Oba adaptery współdzielą backend, zachowując osobne klucze i wersje. Typy
DataStore, Flow i Context nie przekraczają granicy `android-app`.

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

`CampaignScreen` obecnie udostępnia wszystkie poziomy testowo. Zastąpienie
tego zachowania trwałym odblokowaniem należy do osobnego zakresu
`CampaignSystem`; `PlayerProgressionSystem` nie powinien udawać, że poziom
jest odblokowany.

## 9. Milestone'y i integracja z czarami

Zachować udokumentowane reguły legacy: Haste ma być dostępny przed loadoutem
poziomu 6, a Heal przed loadoutem poziomu 8. Ich dostępność na poziomie nadal
pochodzi z `CampaignLevelDefinition`; ich odblokowanie musi wynikać z
potwierdzonego zestawu startowego lub jawnej reguły progresji, nie z ViewModelu.
Dokładny moment aktualizacji profilu nie może omijać walidacji postępu
kampanii. Jeśli do bezpiecznej aktywacji milestone'u wymagane jest
`CampaignSystem`, integracja musi użyć jego jawnego wyniku/portu albo pozostać
blokadą — nie należy odblokowywać czaru jedynie dlatego, że UI otworzyło
dowolny poziom.

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

Ta faza nie pobiera `MatchFinished` do aktualizacji stocku, nagród lub
poziomów. Nie wolno deklarować zapasów z loadoutu jako rozliczonych po meczu.

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
- `LoadoutProgressionPort` dostarcza prawidłowy snapshot dla danego poziomu;
  zakupy są jawnie zablokowane, jeśli StoreSystem nie należy do zakresu.
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
- Dokumentacja jasno oznacza, że zakupy, CampaignSystem, rozliczenie stocku
  i gameplay czarów pozostają poza tym zakresem.

## 13. Pliki aktualnej implementacji

Najważniejsze pliki:

| Obszar | Kandydaci |
|---|---|
| Port profilu i use case'y | `game-application/.../progression/PlayerProgressionRepository.kt`, `PlayerProgressionUseCases.kt` |
| Porty i use case'y loadoutu | `game-application/.../loadout/LoadoutContracts.kt`, `LoadoutUseCases.kt` |
| Adaptery DataStore | `android-app/.../persistence/DataStorePlayerProgressionRepository.kt`, `DataStoreLoadoutRepository.kt` |
| Wspólny DataStore | `android-app/.../persistence/LocalPreferencesDataStore.kt` |
| Kompozycja zależności i ekran | `android-app/.../MainActivity.kt`, `screens/loadout/LoadoutScreen.kt`, `LoadoutViewModel.kt` |
| Testy | core progression/loadout oraz `android-app/.../persistence/DataStoreRepositoriesTest.kt` i `screens/loadout/LoadoutViewModelTest.kt` |

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

- `CampaignSystem` nadal musi zapewnić produkcyjne ograniczenie wyboru poziomów
  i jawne źródło postępu. Obecny selector kampanii nadal jest konfiguracją
  testową i udostępnia wszystkie poziomy.
- Milestone'y Haste/Heal poziomów 6/8 nie są przyznawane na podstawie numeru
  poziomu; wymagają autoryzacji CampaignSystem.
- Ekran pokazuje i zatwierdza deterministyczny draft, ale interaktywne
  kontrolki edycji loadoutu oraz tutoriale poziomów 6/8 pozostają do wdrożenia.
- Należy ustalić późniejszy właścicielski przepływ zakupów/nagród i rozliczeń
  stocku po meczu przed przedstawieniem tych funkcji jako produkcyjnych.
- `CastSpell` i efekty czarów pozostają niezależnym blokerem gameplayu.
