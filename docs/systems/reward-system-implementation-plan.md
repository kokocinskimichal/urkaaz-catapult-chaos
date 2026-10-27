# RewardSystem — szczegółowy plan implementacji

Status: **plan, nic nie zaimplementowano**. Architektura i decyzje:
[reward-system.md](reward-system.md). Ten dokument rozkłada je na fazy z
plikami, krokami, testami i kryteriami akceptacji.

## Zasady wykonania

- Każda faza kończy się zielonym `./gradlew test assembleDebug --quiet`,
  `git diff --check` i przeglądem względem `docs/architecture/architecture.md`.
- Fazy 1–2 zmieniają kontrakt i symulację; reszta może być robiona dopiero po
  ich zakończeniu. Fazy 3 i 6 są niezależne od 1–2 i można je robić równolegle.
- Kwoty złota, progi i wzrost serii **nie są ustalone**. Do czasu decyzji
  katalog w fazie 3 zawiera wyłącznie wartości testowe w testach, a nie w
  kodzie produkcyjnym (patrz „Blokery”).
- Nie ruszamy niezwiązanych zmian w working tree (assety raging boar,
  `.kotlin/`, `CHANGELOG.md`, `knowledge.md` itd.).
- Commit/push tylko na wyraźne polecenie (`wypchnij`).

## Graf zależności faz

```text
1 Kontrakt eventów ─► 2 Emisja w symulacji ─┐
3 Katalog nagród (game-campaign) ───────────┼─► 4 Evaluator + tracker ─► 5 Hook w MatchSession
6 Port rozliczenia + use case ──────────────┘                              │
7 LEVEL_ALREADY_COMPLETED ──► 8 Adapter DataStore ──► 9 Przepływ Android + popup ◄┘
                                                      10 Dokumentacja i bramka walidacji
```

## Faza 1 — rozszerzenie kontraktu eventów (`game-contracts`)

Cel: eventy niosą drużynę i rodzaj celu oraz informują o rozstrzygnięciu
pocisku. Brak parsowania ID.

Plik: `game-contracts/.../MatchEvents.kt` (+ testy kontraktów).

Kroki:

1. Dodać enum `ProjectileTargetKind { UNIT, FORTRESS, FORTRESS_SHIELD, BASTION }`.
2. Dodać pola z jawnymi wartościami domyślnymi `null` („nieznane”, nigdy
   fałszywy sukces):
   - `ProjectileFired.shooterTeam: Team?`;
   - `ProjectileHitEntity.targetTeam: Team?`, `targetKind: ProjectileTargetKind?`;
   - `DamageApplied.targetTeam: Team?`, `targetKind: ProjectileTargetKind?`;
   - `UnitDeployed.team: Team?`.
3. Dodać `MatchEvent.ProjectileResolved(eventId, matchId,
   simulationTimeMilliseconds, projectileId, reason: ProjectileResolution)` oraz
   enum `ProjectileResolution { ENTITY_IMPACT, SHIELD_IMPACT, TERRAIN_IMPACT,
   OUT_OF_BOUNDS, LIFETIME_EXPIRED }`.
4. `targetLayer` zostaje bez zmian (kompatybilność); nowe pola są źródłem
   prawdy dla nagród.

Testy: konstrukcja eventów z domyślnymi `null`; egzaustywność `when` po
`MatchEvent` w całym repozytorium (kompilacja — patrz `RenderStateMapper.kt`,
który używa `is MatchEvent.*`; sprawdzić, czy nowy podtyp nie wymaga gałęzi).

Akceptacja: wszystkie moduły kompilują się bez zmian zachowania; stare testy
zielone.

## Faza 2 — emisja w symulacji (`game-simulation`)

Cel: symulacja wypełnia nowe pola i emituje `ProjectileResolved` dokładnie raz
na pocisk.

Plik: `DeterministicMatchSimulation.kt`.

Kroki:

1. `ProjectileFired` (~l. 332): `shooterTeam` z drużyny katapulty.
2. `ProjectileHitEntity`:
   - tarcza (~l. 242): `FORTRESS_SHIELD`, drużyna właściciela fortecy;
   - encja (~l. 2293): `targetKind` rozstrzygane po **typie encji w
     symulacji** (jednostka / forteca / bastion), nie po tekście ID.
3. `emitDamage` (~l. 2746) i jego wywołania (~1781, 2045, 2127, 2187, 2420,
   2433, 2490): przekazać `targetTeam` i `targetKind`; poprawić warstwę dla
   obrażeń jednostek.
4. `UnitDeployed`: `team`.
5. `ProjectileResolved` we wszystkich terminalnych ścieżkach
   `advanceProjectile` (~l. 662, wywołania ~599/602/609): tarcza, uderzenie
   w encję bez przebicia, teren (~l. 1064), poza mapą, wygaśnięcie.
   Pocisk przebijający emituje trafienia bez rozstrzygnięcia aż do
   zakończenia lotu. Fragmenty (`projectile-N-fragment-k`) rozstrzygają się
   osobno; rodzic po `ProjectileSplit` leci dalej i też emituje własne.
6. Zachować kolejność: eventy trafień i `ProjectileResolved` w tej samej
   chwili symulacji mają stabilną, deterministyczną kolejność (trafienie przed
   rozstrzygnięciem tego samego pocisku).
7. Zakończenie meczu w trakcie ticku: pociski w locie nie dostają
   `ProjectileResolved` po `MatchFinished` (tracker zamyka wtedy mecz
   jawnie, patrz faza 4).

Testy (`game-simulation`):

- każdy typ rozstrzygnięcia emituje dokładnie jeden `ProjectileResolved`;
- trafienie w jednostkę wroga/własną ma poprawne `targetTeam`/`targetKind`;
- bastion ≠ forteca ≠ tarcza w `targetKind`;
- pocisk przebijający: kilka trafień, jedno rozstrzygnięcie;
- fragmentacja: każdy fragment i rodzic rozstrzygają się raz;
- obszarowe obrażenia nie emitują `ProjectileHitEntity`;
- determinizm: ten sam seed i komendy dają identyczny strumień eventów;
- `RenderStateMapperTest` i inne testy konstruujące eventy nadal przechodzą.

Akceptacja: sekwencja eventów z `MatchReplayPlayer` jest identyczna w dwóch
przebiegach; żaden pocisk nie „znika” bez `ProjectileResolved` (poza
zakończeniem meczu).

## Faza 3 — katalog nagród kampanii (`game-campaign`)

Cel: jeden rozszerzalny, walidowany katalog definicji nagród i osiągnięć
poziomu, uogólniający `CampaignMilestoneRegistry`.

Pliki: `CampaignMilestoneDefinition.kt` (lub nowy `CampaignRewardDefinition.kt`),
`CampaignLevelRegistry.kt`, testy `CampaignMilestoneDefinitionTest`.

Kroki:

1. Typy definicji (niemutowalne, bez Androida): stabilne `RewardId`,
   `LevelRewardDefinition(level, winGold, achievements)`,
   `AchievementDefinition(id, condition, gold)`.
2. Typowane warunki jako sealed interface: `DirectUnitHit`, `FastWin(maxTimeMs)`,
   `UntouchedFortress`, `FortressHitStreak(steps)`. Dodanie nowego warunku =
   nowy podtyp + obsługa w evaluatorze (kompilator wymusza `when`).
3. Zachować stabilne ID `campaign_spell_haste` (poziom 5) i
   `campaign_spell_heal` (poziom 7) oraz obecną walidację.
4. Walidacja przy budowie katalogu: poziom w `1..MAX_LEVELS`, brak duplikatów
   ID, kwoty `>= 0`, progi `> 0`, rosnąca seria, jawny błąd zamiast cichego
   pominięcia.

Testy: walidacja każdego odrzucenia, kompletność poziomów, zachowanie
istniejących milestone’ów.

Akceptacja: obecne zachowanie milestone’ów bez zmian; katalog nie zawiera
runtime state.

## Faza 4 — evaluator i tracker (`game-application/.../application/reward/`)

Cel: czysta logika wyliczająca przyznane osiągnięcia z uporządkowanego
strumienia eventów; jeden właściciel runtime state nagród.

Nowe pliki: `RewardTracker`, `RewardEvaluator`, `LevelRewardResult`
(przyznane pozycje + suma), testy.

Stan trackera (per generacja meczu, reset na `MatchStarted`):

- mapa `projectileId → shotId` (z `ProjectileFired` i `ProjectileSplit`);
- per strzał: zbiór nierozstrzygniętych pocisków, flaga „bezpośrednie
  trafienie w fortecę wroga”;
- licznik serii i kolejka strzałów rozstrzyganych w kolejności zakończenia;
- flagi: trafiono jednostkę wroga (para pocisk–cel liczona raz), forteca
  gracza uszkodzona, czas końca;
- zbiór przetworzonych `EventId` (dedup).

Reguły (zgodne z decyzjami użytkownika):

- seria: strzał liczy się, gdy któryś jego pocisk trafił **fortecę wroga
  bezpośrednio**; trafienie w bastion/tarczę się nie liczy; każdy inny
  rozstrzygnięty strzał zeruje serię; strzały wroga nie wpływają;
  kolejność = kolejność rozstrzygnięcia (czas symulacji, potem kolejność
  eventów);
- strzał rozstrzygnięty, gdy wszystkie jego pociski mają `ProjectileResolved`;
- „nietknięta forteca”: brak `DamageApplied`/`ProjectileHitEntity` z
  `targetKind == FORTRESS` i `targetTeam == BLUE`; bastiony i tarcza się nie
  liczą;
- `null` w polu drużyny/rodzaju = nieznane → nie przyznajemy osiągnięcia i
  zgłaszamy jawny błąd walidacji w wyniku (nigdy sukces);
- `MatchFinished(BLUE_WIN)` kończy ocenę: strzały jeszcze w locie są
  rozstrzygane jako nieliczone; wynik zawiera listę osiągnięć i sumę;
  inne wyniki → brak nagród.

Testy: osobna klasa testów trackera z tabelą scenariuszy — pierwsze trafienie
bez bonusu, drugie/trzecie z bonusem, pudło zeruje serię, bastion/tarcza
zeruje serię (nie liczy się), strzały wroga ignorowane, równoczesne strzały i
kolejność rozstrzygnięcia, fragmenty, pierce raz na parę, szybka wygrana na
granicy progu, reset na `MatchStarted`, dedup `EventId`, `null` = brak
przyznania, brak złota przy porażce/remisie/surrender.

Akceptacja: tracker deterministyczny; ten sam strumień → ten sam wynik.

## Faza 5 — hook w `MatchSession` / `LocalMatchGateway`

Cel: tracker widzi **każdy** uporządkowany event, zanim zostanie utracony przez
`dispatch`.

Pliki: `application/MatchSession.kt`, `LocalMatchGateway.kt`, `MatchReplay.kt`.

Kroki:

1. Wprowadzić port obserwatora `MatchEventObserver` (w `game-application`),
   wstrzykiwany do `MatchSession` z domyślnym no-op.
2. Wywoływać go z uporządkowaną paczką po każdym źródle eventów: `start()`,
   `advance`, `fire`, `deployUnit`, `sendWave`, `surrender`, `dispatchSpell`
   oraz `restart` (przez `start`). `MatchStarted` resetuje tracker.
3. `LocalMatchGateway` przekazuje obserwatora bez własnej logiki.
4. Wynik trackera jest dostępny po `MatchFinished` przez read model (nie przez
   mutowalny stan).

Testy: `MatchSessionTest`/`LocalMatchGatewayTest` — obserwator dostaje eventy
ze wszystkich ścieżek, także gdy UI nie wywoła `consumeEvents`; restart
resetuje; replay z `MatchReplayPlayer` daje identyczny wynik trackera.

Akceptacja: brak zmiany obecnego zachowania `consumeEvents`; brak zależności
od Androida.

## Faza 6 — port rozliczenia i use case (`game-application`)

Cel: atomowe, idempotentne naliczenie złota + ukończenia poziomu + receipt.

Pliki: `application/reward/RewardSettlementPort.kt`, `RewardSettlementUseCase`,
`progression/PlayerProgressionRepository.kt`, `PlayerProgressionUseCases.kt`.

Kroki:

1. Port w kształcie `update`: czysta transformacja
   `(CampaignProgress, PlayerProfile, RewardReceipts) → wynik` w warstwie
   aplikacji; atomowy read-modify-write w adapterze. Do portu przekazywany
   jest początkowy profil (obecnie `INITIAL_PROFILE` jest prywatny w
   `PlayerProgressionUseCases` — wyeksponować go jako wspólne źródło prawdy,
   nie kopiować).
2. Klucz receipt: `(level, matchId/runId)`; ponowna próba tego samego
   rozliczenia zwraca ten sam wynik bez podwójnego złota.
3. Przepełnienie `Int` salda (`goldBalance`) → jawne odrzucenie, bez
   obcinania.
4. Wynik: `Settled(rewards)`, `AlreadySettled(rewards)`, `Rejected(reason)`
   (m.in. `LEVEL_ALREADY_COMPLETED`, `OVERFLOW`, `NOT_A_WIN`).
5. Integracja z `CampaignSystem.completeLevel`: przy `BLUE_WIN` jedna
   transformacja ustawia `completedLevels`, odblokowuje następny poziom,
   stosuje milestone’y, nalicza złoto i zapisuje receipt.
6. Polityka dla poziomów już ukończonych bez receipt: **brak
   wstecznej wypłaty** (rekomendacja; do potwierdzenia).

Testy (jak `CampaignSystemTest`, `FakeRepository`): wygrana pierwszy raz,
powtórka idempotentna, porażka bez zmian, overflow, atomowość (błąd zapisu nie
zmienia żadnej części), zachowanie milestone’ów.

Akceptacja: żaden częściowy zapis nie jest możliwy w teście fake’a.

## Faza 7 — `LEVEL_ALREADY_COMPLETED`

Cel: poziom można wygrać raz; po porażce retry nadal działa.

Pliki: `CampaignSystem.kt` (`selectLevel`), `CampaignSelectionRejection`,
`CampaignAwareLoadoutProgressionPort`, `CampaignCompletionPresenter`,
`CampaignScreen.kt` (~l. 232), `loadoutRewards(level)`; testy
`CampaignSystemTest`, `CampaignViewModelTest`.

Kroki: dodać wartość odrzucenia, obsłużyć każdy egzaustywny `when`, ustalić
`loadoutRewards` dla ukończonych poziomów (otwarta decyzja), dodać string
dla UI.

Akceptacja: kompilator nie zgłasza brakujących gałęzi; CampaignScreen
pokazuje jawny powód.

## Faza 8 — adapter DataStore i wiring (`android-app`)

Cel: jedna transakcja `updateData` na wspólnym `DataStore<Preferences>`
obejmuje klucze `profile.*`, `campaign.*` i nowe `reward.*`.

Pliki: `persistence/DataStoreCampaignProgressRepository.kt`,
`DataStorePlayerProgressionRepository.kt`, nowy wspólny plik kodeków,
nowy `DataStoreRewardSettlementAdapter`, `MainActivity.kt` (~l. 36–70),
`DataStoreRepositoriesTest`.

Kroki:

1. Wyodrębnić kodeki odczytu/zapisu kluczy z istniejących adapterów, aby nie
   duplikować (istniejące adaptery używają ich bez zmiany formatu).
2. Nowe klucze: `reward.schema_version`, `reward.receipts`. Wersja schematu i
   jawny błąd przy nieznanej wersji.
3. Adapter wykonuje transformację w jednym `edit`/`updateData`.
4. W `MainActivity` zbudować adapter z tego samego DataStore i wstrzyknąć do
   use case’a; bez nowej logiki w Activity.

Testy: atomowość w jednej transakcji, idempotentny retry, zgodność ze starymi
danymi (brak kluczy `reward.*` → pusty stan), uszkodzone dane → jawny błąd.

Akceptacja: obecne testy repozytoriów zielone, brak zmiany formatu starych
kluczy.

## Faza 9 — przepływ Android i popup wyniku

Cel: UI pokazuje rozbicie nagrody i stan zapisu; nie liczy gameplayu.

Pliki: `ui/MatchViewModel.kt` (~l. 255–335, `forwardCampaignCompletionEvents`),
`ui/MatchScreenController.kt`, `screens/match/CampaignCompletionPresenter.kt`,
`ui/MatchResultPopupState.kt`, `ui/MatchResultPopupView.kt`,
`MatchResultPopupStateMapper`, `strings.xml`, `MainActivity.kt` (~l. 170),
`MatchScreen.kt`.

Kroki:

1. `onMatchFinished` przekazuje także wynik trackera (read model) do
   presentera, który wywołuje use case rozliczenia; demo mode nadal
   pomija.
2. Rozszerzyć `MatchResultPopupState` o niemutowalny model nagrody (lista
   pozycji + suma) i o stan „zapis nagrody nieudany”; mapper tylko mapuje.
3. `MatchResultPopupView`: sekcja z rozbiciem w istniejącym panelu (bez
   nowych assetów); zachować akcje `NEXT_LEVEL` / `EXIT_TO_CAMPAIGN` /
   `RETRY` / `BACK_TO_DASHBOARD`.
4. Jawny retry zapisu, bez cichego pominięcia.

Testy: `MatchResultPopupStateMapperTest` (wygrana z nagrodą, wygrana ponownie,
porażka, błąd zapisu), `MatchViewModelCampaignCompletionTest` (jedno
przekazanie, demo), test braku mutacji domeny z UI.

Akceptacja: dedup (generation, matchId, eventId) zachowany; UI nie ma kopii
stanu trackera.

## Faza 10 — dokumentacja i bramka walidacji

- Zaktualizować: `docs/systems/campaign-system.md`,
  `docs/systems/player-progression-system.md`, `docs/project/knowledge.md`,
  `docs/reference/GLOSSARY.md`, `docs/architecture/project-map.md` (jeśli
  zmienia się mapa przepływów); `reward-system.md` zmienić ze statusu
  „plan” na „zaimplementowano” dopiero po wdrożeniu.
- Bramka: `./gradlew test assembleDebug --quiet`, `git diff --check`,
  `git status --short`.
- Przegląd: SRP, kierunek zależności, niemutowalne snapshoty, brak Androida
  w core, brak gameplayu w UI, brak równoległego katalogu nagród.

## Ryzyka

| Ryzyko | Mitigacja |
|---|---|
| Atomowość na wspólnym DataStore nie działa przy oddzielnych adapterach | Jeden `updateData` w nowym adapterze; test atomowości (faza 8) |
| Istniejące ukończone poziomy bez receipt | Brak wstecznej wypłaty; decyzja do potwierdzenia |
| Nowy podtyp `MatchEvent` łamie `when` w Androidzie | Kompilacja w fazie 1; poprawki w `RenderStateMapper` |
| Utrata eventów między komendami | Hook w `MatchSession`, nie `consumeEvents` (faza 5) |
| Mecz kończy się z pociskami w locie | Jawne zamknięcie strzałów w trackerze po `MatchFinished` |
| Overflow `Int` salda | Jawne odrzucenie w use case |
| Rozjazd replayu | Tracker zasilany z tego samego hooka; test replayu |

## Blokery decyzyjne (przed fazą 3 w wersji produkcyjnej)

1. Kwoty złota: wygrana, każde osiągnięcie, wzrost serii trafień w fortecę.
2. Próg „szybkiej wygranej” (czas) i jego zależność od poziomu.
3. Zatwierdzenie rozszerzeń kontraktu z fazy 1.
4. Polityka dla już ukończonych poziomów bez receipt.
5. `loadoutRewards(level)` dla ukończonych poziomów.
6. Źródło authoringu (założono rejestr Kotlin).
7. Czy osiągnięcia są widoczne w trakcie meczu (poza zakresem planu).
8. Czy Odwet i Ostatni strzał wchodzą do zakresu (obecnie nie).

Poza zakresem: Pogromca, Wsparcie oddziałów, nagrody niezłote, sklep.
