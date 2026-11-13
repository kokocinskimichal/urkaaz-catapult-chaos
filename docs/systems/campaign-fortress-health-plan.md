# Konfigurowalne HP fortec per poziom kampanii

## Status

**Implemented** in `game-balance.json` schema 7 / `game-balance-20`;
the current shared balance contract is schema 8 / `game-balance-21`.
Per-level overrides are available in Studio Balance → Runtime → Campaign.
The checked-in configuration currently sets level 1 to 200 HP for BLUE and RED.
The implementation prompt is retained as a trace of requirements and
acceptance criteria.

## Cel

Umożliwić ustawianie maksymalnego HP fortec BLUE i RED niezależnie dla każdego
poziomu kampanii oraz edycję tych wartości w Urkaaaz Studio. Brak
indywidualnego nadpisania ma zachować obecne zachowanie: obie fortece używają
`runtime.fortresses.standardMaximumHealth` z `game-balance.json`.

W trybie Demo należy zachować odrębną konfigurację: BLUE używa
`demoBlueMaximumHealth`, a RED `standardMaximumHealth`.

## Potwierdzone wymagania

- HP BLUE i RED można konfigurować niezależnie.
- Konfiguracja wskazuje globalny poziom kampanii 1–100.
- Wartości są dodatnimi liczbami całkowitymi HP.
- Brak wpisu dla poziomu lub brak wartości jednej drużyny oznacza dziedziczenie
  odpowiedniego standardowego HP z istniejącego balansu.
- Ustawienia są edytowalne w Studio.
- Poziomy, mapa, bossowie i postęp kampanii nie zmieniają się przez edycję HP.
- Demo zachowuje swoje istniejące wartości fortec.
- Zmiana HP jest częścią konfiguracji meczu i musi być zapisywana w replayu.

## Właściciele i granice

| Odpowiedzialność | Właściciel |
|---|---|
| Standardowe HP kampanijne oraz per-level overrides | `GameBalanceConfig.runtime.campaign` w `game-balance.json` |
| Odczyt i walidacja konfiguracji | istniejący balance repository / strict decoder |
| Projekcja ustawień wybranego poziomu | `CampaignLevelDefinition` / `CampaignLevelRegistry` |
| Przekazanie wartości do meczu | `MatchScenario` i `MatchSession` |
| Utworzenie fortec o zatwierdzonym maksymalnym HP | `DeterministicMatchSimulation` przez istniejące `SimulationConfig` |
| Edycja, walidacja i zapis | Studio Balance, Runtime → Campaign |
| Prezentacja aktualnego/maksymalnego HP | istniejący snapshot i match HUD |

HP nie należy dodawać do `MapDefinition`, campaign map layoutów ani Android
widoków. Mapa opisuje geometrię i prezentację terenu, a reguła HP należy do
konfiguracji kampanii i scenariusza meczu.

## Model konfiguracji

Utrzymać `runtime.fortresses.standardMaximumHealth` jako wartość domyślną
wszystkich kampanijnych fortec. Dodać do `runtime.campaign` rzadką listę
nadpisań identyfikowanych globalnym numerem poziomu, np.:

```json
{
  "runtime": {
    "campaign": {
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
    }
  }
}
```

W przykładzie BLUE poziomu 32 nadal dziedziczy standardową wartość. Pusta lista
oznacza zachowanie obecnego globalnego balansu bez wyjątków. Lista ma być
rzadka: nie wymagać stu jawnych rekordów ani kopiować globalnej wartości do
każdego poziomu.

Podczas projekcji poziomu do `MatchScenario` przenosić obecne wartości
nadpisane per drużyna, nie zastępując ich przedwcześnie wartością domyślną.
`MatchSession` rozwiązuje efektywne HP osobno dla BLUE i RED:

```text
jawne nadpisanie hosta / Demo
  > per-level override scenariusza kampanii
  > runtime.fortresses.standardMaximumHealth
```

Demo nie używa per-level kampanijnego wpisu. Każda symulacja otrzymuje
konkretne, dodatnie maksymalne HP obu fortec. `MatchReplayConfiguration`
zachowuje efektywne BLUE/RED HP, a scenariusz i przypięta rewizja/hash balansu
pozostają dostępne do diagnostyki i odtworzenia.

Przed implementacją należy sprawdzić API istniejących `blueFortressMaxHealth`
i `redFortressMaxHealth` w gateway/session/replay oraz zachować ich użycie
w testach i trybie Demo. Nie dodawać równoległej ścieżki konfiguracji.

## Edycja w Studio

Dodać sekcję **Fortress HP per campaign level** w istniejącej zakładce
**Balance → Runtime → Campaign**. Nie dodawać jej do Studio Campaign map
layout editor, ponieważ ten edytuje wyłącznie rozdziałowe kotwice markerów.

Sekcja powinna:

- pokazywać wszystkie poziomy 1–100, pogrupowane według kanonicznego
  `campaign-chapters.json`, bez ręcznie utrzymanej kopii zakresów rozdziałów;
- zawierać kolumny `Level`, `BLUE max HP`, `RED max HP`;
- pokazywać efektywne wartości, wskazywać dziedziczenie standardowej wartości
  i umożliwiać reset jednej strony lub całego poziomu do domyślnej;
- pozwalać na wpisanie HP BLUE bez wymogu zmiany RED i odwrotnie;
- pozostawać dostępna również wtedy, gdy dany rozdział nie ma jeszcze
  kampanijnej grafiki;
- zapisywać wyłącznie rzeczywiste overrides; domyślne wartości nie powinny
  być masowo duplikowane w pliku;
- używać istniejącego balance validate/save flow, `expectedHash`, atomowego
  zapisu i obsługi konfliktu 409;
- nie oferować trybu surowego JSON ani cichych fallbacków.

Podnieść `schemaVersion` balance zgodnie z konwencją projektu i zwiększyć
`balanceRevision`. Zachować strict validation zarówno w Kotlinie, jak
i w `studio/server`.

## Walidacja danych

Autorytatywna walidacja w balance model/repository i jej odpowiednik w Studio
muszą odrzucać:

- poziom poza `1..100`;
- duplikat wpisu dla tego samego poziomu;
- wpis bez nadpisanej wartości BLUE ani RED;
- `null`, zero, liczby ujemne, ułamki, NaN/Infinity lub wartości poza zakresem
  typu całkowitego;
- nieznane pola i niepoprawne typy;
- niezgodną wersję schematu.

Walidator ma wskazywać dokładną ścieżkę, np.
`runtime.campaign.fortressHealthOverrides[0].blueMaximumHealth`. Nie
przyjmować wartości wpisanej przez Studio jako poprawnej wyłącznie dlatego,
że formularz wcześniej ograniczył pole.

## Plan implementacji

1. **Balance model i schema**
   - Rozszerzyć `CampaignBalance`, Kotlin DTO/decoder, `GameBalanceConfig` oraz
     strict balance schema o per-level, per-team overrides.
   - Zaktualizować zasób `game-balance.json`, fixtures i revision.
   - Zachować standardowe 500 HP i osobny demo-blue override bez zmian.

2. **Projekcja poziomu i kontrakt meczu**
   - Dodać niemutowalny kontrakt per-team fortress HP override w
     `game-contracts`, opcjonalny dla scenariuszy bez nadpisań.
   - `CampaignLevelDefinition` ma pobrać wpis dla swojego globalnego poziomu
     z `CampaignBalance` i przekazać go w `MatchScenario`.
   - Brak wpisu dla teamu ma pozostać jawnie „dziedzicz standard”; nie
     hardkodować 500 w domenie, kampanii ani UI.

3. **Application, simulation i replay**
   - Rozwiązać efektywne HP w jednym miejscu aplikacyjnego tworzenia sesji,
     zachowując istniejące jawne nadpisania Demo/testów.
   - Przekazać konkretne BLUE/RED values do istniejących pól
     `SimulationConfig`; nie zmieniać algorytmu symulacji fortec.
   - Zapisać efektywne wartości w replay configuration; sprawdzić determinism
     i zachowanie przy restarcie.
   - Potwierdzić, że snapshots nadal podają `health` i `maxHealth`, a HUD
     wyświetla nowe maksimum bez własnego źródła HP.

4. **Studio Balance**
   - Rozszerzyć modele typowane formularza, draft materialization,
     serializację, ścisły server validator i edytor Runtime → Campaign.
   - Dodać tabelę 100 poziomów z wartościami efektywnymi, edycją zespołów
     niezależnie i resetem do dziedziczenia.
   - Zachować obsługę niekompletnych draftów, expected-hash guard, atomic save
     i jawne błędy konfliktu/walidacji.

5. **Testy, dokumentacja i walidacja**
   - Dodać testy domeny, balance decoding, CampaignLevelDefinition → scenario,
     application/session/replay, simulation snapshot HP, Demo override oraz
     Studio model/server/form.
   - Zaktualizować wiedzę implementacyjną i balance schema docs po wdrożeniu.
   - Uruchomić pełny Gradle gate i Studio tests/lint.

## Kryteria akceptacji

- BLUE i RED można ustawić różnie dla jednego poziomu.
- Ustawienie jednej fortecy nie zmienia drugiej.
- Poziom bez override używa `standardMaximumHealth` dla obu drużyn.
- Override tylko jednej strony pozostawia drugą stronę na wartości domyślnej.
- Demo nadal używa 5000 BLUE / 500 RED, niezależnie od kampanijnych overrides.
- HP trafia przez scenariusz i granicę aplikacyjną do symulacji, nie jest
  obliczane w Android UI.
- Snapshot i HUD pokazują poprawne `maxHealth`.
- Replay zachowuje i odtwarza konkretne maksymalne HP obu fortec.
- Studio zapisuje, ponownie wczytuje i resetuje per-level overrides; błędne
  dane są odrzucane z czytelną ścieżką pola.
- Ustawienia HP nie są dodane do map JSON ani layoutów map kampanii.
- Zmiana nie zmienia zdrowia bossów, warunków zwycięstwa, geometrii map,
  balansu amunicji ani dotychczasowych ustawień, jeśli nie ma override.

## Poza zakresem

- Automatyczne skalowanie HP wraz z numerem poziomu.
- Zmiany aktualnego HP po rozpoczęciu meczu.
- Zmiany demo fortress HP, boss HP, geometrii, obrażeń i warunków zwycięstwa.
- Edycja HP fortec w Android UI podczas meczu.
