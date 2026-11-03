# Prompt implementacyjny — formularz balansu w Studio

> Ten prompt opisuje zakończony zakres bazowego formularza schema 3. Bieżące
> Studio obsługuje schema 5 z nullable `ammunition[].shopCostGold` i globalnym
> mnożnikiem tempa; aktualnym źródłem prawdy jest
> [`shop-system.md`](../systems/shop-system.md).

Zaimplementuj formularz edycji balansu w Urkaaaz Studio zgodnie z
[`studio-balance-form-implementation-plan.md`](studio-balance-form-implementation-plan.md).

## Cel

Zastąp bezpośrednią edycję JSON-a w `studio/web/src/tabs/balance/BalanceTab.tsx`
pełnym formularzem dla wszystkich pól `game-balance.json` schema 3. Użytkownik
ma pracować przez zakładki, selektory, inputy, dropdowny i kontrolki wyboru
wielu wartości. Nie pokazuj surowego JSON-a, nie dodawaj jego alternatywnego
trybu edycji i nie zmieniaj formatu ani wartości balansu.

## Ustalone wymagania

- Edytowanym źródłem pozostaje
  `game-domain/src/main/resources/balance/game-balance.json`.
- Obsługuj wyłącznie `schemaVersion: 3`. Nieznaną wersję lub niepoprawny
  kształt zgłoś jawnie i nie udostępniaj Save.
- Pozwól edytować wszystkie pola istniejących rekordów: `bosses`, `units`,
  `ammunition`, `spells`, `mana` i pełne `runtime`.
- Nie dodawaj, nie usuwaj i nie przestawiaj bossów, jednostek, amunicji,
  czarów, ataków, abilities ani pasm kampanii. Stabilne ID są read-only.
- Nie zmieniaj obecnych wartości w `game-balance.json`, reguł gry ani serwera
  walidacji, poza rozszerzeniem współdzielonych typów, jeśli jest konieczne.
- Zachowaj istniejące API i przepływ:
  `fetchBalance` → `validateBalance` → `saveBalance` z `expectedHash`.
- Walidator serwera pozostaje autorytatywny. Nie dodawaj cichych fallbacków,
  `Number("") === 0`, nieprawidłowych domyślnych wartości ani szerokich
  `catch`.
- Nie twórz commitów ani nie wykonuj push.

## Najpierw zbadaj istniejący kod

Przed edycją przeczytaj:

- `studio/web/src/tabs/balance/BalanceTab.tsx`;
- `studio/web/src/shared/api.ts` i `studio/server/src/contracts/balance.ts`;
- `studio/server/src/services/balanceCatalog.ts` oraz jego testy;
- `game-domain/src/main/resources/balance/game-balance.json`;
- `docs/architecture/studio-architecture.md`,
  `docs/architecture/studio-balance-form-implementation-plan.md` i
  `studio/README.md`.

Sprawdź istniejące style z `studio/web/src/shell/styles.css` oraz pola z
Campaign/Progress. Reużyj wzorców i kontraktów; nie twórz nowych endpointów ani
równoległego serwisu zapisu, jeżeli obecne API wystarcza.

## Formularz

Utwórz pięć głównych zakładek:

1. **Bosses** — profil wybierany z istniejącej listy; edycja health, collision,
   movement, attacks, special abilities i ammunition immunities.
2. **Units** — profil jednostki oraz wszystkie pola jej konfiguracji.
3. **Ammunition** — profil amunicji i wszystkie pola reload, inventory, flight,
   collision, damage, terrain, behavior oraz available layers.
4. **Spells & Mana** — wszystkie pola zaklęć i pełna konfiguracja many.
5. **Runtime** — economy, fortresses, catapult, match, physics, wind, combat,
   bossCombat, AI profiles, AI strategy, rewards i campaign.

Profile wybiera się dropdownem lub panelem nawigacji. Zmiana tab/profile nie
może odrzucać draftu. Główne zakładki i zagnieżdżone sekcje powinny działać w
wąskim widoku, a każdy input ma mieć label, jednostkę, wskazówkę i dostępną
etykietę błędu.

Kontrolki:

- wartości liczbowe z odpowiednim `min`, `max`, `step` oraz jednostką (`ms`,
  `s`, `%`, `world units`, `gold`, `HP`); nigdy nie aktualizuj pola przez
  `parseFloat(value) || 0`;
- enumy jako dropdowny ograniczone do typów akceptowanych przez
  `balanceCatalog.ts`;
- wartości boolean jako checkbox/switch;
- `hitLayers`, `availableLayers` i `immuneAmmunitionTypes` jako
  wielokrotny wybór z deterministycznym zachowaniem kolejności;
- attacks, abilities oraz `aiDifficultyBands` jako stałe listy istniejących
  rekordów, bez operacji CRUD;
- `AmmunitionImpactBehavior` jako discriminated union z dynamicznie widocznymi
  polami:
  - `DIRECT_IMPACT`: bez pól wariantowych;
  - `EXPLOSION`: radius i lifetime;
  - `LINGERING_AREA`: radius, duration, tick interval, obrażenia, drift,
    terrain following, opcjonalny status i unit friendly fire;
- spell effect fields tylko w wariantach zgodnych ze stabilnym `SpellType` i
  z serwerową walidacją implementowanych efektów.

Zachowuj niekompletne numeric inputy w formie, która pozwala użytkownikowi
dokończyć wpisywanie. Puste pole nie może zmienić się w zero. Nie serializuj
dokumentu, dopóki wszystkie wymagane wartości nie są sparsowane jako skończone
liczby i nie przejdą lokalnej walidacji. Przy zmianie wariantu unionu pokaż
nowe wymagane pola jako puste; nie inventuj wartości zastępczych.

## Model i serializacja

- Wydziel typ schema 3 i adapter formularza w katalogu `tabs/balance`.
- Utrzymaj typ `GameBalanceDocumentV3` i draft formularza w modelu
  `studio/web/src/tabs/balance/model.ts`. Istniejący kontrakt API nadal
  transportuje tekst i hash; dodaj typ do `@studio/contracts/balance` tylko
  wtedy, gdy jest rzeczywiście używany po obu stronach granicy.
- Ogranicz `JSON.parse`/`JSON.stringify` do adaptera modelu. Żaden komponent
  nie renderuje tekstu dokumentu.
- Parser sprawdza `schemaVersion`, wymagane pola, typy i nieznane klucze;
  odrzuca nieobsługiwany dokument zamiast go normalizować.
- Draft może przechowywać surowe teksty pól liczbowych, lecz dokument
  `GameBalanceDocumentV3` zawiera wyłącznie właściwe typy i jest tworzony
  dopiero po parsowaniu.
- Serializer zapisuje pełny dokument w stabilnej kolejności i nie gubi pól
  pominiętych przez UI.
- Dirty state porównuj względem sparsowanego baseline, nie względem formatowania
  surowego JSON-a. Niezmieniony formularz nie może być oznaczony jako dirty.
- `schemaVersion` jest read-only. `balanceRevision` podbijaj według obecnego
  `withIncrementedRevision` tylko dla zmiany treści i zgodnie z aktualną
  semantyką.

## Walidacja i zapis

- Zachowaj akcje `Reload`, `Validate`, `Save`, informację o SHA-256, statusy
  sukcesu/błędu i komunikat o rebuildzie/restarcie.
- Waliduj draft przez istniejące `validateBalance`. Mapuj `issues[].path` do
  właściwego pola i zakładki; pokaż też wspólne podsumowanie.
- Nierozpoznane ścieżki błędów pozostają widoczne w podsumowaniu i nie są
  ignorowane.
- Save ma być możliwe wyłącznie dla dirty, poprawnego draftu; przed zapisem
  uruchom walidację serwera i użyj bieżącego `document.hash`.
- Po sukcesie odśwież baseline, revision i hash z odpowiedzi API.
- Przy `409` zachowaj draft, zablokuj ponowny zapis na starym hashu i pokaż
  jawny Reload. Nie nadpisuj zmian wykonanych poza Studio.
- Reload/odrzucenie dirty draftu wymaga potwierdzenia. Przełączanie zakładek
  ani profilu nie może go odrzucić.
- Każdy błąd parsowania, walidacji i I/O powinien być widoczny; nie używaj
  fallbacku do pustego formularza.

## Testy i dokumentacja

Dodaj testy adekwatne do istniejącego Vitest setup:

- parser/serializer: round-trip całego checked-in schema 3 i brak utraty pól;
- odrzucenie nieznanej wersji, brakujących pól i nieobsługiwanych wartości;
- brak dirty state po samym load, dirty po zmianie i brak domyślnego zera
  dla pustych inputów;
- edycja pól liczbowych, enumów, multiselectów i wariantów zachowania;
- API Validate/Save, rewizja, aktualizacja hash i zachowanie draftu przy `409`;
- formularz ma dostępne etykiety, błędy i czytelne stany loading/saving/error.

Zaktualizuj `docs/architecture/studio-architecture.md` i `studio/README.md`,
by opisywały formularz, obecne endpointy, schema 3 oraz brak hot reloadu.
Nie opisuj symulacji ani JobRunnera jako części tego zadania.

## Walidacja końcowa

Uruchom:

```bash
cd studio
npm test
npm run lint
npm run build
cd ..
git diff --check
git status --short
```

Nie uruchamiaj testów/builda Gradle, jeśli zmiany nie dotkną modułów gry.
Zatrzymaj się, jeśli walidacja serwera lub build UI nie przechodzą; nie
przepisuj ich cichym fallbackiem.

## Kryteria ukończenia

- Brak widocznego lub edytowalnego raw JSON textarea.
- Wszystkie pola schema 3 mają odpowiadające kontrolki.
- Stabilne profile/listy zachowują ID, liczebność i kolejność.
- Walidacja serwerowa jest wymagana przed zapisem.
- Konflikt hasha i błędny draft nie nadpisują pliku ani nie gubią danych.
- Nie zmieniono żadnych wartości `game-balance.json` ani reguł gameplayu.
- Testy, lint, build Studio i `git diff --check` przechodzą.
