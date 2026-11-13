# Prompt implementacji per-level HP fortec kampanii

> Powiązany plan i decyzje: [`campaign-fortress-health-plan.md`](campaign-fortress-health-plan.md).
> Status: zaimplementowano. Prompt zachowano jako zapis zakresu i kryteriów.
> Pierwotne zadanie wymagało wykonania implementacji i testów bez commita/pusha.

## Zadanie

Dodaj osobne maksymalne HP fortec BLUE i RED dla każdego poziomu kampanii 1–100.
Umożliwiaj edycję tych danych w Urkaaaz Studio, w istniejącej zakładce
**Balance → Runtime → Campaign**.

## Potwierdzone wymagania

- BLUE i RED mają niezależne wartości HP na każdym poziomie.
- Standard dla nieustawionej strony pozostaje
  `runtime.fortresses.standardMaximumHealth`.
- Obecne standardowe HP 500 nie może się zmienić.
- Demo zachowuje 5000 HP dla fortecy BLUE i standardowe 500 dla RED.
- Per-level HP jest gameplay configuration, nie częścią mapy, chapter map
  layoutu, Android View ani assetu.
- Nie zmieniaj zakresów/identyfikatorów poziomów, encounterów bossów,
  geometrii map, uszkodzeń, balansu ani warunków zwycięstwa.

## Analiza przed edycją

Sprawdź `git status --short` i zachowaj wszystkie istniejące zmiany użytkownika.
Przeczytaj obowiązujące `AGENTS.md` i `docs/architecture/architecture.md`,
a następnie aktualne implementacje:

```text
game-domain/src/main/kotlin/com/urkaaaz/domain/RuntimeBalance.kt
game-domain/src/main/resources/balance/game-balance.json
game-application/src/main/kotlin/com/urkaaaz/application/balance/GameBalanceRepository.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelRegistry.kt
game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchScenario.kt
game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt
game-application/src/main/kotlin/com/urkaaaz/application/MatchReplay.kt
game-application/src/main/kotlin/com/urkaaaz/application/LocalMatchGateway.kt
game-simulation/src/main/kotlin/com/urkaaaz/simulation/SimulationConfig.kt
studio/web/src/tabs/balance/model.ts
studio/web/src/tabs/balance/BalanceForm.tsx
studio/web/src/tabs/balance/BalanceTab.tsx
studio/server/src/services/balanceCatalog.ts
```

Prześledź wszystkie call sites tworzące campaign scenario, demo/test match,
gateway i replay. Zwróć uwagę na już istniejące argumenty
`blueFortressMaxHealth` i `redFortressMaxHealth`; nie dodawaj konkurencyjnej
ścieżki konfiguracji.

## Model i defaulty

Rozszerz typowany `CampaignBalance` w istniejącym
`GameBalanceConfig.runtime.campaign` o rzadkie per-level overrides. Zalecany
kształt JSON:

```json
"fortressHealthOverrides": [
  {
    "level": 31,
    "blueMaximumHealth": 700,
    "redMaximumHealth": 900
  },
  {
    "level": 32,
    "redMaximumHealth": 650
  }
]
```

Jest to lista wpisów tylko dla poziomów z override. Wpis może ustawić jedną
stronę; brak drugiego pola oznacza dziedziczenie globalnego standardu. Pusta
lista zachowuje obecne zachowanie dla wszystkich poziomów.

Możesz dopasować nazwy do aktualnych konwencji DTO, ale zachowaj wszystkie
semantyczne wymagania:

- globalny poziom kampanii, nie numer lokalny rozdziału;
- niezależne pola BLUE/RED;
- brak wpisu lub brak pola teamu = dziedziczenie standardowego HP;
- brak duplikatów poziomu;
- lista nie wymaga rekordów dla wszystkich stu poziomów.

Podnieś balance `schemaVersion` zgodnie z repozytoryjną konwencją i zwiększ
`balanceRevision`. Zaktualizuj fixture oraz wszystkie testy oczekujące
revision. Nie zmieniaj globalnych wartości HP ani Demo HP.

## Przepływ danych i priorytety

1. Strict balance repository dekoduje oraz waliduje per-level overrides do
   typowanego `CampaignBalance`.
2. `CampaignLevelDefinition.forLevel` pobiera wartości nadpisane dla globalnego
   poziomu; nie zapisuje w domenie/UI stałej kopii wartości 500.
3. Dodaj/wykorzystaj niemutowalny kontrakt do przeniesienia opcjonalnych
   per-team overrides w `MatchScenario`.
4. `MatchSession` rozwiązuje efektywne HP do konkretnych dodatnich wartości,
   po czym przekazuje je do istniejących `SimulationConfig` fields.
5. Zachowaj jawne, dotychczasowe nadpisania hosta/demo/testów. Ustal i
   udokumentuj jedną precedencję bez rozgałęzień w UI:

   ```text
   explicit host/demo override
     > campaign scenario per-level override
     > runtime.fortresses.standardMaximumHealth
   ```

6. Demo bez campaign-level override nadal używa 5000 BLUE / 500 RED.
   Weryfikuj, że Test Mode korzysta z konfiguracji wybranego campaign level,
   a nie z przypadkowej demo override.
7. `MatchReplayConfiguration` zapisuje efektywne HP BLUE i RED. Replay ma
   odtworzyć dokładne maksymalne HP obu fortec; zachowaj istniejące balance
   revision/content-hash guard.
8. Snapshot już udostępnia health/maxHealth. Pozwól istniejącemu HUD-owi
   wyświetlić nowe maksimum bez dodawania drugiego UI źródła wartości.

Nie rozwiązuj HP w `MainActivity`, View, rendererze, `CampaignMapLayout`,
`MapDefinition` ani przez bezpośrednią mutację stanu symulacji.

## Walidacja konfiguracji

Zaktualizuj strict Kotlin balance decoder/domain model i analogiczny
`studio/server` validator. Odrzucaj:

- `level` poza zakresem 1–100 lub niebędący liczbą całkowitą;
- powtórzony numer poziomu;
- wpis bez BLUE i RED;
- HP równe zero, ujemne, ułamkowe, nieliczbowe lub przekraczające obsługiwany
  zakres liczby całkowitej;
- nieznane pola i złą wersję balance schema.

Błąd zwraca ścieżkę JSON, np.
`runtime.campaign.fortressHealthOverrides[0].blueMaximumHealth`.
Nie maskuj błędnego JSON sukcesopodobną wartością domyślną.

## Studio Balance

Dodaj edytor w Runtime → Campaign, oddzielony wizualnie od AI difficulty,
wind i lives:

- pokaż 100 poziomów, pogrupowanych według kanonicznego katalogu rozdziałów;
- wiersz ma `Level`, `BLUE max HP`, `RED max HP`;
- prezentuj efektywne wartości i rozróżniaj inherited/default od override;
- pozwól zmienić tylko jeden team;
- zapewnij reset strony lub poziomu do dziedziczenia;
- nie serializuj stu domyślnych wpisów — zapisuj wyłącznie overrides;
- zachowaj istniejący typed draft, niedokończone pola numeryczne,
  materializację, walidację, `expectedHash`, atomic save, konflikt 409
  i możliwość jawnego reloadu;
- nie wymagaj map artwork: HP można edytować dla wszystkich poziomów,
  również rozdziałów bez mapy.

Nie dodawaj edycji do Studio Campaign marker/layout API ani do map JSON.

## Testy wymagane

### Domena i balance loader

- pusty zestaw overrides zachowuje standardowe HP na poziomach 1–100;
- osobny BLUE/RED override daje osobne wartości;
- brak jednego teamu dziedziczy standard tylko dla tego teamu;
- override dla granicznych poziomów 1 i 100 działa;
- błędny level, duplicate, puste wpisy, niepoprawne HP i unknown keys
  są jawnie odrzucane;
- ścisły loader ładuje poprawny bundled `game-balance.json`.

### Kampania, application i simulation

- `CampaignLevelDefinition` przekazuje override z globalnego poziomu do
  scenariusza;
- `MatchSession` rozwiązuje właściwe BLUE/RED HP i przekazuje je do
  symulacji;
- snapshoty udostępniają wynikowe `maxHealth`; bieżące HP fortecy zaczyna się
  od tej samej wartości;
- poziom bez wpisu pozostaje na standardowym HP;
- Demo dalej ma 5000 BLUE / 500 RED;
- jawne host/test overrides zachowują ustaloną precedencję;
- replay po zmianie effective HP odtwarza te same wartości i snapshot;
- boss profiles, boss HP i victory conditions nie zmieniają się.

### Studio

- formularz odczytuje i edytuje oba pola niezależnie;
- reset pojedynczej strony i poziomu usuwa override i pokazuje wartość
  odziedziczoną;
- materialization/serialization zachowują semantykę i zapisują rzadkie
  overrides;
- błędne lub duplikowane poziomy są pokazywane pod właściwą ścieżką;
- API nie zapisuje przy nieaktualnym hash i zachowuje draft po 409.

## Dokumentacja i walidacja

Zaktualizuj `docs/project/knowledge.md`, balance schema documentation oraz
Studio architecture notes tylko wtedy, gdy implementacja zmienia ich
utrzymywane fakty. Nie modyfikuj bieżących zmian użytkownika w tych plikach;
czytaj i integruj je przed edycją.

Uruchom:

```bash
./gradlew test assembleDebug --quiet
cd studio && npm test && npm run lint
git diff --check
git status --short
```

Nie commituj ani nie pushuj.
