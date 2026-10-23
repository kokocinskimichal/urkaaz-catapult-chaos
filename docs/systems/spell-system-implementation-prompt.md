# Prompt pełnej implementacji SpellSystem

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

---

## Prompt

Zaimplementuj kompletnie i end-to-end rozszerzalny **SpellSystem** oraz
przenieś do niego wszystkie zaklęcia legacy, zgodnie z:

- `docs/systems/spell-system.md` — zaakceptowana architektura, kategorie celu,
  źródłowy balans i etapowy plan migracji;
- `docs/architecture/architecture.md`, `AGENTS.md`, `docs/architecture/implementation-rules.md`,
  `docs/architecture/project-map.md` i `docs/project/knowledge.md` — granice modułów i
  obowiązujący workflow;
- `.github/skills/addSpell/SKILL.md` — standard przyszłego dodawania czarów;
- aktualnym kodem oraz jego testami, nie samymi opisami planowanej
  architektury.

Nie zatrzymuj się na analizie ani planie. Wykonaj implementację, integrację,
testy i aktualizację dokumentacji w tej sesji, o ile nie trafisz na jawny
blocker wymagający decyzji użytkownika.

### Decyzje użytkownika — źródło prawdy

- Zachowaj legacy’owe efekty i balans; nie proponuj ani nie wprowadzaj zmian
  liczbowych bez osobnej zgody.
- **Haste przyspiesza wyłącznie sojusznicze jednostki.** Starszy opis UI
  mówiący o wszystkich jednostkach jest nieaktualny i ma zostać skorygowany.
- Kategorie targetu są niezależne od kategorii roli (`OFFENSIVE`,
  `DEFENSIVE`, `BATTLEFIELD_CONTROL`, `SUPPORT`, `SIEGE`).
- SpellSystem nie wybiera, nie waliduje i nie przechowuje loadoutu.
  Wspólnym właścicielem loadoutu jednostek i czarów jest `LoadoutSystem`
  w `game-application`; SpellSystem konsumuje niemutowalny, zatwierdzony
  wynik kontraktowy.
- Nie implementuj tymczasowego spell-only loadoutu i nie traktuj
  `CampaignLevelDefinition.availableSpells` jako wyboru gracza.
- Nie twórz nowego modułu Gradle ani drugiego katalogu balansu, statusów,
  efektów, eventów, UI lub progresji.
- Nie commituj, nie pushuj ani nie zmieniaj gałęzi.

### Bramka zależności LoadoutSystem

Na początku sprawdź aktualny stan `LoadoutSystem`. Repozytorium ma już
częściową implementację `MatchLoadout` oraz `PlayerLoadoutConfig` w
`MatchScenario`; użyj ich zamiast tworzyć alternatywny wybór.

- Zintegruj się z istniejącym kontraktem; nie kopiuj wyboru ani walidacji.
- Produkcyjny lokalny profil, per-level DataStore i bezpieczne zatwierdzenie
  loadoutu już istnieją. Edytory loadoutu, zakupy, milestone'y kampanii oraz
  rozliczanie stocku pozostają niedokończone. Nie twórz adaptera, który udaje
  dostępność, odblokowania lub stock.
- Jeżeli kontrakt zostanie usunięty lub bez brakującej integracji nie można
  poprawnie walidować
  `CastSpell`/ukończyć match screen, zatrzymaj się i zapytaj użytkownika
  jednym, konkretnym pytaniem, czy ma rozszerzyć zakres o implementację
  wspólnego `LoadoutSystem` jako oddzielnego podsystemu. Nie implementuj go
  ponownie ani nie symuluj go osobnym spell-only modelem.
- Możesz kontynuować niezależne, kompletne części tylko wtedy, gdy nie
  tworzą tymczasowego drugiego właściciela. Jasno oznacz blocker i nie
  deklaruj całego zadania jako ukończonego, dopóki granica loadoutu nie jest
  podłączona.

### Workflow i ochrona pracy użytkownika

1. Odczytaj wymagane dokumenty oraz `git status --short` i bieżącą gałąź.
   Working tree może zawierać równoległe, niepowiązane zmiany. Zachowaj je
   bez zmian; nie cofaj, nie nadpisuj, nie stashuj i nie formatuj plików
   niezwiązanych z tym zadaniem.
2. Zbadaj obecne implementacje i testy. Oddziel istniejące działające
   elementy od kontraktów/projektów, które są na razie tylko szkieletem.
3. Wypisz etapy i rzeczywiste zależności. Działaj bezpośrednio, nie deleguj
   samodzielnych, małych etapów.
4. Nie pytaj ponownie o decyzje zapisane powyżej. Pytaj pojedynczo tylko o
   niezdefiniowaną decyzję, która zmienia zachowanie, balans, zapis progresji,
   interakcje albo zakres.
5. Jeśli dokument architektury i kod legacy różnią się, zachowaj zatwierdzone
   zachowanie użytkownika i porównaj je z kodem źródłowym legacy. Nie kopiuj
   implementacji legacy plik-do-pliku.
6. Przed zakończeniem wykonaj pełną bramkę walidacji projektu. Zatrzymaj się
   przy błędach, napraw je i uruchom odpowiednie testy ponownie.

### Etap 1 — model domenowy i katalog

Zachowaj obecne stabilne identyfikatory:

`ICE_TRAP`, `RUNIC_BASTION`, `HASTE`, `HEAL`, `FORTRESS_SHIELD`,
`CATAPULT_LOCK`, `SLOWING_FIELD`, `VAMPIRIC_BANNER`, `STONE_SKIN`,
`SIEGE_FRENZY`, `BERSERKER`.

Rozszerz istniejący model domenowy zamiast tworzyć równoległy. Utwórz lub
uzupełnij jeden `SpellCatalog` (nazwa i lokalizacja zgodne z istniejącym
kodem), który jest single source of truth dla:

- stabilnego ID i kategorii roli;
- niezależnej kategorii celu;
- ceny złota i kosztu many;
- zasad odblokowania i flagi/zasad stocku specjalnego;
- czasu działania, parametrów efektu i reguł aktywacji.

Nie przechowuj tych samych wartości jednocześnie w enumie i katalogu.
Waliduj kompletność i unikalność definicji, poprawność kosztów i parametrów
oraz jednoznaczne mapowanie każdego ID na regułę. Nieznany typ czaru jest
jawnym błędem/odrzuceniem, nigdy cichym fallbackiem.

Wprowadź ortogonalne kategorie celu:

| Kategoria | Zaklęcia |
|---|---|
| `ALLIED_FORCE` | Haste, Heal, Vampiric Banner, Stone Skin, Siege Frenzy |
| `ALLIED_UNIT` | Berserker |
| `ENEMY_FORCE` | Ice Trap, Slowing Field |
| `ENEMY_SIEGE_ENGINE` | Catapult Lock |
| `OWN_FORTRESS` | Fortress Shield |
| `OWN_GATE` | Runic Bastion |

Target metadata musi również określać sposób wskazania celu. Lodowa pułapka
jest rozstawiana w centrum pola, ale zatrzymuje jednostki strony przeciwnej.
Nie wymagaj dotykowego wyboru celu dla czarów, które w legacy wybierają cel
automatycznie albo działają na całą armię/obiekt.

### Etap 2 — kontrakty, komendy i atomowe odrzucenia

Rozszerz istniejące `MatchCommand.CastSpell`, `MatchEvent.SpellCast`/
eventy gameplayowe, wyniki komend i snapshoty w sposób zgodny z
`docs/architecture/architecture.md`.

- `game-contracts` nie może zależeć od `game-domain` ani Androida.
- Nie zmieniaj serializowanych nazw istniejących ID.
- Event udanej aktywacji powinien mieć wystarczające dane do replay/debug/UI:
  match, czas symulacji, gracz/drużyna, spell ID i target, jeżeli istnieje.
- Snapshot musi bezpiecznie ujawniać aktywne statusy, timery i obiekty świata
  potrzebne rendererowi, w tym autora/drużynę i pozostały czas.
- Najpierw spróbuj rozszerzyć istniejące `UnitSnapshot.statuses`,
  `statusDetails`, `EffectSnapshot` i `MatchSnapshot.effects`. Dodaj typowane
  dane tylko wtedy, gdy istniejący kontrakt nie może jednoznacznie wyrazić
  efektu; nie twórz równoległego ogólnego systemu efektów.
- Odrzuć błędną komendę jawnie: typowany powód dla nieznanego/niedostępnego/
  niewyposażonego czaru, braku many/stocku, nieprawidłowego celu lub
  niedozwolonego stanu meczu.
- Walidacja musi być atomowa. Odrzucenie nie pobiera many/stocku i nie
  pozostawia częściowego efektu.

### Etap 3 — deterministyczny runtime wszystkich zaklęć

Zaimplementuj efekty w istniejącym właścicielu meczu w
`game-simulation`, najlepiej jako wyspecjalizowany SpellSystem/SpellSimulation
koordynowany przez `DeterministicMatchSimulation`. Nie umieszczaj zasad w
Androidzie, `MatchSession` ani jednym rosnącym `when`.

Używaj wyłącznie czasu symulacji i aktualnej sekwencji faz. Aktywacja następuje
atomowo przy komendzie; czasowe efekty aktualizują się deterministycznie
przed fazami ruchu/walki/pocisków, które z nich korzystają. Zdefiniuj
testowalnie granicę wygaśnięcia. Pauza nie przesuwa timerów; restart tworzy
świeży stan; stop/koniec meczu blokuje dalsze aktywacje i aktualizacje.
Zachowaj replay przy tym samym scenario, komendach, deltach symulacji i seedzie.

Przenieś baseline z `docs/systems/spell-system.md` dokładnie:

- **Lodowa pułapka:** 500 złota, 50 many; środek pola, promień 180, 6 s;
  zatrzymuje wrogie jednostki; nowa pułapka drużyny zastępuje jej poprzednią.
- **Runiczny Bastion:** 1 200 złota, 70 many, 360 HP; własna brama; blokuje
  marsz i można go zniszczyć; ponowna aktywacja odnawia HP.
- **Haste:** 900 złota, 50 many, 5 s; ×2 prędkości tylko własnych jednostek.
- **Heal:** 900 złota, 50 many; żywe, ranne własne jednostki leczy o 35%
  maksymalnego HP, również w garnizonie.
- **Tarcza fortecy:** 0 złota, 80 many, 80 HP; osłania własną fortecę;
  nieaktywna do ponownego użycia, dopóki obecna osłona działa.
- **Blokada katapulty:** 1 000 złota, 60 many, 5 s; blokuje wrogą katapultę.
- **Pole spowolnienia:** 1 000 złota, 50 many, 7 s; spowalnia wrogie
  jednostki do 50% prędkości.
- **Wampiryczny sztandar:** 1 100 złota, 60 many, 8 s; co 1 s leczy własne
  ranne jednostki o 2% maksymalnego HP; aktywacja resetuje licznik ticka.
- **Kamienna skóra:** 1 100 złota, 60 many, 6 s; prędkość ×0,75,
  otrzymywane obrażenia ×0,5.
- **Szał oblężniczy:** 1 200 złota, 60 many, 5 s; obrażenia własnych
  jednostek ×1,75.
- **Berserker:** 2 000 złota, 100 many; przemienia kwalifikującego się
  Obrońcę najbliższego wrogiej fortecy, ustawia HP na nowy maksymalny poziom
  ×2 i mnoży obrażenia ×2; działa do śmierci, tej jednostki nie można
  przemienić ponownie.

Wspólna pula many z legacy: maksimum 100, start 100, regeneracja 10 co 8 s.
Zachowaj też szczegóły legacy widoczne w kodzie źródłowym: odnawianie timerów
czasowych czarów, reset ticka Vampiric Banner, wymianę własnej pułapki,
odnowienie HP Bastionu i odrzucenie ponownego użycia aktywnej tarczy.
Wartości czasów, kosztów i obrażeń nie mogą zależeć od Android resource
values.

### Etap 4 — dostępność, loadout i progresja

Nie mieszaj trzech niezależnych pojęć:

1. `CampaignLevelDefinition.availableSpells` — czary dopuszczone przez poziom;
2. trwałe odblokowanie/stock i koszt złota — progresja aplikacji;
3. wybrany loadout — zatwierdzony wynik istniejącego LoadoutSystemu.

Zachowaj legacy’owe zasady progresji:

- domyślnie odblokowana jest Tarcza fortecy;
- czary specjalne to Catapult Lock, Slowing Field, Vampiric Banner, Stone
  Skin, Siege Frenzy i Berserker;
- odblokowanie czaru specjalnego przyznaje stock, aktywacja zużywa stock;
- inne czary odblokowują się trwale;
- legacy’owy limit trzech czarów i zapis wyboru migrują wyłącznie do
  LoadoutSystemu.

Zachowaj aktualną dostępność kampanii: Fortress Shield od początku, Haste od
poziomu 6, Heal od 8, Ice Trap od 11, Runic Bastion od 21. Zidentyfikuj
osobno reguły odblokowania w progresji. Nie utożsamiaj odblokowania z
dostępnością na poziomie ani z loadoutem.

Trwałe złoto, odblokowania i stock pozostają w warstwie aplikacyjnej.
Runtime otrzymuje jedynie zatwierdzoną konfigurację meczu i runtime stock.
Zmiany stocku raportuj jawnym wynikiem/eventem. Nie przenoś
`SharedPreferences` do core. Zachowaj istniejące dane lub przygotuj jawną,
testowaną migrację schematu, jeżeli istniejący zapis Urkaaaz już je zawiera.
Nie zakładaj automatycznego importu save’ów między oddzielnymi aplikacjami.

### Etap 5 — campaign, Android i zasoby

- `game-campaign` dostarcza dostępność przez istniejące kontrakty; nie
  implementuje many, aktywacji ani trwałej progresji.
- `game-application.MatchSession` i gateway muszą poprawnie przekazywać
  `CastSpell`, zwracać wynik/eventy/snapshot i resetować stan na restart.
- `android-app` wysyła komendy przez screen-owned akcje/ViewModel; nie mutuje
  domeny ani symulacji.
- Ekran loadoutu, zarządzany przez LoadoutSystem, pokazuje czary pogrupowane
  kategoriami celu. Nie dodawaj drugiego selection UI.
- Match screen prezentuje koszty, stan dostępności, mana/stock, odrzucenia,
  aktywny czas i efekty świata z niemutowalnego snapshotu.
- Sprawdź istniejące ikony, lokalizacje i asset catalog w Urkaaaz oraz
  odpowiadające im zasoby legacy. Użyj istniejących zasobów, jeśli pasują.
  Brakujące assety zgłoś; nie wstawiaj placeholdera ani nie generuj nowych
  grafik bez osobnej zgody.
- Popraw opisy Haste tak, by wszystkie locale jednoznacznie wskazywały
  sojusznicze jednostki.
- Zachowaj world-space i wspólną transformację kamery dla efektów pola;
  kontrolki HUD pozostają screen-space.

AI nie dostaje osobnej implementacji czarów. Nie dodawaj strategicznego
wyboru czarów przez AI, jeżeli nie istnieje i nie jest wymagany do
kompatybilności; jeśli AI później aktywuje zaklęcia, musi użyć wspólnych
komend i snapshotów.

### Etap 6 — testy

Dodaj lub rozszerz focused testy, co najmniej:

- kompletność katalogu, stabilność ID, wszystkie target categories,
  niezmienione ceny/koszty i parametry;
- aktywacja każdego z 11 czarów, wszystkie warunki odrzucenia i atomowy brak
  skutków ubocznych;
- Haste wyłącznie dla własnych jednostek oraz właściwa strona każdego
  enemy-target;
- wybór Berserkera, warunki pułapki, blokady katapulty, tarczy i Bastionu;
- koszty many, regenerację, stock special, gold/unlock i ich odseparowanie
  od wyboru/loadoutu;
- czas, ticki, odnowienie, wygaśnięcie, pauzę/restart i granice ticka;
- konflikty różnych efektów ruchu/obrażeń oraz interakcje z systemami walki,
  ruchu, pocisków, jednostek i fortec;
- campaign availability i odmowę czaru spoza zatwierdzonego loadoutu;
- eventy, odrzucenia, snapshoty/render mapping i brak gameplayowej mutacji
  przez Android;
- replay determinism i reset runtime między meczami;
- brak importów Android w core, brak duplikacji balansu i loadoutu.

Testy mają sprawdzać konkretne wartości i wyniki, nie tylko obecność klas lub
wywołanie metody. Nie zmniejszaj zakresu testów, żeby zamaskować brakujący
efekt.

### Etap 7 — dokumentacja i gotowość

Zaktualizuj:

- `docs/systems/spell-system.md`: oddziel plan od tego, co faktycznie
  zaimplementowano; usuń rozwiązane decyzje z otwartych i opisz rzeczywisty
  kontrakt/lifecycle;
- `.github/skills/addSpell/SKILL.md`: dopasuj ścieżki i workflow do
  faktycznie dostarczonej architektury, nadal respektując oddzielny
  LoadoutSystem;
- `docs/project/knowledge.md` i `docs/reference/GLOSSARY.md`, jeśli pojawiły się trwałe
  fakty lub nowe pojęcia.

Nie deklaruj ukończenia, jeśli którakolwiek z tych rzeczy nie działa:

- wszystkie 11 czarów jest osiągalnych przez komendę, symulację i snapshot;
- walidacja korzysta z rzeczywistego wyniku LoadoutSystemu;
- wszystkie target categories są dostępne z katalogu i widoczne w UI;
- legacy balans, odblokowania, ekonomia i stock są zachowane;
- wszystkie odrzucenia są jawne i bez częściowego zużycia;
- testy deterministyczności, parytetu, lifecycle i warstw przechodzą;
- Android UI nie zawiera gameplayowych reguł.

### Pełna walidacja

Uruchom z katalogu głównego repozytorium:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Napraw błędy wprowadzane przez tę zmianę i powtórz pełną bramkę. Nie
przypisuj temu zadaniu awarii pochodzących z niezależnych, wcześniejszych
zmian bez sprawdzenia. Zachowaj cudze modyfikacje i opisz dokładnie każdy
blokujący konflikt.

Nie commituj ani nie pushuj. W podsumowaniu wypisz:

- zrealizowane etapy i działające zaklęcia;
- zmienione pliki;
- wynik testów, builda i `git diff --check`;
- ewentualne nierozwiązane decyzje lub blokery, w tym zależność od
  LoadoutSystemu;
- potwierdzenie gałęzi oraz tego, że nie utworzono commita ani pushu.

---
