# Prompt pełnej implementacji LoadoutSystem

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

---

### Later scope clarification

The approved Campaign milestone work in
[`campaign-milestones-plan.md`](campaign-milestones-plan.md) supersedes the
tutorial requirements below: do not implement level 6/8 tutorials, tutorial
metadata, flags, or tutorial UI as part of LoadoutSystem. A future cross-game
TutorialSystem will own those responsibilities. Campaign milestones remain
authorized by CampaignSystem and affect campaign spell entitlement and
new-default-loadout projection only.

## Prompt

Zaimplementuj kompletny, rozszerzalny **LoadoutSystem** dla jednostek,
amunicji i czarów zgodnie z:

- `docs/systems/loadout-system.md` — zaakceptowaną architekturą, regułami
  parytetu legacy, granicami odpowiedzialności, integracją, planem assetów i
  ekranem szczegółów otwieranym z Loadoutu;
- `docs/systems/loadout-screen-mockup.agp`,
  `docs/systems/loadout-item-detail-mockup.agp` i
  `docs/systems/loadout-spell-card-art.agp` — wizualnym kierunkiem oraz
  przyszłymi grafikami; w tym zadaniu nie generuj ani nie dodawaj tych
  grafik;
- zrzutem ekranu Loadoutu dostarczonym przez użytkownika — układ landscape
  16:9, trzy sąsiadujące panele jednostek/amunicji/czarów oraz stały dolny
  pasek z podsumowaniem i akcją startu; nie kopiuj fikcyjnej zawartości;
- `docs/architecture/architecture.md`, `AGENTS.md`, `docs/architecture/implementation-rules.md`,
  `docs/architecture/project-map.md` i `docs/project/knowledge.md` — aktualną architekturą,
  workflow i stanem repozytorium;
- `docs/systems/campaign-system.md` — przepływem Campaign → Loadout → Match;
- `docs/systems/spell-system.md` — granicą odpowiedzialności spelli i
  zakazem przejmowania gameplayu SpellSystemu przez loadout;
- aktualnym kodem oraz testami. Dokumenty opisują zamiar, nie dowodzą, że
  kontrakt albo funkcja są już zaimplementowane.

Nie kończ na analizie ani planie. Zaimplementuj, zintegruj, przetestuj i
zaktualizuj dokumentację w bieżącej sesji, o ile nie trafisz na rzeczywisty
blocker wymagający decyzji użytkownika.

### Zatwierdzone decyzje — nie pytaj ponownie

- Loadout obejmuje wybór **jednostek, amunicji i czarów**.
- Zachowaj zachowanie legacy opisane w `docs/systems/loadout-system.md`,
  dostosowując odpowiedzialności do obecnych modułów. Nie kopiuj legacy plik
  do pliku.
- Nie importuj zapisanych wyborów ani preferencji z aplikacji legacy.
- `game-application` jest właścicielem use case’ów, walidacji, defaultów i
  portu zapisu. Adapter persystencji, ekran i ViewModel należą do
  `android-app`.
- Draft jest tymczasowym stanem ekranu; zapisuj wyłącznie zatwierdzony wybór.
- Zachowaj odrębność dostępności poziomu, odblokowań/zapasów oraz
  zatwierdzonego wyboru.
- Poziom 6 zachowuje odblokowanie/dodanie Haste i tutorial loadoutu; poziom 8
  zachowuje odblokowanie/dodanie Heal, dodanie dostępnego Demolishera i
  tutorial loadoutu.
- Karta elementu Loadoutu ma osobne `ⓘ`: jej główna powierzchnia zmienia
  wybór, a `ⓘ` otwiera wspólną, pełnoekranową podstronę szczegółów. Powrót
  zachowuje niezapisany draft i scroll.
- Cała gra, w tym Loadout, działa w orientacji landscape. Ekran główny ma
  responsywny układ trzech paneli obok siebie i stale widoczny dolny pasek.
  Kafle każdej kategorii tworzą osobną karuzelę poziomą: trzy widoczne naraz,
  pozostałe wybierane gestem lewo/prawo. Wszystkie trzy karuzele używają
  jednakowego wyglądu kafli. ROCK jest pierwszą, stale wybraną kartą amunicji;
  kliknięcie pozostałych typów zaznacza je do pierwszego wolnego slotu, a
  ponowne kliknięcie usuwa je. Nie pokazuj osobnego wyboru aktywnego slotu ani
  drugiej karuzeli amunicji.
- We wszystkich karuzelach pokazuj najpierw zaznaczone i dostępne opcje,
  następnie niedostępne, zablokowane i bez stocku; zachowuj kolejność katalogu
  wewnątrz grup i przeliczaj ją po każdej zmianie stanu.
- Utrzymuj identyczny, stały rozmiar kafli we wszystkich karuzelach. Długie
  nazwy i statusy zawijaj lub obcinaj, nie zwiększaj przez nie wymiarów kafla.
- Wybrany kafelek oznacz świecącym złotym obramowaniem i ciemnym, złoto
  obwiedzionym tickiem w prawym górnym rogu zamiast widocznego napisu
  `SELECTED`. Osobny przycisk `ⓘ` umieść po lewej stronie, bez nakładania
  obu kontrolek.
- Użytkownik wygeneruje grafiki z istniejących promptów AGP i doda je później.
  W tej implementacji użyj istniejących zasobów, nie twórz ani nie
  referencjonuj nieistniejących drawable i nie blokuj działania ekranu z
  powodu brakujących ilustracji.
- Zakupy i progresja pozostają własnością progresji/sklepu. Nie odejmuj złota
  ani nie zmieniaj stocku bez jawnego use case’u/portu właściciela progresji.
- Nie implementuj efektów gameplayowych czarów jako części LoadoutSystemu.
  Loadout udostępnia wybór SpellSystemowi przez kontrakt meczu.
- Nie twórz nowego modułu Gradle ani drugiego katalogu balansu, amunicji,
  jednostek, czarów, kampanii, persystencji lub nawigacji.
- Nie commituj, nie pushuj ani nie zmieniaj gałęzi.

### Reguły legacy do zachowania

**Jednostki**

- Od 1 do 3 unikalnych typów; zachowaj kolejność wyboru.
- Default: pierwsze maksymalnie trzy dostępne typy w kolejności
  `SiegeUnitType.entries`; obecna kolejność `UnitConfigurations.all` zachowuje
  tę kolejność z legacy.
- Nie pozwalaj odznaczyć ostatniej jednostki. Nie pozwalaj wybrać jednostki
  niedostępnej na poziomie.
- Kosztów ani statystyk jednostek nie kopiuj do LoadoutSystemu.

**Amunicja**

- Dokładnie 3 sloty. `ROCK` jest stały i zawsze zajmuje slot 1.
- Sloty 2 i 3 są opcjonalne, nie mogą zawierać duplikatów i mogą wskazywać
  tylko typy dostępne na poziomie, do których gracz ma stock.
- Default: `ROCK` oraz pierwsze maksymalnie dwa inne dostępne typy w
  kanonicznej kolejności; pozostałe sloty są puste.
- Loadout nie zużywa stocku. Amunicję ograniczoną konsumuje runtime meczu.

**Czary**

- Maksymalnie 3 unikalne czary. Zatwierdzenie startu meczu wymaga co najmniej
  jednego czaru.
- Default: maksymalnie trzy dostępne i odblokowane typy w kanonicznej
  kolejności.
- Zwykły czar musi być dostępny na poziomie. Czary specjalne legacy mogą być
  wyposażane niezależnie od dostępności poziomu, ale tylko przy dodatnim
  stocku; bez stocku selektor prowadzi do sklepu.
- Dostępny, ale zablokowany zwykły czar może być kupiony przez przypadek
  użycia progresji za cenę z istniejącej definicji. Czary specjalne prowadzą
  do sklepu. Odmowa zakupu, np. brak złota, jest jawna i nie zmienia draftu.
- Loadout nie implementuje aktywacji, targetowania ani efektów czarów.

**Odczyt i zatwierdzenie**

- Przy odczycie filtruj zapisane typy względem bieżącego poziomu,
  odblokowań i stocku. Jeśli filtrowanie pozostawi puste jednostki lub czary,
  użyj odpowiednich deterministycznych defaultów.
- Zwracaj typowaną informację o nieznanych, zablokowanych, niedostępnych,
  zduplikowanych lub nadmiarowych pozycjach i użyciu defaultu; nie ukrywaj
  korekt w cichym fallbacku.
- Odrzucaj błędne interaktywne wybory jawnie. Błąd nie zapisuje loadoutu, nie
  nawiguje do meczu i nie zmienia progresji.
- Ponownie waliduj świeżą dostępność/stock podczas zatwierdzania.
- Persystuj wyłącznie po udanym zatwierdzeniu.

### Bramka zależności i zakresu

Na początku sprawdź bieżące implementacje:

1. Jeśli istnieje aplikacyjny system progresji, użyj jego kontraktów i
   przypadków użycia. Nie twórz drugiej kopii złota, odblokowań ani stocku.
2. Jeśli progresja nie istnieje, zdefiniuj wyłącznie minimalne, typowane
   porty/snapshoty niezbędne dla LoadoutSystemu i testuj je przez fake’i.
   Nie twórz niejawnej ani sukcesopodobnej implementacji produkcyjnej.
3. Jeżeli parytet poziomów 6/8 wymaga brakującego kontraktu, sprawdź jego
   właściwego właściciela i dodaj tylko minimalne, typowane rozszerzenie po
   tej stronie, z testami. Nie przenoś odblokowań do ViewModelu ani
   LoadoutSystemu. Zakupy pozostają poza zakresem; jeśli nie ma działającej
   transakcji, nie udawaj jej sukcesu i opisz konkretny blocker.
4. Sprawdź SpellSystem. Jeśli jego runtime nie istnieje, LoadoutSystem ma
   dostarczyć wybrany zestaw czarów w kontrakcie startu meczu, lecz nie może
   udawać, że aktywacja czarów działa.

Nie twórz osobnego tymczasowego loadoutu dla MatchScreen ani równoległej
walidacji w Androidzie. Jeśli odpowiedzialność istnieje już w kodzie,
rozszerz ją lub przenieś atomowo po zidentyfikowaniu wszystkich konsumentów.

### Workflow i ochrona pracy użytkownika

1. Odczytaj wymagane dokumenty, `git status --short` i bieżącą gałąź przed
   zmianami. Working tree może zawierać niepowiązane prace; zachowaj je,
   nie formatuj i nie cofaj.
2. Zbadaj implementacje oraz testy w `game-domain`, `game-contracts`,
   `game-campaign`, `game-application`, `game-simulation` i `android-app`.
   Sprawdź również legacy wyłącznie do odczytu. Nie modyfikuj checkoutu
   legacy.
3. Oddziel istniejące działające funkcje od kontraktów/szkieletów. Zapisz
   krótką kolejność prac opartą na rzeczywistych zależnościach i rozpocznij
   implementację.
4. Nie pytaj ponownie o zatwierdzone decyzje. Pytaj tylko o nieustaloną
   decyzję wpływającą na zachowanie, zakres, dane trwałe lub granice
   odpowiedzialności.
5. Najpierw aktualizuj kontrakty i ich testy, następnie use case’y,
   integrację meczu, adaptery Androida i testy UI.
6. Nie deleguj samodzielnych, małych etapów. Nie rób niezwiązanych refaktorów.
7. Po implementacji uruchom walidację podaną na końcu promptu. Napraw błędy
   wprowadzone przez tę zmianę i powtórz odpowiednie testy.

### Etap 1 — modele, defaulty i walidacja

Utwórz lub uzupełnij jedną kanoniczną, niemutowalną reprezentację
`MatchLoadout` dla jednostek, trzech slotów amunicji i czarów.

- Umieść ją w warstwie zgodnej z istniejącym `application.loadout` i
  `docs/architecture/architecture.md`.
- Zbadaj obecny `game-domain.SpellLoadout` oraz wszystkich jego konsumentów.
  Reużyj/przenieś odpowiedzialność, nie utrzymuj drugiej niezależnej listy
  defaultów ani równoległego modelu.
- Używaj istniejących stabilnych identyfikatorów i katalogów. Nie wprowadzaj
  Android `R`, nowych wartości balansu ani `Any`.
- Modele odrzucają strukturalnie błędne dane. Walidator aplikacyjny sprawdza
  dostępność względem poziomu oraz progresji i zwraca typowane błędy, nie
  teksty używane jako kontrakt.
- Defaulty mają być stabilne i pokryte testami dokładnych kolejności.
- Zapisany wybór może być oczyszczony przez jawny rezultat normalizacji.
  Bieżący wybór użytkownika nie może być po cichu poprawiany podczas
  zatwierdzenia.
- Brak jednostki lub czaru blokuje start zgodnie z regułą legacy; w stanie
  draftu można reprezentować pusty wybór czarów, by ekran mógł go pokazać i
  naprawić.

Zastosuj małe, jawne typy wyników dla odczytu, akcji wyboru, zakupu i
zatwierdzenia. Nie używaj szerokich `catch`, nieznanych wartości jako
domyślnych ani sukcesopodobnych rezultatów błędów.

### Etap 2 — przypadki użycia i persystencja

W `game-application` zaimplementuj use case’y inicjalizacji/odczytu
loadoutu, aktualizacji wyboru (jeśli wymaga jej architektura) i
zatwierdzenia/startu meczu. Właścicielem trwałego zapisu wyboru jest jawny
port `LoadoutRepository`; Android implementuje adapter tego portu.

- Repository zapisuje tylko wybór jednostek, slotów amunicji i czarów.
- Nie zapisuje złota, odblokowań ani zapasów i nie konsumuje ich.
- Zapisuj wybór dopiero po pełnej walidacji, przed nawigacją do meczu.
- Odczyt nieznanych lub nieaktualnych danych zwraca typowane informacje o
  korektach/defaultach. Nie importuj wartości z preferencji aplikacji legacy.
- Transakcja zakupu/odblokowania czaru deleguje do właściciela progresji;
  nie zmieniaj zapisu loadoutu, jeśli zakup się nie powiedzie.
- W przypadku błędu zapisu zgłoś go jawnie i nie zwracaj wyniku sugerującego
  udany start.

Zachowaj czytelną granicę między warstwą portu a adapterem platformy. Core
nie może zależeć od `Context`, `SharedPreferences` ani klas Androida.

### Etap 3 — kontrakt scenariusza i runtime meczu

Prześledź przepływ `CampaignLevelDefinition` → `MatchScenario` →
`LocalMatchGateway` → `MatchSession` → `SimulationConfig`/symulacja.
Obecne `available*` opisuje dostępność, a nie wybór gracza.

- Rozszerz niemutowalny kontrakt scenariusza/uruchomienia meczu tak, by
  nieść dostępność poziomu i zatwierdzony loadout gracza jako odrębne dane.
- Nie kieruj zależności `game-contracts` lub `game-simulation` do
  `game-application`. Jeśli model aplikacyjny wymaga projekcji na kontrakt,
  mapuj go jawnie na granicy aplikacji przy użyciu stabilnych ID.
- Dodaj brakujące pole dostępnych jednostek, jeśli jest wymagane do
  walidacji; nie używaj `initialUnits` jako listy dostępności ani loadoutu.
- Przekaż konfigurację przez gateway i MatchSession do jedynego runtime’u.
  Pauza/restart nie tworzą drugiego stanu wyboru; restart używa tego samego
  zatwierdzonego scenariusza.
- Egzekwuj wybór przy `SelectAmmo` i `DeployUnit` oraz udostępnij listę
  wybranych czarów przez kontrakt SpellSystemowi. Jeśli SpellSystem nie
  obsługuje jeszcze `CastSpell`, nie implementuj jego efektów i jawnie
  odnotuj tę granicę.
- Odrzucenie komendy spoza dostępności/wyboru jest jawne i nie zużywa stocku
  ani innych zasobów.
- Nie zmieniaj zasad przeciwnika AI ani nie przyznawaj mu zapisanego
  loadoutu gracza.

Dodaj testy, że pozycja dostępna na poziomie, lecz niewybrana, nie może być
użyta przez gracza; poprawna wybrana pozycja działa zgodnie z istniejącym
runtime’em.

### Etap 4 — Campaign i Android UI

Podłącz Campaign → Loadout → Match przez obecny `AppNavigator`,
`AppScreen` i `ScreenHost`. Zachowaj typowany kontekst wybranego poziomu.
Nie przenoś selektorów ani reguł do `MainActivity`.

Zastąp placeholder `LoadoutScreen` screen-owned widokiem/controllerem i
ViewModelem, który:

- pokazuje podsumowania/liczniki jednostek, amunicji i czarów;
- umożliwia wybór kategorii w legacy flow i pokazuje opisy/informacje;
- udostępnia wspólny pełnoekranowy widok szczegółów jednostki, amunicji lub
  czaru; dane i opis wybiera po stabilnym ID i korzysta z kanonicznych
  konfiguracji zamiast kopiować balans;
- prezentuje dostępność, unlock, stock i saldo z read modelu aplikacji;
- umożliwia użycie istniejących ikon/assetów i lokalizacji;
- blokuje wybór niedostępnych typów, ale prezentuje typowany błąd;
- aktywuje start wyłącznie, gdy wybrano co najmniej jedną jednostkę i jeden
  czar, a konfiguracja przechodzi walidację;
- zachowuje draft po zwykłym odświeżeniu tego ekranu, ale nie zapisuje go
  przed zatwierdzeniem;
- zachowuje flow poziomu 6/8 i targety tutoriali, a reguły odblokowania
  deleguje do progresji.

UI wysyła typowane akcje; nie filtruje dostępności samodzielnie, nie
odejmuje złota, nie zmienia stocku i nie tworzy kopii `MatchLoadout`.
Karty muszą oddzielić kliknięcie wyboru od przycisku `ⓘ`. `ⓘ` otwiera
szczegóły, nie zmienia draftu i nie staje się globalną trasą nawigacji;
podstrona należy do przepływu Loadout. Strzałka oraz systemowy Back zamykają
szczegóły jako pierwsze i przywracają ten sam draft, scroll oraz stan
prezentacyjny. Nie przebudowuj ekranu w sposób, który ponownie wczyta zapis i
zgubi niezapisane wybory.

Szczegóły prezentują nazwę, krótki prawdziwy opis, właściwości ze źródeł
autorytatywnych i stan dostępności. Nie wymyślaj mechaniki ani statystyk; nie
przedstawiaj efektów SpellSystemu, których runtime nie obsługuje, jako
działającej funkcji. Oglądanie nie kupuje, nie odblokowuje, nie wybiera ani
nie aktywuje elementu.

**Grafiki będą dodane później przez użytkownika.** Wykorzystaj tylko zasoby,
które istnieją w repozytorium. Nie generuj, nie pobieraj ani nie dodawaj
nowych obrazów i nie wpisuj do kodu nazw brakujących `R.drawable`. Zaimplementuj
pełną funkcjonalność na obecnych zasobach. Gdy ilustracji brakuje, pokaż
poprawną, natywną kartę opartą na nazwie/tekście bez udawania, że obraz
istnieje; wynik mapowania assetu ma pozostać jawny i testowalny. Nie używaj
losowej ani błędnej grafiki jako placeholdera. Dodanie ilustracji z promptów
AGP ma później wymagać jedynie uzupełnienia androidowego mapowania assetów.
Brak tłumaczenia również nie może być ukryty przez niepowiązany tekst.

### Etap 5 — testy

Dodaj lub rozszerz testy jednostkowe, integracyjne i UI odpowiednio do
istniejących wzorców. Sprawdź co najmniej:

- walidację konstruktorów i dokładne limity/unikalność/kolejność wszystkich
  trzech kategorii;
- defaulty dla pustego, częściowego i pełnego zestawu dostępności;
- slot 1 zawsze `ROCK`, opcjonalne sloty, duplikaty oraz stock ograniczonej
  amunicji;
- filtrowanie zapisanego wyboru według dostępności poziomu, unlocków i
  stocku, wraz z typowanym raportem i defaultami;
- odrzucenie niepoprawnej akcji interaktywnej bez zmiany draftu/persystencji;
- sukces i awarię zapisu bez fałszywego startu meczu;
- zatwierdzenie po ponownej walidacji świeżego snapshotu;
- poziom 6: Haste i tutorial; poziom 8: Heal, dostępny Demolisher i tutorial;
- zakupy: sukces/odmowa przez port progresji bez mutacji UI;
- mapowanie dostępności i zatwierdzonego wyboru do scenariusza jako
  rozdzielnych danych;
- komendy meczu dla wybranych/niewybranych jednostek i amunicji; brak
  częściowego zużycia przy odrzuceniu;
- restart z zachowaniem tej samej zatwierdzonej konfiguracji;
- ekran: start przy pustym wyborze, liczniki, back/discard draft, raporty
  walidacji i dostępność tutoriala;
- szczegóły otwierane dla jednostki, amunicji i czaru; osobny hit target `ⓘ`
  nie zmienia selekcji; zamknięcie strzałką i systemowym Back zachowuje draft
  oraz pozycję scrolla;
- brak ilustracji zaklęć nie odwołuje się do brakujących zasobów, nie psuje
  kompilacji ani działania selektorów; brakujące mapowanie jest jawnie
  raportowane/testowane;
- brak importów Android w core i brak reguł gameplayu/progresji w UI.

Testy sprawdzają konkretne wyniki, nie tylko utworzenie klas lub wywołanie
metod. Nie wyłączaj testów ani nie osłabiaj asercji, aby ukryć brak funkcji.

### Etap 6 — dokumentacja i stan wiedzy

Zaktualizuj `docs/systems/loadout-system.md`, tak aby odróżniał pierwotny plan
od faktycznie zaimplementowanych kontraktów i zachowań. Zachowaj link do tego
promptu oraz do plików AGP; odnotuj, że grafiki są oczekiwane w osobnym kroku.
Zaktualizuj też
`docs/project/knowledge.md`, `docs/architecture/project-map.md`, `docs/architecture/architecture.md` i/lub
`docs/reference/GLOSSARY.md` tylko tam, gdzie implementacja wprowadza trwałą zmianę
odpowiedzialności, przepływu lub nowy termin.

Nie deklaruj funkcji jako zaimplementowanej, jeśli istnieją tylko modele,
stub, ekran bez runtime’u albo port bez adaptera produkcyjnego. Zgłoś
pozostałe granice, zwłaszcza nieistniejącą integrację progresji lub
SpellSystemu, i nie przejmuj ich odpowiedzialności po cichu.

### Kryteria ukończenia

Uznaj zadanie za ukończone tylko, gdy:

- jeden kanoniczny `MatchLoadout` obejmuje jednostki, amunicję i czary;
- reguły, defaulty, filtrowanie i parytet legacy są pokryte testami;
- błędne wybory i awarie persystencji są jawne, bez cichego sukcesu;
- zapis loadoutu jest niezależny od gold, odblokowań, stocku i runtime’u;
- zatwierdzony wybór dociera do MatchSession/symulacji przez
  niemutowalny kontrakt i jest egzekwowany dla aktualnie obsługiwanych
  komend;
- progresja i SpellSystem nie są dublowane przez LoadoutSystem;
- ekran loadoutu działa z aktualną nawigacją, bez logiki systemu w
  `MainActivity`;
- testy i bramka walidacji przechodzą.

Jeżeli któraś zależność blokuje pełny parytet, dokończ niezależne części
tylko wtedy, gdy nie tworzysz drugiego właściciela, a pozostały blocker
opisz precyzyjnie. Nie nazywaj zadania ukończonym.

### Pełna walidacja

Uruchom z katalogu głównego repozytorium:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Napraw błędy wprowadzone przez tę zmianę i powtórz pełną bramkę. Nie
przypisuj temu zadaniu niezależnych, wcześniejszych awarii bez sprawdzenia.
Zachowaj wszystkie niepowiązane zmiany.

Nie commituj ani nie pushuj. W końcowym podsumowaniu podaj:

- ukończone etapy i działające elementy LoadoutSystemu;
- listę zmienionych plików;
- wynik testów, buildu i `git diff --check`;
- nierozwiązane integracje lub blockery;
- potwierdzenie, że w tym zadaniu nie generowano ani nie dodawano grafik;
- bieżącą gałąź oraz potwierdzenie braku commita i pushu.

---
