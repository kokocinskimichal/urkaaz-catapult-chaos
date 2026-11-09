# Prompt implementacji DashboardScreen

> Prompt opisuje pierwotną implementację Dashboardu. Shop był wtedy
> placeholderem; obecnie Dashboard otwiera zaimplementowany `ShopScreen`,
> który po powrocie przywraca Dashboard.

Skopiuj prompt poniżej do zadania implementacyjnego w repozytorium Urkaaaz.

## Prompt

Przebuduj **istniejący Android Dashboard** zgodnie z
`docs/systems/dashboard-screen.md` oraz zaakceptowanym screenshotem i osobno
dostarczonym tłem. To implementacja istniejącego ekranu, nie nowej nawigacji,
modułu Gradle ani systemu gameplayowego.

### Ikony języka

Selektor globusa jest już częścią LocalizationSystem. Zachowuj istniejące
klikalne flagi PL/UK/ES, aktywny stan, zapis języka, komunikat błędu i zamykanie
popupu po wyborze, dotknięciu poza panelem, systemowym Back lub opuszczeniu
Dashboardu. Zobacz `docs/systems/localization-system.md`; nie dodawaj drugiego
mechanizmu języka ani nie cofaj istniejącej integracji.

### Kontekst i źródła prawdy

1. Przeczytaj `AGENTS.md`, `docs/architecture/architecture.md`,
   `docs/architecture/implementation-rules.md`,
   `docs/architecture/project-map.md`, `docs/project/current-state.md`,
   `docs/project/knowledge.md`, `docs/systems/dashboard-screen.md` oraz
   `docs/systems/localization-system.md`.
2. Sprawdź bieżący kod i testy: `DashboardScreen`, `DashboardViewModel`,
   `DashboardUiState`, `DashboardUiAction`, `MainActivity`, `AppNavigator`,
   `ScreenHost`, `PlayerProgressResetRepository` i aktualny port summary.
   Dokumenty opisują zamiar, nie zastępują bieżącego kodu.
3. Użyj jako źródeł prawdy:
   `assets_src/dashboard/screen-concepts/dashboard_screen_approved_reference.png`
   oraz `assets_src/dashboard/dashboard_background_approved.png`. Jeśli któregoś
   brakuje, poproś o odtworzenie źródła zamiast odtwarzać je z pamięci albo
   generować zastępczą grafikę.
4. Usuń nieużywany runtime asset poprzedniego tła
   `android-app/src/main/res/drawable-nodpi/dashboard_background.png`.
   Nie usuwaj źródła zaakceptowanego tła ani screenshotu referencyjnego.
   Użyj dedykowanego
   `android-app/src/main/res/drawable-nodpi/dashboard_background_approved.png`.

### Właściciel i ograniczenia architektury

- Właścicielem jest istniejący ekran w
  `android-app/src/main/java/com/urkaaaz/screens/dashboard/`.
- Zachowaj `ScreenContent`, `AppScreen.Dashboard`, `AppNavigator` i
  `ScreenHost`. Nie twórz drugiego Dashboardu, nowego routera ani modułu
  Gradle.
- `MainActivity` pozostaje composition rootem: przekazuje istniejące callbacki
  odczytu profilu, resetu, nawigacji oraz istniejącego use case'u wyboru
  języka, bez budowania layoutu Dashboardu. Zastosowanie Locale i persystencja
  pozostają w LocalizationSystem.
- `DashboardViewModel` przechowuje tylko stan prezentacyjny. Profil, reset i
  wybór języka pozostają własnością warstw aplikacyjnych/persistencji.
- Nie dodawaj logiki gameplayu, nie zmieniaj symulacji, kampanii, balansów,
  `MatchEvent` ani snapshotów.
- Użyj screen-owned Android Views zgodnych z obecną implementacją. Nie
  wprowadzaj Compose ani migracji nawigacji.

### Layout i assety

- Odtwórz zaakceptowaną kompozycję: pełne tło, Back i logo w górnym nagłówku,
  profil i zasoby, globus w nagłówku z listą flag pokazywaną po dotknięciu,
  wyraźny przycisk Campaign w centrum,
  przyciski Shop, Test Mode i Ad Reward niżej oraz mały RESET w prawym dolnym
  rejonie.
- Użyj wspólnego `shared_header_frame_low_profile.png` dla panelu tytułowego
  Dashboardu; ten sam niski asset jest współdzielony z Shopem i przyszłymi
  nagłówkami. Loadout zachowuje własny, dwupolowy
  `loadout_header_frame.png`. Tekst pozostaje osobną warstwą UI. Użyj też
  `match_hud_universal_frame.9.png` dla panelu statystyk profilu.
  Nałóż ramkę profilu na nieprzezroczyste, ciemne, zaokrąglone wypełnienie,
  aby środek NinePatch nie odsłaniał tła sceny.
  `dashboard_hud_portrait_frame.png` oprawia avatar. Zbuduj napis `URKAAAZ`
  jako żywy tekst w tytułowej ramce; stary `logo_main_menu.png` został usunięty
  i nie należy go przywracać.
- Użyj `loadout_start_match_button` jako tła głównego przycisku `CAMPAIGN`
  oraz `loadout_start_match_button_xs_short` dla `BACK`; etykiety renderuj jako
  żywy tekst. Stare assety `dashboard_campaign_button.png` i
  `dashboard_back_button.png` zostały usunięte.
- Zbuduj `SHOP` i `AD REWARD` na wspólnym `ui_button_dark_m` z żywymi etykietami.
  Shop używa istniejącego `icon_orcoins.png`; Ad Reward używa małej wektorowej
  ikony clapperboardu. Stare `dashboard_shop_button.png` i
  `dashboard_ad_reward_button.png` zostały usunięte.
- Pokaż `TEST MODE` jako dodatkowy przycisk na wspólnym ciemnym tle, z żywą
  etykietą i bez nowego assetu.
- Użyj istniejących ikon życia i złota; status regeneracji pokaż z ikoną
  wektorowego serca z plusem zamiast klepsydry.
- Drawable assety PNG znajdują się w
  `android-app/src/main/res/drawable-nodpi/`; selektory wspólnych buttonów w
  `res/drawable/`.
- Dla powtarzalnych marginesów i Back użyj `ScreenChromeMetrics`, a żywy tekst
  przycisków renderuj z `UiTypography`/Cinzel SemiBold.
- Dodaj tylko brakujące małe ikony wymagane przez specyfikację: globus, flagi
  PL/UK/ES, reset, clapperboard Ad Reward i serce regeneracji z plusem.
  Preferuj proste, oddzielne drawable Androida; nie generuj nowych ramek ani
  bitmap przycisków.
- Zachowaj immersive landscape i bezpieczne marginesy kontrolek. Tło ma
  wypełniać ekran także przy short-edge display cutouts; ustaw tę politykę
  centralnie w `MainActivity`, bez przesuwania kontrolek w niebezpieczny
  obszar.

### Dane, nawigacja i operacje

- Wyświetl `LEVEL`, życia, złoto i `N/A` regeneracji z aktualnego
  `DeveloperProgressSummary`; wartości przykładowe ze screenshotu nie są
  wartościami stałymi.
- Zachowaj przepływy:
  - Back → poprzedni ekran przez `AppNavigator`; gdy Dashboard jest ekranem
    głównym, akcja kończy aplikację;
  - Campaign → `CampaignScreen`, która prowadzi przez wybrany poziom do
    Loadout;
  - Shop → `ShopScreen`; powrót prowadzi do Dashboardu;
  - Test Mode → osobny selektor leveli 1–54, niezależny od postępu kampanii.
    Wybór otwiera testowy Loadout z dostępnością wybranego poziomu, bez zapisu
    do profilu; mecz używa wybranej definicji kampanii i Debug Tools bez
    zapisu postępu, nagród ani użycia stocku.
- Pokaż tylko jeden RESET, w miejscu zgodnym ze screenshotem. Usuń
  nieobsługiwany, duplikujący RESET z górnego HUD-u. Zostaw istniejący dialog
  potwierdzenia i wywołuj `PlayerProgressResetRepository` dopiero po
  potwierdzeniu. Blokuj wielokrotne resetowanie, pokazuj jawny wynik i odśwież
  summary po sukcesie. Reset postępu nie może czyścić osobnego ustawienia
  języka.
- `AD REWARD` pozostaje widoczny z istniejącym assetem, ale bez integracji
  reklam, przyznawania nagród, SDK, komunikatu sukcesu ani nowego use case'u.
  Nie twórz fałszywego działania.
- Odczyt profilu jest asynchroniczny i anulowany zgodnie z lifecycle ekranu.
  Przy błędzie nie pokazuj wartości domyślnych tak, jakby były odczytane:
  udostępnij jawny stan błędu i bezpieczną możliwość ponowienia.

### Selektor języka

- Umieść sam globus w prawym obszarze nagłówka. Po dotknięciu otwórz przy nim
  krótki, poziomy popup z flagami Polski (`PL`), Wielkiej Brytanii (`EN`) i
  Hiszpanii (`ES`).
- Każda flaga jest klikalnym celem co najmniej `48 dp`, ma opis dostępności z
  nazwą języka, a aktywna flaga jest widocznie zaznaczona.
- Wybór wysyła `DashboardUiAction.SelectLanguage` przez
  `DashboardViewModel`, który wywołuje `SetLanguage`. `Saved` odtwarza
  Dashboard w nowym języku; `Unchanged` nie odtwarza; `Failure` pokazuje
  jawny błąd bez nadpisywania zapisu.
- Zamykaj popup po ponownym dotknięciu globusa, dotknięciu poza nim, systemowym
  Back, wyborze języka lub opuszczeniu Dashboardu. Korzystaj z istniejącego
  LocalizationSystem; nie twórz drugiego magazynu języka ani Locale managera.

### Ukryte sloty eventów

- Dodaj dwa screen-owned sloty overlay po lewej i prawej stronie głównego
  przycisku Campaign, ustawione symetrycznie względem tej akcji.
- Sloty mają pozostać całkowicie niewidoczne: bez ikony, tła, obramowania,
  napisu, badge'a, accessibility labela lub placeholderowej grafiki.
- Użyj stanu `GONE` albo równoważnego sposobu ukrycia bez rezerwowania miejsca
  i bez przesuwania obecnych kontrolek. Zachowaj tylko stabilne punkty
  montażowe w układzie.
- Nie dodawaj listy eventów, harmonogramu, danych przykładowych, kliknięć ani
  nowego GameEventSystem. Sloty nie mają obecnie źródła danych ani runtime
  state; przyszły system będzie osobnym zadaniem.

### Testy i akceptacja

Dodaj lub zaktualizuj testy w `android-app/src/test` dla:

- mapowania progress summary oraz stanu loading/error bez podstawiania
  wartości domyślnych przy błędzie;
- callbacków Back, Campaign i Shop;
- resetu wymagającego potwierdzenia, blokady duplikatów, jawnego wyniku i
  odświeżenia summary;
- braku działania `AD REWARD`;
- mapowania flag na `AppLanguage`, stanu aktywnej flagi oraz zamknięcia popupu
  po wyborze i systemowym Back;
- dwóch ukrytych slotów bez widocznego renderowania i bez wpływu na layout;
- lifecycle Dashboardu oraz braku zależności UI w modułach core.

Zaktualizuj bezpośrednio powiązane dokumenty Dashboardu i LocalizationSystem.
Nie zmieniaj `docs/agent-context-index.md`, `AGENTS.md` ani niezwiązanych
plików, jeśli nie są wymagane do tej implementacji.

Uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Nie commituj ani nie pushuj bez osobnego polecenia użytkownika. Nie
reimplementuj LocalizationSystem ani systemu eventów w ramach tego zadania.
