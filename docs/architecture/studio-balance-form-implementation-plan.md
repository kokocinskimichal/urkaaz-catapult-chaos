# Plan implementacji formularza balansu w Studio

**Status: wdrożono.** Zakładka Balance i jej API używają teraz pełnego,
typowanego formularza zamiast tekstowego pola JSON. Formularz zachowuje format
balansu i nie zmienia zachowania gry. Ten dokument pozostaje zapisem zakresu i
kryteriów użytych przy implementacji.

**Aktualny kontrakt:** pierwotny zakres formularza dotyczył schema 3.
ShopSystem rozszerzył źródło balansu do schema 4 i dodał edytowalne,
nullable `ammunition[].shopCostGold`; globalny mnożnik tempa podniósł obecny
kontrakt do schema 5. Bieżące Studio, walidator i formularz obsługują schema 5.
Szczegóły ShopSystem opisuje
[`shop-system.md`](../systems/shop-system.md).

## Cel

Umożliwić edycję wszystkich istniejących pól `game-balance.json` schema 3
przez zakładki, selektory profili, pola liczbowe, dropdowny i kontrolki
wielokrotnego wyboru. JSON pozostaje wewnętrznym formatem pliku i API; nie jest
wyświetlany ani edytowany przez użytkownika.

## Stan obecny

- `studio/web/src/tabs/balance/BalanceTab.tsx` pobiera tekst pliku, pokazuje
  go w `textarea`, wysyła do walidacji i zapisuje z `expectedHash`.
- `GET /api/balance`, `POST /api/balance/validate` i `PUT /api/balance`
  obsługują kanoniczny
  `game-domain/src/main/resources/balance/game-balance.json`.
- `studio/server/src/services/balanceCatalog.ts` waliduje schema 3, stabilne
  ID, zakresy, kompletność profili, rewizję i zapis.
- Zmiana zapisanych danych wymaga rebuilda i restartu aplikacji; nie ma hot
  reloadu ani JobRunnera symulacji balansu.

## Ustalone decyzje

- Obsługiwany format to obecne `schemaVersion: 3`; nieznana lub niepoprawna
  wersja jest jawnie odrzucana, bez fallbacku do edytora JSON.
- Formularz edytuje istniejące profile i pola, ale nie dodaje, usuwa ani nie
  zmienia kolejności profili, ataków, ability ani pasm trudności.
- Stabilne ID profili są tylko do odczytu. Wszystkie pozostałe pola schema 3
  mają być dostępne w formularzu.
- Serwer pozostaje autorytatywnym walidatorem. Nie zmieniamy reguł walidacji,
  wartości w pliku balansu ani logiki gry.
- Zachowujemy istniejące endpointy, kontrolę SHA-256, `expectedHash`, atomowy
  zapis i automatyczne podbijanie `balanceRevision`.
- Żadna wartość liczbowa w niekompletnym polu nie jest po cichu zamieniana na
  zero lub inną wartość domyślną. Zmiana wariantu union pokazuje wymagane pola
  jako nieuzupełnione i blokuje zapis do czasu podania poprawnych wartości.

## Układ formularza

Nagłówek zachowuje tytuł, status operacji i akcje:

- **Reload** — ponowne pobranie dokumentu; przy niezapisanym draftcie wymaga
  potwierdzenia odrzucenia;
- **Validate** — walidacja aktualnego draftu przez `POST /api/balance/validate`;
- **Save** — aktywny dla zmienionego i poprawnego draftu; przed `PUT` ponownie
  uruchamia walidację;
- widoczne metadane schema, bieżącej rewizji i SHA-256.

Główne zakładki:

1. **Bosses** — selektor istniejącego profilu; formularz health, collision,
   movement, attacks, special abilities i ammunition immunities.
2. **Units** — selektor jednostki; supply, health, movement, attack,
   fortress damage, incoming damage i contact movement.
3. **Ammunition** — selektor amunicji; sekcje Reload, Inventory, Flight,
   Collision, Damage, Terrain i Behavior.
4. **Spells & Mana** — selektor zaklęcia i osobna sekcja konfiguracji many.
5. **Runtime** — podzakładki Economy, Fortresses, Catapult, Match, Physics,
   Wind, Combat, Boss Combat, AI Profiles, AI Strategy, Rewards i Campaign.

Listy profili są wybierane przez dropdown lub panel nawigacyjny, zachowując
bieżącą kolejność źródłową. Zmiana zakładki albo profilu nie resetuje draftu.

### Kontrolki według typu danych

- liczby: kontrolka numeryczna z jednostką, `min`/`max`/`step` tam, gdzie
  wynikają z kontraktu, oraz pomocniczym opisem; wartości czasu pokazują ms lub
  s, a odległości world units;
- enumy: dropdown z wartościami obsługiwanymi przez walidator;
- boolean: checkbox lub switch z widoczną etykietą;
- `hitLayers`, `immuneAmmunitionTypes`, `availableLayers`: kontrolki
  wielokrotnego wyboru, serializowane bez niezamierzonej zmiany kolejności;
- warianty `AmmunitionImpactBehavior`: dropdown `DIRECT_IMPACT`,
  `EXPLOSION` albo `LINGERING_AREA`, pokazujący pola właściwe dla wariantu;
- spell effect: pola tylko dla efektu dozwolonego dla wybranego stabilnego
  `SpellType`; nieimplementowane efekty pozostają bez `effect`;
- zagnieżdżone ataki, abilities i pasma trudności: formularze stałych,
  istniejących wpisów bez operacji dodawania/usuwania/reorderowania.

Puste i częściowo wpisane wartości liczbowe pozostają tekstem w stanie
formularza, dopóki nie da się ich poprawnie sparsować. Serializer odmawia
stworzenia dokumentu, jeżeli pole jest puste, niefinitywne lub nie spełnia
reguły zakresu.

## Model, walidacja i zapis

1. `GameBalanceDocumentV3` i adapter formularza należą do
   `studio/web/src/tabs/balance/model.ts`. API nadal transportuje tekst i hash;
   nie dodajemy współdzielonego API typu ani drugiego źródła balansu.
2. Przed parsowaniem `BalanceTab` używa istniejącego `POST
   /api/balance/validate`, który odrzuca błędne pola, brakujące wymagane dane
   i nieznane klucze w całym dokumencie. Parser dodatkowo sprawdza wersję i
   kształt korzenia; błąd nie może nadpisywać pliku.
3. Draft formularza oddziela surowe wartości edytowanych inputów od typowanego,
   sparsowanego dokumentu. Dirty state porównuje dane formularza z bazowym
   dokumentem, a nie z przypadkowym formatowaniem tekstu JSON.
4. Serializer tworzy pełny dokument schema 3 w stabilnej kolejności pól i
   zachowuje wszystkie profile oraz ich kolejność.
5. Walidacja frontendowa pilnuje poprawności składni inputów i podstawowych
   zakresów, ale nie zastępuje walidatora serwera.
6. Wyniki serwera z `path` są przypisywane do odpowiednich pól; błędy
   przekrojowe pozostają w widocznym podsumowaniu i wskazują ścieżkę JSON.
   Nieznane ścieżki błędów nie są ukrywane.
7. `Save` wysyła dokument z ostatnim `expectedHash`. Po sukcesie aktualizuje
   baseline, hash i rewizję. `409` zachowuje draft i wymaga jawnego Reload;
   formularz nie nadpisuje zmian spoza Studio.
8. Rewizja jest podbijana według obecnego mechanizmu tylko wtedy, gdy draft
   zmienił wartości i użytkownik nie zmienił jej samodzielnie.

## Granice odpowiedzialności

- Formularz należy do `studio/web/src/tabs/balance/`.
- Klient webowy korzysta z istniejących `fetchBalance`, `validateBalance`
  i `saveBalance` w `studio/web/src/shared/api`.
- Plik balansu zapisuje wyłącznie `studio/server/src/services/balanceCatalog.ts`.
- Nie dodajemy zależności Studio do Kotlin ani uruchamiania Gradle z serwera.
- Nie zmieniamy symulacji, AI, Androida, katalogów domenowych ani wartości
  `game-balance.json`.

## Etapy implementacji

1. **Model i adapter — wdrożone.** Dodano jawny typ schema 3, parsowanie, draft
   inputów, walidację formatu i serializację. Testy obejmują checked-in JSON,
   nieznaną wersję, pusty numeric draft i przełączanie wariantu zachowania.
2. **Nawigacja formularza — wdrożona.** Utworzono zakładki, selektory istniejących
   profili i layout formularza zgodny z resztą Studio.
3. **Pola schema 3 — wdrożone.** Dodano kontrolki i wszystkie pola Bosses, Units,
   Ammunition, Spells & Mana i Runtime, w tym uniony i zagnieżdżone rekordy.
4. **Walidacja i save lifecycle — wdrożone.** Błędy są mapowane do pól;
   obsłużono
   Validate/Save/Reload, dirty state, potwierdzenie odrzucenia i `409`.
5. **Dokumentacja i weryfikacja — wdrożone.** Zaktualizowano README oraz
   architekturę Studio; testy, lint i build web przechodzą.

## Proponowane pliki

- `studio/web/src/tabs/balance/BalanceTab.tsx` — API, lifecycle i zapis;
- `studio/web/src/tabs/balance/BalanceForm.tsx` — zakładki, profile i pola;
- `studio/web/src/tabs/balance/BalanceFields.tsx` — wspólne kontrolki pól;
- `studio/web/src/tabs/balance/model.ts` — schema 3, parser, draft i serializer;
- `studio/web/src/tabs/balance/model.test.ts` — round-trip i przypadki błędne;
- `studio/web/src/tabs/balance/styles.css` — layout, responsywność i stany pól;
- `docs/architecture/studio-architecture.md` i `studio/README.md` — opis
  aktualnego formularza i przepływu.

Nazwy plików komponentów są propozycją; przed ich dodaniem należy sprawdzić
istniejące wzorce i nie rozbijać formularza na abstrakcje bez realnego
konsumenta.

## Kryteria akceptacji

- Nie ma edytowalnego `textarea` z surowym JSON-em ani równoległego fallbacku.
- Formularz udostępnia każde istniejące pole schema 3, bez pominiętych sekcji.
- Stable IDs, liczebność i kolejność wszystkich list pozostają niezmienne.
- Wczytanie i niezmieniony zapis nie zmieniają wartości balansu.
- Pusta, częściowa lub niepoprawna wartość nie jest po cichu konwertowana na
  liczbę ani wysyłana do zapisu.
- Błędy serwera, także niepowiązane z widoczną zakładką, pozostają dostępne.
- Save zachowuje hash, `409`, atomic write i rewizję.
- Testy sprawdzają round-trip pełnego schema 3, edycję reprezentatywnych pól
  ze wszystkich sekcji, uniony, walidację i konflikt zapisu.
- `cd studio && npm test && npm run lint && npm run build` przechodzi.
- `git diff --check` przechodzi.

## Poza zakresem

- dodawanie, usuwanie i zmiana kolejności profili lub nested records;
- zmiana balansu/obecnych wartości jako część prac nad UI;
- edycja kodu gry, uruchamianie symulacji balansu i raporty `JobRunner`;
- hot reload balansu w aplikacji;
- edycja ręczna JSON-a z UI.
