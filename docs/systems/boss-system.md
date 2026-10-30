# BossSystem — architektura i plan implementacji

## Status

**Plan architektoniczny; system nie jest zaimplementowany.** Dokument opisuje
docelowy system bossów oraz jego pierwszą migrację z legacy. Nie dodaje kodu,
kontraktów Kotlin, testów ani assetów.

Zatwierdzony zakres:

- jeden `BossSystem` obejmuje mobilnych bossów poziomów co 10 oraz nieruchome
  cele centralne poziomów co 5;
- pierwsza migracja obejmuje istniejące profile i grafiki legacy dla poziomów
  5–50;
- pierwsze zachowania odwzorowują legacy, w tym atak obronny celu centralnego
  i enrage mobilnego bossa; nowe ataki dystansowe, np. strzelanie, są odłożone;
- balansem będzie można iterować w testach; rozbieżności HP pomiędzy profilami
  nie blokują projektu ani implementacji;
- profile balansu znajdują się w jednym pliku JSON w repozytorium i są
  edytowane w Urkaaaz Studio; zapis wymaga rebuilda oraz ponownego uruchomienia
  aplikacji, bez hot reloadu;
- poziomy 55–100 pozostają niedostępne w kampanii do czasu przypisania im
  kompletnych profili bossów i assetów.

## Cel i zakres

`BossSystem` odpowiada za definicje bossów oraz ich deterministyczne
zachowanie podczas meczu. Obsługuje dwa archetypy:

1. **Central objective** — stacjonarny cel, który przyjmuje obrażenia od obu
   drużyn, może okresowo atakować zaangażowane jednostki, a po zniszczeniu
   rozstrzyga wynik na podstawie obrażeń zadanych przez każdą drużynę.
2. **Mobile siege boss** — boss poruszający się w kierunku fortecy BLUE,
   walczący z jednostkami i atakujący fortecę po dotarciu do bramy. Może
   przejść w stan enrage przy progu zdrowia zdefiniowanym w profilu.

System obejmuje wybór profilu na poziomie, statystyki, ataki, umiejętności,
runtime state, obrażenia, warunki zakończenia spotkania, snapshoty, eventy,
podstawową prezentację oraz authoring balansu w Studio.

### Poza zakresem pierwszego etapu

- nowe zdolności bossów, w tym ataki dystansowe i pociski;
- profile bossów i dedykowane grafiki dla poziomów 55–100;
- zmiana balansu innych systemów poza koniecznym przygotowaniem wspólnego
  źródła konfiguracji;
- nowy moduł Gradle, skrypty wykonywalne w JSON, hot reload oraz trwały zapis
  runtime state bossa;
- szczegółowe rozpisanie zadań do realizacji oraz kompletna, finalna
  specyfikacja JSON-a; dokument opisuje architekturę i fazy, a kształt pliku
  balansu jest ilustracyjny i zostanie sfinalizowany podczas implementacji.

## Istniejące rozwiązania i ryzyko duplikacji

W repozytorium nie ma jeszcze runtime bossów ani celu centralnego. Istnieją
natomiast kanoniczne definicje balansu w `game-domain`:

- `UnitConfigurations`;
- `AmmunitionCatalog`;
- `SpellCatalog` i `ManaConfiguration`.

Ich wartości są konsumowane przez symulację, AI, loadout i projekcje dla UI.
Dodanie osobnego `BossBalanceCatalog` obok przyszłego wspólnego pliku balansu
stworzyłoby kolejne źródło prawdy. Rekomendacja: rozszerzać jeden typowany
model balansu i migrować istniejące katalogi do JSON-u etapami; nie utrzymywać
tej samej wartości jednocześnie w JSON-ie i Kotlinie.

`CampaignChapterRegistry` i dane `bossPositions` opisują landmarki na mapie
kampanii. Nie są identyfikatorami runtime bossa ani jego pozycją w świecie.
Przypisanie bossa do poziomu musi być osobną, typowaną częścią definicji
poziomu/scenariusza.

`CampaignLevelDefinition` tworzy scenariusze kampanii, a `MatchScenario`
zawiera obecnie `VictoryRulesConfig` z ogólną regułą pokonania przeciwnika.
Nie ma jeszcze pola `bossId`, konfiguracji spawnu bossa ani reguł zwycięstwa
dla tych spotkań. `DeterministicMatchSimulation` jest właścicielem istniejącego
runtime meczu i jego wyniku — BossSystem ma zostać włączony do tego lifecycle,
nie tworzyć równoległego silnika.

Studio ma istniejące wzorce lokalnego API, walidacji danych, oczekiwanego hasha
przy zapisie, błędu konfliktu `409` oraz atomowej podmiany pliku. Balance tab
powinien użyć tych wzorców, ale nie współdzielić katalogu ani kontraktu z
edytorem map.

## Właściciele i moduły

Nie powstaje nowy moduł Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| Typowane profile balansu i czyste reguły walidacji | `game-domain` |
| Kanoniczny plik `game-balance.json` | zasób `game-domain` |
| Odczyt/parsowanie JSON-u, mapowanie do modelu domeny i błędy konfiguracji | adapter/repository w `game-application` |
| Przypisanie `bossId`, archetypu spotkania, spawnu i zasad poziomu | `game-campaign` |
| Stabilne kontrakty scenariusza, snapshotów, eventów i wyniku | `game-contracts` |
| Jedyny runtime state bossów i wykonywanie reguł | `BossSystem` wewnątrz `game-simulation` |
| Wybór celów przez przeciwnika na boss-level | `game-ai`, na podstawie snapshotów i zwykłych decyzji AI |
| Asset keys, mapowanie grafik, HUD i renderowanie | `android-app` |
| Walidacja, edycja i zapis kanonicznego JSON-u | `studio/server` i nowa zakładka `studio/web` |

### Kierunek zależności

```text
Urkaaaz Studio
  -> lokalne API: waliduj / zapisuj game-balance.json z expectedHash
  -> game-domain/src/main/resources/balance/game-balance.json

game-application
  -> wczytuje i waliduje JSON
  -> przekazuje niemutowalny GameBalanceConfig do sesji i symulacji

game-campaign -> MatchScenario(bossId, spawn, victory rule)
game-simulation -> BossSystem -> MatchSnapshot + MatchEvent
game-ai -> MatchSnapshot -> zwykłe decyzje/komendy
android-app -> mapper -> BattlefieldRenderer / boss HUD
```

`game-domain` pozostaje bez zależności od Androida i nie wykonuje I/O.
Deserializacja nie trafia do symulacji. Model domenowy jest zwykłym, typowanym
modelem Kotlin; repository aplikacyjne odpowiada za plik i dekodowanie.

## Konfiguracja balansu

Plik źródłowy:

```text
game-domain/src/main/resources/balance/game-balance.json
```

Schemat rozróżnia:

- `schemaVersion` — wersję struktury JSON;
- `balanceRevision` — rewizję wartości balansu, zapisywaną przy uruchomieniu
  meczu/replayu.

Pierwsza wersja pliku ma autorytatywną sekcję `bosses`. Sekcje `units`,
`ammunition`, `spells` i wspólne parametry gry są dodawane w kolejnych etapach
migracji, równolegle z usunięciem dotychczasowych wartości z ich katalogów
Kotlin. Puste sekcje nie mogą udawać, że istniejący balans został już
przeniesiony.

Każdy profil bossa zawiera:

| Pole | Znaczenie |
|---|---|
| stabilny `id` | identyfikator używany przez definicję poziomu |
| `archetype` | `CENTRAL_OBJECTIVE` albo `MOBILE_SIEGE_BOSS` |
| `maxHealth` | maksymalne HP |
| `movement` | `STATIC` albo ruch postępowy z prędkością w world units/s |
| `attacks` | typ ataku, legalne cele, obrażenia, zasięg i cooldown |
| `specialAbilities` | lista typowanych zdolności i ich parametrów |

Profil zawiera też `collision` (rozmiar hitboxa w world units), bo trafianie
bossa przez pociski, eksplozje i jednostki jest regułą gameplayu, a nie
prezentacji. Rozmiar sprite'a pozostaje po stronie `BossPresentationCatalog`;
hitbox i sprite nie dzielą jednej wartości.

Poglądowy kształt (nie finalna specyfikacja; wartości są placeholderami do
strojenia w testach):

```json
{
  "schemaVersion": 1,
  "balanceRevision": "2026-10-30.1",
  "bosses": [
    {
      "id": "boss.mobile.thorncrown.lvl10",
      "presentationKey": "thorncrown",
      "archetype": "MOBILE_SIEGE_BOSS",
      "maxHealth": 0,
      "collision": { "width": 0, "height": 0 },
      "movement": { "kind": "ADVANCE", "speedWorldUnitsPerSecond": 0 },
      "attacks": [
        { "kind": "MELEE", "targets": ["UNIT"], "damage": 0,
          "range": 0, "cooldownSeconds": 0 },
        { "kind": "MELEE", "targets": ["FORTRESS_GATE"], "damage": 0,
          "range": 0, "cooldownSeconds": 0 }
      ],
      "specialAbilities": [
        { "kind": "ENRAGE", "healthFractionThreshold": 0,
          "damageMultiplier": 0, "speedMultiplier": 0 }
      ]
    }
  ]
}
```

Jeden profil opisuje jedno spotkanie. Poziomy powtarzające grafikę (5/45,
10/50) dostają **osobne profile** o własnych `id`, wskazujące wspólny
`presentationKey`; dzięki temu późniejszy poziom może mieć inny balans bez
łamania wcześniejszego. `presentationKey` to jedyne pole prezentacyjne w
profilu i jest tylko stabilnym identyfikatorem, nie nazwą bitmapy.

`balanceRevision` jest etykietą ludzką; replay zapisuje dodatkowo hash
treści pliku (SHA-256 kanonicznego JSON-u), żeby zmiana liczb bez podbicia
etykiety nie dała po cichu niewykrywalnie innego wyniku replayu.

Ataki o różnym celu mogą mieć oddzielne obrażenia i czasy. Dla przykładu
uderzenie jednostek i uderzenie bramy nie powinny dzielić jednej wartości,
jeśli legacy lub dalszy balans określa je osobno. Profile ruchu i ataku
walidują zakresy: HP dodatnie, czasy i zasięgi poprawne dla danego archetypu,
prędkość nieujemna, a `STATIC` nie przyjmuje prędkości ruchu.

Zdolności są dyskryminowaną unią typów domenowych, a nie dowolnym tekstem,
refleksją, skryptem lub zbiorem niepowiązanych flag. Pierwsza wersja opisuje
tylko zachowania potrzebne do parytetu legacy, m.in. atak obronny celu
centralnego i enrage. `PROJECTILE_ATTACK` można dodać później jako nowy typ,
wraz z kodem symulacji i testami. Nieznana lub nieobsługiwana zdolność w
aktywnym profilu jest błędem konfiguracji.

Id bossa nie jest nazwą prezentacyjną ani kluczem bitmapy. Nazwy lokalizowane
pozostają w Android resources, a `BossPresentationCatalog` mapuje stabilne
`bossId` na asset keys. Dzięki temu zmiana grafiki nie wymaga zmiany balansu.

### Migracja istniejących katalogów

Bosses są pierwszym konsumentem JSON-u. Następna migracja obejmuje po kolei:

1. `UnitConfigurations` i konfiguracje jednostek;
2. `AmmunitionCatalog` oraz profile reloadu, inventory, lotu, kolizji i
   obrażeń;
3. `SpellCatalog`, `ManaConfiguration` i parametry efektów;
4. wspólne parametry meczu oraz poziomowe modyfikatory balansu, jeśli zostaną
   zatwierdzone.

Każdy etap ma wyeliminować duplikację pomiędzy JSON-em a Kotlinem. Wartości są
przekazywane jako jeden niemutowalny katalog do symulacji, AI oraz projekcji
loadoutu; AI i UI nie dostają własnych kopii liczb balansu.

## Kampania, scenariusz i wynik

`CampaignLevelDefinition` wybiera stabilny `bossId` i określa placement w
world-space. Rozmiar mapy, pozycja spawnu, biome i warunek zwycięstwa są
konfiguracją scenariusza, nie profilem balansu bossa. `MatchScenario` otrzymuje
niemutowalny opis spotkania bez zależności `game-contracts -> game-domain`.

Planowany kontrakt scenariusza musi jawnie rozróżniać co najmniej:

- standardowe zniszczenie fortec;
- mobilnego bossa, którego trzeba pokonać przed zniszczeniem fortecy BLUE;
- cel centralny rozstrzygany przez obrażenia obu drużyn.

Nie wolno używać `bossPositions` z katalogu map jako zastępczej konfiguracji
spotkania. Pozycja markera kampanii to presentation/authoring; pozycja bossa w
meczu należy do scenariusza poziomu.

Mapowanie pierwszej migracji:

| Pozycja w rozdziale | Profil/asset legacy |
|---:|---|
| 5 | Ancient War Oak |
| 10 | THORNCROWN |
| 15 | Frozen Troll |
| 20 | Old Yeti Sovereign |
| 25 | Copperwood Siege Lord |
| 30 | Copperwood Final Boss |
| 35 | Burntwood Warden |
| 40 | The Scorchbound King |
| 45 | Ancient War Oak |
| 50 | THORNCROWN |

Poziomy 55–100 nie są zwracane jako grywalne przez kampanię, dopóki nie mają
kompletnego boss assignment. `CampaignSystem` nie może odblokować poziomu bez
tego assignmentu; samo istnienie markera `BOSS` na mapie nie wystarcza.
Odblokowanie kolejnego poziomu po wygranej na poziomie 50 musi być ograniczone
do ostatniego poziomu z kompletną zawartością, bez zmieniania limitu
strukturalnego kampanii 100 poziomów.

`MatchEvent.MatchFinished` i istniejące `MatchOutcome` pozostają końcowym
kanałem wyniku, dzięki czemu `CampaignSystem` i `RewardSystem` nadal rozliczają
wyłącznie zatwierdzone zwycięstwo BLUE. Reguła mobilnego bossa zastępuje zwykły
warunek zwycięstwa przez zniszczenie fortecy, gdy boss jest aktywnym celem.
Zniszczenie fortecy BLUE przez bossa kończy mecz wynikiem RED_WIN.

Przy celu centralnym wynik jest wyliczany z jawnego, runtime’owego licznika
obrażeń BLUE i RED. Remis zachowuje dotychczasowy tie-break na rzecz BLUE,
chyba że osobna decyzja balansowa zmieni tę regułę.

Zachowanie przy upływie limitu czasu meczu, jeśli poziom go ma, jest
otwartą decyzją (patrz „Otwarty zakres”); do jej podjęcia scenariusze bossów
nie mogą mieć limitu czasu, żeby nie wprowadzić niezdefiniowanego wyniku.

Zwycięzca pokonania mobilnego bossa wynika z `sourceTeam` rozstrzygającego
obrażenia, niezależnie od tego, czy ostatni cios zadała jednostka, czy
pocisk. Nie przenosić legacy’owego błędu, w którym pocisk ustawiał zwycięzcę
na BLUE bez względu na drużynę strzelającą.

## Runtime state i cykl życia

Cały mutable boss state należy do jednej instancji
`DeterministicMatchSimulation`. `BossSystem` jest wyspecjalizowanym systemem
wywoływanym przez symulację, a nie osobnym silnikiem ani singletonem.

Runtime state obejmuje minimalny zestaw konieczny dla archetypu:

- identyfikator profilu i encounteru;
- aktualne HP oraz pozycję/phase dla mobilnego bossa;
- cooldowny ataków, timer ataku bramy i czas fazy enrage;
- łączny damage per team dla centralnego celu;
- identyfikatory źródeł używane do poprawnej atrybucji obrażeń.

Stan inicjalizuje się ze zwalidowanego `MatchScenario` i przypiętej rewizji
balansu na `MatchSession.start`. Restart tworzy świeży stan, pauza nie
przesuwa timerów, a zakończony mecz nie aktualizuje bossów. Zmiana pliku w
Studio nie modyfikuje aktywnego meczu.

Aktualizacja następuje w deterministycznym kroku symulacji. BossSystem może
odczytywać kandydatów ataku i przekazywać obrażenia do istniejącej ścieżki
damage resolution, ale nie mutuje bezpośrednio stanu fortec, jednostek,
pocisków ani progresji kampanii. Kolejność względem ruchu jednostek,
kolizji i obrażeń musi być zamrożona testami.

Target preference, losowanie ataku obronnego i wszelkie losowe decyzje używają
seeda meczu. Nie wprowadzają osobnego seedowanego generatora ani zależności od
kolejności iteracji kolekcji.

## Kontrakty, eventy i integracja

Planowane rozszerzenia `game-contracts`:

- pole scenariusza wskazujące boss encounter, `bossId` i placement;
- typowana reguła zwycięstwa, zamiast dodawania stringowych wyjątków;
- niemutowalny `BossSnapshot` zawierający typ/archetyp, pozycję, HP,
  max HP, fazę i — dla celu centralnego — damage totals per team;
- eventy faktów, co najmniej spawn/zmiana fazy/otrzymane obrażenia/pokonanie;
  event obrażeń zawiera target, source i `sourceTeam`.

Przepływ:

```text
CampaignLevelDefinition
  -> MatchScenario(bossId, spawn, victory rule)
  -> MatchSession / SimulationConfig(balanceRevision)
  -> DeterministicMatchSimulation
  -> BossSystem + istniejące systemy obrażeń/kolizji
  -> MatchSnapshot + MatchEvent
  -> ViewModel / mapper / battlefield renderer / boss HUD
  -> MatchFinished -> CampaignSystem / RewardSystem
```

`BossSnapshot` jest źródłem odczytu bieżącego stanu bossa dla UI. Eventy służą
krótkotrwałym efektom, np. floating damage, zmianie fazy lub animacji trafienia.
ViewModel i renderer nie wyliczają obrażeń, zwycięzcy, HP ani cooldownów.

`MatchStarted` lub zapis replayu musi zachować `balanceRevision` obok seeda i
konfiguracji scenariusza. Ten sam seed, scenariusz, wersja balansu i sekwencja
komend/ticków muszą dawać identyczne snapshoty, eventy i wynik.

## AI

AI nadal otrzymuje niemutowalny `MatchSnapshot` i wysyła zwykłe komendy przez
istniejącą granicę. Nie może mutować bossa ani mieć prywatnych wartości ataku.

Na poziomach centralnego celu strategia powinna znać ten cel jako dostępny
target, by RED mógł uczestniczyć w damage race zgodnie z legacy. Strategie
mobilnego bossa uwzględniają jego obecność i stan, ale nie omijają walidacji
komend. BossSystem nie otrzymuje bezpośrednio dostępu do AI.

## Android i assety

Wybrane przez użytkownika legacy artwork zostaje przeniesione do
`assets_src/` oraz `android-app/src/main/res/drawable-nodpi/` zgodnie z
istniejącymi zasadami assetów. Migracja obejmuje warianty encounterów na
poziomach 5–50 jako **statyczne grafiki**; nie generuje nowych grafik ani
animacji, a legacy idle frames nie są w pierwszym etapie migrowane.

Animacje (ruch, walka, enrage, śmierć) są dokładane stopniowo, po jednym
stanie, osobnymi zadaniami. Do tego czasu boss jest rysowany jednym
statycznym sprite'em we wszystkich stanach; zmiana fazy, atak i trafienie są
sygnalizowane wyłącznie HUD-em i feedbackiem z eventów. Dodanie animacji nie
zmienia balansu, kontraktów gameplayu ani hitboxa: wybór klatki to wyłącznie
decyzja prezentacji na podstawie `BossSnapshot` (faza, ruch) i eventów, więc
`BossPresentationCatalog` powinien od początku mapować `bossId` na zestaw
assetów per stan (na start tylko stan domyślny), żeby późniejsze animacje nie
wymagały zmiany kształtu katalogu.

`BattlefieldAssetCatalog`/dedykowany `BossPresentationCatalog` mapuje stabilny
`bossId` do assetów. Asset key nie trafia do JSON-u balansu. Brak wymaganego
assetu powinien powodować jawny błąd walidacji lub ładowania, a nie cichy
fallback do losowej grafiki.

Match screen prezentuje:

- boss sprite w world-space z tą samą transformacją kamery co pozostałe
  elementy battlefield;
- HP i fazę mobilnego bossa;
- HP oraz prowadzenie obrażeniami dla centralnego celu;
- feedback trafienia z eventów.

Nie dodaje się sterowania bossami ani nowego ekranu. UI pozostaje adapterem;
stan bossów pochodzi wyłącznie z snapshotów.

Landmark `BOSS` na Campaign Map nadal wskazuje specjalny poziom, ale nie
implikuje obecności konkretnego bossa w runtime. Nie tworzyć drugiego
kampanijnego markera ani drugiego katalogu assetów.

## Studio i plik balansu

### Zakładka Animations: kategoria `boss`

Częścią zadania jest rozszerzenie istniejącej, read-only zakładki Animations
w Studio o bossów, tak aby każda dokładana animacja była od razu widoczna i
walidowana. Studio nie wymaga nowej zakładki; zmiany obejmują:

- `AnimationCategory` w `studio/server/src/contracts/animations.ts` o wartość
  `boss` oraz parser `animationCatalog` (konwencja
  `anim_boss_<target>_<type>_<frame:3>.png`, np.
  `anim_boss_thorncrown_idle_000.png`; bossy nie mają wariantów `blue`/`red`);
- `studio/config/animations.json`: nowa grupa `targets.boss` z
  `presentationKey` bossów poziomów 5–50 (np. `ancient_war_oak`,
  `thorncrown`, `frozen_troll`), utrzymywana ręcznie jak pozostałe cele;
  ten sam klucz, który profil balansu zapisuje jako `presentationKey`;
- `studio/web/src/tabs/animations/` i `shell/tabs.ts`: filtr kategorii `boss`
  w zakładce i podgląd sekwencji w world-space bez zmian transformacji;
- walidację w Studio: boss z profilu balansu bez żadnej klatki lub statycznej
  grafiki jest raportowany jako `issues`, a sekwencja z nieznanym
  `presentationKey` ma `targetConfigured=false`; stany zdefiniowane w
  `BossPresentationCatalog` bez sekwencji (np. `move` przed jej
  dodaniem) są jawnie widoczne jako brakujące, a nie cichy fallback.

Do czasu dodania animacji boss ma tylko statyczny obraz, więc zakładka
pokazuje go jako sekwencję jednoklatkową; kolejne stany (`move`, `attack`,
`enrage`, `death`) dochodzą wraz z klatkami bez zmian kodu Studio. Zakładka
pozostaje read-only i nie zapisuje assetów. Sekwencje bez ładowarki w
runtime trafiają do `withoutLoader`, aż `BossPresentationCatalog` zacznie
je wczytywać.

Studio Animations i zakładka Balance nie współdzielą plików: pierwsza czyta
klatki z `drawable-nodpi`, druga edytuje `game-balance.json`. Łączy je
wyłącznie stabilny `presentationKey`; Studio nie kopiuje wartości balansu do
konfiguracji animacji.

### Zakładka Balance

Nowa zakładka **Balance** w Studio edytuje dokładnie
`game-domain/src/main/resources/balance/game-balance.json`. Proponowany
przepływ lokalnego API:

1. `GET` zwraca treść i hash pliku;
2. endpoint `validate` wykonuje ścisłe dekodowanie i walidację semantyczną;
3. `PUT` zapisuje treść tylko z aktualnym `expectedHash`;
4. zmiana pliku jest atomowa, a konflikt zwraca `409`;
5. frontend pokazuje błędy wraz ze ścieżką pola, np.
   `bosses[2].attacks[1].cooldownSeconds`.

Walidator serwera i testy domenowe sprawdzają ten sam kontrakt. Edytor
udostępnia pola właściwe dla `archetype` i `ability kind`, zamiast przyjmować
dowolny tekst skryptu. JSON jest zapisywany do repozytorium; nie ma kopii
autorytatywnej w Android assets, nie ma hot reloadu i Studio nie commit-uje ani
nie buduje aplikacji.

Loader używa ścisłej konfiguracji dekodera: nieznane pola, nieobsługiwane
`schemaVersion`, brak wymaganych profili, zduplikowane ID, nieprawidłowe
wartości lub nieznane zdolności kończą się jawnym błędem. Brak definicji
profilu nie wybiera domyślnego bossa.

## Plan implementacji

1. **Zamrozić typowany kontrakt balansu bossów.** Dodać plain domain types dla
   profilu, archetypu, ruchu, ataków i obecnie obsługiwanych zdolności. Dodać
   czystą walidację zakresów, unikalności ID i kompletności profili.
2. **Dodać plik JSON i loader.** Utworzyć zasób balansu, ścisły DTO decoder w
   granicy aplikacyjnej i mapowanie na niemutowalny model domenowy. Wczytać i
   zwalidować konfigurację przed utworzeniem sesji meczu.
3. **Dodać Studio Balance API i editor.** Wykorzystać istniejące wzorce repo
   root allowlisting, walidacji po stronie serwera, `expectedHash`, konfliktu
   i atomowego zapisu.
4. **Dodać przypisanie spotkania do kampanii.** Poziomy 5–50 otrzymują stabilne
   `bossId`, placement oraz właściwą regułę zwycięstwa. Rozszerzyć scenariusz
   i kontrakt wyniku bez wprowadzania zależności contracts -> domain.
5. **Dodać runtime central objective.** Health, damage-per-team, obronny atak,
   cooldown i zniszczenie działają w deterministycznym ticku. Zwycięża strona
   z większym damage; remis zachowuje BLUE.
6. **Dodać runtime mobile boss.** Ruch, zatrzymanie do walki, ataki jednostek,
   atak fortecy przy bramie, enrage i pokonanie. Wygrana po pokonaniu bossa
   przypisywana jest drużynie rzeczywistego źródła ostatnich obrażeń.
7. **Podłączyć walkę, pociski, AI i lifecycle.** Wszystkie trafienia bossa
   przechodzą przez istniejące granice; AI uwzględnia cel centralny w swoim
   snapshotowym wyborze; start/restart/pauza/koniec resetują lub zatrzymują
   state zgodnie z lifecycle sesji.
8. **Dodać snapshoty i eventy oraz Android presentation.** Zmapować
   niemutowalne boss state do render state, przenieść legacy artwork, dodać
   boss HUD i feedback bez przenoszenia reguł do UI. Rozszerzyć Studio
   Animations o kategorię `boss` (patrz „Zakładka Animations”), aby statyczne
   grafiki i kolejne animacje były widoczne i walidowane od razu.
9. **Zablokować zawartość po poziomie 50.** `CampaignSystem` nie udostępnia ani
   nie odblokowuje poziomów 55–100, dopóki registry nie zawiera kompletnego
   spotkania bossa. Późniejsze rozszerzenie frontu kampanii wymaga profilu,
   asset mapping i testu dla każdego przypisanego spotkania.
10. **Rozszerzać wspólny JSON etapami.** Dopiero osobnymi, testowalnymi
    migracjami przenieść konfiguracje jednostek, amunicji, czarów i wspólnych
    parametrów; każda migracja usuwa dotychczasowe wartości z Kotlinowych
    katalogów.

## Testy i kryteria akceptacji

### Balans i loader

- testy symulacji, AI i kampanii używają testowego fixture balansu, a nie
  produkcyjnego `game-balance.json`, aby strojenie liczb w Studio nie psuło
  testów reguł; osobny test sprawdza tylko, że produkcyjny plik się
  wczytuje i przechodzi walidację oraz że każdy przypisany `bossId` istnieje;
- ścisły dekoder akceptuje bieżący schemat i odrzuca złą wersję, nieznane pola,
  nieznane ability kind i uszkodzony JSON;
- ID profili są unikalne, a każde kampanijne `bossId` istnieje;
- HP, szybkości, zasięgi, obrażenia i cooldowny spełniają reguły archetypu;
- brak wymaganej konfiguracji nie uruchamia spotkania z domyślnym bossem;
- każda zmiana balansu zmienia `balanceRevision` i jest widoczna w replayu.

### Kampania i kontrakty

- poziomy 5–50 wskazują właściwe profile central objective/mobile boss;
- brak profilu na 55–100 blokuje wybór i nie przesuwa dalej unlock frontier;
- typowana reguła zwycięstwa trafia do scenariusza; markery mapy nie sterują
  runtime;
- ukończenie poziomu przechodzi przez istniejący `MatchFinished` i
  `CampaignSystem`.

### Symulacja i deterministyczność

- central objective przyjmuje obrażenia obu drużyn i nalicza je dokładnie raz;
- zniszczenie central objective rozstrzyga wynik poprawnie, także dla remisu;
- mobilny boss aktualizuje pozycję zgodnie z prędkością i terenem, wchodzi w
  fazę enrage przy skonfigurowanym progu, atakuje jednostki/bramę i może zostać
  pokonany;
- pocisk i jednostka przypisują obrażenia oraz ostatni cios rzeczywistej
  drużynie źródłowej;
- atak bossa może zakończyć mecz przez zniszczenie fortecy BLUE;
- identyczny seed, revision, scenario, komendy i ticki dają identyczne
  snapshoty, eventy i outcome;
- start, pauza, restart i zakończenie nie pozostawiają runtime state między
  meczami.

### Studio, AI i Android

- API odrzuca niepoprawne profile, błędny hash nie nadpisuje nowszej edycji,
  a zapis jest atomowy;
- po zapisie Studio zmiana jest widoczna po rebuildzie i uruchomieniu aplikacji,
  nie wcześniej;
- AI konsumuje boss snapshot i emituje zwykłe komendy, bez własnych wartości
  obrażeń;
- każdy profil z poziomów 5–50 ma jawne mapowanie assetu i test poprawnego
  ładowania;
- Studio Animations: parser rozpoznaje `anim_boss_*`, zgłasza nieskonfigurowany
  `presentationKey`, nieciągłe klatki i profil balansu bez grafiki;
  istniejące kategorie `character`/`spell`/`ammunition` zachowują wyniki;
- renderer korzysta z eventów/snapshotów i world-space; UI nie mutuje boss
  state;
- testy architektoniczne potwierdzają brak Androida w domain/simulation i
  jeden właściciel runtime state/balansu.

## Proponowane pliki przyszłej implementacji

- `game-domain/src/main/resources/balance/game-balance.json`;
- typowane modele balansu bossów oraz czysta walidacja w
  `game-domain/src/main/kotlin/com/urkaaaz/domain/`;
- DTO, strict decoder i `GameBalanceRepository` w
  `game-application/src/main/kotlin/com/urkaaaz/application/balance/`;
- rozszerzenia `CampaignLevelDefinition`, registry oraz `MatchScenario` /
  `VictoryRulesConfig`;
- `BossSystem` i runtime state w `game-simulation`; integracja w
  `DeterministicMatchSimulation` i `SimulationConfig`;
- boss snapshot/eventy w `game-contracts`;
- targetowanie boss-level w `game-ai`;
- asset mapping, render state, boss HUD i integracja match screen w
  `android-app`;
- kontrakt/API/service/testy w `studio/server/src/`;
- kategoria `boss` w `studio/server/src/contracts/animations.ts`,
  `studio/server/src/services/animationCatalog.ts`,
  `studio/config/animations.json` oraz filtr w
  `studio/web/src/tabs/animations/`;
- nowa zakładka edytora w `studio/web/src/tabs/balance/`;
- testy domeny, kampanii, kontraktów, symulacji, AI, Androida i Studio.

Nazwy klas i ścieżki poza istniejącymi plikami są propozycją do implementacji,
nie deklaracją, że kod już istnieje.

## Rozważone warianty

| Wariant | Ocena |
|---|---|
| Dodać warunki bossów bezpośrednio do `DeterministicMatchSimulation` i
  przechowywać liczby w Kotlinie | Najmniejszy pierwszy diff, ale zwiększa
  odpowiedzialność koordynatora i utrzymuje balans rozproszony po kodzie. |
| Nowy moduł Gradle oraz odrębny engine bossów | Dubluje lifecycle meczu,
  ładowanie snapshotów, obsługę komend i konfigurację. Nie jest potrzebny dla
  jednej nowej odpowiedzialności. |
| Jeden BossSystem w istniejącej symulacji, profile w wspólnym JSON-ie,
  scenario w kampanii i jawne kontrakty | **Rekomendowany.** Zachowuje jeden
  właściciel runtime i balansu, wspiera legacy parity, Studio i przyszłe
  sekcje jednostek/amunicji/czarów bez równoległych katalogów. |

## Otwarty zakres po pierwszym etapie

- zachowanie limitu czasu na poziomach bossów (czy istnieje i kto wygrywa);
- czy boss ma odporności/modyfikatory obrażeń zależne od typu amunicji lub
  czaru (parytet legacy do potwierdzenia w trakcie fazy 5–6);
- wartości HP, prędkości, obrażeń i cooldownów są celowo strojone w trakcie
  testów, nie zamrożone w tym dokumencie;
- atak dystansowy/pocisk bossa wymaga osobnego rozszerzenia typed ability oraz
  decyzji, czy używa istniejącego profilu amunicji, czy profilu pocisku
  boss-only;
- profile i assety dla poziomów 55–100 są przyszłą zawartością i dopiero po
  ich walidacji można przesunąć frontier kampanii;
- animacje bossów (ruch, walka, enrage, śmierć) są dokładane stopniowo po
  pierwszym etapie; do tego czasu obowiązują statyczne grafiki;
- szersza migracja katalogów jednostek, amunicji i czarów pozostaje kolejnymi
  etapami wspólnego pliku balansu.
