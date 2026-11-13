# Plan edycji dostępności zawartości kampanii w Studio

> Prompt implementacyjny: [`campaign-content-availability-implementation-prompt.md`](campaign-content-availability-implementation-prompt.md).

## Status i zakres

Plan i zapis wdrożonej funkcji edycji globalnego poziomu kampanii, od
którego jednostka, typ amunicji lub czar może być wybrany w kampanijnym
Loadout i użyty w meczu. Plan uwzględnia decyzje użytkownika:

- Haste i Heal zachowują nagrody milestone na poziomach 5 i 7, a ich poziom
  dostępności jest edytowany razem z konfiguracją milestone;
- progi kampanii nie zmieniają widoczności ani zasad zakupów w Shop.

**Status: wdrożono** w `game-balance.json` schema 8. Funkcję wprowadzono w
rewizji `game-balance-21`; bieżąca konfiguracja to `game-balance-25`.
Konfiguracja steruje scenariuszową dostępnością i dostępnością Haste/Heal w
milestone registry, bez zmiany poziomów nagród 5/7, posiadania przedmiotów,
Shop ani efektów gameplayowych.

## Obecny stan

Wartości domyślne zachowują poprzednie progi `CampaignLevelDefinition`:

| Rodzaj | ID | Obecnie od poziomu |
|---|---|---:|
| Jednostka | `DEFENDER` | 2 |
| Jednostka | `SAPPER` | 5 |
| Jednostka | `DEMOLISHER` | 8 |
| Jednostka | `RAGING_BOAR` | 11 |
| Jednostka | `SLINGMASTER` | 13 |
| Amunicja | `ROCK` | 1 |
| Amunicja | `SIEGE_BOMB` | 1 |
| Amunicja | `POWDER_BARREL` | 10 |
| Amunicja | `FIRE_RAIN` | 31 |
| Amunicja | `PLAGUE_CAULDRON` | 41 |
| Czar | `FORTRESS_SHIELD` | 1 |
| Czar | `HASTE` | 6 |
| Czar | `HEAL` | 8 |
| Czar | `ICE_TRAP` | 11 |
| Czar | `RUNIC_BASTION` | 21 |

Pozostałe czary mają `availableFromLevel: null`. Teraz progi są zapisane
w `runtime.campaign.contentAvailability`; `CampaignMilestoneRegistry`
zachowuje stałe poziomy nagród i pobiera dostępność Haste/Heal z tej samej
konfiguracji. `MatchScenario.availableUnits`, `availableAmmunition` i `availableSpells`
przekazują wynik do Loadoutu, MatchSession i AI; aplikacja meczu egzekwuje te
zbiory. W `MatchScenario.availableUnits` wartość `null` oznacza brak
ograniczenia, a jawna pusta lista zabrania wystawiania jednostek obu drużynom.
Definicje kampanii zawsze przekazują listę z progów, również pustą dla poziomu
1.

Studio Balance edytuje trzy kompletne kategorie progów w Runtime → Campaign,
z możliwością ustawienia `Never` i resetu pojedynczego wpisu do baseline.
Ścisła walidacja serwera sprawdza kanoniczne ID, zakresy, wymagane pola oraz
Haste/Heal thresholds względem poziomów nagród.

## Kontrakt konfiguracji

Rozszerzyć `CampaignBalance` w `RuntimeBalance.kt` o typowaną sekcję, np.
`contentAvailability`, z osobnymi listami jednostek, amunicji i czarów:

```json
{
  "contentAvailability": {
    "units": [
      { "unitId": "DEFENDER", "availableFromLevel": 2 },
      { "unitId": "SAPPER", "availableFromLevel": 5 }
    ],
    "ammunition": [
      { "ammunitionType": "ROCK", "availableFromLevel": 1 },
      { "ammunitionType": "FIRE_RAIN", "availableFromLevel": 31 }
    ],
    "spells": [
      { "spellType": "FORTRESS_SHIELD", "availableFromLevel": 1 },
      { "spellType": "HASTE", "availableFromLevel": 6 },
      { "spellType": "HEAL", "availableFromLevel": 8 }
    ]
  }
}
```

W docelowym katalogu każda jednostka, amunicja i czar z kanonicznego ownera
ma dokładnie jeden wpis. `availableFromLevel` jest całkowitą liczbą z zakresu
`1..100`; `null` oznacza „nigdy niedostępne w kampanii”. Domyślna konfiguracja
musi zachować powyższe poziomy i obecny brak kampanijnej dostępności pozostałych
czarów. Identyfikatory są tylko referencjami: Studio nie może dodawać nowych
unitów, typów amunicji ani czarów.

Zmiana progu zmienia wyłącznie warunek kampanii:

```text
availableAtLevel(content, level) =
    configuredLevel(content) != null &&
    level >= configuredLevel(content)
```

Dostępność jest monotoniczna: po odblokowaniu na niższym poziomie zawartość
pozostaje dostępna na kolejnych. Ten model nie dodaje wyjątków wyłączających
zawartość na pojedynczym poziomie.

## Milestone’y i posiadanie czarów

`CampaignMilestoneRegistry` pozostaje właścicielem nagród i stabilnych
milestone ID, ale nie przechowuje drugiej, niezależnej kopii progów Haste/Heal.
`availableFromLevel` tych dwóch definicji należy wyprowadzić z tego samego
`contentAvailability.spells`, który buduje zestaw czarów poziomu.

- Poziomy ukończenia i przyznania nagrody pozostają stałe: Haste po poziomie 5,
  Heal po poziomie 7.
- Konfiguracja dostępności Haste/Heal musi pozostać późniejsza niż odpowiedni
  poziom nagrody; walidator odrzuca próg równy lub wcześniejszy.
- Zmiana progu nie przyznaje czaru ani nie zmienia `CampaignProgress`.
  Zapisany milestone/entitlement nadal jest wymagany, a nowy próg dodatkowo
  ogranicza, od którego poziomu czar pojawi się w kampanijnym Loadout i
  scenariuszu meczu.
- Dla innych czarów próg steruje wyłącznie dostępnością w scenariuszu.
  Ustawienie poziomu nie implementuje efektu czaru; istniejąca walidacja
  niezaimplementowanych efektów pozostaje bez zmian. Studio powinno oznaczać
  status implementacji jako informację tylko do odczytu.

`CampaignMilestoneRegistry`, `CampaignRewardRegistry`, `BundledCampaignBalance`
i projekcja `CampaignLevelDefinition` muszą otrzymywać tę samą
`CampaignBalance`, aby milestone granty i dostępność scenariusza nie rozeszły
się po zmianie konfiguracji.

## Przepływ danych

1. `game-balance.json` przechowuje `runtime.campaign.contentAvailability`.
2. Strict decoder `GameBalanceRepository` waliduje typowane listy i kompletność
   identyfikatorów względem katalogów jednostek, amunicji i czarów.
3. `CampaignLevelDefinition.forLevel` zastępuje wszystkie lokalne `if
   (level >= N)` oraz ręczne dodawanie milestone czarów filtrowaniem po jednym
   wspólnym `CampaignBalance`.
4. `CampaignLevelDefinition.toMatchScenario` przekazuje zwykłe zbiory
   `availableUnits`, `availableAmmunition` i `availableSpells`; nie dodaje
   konfiguracji poziomów do Android UI ani map.
5. Istniejące ścieżki Loadout, `MatchSession`, `AiMatchCoordinator` i testy
   scenariusza nadal konsumują te same zbiory i wspólną walidację.
6. Demo pozostaje oddzielone i zachowuje dotychczasowe opcje. Test Mode
   wybierający kampanijny poziom korzysta z jego progów.
7. Shop nadal pokazuje i sprzedaje pozycje według istniejących reguł
   `shopCostGold`/stock; kampanijny próg nie zmienia zakupu ani posiadania.

## Studio Balance

Dodać sekcję **Campaign content availability** do istniejącej
**Balance → Runtime → Campaign**, obok wiatru, żyć, AI bands i fortress HP.
Nie dodawać nowego endpointu ani zakładki Campaign map.

- Pokaż trzy grupy: **Units**, **Ammunition**, **Spells**.
- Każdy wiersz ma stabilne ID, pole **Available from level** (1–100) oraz
  stan **Never** dla `null`. Pokazuj status implementacji czaru jako read-only.
- Zmiana pojedynczego pola nie może przebudowywać ani nadpisywać progów innych
  elementów. `CampaignLevelDefinition` pozostaje runtime’owym źródłem projekcji,
  a JSON balansu jest jedynym źródłem wartości progów.
- Formularz korzysta z istniejących `BalanceFormDraft`, `NumberField`,
  materializacji, resetu do wartości bazowej, walidacji, `expectedHash`,
  atomic save i jawnej obsługi konfliktu 409.
- Edytor nie ma surowego JSON ani możliwości edycji identyfikatorów/efektów.

## Walidacja

Kotlin balance model/decoder i `studio/server` validator sprawdzają:

- kompletne, dokładnie raz występujące ID dla każdej kanonicznej kategorii;
- wyłącznie znane `UnitId`, `AmmunitionType` i `SpellType`;
- `availableFromLevel` jako `null` albo integer z zakresu `1..100`;
- unikalność ID i brak nieznanych pól;
- progi Haste/Heal późniejsze niż poziomy przyznania ich milestone’ów;
- wersję schema oraz rewizję balansu bez sukcesopodobnego fallbacku.

Błąd wskazuje dokładną ścieżkę, np.
`runtime.campaign.contentAvailability.spells[2].availableFromLevel`.

## Plan implementacji

1. **Balance schema**
   - Dodać typy `CampaignContentAvailability` i wpisy per kategorię.
   - Ustalić domyślne progi zgodne z aktualnym `CampaignLevelDefinition`.
   - Podnieść balance schema z wersji 7 do 8 i zwiększyć `balanceRevision`.
   - Zaktualizować strict decoder, fixtures, zasób bundled i testy migracyjne.

2. **Projekcja kampanii i milestone’y**
   - Zbudować zbiory dostępności na podstawie konfiguracji dla globalnego
     poziomu 1–100; usunąć ręczne progi z `CampaignLevelDefinition`.
   - Zasilić `CampaignMilestoneRegistry` dostępnością Haste/Heal z tej samej
     konfiguracji, pozostawiając poziomy nagród 5/7 bez zmian.
   - Przekazać wspólny `CampaignBalance` przez `BundledCampaignBalance` do
     rejestru poziomów oraz rejestru nagród/milestone’ów.
   - Nie dodawać nowych flag do profilu, `CampaignProgress` ani symulacji.

3. **Studio Balance**
   - Zaktualizować typ `GameBalanceDocumentV7` do wersji 8, formularz,
     normalizację draftu, serializację i walidator serwera.
   - Dodać grupowaną tabelę trzech kategorii z edycją progu/`Never` i resetem.
   - Zachować istniejący hash guard, atomowy zapis, konflikt 409 i reload.

4. **Testy i dokumentacja**
   - Pokryć wartości przed, na i po granicy odblokowania oraz brak
     dostępności `null`.
   - Sprawdzić scenariusz kampanii, Loadout, walidację MatchSession/AI, Test
     Mode, niezmienione Demo i Shop oraz milestone entitlement Haste/Heal.
   - Zaktualizować `docs/project/knowledge.md`, changelog, dokumentację balansu
     i Studio po wdrożeniu.

## Kryteria akceptacji

- Studio umożliwia ustawienie pierwszego poziomu dostępności każdego
  kanonicznego unitu, typu amunicji i czaru.
- Domyślne progi dokładnie zachowują obecne zachowanie poziomów kampanii.
- Wszystkie progi trafiają przez `CampaignBalance` do `CampaignLevelDefinition`
  i istniejącego `MatchScenario`; Kotlin nie zawiera już progów `>= N` dla
  dostępności.
- Progi Haste/Heal są edytowane w jednym miejscu z milestone’ami; reward
  completion i trwałe posiadanie pozostają odrębne i bez zmian.
- Zmiana progu wpływa na kampanijny Loadout, komendy i AI przez istniejące
  scenario gates; nie zmienia Demo ani zakupów w Shop.
- Niezaimplementowany czar nie staje się grywalny wyłącznie przez ustawienie
  progu.
- Błędne, niepełne, zduplikowane lub nieznane wpisy są odrzucane z dokładną
  ścieżką pola.

## Poza zakresem

- Zakupy i ceny Shop, stock, nagrody oraz posiadanie czarów/jednostek.
- Zmiana poziomów przyznania Haste/Heal, ich wartości gameplayowych ani efektów.
- Odblokowanie mechaniki, która nie ma jeszcze implementacji.
- Czasowe eventy, wyjątki pojedynczego poziomu, cofanie dostępności lub
  warianty progów per chapter.
