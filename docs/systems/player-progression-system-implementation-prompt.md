# Prompt implementacji PlayerProgressionSystem

Użyj poniższego promptu jako samodzielnego zadania implementacyjnego w
repozytorium Urkaaaz.

---

## Prompt

Zaimplementuj w pełni uzgodniony, lokalny zakres **PlayerProgressionSystem**
opisany w `docs/systems/player-progression-system.md`. Główny rezultat to
produkcyjny, trwały snapshot progresji używany przez LoadoutSystem oraz
bezpieczne przejście z ekranu loadoutu do meczu po zwalidowaniu i zapisaniu
wyboru.

Przed zmianami przeczytaj:

- `AGENTS.md`;
- `architecture.md`;
- `AI_IMPLEMENTATION_RULES.md`;
- `AI_PROJECT_MAP.md`;
- `docs/PROJECT_KNOWLEDGE.md`;
- `docs/systems/player-progression-system.md`;
- `docs/systems/loadout-system.md`;
- `docs/systems/loadout-system-implementation-prompt.md`;
- `docs/systems/campaign-system.md`;
- istniejący kod i testy portów, use case'ów i ekranu loadoutu.

Dokumenty opisują decyzje, nie stan implementacji. Sprawdź aktualny kod,
zależności Gradle, gałąź i `git status --short`. Working tree może zawierać
prace użytkownika: zachowaj je, nie formatuj ich hurtowo i nie cofaj
niepowiązanych zmian.

### Zatwierdzone decyzje — nie pytaj ponownie

- Nazwa: `PlayerProgressionSystem`.
- Właściciel przypadków użycia, snapshotu progresji i portu repozytorium:
  `game-application`; adapter persystencji i screen wiring: `android-app`.
- Nie twórz modułu Gradle ani równoległego katalogu profilu, loadoutu,
  amunicji, czarów, kampanii lub balansu.
- `CampaignLevelDefinition` pozostaje źródłem dostępności na poziomie.
  `CampaignSystem` pozostaje właścicielem postępu i odblokowania poziomów.
  `LoadoutSystem` pozostaje właścicielem wyboru oraz zapisu zatwierdzonego
  loadoutu.
- Lokalny zapis używa AndroidX DataStore. Porty i use case'y I/O są
  asynchroniczne (`suspend` lub istniejący równoważny wzorzec); nie używaj
  `runBlocking`, blokującego I/O ani synchronicznego cache'u jako pozornego
  źródła prawdy.
- Nowy profil odtwarza tylko potwierdzone wartości startowe legacy. Możesz
  odczytać checkout legacy wyłącznie read-only; nie modyfikuj go. Jeśli
  początkowego unlocku, złota lub stocku nie da się jednoznacznie ustalić,
  zatrzymaj tę część i zadaj użytkownikowi jedno konkretne pytanie. Nie
  wymyślaj ilości, nieograniczonych zapasów ani pełnych odblokowań.
- Nie importuj legacy save/preferences ani ich kluczy. Migracje dotyczą
  wyłącznie wersji schematu nowej aplikacji.
- `CampaignLevelDefinition.startingAmmunition` to stan początkowy meczu, nie
  trwały zapas profilu.
- Zakupy, nagrody, sklep, postęp poziomów, zużycie/rozliczenie stocku po
  `MatchFinished`, cloud sync, wiele profili oraz `CastSpell` i efekty
  czarów są poza zakresem.
- Jeśli istniejący port wymaga zakupu czaru, zwróć jawny `Blocked`, nie
  udawaj zakupu i nie zmieniaj salda ani odblokowań.
- Zachowaj zatwierdzoną regułę legacy: Haste jest dostępny przed loadoutem
  poziomu 6, Heal przed poziomu 8. Nie przyznawaj ich w UI i nie omijaj
  autoryzacji CampaignSystem. Jeśli bez CampaignSystemu nie da się
  bezpiecznie wykonać konkretnego milestone'u, pozostaw go jawnie zablokowany
  i zgłoś ograniczenie.
- Nie commituj, nie pushuj i nie zmieniaj gałęzi.

### Zasady implementacji

1. Zacznij od audytu aktualnych kontraktów. Wykorzystaj istniejące
   `LoadoutProgressionSnapshot`, `LoadoutProgressionPort`,
   `LoadoutRepository`, `LoadoutUseCases`, `MatchLoadout` i
   `PlayerLoadoutConfig`. Rozszerz je lub zmień ich asynchroniczność
   atomowo; nie dodawaj drugiego snapshotu o tych samych polach ani drugiej
   walidacji w Androidzie.
2. Zachowaj kierunek zależności: core nie zna DataStore, Androida, `Context`,
   `Flow` platformowego ani serializatora Androida. Adapter DataStore
   implementuje port aplikacyjny.
3. Dane profilu, zapis wyboru loadoutu, dostępność poziomu i runtime meczu
   są odrębnymi odpowiedzialnościami, nawet gdy adaptery używają jednego
   backendu DataStore.
4. Zaimplementuj jawne wyniki błędów. Nie używaj szerokich `catch`, cichych
   fallbacków, domyślnego pełnego dostępu ani sukcesopodobnych rezultatów.
5. `LoadoutScreen` może uruchomić mecz tylko po otrzymaniu prawidłowego,
   świeżo zwalidowanego `PlayerLoadoutConfig` i udanym zapisie loadoutu.
   Błąd I/O, uszkodzony profil, niedostępny/nieodblokowany element lub brak
   stocku ma blokować start i być reprezentowany w stanie UI.
6. Nie przenoś reguł ani zapisu do `MainActivity`. Składanie zależności nie
   może zamienić Activity w właściciela progresji.

### Etap 1 — kontrakty i use case'y progresji

W `game-application` dodaj lub uzupełnij niemutowalny model profilu i jawny
port repozytorium progresji. Minimalne dane muszą odpowiadać istniejącemu
kontraktowi `LoadoutProgressionSnapshot`: odblokowane zwykłe czary, stock
ograniczonej amunicji, stock czarów specjalnych oraz nieujemne saldo.

- Używaj stabilnych identyfikatorów z obecnych katalogów.
- Nie kopiuj cen, statystyk ani balansu do nowego profilu.
- Rozróżnij `profile missing` (inicjalizacja pierwszego uruchomienia) od
  błędu odczytu, uszkodzonego zapisu i nieobsługiwanej wersji.
- Odczyt profilu dla `LoadoutAvailability.level` tworzy poprawny
  `LoadoutProgressionSnapshot`; dostępność na poziomie nadal waliduje
  LoadoutSystem.
- Odczyt/jądro profilu nie może automatycznie włączać wszystkiego, gdy brakuje
  danych.
- Zakup czaru zwraca jawne `Blocked` w tej fazie. Nie aktualizuj salda,
  odblokowań ani stocku.
- Zachowaj milestone'y poziomów 6/8 tylko w sposób autoryzowany przez
  CampaignSystem. Sama liczba poziomu przekazana z UI nie jest dowodem, że
  gracz odblokował milestone.
- I/O portów oraz use case'ów ma być asynchroniczne. Funkcje czysto
  walidujące i edytujące draft mogą pozostać synchroniczne, jeśli nie
  wykonują I/O.

Dodaj testy logiki profilu, walidacji, inicjalizacji i typowanych błędów bez
Androida. Zastosuj fake repozytorium, które potrafi modelować brak danych,
uszkodzony zapis i błąd I/O; nie testuj wyłącznie ścieżki sukcesu.

### Etap 2 — potwierdzenie legacy seed

Sprawdź dokumentację w bieżącym repozytorium i odpowiednie źródła legacy
wyłącznie do odczytu. Dla każdej wartości nowego profilu odnotuj źródło i
regułę:

- odblokowane czary na świeżym profilu;
- saldo startowe;
- stock ograniczonej amunicji;
- stock czarów specjalnych;
- milestone'y poziomów 6/8, jeśli da się potwierdzić i bezpiecznie powiązać
  z autoryzowanym postępem.

Nie używaj ilości `CampaignLevelDefinition.startingAmmunition` jako stocku
trwałego. Jeśli źródło legacy jest niejednoznaczne lub nie zawiera jednej z
wartości, nie podstawiaj wartości zastępczej: zatrzymaj implementację tego
seedowania i poproś użytkownika o decyzję.

Seed wykonuje się tylko raz, przy braku profilu. Ponowne uruchomienie
aplikacji nie może nadpisywać postępu ani ponownie przyznawać stocku.

### Etap 3 — asynchroniczny lokalny DataStore

Zaimplementuj produkcyjny adapter w `android-app`.

- Najpierw sprawdź istniejące zależności. Dodaj zgodną zależność AndroidX
  DataStore tylko wtedy, gdy brakuje jej w projekcie; nie instaluj ani nie
  aktualizuj niezwiązanych narzędzi.
- Użyj wersjonowanego schematu i atomowej aktualizacji DataStore.
- Zapisuj tylko stabilne ID oraz liczby profilu; nie serializuj obiektów
  domenowych ani Android Views.
- Obsłuż migracje między wersjami schematu nowej aplikacji i jawnie raportuj
  nieznaną wersję/uszkodzone dane.
- Obsłuż oczekiwane błędy I/O jako typowany failure. Nie przechwytuj
  wszystkich wyjątków i nie zamieniaj awarii na nowy profil.
- Nie zapisuj `CampaignProgression` ani runtime `MatchSnapshot` w repozytorium
  profilu tego systemu.
- Nie importuj ani nie odczytuj kluczy z legacy `SharedPreferences`.

Dodaj testy adaptera dla: inicjalizacji, ponownego odczytu, zachowania danych
po odtworzeniu adaptera, atomic update, każdej migracji, uszkodzonego payloadu,
nieznanej wersji i błędu I/O. Jeżeli repo nie ma właściwego host testowego dla
DataStore, użyj testów adaptera zgodnie z aktualnymi wzorcami Gradle; nie
deklaruj testu jednostkowego fake'a jako testu persystencji.

### Etap 4 — produkcyjny zapis loadoutu

Zaimplementuj adapter `LoadoutRepository` na lokalnym DataStore, zgodnie z
istniejącym portem i niezmiennym `MatchLoadout`.

- Zapisuj wyłącznie zatwierdzony wybór: jednostki, trzy sloty amunicji i
  czary.
- Zachowuj kolejność jednostek i czarów oraz pozycję `ROCK`.
- Draftu ekranu nie zapisuj.
- Odczyt starego/nieznanego wyboru przekazuje raw wartości do istniejącej
  normalizacji aplikacyjnej, aby UI dostało typowane korekty.
- Błąd odczytu/zapisu jest jawny i nie może zwrócić success ani umożliwić
  przejścia do meczu.
- Zachowaj osobny schemat/namespace dla loadoutu i profilu. Jeśli używają
  wspólnego DataStore, aktualizacja jednej sekcji nie może nadpisać drugiej.

Testy potwierdzają round-trip, wszystkie trzy kategorie, kolejność, nullable
sloty, brak zapisu draftu, odporność na nieznane ID i izolację danych profilu.

### Etap 5 — LoadoutUseCases i ekran

Zintegruj asynchroniczne porty z istniejącym LoadoutSystemem:

- `open` czeka na poprawny snapshot profilu i zapis loadoutu; rozróżnia
  zablokowaną progresję, błąd profilu, błąd repozytorium oraz otwarty draft.
- `commit` ponownie pobiera aktualny snapshot, waliduje wybór i zapisuje go
  przed wydaniem `PlayerLoadoutConfig`.
- `purchaseSpell` pozostaje jawnie zablokowany w tym zakresie.
- `LoadoutScreen`/screen-owned ViewModel pokazuje stan loading, gotowy draft,
  korekty i czytelny stan błędu. Nie włącza startu podczas loadingu ani po
  błędzie.
- Dopiero sukces `commit` umożliwia `AppNavigator` przejście do `MatchScreen`.
- Użyj scope powiązanego z lifecycle ekranu. Anuluj pracę po usunięciu
  ekranu; nie blokuj main thread.
- Nie zmieniaj CampaignScreen na system odblokowań w tym zadaniu. Jej
  tymczasowe testowe odblokowanie wszystkich poziomów pozostaje osobnym
  blokerem CampaignSystemu.
- Zachowaj obecny flow nawigacji, `AppScreen`, `ScreenHost`, MatchScreen i
  MatchSession. Nie dodawaj logiki do renderera.

Dodaj testy ViewModel/controllera dla loading, sukcesu, korekt, braku stocku,
nieodblokowanego wyboru, błędu odczytu, błędu zapisu, anulowania i
niezachodzenia nawigacji po błędzie. Dla poprawnego startu sprawdź, że
`PlayerLoadoutConfig` ma wartości z zatwierdzonego loadoutu.

### Etap 6 — granice i dokumentacja

- Sprawdź, że core nie zależy od Androida ani DataStore.
- Sprawdź, że CampaignSystem nadal posiada `highestUnlockedLevel`.
- Sprawdź, że `CampaignLevelDefinition` nadal posiada dostępność poziomu.
- Sprawdź, że LoadoutSystem nadal posiada walidację i zapis wyboru.
- Sprawdź, że profile stocku nie udają rozliczenia zużycia z runtime meczu.
- Nie implementuj `CastSpell`, zakupów, nagród, cloud sync ani importu z
  legacy.
- Zaktualizuj `docs/systems/player-progression-system.md`,
  `docs/systems/loadout-system.md`, `docs/systems/campaign-system.md`,
  `architecture.md`, `AI_PROJECT_MAP.md`, `docs/PROJECT_KNOWLEDGE.md` i
  `GLOSSARY.md` tylko w zakresie faktycznie zaimplementowanego stanu.
  Rozróżnij kontrakty, działające adaptery i pozostałe blokery.
- Jeśli źródła legacy wymagały decyzji użytkownika, najpierw ją uzyskaj;
  nie oznaczaj implementacji jako kompletnej przed rozstrzygnięciem.

### Testy i walidacja

Uruchom najpierw testy modułu aplikacyjnego i testy Androida adekwatne do
zmienionych adapterów. Następnie przed zakończeniem wykonaj pełną bramkę
projektu:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Napraw regresje wprowadzone przez tę zmianę i powtórz walidację. Nie commituj
ani nie pushuj. W podsumowaniu wypisz rzeczywiście zmienione pliki, wyniki
walidacji, pozostałe blokery i potwierdź gałąź bez wykonywania operacji Git
zmieniających historię.
