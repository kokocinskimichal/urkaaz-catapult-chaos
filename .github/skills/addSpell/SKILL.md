---
name: addSpell
description: Add a Urkaaaz spell through the shared domain catalog, deterministic simulation, contracts, campaign and Android adapters without taking ownership of the shared loadout system.
argument-hint: "[optional spell name]"
---

# /addSpell — dodawanie nowego czaru

Używaj tego skilla, gdy użytkownik wywoła `/addSpell` albo poprosi o dodanie
nowego czaru do Urkaaaz.

## Granica odpowiedzialności

- SpellSystem odpowiada za definicję, balans, targetowanie, aktywację, runtime
  efektu, eventy i snapshoty czaru.
- `LoadoutSystem` w `game-application` jest właścicielem wspólnego loadoutu
  jednostek i czarów. Nie implementuj selekcji, walidatora, persystencji ani
  osobnego spell-only modelu w ramach dodawania czaru.
- Nowy czar integruje się z niemutowalnym, zatwierdzonym wynikiem LoadoutSystemu.
  Obecnie dostępne są `PlayerLoadoutConfig`, lokalny profil i per-level zapis
  DataStore oraz bezpieczne zatwierdzanie. Ekran nadal nie ma interaktywnych
  edytorów, a zakupy, milestone'y kampanii i rozliczanie stocku są poza
  implementacją. Nie obchodź tych granic przez fikcyjny adapter ani
  tymczasowy spell-only loadout.
- Obowiązującą propozycję architektury znajdziesz w
  `docs/systems/spell-system.md`. Gdy repozytorium uległo zmianie, przeczytaj
  aktualny kod i uzgodnij rozbieżności zamiast zakładać, że dokument został
  już zaimplementowany.

## Kontrakt rozmowy

Zadawaj pytania pojedynczo. Nie wymyślaj mechaniki, balansu, limitów,
targetowania, reguł stackowania ani assetów na podstawie samej nazwy czaru.
Jeżeli użytkownik nie ma wartości, przeanalizuj najbliższe istniejące czary,
zaproponuj konkretną propozycję i uzyskaj akceptację przed jej implementacją.
Informacje już podane przez użytkownika są źródłem prawdy.

Ustal kolejno:

1. Nazwę czaru, stabilny identyfikator i krótką fantazję/rolę.
2. Istniejącą kategorię roli (`OFFENSIVE`, `DEFENSIVE`,
   `BATTLEFIELD_CONTROL`, `SUPPORT`, `SIEGE`) lub zaakceptowaną nową kategorię.
3. Kategorię celu niezależnie od roli: własna armia, pojedyncza własna
   jednostka, wrogie jednostki, wroga machina oblężnicza, własna forteca,
   własna brama albo jawnie zatwierdzony nowy target.
4. Stronę i wybór targetu: automatyczny wybór, wskazany `EntityId`, cała
   drużyna, wejście w obszar, pozycja stała lub obiekt fortyfikacji. Określ
   także, kogo efekt nie może objąć.
5. Dokładne zachowanie: efekt, wartości balansu, koszt many, koszt złota,
   czas działania, interwał ticka, cooldown, warunki aktywacji i warunek
   zakończenia.
6. Odnowienie i konflikty: czy ponowna aktywacja odświeża, zastępuje, odrzuca
   czy zużywa kolejny stock; jak czar zachowuje się obok efektów tego samego
   i innych typów; kolejność względem ruchu, walki, obrażeń, amunicji i
   fortyfikacji.
7. Odblokowanie i ekonomię: poziom kampanii, trwałe odblokowanie, cena złota,
   stock specjalny oraz ewentualna zasada stocku runtime. Nie przenoś tych
   reguł do SpellSystemu, jeśli należą do progresji aplikacyjnej.
8. Zachowanie AI, jeżeli ma czar używać; AI emituje zwykłe komendy na podstawie
   snapshotu i nie implementuje efektu osobno.
9. Wymagania Androida: nazwa i opis lokalizowany, ikona, wizualizacja świata,
   timer/status HUD-u oraz brakujące assety. Najpierw sprawdź istniejące
   katalogi i assety.
10. Kryteria akceptacji i testy/regresje wymagane przez użytkownika.

Nie przerywaj pytań już rozstrzygniętych w bieżącej rozmowie. Jeżeli
nieuzgodnione zachowanie wpływa na efekt gameplayowy, zatrzymaj się i uzyskaj
decyzję zamiast wybierać wygodniejszą implementację.

## Analiza przed zmianami

Przeczytaj:

- `AGENTS.md`, `architecture.md`, `AI_IMPLEMENTATION_RULES.md`,
  `docs/PROJECT_KNOWLEDGE.md`;
- `docs/systems/spell-system.md`;
- `game-domain/src/main/kotlin/com/urkaaaz/domain/Spells.kt` i
  `AmmunitionDefinitions.kt`;
- `game-contracts` command/event/snapshot/scenario kontrakty;
- `game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt`
  oraz testy symulacji/replay;
- `game-application/.../MatchSession.kt` i testy lifecycle;
- `game-campaign/.../CampaignLevelDefinition.kt` i testy;
- bieżący właściciel loadoutu, jeżeli już został dodany;
- odpowiednie MatchViewModel, screen-owned kontrolery/renderery oraz asset
  catalog w `android-app`;
- istniejące testy domeny, kontraktów, symulacji, aplikacji i Androida.

Sprawdź podobne efekty, modyfikatory, unit statusy, cooldowny, odrzucenia,
eventy i mechanizmy obrażeń przed dodaniem nowej abstrakcji. Nie przeszukuj ani
nie modyfikuj osobnego checkoutu legacy bez potrzeby; gdy migracja legacy jest
częścią zadania, użyj go tylko do odczytu i traktuj bieżący kod legacy jako
referencję do parytetu.

## Zasady implementacji

1. Dodaj stabilny typ czaru do istniejącego kontraktu domenowego. Nie zmieniaj
   istniejących serializowanych identyfikatorów.
2. Umieść wszystkie koszty i parametry balansu w jednym katalogu domenowym;
   nie duplikuj ich w Androidzie, kampanii ani AI.
3. Dodaj metadane kategorii celu niezależne od istniejącej kategorii roli.
   Targety pochodzą z reguł domenowych, nie z warunków w UI.
4. Zachowaj pojedynczego właściciela runtime state w symulacji. Nowa reguła
   komunikuje się przez istniejące granice komend/eventów/snapshotów i nie
   przechowuje drugiej kopii many, zdrowia ani statusów.
5. Aktywacja jest atomowa: zweryfikuj stan meczu, dostępność, zatwierdzony
   loadout, manę, stock i target przed pobraniem zasobów lub zastosowaniem
   efektu. Odrzucona komenda zwraca czytelną przyczynę i nie pozostawia skutków
   ubocznych.
6. Używaj czasu symulacji, stałego właściciela seeda i ustalonej kolejności
   faz. Nie używaj zegara ściennego ani losowych fallbacków.
7. Dodaj dedykowaną regułę lub istniejący współdzielony mechanizm. Nie
   rozbudowuj centralnego God Objectu o kolejne bloki `when` i nie twórz
   drugiego silnika czarów.
8. AI czyta snapshot i zwraca `MatchCommand.CastSpell`; aplikacja przekazuje
   komendę; Android tylko mapuje stan i wynik na prezentację.
9. Dostępność poziomu pochodzi z kampanii, trwałe odblokowanie/złoto/stock z
   progresji, a wybór loadoutu z LoadoutSystemu. Nie mieszaj tych trzech
   odpowiedzialności.
10. Użyj niemutowalnego snapshotu dla HUD-u i renderingu. Światowe efekty
    korzystają z transformacji kamery; HUD/timery pozostają screen-space.

## Testy

Dodaj testy adekwatne do czaru:

- poprawność katalogu, stabilne ID, kosztów i target category;
- poprawna aktywacja, dokładne skutki i odrzucenia;
- brak pobrania many/stocku oraz brak mutacji przy błędnej komendzie;
- strony/twarde granice targetu, w tym friendly-fire;
- czas trwania, ticki, wygaśnięcie, odnowienie i stacking;
- interakcje z ruchem, walką, obrażeniami, pociskami, fortecami i innymi
  statusami;
- snapshot/event i brak mutacji przez UI;
- lifecycle start/pauza/restart/koniec meczu;
- deterministyczność i replay regression przy powtarzalnych komendach;
- dostępność kampanii, zatwierdzony loadout i stock progresji jako osobne
  wejścia;
- brak zależności Android w core oraz brak drugiego katalogu balansu.

Nie uznawaj czaru za ukończony, jeżeli cel, aktywacja, odrzucenia, stan
runtime, snapshot, UI i wymagane testy nie są połączone.

## Walidacja i finalizacja

Po zmianie uruchom najmniejszy zestaw testów obejmujący zmienione moduły,
następnie obowiązkową bramkę projektu przed commitem:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Zaktualizuj bezpośrednio powiązaną dokumentację. Nie twórz commita ani nie
pushuj bez wyraźnego polecenia. W podsumowaniu podaj efekt, dotknięte moduły,
testy, ewentualne brakujące assety/decyzje i wynik walidacji.
