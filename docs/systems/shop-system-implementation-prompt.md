# Prompt implementacji ShopSystem

Użyj poniższego promptu jako samodzielnego zadania implementacyjnego w
repozytorium Urkaaaz.

---

## Prompt

Zaimplementuj end-to-end **ShopSystem** zgodnie z zaakceptowanym projektem w
`docs/systems/shop-system.md`. Zakres obejmuje kontrakt balansu i edytor
Studio, transakcyjne zakupy, trwały stock, jego użycie i rozliczanie w
kampanijnym meczu oraz ekran Shop. Nie kończ na samym ekranie ani na
przechowywaniu liczb, które nie ograniczają faktycznych użyć w runtime.

Przed zmianami przeczytaj:

- `AGENTS.md`;
- `docs/architecture/architecture.md`;
- `docs/architecture/implementation-rules.md`;
- `docs/architecture/project-map.md`;
- `docs/project/current-state.md` i `docs/project/knowledge.md`;
- `docs/systems/shop-system.md`;
- `docs/systems/shop-screen-mockup.agp`;
- `docs/systems/player-progression-system.md` i jego prompt
  implementacyjny;
- `docs/systems/loadout-system.md` i jego prompt implementacyjny;
- `docs/systems/reward-system.md`;
- `docs/systems/spell-system.md`;
- `docs/architecture/studio-architecture.md`,
  `studio/README.md` oraz aktualny plan i prompt formularza Balance;
- kod i testy aktualnych kontraktów profilu, balansu, MatchSession,
  symulacji, nawigacji, Shop placeholdera i DataStore.

Dokumenty są wymaganiami architektonicznymi, nie dowodem, że opisana
funkcjonalność już działa. Sprawdź bieżący kod, testy, zależności, gałąź i
`git status --short`. Zachowaj istniejące zmiany użytkownika; niczego nie
cofaj, nie stashuj, nie nadpisuj i nie formatuj hurtowo.

### Decyzje użytkownika — nie pytaj ponownie

- ShopSystem jest lokalnym sklepem za OrCoins. UI pokazuje OrCoins, a profil
  używa istniejącego pola `goldBalance`.
- MVP pozwala kupować po jednej sztuce na transakcję; zakup natychmiast
  atomowo odejmuje koszt i dodaje stock. Nie ma koszyka ani autoekwipowania.
- Dla amunicji `game-balance.json` przechodzi na schema 4 i zyskuje nullable,
  nieujemne `shopCostGold`; `null` oznacza „niesprzedawana”. Zachowaj ceny
  legacy: `POWDER_BARREL` = 250, `FIRE_RAIN` = 350,
  `PLAGUE_CAULDRON` = 500.
- Ceny czarów pochodzą z istniejącego `goldCost`. Oferta obejmuje czary
  specjalne: `CATAPULT_LOCK`, `SLOWING_FIELD`, `VAMPIRIC_BANNER`,
  `STONE_SKIN`, `SIEGE_FRENZY`, `BERSERKER`. HASTE i HEAL są wyłączone ze
  sprzedaży, bo pochodzą z milestone'ów kampanii.
- Jeden zakup zwiększa stock o 1. Nie ma limitu gameplayowego zapasu;
  przekroczenie `Int.MAX_VALUE` dla salda lub stocku ma być jawnie odrzucone.
- Profilowy stock limitowanej amunicji ogranicza liczbę udanych użyć BLUE w
  kampanijnym meczu. Stock niewykorzystany w meczu zostaje w profilu.
- Stock rozlicza się po każdym `MatchFinished`, niezależnie od wyniku, oraz
  przed jawnym przerwaniem lub restartem, które nie emitują `MatchFinished`.
- Shop można otworzyć z Dashboardu i Loadoutu. Powrót z Loadoutu prowadzi do
  tego samego przedmiotu, a zakup nie wybiera go automatycznie.
- Użytkownik zaakceptował możliwość kupienia zaklęć specjalnych, których
  efekty nie są jeszcze zaimplementowane. Nie implementuj tych efektów ani
  nie omijaj `SPELL_NOT_IMPLEMENTED`. Sklep i powiązany status Loadoutu muszą
  jasno mówić, że te zaklęcia nie są obecnie możliwe do rzucenia.
- RED/AI i demo nie dziedziczą profilu stocku BLUE. Zachowaj ich odrębne,
  dotychczasowe zasady zapasu.
- Zapis stocku następuje po meczu; pełna gwarancja po przymusowym ubiciu
  procesu przed settlementem nie należy do MVP.
- Nie twórz nowego modułu Gradle, drugiego profilu, katalogu amunicji/czarów,
  balansu, loadoutu ani store'a. Nie generuj nowych grafik.
- Nie commituj, nie pushuj ani nie zmieniaj gałęzi.

### Etap 1 — audyt i katalog balansu schema 4

1. Sprawdź aktualne schema version, decoder, walidatory Gradle i Studio,
   fixtures JSON oraz wszystkie testy, które zakładają schema 3.
2. Rozszerz istniejący `AmmunitionDefinition` i DTO balansu o
   `shopCostGold: Int?`. Waliduj `null` lub wartość całkowitą `>= 0`; nie
   dopuszczaj wartości ujemnej, ułamkowej ani cichego fallbacku.
3. Podnieś wspólny kontrakt balansu do schema 4 i zaktualizuj bundled JSON
   oraz rewizję zgodnie z istniejącą konwencją. Zachowaj pozostałe wartości
   balansu. Ustaw ceny tylko dla zatwierdzonych limitowanych typów amunicji;
   ROCK, `SIEGE_BOMB` i inne typy nie mogą stać się ofertą bez decyzji.
4. Pozostaw `AmmunitionType` stabilnym ID. Nie umieszczaj ceny w Androidzie,
   AI, Studio jako osobnej kopii ani w nowym katalogu sklepowym.
5. Zaktualizuj Studio:
   - ścisły walidator `studio/server/src/services/balanceCatalog.ts`;
   - kontrakty/model schema w `studio/web/src/tabs/balance/model.ts`;
   - formularz `BalanceForm.tsx`, aby nullable cena była edytowalna jako pole
     formularza, nie jako JSON;
   - fixtures i testy walidatora, API, modelu oraz formularza;
   - README i architekturę Studio w miejscach opisujących schema 3.
6. Zachowaj dotychczasowe bezpieczeństwo zapisu, SHA-256, atomowość,
   optymistyczną kontrolę konfliktu i walidację pełnego dokumentu.

Nie dodawaj migracji z nieistniejącego produkcyjnego formatu lub losowych
wartości domyślnych. Jeśli kod obsługuje jawnie wcześniejsze schema, zachowaj
wyraźne reguły kompatybilności i testy; nie akceptuj schema 3 jako schema 4
bez jawnej migracji.

### Etap 2 — transakcje PlayerProgression i zakupy

Użyj istniejącego `LoadoutProgressionSnapshot` oraz pól
`goldBalance`, `ammunitionStocks` i `specialSpellStocks`. Rozszerz
`PlayerProgressionRepository` o jawną, atomową mutację aktualnego profilu;
dokładna nazwa i kształt wyniku mają pasować do istniejących kontraktów.

- Use case zakupu musi pobrać/zmienić aktualny stan w jednej transakcji.
  Nie wykonuj `loadOrInitialize → modyfikacja w pamięci → zapis pełnego
  snapshotu`, bo RewardSystem może równolegle dodać złoto.
- W tej samej atomowej aktualizacji sprawdź istnienie i typ pozycji,
  dostępność w ofercie, cenę, profil, saldo oraz overflow; dopiero potem
  odejmij cenę i dodaj dokładnie jeden stock.
- Amunicja sprzedawana jest tylko wtedy, gdy jest limitowana i ma
  nie-null `shopCostGold`. Czary sprzedawane są tylko wtedy, gdy są
  `special`; nie dodawaj HASTE ani HEAL do oferty.
- Źródłem ceny czaru jest istniejące `goldCost`. Nie kopiuj cen do kodu
  Androida ani do osobnego katalogu.
- Wielokrotne jawne zakupy tej samej pozycji dodają kolejne pojedyncze stocki,
  aż do granicy typu `Int`; nie wprowadzaj ukrytego limitu ani zasady
  jednorazowego zakupu.
- Zwracaj typowane wyniki dla sukcesu, niewystarczających środków, nieznanej
  lub niesprzedawanej pozycji, błędu konfiguracji/profilu, overflow i błędu
  storage. Każda odmowa pozostawia saldo i stock bez zmian.
- Przedstawiaj katalog jako read model z aktualnym saldem, ceną, posiadanym
  stockiem, statusem sprzedaży i statusem obsługi w runtime. Reuse istniejący
  balans i prezentacyjne projekcje tam, gdzie pasują.

Współdziel profile codec i wzorzec atomowej modyfikacji z
`DataStoreRewardSettlementAdapter`. Adapter nagród aktualizuje saldo,
CampaignProgress i receipts; nowe zakupy lub settlementy muszą zachować te
klucze i nie mogą przywracać starego `goldBalance`/stocku. Nie dodawaj drugiej
niezależnej serializacji profilu ani drugiego DataStore.

Settlement meczu musi mieć trwałą idempotencję dla identyfikatora generacji
meczu. Przechowuj receipt w tej samej transakcji co odjęcie stocku i zapewnij,
że retry po wyniku niepewnym nie pobierze stocku drugi raz. Wybierz
minimalny, wersjonowany format zgodny z istniejącym storage; nie twórz
nieograniczonego ledgeru bez uzasadnienia. Błąd storage jest jawnym
failure'em, nigdy success-shaped fallbackiem.

### Etap 3 — stock wejściowy i deterministyczny runtime

Dodaj niemutowalny, player-scoped kontrakt wejściowego stocku dla konkretnej
generacji meczu. Umieść go przy istniejącym `MatchScenario` lub równoważnym
kontrakcie scenariusza, zachowując rozdział:

- `CampaignLevelDefinition` opisuje statyczną definicję poziomu;
- `PlayerLoadoutConfig` opisuje zatwierdzony wybór;
- kontrakt inventory opisuje stock dostępny w tej generacji;
- trwały profil pozostaje poza runtime'em i należy do PlayerProgressionSystemu.

Przekaż stock z profilu przez nawigację Loadout → MatchScreen, scenariusz,
`MatchSession` i `SimulationConfig` do runtime'u. Nie inicjalizuj stocku
kampanijnego BLUE z `AmmunitionDefinition.inventory.startingCount`.

- Dla wybranej, dostępnej na poziomie limitowanej amunicji BLUE dokładna liczba
  użyć pochodzi z profilu. Brak stocku ma blokować commit loadoutu lub start
  meczu z jawnym wynikiem; nie używaj liczby startowej jako fallbacku.
- Zachowaj aktualne zachowanie dostępności poziomu i zatwierdzonego
  `PlayerLoadoutConfig`; posiadanie stocku nie odblokowuje pozycji na poziomie
  ani nie wybiera jej automatycznie.
- Zapas RED/AI i demo pozostaje odrębny. Nie przekazuj profilu BLUE do AI ani
  do trybu demo.
- Właścicielem pozostałego stocku w meczu jest runtime. Amunicja musi
  respektować istniejące `ResourceWallet`, które zmniejsza stan przy udanym
  strzale.
- Uwzględnij wyłącznie udane zdarzenia konsumpcji. Dla amunicji użyj
  `ProjectileFired` z `shooterTeam == BLUE`; dla przyszłych castów
  stockowanych czarów użyj udanego `SpellCast` właściwego gracza. Odrzucona
  komenda nie zużywa stocku.
- Nie implementuj efektów czarów specjalnych ani nie zmieniaj
  `SPELL_NOT_IMPLEMENTED`. Zakup i stock mogą istnieć mimo braku używalnego
  efektu; UI musi informować o ograniczeniu.
- Zachowaj wejściowe ilości stocku w konfiguracji replay. Ten sam seed,
  konfiguracja, stock i komendy muszą dawać tę samą symulację.
- Replay nie może zmieniać DataStore, kupować ani rozliczać profilu.

Zaktualizuj testy inicjalnego `ResourceWallet`, scenariusza, eventów i
replayu. Nie zmieniaj bazowych zapasów RED, demo ani ogólnych reguł balansu,
jeśli nie jest to konieczne do izolowanego stocku BLUE.

### Etap 4 — tracker i finalizacja stocku

Dodaj osobnego, aplikacyjnego właściciela zliczania zużycia meczu. Nie
przeciążaj `RewardTracker` i nie polegaj na `consumeEvents`, ponieważ lista
eventów sesji może zostać wyzerowana przez kolejną komendę. `MatchSession`
ma przekazywać uporządkowane eventy observerowi w miejscu ich powstania.

- Filtruj po identyfikatorze generacji meczu i `EventId`; duplikaty oraz eventy
  innej drużyny/meczu nie mogą zwiększyć zużycia.
- Zliczaj tylko typy stockowane i faktyczne użycia; trafienie, obrażenia,
  odłamki, reload i eventy AI nie są kolejnym zużyciem amunicji.
- Przy `MatchFinished` rozlicz stock dla każdego wyniku: BLUE_WIN, RED_WIN,
  DRAW, timeout i surrender.
- `MatchSession.stop()` i `restart()` obecnie nie emitują
  `MatchFinished`. Przed skasowaniem runtime'u/finalizacją starej generacji
  rozlicz dotychczasowe użycia. Restart tworzy nową generację z aktualnego
  profilu; nie przenosi stanu runtime starej próby.
- Ponieważ DataStore jest asynchroniczny, nie używaj `runBlocking` ani
  synchronicznego I/O. Dostosuj application/gateway/ViewModel flow do
  `suspend` lub jawnego asynchronicznego wyniku.
- Przy błędzie settlementu pozostaw symulację/tracker lub jawny pending
  settlement do retry. Nie pozwól rozpocząć kolejnej generacji z nierozliczonym
  stockiem, nie zgub błędu i nie przedstawiaj go jako sukcesu.
- Modyfikuj bieżący profil przez transakcyjny delta/debit na jego świeżym
  stanie, a nie zapis starego snapshotu z początku meczu. Jeśli profil nie ma
  wymaganej liczby sztuk w chwili settlementu, zwróć jawny konflikt zamiast
  zejść poniżej zera lub cicho skorygować stan.
- Zachowaj jawnie ograniczenie MVP: tracker wyłącznie w pamięci nie daje
  gwarancji po przymusowym ubiciu procesu przed settlementem. Nie rozszerzaj
  tego na trwałą rezerwację/per-action write bez osobnej zgody.

### Etap 5 — Android Shop i nawigacja

Zastąp `ShopScreen` placeholder działającym, screen-owned ekranem. Dodaj lub
rozszerz ViewModel/controller; `MainActivity` ma pozostać cienkim miejscem
składania zależności.

- Wejście z Dashboardu i Loadoutu ma używać `AppScreen`/`AppNavigator` oraz
  istniejącego wzorca nawigacji.
- Powrót z Dashboardu wraca do Dashboardu. Wejście z Loadoutu zachowuje
  przedmiot szczegółów i po powrocie pokazuje ten sam element.
- Po udanym zakupie odśwież saldo/stock i zachowaj wybrany element. Nie
  autoekwipuj przedmiotu i nie zapisuj loadoutu jako skutku zakupu.
- UI zawiera dokładnie dwie kategorie `SPELLS` i `AMMUNITION`, aktualne OrCoins,
  live ceny/stock/status, szczegóły pozycji i jedną akcję zakupu.
- `shopCostGold == null` nie daje działającej akcji zakupu. Brak złota,
  loading, błąd profilu, błąd storage i wynik zakupu muszą być jawnie
  przedstawione.
- Dla niezaimplementowanych czarów specjalnych pokaż widoczny status, że
  obecnie nie można ich rzucić. Nie blokuj zatwierdzonej sprzedaży, ale nie
  przedstawiaj zakupu jako nowego efektu gameplayowego.
- Kontrolki nie mogą zawierać reguł cen, bilansu, stocku ani settlementu.
  Korzystaj z istniejących konwencji stringów/lokalizacji i stylu UI; nie
  dodawaj parallel localization system.
- Użyj `docs/systems/shop-screen-mockup.agp` oraz aktualnych ekranów
  Dashboard/Campaign/Loadout jako referencji. Jeśli używasz
  `assets_src/shop/screen-concepts/shop_background_reference.png`, traktuj
  ją wyłącznie jako oddzielną warstwę scenery. Nie używaj gotowego konceptu
  jako spłaszczonego ekranu, nie zasłaniaj/nie duplikuj sprzedawcy, nie
  regeneruj tła i nie dodawaj niezatwierdzonych assetów.
- Traktuj `assets_src/shop/screen-concepts/shop_ui_reference.png` jako
  główną, zapisaną referencję kompozycji i proporcji UI. Odwzoruj jej układ:
  `BACK` w lewym górnym rogu, obramowany tytuł `SHOP` obok, saldo OrCoins
  w prawym górnym rogu, sprzedawca i lada po lewej, a po prawej duży
  ciemny panel ze złotą/brązową ramą. W panelu umieść zakładki u góry,
  trzykolumnową siatkę kart po lewej stronie panelu i oddzielony panel
  szczegółów wybranego przedmiotu po prawej.
- Dopasuj wizualną hierarchię, odstępy, kształt ramek, ciemne drewno/metal,
  ciepłe oświetlenie rynku i oszczędne złote podświetlenia do referencji.
  Nie używaj samego zrzutu jako tła ani nie spłaszczaj go do runtime ekranu;
  wszystkie wartości, tekst, karty i kontrolki pozostają osobnymi elementami
  UI.
- Referencja zawiera `ARTIFACTS`, pozycje spoza sklepowej oferty oraz miesza
  czary z amunicją. Nie kopiuj tych treści ani trzeciej zakładki: utrzymaj
  dokładnie `SPELLS` i `AMMUNITION`, pokazuj wyłącznie wybraną kategorię, a
  pozycje i ceny bierz z zaakceptowanego katalogu balansu/progresji.
- Nie dodawaj RESET, language selector, bezpośredniego wejścia do match,
  koszyka ani niezwiązanego UI.

### Etap 6 — testy

Dodaj focused testy każdego właściciela. Co najmniej:

**Balance i Studio**

- schema 4 przechodzi przez Kotlin decoder oraz Studio GET/validate/PUT;
- `shopCostGold` przyjmuje `null` i nieujemne liczby całkowite, odrzuca
  wartość ujemną, ułamkową i błędny typ;
- formularz odczytuje i zapisuje ceny bez utraty innych pól, hash/rewizja i
  kontrola konfliktu nadal działają;
- ceny są poprawne dla trzech zatwierdzonych typów i nie zmieniają
  pozostałego balansu.

**Zakupy i profil**

- poprawny zakup amunicji/czaru odejmuje dokładną cenę i dodaje dokładnie
  jeden stock;
- `null`, nieznane ID, HASTE/HEAL, pozycja spoza oferty, niewystarczające
  saldo, błędny profil, storage failure, stock/gold overflow nie zmieniają
  żadnego pola profilu;
- wielokrotne zakupy to osobne pojedyncze transakcje aż do granicy `Int`;
- współbieżny zakup i rozliczenie nagrody nie tracą zmian saldo/stocku;
- codec odrzuca uszkodzone/nieobsługiwane dane bez resetu do profilu startowego.

**Runtime i settlement**

- kampanijna amunicja BLUE otrzymuje dokładnie stock z profilu, a `startingCount`
  nie daje dodatkowych sztuk; profil nie zmienia RED/AI ani demo;
- tylko sukces strzału/castu zużywa stock; eventy są przypisane do
  właściwego meczu, drużyny i typu;
- `EventId` duplikowany, event innej generacji, fire rejection i cast
  rejection nie powodują drugiego debitu;
- każdy wynik `MatchFinished`, stop i restart rozlicza użycie dokładnie raz;
- niewykorzystane stocki zostają, retry nie odejmuje ponownie;
- failure storage zatrzymuje przejście do kolejnej generacji i jest widoczny;
- replay z tym samym wejściowym stockiem jest deterministyczny i nie zmienia
  profilu.

**UI i architektura**

- Dashboard → Shop → Dashboard oraz Loadout → Shop → ten sam item;
- zakup odświeża saldo/stock i nie autoekwipuje;
- Shop ma dokładnie dwie kategorie, live values, jawne błędy i status
  nieobsługiwanych czarów;
- brak logiki gameplayowej/cen w UI i brak Android/DataStore zależności w core;
- reset/recreate screen lifecycle nie powoduje podwójnego zakupu ani
  settlementu.

### Etap 7 — dokumentacja i walidacja

Po wdrożeniu zaktualizuj dokumentację tylko do stanu rzeczywiście
zaimplementowanego:

- `docs/systems/shop-system.md` — status, kontrakty, lifecycle, testy i
  pozostałe ograniczenia;
- dokumenty PlayerProgression, Loadout, Reward i Spell tylko w punktach,
  których zachowanie faktycznie zmieniono;
- `docs/architecture/architecture.md`,
  `docs/architecture/project-map.md`, `docs/project/knowledge.md`,
  `docs/architecture/studio-architecture.md` i `studio/README.md`;
- `AGENTS.md` i `docs/agent-context-index.md` — linki do projektu Shop i tego
  promptu, jeśli jeszcze ich nie zawierają;
- `docs/project/CHANGELOG.md` z krótkim, widocznym dla użytkownika wpisem pod
  aktualną datą, jeśli realizacja zmienia zachowanie aplikacji.

Nie dopisuj pustych kategorii changeloga ani ogólnego wpisu o postępie. Nie
deklaruj gwarancji crash-safety ani używalności niezaimplementowanych czarów.

Uruchom odpowiednie testy Kotlin i Androida oraz wszystkie testy/typowanie/
build Studio, jeśli jego pliki się zmieniły. Z katalogu głównego wymagane:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

W `studio/`, jeśli zmiany obejmują Studio:

```bash
npm test
npm run lint
npm run build
```

Nie instaluj ani nie aktualizuj pakietów bez potrzeby. Najpierw uruchom
istniejące narzędzia; instaluj tylko po zmianie manifestu albo konkretnym
błędzie brakującej zależności. Napraw regresje wprowadzone przez tę zmianę i
ponów właściwe walidacje.

Nie commituj, nie pushuj, nie zmieniaj gałęzi i nie cofaj pracy użytkownika.
W podsumowaniu wymień zmienione pliki, testy/build/check rzeczywiście
uruchomione, pozostałe blokery i ograniczenia, bieżącą gałąź oraz potwierdź
brak commita i pushu.

---
