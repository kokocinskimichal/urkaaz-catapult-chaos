# SpellSystem

## Status i cel

SpellSystem jest częściowo zaimplementowany. Oprócz `FORTRESS_SHIELD` i HASTE
trzeci pionowy wycinek obejmuje `RUNIC_BASTION`: aktywację przez sesję,
deterministyczny stan 360 HP, powiększoną geometrię przed bramą, blokowanie
i ataki jednostek, kolizje pocisków, snapshot oraz prezentację zdrowia na
ikonie zaklęcia. Jedna
bariera na drużynę blokuje kolejne rzucenie bez pobrania many aż do zniszczenia.
Pozostałe osiem efektów oraz trwałe rozliczanie stocku nadal są
niezaimplementowane. Zatwierdzona 20-klatkowa animacja wyrastania Bastionu
z ziemi przechodzi po 2,4 s w zatwierdzoną 24-klatkową pętlę pulsowania run;
24-klatkowa animacja zniszczenia jest odtwarzana po jego zniknięciu.
Zaakceptowana przez użytkownika 12-klatkowa animacja world-space HASTE z
trzema orbitującymi szewronami jest zintegrowana: pojawia się za żywymi,
poruszającymi się jednostkami HASTED i jest lustrzana zgodnie z kierunkiem
ruchu.
Podczas działania tarczy ikona zaklęcia jest przykrywana od góry tym samym
ciemnym cieniem co ikona amunicji podczas przeładowania; zakryta część
odpowiada utraconemu HP i znika po wygaśnięciu tarczy.

SpellSystem ma obsługiwać deterministyczne efekty wszystkich 11 zaklęć
legacy, od walidacji aktywacji po eventy i snapshoty. Powinien zapewniać jeden
katalog balansu, jawne granice komend/eventów oraz rozszerzalne reguły bez
przenoszenia gameplayu do Androida lub rozbudowywania centralnego obiektu
warunkami dla każdego typu.

Migracja zachowuje obserwowalne zachowanie i balans z legacy. Wyjątkiem jest
rozbieżność w opisie Haste: użytkownik potwierdził, że działa wyłącznie na
sojuszników. Tę regułę należy traktować jako źródło prawdy i skorygować
prezentację, nawet jeśli starszy tekst UI mówi o wszystkich jednostkach.

## Zakres i wyłączenia

W zakresie:

- definicje zaklęć, targety, koszty many i efekty gameplayowe;
- deterministyczny runtime, aktywne czasy, pułapki i fortyfikacje;
- jawne aktywacje, odrzucenia, eventy i snapshoty;
- integracja z dostępnością kampanii i zatwierdzonym loadoutem oraz jawne
  raportowanie zużycia stocku runtime do granicy aplikacyjnej progresji;
- Androidowa aktywacja zaklęć, prezentacja kategorii celu i efektów;
- migracja wszystkich 11 zaklęć legacy z testami zgodności.

Poza zakresem SpellSystemu:

- wybieranie, walidowanie, przechowywanie i prezentowanie loadoutu;
- implementacja ani przejmowanie odpowiedzialności istniejącego
  `LoadoutSystem`;
- własność trwałego złota, odblokowań i stocku gracza;
- reguły kampanii, nawigacji i Android lifecycle;
- zakupy oraz trwałe rozliczanie profilu po meczu — pozostają granicą
  PlayerProgressionSystemu i adapterów persystencji;
- implementacja reguł gameplayowych w Android UI lub osobna implementacja
  tych reguł w AI.

`LoadoutSystem` ma już kanoniczny model wyboru jednostek, amunicji i zaklęć,
interaktywne edytory, walidację oraz lokalny zapis zatwierdzonego loadoutu.
`CampaignSystem` zapisuje postęp i nagrody Haste/Heal. SpellSystem nie może
tworzyć równoległego loadoutu ani przejmować walidacji wyboru lub kampanijnych
odblokowań. Otrzymuje niemutowalny, zatwierdzony `PlayerLoadoutConfig` jako
konfigurację meczu. Zakupy i trwałe rozliczanie stocku pozostają
niewdrożonymi odpowiedzialnościami progresji.

## Obecne elementy i granice

W repozytorium istnieją już:

| Element | Obecna rola | Docelowe użycie |
|---|---|---|
| `game-domain/Spells.kt: SpellType` | stabilne identyfikatory zaklęć | zachować identyfikatory; balans i metadane należą do katalogu |
| `game-domain/SpellCatalog.kt: SpellCatalog` | koszty, kategorie roli/celu i typowane efekty HASTE, tarczy oraz Bastionu | kanoniczne źródło definicji wszystkich 11 zaklęć |
| `game-domain/Spells.kt: SpellCategory` | `OFFENSIVE`, `DEFENSIVE`, `BATTLEFIELD_CONTROL`, `SUPPORT`, `SIEGE` | zachować jako kategorię roli/efektu; nie używać jako kategorii targetu |
| `game-application/.../loadout/MatchLoadout` | wspólny wybór jednostek, amunicji i czarów | kanoniczny model LoadoutSystemu |
| `MatchCommand.CastSpell` | komenda z ID tekstowym i opcjonalnym celem | wejście do walidacji/aktywacji; nie zmienia stanu bezpośrednio |
| `CommandResult` / `MatchEvent.SpellCast` | jawny wynik aktywacji oraz event ze wskazanym celem i kosztem many | wspólny kanał odpowiedzi dla kolejnych zaklęć |
| `MatchEvent.ShieldDamaged` | event trafienia i pozostałego HP tarczy | jawny kontrakt prezentacji trafienia |
| `MatchSnapshot.effects` / `EffectSnapshot` | ogólny odczyt efektów | wykorzystać po weryfikacji wystarczalności; nie dublować stanu renderowania |
| `ResourceSnapshot.mana/gold` | odczyt zasobów obu drużyn | symulacja publikuje manę, złoto, supply i amunicję |
| `CampaignLevelDefinition.availableSpells` | dostępność zaklęć na poziomie | pozostaje konfiguracją kampanii i wejściem do match launch |
| `MatchSession` | lifecycle, pauza, restart i forwarding | przekazywać komendy; tworzyć świeży runtime przy starcie/restart |
| `DeterministicMatchSimulation` | deterministyczny runtime meczu | właściciel stanu SpellSystemu w ramach symulacji |
| `LoadoutScreen` | wybór zaklęć w zatwierdzonym loadoucie | nie jest ekranem aktywacji zaklęć podczas meczu |

`MatchSession.dispatchResult` i `LocalMatchGateway.dispatchResult` obsługują
`CastSpell`; Androidowy match screen pokazuje manę, wysyła akcję aktywacji
wyposażonego `FORTRESS_SHIELD`, HASTE lub `RUNIC_BASTION`, wyświetla
wynik/odrzucenie i rysuje osłonę z niemutowalnego stanu fortecy w world-space.
Snapshot i render state przekazują geometrię oraz HP aktywnych Bastionów; ikona
zaklęcia pokazuje utracone HP. HASTE publikuje pozostały
czas przez `ActiveSpellSnapshot`; status HASTED pojawia się tylko na
sojuszniczych jednostkach poza garnizonem. Wynik aktywacji sprawdza status
meczu, dostępność poziomu, zatwierdzony loadout i poprawny cel. Nieznane,
niedostępne, niewyposażone i
niezaimplementowane zaklęcia oraz błędy many/aktywnej tarczy są odrzucane
jawnym `MatchError`. Koszt pobierany jest dopiero przy powodzeniu. Replay
zachowuje zaakceptowane i odrzucone próby aktywacji.

Mana startuje i ma limit 100, regeneruje 10 co 8 sekund symulacji, a jej stan
jest publikowany w snapshotach. `FORTRESS_SHIELD` kosztuje 80 many, ma 150 HP,
przechwytuje wyłącznie pociski drużyny przeciwnej i nie przepuszcza reszty
obrażeń na fortecę. Kolizja i renderowanie korzystają ze wspólnej,
fortress-centered geometrii eliptycznej `FortressShieldGeometry` w
`game-contracts`. Stan tarczy znajduje się w `FortressSnapshot`;
przechwycenie toru pocisku emituje `ProjectileHitEntity` oraz `ShieldDamaged`.
HASTE kosztuje 50 many, trwa 15 sekund i mnoży prędkość ruchu wysłanych
sojuszniczych jednostek przez 2. Efekt obejmuje też jednostki wysłane podczas
jego trwania, ale nigdy jednostki w garnizonie ani wrogów. Ponowne rzucenie
zużywa kolejne 50 many i odświeża czas do 15 sekund bez kumulowania mnożnika.
SLOWED mnoży się z HASTE (×0,5 daje normalną prędkość), a STUNNED nadal
zatrzymuje jednostkę. Cena katalogowa 900 złota i dostępność kampanii od
poziomu 6 pozostają bez zmian; ceny złota nie pobiera runtime meczu.
`ActiveSpellSnapshot` przekazuje drużynę i pozostały czas, a
`UnitSnapshot.statuses`/`statusDetails` sygnalizują aktywnych HASTED.
`RUNIC_BASTION` ma wspólną geometrię `288 × 216` jednostek świata i 360 HP.
Jego środek leży 436 jednostek przed dotychczasowym punktem ataku bramy,
w stronę środka pola. Geometria jest
podniesiona o 12 jednostek świata; cap terenu rysowany nad nią zasłania jej
dolną krawędź, a jednostki nadal zderzają się z podniesioną geometrią.
Wszystkie typy atakują Bastion przed fortecą, Demolisher detonuje przy
kontakcie, a Sapper zużywa bombę i wraca. Wrogie pociski z warstwą trafień
fortecy zatrzymują się na Bastionie i wykonują zwykły efekt amunicji.
Zniszczenie nie kończy meczu; udostępnia ponowne rzucenie. Pozostałe osiem
efektów katalogu nadal nie ma runtime definition. `EffectSnapshot`
nadal opisuje aktywne efekty amunicji.
Walidacja scenariusza sprawdza identyfikatory dostępności, a zgodność
dostępności z loadoutem jest egzekwowana przy aktywacji.

Copy-ready prompt dla źródłowej pierwszej klatki spokojnej animacji aktywnej
kopuły znajduje się w
[`fortress-shield-active-dome-first-frame.agp`](fortress-shield-active-dome-first-frame.agp).
Grafika ma być osobnym, przezroczystym sprite'em bez fortecy. Krycie kopuły
pozostaje stałe niezależnie od HP; stan wytrzymałości pokazuje ikona zaklęcia.
Brief pierwszej klatki aktywnego Runicznego Bastionu znajduje się w
[`runic-bastion-first-frame.agp`](runic-bastion-first-frame.agp). Jako
referencję stylu wykorzystuje kartę zaklęcia, a docelowa grafika przedstawia
wyłącznie osobny fragment kamiennej bariery bez bramy i otoczenia. Zatwierdzone
klatki animacji wyrastania i pulsowania są przechowywane w
`assets_src/spells/runic-bastion/creation/` i
`assets_src/spells/runic-bastion/idle-runes/`. Zasoby
`anim_spell_runic_bastion_creation_000`–`anim_spell_runic_bastion_creation_019` oraz
`anim_spell_runic_bastion_idle_000`–`anim_spell_runic_bastion_idle_023` są odtwarzane w world-space
względem geometrii `RunicBastionGeometry`; sekwencja wyrastania trwa 2,4 s,
po czym przechodzi w pętlę idle. Obie fazy są wstrzymywane razem z meczem.
Po zniknięciu Bastionu renderer odtwarza jednorazowo
`anim_spell_runic_bastion_destruction_000`–`anim_spell_runic_bastion_destruction_023` przez 24 klatki
po 167 ms. Zatwierdzony eksport po poprawce kadru używa rozszerzonego
przezroczystego canvasu (`source_scale = 0.5`); wszystkie 24 klatki RGBA
640×360 mieszczą się w kadrze, bez dotykania jego krawędzi. Nowy ZIP, wideo
źródłowe i podglądy oraz poprzedni eksport są zachowane w
`assets_src/spells/runic-bastion/destruction/`.
Ponieważ mur w tej sekwencji zajmuje mniejszy obszar źródłowego canvasu,
renderer używa osobnych widocznych granic pierwszej klatki zniszczenia, aby
zachować rozmiar world-space i kotwicę zgodne z fazami creation oraz idle.
Jednostki atakujące Bastion zachowują pozycję do końca jego animacji zniszczenia;
pozostali maszerujący przeciwnicy nie mogą przejść przez jego obszar. Czas
4 008 ms jest współdzielony przez symulację i prezentację w
`RunicBastionAnimationTiming`.
Ikona Bastionu w HUD-ie pozostaje przygaszona i nieaktywna, dopóki snapshot
meczu zawiera istniejącą konstrukcję; po jej zniszczeniu ponownie odzwierciedla
gotowość zaklęcia.

Zaimportowana animacja spokojnej kopuły (AnimGen, Seedance 2.0 fast, 24 klatki
RGBA 640×480, pętla z tą samą pierwszą i ostatnią klatką) jest zapisana jako
`anim_spell_fortress_shield_dome_000.png`–`23.png` w `drawable-nodpi`; oryginały, podgląd
MP4 i klatka źródłowa są w `assets_src/spells/fortress-shield/dome-idle/`.
`BattlefieldRenderer` rozciąga widoczną kopułę na elipsę
`FortressShieldGeometry`, rysuje ją ze stałym alpha i ponownie nakłada teksturę
soil w obrębie sprite'a, aby grunt zasłaniał kopułę poniżej powierzchni.
Reguły prezentacji są w `FortressShieldPresentation`. Użytkownik zaakceptował animację mimo znanych
wad: nierówne wycinanie półprzezroczystych komórek (`alpha_key`), ciemne
obwódki na jasnym tle i ~18 px drgania dolnej krawędzi w klatkach 3–5.
Sekwencja błysku po trafieniu ma 24 klatki RGBA 640×480 w
`anim_spell_fortress_shield_hit_000.png`–`23.png`; oryginał ZIP i podgląd MP4 są w
`assets_src/spells/fortress-shield/hit-flash/`. Animacja jest odtwarzana
jednorazowo po `ShieldDamaged`, zaczynając od klatki 04, aby rozbłysk był
widoczny od razu; klatki 00–03 są pomijane. Po sekwencji wraca animacja
spokojna; także trafienie wyczerpujące ostatnie HP tarczy pokazuje błysk.
Ponieważ pocisk przeciwnika trafia prawą stronę lewej fortecy, renderer
lustrzanie odbija tylko błysk niebieskiej (lewej) fortecy; czerwona (prawa)
używa oryginalnej orientacji. Podgląd zaakceptowano mimo kolorowej poświaty
poza konturem i niewielkiej zmiany obrysu/położenia w środkowych klatkach.

Istniejące podobne rozwiązanie do naśladowania to `AmmunitionCatalog`:
jednoznaczna definicja balansu w domenie, niezależna od Androida i używana
przez symulację. Nie należy kopiować parametrów zaklęć do rendererów,
ViewModelu, kampanii ani AI.

## Właściciele i kierunek zależności

SpellSystem jest podsystemem, nie nowym modułem Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| Stabilne typy/ID, definicje, walidacja parametrów i balans | `game-domain` |
| Runtime efektów, many w meczu, timerów, pułapek, bastionów i zużycia stocku meczu | `game-simulation`, wewnątrz jedynego runtime meczu |
| Komendy, wyniki odrzuceń, eventy i niemutowalne snapshoty | `game-contracts` |
| Lifecycle sesji i przekazywanie komend/snapshotów | `game-application` |
| Dostępność na poziomie | `game-campaign` |
| Odczyt lokalnego złota, odblokowań i stocku oraz ich zapis | częściowy `PlayerProgressionSystem` w `game-application` z adapterem DataStore w `android-app`; zakupy i rozliczenie stocku pozostają poza zakresem |
| Łączny loadout jednostek i zaklęć | istniejący `LoadoutSystem`, poza zakresem SpellSystemu |
| Input, lokalizacja, HUD i rysowanie | `android-app` |
| Decyzje AI na snapshotach i komendy | `game-ai`, jeśli później zostanie włączone do zakresu |

Kierunek zależności:

```text
android-app ──> game-application ──> game-simulation ──> game-domain
       │                 │                  │                 │
       └─────────────────┴──────────────────┴──────> game-contracts

game-campaign ──> game-contracts + game-domain
game-ai ────────> game-contracts + game-domain
```

Żadna warstwa core nie importuje Androida. Symulacja może korzystać z typów i
reguł domeny, ale nie z persystencji ani UI. Android wysyła `MatchUiAction`
przetłumaczone na `MatchCommand`; nie zmienia many, efektów, zdrowia ani stocku
bezpośrednio.

## Kategorie według celu

Obecne `SpellCategory` opisuje rolę zaklęcia. Nowy, ortogonalny
`SpellTargetCategory` (nazwa robocza) opisuje stronę i rodzaj celu. Kategorie
nie mogą być mieszane ani wyliczane przez Android UI.

| Kategoria celu | Zaklęcia legacy | Reguła celu |
|---|---|---|
| `ALLIED_FORCE` — własna armia | Haste, Heal, Wampiryczny sztandar, Kamienna skóra, Szał oblężniczy | efekt obejmuje własne jednostki; Haste nie obejmuje przeciwnika |
| `ALLIED_UNIT` — pojedyncza własna jednostka | Berserker | najbliższy właściwy Obrońca; nie można ponownie wybrać Berserkera |
| `ENEMY_FORCE` — wrogie jednostki | Lodowa pułapka, Pole spowolnienia | działa wyłącznie na jednostki przeciwnej drużyny; pułapka ma dodatkowo stałe miejsce rozstawienia |
| `ENEMY_SIEGE_ENGINE` — wroga machina oblężnicza | Blokada katapulty | blokuje katapultę drużyny przeciwnej |
| `OWN_FORTRESS` — własna forteca | Tarcza fortecy | bariera należy do fortecy drużyny rzucającej |
| `OWN_GATE` — własna brama | Runiczny Bastion | bariera jest pozycjonowana względem bramy własnej fortecy |

Target category jest metadanymi domenowymi i źródłem grupowania w UI. Nie
oznacza, że każde zaklęcie wymaga wyboru celu przez dotyk: zaklęcie może
automatycznie znaleźć jednostkę, rozstawić obiekt w z góry określonym miejscu
albo wskazywać całą armię.

## Katalog i rozszerzalność

`SpellCatalog` jest kanonicznym źródłem stabilnych typów, kategorii roli i
celu oraz kosztów złota i many dla wszystkich 11 zaklęć. Runtime effect
definition istnieje dla HASTE, tarczy fortecy i Runicznego Bastionu; pozostałe
osiem efektów nie jest zaimplementowanych. Katalog docelowo
pozostaje jedynym źródłem:

- stabilnego `SpellType`/ID;
- obecnej kategorii roli `SpellCategory`;
- kategorii celu;
- kosztu złota, kosztu many i zasad stocku;
- czasu działania, parametrów efektu i reguł aktywacji;
- kluczy lokalizacji/assetów, bez zależności od Android `R`.

`SpellType` zachowuje stabilne identyfikatory, a katalog dostarcza ich
kompletne metadane. Obecne `goldCost`, `category`, `special`, koszty many i
kategorie celu są już przeniesione do jednej definicji katalogowej. Nie może
po cichu zmieniać nazw `ICE_TRAP`,
`RUNIC_BASTION`, `HASTE`, `HEAL`, `FORTRESS_SHIELD`, `CATAPULT_LOCK`,
`SLOWING_FIELD`, `VAMPIRIC_BANNER`, `STONE_SKIN`, `SIEGE_FRENZY` i
`BERSERKER`.

Nowy typ dodaje kompletną definicję do katalogu oraz własną, typowaną regułę
efektu, gdy różni się od istniejących efektów. Wspólne mechanizmy, takie jak
czasowy modyfikator prędkości, modyfikator obrażeń, leczenie, targetowanie lub
fortyfikacja, powinny być współdzielone bez zmiany właściciela runtime state.
Rejestracja nie powinna wymagać dopisywania kolejnego rozgałęzienia do
`MainActivity`, `MatchSession` ani jednego centralnego `when` w symulacji.

Wszystkie wartości balansu występują tylko w definicji domenowej. Walidacja
katalogu musi sprawdzać unikalność identyfikatorów, nieujemne ceny złota,
dodatnie koszty many i czasy tam, gdzie mają zastosowanie, poprawność
parametrów oraz kompletność mapowania typu na regułę. Brak definicji albo
nieznany ID jest jawnym błędem, a nie domyślnym czarem.

## Kontrakty wejścia i wyjścia

### Wejście

1. `MatchCommand.CastSpell` zawiera `matchId`, `playerId`, stabilny typ czaru i
   opcjonalny `target`. Id tekstowe są walidowane wobec katalogu. Kontrakt nie
   importuje `game-domain`; jeśli później potrzebny będzie typowany wspólny
   identyfikator, należy wprowadzić go w `game-contracts`, nie kierować
   zależności kontraktów do domeny.
2. Uwierzytelnione przypisanie `playerId -> Team` oraz running state meczu.
3. Niemutowalne dane uruchomienia meczu: dostępne typy z
   `CampaignLevelDefinition.availableSpells` oraz zwalidowany, wybrany
   loadout dostarczony przez LoadoutSystem. LoadoutSystem pozostaje
   właścicielem wyboru i walidacji; konfiguracja meczu oraz aktywacja muszą
   jawnie sprawdzić, że zaklęcie jest dostępne i wyposażone.
4. Autorytatywny stan symulacji: mana, jednostki, katapulty, fortece,
   aktywne efekty i stock przypisany do bieżącego meczu. Mana jest już częścią
   runtime'u; stock zaklęć pozostaje niewdrożony.
5. Deterministyczny czas symulacji przekazany przez istniejący cykl
   `AdvanceSimulation`.

### Aktywacja i wyniki

Obsługa aktywacji powinna być atomowa:

1. rozpoznać gracza/drużynę i czar;
2. sprawdzić running state, dostępność poziomu i loadout dostarczony przez
   LoadoutSystem;
3. zweryfikować manę, stock oraz warunki/target specyficzne dla czaru;
4. dopiero po pełnej walidacji pobrać zasoby i zastosować efekt;
5. opublikować event oraz nowy snapshot.

Odrzucenie nie pobiera many/stocku ani nie pozostawia częściowego efektu.
Aktywacja korzysta z `CommandResult.Accepted` albo
`CommandResult.Rejected(reason)` przez `dispatchResult`; legacy'owe
`dispatch` nadal zwraca `MatchSnapshot`. SpellSystem nie tworzy osobnego
kanału odrzuceń. Obecne przyczyny obejmują:
nieznany czar, mecz nieaktywny, czar niedostępny na poziomie, czar
niewyposażony, brak many, brak stocku, brak prawidłowego celu i efekt już
aktywny, jeśli dany czar nie może być ponownie aktywowany.

### Wyjście

- `SpellCast`/`SpellActivated` jako fakt udanej aktywacji z graczem/drużyną,
  typem, czasem symulacji, celem i kosztem many;
- jawne odrzucenie komendy z typowaną przyczyną;
- aktualizowany `MatchSnapshot`, w tym mana, statusy jednostek oraz odczyt
  aktywnych efektów i obiektów świata;
- jawny wynik zużycia stocku meczu, który aplikacja może później rozliczyć
  przez PlayerProgressionSystem. Trwały zapis nie należy do runtime'u
  symulacji.

`UnitSnapshot.statuses`/`statusDetails` i `MatchSnapshot.effects` są istniejącymi
kontraktami do oceny pod kątem reuse. Jeżeli nie kodują właściciela/drużyny,
typu czaru albo pozostałego czasu wystarczająco jasno, należy rozszerzyć je
albo dodać wyspecjalizowany niemutowalny snapshot spellowy. Nie wolno utrzymywać
drugiego stanu efektów w ViewModelu ani tworzyć konkurencyjnego, ogólnego
kontraktu efektów.

## Stan, konfiguracja i lifecycle

| Rodzaj danych | Właściciel |
|---|---|
| Statyczne definicje oraz balans | katalog `game-domain` |
| Dostępność poziomu | `CampaignLevelDefinition` |
| Wybór loadoutu | istniejący LoadoutSystem |
| Złoto, odblokowania, trwały stock | warstwa aplikacyjnej progresji/persystencji |
| Mana i tarcza fortecy; bieżący stock meczu, pozostałe aktywne timery, pułapki i bastiony | runtime `DeterministicMatchSimulation`; mana i tarcza są wdrożone, reszta pozostaje w planie |
| Snapshot/render state | kontrakty i mapper Androida jako niemutowalny odczyt |

Trwały stock w progresji jest oddzielony od stocku przekazanego do meczu.
Symulacja zużywa tylko swój runtime stock i raportuje zmianę; aplikacja
rozlicza zapis trwałej progresji przez jawny wynik/event. Nie przenosić
`SharedPreferences` ani repozytorium progresji do `game-domain` lub
`game-simulation`.

Nowy mecz tworzy świeży runtime symulacji. Pauza nie przesuwa timerów, bo
`MatchSession` nie przekazuje kroków symulacji. Restart tworzy świeży stan
many i zaklęć z konfiguracji meczu, a zakończenie meczu uniemożliwia dalsze
aktywacje/tickowanie. Brak stanu efektu w UI nie może wpływać na gameplay.

## Deterministyczność i kolejność faz

Czas trwania zaklęć mierzy czas symulacji, nie zegar ścienny. Przy tym samym
scenariuszu, konfiguracji, sekwencji komend i delcie symulacji wynik, zasoby,
eventy i snapshoty muszą być powtarzalne. Nowe zaklęcia nie wprowadzają
niejawnej losowości; jeżeli losowość stanie się wymagana, musi użyć istniejącego
kontrolowanego seeda symulacji.

Rekomendowana kolejność:

1. Komenda aktywacji jest walidowana i atomowo rozliczana przed następnym
   krokiem czasu symulacji.
2. W kroku symulacji SpellSystem aktualizuje czasowe efekty i przygotowuje ich
   modyfikatory przed ruchem jednostek i rozstrzyganiem walki/pocisków.
3. Ruch odczytuje aktywne modyfikatory prędkości/pułapki; walka i obrażenia
   odczytują aktywne modyfikatory ataku/obrony; obiekty obronne są
   rozstrzygane w istniejących interakcjach jednostek/pocisków.
4. Wygasłe efekty przestają wpływać na dalsze fazy, a snapshot/eventy są
   publikowane z tym samym `simulationTimeMilliseconds`.

Dokładną granicę ticka wygaśnięcia i kolejność względem istniejących faz
`advanceUnits`, `resolveUnitInteractions`, projektyli i
`advanceLingeringEffects` trzeba zamrozić testami przed migracją. Różnice na
granicy czasu nie mogą zależeć od zegara urządzenia. Warunki aktywacji nie
mogą być implementowane w Android UI.

## Katalog migracyjny czarów legacy

Poniższa tabela jest baseline’em do testów parytetu. `Złoto` dotyczy zakupu /
progresji, a `Mana` aktywacji w meczu. Legacy’owa pula many miała maksimum i
wartość początkową 100 oraz regenerowała 10 many co 8 s symulacji.
Czas/parametry mają pochodzić z legacy, nie z nowej propozycji balansu.

| Typ legacy | Cel | Złoto | Mana | Efekt / parametry bazowe |
|---|---|---:|---:|---|
| `ICE_TRAP` — Lodowa pułapka | `ENEMY_FORCE` | 500 | 50 | rozstawiana na środku pola; promień 180; zatrzymuje wrogą jednostkę; aktywna 6 s; aktywacja zastępuje poprzednią pułapkę tej drużyny |
| `RUNIC_BASTION` — Runiczny Bastion | `OWN_GATE` | 1 200 | 70 | 360 HP; geometria `288 × 216` jednostek świata; stoi przed własną bramą, przesunięty o 436 jednostek ku środkowi pola; blokuje marsz i jest atakowalny; jeden aktywny na drużynę, ponowne rzucenie odrzucone bez kosztu aż do zniszczenia |
| `HASTE` | `ALLIED_FORCE` | 900 | 50 | 15 s (zaakceptowany override legacy 5 s); prędkość ruchu wysłanych sojuszników ×2 |
| `HEAL` | `ALLIED_FORCE` | 900 | 50 | leczy ranne żywe jednostki o 35% maksymalnego HP, w tym jednostki w garnizonie |
| `FORTRESS_SHIELD` — Tarcza fortecy | `OWN_FORTRESS` | 0 | 80 | osłona fortecy o 150 HP; nie można aktywować, gdy osłona jest już aktywna |
| `CATAPULT_LOCK` — Blokada katapulty | `ENEMY_SIEGE_ENGINE` | 1 000 | 60 | blokuje katapultę przeciwnika przez 5 s |
| `SLOWING_FIELD` — Pole spowolnienia | `ENEMY_FORCE` | 1 000 | 50 | spowalnia wrogie jednostki do 50% przez 7 s |
| `VAMPIRIC_BANNER` — Wampiryczny sztandar | `ALLIED_FORCE` | 1 100 | 60 | działa 8 s; co 1 s leczy sojusznicze ranne jednostki o 2% maksymalnego HP |
| `STONE_SKIN` — Kamienna skóra | `ALLIED_FORCE` | 1 100 | 60 | 6 s; otrzymywane obrażenia ×0,5 i prędkość ×0,75 |
| `SIEGE_FRENZY` — Szał oblężniczy | `ALLIED_FORCE` | 1 200 | 60 | 5 s; obrażenia sojuszników ×1,75 |
| `BERSERKER` | `ALLIED_UNIT` | 2 000 | 100 | wybiera kwalifikującego się Obrońcę najbliższego wrogiej fortecy; maksymalne i bieżące HP ×2, obrażenia ×2; efekt kończy się ze śmiercią jednostki |

W legacy zaklęcia specjalne to `CATAPULT_LOCK`, `SLOWING_FIELD`,
`VAMPIRIC_BANNER`, `STONE_SKIN`, `SIEGE_FRENZY` i `BERSERKER`. Ich
odblokowanie dodaje stock, aktywacja zużywa stock gracza, a zerowy stock
uniemożliwia użycie. Pozostałe zaklęcia są odblokowywane trwale. Domyślnie
odblokowana była Tarcza fortecy. Limit loadoutu wynosił 3, ale jego migracja
należy do LoadoutSystemu.

Legacy’owy `SpellType` zawiera ceny złota; `ManaConfig` zawiera koszty many,
czasy i modyfikatory; `SpellProgress` zarządzał odblokowaniem/stockiem przez
Android `SharedPreferences`; `MatchLoadout` opisuje wybór. Nowa implementacja
ma przenieść zachowanie, nie skopiować Androidowy model persystencji do core.

W obecnym `CampaignLevelDefinition` dostępność kampanii jest stopniowana:
Tarcza fortecy od początku, Haste od poziomu 6, Heal od 8, Lodowa pułapka od
11, Runiczny Bastion od 21. Należy zachować tę konfigurację i skoordynować ją
z trwałym odblokowaniem oraz zatwierdzonym loadoutem, bez uznawania dostępności
poziomu za wybór loadoutu.

## Błędy, konflikty i integracja

- Nieznany typ czaru, niewystarczająca mana/stock, niewłaściwy cel lub
  niedozwolony stan meczu zwraca jawne `Rejected(reason)`.
- Najpierw sprawdzane są wszystkie warunki; koszt i stock są zużywane dopiero
  przy udanej aktywacji.
- Aktywacja nie może zmieniać many lub zdrowia przez Android UI.
- Ponowne aktywacje zachowują konkretne reguły legacy: czasowe efekty
  odnawiają swój timer, Vampiric Banner resetuje licznik ticka, pułapka
  zastępuje własną poprzednią pułapkę, aktywny Bastion i tarcza odrzucają
  aktywację, a Berserker wybiera tylko nieprzemienionego
  Obrońcę. HASTE odświeża 15-sekundowy timer i nie kumuluje prędkości.
- Konflikty różnych buffów, redukcji obrażeń i efektów prędkości muszą
  odzwierciedlać istniejącą kolejność i matematykę legacy. Nie wprowadzać
  nowego stackowania, mnożników addytywnych ani priorytetów bez osobnej
  akceptacji. Zatwierdzony HASTE mnoży się z SLOWED; STUNNED ma pierwszeństwo
  jako zatrzymanie ruchu.
- AI, jeśli włączone później, czyta snapshot i wysyła ten sam
  `MatchCommand.CastSpell`; nie dostaje osobnej implementacji efektów.

## Android UI i assety

`android-app` jest adapterem:

- ekran loadoutu należący do LoadoutSystemu będzie prezentował
  zaklęcia pogrupowane według kategorii celu z metadanych domenowych;
- match screen wysyła akcję UI i komendę `CastSpell`; aktywuje
  `FORTRESS_SHIELD`, HASTE i `RUNIC_BASTION`, wyświetla manę, pozostały czas
  HASTE, zdrowie aktywnego Bastionu oraz powód odrzucenia;
- dostępność przycisku/komunikaty wynikają z snapshotu i odrzucenia komendy,
  nie z lokalnej kopii many/stocku;
- timer, aktywny buff, tarcza, pułapka i bastion są renderowane na podstawie
  niemutowalnego render state;
- ikony i polskie/angielskie etykiety są mapowane przez istniejące katalogi
  assetów/lokalizacji; core przechowuje klucze, nie Android resource ID.

Spell-card artwork jest już dostępny. Aktualną, zaakceptowaną przez użytkownika
12-klatkową animację HASTE z orbitującymi szewronami zintegrowano jako
`anim_spell_haste_orbiting_chevron_000.png`–`anim_spell_haste_orbiting_chevron_011.png`. Pojawia się za
żywymi, poruszającymi się jednostkami HASTED, rozkłada klatki na 16 ticków
renderera (~22,7 fps) i odbija efekt względem kierunku ruchu. Klatki są
przezroczystymi PNG RGBA 1024×576; każda ma co najmniej 69 px marginesu
widocznej zawartości i nie jest ucięta. Źródłowy obraz oraz oryginalne ZIP i
MP4 są zachowane w `assets_src/spells/haste/orbiting-chevrons-first-frame.png`
i `assets_src/spells/haste/orbiting-chevrons/`. Brief pierwszej klatki to
[`haste-orbiting-chevrons-first-frame.agp`](haste-orbiting-chevrons-first-frame.agp).
Poprzednie animacje quickstep i wind pozostają zarchiwizowane w
`assets_src/spells/haste/quickstep/` i `assets_src/spells/haste/wind-trail/`;
ich klatki nie są już aktywnymi runtime sprite'ami. Nie generuj ani nie
zastępuj animacji ponownie bez osobnej zgody. Opis Haste ma jasno wskazywać
sojuszniczy cel; UI nie może deklarować, że Haste przyspiesza przeciwnika.

## Etapowy plan migracji

Stan pionowego wycinka: etapy 2–5 są częściowo wdrożone dla katalogu,
kontraktów, HASTE i `FORTRESS_SHIELD`; mana, timer HASTE, ruch, tarcza i jej
prezentacja w match screenie działają. Battlefield VFX HASTE jest zintegrowany
z zatwierdzonego assetu AnimGen. Pozostałe efekty, prezentacja kategorii celu, AI
oraz trwałe rozliczanie stocku są jeszcze poza implementacją.

1. **Baseline i parytet.** Wartości kosztów i targetów są zapisane w
   `SpellCatalog`; pozostałe reguły efektów nadal wymagają porównania z
   legacy `SpellType.kt`, `Mana.kt`,
   poszczególnych klas czarów, `SpellContext`, `SpellSystem`, `GameEngine`,
   `SpellProgress` i zasobów UI. Dla każdego czaru zdefiniować cel,
   warunki aktywacji, czas/stackowanie oraz zachowanie na granicy ticka.
   Zarejestrować rozbieżność opisu Haste i użyć reguły sojuszniczej.
2. **Kontrakty domenowe — częściowo wdrożone.** Katalog obejmuje wszystkie
   stabilne typy, koszty i kategorie roli/celu; runtime effect definition i
   testy balansu uzupełniać wraz z implementacją kolejnych czarów.
3. **Kontrakty meczu — wdrożone dla tarczy, HASTE i Bastionu.** `CastSpell`
   zwraca `CommandResult`, event aktywacji niesie cel i koszt, a snapshot
   publikuje stan tarczy, czas HASTE oraz geometrię i zdrowie Bastionu. Nie wprowadzać zależności
   `game-contracts -> game-domain`.
4. **Granica loadoutu — wdrożona dla aktywacji.** Użyć istniejącego
   LoadoutSystemu i jego zatwierdzonego `PlayerLoadoutConfig`; odrzucać
   niedostępny lub niewyposażony czar. Nie implementować tymczasowego
   spell-only loadoutu ani nie traktować `availableSpells` jako wyboru gracza.
   Nie duplikować walidacji loadoutu ani jego persystencji.
5. **Deterministyczny runtime — częściowo wdrożony.** Mana, jej regeneracja,
   tarcza, HASTE, Bastion, interakcje jednostek z Bastionem i przechwytywanie
   pocisków działają w istniejącej symulacji. Migrować dalej leczenie,
   pułapki/katapultę i pozostałe reguły.
   Zachować jednego właściciela runtime state i ustaloną kolejność faz.
6. **Granica progresji.** Nie przenosić zakupów, trwałego złota, odblokowań
   ani zapisu profilu do domeny lub symulacji. Match launch otrzymuje
   zatwierdzoną dostępność/loadout i dozwolony stock runtime; wynik meczu
   raportuje zużycie do aplikacyjnej granicy progresji. Szczegóły trwałego
   rozliczenia pozostają zakresem PlayerProgressionSystemu.
7. **Android — częściowo wdrożony.** Match screen jest podłączony do komendy,
   wyniku komendy i render state dla tarczy, many, HASTE oraz Bastionu, w tym
   jego zdrowia, animacji world-space i dostępności ikony. Dodać pozostałe
   efekty i targetowanie; edytory Loadoutu pozostają własnością
   LoadoutSystemu. Spell-card artwork jest dostępny; integrować world-effect
   wizualizacje wraz z odpowiednimi efektami.
8. **Parytet i domknięcie.** Uruchomić testy domeny, kontraktów, symulacji,
   sesji aplikacji i Android mapper/UI; porównać wszystkie wartości i reguły z
   baseline’em legacy; utrwalić replay regression i sprawdzić brak zależności
   Android w core. Oddzielnie zweryfikować AI, jeśli jego aktywowanie czarów
   zostanie dodane do zakresu.

SpellSystem musi konsumować zatwierdzony kontrakt LoadoutSystemu; nie wolno
dodać tymczasowego drugiego właściciela wyboru ani walidacji.

## Plan testów i kryteria akceptacji

Kolejne etapy implementacji powinny dodać:

- testy katalogu: wszystkie 11 stabilnych ID, unikalność, kategorie i
  niezmienione koszty; dodać runtime effect definition pozostałych czarów;
- testy każdej aktywacji: koszt many, target, odrzucenie i brak częściowych
  skutków przy błędzie;
- testy targetu/strony: Haste obejmuje wyłącznie sojuszników; kontrola
  przeciwników nie dotyczy własnych jednostek; Berserker wybiera prawidłowego
  sojuszniczego Obrońcę;
- testy efektów i wygaśnięć: czas symulacji, granica ticka, reset/odnowienie,
  stock, leczenie, redukcja obrażeń, prędkość, pułapka, katapulta, tarcza i
  bastion;
- testy interakcji ze stanami jednostek, ruchem, walką, obrażeniami,
  pociskami i warunkami zwycięstwa;
- testy lifecycle: start, pauza, restart, stop/koniec meczu i brak wycieku
  runtime state pomiędzy meczami;
- testy replay: to samo scenario, komendy, delta czasu i seed dają identyczne
  snapshoty/eventy;
- testy `CampaignLevelDefinition.availableSpells` kontra loadout dostarczony
  przez LoadoutSystem;
- testy eventów, statusów/snapshotów oraz adaptera Android bez mutowania
  gameplayu przez UI;
- test architektury: brak Androida w core, jeden właściciel balansu i runtime,
  brak drugiego SpellSystemu/loadoutu.

Pełna akceptacja SpellSystemu wymaga wszystkich 11 efektów. Obecny wycinek
zapewnia jawną aktywację i odrzucenia HASTE oraz tarczy, testy deterministyczne
i wykorzystanie wspólnego loadoutu. Pełny runtime HASTE i jego world-space VFX
są zintegrowane. Pozostałe efekty, targetowanie i ich prezentacja w Androidzie
są nadal otwarte.

## Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji | Ocena |
|---|---|---|---|---|---|
| Rozszerzyć istniejące typy/komendy/snapshoty i dodać runtime w symulacji | wysoka, jeśli role/targety są ortogonalne | stały, mały koszt katalogu | wysoka, core bez Androida | średni, wymaga integracji domena–symulacja–aplikacja | **Rekomendowany** |
| Zbudować drugi niezależny SpellEngine/katalog równolegle do istniejących kontraktów | niska, podwójne źródła prawdy | zbędny koszt i synchronizacja | niska, trudne testy zgodności | wysoki, duplikacja gateway/UI/snapshotów | odrzucony |
| Umieścić reguły w `MatchSession`, `MainActivity` lub jednym `when` w symulacji | niska, centralny God Object | pozornie prosty, rosnący koszt rozgałęzień | niska, reguły trudniej izolować | początkowo mały, docelowo najwyższy | odrzucony |

Rekomendowane rozszerzenie wykorzystuje obecne kontrakty i strukturę
modułową, a jednocześnie zachowuje oddzielną odpowiedzialność przyszłego
LoadoutSystemu.

## Otwarte decyzje i brakujący zakres

- czy kolejne aktywne czary mają rozszerzyć `ActiveSpellSnapshot`, czy użyć
  kontraktów wyspecjalizowanych dla własnych efektów;
- pełna reguła wygaśnięcia efektu na granicy ticka i matematyka nakładających
  się różnych buffów, potwierdzona testami parytetu legacy;
- rozliczenie trwałego stocku w przypadku przerwanego/zakończonego meczu;
- które efekty wymagają osobnych assetów, a które mogą użyć istniejących
  zasobów; ilustracje kart zaklęć są dostępne, natomiast mapowanie wizualizacji
  world-effectów pozostaje do rozstrzygnięcia dla kolejnych efektów.

Nadal trzeba domknąć trwałe rozliczanie stocku, pozostałe efekty oraz ich
prezentację. Zrealizowana tarcza i HASTE nie zmieniają
przyjętych zasad: Haste działa tylko na sojuszników, LoadoutSystem pozostaje
właścicielem loadoutu, a SpellSystem nie posiada persystencji.

## Proponowane pliki przyszłej implementacji

Lista pozostałych prac:

- `game-domain/.../Spells.kt` i `SpellCatalog.kt` — stabilne ID, koszty i
  kategorie są już wdrożone; uzupełniać definicje efektów;
- `game-domain/.../SpellRules.kt` lub wyspecjalizowane reguły — tylko jeśli
  potrzebne po analizie istniejących helperów;
- `game-contracts/.../MatchCommands.kt`, `MatchEvents.kt`,
  `MatchSnapshot.kt` — komenda, wynik, eventy i odczyt efektów;
- `game-simulation/.../DeterministicMatchSimulation.kt` — mana i tarcza są
  już wdrożone; pozostałe efekty integrować w tej samej symulacji;
- `game-application/.../MatchSession.kt` — wynik aktywacji i lifecycle są już
  wdrożone dla `CastSpell`;
- `game-campaign/.../CampaignLevelDefinition.kt` — dostępność bez logiki
  progresji/loadoutu;
- `android-app/.../MatchViewModel.kt`, match-owned UI/render mappery i przyszły
  ekran LoadoutSystemu;
- odpowiednie testy w `game-domain`, `game-contracts`, `game-simulation`,
  `game-application` i `android-app`.

Nie tworzyć nowego modułu Gradle ani implementować LoadoutSystemu jako części
SpellSystemu.
