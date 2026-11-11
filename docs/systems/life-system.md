# LifeSystem — architektura i plan implementacji

## Status

**Zaimplementowano lokalnie.** Trwały profil kampanii ma kanoniczny LifeState,
regenerację offline, idempotentne rozliczenie kampanijnych porażek i wspólne
API grantów skończonych oraz czasowych unlimited. Campaign oraz Dashboard
odczytują aplikacyjny `LifeSnapshot`; nie dodano integracji reklamowej ani
event feedu.

Prompt wykonania pozostaje w
[`life-system-implementation-prompt.md`](life-system-implementation-prompt.md).
Lokalny wall-clock nie zabezpiecza przed ręcznym przyspieszeniem czasu do
przodu, a wymuszone zamknięcie procesu przed zapisaniem wyniku może utracić
zdarzenie — trwały outbox nie należy do zakresu.

## Cel i zakres

LifeSystem zarządza trwałą liczbą żyć profilu kampanii, ich regeneracją offline
oraz grantami przyznawanymi przez aplikacyjne źródła nagród. Życia ograniczają
rozpoczynanie kampanijnych prób; nie są zasobem symulacji pojedynczego meczu.

Pierwszy zakres obejmuje:

- początkowe `5` żyć i maksymalny limit `5`;
- zużycie jednego życia po kampanijnym wyniku innym niż `BLUE_WIN`, w tym
  `RED_WIN`, `DRAW` i `SURRENDER`;
- brak zużycia w Demo i Test Mode oraz przy meczu bez kanonicznego
  `MatchEvent.MatchFinished`;
- regenerację jednego życia co 15 minut czasu ściennego, również przy
  zamkniętej aplikacji;
- naliczenie wszystkich pełnych interwałów po powrocie, do osiągnięcia limitu;
- czasowe granty nieskończonych żyć od przyszłych eventów;
- jawne, idempotentne granty skończonych żyć od przyszłego Ad Reward i innych
  źródeł;
- blokadę startu kampanii przy zerowej liczbie żyć, z wyjątkiem aktywnego
  grantu nieskończonych żyć;
- lokalną persystencję, reset, migrację danych, błędy i prezentację stanu.

System nie implementuje dostawcy reklam, harmonogramu eventów, konta ani
synchronizacji chmurowej. Te źródła mogą w przyszłości wywołać wspólny
aplikacyjny kontrakt grantów, ale same nie są częścią LifeSystemu.

## Zatwierdzone decyzje gameplayowe

| Temat | Decyzja |
|---|---|
| Początkowa liczba żyć | `5` dla nowego profilu |
| Maksymalna liczba żyć | `5` |
| Koszt wyniku kampanii | Jedno życie za każdy wynik inny niż `BLUE_WIN`, także remis i poddanie |
| Demo/Test Mode | Nie zużywają żyć |
| Start przy zerze | Przeglądanie mapy i wybór poziomu są dozwolone; rozpoczęcie poziomu jest zablokowane |
| Regeneracja | Jedno życie co `15 minut` czasu ściennego |
| Aplikacja zamknięta | Czas regeneracji upływa; po powrocie naliczane są wszystkie pełne interwały, do limitu |
| Timer przy limicie | Przy `5/5` nie nalicza się postęp regeneracji; po utracie życia z pełnego limitu rusza pełne 15 minut |
| Kolejne porażki | Gdy liczba żyć jest poniżej limitu, kolejna porażka nie resetuje bieżącego timera |
| Skończony grant | Źródło podaje ilość; przyznawana jest tylko liczba mieszcząca się w limicie |
| Grant przy pełnym limicie | Zwraca jawny wynik `LIMIT_REACHED`; nie zwiększa liczby żyć ponad 5 |
| Grant podczas unlimited | Skończony grant nadal zwiększa zapisany licznik, do limitu 5 |
| Grant unlimited | Źródło podaje czas trwania; pozwala startować przy zapisanym `0`, nie zużywa ani nie regeneruje zwykłych żyć |
| Wielokrotne granty unlimited | Czas kolejnego grantu dodaje się do końca aktywnego okresu |
| Regeneracja podczas unlimited | Zatrzymana; zachowany pozostały czas wznawia się po wygaśnięciu grantu |
| Unlimited offline | Czas grantu upływa również przy zamkniętej aplikacji |
| RESET | Przywraca `5/5`, usuwa timer, granty czasowe i receipts LifeSystemu |
| Źródła grantów | Stabilny source/event ID zapewnia idempotencję; przyszły Ad Reward przekazuje grant `1` życia |
| Developer Progress Preset | Jawne ustawienie żyć ustawia również spójny timer/granty: poniżej limitu startuje pełne 15 minut, przy limicie timer i granty są czyszczone |
| Pełny Dashboard | Pole regeneracji pokazuje `N/A` |
| Dashboard z regeneracją | Pokazuje odliczanie do następnego życia |
| Dashboard z unlimited | W polu regeneracji pokazuje `∞` i czas do końca grantu |
| Błąd zapisu zużycia | Jawny błąd i retry z popupu wyniku; nie można opuścić wyniku do czasu rozliczenia |

### Reguła zegara

LifeSystem używa lokalnego zegara ściennego, aby obsłużyć regenerację i granty
podczas zamknięcia procesu. Persistowany high-water mark efektywnego czasu
zapobiega cofnięciu liczby żyć lub już naliczonego postępu, jeśli zegar
urządzenia zostanie cofnięty. Po cofnięciu zegara naliczanie czeka, aż czas
ścienny ponownie osiągnie zapisany high-water mark, a następnie rusza dalej.

Ponieważ system działa lokalnie i offline, nie może wiarygodnie wykryć
przyspieszenia zegara do przodu. Użytkownik zaakceptował to ograniczenie.
Docelowym zegarem aplikacyjnym jest jawny port czasu wall-clock; nie używać
zegara symulacji, ticków meczu ani samego monotonicznego `elapsedRealtime`,
który nie przeżywa restartu urządzenia.

## Właściciel i granice modułów

Nie powstaje nowy moduł Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| LifeSystem: reguły żyć, timerów, grantów i blokady dostępu | `game-application.application.progression` |
| Wspólny limit, wartość początkowa i interwał regeneracji | `game-domain` `GameBalanceConfig`, edytowany przez istniejący Studio Balance |
| Trwały profil i transakcyjne mutacje | wspólny profile codec/DataStore przez `PlayerProgressionRepository` i `LifeStateRepository` |
| Adapter lokalnego zegara i DataStore | `android-app` |
| Odblokowanie/ukończenie poziomu | `CampaignSystem` |
| Start gate łączący progres kampanii i życie | `CampaignLevelStartUseCase` |
| Wyświetlenie licznika i CTA | `DashboardScreen` oraz `CampaignScreen` |
| Rozliczenie ukończenia i złota wygranej | istniejący `CampaignSystem` / `RewardSystem` |
| Runtime state meczu | istniejący `MatchSession` / symulacja, bez żyć |

Moduły core nie importują Androida. Android UI wyłącznie odczytuje
niemutowalny `LifeSnapshot` oraz wywołuje use case’y; nie zmienia profilu,
nie oblicza regeneracji i nie przyznaje grantów.

## Istniejące rozwiązania, nakładanie i ryzyko duplikacji

### Już istnieje

- `DeveloperProgressPreset` i `DeveloperProgressSummary` zachowują pole
  `lives` dla debugowego narzędzia. Adapter zapisuje je przez `LifeStateFactory`
  do kanonicznego LifeState; `profile.lives` jest wyłącznie legacy kluczem
  odczytywanym przez migrację.
- `PlayerProgressionRepository` i `PlayerProgressionDataStoreCodec` zarządzają
  inventory i złotem. Codec schema 2 współdzieli profil z
  `LifeStateRepository`, który mutuje życie w tym samym atomowym DataStore.
- `CampaignSystem.selectLevel` sprawdza poprawność, odblokowanie i ukończenie
  poziomu; nie sprawdza liczby żyć.
- `CampaignSystem.completeLevel` zwraca `NoProgress` dla wyniku innego niż
  `BLUE_WIN`. `RewardSystem` wprost nie przyznaje nagród za porażkę, remis ani
  poddanie.
- Wspólny lokalny Preferences DataStore jest już używany przez profil,
  CampaignProgress, reward receipts i reset. RESET atomowo czyści postęp,
  zachowując hash zastosowanego Developer Progress Preset.
- Dashboard korzysta z osobnego `DashboardProgressSummaryUseCase` i pokazuje
  countdown, `N/A` przy pełnym stanie lub `∞` z czasem aktywnego grantu.

### Ryzyko duplikacji

Utworzenie osobnego licznika żyć w UI, symulacji albo niezależnym DataStore
spowodowałoby rozjazd z `profile.lives`, resetem oraz podglądem Dashboardu.
Dodanie żyć do `RewardSystem` pomieszałoby politykę ekonomii wygranej z
regeneracją i grantami, mimo że życie zużywa się właśnie przy wynikach bez
rewardu. Samo zablokowanie przycisku w `CampaignScreen` dałoby się ominąć
przez inną ścieżkę startu.

### Rekomendacja

Rozszerzyć istniejącą granicę profilu o jeden kanoniczny `LifeState` i dodać
LifeSystem jako podsystem use case’ów w `game-application.application.progression`.
Nie przechowywać drugiej kopii `lives` w `LoadoutProgressionSnapshot` tylko
po to, by spełnić wymagania Loadoutu; dostarczać oddzielny read model
`LifeSnapshot` z tego samego profilu i transakcji DataStore. Rozszerzyć
istniejący codec, port persystencji, debugowy preset i reset tak, aby
wszystkie operacje odczytywały/mutowały tę samą kanoniczną wartość.

`CampaignSystem` pozostaje właścicielem progress/unlock selection.
`CampaignLevelStartUseCase` łączy jego walidację z LifeSystemem i zwraca
typowane odrzucenie `NO_LIVES`; nie kopiuje reguł ani stanu żyć. Kampania może
przeglądać i wybierać mapę przy zerze, lecz świeża walidacja startu w warstwie
aplikacyjnej odrzuca rozpoczęcie, chyba że jest aktywny unlimited grant.

Wyniki meczu przechodzą przez istniejący application match-finish flow.
LifeSystem konsumuje wyłącznie kanoniczny `MatchFinished` prawdziwej kampanii;
`MatchViewModel` już odfiltrowuje Demo i Test Mode. Life settlement dla
wyniku niebędącego wygraną jest osobną, atomową mutacją profilu z receipt po
`MatchId`; nie dodaje reguł żyć do `RewardSystem` ani do symulacji.
`CampaignMatchCompletionUseCase` routuje wynik do Campaign/Reward albo
LifeSystem. `CampaignCompletionPresenter` pozostaje adapterem prezentacyjnym,
a retry uruchamia ten sam idempotentny application use case.

Alternatywa — osobny Gradle module / drugi DataStore — pogarsza spójność,
reset i testowalność. Umieszczenie żyć w `game-simulation` błędnie związałoby
między-meczowy postęp i wall-clock z runtime state meczu. Użycie samego
Developer Progress Preset jako systemu żyć pozostawiłoby reguły nieobecne w
produkcji. Rozszerzenie istniejącego właściciela profilu unika tych duplikatów.

## Kontrakty, wejścia i wyjścia

### Read model

`LifeSnapshot` jest niemutowalnym, aplikacyjnym odczytem:

- liczba zwykłych żyć i `maximumLives`;
- `canStartCampaign` wyliczone z liczby żyć lub aktywnego unlimited;
- pozostały czas do następnego życia, albo brak timera przy `5/5`;
- czy unlimited jest aktywny i jego pozostały czas;
- jawny status odczytu/błąd profilu.

Dashboard i CampaignScreen nie odtwarzają tego wyliczenia lokalnie. Odczyt
przelicza upływ wall-clock atomowo i zwraca świeży snapshot.

### Życie po wyniku kampanii

Wejściem jest kanoniczny `MatchEvent.MatchFinished` z `MatchId`, wynikiem i
informacją aplikacyjną, że mecz jest prawdziwym poziomem kampanii. System
odrzuca lub pomija Demo/Test oraz brak zakończonego meczu.

- `BLUE_WIN`: żadnego zużycia życia; istniejący reward/campaign settlement
  zachowuje dotychczasowe zachowanie.
- `RED_WIN`, `DRAW`, `SURRENDER`: zużyj jedno zwykłe życie, chyba że aktywny
  jest unlimited grant.
- Każdy `MatchId` może zużyć życie najwyżej raz. Powtórne dostarczenie wyniku
  zwraca wcześniej zapisany rezultat.
- Wynik anulowany lub przerwany bez `MatchFinished` nie zużywa życia.

### Granty

Wspólny aplikacyjny grant use case przyjmuje stabilny `sourceId`, źródło oraz
jeden typowany wariant:

- **Finite(amount)** — `amount > 0`; dodaje do licznika do limitu. Przy
  `4/5` i grancie `3` przyznaje `1`, zwracając zastosowaną oraz odrzuconą
  nadwyżkę. Przy `5/5` nic nie dodaje i zwraca `LIMIT_REACHED`.
- **Unlimited(duration)** — `duration > 0`; zapisuje/rozszerza czasowy
  unlimited grant. Przy kolejnym grancie podczas aktywnego okresu czas
  wydłuża się od istniejącego końca o czas nowego grantu.

Powtórne wywołanie z tym samym source ID zwraca zapisany rezultat bez
ponownego grantowania; użycie tego samego ID z innym payloadem jest jawnym
konfliktem. Grant Ad Reward w przyszłości przekazuje `Finite(1)`. Eventy
przekazują jawnie przyznawaną ilość lub czas unlimited. LifeSystem nie
implementuje ani nie potwierdza wiarygodności reklamy czy eventu — to należy
do ich przyszłych właścicieli.

### Wyniki operacji i błędy

Use case’y zwracają jawne wyniki: `Applied`, `AlreadyApplied`,
`PartiallyApplied`, `LIMIT_REACHED`, `NO_LIVES`, `INVALID_GRANT`,
`IDEMPOTENCY_CONFLICT`, błędy walidacji danych profilu oraz `STORAGE_UNAVAILABLE`.
Nie ma cichego fallbacku ani sukcesopodobnego profilu przy uszkodzonym zapisie.

Jeśli zapis zużycia życia po zakończeniu meczu zawiedzie, wynik meczu
pozostaje widoczny, pokazuje błąd i umożliwia retry. Nie wolno wrócić do
kampanii ani uruchomić następnego poziomu przed rozliczeniem. Retry z tym
samym `MatchId` jest bezpieczny. Wymuszone zamknięcie procesu przed udanym
zapisem pozostaje ograniczeniem lokalnego MVP; trwały outbox zdarzeń nie jest
w tym zakresie.

## Balans i persystencja

Limity i interwał są konfiguracją balansu, nie stałymi Android UI. Proponowana
gałąź konfiguracji to `runtime.campaign.lives` w istniejącym
`game-balance.json`, z polami:

- `initialLives = 5`;
- `maximumLives = 5`;
- `regenerationIntervalMilliseconds = 900000`.

Loader `GameBalanceRepository`, typowany model w `game-domain` i istniejący
Studio Balance muszą walidować oraz edytować ten sam profil. Zmiana
`game-balance.json` podnosi `balanceRevision`; konfiguracja pojedynczej
generacji i replay nie przejmują czasu życia z symulacji. Czas duration
unlimited grantu pochodzi z walidowanego requestu jego źródła, nie z
`MatchScenario`.

Profil zachowuje jeden licznik bazowy, timer regeneracji, high-water mark
czasu, bieżący czas wygaśnięcia unlimited oraz receipts meczów i grantów.
Wszystkie modyfikacje przechodzą przez atomowe `DataStore.updateData` w
istniejącym `urkaaz_local_progress`; adapter Android implementuje port
aplikacyjny. W UI i w innym repozytorium nie powstaje niezależna kopia.

### Inicjalizacja, migracja i reset

- Nowy profil: `5/5`, bez timera, bez aktywnego unlimited i bez receipts.
- Istniejące `profile.lives > 5`: migracja ogranicza do `5`.
- Istniejące `profile.lives < 5` bez timera: rozpoczyna pełne 15 minut od
  chwili migracji; nie zgaduje przeszłego postępu.
- Brak wartości lives przy istniejącym profilu: dotychczasowy default `5`;
  zapisany profil zostaje uzupełniony w kontrolowanej migracji schematu.
- Debug preset, jeśli jawnie ustawia lives, ustawia także spójny stan:
  poniżej limitu pełny timer, przy `5/5` bez timera i bez unlimited grants.
- RESET przywraca `5/5` i usuwa timer, unlimited grants, high-water oraz
  receipts LifeSystemu; istniejące zachowanie resetu ustawień języka i hash
  zastosowanego debug presetu pozostaje niezmienione.
- Nieznana wersja, częściowy albo uszkodzony LifeState daje jawny błąd.
  Migracja nie może po cichu tworzyć żyć z nieprawidłowego profilu.

## Przepływ i integracja

```text
Dashboard
  → LifeSystem.readSnapshot()
  → lives / regeneracja lub unlimited countdown

CampaignScreen
  → pozwala przeglądać i wybrać odblokowaną mapę
  → przy 0 żyć wyłącza START LEVEL i pokazuje komunikat
  → application-level select/start revaliduje LifeSystem
  → aktywny unlimited omija gate

Campaign Match
  → MatchEvent.MatchFinished
  → application-level campaign outcome handler
      ├── BLUE_WIN → dotychczasowe CampaignSystem / RewardSystem
      └── non-BLUE_WIN → LifeSystem.settle(matchId, outcome)
  → atomowy profile update + receipt
  → wynik / LifeSnapshot prezentowany przez Android

Przyszły Ad Reward / Game Event
  → zweryfikowane źródło zdarzenia
  → grantFinite(sourceId, amount) albo grantUnlimited(sourceId, duration)
  → LifeSystem → ten sam profil i receipt store
```

Życia są pomiędzy meczami. Nie trafiają do `MatchSnapshot`, `SimulationConfig`,
`MatchReplay`, `ResourceWallet`, AI ani `CampaignProgress`; nie wpływają na
wynik aktualnie uruchomionego meczu.

`CampaignSystem` nadal jest właścicielem odblokowania/ukończenia poziomu.
LifeSystem jest właścicielem liczby żyć i dostępności startu wynikającej
wyłącznie z tej liczby lub aktywnego unlimited. UI tylko pokazuje `LifeSnapshot`.
Nie przenosić tej reguły do `CampaignMapView`, rendererów, `MainActivity` ani
symulacji.

## Lifecycle i czas

- LifeSystem nie jest resetowany przez pauzę, restart, Back ani lifecycle
  match screen.
- Czas regeneracji i unlimited liczy się po wall-clock także offline.
- Przy `lives == maximumLives` nie ma aktywnego timera; utrata życia z
  `5/5` ustawia następne odrodzenie na pełny interwał od bieżącego effective
  wall-clock.
- Gdy żyć jest mniej niż limit, każdy upłynięty interwał dodaje życie;
  pozostała część interwału zachowuje się, dopóki licznik nie osiągnie limitu.
- Gdy skończony grant przywróci `5/5`, timer jest kasowany.
- Unlimited zamraża pozostały czas regeneracji i nie zmienia bazowego
  `lives`; po wygaśnięciu wznawia zachowany czas. Sam unlimited expiry
  offline jest obliczany przy następnym odczycie/operacji.
- Ten sam wall-clock provider jest wstrzykiwany do use case’ów, aby testy
  mogły sterować czasem bez zegara systemowego i Androida.
- Żaden z tych timestampów nie bierze udziału w deterministycznym replayu
  meczu.

## Integracja z istniejącymi systemami i warianty

| Wariant | Ocena |
|---|---|
| LifeSystem w `game-application.application.progression`, wspólny profile DataStore, CampaignSystem pyta o dostęp | **Rekomendowany**: jeden stan, jasne odpowiedzialności, core testowalny bez Androida |
| Osobny moduł/DataStore lub osobny licznik UI | Odrzucony: duplikacja profilu, trudniejszy reset i ryzyko rozjazdu Dashboardu z gate kampanii |
| Życia jako `ResourceWallet`/zasób `game-simulation` | Odrzucony: życie ma przetrwać wiele meczów i aplikację, więc nie należy do runtime meczu |
| Rozszerzyć RewardSystem, aby zarządzał życiami | Odrzucony: RewardSystem rozlicza złoto za `BLUE_WIN`; LifeSystem musi rozliczać również non-win i czasowe granty |
| Przenieść reguły do CampaignScreen | Odrzucony: UI-check można ominąć, a UI stałby się właścicielem polityki gameplay/progresji |

Jedna atomowa transakcja DataStore aktualizuje lives/timer/grant receipt lub
match receipt. Życie i gold settlement pozostają oddzielnymi use case’ami.
Non-win nie zmienia CampaignProgress, więc jego LifeSettlement jest jedyną
mutacją progresji dla tego wyniku. BLUE_WIN zachowuje istniejący atomic
settlement ukończenia/nagrody i nie obciąża życia.

## Błędy, przypadki brzegowe i testy

### Reguły odrzucenia

- Ujemny/zerowy amount albo duration grantu: `INVALID_GRANT`.
- Ten sam source ID i ten sam request: zwróć poprzedni rezultat; ten sam ID
  z innym payloadem: `IDEMPOTENCY_CONFLICT`.
- Finite grant nad limitem: częściowo uzupełnij do `5` i zwróć ilość
  zastosowaną oraz nadwyżkę; przy pełnym limicie `LIMIT_REACHED`.
- Nowy `MatchId`: non-win zużywa najwyżej jedno życie; powtórzenie zwraca
  zapisany rezultat. `BLUE_WIN`, Demo/Test i brak `MatchFinished` nie zużywają.
- `lives == 0` bez aktywnego unlimited: odrzucenie startu z `NO_LIVES`.
- `lives == 0` z aktywnym unlimited: start dozwolony, zwykłych żyć nie
  zużywa się, a zegar regeneracji jest zatrzymany.
- Przy odczycie storage failure lub invalid data: jawny błąd, nie podstawiać
  `5/5` ani `N/A` jak poprawnych danych.
- Clock backward: nie zmniejszać high-water ani progress; clock forward może
  przyspieszyć lokalną regenerację — znane ograniczenie offline.
- Proces zabity przed trwałym zapisem wyniku: lokalny MVP może nie mieć jak
  odzyskać zdarzenia; pełny outbox/journal pozostaje poza pierwszą fazą.

### Testy pierwszej implementacji

- Balance JSON strict decode, walidacja i Studio edycja wartości `5`, `5`,
  `900000 ms`; zmiana rewizji balansu i przekazanie typed config do use case.
- Nowy profil i migracje istniejącego `profile.lives`: missing, `>5`,
  `<5` bez timera, pełny profil i unsupported/corrupt LifeState.
- Zużycie dla `RED_WIN`, `DRAW`, `SURRENDER`; brak zużycia dla `BLUE_WIN`,
  Demo, Test i braku `MatchFinished`.
- `MatchId` duplicate, restart/ponowne dostarczenie tego samego eventu i
  idempotentny retry po `STORAGE_UNAVAILABLE`.
- Regeneracja pod granicami czasu (przed, dokładnie na, po 15 min), kilka
  pełnych interwałów offline, cap catch-up oraz brak postępu przy `5/5`.
- Pierwsza porażka z `5/5` rozpoczyna 15 minut; kolejne porażki nie resetują
  timera; partial grant zachowuje timer; dojście do pełnego limitu kasuje go.
- Zegar cofnięty nie odbiera życia ani postępu; po dojściu do high-water
  regeneracja kontynuuje.
- Finite grant `1`, multi-life partial grant, at-cap result, stable source ID,
  duplikat i idempotency conflict.
- Unlimited activation, bypass bramki przy `0`, brak zużycia i regeneracji,
  offline expiry, wznowienie zachowanego timera, additive stacking oraz
  finite grant podczas unlimited.
- RESET przywraca `5/5` i usuwa aktywne granty/timery/receipts, nie kasując
  języka; debug preset synchronizuje timer i grants.
- Dashboard: countdown, `N/A` przy pełnym stanie, unlimited `∞` i pozostały
  czas; Campaign: mapa i wybór nadal działają przy zerze, start jest
  zablokowany z komunikatem; aplikacyjna walidacja nie daje ominąć UI.
- Brak life fields w `MatchSnapshot`/`SimulationConfig` i brak Androidowych
  zależności w LifeSystem.

### Kryteria akceptacji

1. Jedno kanoniczne życie na profil, wspólny z istniejącym DataStore/resetem.
2. Dokładne, idempotentne zużycie tylko dla kampanijnych non-win wyników.
3. Regeneracja 15-minutowa offline, cap 5, catch-up i clock rollback zgodne z
   zaakceptowanymi regułami.
4. Campaign może być przeglądana przy zero; start wymaga zwykłego życia albo
   aktywnego unlimited grant.
5. Granty finite/unlimited mają stabilne ID, typed result, partial cap,
   additive duration oraz retry bez dublowania.
6. Developer preset i RESET korzystają z tych samych reguł stanu; nie tworzą
   drugiej ścieżki zapisu.
7. Dashboard i Campaign renderują tylko LifeSystem read model; brak reguł
   żyć w Android UI i symulacji.
8. Ad Reward i event sources nie są implementowane; ich przyszłe wywołania
   korzystają z tego samego grant API.

## Zrealizowana implementacja

1. `runtime.campaign.lives` dodaje typed `LifeBalance` z limitem, wartością
   początkową i interwałem. Strict decoder, Studio Balance i testy walidują
   ten sam profil; zmiana podniosła revision do `game-balance-18`.
2. `LifeSystem` w `game-application.application.progression` używa wstrzykniętego
   `WallClock`, niezmiennych state/snapshot/receipt oraz atomowego portu
   `LifeStateRepository`.
3. Preferences DataStore zachowuje jedno źródło prawdy. `profile.schema_version`
   migruje z v1 do v2, `profile.lives` jest przenoszone do LifeState, a
   zużycia/granty zapisują receipts z tym samym atomowym profilem. RESET czyści
   LifeState, granty i receipts, zachowując hash debug presetu.
4. Developer Progress Preset ustawia LifeState przez wspólną fabrykę. Preset
   bez pola `lives` nie nadpisuje go.
5. `CampaignLevelStartUseCase` sprawdza poziom i świeży LifeSnapshot zarówno
   przed Loadoutem, jak i przed uruchomieniem kampanijnego meczu. Przy zerze
   nadal można przeglądać mapę i zaznaczyć poziom; CTA startu jest blokowane.
6. `CampaignMatchCompletionUseCase` kieruje BLUE_WIN do dotychczasowego
   Campaign/Reward flow, a pozostałe kampanijne wyniki do LifeSystem.
   Niepowodzenie zapisu wymaga retry; popup i systemowy Back nie pozwalają
   opuścić wyniku przed settlementem.
7. Dashboard czyta nowy `DashboardProgressSummaryUseCase` i prezentuje życie,
   odliczanie, `N/A` lub unlimited `∞`. Dashboard i Campaign aktualizują
   wyłącznie lokalny countdown w czasie lifecycle ekranu.
8. Testy obejmują reguły i czas, migrację/receipt/reset DataStore, bramkę
   kampanii, settlement wyniku, Studio Balance oraz Android presentation.

## Główne pliki implementacji

- `game-domain/src/main/kotlin/com/urkaaaz/domain/RuntimeBalance.kt`
- `game-domain/src/main/resources/balance/game-balance.json`
- `game-application/src/main/kotlin/com/urkaaaz/application/progression/LifeStateRepository.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/progression/LifeSystem.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/progression/DashboardProgressSummaryUseCase.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignLevelStartUseCase.kt`
- `game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignMatchCompletionUseCase.kt`
- `android-app/src/main/java/com/urkaaaz/persistence/PlayerProgressionDataStoreCodec.kt`
- `android-app/src/main/java/com/urkaaaz/persistence/DataStoreLifeStateRepository.kt`
- `android-app/src/main/java/com/urkaaaz/persistence/SystemWallClock.kt`
- `android-app/src/main/java/com/urkaaaz/persistence/DataStoreDeveloperProgressPresetAdapter.kt`
- `android-app/src/main/java/com/urkaaaz/screens/campaign/CampaignScreen.kt`
- `android-app/src/main/java/com/urkaaaz/screens/dashboard/DashboardScreen.kt`
- `android-app/src/main/java/com/urkaaaz/screens/match/CampaignCompletionPresenter.kt`
- `studio/server/src/services/balanceCatalog.ts`
- `studio/web/src/tabs/balance/model.ts` i `BalanceForm.tsx`

## Poza zakresem i ograniczenia

- brak realnego rewarded-ad SDK, event scheduler, backendu, kont i cloud sync;
- brak przenoszenia żyć do symulacji, `MatchSnapshot` lub replay;
- brak powiadomień Androida o odrodzeniu życia;
- lokalny clock nie zabezpiecza przed ręcznym przyspieszeniem czasu;
- `ResultScreen` pozostaje placeholderem; retry korzysta z istniejącego
  match-result popup;
- Ad Reward, event source, backend i synchronizacja chmurowa pozostają
  niezaimplementowane; ich przyszłe adaptery mają używać wspólnego grant API.
