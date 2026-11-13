# Prompt implementacyjny dostępności zawartości kampanii

> Plan i potwierdzone decyzje: [`campaign-content-availability-plan.md`](campaign-content-availability-plan.md).

## Status

**Wdrożono** w schema 8. Funkcję wprowadzono w `game-balance-21`; bieżąca
konfiguracja to `game-balance-25`. Ten prompt zachowuje kontrakty i kryteria
akceptacji użyte przy implementacji oraz aktualne wartości domyślne.

## Zadanie

Przenieś poziomy odblokowania jednostek, typów amunicji i czarów do typowanej
konfiguracji kampanii edytowanej w Urkaaaz Studio. Zachowaj istniejące
zachowanie jako wartości domyślne i użyj istniejących kontraktów scenariusza
oraz walidacji meczu.

## Potwierdzone wymagania

- Dostępność dotyczy globalnych poziomów kampanii `1..100`.
- Każdy stabilny ID jednostki, amunicji i czaru ma jeden konfigurowalny próg.
- `availableFromLevel = null` oznacza, że zawartość nie jest dostępna w
  kampanii. W innym przypadku jest dostępna od wskazanego poziomu wzwyż.
- Dostępność jest monotoniczna; nie dodawaj wyjątków per poziom ani cofania
  odblokowania.
- Domyślne wartości muszą zachować bieżące zachowanie:

  | Rodzaj | ID | Poziom |
  |---|---|---:|
  | Unit | `DEFENDER` | 2 |
  | Unit | `SAPPER` | 5 |
  | Unit | `DEMOLISHER` | 8 |
  | Unit | `RAGING_BOAR` | 11 |
  | Unit | `SLINGMASTER` | 13 |
  | Ammunition | `ROCK` | 1 |
  | Ammunition | `SIEGE_BOMB` | 1 |
  | Ammunition | `POWDER_BARREL` | 10 |
  | Ammunition | `FIRE_RAIN` | 31 |
  | Ammunition | `PLAGUE_CAULDRON` | 41 |
  | Spell | `FORTRESS_SHIELD` | 1 |
  | Spell | `HASTE` | 6 |
  | Spell | `HEAL` | 8 |
  | Spell | `ICE_TRAP` | 11 |
  | Spell | `RUNIC_BASTION` | 21 |
  | Spell | pozostałe obecne spell IDs | `null` |

- Zmiana progu Haste/Heal jest edycją ich milestone availability, ale nie
  zmienia poziomów ukończenia nagrody: Haste pozostaje nagrodą za poziom 5,
  Heal za poziom 7. Posiadanie nadal wymaga zapisanego milestone entitlement.
  Próg dostępności dla każdego z tych czarów musi być większy niż poziom jego
  nagrody.
- Progi nie wpływają na widoczność, ceny ani zakupy w Shop.
- Progi nie implementują brakujących efektów czarów. Obecne guardy
  `SpellDefinition.isImplemented` i walidacja castowania pozostają bez zmian.
- Demo zachowuje własną konfigurację. Test Mode dla wybranego campaign level
  korzysta z jego scenariuszowych progów.
- Nie dodawaj danych progów do map, `MapDefinition`, map layoutów, profilu
  gracza, `CampaignProgress` ani Android UI.

## Analiza przed edycją

Sprawdź `git status --short` i zachowaj istniejące zmiany użytkownika.
Przeczytaj obowiązujące `AGENTS.md`, `docs/architecture/architecture.md`,
`docs/architecture/level-definition-architecture.md`,
`docs/systems/campaign-content-availability-plan.md`,
`docs/systems/campaign-milestones-plan.md` oraz aktualne implementacje:

```text
game-domain/src/main/kotlin/com/urkaaaz/domain/RuntimeBalance.kt
game-domain/src/main/resources/balance/game-balance.json
game-application/src/main/kotlin/com/urkaaaz/application/balance/GameBalanceRepository.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelRegistry.kt
game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignMilestoneDefinition.kt
game-application/src/main/kotlin/com/urkaaaz/application/campaign/BundledCampaignBalance.kt
game-application/src/main/kotlin/com/urkaaaz/application/campaign/CampaignSystem.kt
game-contracts/src/main/kotlin/com/urkaaaz/contracts/MatchScenario.kt
game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt
game-application/src/main/kotlin/com/urkaaaz/application/AiMatchCoordinator.kt
studio/web/src/tabs/balance/model.ts
studio/web/src/tabs/balance/BalanceTab.tsx
studio/web/src/tabs/balance/BalanceForm.tsx
studio/server/src/services/balanceCatalog.ts
studio/server/src/contracts/balance.ts
```

Prześledź wszystkie call sites tworzące campaign scenario, reward/milestone
registry, Test/Demo match i Studio Balance draft. Użyj aktualnych canonical
IDs z unit, ammunition i spell catalogs; nie wprowadzaj drugiej kopii ich list.

## Kontrakt konfiguracji i wersja

Dodaj typowaną sekcję `runtime.campaign.contentAvailability` do
`CampaignBalance`, z osobnymi listami jednostek, amunicji i czarów. Zalecany
kształt:

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
      { "spellType": "HEAL", "availableFromLevel": 8 },
      { "spellType": "BERSERKER", "availableFromLevel": null }
    ]
  }
}
```

Nazwy typów/właściwości można dopasować do repozytorium, ale zachowaj
semantykę:

- kompletna lista każdego canonical ID dokładnie raz w odpowiedniej kategorii;
- `availableFromLevel` jest `null` albo integerem `1..100`;
- lista nie zawiera ręcznych kopii balansowych definicji jednostki, amunicji
  lub czaru;
- domyślna konfiguracja zachowuje tabelę wartości z sekcji „Potwierdzone
  wymagania”.

Przed implementacją bundled `game-balance.json` był schema 7 z rewizją
`game-balance-20`. Podnieś schema do 8, ustaw kolejną rewizję balansu i zaktualizuj Kotlin DTO,
strict decoder, zasób bundled, Studio model/contracts, fixtures i wszystkie
testy schema/revision. Nie wprowadzaj cichego fallbacku schema 7 do domyślnych
wartości.

## Ownerzy i przepływ

1. `CampaignBalance.contentAvailability` jest jedynym źródłem progów.
2. `CampaignLevelDefinition.forLevel` filtruje progi dla globalnego poziomu
   i buduje obecne `availableUnits`, `availableAmmunition`,
   `availableSpells`. Usuń z tej funkcji wszystkie ręczne progi `>= N` oraz
   niezależne dodawanie milestone spell availability.
3. `CampaignLevelDefinition.toMatchScenario` nadal przekazuje standardowe
   zbiory dostępności; nie wprowadzaj drugiego kontraktu. Dla
   `MatchScenario.availableUnits` `null` oznacza brak ograniczenia, a jawna
   pusta lista oznacza, że żadna drużyna nie może wystawiać jednostek.
   Kampania przekazuje listę jawnie także wtedy, gdy jest pusta.
4. `MatchSession` i AI stosują te zbiory przez obecną walidację i
   `AiMatchCoordinator`; pustej listy nie traktuj jako braku ograniczenia.
   Nie duplikuj progów w Android, AI, Loadout ani symulacji.
5. Haste/Heal entitlement pozostaje własnością CampaignSystem i
   `CampaignProgress.appliedMilestoneIds`. `CampaignMilestoneDefinition`
   zachowuje completion level i reward ID, ale jego `availableFromLevel`
   musi pochodzić z tego samego spell availability entry, a nie z drugiej
   liczby wpisanej w `DEFAULT_DEFINITIONS`.
6. Przekaż ten sam `CampaignBalance` do budowania `CampaignLevelRegistry` i
   `CampaignRewardRegistry`/`CampaignMilestoneRegistry`. Zachowaj idempotentne
   przyznawanie milestone’ów i default-loadout preference.
7. Waliduj Haste/Heal thresholds jako ściśle większe od milestone
   `completedLevel`. Późniejsze odblokowanie nie przyznaje wcześniej
   entitlement; wcześniejszy/equal próg zostaje odrzucony.
8. Shop nadal używa `shopCostGold`, własności i stocków bez sprawdzania
   `availableFromLevel`.
9. Efekt czaru pozostaje kontrolowany przez istniejący spell implementation
   status. Konfiguracja poziomu nie może włączyć niezaimplementowanego efektu.

## Walidacja balansu

Kotlin/domain decoder i `studio/server` validator muszą odrzucić:

- brak lub duplikat canonical ID w którejkolwiek z trzech kategorii;
- nieznany ID albo ID z niewłaściwego ownera/kategorii;
- `availableFromLevel` spoza `1..100`, `null` nieobsłużony zgodnie ze
  schematem, typ niecałkowity albo nieznany field;
- Haste/Heal próg równy lub wcześniejszy niż milestone reward level;
- niezgodną schema version.

Błąd zawiera dokładną ścieżkę JSON, np.
`runtime.campaign.contentAvailability.spells[2].availableFromLevel`.
Walidacja serwera jest autorytatywna; nie polegaj wyłącznie na formularzowych
`min`/`max`.

## Studio Balance

Dodaj sekcję **Campaign content availability** pod istniejącym
**Balance → Runtime → Campaign**. Nie dodawaj nowej zakładki, endpointu ani
edytora map.

- Pokaż trzy grupy `Units`, `Ammunition` i `Spells`.
- Renderuj stabilne ID read-only oraz edytowalne `Available from level`
  (`1..100`) lub `Never` dla `null`.
- Pokaż implementację efektu spell jako read-only status; nie pozwól edytować
  IDs ani efektu z tej sekcji.
- Haste/Heal są w tej samej tabeli czarów; ich milestone award levels (5/7)
  są pokazane jako read-only kontekst, a pole dostępności edytuje wspólne
  `CampaignBalance` źródło.
- Zmiana jednej komórki nie może resetować progów innych elementów. Zachowaj
  typed `BalanceFormDraft`, dirty-check, materialization, serialization,
  validate/save flow, `expectedHash`, atomic write, konflikt 409 i reload.
- Reset pojedynczego elementu przywraca wartość z załadowanej baseline, a nie
  hardkodowaną kopię ID/progu w komponencie.
- Shop nie pokazuje ani nie blokuje zakupów według tych progów.

## Testy wymagane

### Domain, decoder i kampania

- bundled defaults odtwarzają dokładnie obecną dostępność wszystkich typów;
- dla każdej kategorii testuj poziom `availableFromLevel - 1`,
  `availableFromLevel` i poziom późniejszy;
- `null` nie pojawia się w `CampaignLevelDefinition.available*`;
- zmiana balance progu zmienia tylko odpowiadający element i projekcję
  `MatchScenario`;
- braki, duplikaty, unknown IDs, typy/poziomy niepoprawne i schema mismatch
  są jawnie odrzucane;
- Haste/Heal reward completion i persisted ownership pozostają bez zmian,
  a `availableFromLevel` w milestone registry jest zgodne z centralnym
  spell entry;
- Demo zachowuje dotychczasowe opcje, Test Mode używa wybranego campaign
  scenario, a Shop purchases/stock są niezależne.

### Studio

- formularz ładuje wszystkie IDs w stabilnym porządku i odczytuje progi;
- próg lub `Never` można zmienić niezależnie dla każdego wiersza;
- draft materialization/serialization zachowują `null` i numeryczne wartości;
- reset pojedynczego wiersza przywraca baseline;
- server API odrzuca missing/duplicate/unknown IDs oraz niepoprawne progi z
  dokładnymi ścieżkami;
- `expectedHash`/409, atomic save i reload zachowują obecne zachowanie.

## Kryteria akceptacji

- Nie ma już hardkodowanych unlock `if (level >= N)` w
  `CampaignLevelDefinition`.
- Jeden `CampaignBalance` steruje scenariuszową dostępnością i
  `availableFromLevel` Haste/Heal w milestone registry.
- Zmiana progu w Studio wpływa na odczyt balance po rebuildzie/restarcie i na
  dostępność w kampanijnym Loadout, scenariuszu, MatchSession i AI.
- Przedmiot nie staje się własnością gracza ani Shop unlockiem wskutek samego
  ustawienia progu.
- Effect implementation guard, campaign rewards, Shop, Demo, mapy, chaptery
  i geometria pozostają niezmienione.
- Dokumentacja `campaign-content-availability-plan.md`,
  `campaign-milestones-plan.md`, aktualny stan, knowledge i changelog
  opisują wspólny próg bez powielania źródła.

## Walidacja i zakres operacji

Uruchom:

```bash
./gradlew test assembleDebug --quiet
cd studio && npm test && npm run lint && npm run build
git diff --check
git status --short
```

Zaktualizuj bezpośrednio powiązane dokumenty. Nie implementuj innych
niezwiązanych pomysłów. Nie commituj ani nie pushuj, chyba że użytkownik
poprosi o to osobno.
