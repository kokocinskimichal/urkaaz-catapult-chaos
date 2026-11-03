# Prompt implementacyjny: globalny mnożnik tempa rozgrywki

Zaimplementuj
[`gameplay-tempo-multiplier-plan.md`](gameplay-tempo-multiplier-plan.md).
Plan jest źródłem decyzji o zachowaniu. Przeczytaj go w całości przed zmianami.
Ten prompt nie zleca innych zmian balansu, nowych mechanik ani redesignu UI.

Przed edycją sprawdź branch i `git status --short`. Zachowaj wszystkie
zastane zmiany worktree; nie nadpisuj plików i nie formatuj hurtowo zakresu
poza zadaniem. Nie commituj ani nie pushuj bez jawnego polecenia użytkownika.

## Potwierdzone decyzje

- Jeden globalny mnożnik balansu steruje ruchem jednostek oraz czasem lotu
  pocisków katapult i ich fragmentów. Nie dodawaj osobnego mnożnika pocisków.
- Reużyj istniejącego pola ruchu jako jedynego globalnego slotu, nazywając je
  `runtime.combat.gameplaySpeedMultiplier`.
- Domyślna wartość po normalizacji to `1.2`; dopuszczalny zakres to `1.0–1.5`
  z krokiem `0.05`.
- Mnożnik ma skracać czas lotu, zachowując przestrzenny tor i zasięg przy tych
  samych warunkach startowych. Nie zwiększaj `initialSpeedMultiplier`
  amunicji.
- Nie przyspieszaj osobnego, 500 ms overlayu wizualnego lotu bomby Sappera.
- Nie zmieniaj zegara meczu, przeładowań, cooldownów, obrażeń, zdrowia,
  kosztów, fal AI, czasu trwania statusów, warunków wygranej/przegranej ani
  krzywej trudności per poziom.

## Audyt przed zmianami

Przeczytaj:

```text
AGENTS.md
docs/architecture/architecture.md
docs/architecture/implementation-rules.md
docs/architecture/project-map.md
docs/architecture/studio-architecture.md
docs/systems/gameplay-tempo-multiplier-plan.md
docs/architecture/studio-balance-form-implementation-plan.md
docs/architecture/studio-balance-form-implementation-prompt.md
```

Prześledź aktualne kontrakty oraz testy w:

```text
game-domain/src/main/resources/balance/game-balance.json
game-domain/src/main/kotlin/com/urkaaaz/domain/RuntimeBalance.kt
game-domain/src/main/kotlin/com/urkaaaz/domain/Combatants.kt
game-domain/src/main/kotlin/com/urkaaaz/domain/AmmunitionDefinitions.kt
game-application/src/main/kotlin/com/urkaaaz/application/balance/GameBalanceRepository.kt
game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt
game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt
game-simulation/src/main/kotlin/com/urkaaaz/simulation/UnitMovementSpeed.kt
game-simulation/src/main/kotlin/com/urkaaaz/simulation/BossSystem.kt
android-app/src/main/java/com/urkaaaz/ui/RenderStateMapper.kt
android-app/src/main/java/com/urkaaaz/ui/BattlefieldView.kt
android-app/src/main/java/com/urkaaaz/ui/SapperBombFlightClock.kt
studio/server/src/services/balanceCatalog.ts
studio/server/src/services/balanceCatalog.test.ts
studio/web/src/tabs/balance/model.ts
studio/web/src/tabs/balance/BalanceForm.tsx
```

## Balance schema i normalizacja ruchu

1. Obecny balance jest schema 4. Podnieś go do schema 5 i zmień
   `balanceRevision` na kolejną rewizję. Zaktualizuj typowany model domeny,
   DTO/loader aplikacyjny, model formularza, walidator servera i testy w
   jednym spójnym kroku.
2. Reużyj dotychczasowy slot `runtime.combat.unitMovementSpeedMultiplier`,
   zmieniając jego nazwę na `runtime.combat.gameplaySpeedMultiplier`.
   Nie pozostawiaj starego pola jako drugiego edytowalnego mnożnika i nie
   akceptuj schema 4 przez cichy fallback.
3. Przenieś dotychczasowy czynnik `2.0` do bazowych skal ruchu, zachowując
   obecne tempo przy mnożniku `1.0`:
   - `unitMovementSpeedScale`: `0.45` → `0.90`;
   - `garrisonMovementSpeedScale`: `0.22` → `0.44`;
   - `gameplaySpeedMultiplier`: domyślnie `1.2`.
4. W Studio pokaż jedno pole numeryczne w **Balance → Runtime → Combat**,
   opisane jako **Gameplay speed multiplier** i z jednostką `×`. Użyj
   uzgodnionego zakresu `1.0–1.5`, kroku `0.05` i wartości domyślnej `1.2`.
5. Zachowaj ścisłą walidację, `balanceRevision`, hash-guard, atomowy zapis
   oraz brak hot reloadu. Zmiana zapisanej wartości obowiązuje nowe sesje po
   rebuildzie/restarcie zgodnie z obecnym przepływem Studio.

## Ruch jednostek i mobilego bossa

- Stosuj mnożnik dokładnie raz do końcowego kroku ruchu, po bazowej skali
  jednostki/garnizonu i statusach HASTE/SLOWED. Zachowaj multiplikatywne
  działanie kontaktowego spowolnienia Sappera.
- Zastosuj go do ruchu wysłanych jednostek, garnizonu i mobilego bossa.
  Boss `STATIC` pozostaje nieruchomy; enrage nadal działa jako lokalny
  mnożnik prędkości.
- Nie zmieniaj celów ruchu, slotów ani rozstawu formacji.
- Synchronizuj animację lokomocji z nowym tempem ruchu. Nie przyspieszaj
  animacji ataków, śmierci, HUD-u ani zegarów.

## Pociski: krótszy czas, ten sam tor

- Nie zmieniaj per-ammo `initialSpeedMultiplier`. Dla każdego kroku lotu
  oblicz efektywny czas pocisku jako `simulationDelta × gameplaySpeedMultiplier`.
- W tę samą skalę czasu włącz całe kinematics pocisku: grawitację, wiatr,
  drag, `ageSeconds`, wyszukiwanie apogeum, fragmentację i test kolizji.
  Pocisk ma przejść po tej samej przestrzennej trajektorii i trafić w to samo
  miejsce, ale zrobić to szybciej.
- Przy `1.2` czas lotu przy stałych warunkach wynosi w przybliżeniu
  `1 / 1.2` dotychczasowego czasu. Zegar symulacji, zegar meczu i czasy
  reload/cooldown nadal używają rzeczywistego kroku.
- Zaktualizuj obsługę `timeToApex`, pozostałego kroku po fragmencie,
  `terrainImpactPosition` oraz wszystkich punktów integracji tak, by każdy
  pocisk i każdy odłamek otrzymały identyczną skalę.
- Zwiększony efektywny krok nie może powodować przenikania przez teren,
  fortyfikację ani Runic Bastion. Użyj adaptacyjnych substeps, zachowując
  dotychczasową maksymalną precyzję kolizji przy mnożniku `1.5`.
- Nie zamrażaj ani nie zmieniaj harmonogramu wiatru. Dla stałego wiatru tor
  ma być zgodny; przy zmianie wiatru w czasie lotu skrócenie ekspozycji może
  wpłynąć na dryf.
- Androidowy statyczny preview trajektorii ma nadal zgadzać się geometrycznie
  z symulacją. `SapperBombFlightClock` pozostaje bez zmian.

## Zakres architektury

- `game-domain` przechowuje tylko typowaną konfigurację balansu i jej
  niezmienniki; nie zna Androida, Studio ani rendererów.
- `game-application` ładuje jeden niemutowalny `GameBalanceConfig` dla sesji.
- `game-simulation` jest jedynym właścicielem ruchu, projectile clock,
  fragmentacji i kolizji.
- AI i BLUE używają tej samej deterministycznej symulacji; nie dodawaj osobnej
  logiki prędkości w AI ani Android UI.
- Studio edytuje to samo kanoniczne `game-balance.json` przez istniejący API;
  nie twórz drugiego endpointu, pliku balansu ani per-ammo override.

## Testy i kryteria akceptacji

- schema 5, migracja wartości, serializacja/deserializacja, ścisła walidacja
  zakresu oraz zapis przez istniejący balance API/form;
- przy mnożniku `1.0` ruch jednostek i garnizonu zachowuje dotychczasowe
  tempo; przy `1.2` dystans w stałym czasie rośnie o 20%, z HASTE/SLOWED,
  kontaktem i garnizonem zachowującymi dotychczasowe proporcje;
- mobile boss porusza się zgodnie z globalnym mnożnikiem, static boss nie;
- dla stałego wiatru i identycznego seed/aim/amunicji tor, apogeum, punkty
  fragmentacji i miejsce trafienia są zgodne przy `1.0` i `1.2`;
- czas lotu przy `1.2` jest krótszy, a przy `1.5` nie występuje tunneling;
- AI, fragmenty i wszystkie typy amunicji korzystają z jednego mnożnika;
- czas meczu, ataki, reload, cooldowny, obrażenia, efekty/statusy i warunki
  zwycięstwa pozostają niezmienione;
- preview Androida pozostaje zgodny z symulowanym torem.

Uruchom:

```bash
./gradlew test assembleDebug --quiet
cd studio
npm test
npm run lint
npm run build
cd ..
git diff --check
git status --short
```

Nie commituj ani nie pushuj.
