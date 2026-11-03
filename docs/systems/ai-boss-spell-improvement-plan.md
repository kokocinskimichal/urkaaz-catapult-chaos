# Plan poprawy AI: cele bossów i używanie czarów

> Status: zaimplementowano. Poniżej opisano rezultat oraz zachowano pierwotne
> uzasadnienie i kryteria zakresu.

## Cel

Poprawić faktyczną skuteczność AI przeciw bossom centralnym oraz dodać
deterministyczne, sytuacyjne używanie czarów przez AI. AI nadal podejmuje
decyzje wyłącznie na podstawie `MatchSnapshot` i wysyła zwykłe
`MatchCommand`; nie przejmuje runtime'u bossów, czarów ani symulacji.

## Rezultat implementacji

- Dla żywego `CENTRAL_OBJECTIVE` AI wybiera legalną, gotową amunicję bez
  immunitetu bossa i szuka kąta/mocy, których przewidywany lot trafia hitbox.
  Predykcja używa wspólnego `Projectile.advance`, aktualnego wiatru, balansu
  lotu i próbek terenu. Seedowany rozrzut profilu jest nakładany po znalezieniu
  rozwiązania; mobile siege boss zachowuje dotychczasowe targetowanie.
- Test integracyjny kampanii potwierdza event `BossDamaged` od RED AI na
  centralnym bossie; testy jednostkowe obejmują targetowanie mobile bossa,
  profile oraz przeładowaną amunicję.
- Rejestr AI zawiera sytuacyjne polityki HASTE, FORTRESS_SHIELD i
  RUNIC_BASTION. W jednej decyzji emitowany jest najwyżej jeden cast,
  wyłącznie dla dostępnego, zaimplementowanego czaru z maną i poprawną
  sytuacją. `AiAgent` odrzuca rejestr bez polityki dla runtime-zaimplementowanego
  czaru.
- `runtime.aiStrategy.specialSpellStockPerMatch` w balance schema 6 ustala
  stock RED dla każdego poziomowo dostępnego czaru specjalnego. Bieżące stany
  BLUE/RED są w runtime wallet i snapshotach; `MatchSession` zużywa stock
  dopiero po zaakceptowanym castcie. BLUE nadal korzysta ze swojego
  `PlayerInventoryConfig`.

## Potwierdzone decyzje

- Priorytet atakowania bossa dotyczy **central objectives** rozstrzyganych
  wyścigiem obrażeń. AI RED ma zadawać obrażenia bossowi zamiast traktować
  fortecę BLUE jako podstawowy cel.
- Mobile siege boss pozostaje sprzymierzony z przeciwnikiem RED i naciera na
  BLUE. AI nie atakuje go; zachowuje dotychczasowe cele jednostek i fortecy.
- Każdy czar z zaimplementowanym efektem runtime ma otrzymać politykę użycia
  przez AI. Czar jest rzucany wtedy, gdy jest dostępny i sytuacyjnie przydatny,
  nie jest wymuszany w każdym meczu.
- AI RED dostaje **jedno użycie każdego dostępnego czaru specjalnego na mecz**.
  Stock jest konfigurowany balansem i odrębny od inventory BLUE.
- Dodanie AI policy nie implementuje brakujących efektów SpellSystemu.
  AI nie może wysłać skutecznego castu, dopóki runtime efekt nie jest
  zaimplementowany.

## Stan przed implementacją i przyczyna problemu

### Cele bossów

Wspólna funkcja `fireAt` w
`game-ai/src/main/kotlin/com/urkaaaz/ai/AiStrategies.kt` wybiera pozycję
`BossSnapshot` dla `CENTRAL_OBJECTIVE`. Z tej samej funkcji korzystają
strategie `simple`, `situational` i `counterplay`. To jest wybór współrzędnych,
nie dowód, że lot pocisku zakończy się trafieniem w bossa.

Moc jest liczona głównie jako pozioma odległość podzielona przez
`horizontalDistancePerPowerPercent`, a kąt jako kąt linii wzroku z dodanym
stałym `aimAngleOffsetDegrees` i błędem profilu. W bieżącym balansie używane są
wartości `12` world units na punkt mocy i offset `42°`; odległości większe niż
1200 world units nasycają moc do 100. Dla centralnego celu boss i forteca mogą
więc dostać bardzo podobną moc, a wynik nadal zależy od niedopasowanej do
balistyki heurystyki kąta.

Obecny test `AiAgentTest.aiTargetsTheCentralObjectiveWhenOneIsActive`
sprawdza, że kąt różni się od zwykłego strzału. Nie sprawdza toru pocisku,
`BossDamaged`, trafienia w fortecę ani udziału AI w damage race. Również wybór
amunicji następuje przed uwzględnieniem celu bossa i nie używa osobnego
priorytetu obrażeń bossa.

### Czary

`AiStrategyRegistry` składa dziś strategie katapulty/amunicji, rekrutacji i
wysyłania fal. `AiAgent` nie generuje `MatchCommand.CastSpell`, a
`AiMatchCoordinator` nie przekazuje agentowi listy czarów dopuszczonych przez
scenariusz ani inventory RED.

`MatchScenario.availableSpells` jest wspólne dla obu drużyn, natomiast
`playerLoadout` i `PlayerInventoryConfig` opisują BLUE. `MatchSession`
waliduje dostępność poziomu, zatwierdzony loadout BLUE oraz
`SpellDefinition.isImplemented`; nie ma równoległej polityki AI ani
ograniczonego stocku czarów RED. Z katalogu 11 typów tylko HASTE,
FORTRESS_SHIELD i RUNIC_BASTION mają obecnie efekty runtime; pozostałe
niezaimplementowane czary są jawnie odrzucane.

## Plan zmian

### 1. Ustalić cel encounteru jako politykę AI

- Dla żywego `BossArchetype.CENTRAL_OBJECTIVE` wyznaczyć bossowi priorytet
  celu katapulty i amunicji. Nie wracać do fortecy BLUE, dopóki boss jest
  aktywnym celem damage race.
- Opierać rozróżnienie na typowanym encounterze/boss snapshot, nie na numerze
  poziomu, map markerze ani stringowym `bossId`.
- Gdy boss nie istnieje, został pokonany albo mecz nie ma central objective,
  zachować dotychczasowy wybór celu.
- Nie kierować AI RED na mobile siege boss: w obecnym modelu jest on po stronie
  przeciwnika, który naciera na BLUE.
- Jednostki pozostawić w istniejącej ścieżce `BossSystem`; nie dodawać
  równoległego AI-targetingu jednostek, jeśli symulacja już kieruje ich na
  aktywny boss encounter.

### 2. Ulepszyć celowanie tak, by trafiało w hitbox

- Zastąpić „kąt do punktu + stały offset” ograniczoną, deterministyczną oceną
  kandydatów kąta i mocy względem hitboxa bossa.
- Kandydaci muszą uwzględniać amunicję, jej prędkość/drag/grawitację, aktualny
  wiatr, geometrię celu, teren i kolizje. Celem jest przewidywane
  `BossDamaged`, nie tylko różny parametr `Aim`.
- Użyć wspólnego, czystego predyktora trajektorii dla AI i symulacji albo
  jawnego portu, który zachowa dozwolony kierunek zależności. Nie kopiować
  drugiego modelu balistyki do Androida ani nie dodawać zależności
  `game-ai -> android-app`.
- Dobierać amunicję razem z rozwiązaniem strzału. Uwzględniać immunitety
  bossa, obrażenia przypisywane bossowi, dostępny stock i reload. Wcześniej
  odfiltrować amunicję przeładowywaną: obecnie `fireAt` wybiera najlepszy typ,
  a dopiero później sprawdza reload, więc może nie wybrać gotowej alternatywy.
- Jeżeli boss pozostaje celem, a obecna amunicja nie ma rozwiązania, sprawdzić
  pozostałe legalne typy albo poczekać na gotowy strzał; nie zmieniać cicho
  celu na fortecę.
- Zachować deterministyczne błędy celowania profili i seed meczu. Heurystyka
  celności nie może przyznawać bonusów obrażeń ani zasobów.

### 3. Dodać politykę używania czarów przez AI

- Rozszerzyć istniejący `AiStrategyRegistry` o jawny rejestr polityk
  `SpellType -> AiSpellPolicy` (lub równoważny mechanizm używający obecnych
  granic). Polityka ocenia snapshot i zwraca kandydata `CastSpell` z typem
  celu oraz deterministycznym utility score.
- Dodać do kontekstu AI listę spell IDs dostępnych w `MatchScenario` i
  odczyt RED: mana, aktywne efekty/statusy, cele i pozostały AI spell stock.
  Nie używać loadoutu ani inventory BLUE jako konfiguracji przeciwnika.
- Kandydat może powstać wyłącznie wtedy, gdy czar jest dostępny na poziomie,
  ma zaimplementowany efekt, AI ma wystarczającą manę i stock, cel jest
  prawidłowy, a ponowne użycie nie narusza reguł aktywnego efektu.
- W jednej reakcji wybierać najwyżej jeden cast, aby nie wysyłać kilku
  sprzecznych lub nieważnych komend w tym samym ticku. Pozostałe decyzje
  katapulty/rekrutacji/fal zachować zgodnie z obecnym cadence.
- Dla istniejących efektów przygotować sytuacyjne polityki, np. HASTE przy
  kilku aktywnie walczących sojusznikach, FORTRESS_SHIELD przy zagrożonej
  fortecy bez aktywnej tarczy, RUNIC_BASTION przy nacierających wrogach blisko
  RED gate. Progi i stocki mają być balansowalne, nie rozproszone po UI.
- Efekty pozostają w SpellSystemie. AI wyłącznie wysyła `CastSpell`, a
  `MatchSession`/symulacja wykonują istniejącą walidację, zużycie i efekt.
- Stock specjalnych czarów AI ma mieć osobną, skończoną konfigurację w
  `GameBalanceConfig`, a pozostały stock — autorytatywnego właściciela
  runtime'u oraz niemutowalny odczyt w snapshotie. Nie kopiować stocku BLUE.
  Konfiguracja początkowa wynosi 1 dla każdego dostępnego czaru specjalnego;
  nie wprowadzać profilo-zależnego zwiększania stocku.

### 4. Wymusić kompletność dla nowych czarów

- Każdy `SpellDefinition` z `isImplemented == true` musi mieć dokładnie jedną
  politykę AI. Test katalogu/polityk ma wykrywać nowy runtime spell bez
  obsługi AI.
- Nowy czar nie uzyskuje automatycznie reguł użycia przez samą obecność w
  `SpellCatalog`; jego efekt, target policy, AI policy i testy są dodawane
  razem.
- Czar z `effect == null` nie jest wybierany i nie generuje odrzuconych
  komend.
- Polityka wykorzystuje `SpellTargetCategory` i stabilny `SpellType`; nie
  duplikuje parametrów efektu ani zasad w rendererze/Android UI.

## Etapy i weryfikacja

1. **Boss objective:** testy target selector dla central, mobile i poziomu bez
   bossa; end-to-end symulacja sprawdza eventy obrażeń z deterministycznym
   seedem. Test nie kończy się na porównaniu kąta.
2. **Aim evaluation:** testy różnych amunicji, wiatru, terenu, immunitetu i
   reloadu. Porównać liczbę `BossDamaged` i `FortressDamaged` w zdefiniowanych
   scenariuszach; przed ustaleniem wymaganego hit rate zapisać baseline i nie
   wymyślać progu bez decyzji balansowej.
3. **Spell contract/inventory:** testy `availableSpells`, many, stocku RED,
   aktywnego efektu, złego celu i jawnych odrzuceń; odrzucenie nie zużywa
   zasobu.
4. **AI policies:** dla każdego zaimplementowanego czaru test pozytywny,
   negatywny i test poprawnego `MatchCommand.CastSpell`; zmiana seeda oraz
   powtórzenie tych samych wejść pozostają deterministyczne.
5. **Replay/warstwy:** ten sam scenariusz, seed, komendy i ticki odtwarzają
   identyczne eventy/snapshoty. AI nie mutuje symulacji; efekty są wyłącznie
   w istniejących systemach runtime.
6. **Dokumentacja i balans:** aktualizować AI/SpellSystem, balance schema i
   Studio wyłącznie wtedy, gdy dodano nowe typed pola balansu.

## Kryteria akceptacji

- W central damage-race AI RED kieruje decyzje katapulty na aktywnego bossa;
  rzeczywiste trafienia są mierzone przez `BossDamaged`, a nie wnioskowane
  wyłącznie z komendy `Aim`.
- AI nie atakuje mobile siege bossa sprzymierzonego z RED; poziomy bez bossów
  zachowują dotychczasowe targetowanie.
- Reload lub immunitet wybranego typu amunicji nie powodują cichego powrotu do
  fortecy, gdy central boss nadal żyje.
- Każdy zaimplementowany, dostępny czar z wystarczającą maną/stockiem ma
  sytuację, w której AI może wysłać prawidłowy cast; nie ma wymogu rzucania
  każdego czaru w każdym meczu.
- Nowy runtime spell bez AI policy powoduje błąd testu kompletności.
- AI nie dostaje bonusów statystyk/zasobów poza zatwierdzonym RED spell stock.
- Replay z tym samym wejściem jest identyczny; `MatchViewModel` i renderer
  nie przejmują reguł spell/boss gameplayu.

## Otwarte dane balansowe

- Wymagany minimalny hit rate bossa nie został ustalony. Test integracyjny
  potwierdza rzeczywiste trafienie w jednym deterministycznym scenariuszu z
  profilem ELITE; nie definiuje to progu skuteczności dla wszystkich profili,
  map i warunków.
