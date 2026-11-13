# Plan: globalny mnożnik tempa rozgrywki

**Status: zaimplementowano.** Mnożnik dodano w balance schema 5; bieżący
kontrakt używa schema 7, `gameplaySpeedMultiplier: 1.2` i znormalizowanych
skal ruchu.

Prompt implementacyjny:
[`gameplay-tempo-multiplier-implementation-prompt.md`](gameplay-tempo-multiplier-implementation-prompt.md).

## Cel

Dodać jeden parametr balansu, którym można wspólnie stroić prędkość ruchu
jednostek i czas lotu pocisków katapult. Zwiększenie wartości ma przyspieszać
oba rodzaje ruchu bez zmiany spatialnego toru i zasięgu pocisku.

To nie jest globalne przyspieszenie zegara meczu. Mnożnik nie zmienia czasu
trwania meczu, przeładowań, cooldownów, obrażeń, zdrowia, kosztów ani długości
statusów.

## Decyzje uzgodnione

- Wykorzystać istniejący slot
  `runtime.combat.unitMovementSpeedMultiplier` jako jeden globalny mnożnik,
  zmieniając jego nazwę na `runtime.combat.gameplaySpeedMultiplier` zamiast
  dodawać niezależny mnożnik pocisków.
- Domyślne tempo po normalizacji ma wynosić **1.2×**, czyli około 20% szybciej
  niż obecne zachowanie.
- Przyspieszenie pocisków ma skrócić czas lotu przy zachowaniu tego samego
  toru przestrzennego i zasięgu dla tych samych warunków startowych.
- Zakres pocisków obejmuje pociski katapult i ich fragmenty. Osobny, 500 ms
  wizualny lot bomby Sappera pozostaje poza zakresem.

## Stan przed implementacją

- `game-balance.json` jest w schema 4. `runtime.combat` zawiera
  `unitMovementSpeedMultiplier: 2`, `unitMovementSpeedScale: 0.45` oraz
  `garrisonMovementSpeedScale: 0.22`.
- `DeterministicMatchSimulation` skaluje ruch zwykłych jednostek przez
  `unitMovementSpeedMultiplier`; odrębne skale regulują ruch jednostek
  wysłanych i pozostających w garnizonie. HASTE, SLOWED i mnożnik kontaktu
  są stosowane dodatkowo.
- Lot pocisku zależy od `ammunition[].flight.initialSpeedMultiplier`,
  grawitacji, wiatru, drag i czasu integracji. Podgląd trajektorii Androida
  odtwarza tę samą geometrię na podstawie parametrów z `RenderState`.
- Plik balansu jest ładowany raz dla sesji. Zmiana zapisanej wartości działa
  po ponownym uruchomieniu aplikacji; nie ma hot reloadu.

## Wdrożenie

- `runtime.combat.gameplaySpeedMultiplier` ma zakres 1.0–1.5 i domyślną
  wartość 1.2; skale ruchu wysłanych jednostek i garnizonu wynoszą odpowiednio
  0.90 i 0.44.
- Efektywny czas pocisku jest mnożony w symulacji, z adaptacyjnymi substeps,
  zachowującymi precyzję kolizji dla pocisków i fragmentów.
- Mobilny boss i animacje lokomocji korzystają z tego samego mnożnika; match,
  reload, cooldown i status clocks nie są przyspieszane.

## Zaimplementowany kontrakt balansu

Zachować jeden parametr, ale nazwać go zgodnie z szerszą odpowiedzialnością:
`runtime.combat.gameplaySpeedMultiplier`. Jest to migracja istniejącego pola,
a nie drugi, niezależny mnożnik. Balance podniesiono z schema 4 do schema 5,
ponieważ serwerowy walidator i formularz Balance ściśle sprawdzają klucze.

Przenieść obecny czynnik ruchu jednostek do bazowych skal, aby mnożnik równy
1.0 zachował aktualne tempo:

| Konfiguracja | Obecnie | Po normalizacji | Przy mnożniku 1.2 |
|---|---:|---:|---:|
| Ruch wysłanych jednostek (`unitMovementSpeedScale`) | 0.45 × 2.0 | 0.90 × 1.0 | 0.90 × 1.2 |
| Ruch garnizonu (`garrisonMovementSpeedScale`) | 0.22 × 2.0 | 0.44 × 1.0 | 0.44 × 1.2 |

Wartości efektów lokalnych pozostają osobne: HASTE/SLOWED, kontakt Sappera,
profile prędkości jednostek i enrage mobilego bossa nadal zachowują swoje
względne działanie. Globalny mnożnik stosuje się raz, na końcu obliczenia
prędkości ruchu. Static bosses nie są przesuwane.

Wartość początkowa **1.2** oraz zakres **1.0–1.5** z krokiem **0.05** są
uzgodnione. Studio może użyć istniejącego pola numerycznego w **Balance →
Runtime → Combat**, z etykietą **Gameplay speed multiplier** i jednostką `×`.

## Własność i przepływ danych

```text
game-balance.json
    → GameBalanceRepository / GameBalanceConfig
    → MatchSession / SimulationConfig
    → DeterministicMatchSimulation
       ├── ruch jednostek (w tym garnizon i mobile boss)
       └── czas integracji pocisków i fragmentów
```

- Źródłem wartości pozostaje `game-domain/src/main/resources/balance/game-balance.json`.
- Zaktualizować `RuntimeBalance.CombatBalance`, DTO/loader w
  `GameBalanceRepository`, walidator `studio/server` oraz model i formularz
  `studio/web/src/tabs/balance`.
- Podnieść schema 4 do 5, zaktualizować `balanceRevision` i wszystkie fixture
  oraz testy. Walidator ma jawnie akceptować schema 5 i odrzucać nieznane
  wersje bez fallbacku.
- Nie dodawać parametru do AI, Androida ani poszczególnych profili amunicji.
  AI i gracz korzystają z tej samej symulacji i tego samego balansu.

## Zasada przyspieszenia lotu pocisków

Nie zmieniać per-ammo `initialSpeedMultiplier` ani nie zwiększać tylko
prędkości początkowej, bo zmieniłoby to zasięg i łuk. Zamiast tego mnożnik
skaluje **efektywny czas integracji lotu**. Dla każdej klatki symulacji
`projectileDelta` wynosi `simulationDelta × gameplaySpeedMultiplier`.
Grawitacja, wiatr, drag, wiek lotu i obliczenie czasu do apogeum używają tego
samego czasu pociskowego, więc przy stałym wietrze zmienia się czas dotarcia,
nie geometria trajektorii. Przy wartości 1.2 lot trwa w przybliżeniu
`1 / 1.2` poprzedniego czasu.

Uwzględnić tę samą skalę w obsłudze fragmentacji i terrain impact. Zwiększenie
efektywnego kroku nie może powodować przenikania przez teren ani fortyfikacje:
wewnętrzne substeps i wyszukiwanie kolizji powinny zachować obecną precyzję
przy maksymalnym dopuszczonym mnożniku. Zegar symulacji, zegar meczu oraz
cooldowny nadal używają rzeczywistego kroku symulacji.

Wiatr nadal jest zewnętrznym stanem meczu: tor pozostaje taki sam przy
stałym wietrze, ale skrócenie lotu może zmienić ekspozycję na późniejszą
zmianę wiatru. Nie zamrażać wiatru przy starcie pocisku w ramach tej zmiany.
Statyczny podgląd trajektorii powinien nadal pokazywać ten sam tor dla bieżącego
wiatru.

## Zakres zmian

1. **Balance schema i Studio**
   - przemianować istniejący mnożnik na `gameplaySpeedMultiplier`;
   - znormalizować skale ruchu z 0.45/0.22 do 0.90/0.44;
   - dodać ścisłą walidację, limity, formularz Balance i aktualizację rewizji.
2. **Ruch**
   - zastosować globalny mnożnik do ruchu wysłanych jednostek, garnizonu i
     mobilego bossa;
   - zachować multiplikatywną kolejność statusów, spowolnienia kontaktowego
     i enrage, nie zmieniając ich czasów trwania.
3. **Balistyka**
   - skalować czas lotu pocisków i fragmentów z adaptacyjnymi substeps;
   - pozostawić `initialSpeedMultiplier`, parametry wiatru/grawitacji,
     obrażenia i trajektorię podglądu niezmienione;
   - nie zmieniać osobnego overlayu lotu bomby Sappera.
4. **Integracja i dokumentacja**
   - zaktualizować testy kontraktu, symulacji, balansu i formularza;
   - opisać nowy parametr oraz schema 5 w wiedzy projektu i dokumentacji Studio.

## Poza zakresem

- zmiana czasu meczu, cooldownów, przeładowań, czasu trwania zaklęć/statusów
  lub animacji ataków;
- zmiana obrażeń, zdrowia, zasięgów, kosztów, fal AI albo krzywej trudności;
- inne parametry prędkości per jednostka, biom lub amunicja;
- zmiana geometrii map, kamery, synchronizacji Sapper bomb overlay albo
  początkowych parametrów trajektorii poszczególnych amunicji.

## Wpływ na rozgrywkę

- Pętla deploy → aim → fire oraz warunki zwycięstwa/porażki pozostają bez zmian.
- Mnożnik działa tak samo na wszystkich poziomach; nie tworzy osobnej krzywej
  trudności per rozdział ani dla AI.
- Prędkość reakcji na wejście użytkownika, cooldowny i czas meczu pozostają
  bez zmian; zwiększa się wyłącznie dystans ruchu na jednostkę czasu.
- Ruch lokomocyjny sprite'ów należy zsynchronizować z ruchem jednostek, ale
  nie przyspieszać animacji ataków, śmierci, HUD-u ani zegarów.
- Przy proponowanym maksimum 1.5 sprawdzić czytelność ruchu i trajektorii na
  docelowych rozdzielczościach oraz brak przenikania pocisków przez teren.

## Testy i kryteria akceptacji

- mnożnik 1.0 po normalizacji zachowuje dotychczasowe prędkości ruchu;
- mnożnik 1.2 zwiększa o około 20% dystans jednostki i mobilego bossa w tym
  samym czasie symulacji, także przy HASTE, SLOWED, garnizonie i kontakcie;
- ten sam seed, aim, amunicja i stały wiatr dają ten sam przestrzenny tor,
  punkt apogeum, punkt fragmentacji i miejsce trafienia dla 1.0 i 1.2;
- czas lotu przy 1.2 jest krótszy, a adaptacyjne substeps nie pomijają kolizji;
- pociski player/AI oraz fragmenty używają tego samego mnożnika;
- czas meczu, ataki, reload, cooldowny, obrażenia i statusy nie zmieniają
  swoich czasów ani wartości;
- `RenderState` i podgląd trajektorii nadal zgadzają się z symulacją;
- schema 5 ładuje się z pliku balansu, Studio waliduje i zapisuje pole przez
  istniejący hash-guard; nie ma hot reloadu aktywnej sesji;
- przy mnożniku maksymalnym ruch i pociski pozostają czytelne na ekranie
  battlefield, a wejście aim/deploy nadal reaguje natychmiast.

Walidacja implementacji:

```bash
./gradlew test assembleDebug --quiet
cd studio
npm test && npm run lint && npm run build
```
