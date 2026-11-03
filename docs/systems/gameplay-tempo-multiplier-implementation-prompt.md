# Prompt implementacyjny: globalny mnożnik tempa rozgrywki

Zaimplementuj
[`gameplay-tempo-multiplier-plan.md`](gameplay-tempo-multiplier-plan.md).
Plan jest źródłem decyzji o zachowaniu. Przeczytaj go w całości przed zmianami.
Ten prompt nie zleca innych zmian balansu, nowych mechanik ani redesignu UI.

Przed edycją sprawdź branch i `git status --short`. Zachowaj wszystkie
zastane zmiany worktree;
nie nadpisuj plików i nie formatuj hurtowo zakresu poza zadaniem.
Nie commituj ani nie pushuj bez jawnego polecenia użytkownika.

## Potwierdzone decyzje

- Jeden globalny mnożnik w balansie ma sterować ruchem jednostek oraz
  czasem lotu pocisków katapult i ich fragmentów. Nie dodawaj osobnego
  per-ammo mnożnika tempa.
- Mnożnik ma przyspieszać lot, ale zachować przestrzenny tor i zasięg przy
  tych samych warunkach startowych. Nie zwiększaj `initialSpeedMultiplier`
  amunicji.
- Wartość domyślna po normalizacji to `1.2`, około 20% szybciej niż obecny
  ruch jednostek.
- Nie przyspieszaj osobnego, 500 ms overlayu wizualnego lotu bomby Sappera.
- Nie zmieniaj zegara meczu, przeładowań, cooldownów, obrażeń, zdrowia,
  kosztów, fal AI, czasu trwania statusów, warunków wygranej/przegranej ani
  trudności per poziom.

## Audyt przed zmianami

Sprawdź `git status --short` i rozdziel istniejące zmiany użytkownika od
zakresu tej implementacji. Przeczytaj:

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

## Kontrakt balansu i migracja

1. Reużyj istniejącego pola `runtime.combat.unitMovementSpeedMultiplier` jako
   jedynego globalnego slotu. Zmień jego nazwę na
   `runtime.combat.gameplaySpeedMultiplier`, aby nazwa opisywała także lot
   pocisków; nie dodawaj drugiego mnożnika.
2. Ponieważ walidator servera i formularz wymagają dokładnego zestawu kluczy,
   podnieś balance schema z 4 do 5. Zaktualizuj `balanceRevision`, typowane DTO,
   parser domenowy, fixture, walidację servera i model/formularz Studio razem.
   Nie akceptuj schema 4 przez cichy fallback.
3. Zachowaj obecne tempo jako punkt `1.0`, przenosząc stary czynnik `2.0` do
   bazowych skal ruchu: `unitMovementSpeedScale` z `0.45` na `0.90`, a
   `garrisonMovementSpeedScale` z `0.22` na `0.44`. Przy `1.2` obie kategorie
   mają być około 20% szybsze od obecnego zachowania.
4. W Studio pokaż jedno pole numeryczne w **Balance → Runtime → Combat**,
   opisane jako **Gameplay speed multiplier** i z jednostką `×`. Proponowany
   zakres to `1.0–1.5`, krok `0.05`, domyślnie `1.2`. Górny limit jest
   propozycją planu: jeśli wymaga zmiany, zapytaj użytkownika przed
   implementacją zamiast samodzielnie wybierać inną granicę.
5. Zachowaj strict validation, hash-guard, atomowy zapis i zasadę braku hot
   reloadu. Zmiana zapisanej wartości obowiązuje nowe sesje po rebuildzie/
   restarcie zgodnie z obecnym przepływem Studio.

## Ruch jednostek i bossów

- Zastosuj mnożnik dokładnie raz do końcowego kroku ruchu jednostki, po
  istniejących bazowych skalach i statusach HASTE/SLOWED. Zachowaj
  multiplikatywne działanie kontaktowego spowolnienia Sappera.
- Zastosuj ten sam mnożnik do prędkości garnizonu; nie zmieniaj slotów,
  rozstawu formacji ani celów ruchu.
- Ruch mobilego bossa również korzysta z globalnego mnożnika. `STATIC` boss
  pozostaje nieruchomy; enrage nadal działa jako lokalny mnożnik prędkości.
- Nie skaluj attack windupów, cooldownów, status-duration timerów, animatorów
  ataku/śmierci, zegara meczu ani AI decision interval.
- Utrzymaj synchronizację animacji lokomocji z nową prędkością rzeczywistą.
  Nie przyspieszaj animacji innych stanów.

## Pociski: krótszy czas, ten sam tor

- Globalny mnożnik skaluje efektywny czas integracji pocisku, a nie
  `ammunition[].flight.initialSpeedMultiplier`. W tym samym czasie skaluje
  integrację grawitacji, wiatru, drag, wieku lotu, czasu do apogeum i
  fragmentacji, tak aby dla stałego wiatru zachować tę samą geometrię toru.
- Przy `1.2` lot ma trwać w przybliżeniu `1 / 1.2` dotychczasowego czasu.
  Nie zmieniaj zasięgu, celu ani punktu apogeum dla identycznego startu i
  niezmiennego wiatru.
- Stosuj tę samą logikę do wszystkich symulowanych pocisków katapult i
  odłamków. AI i BLUE muszą używać tego samego mechanizmu.
- Przeliczaj `timeToApex` oraz pozostały krok po fragmentacji w tej samej
  skali czasu pocisku. Simulation/match clock pozostaje w czasie rzeczywistym;
  zmieniają się czasy zdarzeń lotu, nie globalne timery meczu.
- Zachowaj adaptacyjne collision substeps. Przy maksymalnym mnożniku nie może
  dojść do przenikania przez teren, fortyfikację ani Runic Bastion.
- Wiatr pozostaje dynamicznym stanem meczu i nie jest zamrażany przy wystrzale.
  Równość toru dotyczy tych samych warunków wiatru; lot trwający krócej może
  mieć mniejszą ekspozycję na późniejszą zmianę wiatru.
- Androidowy statyczny preview trajektorii ma nadal zgadzać się geometrycznie
  z symulacją. SapperBombFlightClock pozostaje poza zakresem.

## Testy i kryteria akceptacji

Dodaj/zmień testy tak, aby sprawdzały zachowanie, nie prywatną implementację:

- schema 5, migrację/normalizację wartości, walidację granic mnożnika i round
  trip przez `GameBalanceRepository` oraz Studio balance API/form;
- przy mnożniku `1.0` unit/garrison movement zachowuje dotychczasowe tempo;
  przy `1.2` dystans w stałym czasie rośnie o 20%, z HASTE/SLOWED i
  contact-speed modifier zachowującymi dotychczasowe proporcje;
- mobile boss porusza się zgodnie z mnożnikiem, static boss nie;
- dla stałego wiatru i identycznego seed/aim/amunicji tor, apogeum, punkty
  fragmentacji i miejsce trafienia są zgodne przy `1.0` i `1.2`, a czas lotu
  jest krótszy zgodnie z mnożnikiem;
- wszystkie ammo typy i fragmenty zachowują poprawne kolizje przy wartości
  maksymalnej; nie ma tunnelingu;
- czas meczu, reload, cooldowny, obrażenia, efekty/statusy i warunki
  zwycięstwa pozostają niezmienione przez globalny mnożnik;
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
