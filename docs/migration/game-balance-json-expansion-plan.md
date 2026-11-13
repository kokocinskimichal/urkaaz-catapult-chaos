# Plan migracji balansu jednostek, amunicji i czarów do JSON

## Status i cel

**Status: wdrożono schema 3.** Migracja schema 2 przeniosła katalogi bossów,
jednostek, amunicji, czarów i many. Schema 3 przenosi również wspólne parametry
runtime ekonomii, fortec, meczu, fizyki, wiatru, walki, bossów, AI, nagród i
kampanii. Poniższe etapy schema 2 opisują zakończoną migrację bazową.

Późniejsze rozszerzenia podniosły kontrakt do schema 7. Revision
`game-balance-18` dodał `runtime.campaign.lives` dla LifeSystemu, a revision
`game-balance-19` dodał sparse per-level overrides maksymalnego HP fortec BLUE
i RED. Późniejszy zapis w Studio ustawił poziom 1 na 200 HP dla obu fortec,
podnosząc bieżącą rewizję do `game-balance-20`. Studio Balance używa strict
schema 7.

Plan przenosi obecne charakterystyki jednostek, amunicji i czarów do
`game-domain/src/main/resources/balance/game-balance.json`. Jest to migracja
źródła konfiguracji, nie zmiana balansu ani dodanie nowych mechanik. Wartości
mają pozostać równe obecnemu zachowaniu runtime.

Przed migracją plik używał `schemaVersion: 1` i zawierał wyłącznie profile bossów.
`GameBalanceConfig` oraz `GameBalanceRepository` również modelują obecnie tylko
bossy. Jednostki korzystają z `UnitConfigurations`, amunicja z
`AmmunitionType` i `AmmunitionCatalog`, a czary z `SpellCatalog` oraz
`ManaConfiguration`. Studio Balance walidowało wtedy wyłącznie sekcję bossów.

## Ustalone decyzje

- `AmmunitionCatalog` jest kanonicznym źródłem efektywnych wartości runtime.
  `AmmunitionType` zachowuje stabilne serializowane ID, ale nie przechowuje
  równoległego balansu.
- `UnitDefinition` i `SpellType` zachowują stabilne identyfikatory oraz
  implementacje zachowań. JSON opisuje ich konfigurację, nie wykonywalne reguły.
- Migracja nie zmienia liczb ani nie implementuje pozostałych efektów czarów.
  Osiem niezaimplementowanych czarów pozostaje niezaimplementowanych.
- Konfigurację wczytuje i waliduje warstwa aplikacyjna. Domena, symulacja, AI
  i UI otrzymują typowany, niemutowalny odczyt; nie wykonują własnego I/O.
- `balanceRevision` i SHA-256 pozostają źródłem kontroli zgodności replayów.

## Zakres danych

Schema 2 rozszerzyła istniejące `bosses` o:

| Sekcja | Dane przenoszone z obecnych katalogów |
|---|---|
| `units` | supply cost, HP, prędkość, obrażenia, cooldown, zasięg i obrażenia fortecy dla pięciu stabilnych ID jednostek |
| `ammunition` | typ efektu, reload, inventory, parametry lotu i fragmentacji, kolizje, profile obrażeń/falloffu, teren, warstwy trafień i parametry impact behavior |
| `spells` | gold/mana cost, kategoria, target, `special`, recast i typowane parametry obecnie zaimplementowanych efektów |
| `mana` | start, limit, ilość i interwał regeneracji z `ManaConfiguration` |
| `runtime` (schema 3) | ekonomia i supply, zdrowie fortec, czas meczu, fizyka i wiatr, walka jednostek, Demolisher, walka bossów, profile i strategie AI, nagrody i parametry kampanii |

Przykładowy kształt korzenia:

```json
{
  "schemaVersion": 3,
  "balanceRevision": "game-balance-3",
  "bosses": [],
  "units": [],
  "ammunition": [],
  "spells": [],
  "mana": {},
  "runtime": {}
}
```

Wartości wyliczane przez reguły, np. zakres obrażeń jednostki po zastosowaniu
obecnej formuły, pozostają obliczeniami w domenie. Klucze tłumaczeń, drawable,
animacje i inne assety pozostają poza plikiem balansu. Amunicyjne typy
zachowania (`DIRECT_IMPACT`, `EXPLOSION`, `LINGERING_AREA`) i efekty czarów
pozostają typowanymi wariantami danych obsługiwanymi przez kod; JSON nie
zawiera skryptów.

## Etapy implementacji

1. **Inwentaryzacja i baseline.** Spisać efektywne wartości, właścicieli i
   konsumentów każdej definicji. Rozstrzygnąć duplikaty przed migracją:
   przykładowo `POWDER_BARREL` w `AmmunitionType` ma radius obrażeń `155` i
   krater `165×112`, podczas gdy profil `AmmunitionCatalog` używany przez
   runtime ma radius `220` i krater `150×90`. Zgodnie z ustaleniem przenieść
   wartości katalogu runtime, bez retuningu.
2. **Schema 2 i typowany model (zakończone).** Rozszerzyć `GameBalanceConfig`,
   `GameBalanceRepository` i DTO o wszystkie sekcje. Dodać ścisłą walidację
   pól, zakresów, enumów, unikalności ID i pełnego pokrycia stabilnych ID.
   Rozszerzyć `studio/server/src/services/balanceCatalog.ts` o tę samą
   walidację i testy. Nieznana wersja ani niepoprawne dane kończą się jawnym
   błędem.
3. **Jednostki.** Zastąpić liczby w `UnitConfigurations` danymi z balansu.
   `UnitFactory` tworzy typowane zachowania jednostek z przekazanej
   `UnitConfiguration`. Przepiąć `MatchSession`/`SimulationConfig`, rekrutację,
   loadout i projekcję `LoadoutCatalog`; zostawić klasy zachowań w domenie.
4. **Amunicja.** Zbudować typed profiles z sekcji JSON i przepiąć symulację,
   `game-ai`, inventory, reload oraz `LoadoutCatalog`. Usunąć równoległe pola
   balansu z `AmmunitionType`; zachować enum jako stabilny ID. Każdy konsument
   `unlimited` i pozostałych przenoszonych wartości korzysta z profilu, nie z
   drugiej kopii w enumie.
5. **Czary i mana.** Przepiąć `SpellCatalog`, `ManaConfiguration`,
   `LoadoutUseCases`, progresję i runtime meczu na typowane profile. Parametry
   HASTE, Fortress Shield i Runic Bastion pozostają zgodne z obecnym runtime;
   brakujące efekty nadal mają jawny brak implementacji.
6. **Domknięcie migracji.** Usunąć stare katalogi jako właścicieli wartości,
   pozostawiając tylko stabilne ID, typy i reguły. Zachować pojedynczy,
   niemutowalny `GameBalanceConfig` odczytany dla sesji; AI, loadout i
   symulacja korzystają z tych samych definicji. W replayu nadal zapisać
   rewizję i hash dokładnego pliku.

## Kryteria akceptacji

- Każde `UnitDefinition`, `AmmunitionType` i `SpellType` ma dokładnie jedną
  konfigurację; brak lub duplikat jest błędem, bez domyślnych wartości sukcesu.
- Parametry gameplayu, ekonomii i trudności zdefiniowane w `runtime` mają
  wyłącznie jedną wartość w schema 3; geometria, prezentacja i stałe techniczne
  pozostają w kontraktach i kodzie.
- Wartości przeniesione są zgodne z obecnym runtime, łącznie z profilem
  amunicji kanonicznym dla symulacji.
- `UnitFactory`, symulacja, AI, loadout, progresja i Androidowy read model nie
  przechowują własnych kopii liczb balansu.
- Testy sprawdzają loader/validator, granice i błędne JSON-y, zachowanie
  jednostek/amunicji/czarów, decyzje AI, walidację loadoutu oraz
  deterministyczność i kontrolę rewizji/hashu replaya.
- Studio Balance przyjmuje schema 3, odrzuca błędne sekcje i nadal zapisuje
  atomowo z kontrolą `expectedHash` oraz `balanceRevision`.
- Istniejące assety, stabilne identyfikatory, dostępność kampanijna i
  niezaimplementowane efekty czarów pozostają bez zmian.

## Poza zakresem

- strojenie lub zmiana wartości balansu;
- implementacja pozostałych ośmiu efektów czarów;
- zmiana identyfikatorów serializowanych, ikon, animacji lub polityki assetów;
- migracja profilu gracza, loadoutu lub save'ów — dane postępu nie są
  konfiguracją balansu.

## Weryfikacja

Uruchomić testy domeny, aplikacji, symulacji, AI i Studio, następnie:

```bash
./gradlew test assembleDebug --quiet
cd studio && npm test && npm run lint && npm run build
git diff --check
```
