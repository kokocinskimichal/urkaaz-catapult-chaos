# Prompt implementacji LifeSystem

Zaimplementuj **LifeSystem** end-to-end zgodnie z zatwierdzonym projektem
[`life-system.md`](life-system.md). System ma obsługiwać trwałe życia profilu
kampanii, regenerację offline, skończone i czasowe granty unlimited oraz
blokadę startu poziomu przy zerowej liczbie żyć.

To implementacja istniejącego lokalnego przepływu. Nie dodawaj nowego modułu
Gradle, serwera, kont, integracji reklamowej ani logiki żyć do symulacji meczu.

## Kontekst repozytorium i zasady pracy

Przed zmianami:

1. Sprawdź bieżący branch i pełny `git status --short`. Zachowaj wszystkie
   wcześniejsze, niezatwierdzone zmiany użytkownika, w tym zmiany ekranu
   Campaign, assetów i map. Nie cofaj, nie stashuj ani nie formatuj ich
   hurtowo.
2. Przeczytaj `AGENTS.md`, `docs/architecture/architecture.md`,
   `docs/architecture/implementation-rules.md`, `docs/architecture/project-map.md`,
   `docs/project/current-state.md`, `docs/project/knowledge.md`,
   `docs/systems/life-system.md`, `docs/systems/player-progression-system.md`,
   `docs/systems/campaign-system.md` oraz `docs/systems/reward-system.md`.
3. Przeczytaj aktualne testy i implementacje wskazane w LifeSystem design:
   player progression repository/codec, developer preset, reset,
   `DataStoreRewardSettlementAdapter`, CampaignSystem, CampaignScreen,
   CampaignViewModel, CampaignCompletionPresenter, DashboardScreen oraz
   `MainActivity`.
4. Dokumenty opisują zatwierdzone decyzje, ale bieżący kod jest źródłem prawdy
   dla istniejących kontraktów. Nie zastępuj ani nie nadpisuj zmian, które
   pojawiły się w worktree po przygotowaniu dokumentów.

Granice architektury:

- Właścicielem reguł i aplikacyjnych use case’ów jest
  `game-application.application.progression`; nie twórz nowego modułu Gradle.
- Trwałe dane żyć są częścią jednego kanonicznego lokalnego profilu w
  istniejącym Preferences DataStore. Nie twórz drugiego licznika, profilu ani
  DataStore.
- `game-domain` przechowuje typed config balansu. Android DataStore/Clock/View
  zależności pozostają w `android-app`.
- CampaignSystem zachowuje własność odblokowania i ukończenia poziomów.
  RewardSystem zachowuje własność złota i zwycięskich nagród.
- UI renderuje `LifeSnapshot` i wysyła akcje/use case’y. Nie liczy żyć,
  timestampów ani grantów samodzielnie.
- Życia nie trafiają do `MatchSnapshot`, `SimulationConfig`, `ResourceWallet`,
  AI ani `MatchReplay`.
- Błędy persystencji są jawne. Nie używaj szerokich `catch`, cichych fallbacków
  ani wyniku sugerującego sukces po nieudanym zapisie.

## Potwierdzone wymagania — nie pytaj ponownie

### Reguły żyć i kampanii

- Nowy profil ma `5/5` żyć; limit to `5`.
- Każdy kanoniczny kampanijny `MatchFinished` inny niż `BLUE_WIN` zużywa jedno
  życie: `RED_WIN`, `DRAW` i `SURRENDER`.
- `BLUE_WIN` nie zużywa życia. Istniejące zwycięskie rozliczenie Campaign/
  Reward pozostaje bez zmian.
- Demo i Test Mode nigdy nie zużywają życia. Utrzymaj istniejącą filtrację
  zdarzeń Campaign completion dla tych trybów.
- Brak `MatchFinished` (np. przerwany/aborted mecz bez wyniku) nie zużywa życia.
- Zużycie jest idempotentne po `MatchId`: ponowne dostarczenie wyniku nie może
  odjąć drugiego życia.
- Przy `0` życiach gracz nadal może przeglądać mapę kampanii i wybrać poziom,
  ale `START LEVEL` jest wyłączony i pokazuje lokalizowany komunikat. Aktywny
  unlimited grant pozwala rozpocząć poziom nawet przy zapisanym `0`.
- Samo wyłączenie przycisku w CampaignScreen nie wystarcza: aplikacyjna
  walidacja startu musi ponownie sprawdzić dostępność życia/unlimited.

### Regeneracja i zegar

- Jedno życie regeneruje się co `900000 ms` (15 minut) lokalnego czasu
  ściennego, także gdy aplikacja jest zamknięta.
- Po powrocie nalicz wszystkie pełne interwały, ograniczając wynik do `5`.
- Przy `5/5` nie ma aktywnego timera ani akumulowanego over-cap czasu.
- Utrata życia z pełnych `5/5` rozpoczyna pełne 15 minut od bieżącego czasu.
- Gdy liczba żyć jest poniżej limitu, kolejna porażka zachowuje bieżący postęp
  regeneracji; nie resetuje timera.
- Skończony grant zachowuje bieżący timer. Jeśli przywraca `5/5`, timer zostaje
  wyczyszczony.
- Gdy zegar systemowy zostanie cofnięty, nie cofaj liczby żyć ani już
  naliczonego postępu. Persistuj high-water mark efektywnego czasu; regeneracja
  wznawia się, gdy wall-clock ponownie go osiągnie.
- Akceptowany jest lokalny zegar offline. Nie deklaruj ochrony przed ręcznym
  przyspieszeniem czasu do przodu.
- Wstrzyknij jawny wall-clock port dla testów. Nie używaj czasu symulacji,
  ticków meczu ani wyłącznie `elapsedRealtime`.

### Granty

- Wszystkie przyszłe źródła korzystają z jednego LifeSystem grant use case.
  LifeSystem nie implementuje reklamy ani event feedu.
- Każdy grant ma stabilny `sourceId`, typ źródła i payload. Duplikat tego
  samego ID/payloadu zwraca wcześniej zapisany wynik; to samo ID z innym
  payloadem jest jawnym konfliktem.
- Skończony grant przyjmuje dodatnią liczbę żyć. Przyznaj tylko tyle, ile
  mieści się do limitu: np. `4/5` plus grant `3` daje `5/5`, z wynikiem
  pokazującym przyznane `1` i odrzuconą nadwyżkę. Przy pełnym limicie zwróć
  `LIMIT_REACHED`; nigdy nie przekraczaj limitu.
- Przyszły Ad Reward przekazuje grant jednego życia (`Finite(1)`); inne eventy
  przekazują jawną ilość.
- Czasowy unlimited przyjmuje dodatni czas od źródła. Nie zmienia bazowego
  licznika; podczas aktywności:
  - kampania może startować także przy bazowym `0`;
  - wyniki nie zużywają zwykłych żyć;
  - regeneracja zwykłych żyć jest wstrzymana, z zachowaniem pozostałego czasu.
- Grant unlimited upływa także offline. Kolejny grant w czasie aktywnego okresu
  wydłuża jego koniec o podany czas.
- Po wygaśnięciu wznów zachowany timer. Jeśli aplikacja była zamknięta po
  wygaśnięciu, uwzględnij elapsed wall-clock od chwili wznowienia i nalicz
  wszystkie pełne interwały do limitu.
- Skończone granty podczas unlimited nadal dodają do bazowego licznika do
  limitu `5`; przy pełnym liczniku zwracają `LIMIT_REACHED`.

### Profil, konfiguracja i UI

- Parametry `initialLives=5`, `maximumLives=5` i
  `regenerationIntervalMilliseconds=900000` pochodzą z jednego typed źródła
  `game-balance.json`, proponowana lokalizacja:
  `runtime.campaign.lives`. Rozszerz strict decoder/validator i istniejący
  Studio Balance; podnieś balance revision zgodnie z konwencją.
- Developer Progress Preset może jawnie ustawiać liczbę żyć wyłącznie w debug
  buildzie, ale musi korzystać z tych samych reguł LifeSystemu: wartość poniżej
  limitu dostaje pełne 15 minut od zastosowania; `5/5` czyści timer i granty.
- Migracja istniejącego `profile.lives > 5` ogranicza wartość do `5`.
  Wartość poniżej `5` bez timera otrzymuje pełny timer 15 minut od migracji.
  Brak wartości życia dla istniejącego profilu używa zaakceptowanego defaultu
  `5`, a nowy schema state zapisuje się kontrolowanie. Nie maskuj częściowych,
  uszkodzonych ani nieobsługiwanych danych sukcesem.
- RESET przywraca `5/5` i czyści LifeSystem timer, high-water mark, active
  grants i receipts; zachowaj istniejące zachowanie resetu innych danych oraz
  języka i hash już zastosowanego Developer Progress Preset.
- Dashboard wykorzystuje istniejące pole regeneracji:
  - przy `5/5`: `N/A`;
  - poniżej limitu: czas do następnego życia;
  - unlimited: `∞` i czas do końca grantu.
- CampaignScreen zachowuje mapę/wybór przy `0`, ale blokuje `START LEVEL` z
  jawnie lokalizowanym powodem. Gdy stan LifeSystem jest loading/error,
  bezpiecznie nie uruchamiaj kampanii i pokaż jawny stan.
- Aktualizuj Dashboard i Campaign na lifecycle ekranu; ticker countdownu jest
  tylko prezentacyjny i anulowany przy `onPause`/zniszczeniu. Nie dodawaj
  alarmów, WorkManager, powiadomień ani background tasków.

## Implementacja krok po kroku

### 1. Audyt i plan migracji

- Sprawdź aktualne schema `game-balance.json`, `CampaignBalance`,
  Balance decoder/validator, Studio Balance model/form/testy.
- Sprawdź `LoadoutProgressionSnapshot`, `PlayerProgressionRepository`,
  `PlayerProgressionDataStoreCodec`, `DataStoreDeveloperProgressPresetAdapter`,
  `DataStorePlayerProgressResetRepository`, `DataStoreRewardSettlementAdapter`
  i adaptery korzystające ze wspólnego DataStore.
- Obecnie lives są zapisane przez Developer Progress Preset pod
  `profile.lives`, ale `PlayerProgressionDataStoreCodec` i kanoniczny port
  profilu nie modelują jeszcze całego LifeState. Zaprojektuj migrację tak,
  by istniała jedna kanoniczna wartość, a Reward/Shop settlement nie
  nadpisywały jej zapisem starego snapshotu.
- Nie uruchamiaj implementacji z drugim profilem lub niezależnym DataStore.

### 2. Balans i Studio

- Dodaj typed `LifeBalance` do istniejącej konfiguracji kampanii, z walidacją
  dodatniego limitu/interwału oraz `initialLives` w zakresie `0..maximumLives`.
- Dodaj wartości zatwierdzone powyżej do bundled `game-balance.json`.
- Zaktualizuj decoder, strict schema/range tests, fixtures i balance revision.
- Dodaj pola do istniejącego Studio Balance (sekcja Runtime/Campaign), bez
  raw-JSON editora i bez kopiowania wartości do Androida.
- Zachowaj wspólne źródło prawdy dla Androida, symulacji, aplikacji i Studio;
  LifeSystem nie używa tych wartości w symulacji meczu.

### 3. Application LifeSystem

- Dodaj immutable `LifeState` i `LifeSnapshot` oraz typowane request/result/
  failure contracts w `game-application.application.progression`.
- Dodaj injected wall-clock abstraction i czyste obliczenia czasu, testowalne
  z kontrolowanym czasem.
- Rozszerz istniejącego właściciela profilu/port transaction albo dodaj
  LifeSystem port używający tej samej atomowej transakcji profilu. Nie dodawaj
  drugiego canonical snapshotu życia. `LoadoutProgressionSnapshot` pozostaje
  read modelem dla Loadout; dodaj LifeSnapshot oddzielnie.
- Wszystkie odczyty i zmiany stanu aktualizują wall-clock high-water mark,
  przetwarzają offline catch-up i zachowują reguły pełnego limitu/unlimited.
- Grant API waliduje request, stosuje cap/partial grant, stacking unlimited,
  receipts i payload conflict.
- Result API przyjmuje kampanijny poziom/kontekst oraz kanoniczne
  `MatchFinished`; zużycie jest dokładnie raz po `MatchId` tylko dla
  nie-`BLUE_WIN` kampanii. Nie zmieniaj `CampaignProgress` w LifeSystemie.
- Nie rozliczaj wyniku Demo/Test, replay ani nieukończonego meczu.
- Daj aplikacyjny access/read API dla CampaignSystem, aby kampanijna
  autoryzacja weryfikowała życie/unlimited świeżo, a nie tylko na podstawie
  starego UI snapshotu.

### 4. DataStore, migracja i transakcje

- Użyj istniejącego `urkaaz_local_progress` Preferences DataStore.
- Rozszerz wspólny profile codec/transaction tak, by życie, timer, grants i
  receipts były częścią tego samego kanonicznego profilu. Nie twórz równoległej
  serializacji, której `PlayerProgressionDataStoreCodec` nie zna.
- Atomowe `DataStore.updateData` musi:
  - zachować klucze CampaignProgress, gold, unlocks, stocków, loadoutów, reward
    receipts i developer preset hash;
  - odczytywać aktualny stan, walidować go i zapisywać life delta plus receipt
    w jednej transakcji;
  - nie nadpisywać świeżego gold/stock snapshotem z początku operacji.
- Dodaj jawny, wersjonowany migration path ze stanu istniejącego
  `profile.lives`; partial/corrupt/unsupported profile zwraca failure.
- Włącz life state i receipts do resetu; reset jest jednym atomic update,
  wraca do default `5/5` (przez brak kluczy albo jawne initial state zgodnie z
  obecnym repo pattern) i zachowuje zastosowany developer preset hash.
- Jeżeli DeveloperProgressPreset włącza pole `LIVES`, zapisuje je w tej samej
  canonical life transaction i nie zapisuje niezależnej kopii.
- Storage failure ma typowany wynik. Przy failure zużycia nie zgłaszaj
  settlement success.

### 5. Campaign, match completion i bramka startu

- Zachowaj istniejące reguły CampaignSystem: poziomy, completed state,
  milestone’y i BLUE_WIN rewards pozostają jego/RewardSystem odpowiedzialnością.
- Dodaj jawny application boundary dla life eligibility przy wyborze/start
  kampanijnego poziomu. CampaignScreen może przeglądać mapę i zaznaczyć
  dostępny poziom przy `0`, ale `START LEVEL` jest disabled i informuje o braku
  życia. Use case świeżo rewaliduje stan i zwraca typed `NO_LIVES`.
- Active unlimited grant pozwala na start także przy base lives `0`.
- Zinwentaryzuj istniejący match completion callback. `MatchViewModel`
  forwarduje `MatchFinished` raz i wyłącza Demo/Test; utrzymaj tę granicę.
- Zrealizuj life settlement w application-level completion flow, nie w
  CampaignScreen, `MainActivity` ani `CampaignCompletionPresenter` jako
  właścicielu reguły. Result transportuje jawny status.
- Przy non-win tylko LifeSystem zmienia profil; CampaignProgress i gold
  pozostają bez zmian. Przy BLUE_WIN istniejący RewardSettlement pozostaje
  bez life debit.
- Przy life storage failure utrzymaj popup wyniku, pokaż błąd i retry.
  Zablokuj Campaign return/restart/next action do czasu rozliczenia. Retry
  używa tego samego `MatchId` i jest idempotentny.

### 6. UI i lokalizacja

- Rozszerz Dashboard read model o `LifeSnapshot` bez uzależniania production
  lives od DeveloperProgressPreset API.
- Renderuj timer i unlimited status z LifeSnapshot. Przy pełnym limicie
  zachowaj `N/A`. Odśwież countdown przez lifecycle-safe presentation tick,
  bez zmiany profilu co każdą sekundę.
- CampaignScreen odczytuje campaign progress i life eligibility; nie pokazuje
  poziomów jako zablokowanych tylko z powodu zerowych żyć, ale blokuje start.
- Dodaj PL/EN/ES strings dla brakującego życia, regeneracji, błędów i retry.
- UI ma tylko prezentować stany i przekazywać akcje/use case’y.
- Nie dodawaj Ad Reward Screen, ad SDK, event feed, button actions ani
  backendu; grant endpoint pozostaje gotowy dla osobnych przyszłych systemów.

## Testy i kryteria akceptacji

### Domain/config

- Balance strict decode, validation, serialization/form round-trip i revision.
- Granice `initialLives`/maximum/interwału, invalid values oraz JSON unknown
  fields.

### Application

- New profile `5/5`, migration cases `>5`, `<5` without timer, missing lives,
  corrupt/unsupported schema.
- Campaign `BLUE_WIN` bez life debit; `RED_WIN`, `DRAW`, `SURRENDER` debit
  dokładnie raz; Demo/Test/missing finish bez debitu.
- Offline catch-up jednego i wielu interwałów, exact boundary, cap, no
  over-cap accumulation, consume from full starts full interval, repeated
  losses preserve timer.
- Wall-clock rollback/high-water, forward-time behavior documented/tested.
- Finite grants exact, partial, at cap, amount validation, duplicate source ID
  returns same outcome, payload conflict rejected.
- Unlimited: activation, bypass at zero, no debit/regen, timer freeze/resume,
  offline expiration, grant stacking and finite grant while active.
- Life reset and developer preset set/reset lives plus timer/grants coherently.
- Storage error returns explicit retryable failure and never changes displayed
  life state to success.
- Same time input and persisted state yields same snapshot; no wall-clock
  dependency hidden inside pure rules.

### DataStore

- Transactional concurrent profile update preserves Reward/Shop/profile data.
- Match and grant receipts persist atomically with the life changes.
- Duplicate transactions are idempotent after adapter recreation.
- Reset removes only intended progression, does not remove language settings,
  and preserves applied Developer Progress preset hash.
- Migration is atomic and never replaces corrupt data with default success.

### Android/Campaign

- Dashboard shows countdown, `N/A`, unlimited `∞`/remaining time, loading and
  storage errors using PL/EN/ES strings.
- Campaign map remains browsable/selectable at zero; `START LEVEL` disabled
  with message; application-level call also rejects direct bypass.
- Active unlimited grant permits campaign start at zero.
- Demo/Test do not consume lives, and replay cannot mutate persistent profile.
- Life settlement failure keeps result visible with retry and blocks exiting/
  restarting until resolved.
- Lifecycle cancels UI-only ticking; no gameplay state exists in views.
- Core remains Android-independent; no duplicate life counter or profile.

### Done criteria

- One canonical persisted life state, transactionally shared with the existing
  profile/DataStore.
- All confirmed gameplay/time/grant/reset rules hold and have focused tests.
- Campaign selection is gated by fresh LifeSystem state, with UI preserving
  map browsing.
- The new LifeSystem owns no match runtime state and does not alter reward,
  test-mode, demo, or replay behavior.
- `./gradlew test assembleDebug --quiet`, `git diff --check`, and
  `git status --short` pass. Do not commit or push unless separately requested.

## Expected output

Implement code, tests, and directly related documentation. Update
`docs/systems/life-system.md` from design to implemented status only for
completed behavior. Update `docs/project/CHANGELOG.md`,
`docs/project/current-state.md`, `docs/project/knowledge.md`, the
architecture/project map, glossary, and balance Studio documentation only
where implementation actually changes them. Preserve all unrelated working
tree changes, report remaining limitations, and do not create a commit or push.
