# SpellSystem

## Status i cel

Ten dokument opisuje docelową architekturę oraz plan migracji zaklęć z
legacy. Nie oznacza, że system albo opisane kontrakty są już zaimplementowane.
Zakres tej zmiany obejmuje wyłącznie dokumentację i skill `/addSpell`.

SpellSystem ma przenosić efekty, koszty i reguły aktywacji zaklęć do
deterministycznego meczu Urkaaaz, zapewniając jeden katalog balansu, jawne
granice komend/eventów oraz możliwość dodawania kolejnych zaklęć bez
rozbudowywania centralnego obiektu z warunkami dla każdego typu.

Migracja zachowuje obserwowalne zachowanie i balans z legacy. Wyjątkiem jest
rozbieżność w opisie Haste: użytkownik potwierdził, że działa wyłącznie na
sojuszników. Tę regułę należy traktować jako źródło prawdy i skorygować
prezentację, nawet jeśli starszy tekst UI mówi o wszystkich jednostkach.

## Zakres i wyłączenia

W zakresie:

- definicje zaklęć, targety, koszty many i efekty gameplayowe;
- deterministyczny runtime, aktywne czasy, pułapki i fortyfikacje;
- jawne aktywacje, odrzucenia, eventy i snapshoty;
- integracja z dostępnością kampanii, progresją, złotem i stockiem zaklęć
  specjalnych;
- Androidowy wybór/aktywacja zaklęć, prezentacja kategorii celu i efektów;
- migracja wszystkich 11 zaklęć legacy z testami zgodności.

Poza zakresem SpellSystemu:

- wybieranie, walidowanie, przechowywanie i prezentowanie loadoutu;
- implementacja ani przejmowanie odpowiedzialności istniejącego
  `LoadoutSystem`;
- własność trwałego złota, odblokowań i stocku gracza;
- reguły kampanii, nawigacji i Android lifecycle;
- implementacja reguł gameplayowych w Android UI lub osobna implementacja
  tych reguł w AI.

`LoadoutSystem` jest częściowo zaimplementowany i ma wspólny właścicielski
model wyboru jednostek, amunicji i zaklęć w `game-application`. Wcześniejszy
`game-domain.SpellLoadout` został usunięty. SpellSystem nie powinien tworzyć
równoległej wersji loadoutu ani przejmować jego walidacji. Runtime otrzyma
niemutowalny, zatwierdzony wynik loadoutu jako konfigurację meczu. Lokalny
profil, zapis loadoutu i bezpieczne zatwierdzanie są zaimplementowane;
interaktywne edytory, zakupy i autoryzacja milestone'ów kampanii pozostają
niedokończone.

## Obecne elementy i granice

W repozytorium istnieją już:

| Element | Obecna rola | Docelowe użycie |
|---|---|---|
| `game-domain/Spells.kt: SpellType` | stabilne typy zaklęć, koszt złota, kategoria roli, flaga special | zachować identyfikatory; uporządkować definicje i balans w jednym katalogu |
| `game-domain/Spells.kt: SpellCategory` | `OFFENSIVE`, `DEFENSIVE`, `BATTLEFIELD_CONTROL`, `SUPPORT`, `SIEGE` | zachować jako kategorię roli/efektu; nie używać jako kategorii targetu |
| `game-application/application/loadout/MatchLoadout` | wspólny wybór jednostek, amunicji i czarów | kanoniczny model LoadoutSystemu |
| `MatchCommand.CastSpell` | komenda z ID tekstowym i opcjonalnym celem | wejście do walidacji/aktywacji; nie zmienia stanu bezpośrednio |
| `MatchEvent.SpellCast` | event udanej aktywacji | rozszerzyć lub zastąpić typowanymi eventami bez utraty kompatybilności kontraktu |
| `MatchSnapshot.effects` / `EffectSnapshot` | ogólny odczyt efektów | wykorzystać po weryfikacji wystarczalności; nie dublować stanu renderowania |
| `ResourceSnapshot.mana/gold` | odczyt zasobów | mana w runtime meczu; złoto w progresji/aplikacji |
| `CampaignLevelDefinition.availableSpells` | dostępność zaklęć na poziomie | pozostaje konfiguracją kampanii i wejściem do match launch |
| `MatchSession` | lifecycle, pauza, restart i forwarding | przekazywać komendy; tworzyć świeży runtime przy starcie/restart |
| `DeterministicMatchSimulation` | deterministyczny runtime meczu | właściciel stanu SpellSystemu w ramach symulacji |
| `LoadoutScreen` | dostępność poziomu, zwalidowany draft i stan zapisu | interaktywne edytory LoadoutSystemu pozostają do wdrożenia |

Kontrakty przewidują `CastSpell`, `SpellCast`, `EffectSnapshot` oraz
`availableSpells`, ale `MatchSession` nie obsługuje jeszcze `CastSpell`, a
symulacja nie ma aktywnego SpellSystemu. Nie należy uznawać samych kontraktów
za działającą implementację.

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

Docelowy katalog domenowy powinien być jedynym źródłem:

- stabilnego `SpellType`/ID;
- obecnej kategorii roli `SpellCategory`;
- kategorii celu;
- kosztu złota, kosztu many i zasad stocku;
- czasu działania, parametrów efektu i reguł aktywacji;
- kluczy lokalizacji/assetów, bez zależności od Android `R`.

`SpellType` zachowuje stabilne identyfikatory, a katalog dostarcza ich
kompletne metadane. Migracja ma przenieść obecne `goldCost`, `category` i
`special` do jednej definicji katalogowej, zamiast utrzymywać te same wartości
w enumie i drugim katalogu. Nie może po cichu zmieniać nazw `ICE_TRAP`,
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
   właścicielem wyboru i walidacji tego loadoutu.
4. Autorytatywny stan symulacji: mana, jednostki, katapulty, fortece,
   aktywne efekty i stock przypisany do bieżącego meczu.
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
Zgodnie z opisem w `architecture.md` wynik powinien mieć postać
`Accepted` albo `Rejected(reason)`. Przyczyny obejmują co najmniej:
nieznany czar, mecz nieaktywny, czar niedostępny na poziomie, czar
niewyposażony, brak many, brak stocku, brak prawidłowego celu i efekt już
aktywny, jeśli dany czar nie może być ponownie aktywowany.

### Wyjście

- `SpellCast`/`SpellActivated` jako fakt udanej aktywacji z graczem/drużyną,
  typem, czasem symulacji i celem, jeżeli istnieje;
- jawne odrzucenie komendy z typowaną przyczyną;
- aktualizowany `MatchSnapshot`, w tym mana, statusy jednostek oraz odczyt
  aktywnych efektów i obiektów świata;
- event zużycia stocku meczu, jeśli jest potrzebny do utrzymania trwałej
  progresji po zakończeniu meczu.

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
| Mana, bieżący stock meczu, aktywne timery, pułapki, tarcze i bastiony | jeden runtime `DeterministicMatchSimulation` |
| Snapshot/render state | kontrakty i mapper Androida jako niemutowalny odczyt |

Trwały stock w progresji jest oddzielony od stocku przekazanego do meczu.
Symulacja zużywa tylko swój runtime stock i raportuje zmianę; aplikacja
rozlicza zapis trwałej progresji przez jawny wynik/event. Nie przenosić
`SharedPreferences` ani repozytorium progresji do `game-domain` lub
`game-simulation`.

Nowy mecz tworzy świeży SpellSystem/runtime. Pauza nie przesuwa timerów, bo
`MatchSession` nie przekazuje kroków symulacji. Restart tworzy świeży stan
zaklęć z konfiguracji meczu, a zakończenie meczu uniemożliwia dalsze
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
| `RUNIC_BASTION` — Runiczny Bastion | `OWN_GATE` | 1 200 | 70 | 360 HP; ustawiany względem własnej bramy; blokuje marsz i jest atakowalny; ponowna aktywacja odnawia HP |
| `HASTE` | `ALLIED_FORCE` | 900 | 50 | 5 s; prędkość sojuszników ×2 |
| `HEAL` | `ALLIED_FORCE` | 900 | 50 | leczy ranne żywe jednostki o 35% maksymalnego HP, w tym jednostki w garnizonie |
| `FORTRESS_SHIELD` — Tarcza fortecy | `OWN_FORTRESS` | 0 | 80 | osłona fortecy o 80 HP; nie można aktywować, gdy osłona jest już aktywna |
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
  zastępuje własną poprzednią pułapkę, Bastion odnawia HP, tarcza odrzuca
  aktywację, gdy nadal działa, a Berserker wybiera tylko nieprzemienionego
  Obrońcę.
- Konflikty różnych buffów, redukcji obrażeń i efektów prędkości muszą
  odzwierciedlać istniejącą kolejność i matematykę legacy. Nie wprowadzać
  nowego stackowania, mnożników addytywnych ani priorytetów bez osobnej
  akceptacji.
- AI, jeśli włączone później, czyta snapshot i wysyła ten sam
  `MatchCommand.CastSpell`; nie dostaje osobnej implementacji efektów.

## Android UI i assety

`android-app` jest adapterem:

- ekran loadoutu należący do LoadoutSystemu będzie prezentował
  zaklęcia pogrupowane według kategorii celu z metadanych domenowych;
- match screen wysyła akcję UI i komendę CastSpell;
- dostępność przycisku/komunikaty wynikają z snapshotu i odrzucenia komendy,
  nie z lokalnej kopii many/stocku;
- timer, aktywny buff, tarcza, pułapka i bastion są renderowane na podstawie
  niemutowalnego render state;
- ikony i polskie/angielskie etykiety są mapowane przez istniejące katalogi
  assetów/lokalizacji; core przechowuje klucze, nie Android resource ID.

Migracja musi sprawdzić dostępne pliki ikon/efektów legacy i istniejące zasoby
Urkaaaz przed tworzeniem nowych assetów. Opis Haste ma jasno wskazywać
sojuszniczy cel. UI nie może deklarować, że Haste przyspiesza przeciwnika.

## Etapowy plan migracji

1. **Baseline i parytet.** Spisać wartości z `SpellType.kt`, `Mana.kt`,
   poszczególnych klas czarów, `SpellContext`, `SpellSystem`, `GameEngine`,
   `SpellProgress` i zasobów UI. Dla każdego czaru zdefiniować cel,
   warunki aktywacji, czas/stackowanie oraz zachowanie na granicy ticka.
   Zarejestrować rozbieżność opisu Haste i użyć reguły sojuszniczej.
2. **Kontrakty domenowe.** Uporządkować stabilne typy i utworzyć jeden
   katalog definicji balansu z ortogonalnymi kategoriami roli i celu.
   Dodać walidację kompletności/unikalności oraz testy całego katalogu.
3. **Kontrakty meczu.** Podłączyć `CastSpell` do jawnego wyniku komendy,
   rozbudować event aktywacji i niemutowalny snapshot efektów. Dodać typowane
   odrzucenia. Nie wprowadzać zależności `game-contracts -> game-domain`.
4. **Granica loadoutu.** Użyć istniejącego LoadoutSystemu. Jego
   zatwierdzony wynik musi wejść do launch config/scenario. Nie implementować
   tymczasowego spell-only loadoutu ani nie traktować `availableSpells` jako
   wyboru gracza. Nie duplikować jego walidacji ani persystencji.
5. **Deterministyczny runtime.** Dodać obsługę aktywacji i ticków w istniejącej
   symulacji. Migrować najpierw zasoby/aktywację, potem czasowe buffy, leczenie
   i targetowanie jednostek, następnie pułapki/katapultę/tarczę/bastion.
   Zachować jednego właściciela runtime state i ustaloną kolejność faz.
6. **Progresja.** Przenieść reguły ceny złota, trwałego odblokowania i stocku
   do warstwy aplikacyjnej. Konfiguracja meczu otrzymuje tylko zatwierdzone
   dostępne czary i runtime stock. Zapis zmian stocku przechodzi przez jawne
   eventy/wynik meczu; `SharedPreferences` nie trafia do domeny/symulacji.
7. **Android.** Podłączyć match screen do komendy i render state; rozszerzyć
   istniejący ekran loadoutu o edytory czarów; pokazać grupy celu, koszty,
   stock, aktywne czasy oraz powody odrzuceń. Użyć lub zweryfikować istniejące
   ikony i lokalizacje.
8. **Parytet i domknięcie.** Uruchomić testy domeny, kontraktów, symulacji,
   sesji aplikacji i Android mapper/UI; porównać wszystkie wartości i reguły z
   baseline’em legacy; utrwalić replay regression i sprawdzić brak zależności
   Android w core. Oddzielnie zweryfikować AI, jeśli jego aktywowanie czarów
   zostanie dodane do zakresu.

SpellSystem musi konsumować zatwierdzony kontrakt LoadoutSystemu; nie wolno
dodać tymczasowego drugiego właściciela wyboru ani walidacji.

## Plan testów i kryteria akceptacji

Przyszła implementacja powinna dodać:

- testy katalogu: wszystkie 11 stabilnych ID, unikalność, kompletna definicja,
  kategorie i niezmienione koszty/parametry;
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

Akceptacja implementacji wymaga wszystkich 11 efektów, jawnych odrzuceń,
zachowania legacy balansu, testów deterministycznych, wykorzystania wspólnego
loadoutu z LoadoutSystemu, prezentacji kategorii celu oraz braku duplikacji
zasobów/progresji.

## Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji | Ocena |
|---|---|---|---|---|---|
| Rozszerzyć istniejące typy/komendy/snapshoty i dodać runtime w symulacji | wysoka, jeśli role/targety są ortogonalne | stały, mały koszt katalogu | wysoka, core bez Androida | średni, wymaga integracji domena–symulacja–aplikacja | **Rekomendowany** |
| Zbudować drugi niezależny SpellEngine/katalog równolegle do istniejących kontraktów | niska, podwójne źródła prawdy | zbędny koszt i synchronizacja | niska, trudne testy zgodności | wysoki, duplikacja gateway/UI/snapshotów | odrzucony |
| Umieścić reguły w `MatchSession`, `MainActivity` lub jednym `when` w symulacji | niska, centralny God Object | pozornie prosty, rosnący koszt rozgałęzień | niska, reguły trudniej izolować | początkowo mały, docelowo najwyższy | odrzucony |

Rekomendowane rozszerzenie wykorzystuje obecne kontrakty i strukturę
modułową, a jednocześnie zachowuje oddzielną odpowiedzialność przyszłego
LoadoutSystemu.

## Otwarte decyzje przed implementacją

- dokładny kontrakt przekazania zatwierdzonego loadoutu przez przyszły
  LoadoutSystem do match launch;
- pełna reguła wygaśnięcia efektu na granicy ticka i matematyka nakładających
  się różnych buffów, potwierdzona testami parytetu legacy;
- rozliczenie trwałego stocku w przypadku przerwanego/zakończonego meczu;
- które efekty wymagają odtworzenia osobnych assetów, a które mogą użyć już
  dostępnych zasobów.

Te kwestie nie zmieniają przyjętych zasad: brak zmian balansu, Haste tylko dla
sojuszników, LoadoutSystem jako osobny właściciel loadoutu i SpellSystem bez
Androida/persystencji.

## Proponowane pliki przyszłej implementacji

Lista orientacyjna; dokładne pliki ustalić po ponownym odczytaniu bieżącego
kodu:

- `game-domain/.../Spells.kt` — stabilne ID i obecna kategoria roli;
- `game-domain/.../SpellCatalog.kt` — definicje, balans i kategorie celu;
- `game-domain/.../SpellRules.kt` lub wyspecjalizowane reguły — tylko jeśli
  potrzebne po analizie istniejących helperów;
- `game-contracts/.../MatchCommands.kt`, `MatchEvents.kt`,
  `MatchSnapshot.kt` — komenda, wynik, eventy i odczyt efektów;
- `game-simulation/.../SpellSimulation.kt` — aktywacja/tick wpięte do
  `DeterministicMatchSimulation`;
- `game-application/.../MatchSession.kt` — forwarding, wynik i lifecycle;
- `game-campaign/.../CampaignLevelDefinition.kt` — dostępność bez logiki
  progresji/loadoutu;
- `android-app/.../MatchViewModel.kt`, match-owned UI/render mappery i przyszły
  ekran LoadoutSystemu;
- odpowiednie testy w `game-domain`, `game-contracts`, `game-simulation`,
  `game-application` i `android-app`.

Nie tworzyć nowego modułu Gradle ani implementować LoadoutSystemu jako części
SpellSystemu.
