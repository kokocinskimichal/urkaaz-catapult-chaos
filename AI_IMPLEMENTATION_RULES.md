# Urkaaaz — reguły implementacji i walidacji

## Granice architektoniczne

- `android-app` jest jedynym modułem zależnym od Androida.
- Silnik nie importuje `Activity`, `View`, `Canvas`, `Context`, bitmap ani
  innych klas Androida.
- UI wysyła `MatchUiAction` albo komendy aplikacyjne; nie mutuje domeny
  bezpośrednio.
- `Match`/symulacja jest jedynym właścicielem runtime state meczu.
- Renderery otrzymują niemutowalne snapshoty lub render state, nie obiekty
  domenowe do modyfikacji.
- Stan persystencji i kampanii nie może być mieszany ze stanem runtime
  symulacji.
- Komendy, snapshoty i eventy są jawnymi granicami komunikacji.
- Nie tworzyć równoległych kontraktów terenu, assetów, nawigacji ani balansu.

## Reguła rozwoju przez podsystemy

- Każda większa funkcjonalność gameplayowa ma być projektowana jako osobny
  podsystem z własną odpowiedzialnością, kontraktem wejścia/wyjścia, stanem,
  eventami, testami i adapterem UI, jeśli jest potrzebny.
- Istniejące wzorce referencyjne to `AmmunitionSystem`, `CombatSystem` i
  `Level Definition/TerrainSystem`.
- Kolejne obszary, takie jak `SpellSystem`, `TutorialSystem`, system progresji
  (`CampaignSystem`), `AISystem` czy system nagród, nie powinny być
  implementowane jako przypadkowe pola i warunki w `Match`, `MainActivity`,
  ViewModelu albo jednym „God Object”.
- Nazwa podsystemu nie wymusza osobnego modułu Gradle. `AISystem` może należeć
  do `game-ai`, a `CampaignSystem` do `game-campaign`, o ile granice
  odpowiedzialności i kontrakty pozostają jawne.
- Podsystemy komunikują się przez istniejące granice komend, snapshotów,
  eventów i kontraktów aplikacyjnych. Nie mutują bezpośrednio stanu należącego
  do innego podsystemu.
- Przed implementacją nowego podsystemu należy opisać jego właściciela,
  zależności, kontrakt, wpływ na runtime state oraz strategię testowania.

## Reguły domeny, symulacji i AI

- Reguły gameplayu pozostają w `game-domain` lub `game-simulation`, nigdy w
  Android UI.
- Symulacja jest deterministyczna: stały krok, kontrolowana losowość i
  reprodukowalne replaye.
- Błędy i odrzucone komendy muszą być jawne; nie stosować cichych fallbacków,
  szerokich `catch` ani sukcesopodobnych wartości błędnych.
- `AmmunitionCatalog` jest single source of truth dla amunicji.
- Nie duplikować wartości balansu w Androidzie ani AI.
- Jednostki korzystają z centralnych konfiguracji; nowe zachowania należy
  przypisać właściwej klasie/systemowi zamiast rozbudowywać God Object.
- Subprojectile jest zwykłym deterministycznym pociskiem z `parentId` i
  generacją; nie może rekurencyjnie tworzyć kolejnej generacji.
- Logowanie gameplayu nie może zmieniać stanu gry.
- AI podejmuje decyzje na podstawie snapshotu i emituje komendy, nie mutuje
  symulacji.

## Reguły Android UI

- `MainActivity` pozostaje cienkim entry pointem.
- Nowe kontrolki dodawać w screen-owned view/controller i podłączać przez
  `AppScreen` oraz `AppNavigator`.
- Campaign screen może używać legacy visual language, ale poziomy muszą
  pochodzić z `game-campaign`; nie kopiować legacy progression ani engine.
- World-space: pociski, trafienia, eksplozje, lingering areas i jednostki.
- Screen-space: HUD, reload, inventory i kontrolki.
- Kamera, celowanie i pan muszą rozróżniać gest aim od gestu przesuwania;
  `ACTION_UP` odpala tylko prawidłowy gest celowania.
- Debug overlay jest narzędziem prezentacji i nie wprowadza osobnego stanu
  gameplayu.

## Reguły assetów i renderingu

- Assety powinny być ładowane przez istniejące katalogi/cache, nie dekodowane
  ponownie dla każdej klatki lub jednostki.
- Renderowanie zachowuje istniejącą kolejność i wspólną transformację kamery.
- Pozycje jednostek opierają się na wspólnej linii terenu/renderera; nie
  dodawać niezależnych, „pływających” offsetów.
- HP bar jednostki i stan dostępności amunicji muszą odzwierciedlać snapshot,
  nie lokalną kopię stanu gameplayu.

## Reguły jakości kodu

- Najpierw odczytać odpowiedni moduł, testy i istniejący wzorzec.
- Wprowadzać najmniejszą kompletną zmianę, bez niezwiązanych refaktorów.
- Preferować istniejące helpery, typy i konfiguracje.
- Zachować typowanie i unikać niepotrzebnych `as any` / `as unknown as`.
- Komentarze dodawać tylko dla nieoczywistej decyzji lub ograniczenia.
- Zmiany dokumentacji utrzymywać razem ze zmianą kontraktu lub zachowania.
- Nie kopiować implementacji legacy plik do pliku; przenosić funkcję przez
  kontrakt i właściwą warstwę.

## Testy

Dodawać testy odpowiednie dla warstwy:

- domena — reguły, konfiguracje, walidacja i przejścia stanu;
- symulacja — ticki, kolizje, wiatr, eventy i deterministyczne replaye;
- aplikacja — lifecycle sesji, gateway i restart;
- AI — snapshot → komendy;
- Android — granice `MainActivity`, ViewModel, mapowanie i zachowanie UI;
- architektura — brak importów Androida w core i poprawny kierunek zależności.

## Obowiązkowa walidacja

Przed commitem lub deklaracją ukończenia zmiany uruchomić z katalogu głównego:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Dla węższej zmiany można najpierw uruchomić najmniejszy adekwatny zestaw,
ale pełna bramka jest wymagana przed commitem. Dokumentacja-only nie wymaga
builda, chyba że repozytorium posiada testy dokumentacji; w każdym przypadku
należy wykonać `git diff --check`.

## Workflow zmian

1. Zidentyfikować właściciela odpowiedzialności i przeczytać jego testy.
2. Ustalić kontrakt wejścia/wyjścia oraz wpływ na zależności.
3. Zaimplementować zmianę w odpowiedniej warstwie.
4. Dodać lub zaktualizować testy i dokumentację.
5. Uruchomić walidację.
6. Nie commitować ani nie pushować bez wyraźnej prośby użytkownika.
