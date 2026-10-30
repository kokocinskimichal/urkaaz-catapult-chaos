# BossSystem — architektura i plan implementacji

## Status

**Pierwszy zakres BossSystemu jest zaimplementowany.** Mobilne bossy i cele
centralne działają na poziomach 5–50; system korzysta z typowanych kontraktów,
deterministycznego runtime, wspólnego JSON-u balansu, Studio, statycznych
assetów i match HUD-u. Dokument pozostaje opisem architektury oraz granic
odłożonych rozszerzeń.

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
  kompletnych profili bossów i assetów; poziomy 51–54 bez encounteru bossa są
  grywalne, a po poziomie 54 kampania nie odblokowuje poziomu 55.

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
- animacje ruchu, walki, enrage i śmierci; obecna wersja używa statycznych
  sprite'ów.

## Istniejące rozwiązania i ryzyko duplikacji

Przed tym wdrożeniem repozytorium nie miało runtime bossów ani celu
centralnego. Obecnie runtime należy do `BossSystem` w
`game-simulation`, a kanoniczne definicje balansu bossów znajdują się w
typowanym modelu `game-domain` oraz zasobie JSON. Wciąż istnieją także
kanoniczne katalogi pozostałych systemów:

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

`CampaignLevelDefinition` tworzy scenariusze kampanii z typowanym
`BossEncounterConfig`. `MatchScenario` przenosi `profileId`, spawn X jako
proporcję world width i `BossVictoryRule`; nie importuje modelu domenowego.
`DeterministicMatchSimulation` pozostaje właścicielem lifecycle i wyniku
meczu, a `BossSystem` jest jego podsystemem.

Studio ma istniejące wzorce lokalnego API, walidacji danych, oczekiwanego hasha
przy zapisie, błędu konfliktu `409` oraz atomowej podmiany pliku. Balance
korzysta z analogicznego API dla jednego pliku i nie współdzieli katalogu ani
kontraktu z edytorem map.

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
| `immuneAmmunitionTypes` | stabilne ID amunicji ignorowane przez bossa |

Profil zawiera też `collision` (rozmiar hitboxa w world units), bo trafianie
bossa przez pociski, eksplozje i jednostki jest regułą gameplayu, a nie
prezentacji. Rozmiar sprite'a pozostaje po stronie `BossPresentationCatalog`;
hitbox i sprite nie dzielą jednej wartości.

Kształt pliku jest finalny dla wersji schematu 1. Pełne, edytowalne profile
poziomów 5–50 znajdują się w zasobie:
`game-domain/src/main/resources/balance/game-balance.json`.

```json
{
  "schemaVersion": 1,
  "balanceRevision": "boss-legacy-baseline-1",
  "bosses": [
    {
      "id": "boss.central-ancient-war-oak.level-05",
      "presentationKey": "ancient_war_oak",
      "archetype": "CENTRAL_OBJECTIVE",
      "maxHealth": 12000,
      "collision": { "width": 1440.0, "height": 2016.0 },
      "movement": { "kind": "STATIC", "speedWorldUnitsPerSecond": 0.0 },
      "attacks": [
        { "target": "UNIT", "damage": 150, "minimumDamage": 35,
          "rangeWorldUnits": 780.0, "cooldownSeconds": 4.0,
          "initialCooldownSeconds": 2.5 }
      ],
      "specialAbilities": [],
      "immuneAmmunitionTypes": ["PLAGUE_CAULDRON"]
    }
  ]
}
```

Jeden profil opisuje jedno spotkanie. Poziomy powtarzające grafikę (5/45,
10/50) dostają **osobne profile** o własnych `id`, wskazujące wspólny
`presentationKey`; dzięki temu późniejszy poziom może mieć inny balans bez
łamania wcześniejszego. `presentationKey` to jedyne pole prezentacyjne w
profilu i jest tylko stabilnym identyfikatorem, nie nazwą bitmapy.

`balanceRevision` jest etykietą ludzką; Studio automatycznie podbija jej
końcowy numer przy zapisie zmienionych danych. Replay zapisuje dodatkowo SHA-256
dokładnej treści pliku, więc każda zmiana, również dokonana poza Studio,
pozostaje wykrywalna.

Ataki o różnym celu mogą mieć oddzielne obrażenia i czasy. Dla przykładu
uderzenie jednostek i uderzenie bramy nie powinny dzielić jednej wartości,
jeśli legacy lub dalszy balans określa je osobno. Profile ruchu i ataku
walidują zakresy: HP dodatnie, czasy i zasięgi poprawne dla danego archetypu,
prędkość nieujemna, a `STATIC` nie przyjmuje prędkości ruchu.

Zdolności są dyskryminowaną unią typów domenowych, a nie dowolnym tekstem,
refleksją, skryptem lub zbiorem niepowiązanych flag. Pierwsza wersja opisuje
tylko zachowania potrzebne do parytetu legacy, m.in. atak obronny celu
centralnego i enrage. Enrage zmienia prędkość (`1.35×`) oraz skraca cooldown
ataku (`0.666667×`); nie zwiększa obrażeń. `PROJECTILE_ATTACK` można dodać później jako nowy typ,
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

Scenariusz używa typowanego `BossEncounterConfig` z `profileId`,
`spawnXRatio` i `BossVictoryRule`. Reguły scenariusza jawnie rozróżniają:

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

Kampania zachowuje poziomy 1–100 w strukturze. Po wygranej na poziomie 50
odblokowuje zwykłe poziomy 51–54, ale nie poziom 55; limit grywalnej zawartości
wynosi 54, dopóki bossy na poziomach 55–100 nie mają pełnych profili i assetów.
`CampaignSystem` blokuje wybór i ukończenie poziomów od 55, a mapper mapy
kampanii utrzymuje ich markery jako zablokowane także dla starszego profilu,
który wcześniej odblokował dalsze poziomy. Sama ikona `BOSS` na mapie nie jest
assignmentem spotkania.

`MatchEvent.MatchFinished` i istniejące `MatchOutcome` pozostają końcowym
kanałem wyniku, dzięki czemu `CampaignSystem` i `RewardSystem` nadal rozliczają
wyłącznie zatwierdzone zwycięstwo BLUE. Reguła mobilnego bossa zastępuje zwykły
warunek zwycięstwa przez zniszczenie fortecy, gdy boss jest aktywnym celem.
Zniszczenie fortecy BLUE przez bossa kończy mecz wynikiem RED_WIN.

Przy celu centralnym wynik jest wyliczany z jawnego, runtime’owego licznika
obrażeń BLUE i RED. Remis zachowuje dotychczasowy tie-break na rzecz BLUE,
chyba że osobna decyzja balansowa zmieni tę regułę.

Limit czasu poziomów bossów jest taki sam jak na pozostałych poziomach
(obecnie `SimulationConfig.matchDurationMilliseconds`, 300 000 ms), ale
**wydzielony do osobnej zmiennej** `bossEncounterMatchDurationMilliseconds`
w `SimulationConfig`, by można go było zmienić tylko dla bossów. Wartość
domyślna jest równa limitowi standardowemu; nie trafia do profilu bossa, bo
należy do reguł scenariusza. Obecna symulacja tylko odlicza czas do zera i nie
rozstrzyga wyniku po jego upływie, więc BossSystem nie wprowadza własnej
reguły timeoutu; ewentualne rozstrzygnięcie po upływie czasu zostanie
zaprojektowane dla wszystkich poziomów razem.

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
- aktualne HP, pozycję i phase;
- cooldowny ataków;
- łączny damage per team dla centralnego celu;
- ostatni `sourceTeam` dla mobilnego bossa; `sourceId` jest przenoszony w
  evencie trafienia, nie jest dodatkowym trwałym stanem.

Stan inicjalizuje się ze zwalidowanego `MatchScenario` i przypiętej rewizji
balansu na `MatchSession.start`. Restart tworzy świeży stan, pauza nie
przesuwa timerów, a zakończony mecz nie aktualizuje bossów. Zmiana pliku w
Studio nie modyfikuje aktywnego meczu.

Aktualizacja następuje w deterministycznym kroku symulacji. BossSystem może
odczytywać kandydatów ataku i przekazywać obrażenia do istniejącej ścieżki
damage resolution, ale nie mutuje bezpośrednio stanu fortec, jednostek,
pocisków ani progresji kampanii. Kolejność względem ruchu jednostek,
kolizji i obrażeń musi być zamrożona testami.

Central objective materializuje płaską, chronioną strefę terenu pod swoim
placementem. Mobile boss korzysta z istniejącej ścieżki obrażeń jednostek,
fortec i amunicji; Sapper i Slingmaster nie dostają nowej ścieżki ataku na
bossy, zgodnie z zachowaniem legacy.

Jednostki atakujące bossa zachowują dotychczasowe odstępy w formacji. Symulacja
wyznacza ich rangę w kolejce względem aktywnych sojuszników zmierzających do
tego samego bossa i dodaje `ranga × ATTACK_FORMATION_SPACING` do efektywnego
zasięgu ataku, dzięki czemu dalsze jednostki mogą atakować bez przesuwania
formacji. Jednostki zaangażowane w inny cel nie zwiększają rangi.

Target preference, losowanie ataku obronnego i wszelkie losowe decyzje używają
seeda meczu. Nie wprowadzają osobnego seedowanego generatora ani zależności od
kolejności iteracji kolekcji.

## Kontrakty, eventy i integracja

`game-contracts` zawiera typowane `BossEncounterConfig` i `BossVictoryRule`,
niemutowalny `BossSnapshot` oraz eventy `BossSpawned`, `BossDamaged`,
`BossAttackHit`, `BossPhaseChanged` i `BossDefeated`. Event obrażeń bossa
zawiera źródło i `sourceTeam`; `ProjectileTargetKind.BOSS` rozróżnia bossy od
fortec i jednostek. `MatchStarted` i `MatchSnapshot` zachowują `balanceRevision`
oraz hash treści balansu.

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
krótkotrwałym efektom, np. floating damage, zmianie fazy lub feedbackowi ataku.
ViewModel i renderer nie wyliczają obrażeń, zwycięzcy, HP ani cooldownów.

`MatchStarted` lub zapis replayu musi zachować `balanceRevision` obok seeda i
konfiguracji scenariusza oraz duration dla boss encounter. Ten sam seed,
scenariusz, rewizja/hash balansu, duration i sekwencja komend/ticków muszą
dawać identyczne snapshoty, eventy i wynik.

## AI

AI nadal otrzymuje niemutowalny `MatchSnapshot` i wysyła zwykłe komendy przez
istniejącą granicę. Nie może mutować bossa ani mieć prywatnych wartości ataku.

Na poziomach centralnego celu strategie katapulty kierują AI RED w stronę
obiektu, by mogło uczestniczyć w damage race. Jednostki obu drużyn mogą
atakować boss encounter przez wspólną symulację. AI wysyła zwykłe komendy;
`BossSystem` nie otrzymuje dostępu do AI.

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
decyzja prezentacji na podstawie `BossSnapshot` (faza) i eventów. Każdy
statyczny asset jest dostępny też jako jednoklatkowa sekwencja
`anim_boss_<presentationKey>_idle_000.png`. `BossPresentationCatalog` mapuje
`presentationKey` na klucz sprite'a i lokalizowaną nazwę, a fazy bez własnej
sekwencji jawnie używają tego statycznego sprite'a. Późniejsze animacje mogą
dodać mapowanie per `BossPhase` bez zmiany kontraktu gameplayu.

`BattlefieldAssetCatalog` dekoduje wymagany sprite i zgłasza jawny błąd, gdy
brakuje zasobu. `BattlefieldRenderer` rysuje bossa w world-space przed
terrain capem. `RenderStateMapper` tworzy niemutowalny boss render state, a
match screen pokazuje `BossHudView`: HP/fazę mobilnego bossa lub HP i damage
race dla celu centralnego. Asset key nie trafia do JSON-u balansu.

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
- walidację w Studio: profil z JSON-u bez klatki jest pokazany w `catalogIssues`,
  a sekwencja z nieznanym `presentationKey` ma `targetConfigured=false`.

Do czasu dodania animacji boss ma tylko statyczny obraz, więc zakładka
pokazuje go jako sekwencję jednoklatkową; kolejne stany (`move`, `attack`,
`enrage`, `death`) dochodzą wraz z klatkami bez zmian kodu Studio. Zakładka
pozostaje read-only i nie zapisuje assetów. Jednoklatkowy `idle` ma loader;
pozostałe sekwencje bossów oraz wieloklatkowe `idle` są oznaczane jako bez
runtime loadera, dopóki prezentacja nie zacznie ich odtwarzać.

Studio Animations i zakładka Balance nie współdzielą plików: pierwsza czyta
klatki z `drawable-nodpi`, druga edytuje `game-balance.json`. Łączy je
wyłącznie stabilny `presentationKey`; Studio nie kopiuje wartości balansu do
konfiguracji animacji.

### Zakładka Balance

Zakładka **Balance** w Studio edytuje dokładnie
`game-domain/src/main/resources/balance/game-balance.json` przez ścisły edytor
JSON oraz walidację ścieżek pól. API lokalne:

1. `GET` zwraca treść i hash pliku;
2. endpoint `validate` wykonuje ścisłe dekodowanie i walidację semantyczną;
3. `PUT` zapisuje treść tylko z aktualnym `expectedHash`;
4. zmiana pliku jest atomowa, a konflikt zwraca `409`;
5. frontend pokazuje błędy wraz ze ścieżką pola, np.
   `bosses[2].attacks[1].cooldownSeconds`.

Zmiana wartości wymaga nowej `balanceRevision`; zakładka podbija końcowy numer
automatycznie, jeśli użytkownik jej nie zmienił. Walidator serwera i loader
aplikacji odrzucają nieznane pola, typy i zdolności. JSON jest zapisywany do
repozytorium; nie ma kopii autorytatywnej w Android assets, nie ma hot reloadu
i Studio nie commit-uje ani nie buduje aplikacji.

Loader używa ścisłej konfiguracji dekodera: nieznane pola, nieobsługiwane
`schemaVersion`, brak wymaganych profili, zduplikowane ID, nieprawidłowe
wartości lub nieznane zdolności kończą się jawnym błędem. Brak definicji
profilu nie wybiera domyślnego bossa.

## Zrealizowane etapy

1. **[x] Typowany kontrakt balansu bossów.** Dodano plain domain types dla
   profilu, archetypu, ruchu, ataków i obecnie obsługiwanych zdolności oraz
   czystą walidację zakresów, unikalności ID i kompletności profili.
2. **[x] Plik JSON i loader.** Dodano zasób balansu, ścisły DTO decoder w
   granicy aplikacyjnej i mapowanie na niemutowalny model domenowy. Konfiguracja
   jest wczytywana i walidowana przed utworzeniem sesji meczu.
3. **[x] Studio Balance API i edytor.** Dodano walidację, `expectedHash`,
   atomowy zapis, konflikt 409 i automatyczne podbijanie rewizji przy edycji.
   Wykorzystano istniejące wzorce repo
   root allowlisting, walidacji po stronie serwera, `expectedHash`, konfliktu
   oraz atomowego zapisu.
4. **[x] Przypisanie spotkań kampanii.** Poziomy 5–50 otrzymały stabilne
   `profileId`, placement oraz właściwą regułę zwycięstwa. Scenariusz i
   kontrakty pozostały niezależne od `game-domain`.
5. **[x] Runtime central objective.** HP, damage-per-team, obronny atak,
   cooldown i zniszczenie działają w deterministycznym ticku. Zwycięża strona
   z większym damage; remis zachowuje BLUE.
6. **[x] Runtime mobile boss.** Ruch, zatrzymanie do walki, ataki jednostek,
   atak fortecy przy bramie, enrage i pokonanie. Wygrana po pokonaniu bossa
   przypisywana jest drużynie rzeczywistego źródła ostatnich obrażeń.
7. **[x] Walka, pociski, AI i lifecycle.** Trafienia bossa
   przechodzą przez istniejące granice; AI uwzględnia cel centralny w swoim
   snapshotowym wyborze; start/restart/pauza/koniec resetują lub zatrzymują
   state zgodnie z lifecycle sesji.
8. **[x] Snapshoty, eventy i Android presentation.** Zmapowano
   niemutowalny boss state do render state, przeniesiono legacy artwork,
   dodano boss HUD i feedback bez przenoszenia reguł do UI. Rozszerzono Studio
   Animations o kategorię `boss` (patrz „Zakładka Animations”), aby statyczne
   grafiki i przyszłe animacje były widoczne i walidowane od razu.
9. **[x] Zatrzymać frontier przy ostatnim pełnym spotkaniu.** Poziomy 51–54
   pozostają grywalne; `CampaignSystem` nie odblokowuje poziomu 55 bez
   kompletnego boss assignmentu. Strukturalny limit kampanii pozostaje 100.
10. **[odłożone] Rozszerzyć wspólny JSON etapami.** Osobnymi migracjami
    przenieść konfiguracje jednostek, amunicji, czarów i wspólnych parametrów;
    każda migracja ma usunąć równoległe wartości z Kotlinowych katalogów.

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
- zwycięstwo na 50 odblokowuje 51, poziomy 51–54 pozostają grywalne, a
  zwycięstwo na 54 nie odblokowuje 55;
  nawet stary zapis z wyższym unlock frontier nie pokazuje poziomów 55–100
  jako dostępnych;
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

## Główne pliki implementacji

- `game-domain/src/main/resources/balance/game-balance.json`;
- `game-domain/src/main/kotlin/com/urkaaaz/domain/BossBalance.kt`;
- `game-application/src/main/kotlin/com/urkaaaz/application/balance/GameBalanceRepository.kt`;
- `game-contracts/src/main/kotlin/com/urkaaaz/contracts/BossEncounter.kt` oraz
  rozszerzenia `MatchScenario`, `MatchSnapshot` i `MatchEvents`;
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt`;
- `game-simulation/src/main/kotlin/com/urkaaaz/simulation/BossSystem.kt` oraz
  integracja w `DeterministicMatchSimulation.kt` i `SimulationConfig.kt`;
- strategia wyboru celu w `game-ai/src/main/kotlin/com/urkaaaz/ai/AiStrategies.kt`;
- `assets_src/bosses/` i jednoklatkowe zasoby
  `android-app/src/main/res/drawable-nodpi/anim_boss_*_idle_000.png`;
- `android-app/src/main/java/com/urkaaaz/ui/BossPresentationCatalog.kt`,
  `BossHudView.kt`, `BattlefieldAssetCatalog.kt`, `BattlefieldRenderer.kt`
  i `RenderStateMapper.kt`;
- `studio/server/src/services/balanceCatalog.ts` i `studio/server/src/api/balance.ts`;
- kategoria `boss` w `studio/server/src/contracts/animations.ts`,
  `studio/server/src/services/animationCatalog.ts`,
  `studio/config/animations.json` oraz filtr w
  `studio/web/src/tabs/animations/`;
- zakładka edytora w `studio/web/src/tabs/balance/BalanceTab.tsx`.

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

- rozstrzygnięcie meczu po upływie limitu czasu (dziś nieobsługiwane w
  symulacji) wspólnie dla wszystkich poziomów; limit bossów jest już osobną
  zmianą konfiguracji;
- odporność na `PLAGUE_CAULDRON` jest zachowana dla obu archetypów zgodnie
  z legacy; inne modyfikatory obrażeń nie są częścią obecnego profilu;
- początkowe HP, prędkości, obrażenia i cooldowny są przepisane z legacy
  (zdecydowano), po czym strojone w trakcie testów; wartości nie są
  zamrożone na stałe;
- atak dystansowy/pocisk bossa wymaga osobnego rozszerzenia typed ability oraz
  decyzji, czy używa istniejącego profilu amunicji, czy profilu pocisku
  boss-only;
- profile i assety dla poziomów 55–100 są przyszłą zawartością i dopiero po
  ich walidacji można przesunąć frontier kampanii;
- animacje bossów (ruch, walka, enrage, śmierć) są dokładane stopniowo po
  pierwszym etapie; do tego czasu obowiązują statyczne grafiki;
- szersza migracja katalogów jednostek, amunicji i czarów pozostaje kolejnymi
  etapami wspólnego pliku balansu.
