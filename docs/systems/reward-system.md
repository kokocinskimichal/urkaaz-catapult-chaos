# RewardSystem

## Status i cel

**Projekt architektury — system nie jest zaimplementowany.**

`RewardSystem` przyznaje złoto za ukończenie kampanijnego poziomu oraz za
spełnione w jego trakcie osiągnięcia. Warunki osiągnięć są oceniane na
podstawie eventów meczu, a nagrody są rozliczane dopiero po zwycięstwie BLUE.
Po zapisanym zwycięstwie poziom jest ukończony i nie można go rozegrać ponownie;
porażka, remis lub poddanie nie zużywają podejścia i nie przyznają złota.

System ma być rozszerzalny o kolejne poziomowe warunki i typy nagród bez
przenoszenia reguł do Android UI ani tworzenia równoległego właściciela
kampanii lub profilu gracza. Dokument nie ustala kwot, balansu ani finalnej
listy osiągnięć.

## Zakres

### W zakresie

- nagroda za zwycięskie ukończenie poziomu;
- osiągnięcia oceniane z eventów jednej sesji meczu;
- rozbicie wyniku na nagrodę za wygraną, nagrody za osiągnięcia i sumę;
- dokładnie jednorazowa wypłata dla poziomu;
- atomowy zapis zwycięstwa, odblokowania następnego poziomu i salda złota;
- ponowne podejście po wyniku innym niż `BLUE_WIN`;
- prezentacja wyniku nagrody w istniejącym popupie wyniku meczu.

### Poza zakresem

- osiągnięcia globalne, dzienne, kolekcjonerskie ani niezwiązane z poziomem;
- nagrody za przegraną, remis, poddanie lub mecz demo;
- sklep, ceny i transakcje zakupowe;
- reklamy, zakupy za prawdziwe pieniądze, konta i synchronizacja chmurowa;
- rozliczanie amunicji lub czarów zużytych w meczu;
- nowe ekrany, animacje nagród, assety i backend;
- ustalanie kwot, progów lub limitu maksymalnego salda.

## Właściciele i granice modułów

Nie powstaje nowy moduł Gradle.

| Odpowiedzialność | Właściciel |
|---|---|
| Definicje poziomowych nagród i warunków osiągnięć | `game-campaign`, przez rozszerzony katalog nagród kampanii |
| Tracking eventów meczu, ocena osiągnięć i składanie wyniku nagrody | `RewardSystem` w `game-application` |
| Odblokowanie poziomów i zapis ukończenia kampanii | istniejący `CampaignSystem` w `game-application` |
| Saldo złota i zatwierdzone odblokowania profilu | istniejący `PlayerProgressionSystem` w `game-application` |
| Atomowy adapter lokalnej persystencji | `android-app.persistence`, przez istniejący DataStore |
| Wynik nagrody i akcje użytkownika | istniejący match result popup w `android-app` |

### Kierunek zależności

```text
android-app ──► game-application (RewardSystem, CampaignSystem, PlayerProgressionSystem)
                      │                       │
                      ▼                       ▼
               game-campaign (definicje)   game-contracts (MatchEvent)
```

`game-contracts` i `game-campaign` nie zależą od `game-application` ani
Androida. `RewardSystem` nie importuje `game-simulation`; konsumuje wyłącznie
eventy z kontraktów. Adapter DataStore implementuje port zdefiniowany w
`game-application`.

### Replay

Tracker jest czystą funkcją uporządkowanego strumienia eventów. Odtworzenie
`MatchReplay` przez tę samą sekwencję eventów musi dać identyczny wynik
nagrody; replay nie wykonuje rozliczenia ani zapisu, chyba że zostanie to
jawnie zatwierdzone.

`RewardSystem` jest właścicielem przejściowego stanu trackingu osiągnięć
jednego meczu, ale nie jest właścicielem `CampaignProgress`, profilu ani
runtime state symulacji. Tracker należy do zakresu życia `MatchSession`, nie
do `MatchViewModel`, widoku, globalnego singletonu ani grafu domenowego meczu.
UI dostaje wyłącznie niemutowalny wynik.

## Istniejące rozwiązania i ryzyko duplikacji

Obecny przepływ ma trzy istotne elementy:

- `CampaignSystem` przyjmuje `MatchEvent.MatchFinished`, tylko `BLUE_WIN`
  oznacza ukończenie, zapisuje `completedLevels`, odblokowuje kolejny poziom
  i atomowo dodaje spell-milestones.
- `CampaignMilestoneRegistry` w `game-campaign` definiuje nagrody kampanii
  odblokowujące Haste i Heal.
- `LoadoutProgressionSnapshot` przechowuje `goldBalance`, a
  `PlayerProgressionUseCases` go odczytuje. Nie istnieje jeszcze use case ani
  repozytorium do naliczania złota. Profil i kampania korzystają z odrębnych
  kluczy w tym samym AndroidX DataStore.

Dodanie osobnego, niezależnego `CampaignRewardRegistry` obok milestone registry
grozi rozdzieleniem definicji nagród, kolizjami identyfikatorów oraz różną
polityką przyznawania. Dopisanie trackingu, profilu i wszystkich nagród
bezpośrednio do `CampaignSystem` mieszałoby ukończenie kampanii z oceną
warunków meczu i ekonomią. Wyliczanie nagród w Android UI łamałoby granice
architektury i utrudniało deterministyczne testy.

Rekomendacja: rozdzielić **tracking i wyliczenie** od istniejącego właściciela
kampanii, lecz rozszerzyć wspólną definicję nagród i użyć jednej aplikacyjnej
granicy **atomowego rozliczenia**. Istniejący `CampaignSystem` nadal
autoryzuje poziomy i zachowuje milestone'y, a jego zapis powinien korzystać z
tej samej transakcji co zmiana złota. `PlayerProgressionSystem` pozostaje
właścicielem salda profilu.

## Rozważone warianty

| Wariant | Czytelność | Wydajność | Testowalność | Koszt integracji |
|---|---|---|---|---|
| Dodać tracking i wypłatę w całości do `CampaignSystem` | Średnia: odpowiedzialność kampanii rośnie o analizę eventów i ekonomię | Wystarczająca | Średnia; trudniej izolować reguły osiągnięć | Niski początkowo, rosnący przy kolejnych rodzajach nagród |
| Osobny `RewardSystem`, wspólne definicje nagród i transakcyjne rozliczenie | Wysoka: tracking, postęp kampanii i saldo mają jasne role | Wystarczająca; przetwarza tylko eventy meczu | Wysoka; tracker i persystencję można testować niezależnie | Umiarkowany; wymaga rozszerzenia granicy use case/repository |
| Rozliczać osiągnięcia i złoto w Android UI | Niska: logika gameplayowa w warstwie prezentacji | Wystarczająca | Niska; zależna od ekranu i lifecycle UI | Pozornie niski, z wysokim ryzykiem duplikacji i utraty stanu |

Rekomendowany jest drugi wariant. Pozwala rozwijać osiągnięcia i przyszłe
typy nagród bez przeciążania CampaignSystem, zachowuje pojedyncze źródło
prawdy dla poziomów i salda, a reguły pozostają testowalne bez Androida.

## Definicje nagród i osiągnięć

Rozszerzalny katalog kampanii powinien być jedynym źródłem definicji nagród
poziomowych. Zalecanym kierunkiem jest uogólnienie istniejącego
`CampaignMilestoneRegistry` do katalogu nagród kampanii, zachowując istniejące
stabilne ID spell-milestones i ich dostępność. Nie należy tworzyć drugiego
katalogu równoległego.

Definicja nagrody powinna mieć:

- stabilny identyfikator, niezmienny po opublikowaniu danych persystencji;
- poziom lub jawny zakres poziomów, do których należy;
- warunek przyznania: zwycięskie ukończenie albo konkretne osiągnięcie;
- typowaną nagrodę; w początkowym zakresie jest to wyłącznie złoto;
- kwotę wyłącznie jako konfigurację kampanii, nie jako wartość zakodowaną
  w trackerze, ViewModelu ani katalogu profilu;
- ewentualną dostępność od poziomu, gdy dotyczy istniejącego milestone'u.

Warunki powinny być typowanymi danymi, a nie dowolnymi lambdami lub skryptami
zapisanymi w definicji. Osobny evaluator w `game-application` mapuje
obsługiwane warunki na eventy. Dzięki temu katalog pozostaje walidowalny,
stabilny i niezależny od Androida. Dodanie nowego rodzaju warunku wymaga
jawnego rozszerzenia kontraktu/evaluatora i testów.

Możliwe przykłady do oceny w przyszłej konfiguracji — nie są zatwierdzonymi
kwotami ani balansem:

- bezpośrednie trafienie wrogiej jednostki pociskiem z katapulty podczas
  zwycięskiego meczu;
- szybkie pokonanie przeciwnika, mierzone czasem symulacji meczu;
- zwycięstwo bez otrzymania obrażeń przez własną fortecę;
- seria kolejnych bezpośrednich trafień pociskiem z katapulty we wrogą
  fortecę: pierwsze trafienie rozpoczyna serię bez bonusu, drugie przyznaje
  bonus, a każde następne trafienie z rzędu zwiększa jego poziom.

Każdy przykład wymaga dostatecznych danych o drużynie, celu i źródle w
eventach. Jeżeli kontrakt eventu tego nie gwarantuje, kryterium nie może być
implementowane przez heurystykę w UI ani przez odgadywanie z identyfikatorów
tekstowych. Wynik audytu obecnego kontraktu opisuje sekcja „Audyt kontraktu
eventów”.

### Audyt kontraktu eventów

Audyt wykonany na `MatchEvents.kt`, `DeterministicMatchSimulation.kt` i
`MatchSession.kt` (stan repozytorium w chwili pisania dokumentu).

**Co wystarcza już dziś**

- Bezpośrednie trafienie wyróżnia wyłącznie `ProjectileHitEntity`
  (`projectileId`, `targetId`, `targetLayer`). Obrażenia obszarowe nigdy go
  nie emitują; powstaje po nich tylko `DamageApplied` z `sourceProjectileId`.
  Trafienie bezpośrednie = `ProjectileHitEntity` dla pary pocisk–cel.
- Czas: każdy event niesie `simulationTimeMilliseconds`, a czas symulacji
  rośnie tylko w `advance`, który podczas pauzy nie jest wykonywany.
  `MatchFinished` niesie wynik i czas końcowy.
- Strzały gracza i przeciwnika emitują `ProjectileFired` z `catapultId`; strzał
  odrzucony nie emituje eventu. Fragmenty pocisków mają własne ID, ale
  `ProjectileSplit` mapuje je na pocisk macierzysty.
- Tożsamość i drużyna katapult oraz fortec są dostępne w `MatchSnapshot`
  (`CatapultSnapshot.team`, `FortressSnapshot.team`) i są stałe w meczu, więc
  tracker może je wczytać raz ze snapshotu startowego bez parsowania ID.
- Replay: `MatchReplayPlayer` zwraca te same eventy (ID `event-N`,
  `projectile-N` są deterministyczne), więc tracker karmiony replayem daje ten
  sam wynik.

**Luki kontraktu (wymagają rozszerzenia przed implementacją)**

1. **Brak drużyny w eventach.** `ProjectileFired`, `ProjectileHitEntity`,
   `DamageApplied` i `UnitDeployed` nie niosą drużyny.
   Drużyna jednostek istnieje tylko w snapshotach, a jednostki giną i
   pojawiają się dynamicznie, więc odtworzenie jej ze snapshotu po fakcie jest
   zawodne. Jedyną drogą jest parsowanie tekstu ID (`blue-…`, `red-…`), czego
   dokument zabrania; istniejące `startsWith("red")` w `MatchSession.Fire`
   nie jest wzorcem do kopiowania. Jednostka gracza może też być trafiona przy
   polityce friendly-fire danej amunicji, więc „wroga jednostka” wymaga
   jawnej drużyny celu.
2. **Brak eventu zakończenia pocisku bez trafienia.** Pocisk, który opuszcza
   mapę lub wygasa (`advanceProjectile` zwraca `null`), nie emituje żadnego
   eventu. Wiele pocisków może lecieć jednocześnie (przeładowanie jest
   per amunicja, nie turowe), więc kolejność trafień nie musi odpowiadać
   kolejności strzałów. Bez eventu rozstrzygnięcia strzału nie da się
   jednoznacznie zdefiniować „poprzedniego strzału” dla serii.
3. **Niewiarygodna warstwa w `DamageApplied`.** Obrażenia jednostek zadawane
   jednostkom i eksplozje demolishera używają domyślnej warstwy `FORTRESS`, a
   `ammunitionType` jest stałe `"AMMUNITION"`. Warstwa nie może być kluczem
   klasyfikacji; należy używać `targetId` porównanego z ID fortecy ze
   snapshotu startowego.
4. **Bastion i tarcza.** Trafienie w Runic Bastion ma `targetLayer =
   FORTRESS`, tak jak fortecę; trafienie w tarczę fortecy ma
   `FORTRESS_SHIELD` i generuje `ShieldDamaged`, a nie `DamageApplied`.
   Fortecę od bastionu odróżnia tylko porównanie `targetId` z ID fortec ze
   snapshotu.
5. **Dostarczanie eventów.** `MatchSession.dispatch` zeruje `latestEvents` na
   początku każdej komendy; zdarzenia nie odebrane przez `consumeEvents` są
   tracone. Tracker nie może polegać na `consumeEvents`, tylko musi być
   zasilany w `MatchSession` w miejscu, gdzie eventy powstają, przed
   udostępnieniem ich UI.

**Proponowane rozszerzenia kontraktu** (propozycje, wymagają akceptacji;
dodanie pól z wartościami domyślnymi nie zmienia istniejących konsumentów):

- `ProjectileFired`: drużyna strzelającego;
- `ProjectileHitEntity`: drużyna celu oraz rodzaj celu (jednostka, fortecy,
  bastion, tarcza) zamiast jedynie tekstowej warstwy;
- `DamageApplied`: drużyna celu; poprawna warstwa dla obrażeń jednostek;
- `UnitDeployed`: drużyna jednostki;
- nowy event `ProjectileResolved(projectileId, reason)` emitowany raz, gdy
  pocisk opuszcza symulację (powody: trafienie w encję, tarczę lub teren,
  wyjście poza mapę, wygaśnięcie); szczegóły w
  [planie implementacji](reward-system-implementation-plan.md).

`UnitDefeated` pozostaje bez zmian: bonusy oparte na zabójstwach są poza
zakresem.

**Gotowość przykładowych osiągnięć**

| Osiągnięcie | Gotowość na obecnym kontrakcie | Czego brakuje |
|---|---|---|
| Szybkie pokonanie przeciwnika | Gotowe | — |
| Zwycięstwo bez obrażeń własnej fortecy | Gotowe z `targetId` ze snapshotu startowego | — (tarcza i bastion nie liczą się jako obrażenia fortecy) |
| Bezpośrednie trafienie wrogiej jednostki | Częściowo | Luka 1 (drużyna celu) |
| Seria trafień w wrogą fortecę | Częściowo | Luki 1, 2 i 4; decyzja o kolejności strzałów |
| Odwet, Ostatni strzał | Częściowo | Luki 1 i 4 |

Zasady klasyfikacji, które wynikają z audytu i mają być stosowane w
evaluatorze:

- Bezpośrednie trafienie w fortecę = `ProjectileHitEntity` z celem o ID
  fortecy wrogiej drużyny, z pocisku wystrzelonego przez gracza. Trafienie
  w tarczę, w bastion i obrażenia obszarowe tego nie spełniają — decyzja
  użytkownika: trafienie w tarczę ani w bastion się nie liczy. To samo
  dotyczy „nienaruszonej twierdzy”: obrażenia przyjęte przez tarczę lub
  bastion własnej fortecy nie są obrażeniami fortecy.
- Pocisk z piercingiem może wygenerować wiele `ProjectileHitEntity`; trafienie
  liczy się raz na parę (pocisk, cel).
- Strzał gracza obejmuje pocisk i jego fragmenty (przez `ProjectileSplit`);
  jest trafieniem w fortecę, jeśli którykolwiek z nich trafi ją bezpośrednio.
- Kolejność serii — decyzja użytkownika: kolejność rozstrzygnięcia strzałów
  (czas symulacji ich zakończenia), nie kolejność oddania.

Kandydaci do późniejszego wyboru (niezatwierdzeni, poza początkowym zakresem;
kryterium bez dostatecznych danych w eventach pozostaje zablokowane):

- **Odwet** — po obrażeniach własnej fortecy bezpośrednie trafienie we wrogą
  fortecę w oknie czasu symulacji i zwycięstwo;
- **Ostatni strzał** — zwycięstwo zakończone bezpośrednim trafieniem fortecy.

Decyzja użytkownika: bonusy Pogromca i Wsparcie oddziałów (oparte na
zabójstwach jednostek) nie są wdrażane i nie wchodzą do zakresu.

Rozszerzenie o nowy rodzaj osiągnięcia wymaga nowego typu warunku, jego
obsługi w evaluatorze i testów, bez zmian w tracker lifecycle ani persystencji.

Bezpośrednie trafienie musi być odróżniane od obrażeń obszarowych, a trafienie
musi pochodzić z pocisku wystrzelonego przez katapultę gracza. „Szybkie
pokonanie” wymaga zatwierdzonego progu czasu; czas powinien pochodzić z czasu
symulacji, nie zegara ściennego. „Nienaruszona twierdza” oznacza brak
jakiegokolwiek zdarzenia obrażeń skierowanego do twierdzy gracza w tym meczu.

Seria trafień w twierdzę jest liczona tylko po własnych strzałach gracza.
Każdy kolejny strzał gracza, który nie trafi bezpośrednio we wrogą twierdzę,
zeruje serię — także trafienie wrogiej jednostki, trafienie obszarowe lub
uderzenie w teren. Strzały przeciwnika nie zmieniają serii gracza. Bonus za
trafienie z serii jest naliczany do końcowego rozliczenia meczu, nie
bezpośrednio do salda w trakcie symulacji. Dokładne kwoty, wzrost bonusu i
ewentualny limit serii pozostają do ustalenia.

## Kontrakty, wejścia i wyjścia

### Wejścia

- typowana definicja kampanijnego poziomu i jego zestaw nagród;
- identyfikator poziomu, meczu, generacji sesji i lokalnego gracza;
- uporządkowana sekwencja `MatchEvent` z tego meczu;
- `MatchEvent.MatchFinished` jako ostateczna bramka wypłaty;
- bieżący, zwalidowany `CampaignProgress` oraz profil progresji odczytywane
  przez aplikacyjne use case'y.

Demo nie jest kampanijnym poziomem i nie wchodzi do tego przepływu.
Symulacja pozostaje źródłem eventów; RewardSystem nie odczytuje ani nie
mutuje jej wewnętrznego stanu.

### Wyjścia

- niemutowalny wynik rozliczenia zawierający poziom, wynik meczu, przyznane
  identyfikatory osiągnięć, rozbicie kwot, łączną wypłatę i nowe saldo;
- jawny wynik braku nagrody dla wyniku innego niż `BLUE_WIN`;
- typowane odrzucenie lub błąd walidacji/persystencji;
- ukończenie poziomu i odblokowanie kolejnego poziomu przez istniejący
  CampaignSystem;
- gotowy model prezentacyjny dla popupu wyniku.

Wynik nagrody jest odczytem, a nie drugim właścicielem profilu. UI nie może
zmieniać salda ani samodzielnie sumować kwot.

## Runtime state i cykl życia

Tracker posiada tylko minimalne akumulatory potrzebne do skonfigurowanych
warunków osiągnięć, na przykład liczniki zdarzeń i zestaw użytych typów
amunicji. Nie kopiuje całego snapshotu meczu ani nie przechowuje obiektów
domenowych.

1. Dla nowej sesji kampanijnej i poziomu tworzony jest pusty tracker z
   identyfikatorem meczu i generacją.
2. Każdy event z tej sesji jest przekazywany do trackera w kolejności
   symulacji.
3. Pauza nie zmienia tracker state; ponieważ symulacja nie emituje w tym
   czasie eventów postępu, osiągnięcia nie są naliczane podczas pauzy.
4. Restart tworzy nową generację i zeruje stan trackera. Zdarzenia starej
   generacji są odrzucane.
5. Przy `BLUE_WIN` evaluator zamyka tracker, sprawdza końcowe warunki i
   składa wynik rozliczenia.
6. Przy `RED_WIN`, `DRAW` lub `SURRENDER` tracker zostaje zamknięty bez
   przyznania złota i bez ukończenia poziomu; poziom można ponowić.
7. Po udanym, atomowym zapisie poziom jest oznaczony jako ukończony. Wybór
   ukończonego poziomu zostaje odrzucony. Następny poziom jest odblokowany
   zgodnie z obecnymi regułami kampanii.
8. Po błędzie zapisu tracker/wynik pozostaje dostępny do ponowienia samego
   rozliczenia z popupu; nie należy wymagać rozegrania meczu ponownie ani
   pokazywać niezapisanego salda jako zatwierdzonego.

Tracker jest deterministyczny: te same definicje, generacja i uporządkowane
eventy dają ten sam zestaw osiągnięć oraz kwot. Nie używa czasu ściennego,
losowości ani seeda; czas symulacji może być warunkiem przyszłego osiągnięcia
tylko wtedy, gdy jest jawnie reprezentowany w eventach i replayu.

## Idempotencja i atomowa persystencja

Kampania zapisuje postęp, a profil zapisuje saldo. Rozdzielenie tych dwóch
zapisów bez wspólnej transakcji grozi oznaczeniem poziomu jako ukończonego bez
wypłaty albo wypłatą złota bez ukończenia poziomu.

Należy dodać w `game-application` jawny port rozliczenia, który w jednej
atomowej aktualizacji:

- sprawdza poprawność profilu i kampanii oraz czy poziom jest odblokowany;
- sprawdza, czy poziom nie ma już zatwierdzonego zwycięskiego rozliczenia;
- zwiększa istniejące `goldBalance`;
- zapisuje `completedLevels`, odblokowanie następnego poziomu oraz stosowne
  milestone IDs;
- zapisuje trwały receipt lub równoważny stabilny klucz rozliczenia wraz z
  zestawem osiągnięć/nagród, by zwrócić ten sam wynik przy ponowieniu;
- nie zmienia żadnego pola, jeśli walidacja lub zapis się nie powiedzie.

Adapter może wykorzystać fakt, że bieżące profile i kampania znajdują się w
tym samym AndroidX DataStore, ale ten szczegół nie przekracza granicy
`android-app`. Port w core opisuje transakcję semantycznie i zwraca jawne
wyniki.

Idempotency key powinien być oparty o identyfikator kampanijnego poziomu i
stabilny identyfikator rozliczenia, nie tylko o nazwę lokalnej sesji.
`EventId` służy dodatkowo do ignorowania ponownie dostarczonego eventu w
trackerze. Ponowne dostarczenie zakończenia lub retry persystencji nie może
zwiększyć salda drugi raz.

Katalog musi mieć unikalne, stabilne identyfikatory definicji nagród.
Osiągnięcie jednorazowe może wypłacić najwyżej raz, natomiast reguła
powtarzalna — jak premia za serię trafień — może wygenerować wiele pozycji
wypłaty. Każda pozycja musi mieć stabilny identyfikator wystąpienia, aby
ponowne dostarczenie eventu lub retry nie naliczyły jej drugi raz. Sumowanie
musi być jawnie walidowane pod kątem przepełnienia istniejącego typu salda.
Nie wolno po cichu zawijać, obcinać ani zastępować wartości.

## Integracja z istniejącymi systemami

```text
CampaignScreen
  → CampaignSystem: autoryzacja nieukończonego, odblokowanego poziomu
  → LoadoutSystem: odczyt i zatwierdzenie loadoutu
  → MatchScreen / LocalMatchGateway / MatchSession
      → MatchEvent stream
      → RewardSystem tracker (jedna generacja meczu)
      → MatchEvent.MatchFinished
          ├── wynik nie-BLUE_WIN → brak nagrody, poziom pozostaje dostępny
          └── BLUE_WIN → ocena osiągnięć i wynik nagrody
                → atomowe rozliczenie CampaignProgress + goldBalance
                → istniejący popup wyniku pokazuje rozbicie nagrody
```

`CampaignSystem` nadal posiada autoryzację poziomów, `highestUnlockedLevel`,
`completedLevels` i projekcję kampanijnych spell-milestones. Interfejs wyboru
poziomu musi dodatkowo odrzucać poziom już ukończony. Jego obecne sprawdzenie
`level <= highestUnlockedLevel` dopuszcza powtórki, więc polityka jednego
zwycięskiego podejścia wymaga jawnego wyniku `LEVEL_ALREADY_COMPLETED`.

`PlayerProgressionSystem` pozostaje jedynym właścicielem `goldBalance`.
RewardSystem nie tworzy drugiego salda ani nie przenosi ceny czarów z
`LoadoutCatalog`. Ewentualny system sklepu w przyszłości konsumuje to samo
saldo przez osobny use case transakcyjny.

Osiągnięcia nie są częścią `MatchSnapshot`, dopóki żaden ekran meczu nie
potrzebuje ich wyświetlać w trakcie rozgrywki. Wynik rozliczenia trafia do
popupu dopiero po poprawnym zapisie. Nie należy dodawać kontrolek do
`MainActivity` ani przenosić reguł do `MatchResultPopupStateMapper`.

## Błędy, odrzucenia i przypadki brzegowe

- Nieznany lub zamknięty poziom: jawne odrzucenie bez wypłaty.
- Poziom zablokowany: odrzucenie przed rozpoczęciem sesji i przed rozliczeniem.
- Poziom już ukończony: wybór odrzucony; ponowne zakończenie nie nalicza złota.
- Inny wynik niż `BLUE_WIN`: jawny `NoReward`, bez postępu kampanii.
- Event z innego `MatchId` lub generacji: jawne zignorowanie przez granicę
  sesji albo typowane odrzucenie; nie może zmienić trackera.
- Powtórzony `EventId`: nie zwiększa liczników drugi raz.
- Nieznane ID warunku lub nagrody w zapisanych danych: jawny błąd walidacji.
- Brak niezbędnego kontekstu drużyny/celu: konfiguracja osiągnięcia jest
  nieważna, nie wolno odgadywać go z tekstowego ID.
- Błąd DataStore, częściowy profil, nieobsługiwana wersja albo korupcja:
  jawny failure, nie zwraca się sukcesu ani salda domyślnego.
- Przepełnienie salda lub ujemna kwota: walidacja odrzuca całą transakcję.
- Błąd zapisu po zwycięstwie: UI oferuje retry persystencji i nie pozwala
  rozpocząć następnego poziomu, dopóki completion nie zostanie zapisane.
- Demo: nie tworzy kampanijnego trackera i nie przyznaje nagród.

## Android UI

Używany jest istniejący in-match result popup. Po zapisanej wygranej powinien
pokazać:

- złoto za zwycięstwo;
- osobne wiersze dla osiągnięć, które przyznały nagrodę;
- sumę przyznaną za poziom;
- saldo po rozliczeniu.

Przy wyniku bez nagrody nie pokazuje się fałszywego payoutu. Podczas zapisu
można pokazać istniejący stan zapisywania postępu. Po błędzie przycisk retry
ponawia transakcję, a nie mecz. Warstwa prezentacji konsumuje typowany
read model i nie wylicza kwot.

Nie są wymagane nowe assety ani osobny `ResultScreen`.

## Plan przyszłej implementacji

Szczegółowy, fazowy plan: [reward-system-implementation-plan.md](reward-system-implementation-plan.md).
Poniższa lista to przegląd.

0. Zatwierdzić i wdrożyć rozszerzenia kontraktu eventów z sekcji „Audyt
   kontraktu eventów” (drużyny, rozstrzygnięcie pocisku bez trafienia),
   z testami symulacji i replayu; bez tego osiągnięcia oparte na drużynie
   pozostają zablokowane.
1. Rozszerzyć typy progresji i port persystencji o jawne, atomowe
   rozliczenie kampanii wraz ze złotem i idempotentnym receipt.
2. Uogólnić katalog `CampaignMilestoneRegistry` do jednego rozszerzalnego
   katalogu nagród kampanii, zachowując obecne spell-milestones i ich stabilne
   ID.
3. Dodać typowane definicje warunków osiągnięć i evaluator w
   `game-application`; określić walidację kompletności danych eventów.
4. Dodać tracker per match/session generation oraz podłączyć uporządkowany
   strumień eventów z `MatchSession` bez przekazywania stanu domenowego do UI.
5. Dodać use case finalizacji, który tworzy receipt przy `BLUE_WIN` i
   przekazuje wynik do atomowego portu rozliczenia.
6. Zmienić selekcję poziomu, aby odrzucała ukończone poziomy, pozostawiając
   retry po porażce.
7. Rozszerzyć Android DataStore adapter i wiring zależności; zachować
   asynchroniczność oraz jawny retry zapisu.
8. Rozszerzyć istniejący result popup o rozbicie zatwierdzonej nagrody.
9. Zaktualizować dokumentację `CampaignSystem`, `PlayerProgressionSystem` i
   `knowledge.md` w ramach implementacji, gdy nowe zachowanie będzie
   rzeczywiście wdrożone.

Lista prawdopodobnych plików do zmiany przy implementacji:

- `game-campaign/.../CampaignMilestoneDefinition.kt` lub jego uogólniony
  odpowiednik;
- `game-application/.../application/campaign/CampaignSystem.kt`;
- `game-application/.../application/campaign/CampaignProgressRepository.kt`;
- `game-application/.../application/progression/PlayerProgressionRepository.kt`;
- `game-application/.../application/progression/PlayerProgressionUseCases.kt`;
- nowe pliki `game-application/.../application/reward/`;
- `android-app/.../persistence/DataStoreCampaignProgressRepository.kt` oraz
  `DataStorePlayerProgressionRepository.kt` albo jeden adapter transakcyjny;
- `android-app/.../ui/MatchViewModel.kt`,
  `screens/match/CampaignCompletionPresenter.kt` i result popup;
- testy jednostkowe katalogu, trackera, use case'ów i adaptera DataStore.

Dokładny podział repozytoriów należy potwierdzić przy implementacji przez
sprawdzenie współdzielenia instancji DataStore i atomowości update'ów.

## Plan testów i kryteria akceptacji

### Testy katalogu i warunków

- unikalność i stabilność ID, poprawność poziomu, nieujemna kwota oraz
  poprawność referencji kryteriów;
- znane typowane kryteria poprawnie mapują się na obsługiwane eventy;
- brak wymaganych danych kontekstowych powoduje jawny błąd walidacji;
- dotychczasowe spell-milestones i ich progi pozostają bez zmian.

### Testy trackera

- naliczanie każdego kryterium przy właściwym evencie i ignorowanie
  niepasujących eventów;
- powtórny `EventId` nie nalicza postępu drugi raz;
- obcy `MatchId`/generacja nie zmienia stanu;
- pauza nie zmienia stanu; restart resetuje go dla nowej generacji;
- pierwsze bezpośrednie trafienie twierdzy rozpoczyna serię bez premii,
  drugie i kolejne zwiększają jej poziom, a nietrafienie resetuje serię;
- trafienie w jednostkę, obrażenia obszarowe lub trafienie terenu resetują
  serię trafień w twierdzę; strzały przeciwnika jej nie zmieniają;
- pocisk, który wygasł lub opuścił mapę, rozstrzyga strzał jako chybiony i
  resetuje serię; fragmenty pocisku należą do tego samego strzału;
- trafienie w tarczę, bastion lub jednostkę nie jest trafieniem w fortecę
  (zgodnie z decyzją); pierce liczy parę (pocisk, cel) raz;
- drużyna strzelającego i celu pochodzi z pól eventów, a nie z parsowania ID;
- tracker zasilany w `MatchSession` nie traci eventów, gdy UI nie wywoła
  `consumeEvents` między komendami;
- każda powtarzalna premia ma unikalny identyfikator wystąpienia i nie może
  zostać wypłacona dwukrotnie;
- te same uporządkowane eventy dają ten sam wynik bez seeda i zegara
  ściennego;
- wynik nie-BLUE_WIN nie emituje nagród;
- pojedynczy achievement ID przyznaje najwyżej jedną nagrodę.

### Testy rozliczenia i integracji

- zwycięstwo przyznaje nagrodę bazową oraz tylko spełnione osiągnięcia;
- pierwszy BLUE_WIN atomowo zapisuje złoto, ukończenie, odblokowanie i
  milestone'y;
- powtórzenie eventu lub retry zwraca ten sam receipt i nie zmienia salda
  drugi raz;
- przegrana, remis i poddanie pozwalają ponowić poziom i nie zmieniają
  salda ani CampaignProgress;
- ukończony poziom zostaje odrzucony przez `selectLevel`;
- błąd magazynu, błędne dane i przepełnienie salda nie zapisują częściowego
  completion ani payoutu;
- migracja istniejącego profilu zachowuje saldo i nie wypłaca retroaktywnie
  nagród bez zatwierdzonego receipt;
- popup pokazuje tylko zatwierdzone dane i retry nie restartuje symulacji;
- brak Androida w definicjach/evaluatorze oraz brak logiki nagród w UI;
- brak równoległego salda, katalogu nagród lub właściciela ukończenia.

### Kryteria akceptacji

- tylko BLUE_WIN może przyznać złoto;
- porażka pozwala na kolejne podejście, a zapisane zwycięstwo kończy możliwość
  wyboru tego poziomu;
- eventy meczu są jedynym źródłem postępu specjalnych osiągnięć;
- jedna atomowa operacja zapisuje saldo, ukończenie, odblokowanie i receipt;
- duplicate delivery oraz retry nigdy nie dublują wypłaty;
- definicje nagród i warunków są rozszerzalne, typowane i testowalne bez
  Androida;
- kampania pozostaje właścicielem progresji poziomów, a profil właścicielem
  złota;
- UI wyłącznie prezentuje wynik use case'u.

## Otwarte decyzje

- Zatwierdzenie rozszerzeń kontraktu eventów z audytu (drużyny w eventach,
  event rozstrzygnięcia pocisku bez trafienia).

- Kwoty za zwycięstwo i poszczególne osiągnięcia oraz ewentualne limity.
- Konkretna lista osiągnięć, ich progi i czy w przyszłości mogą istnieć
  osiągnięcia bez nagrody złotem.
- Źródło authoringu: per-level entries w kodzie `game-campaign` czy osobny
  serializowany format; bieżący zakres zakłada registry Kotlin, zgodny z
  obecnymi definicjami kampanii.
- Polityka dla istniejących zapisów: ukończone poziomy nie mają jeszcze
  reward receipt. Rekomendacja to nie przyznawać im automatycznie nagród
  wstecz; należy zatwierdzić migrację przed wdrożeniem.
- Typ i granica maksymalnego salda: obecne `goldBalance` jest typu `Int`;
  przed wdrożeniem trzeba potwierdzić limit lub bezpieczną migrację do typu
  o większym zakresie.
- Czy przyszłe nagrody pozłotowe (np. odblokowania, stock) mają używać
  tego samego katalogu i receipt, czy oddzielnych use case'ów.
- Czy osiągnięcia są widoczne w trakcie meczu, czy tylko w podsumowaniu.
