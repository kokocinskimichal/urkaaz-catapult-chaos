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

- Pełny LocalizationSystem jest osobnym, jeszcze niezaimplementowanym
  zadaniem. Globus w nagłówku ma otwierać i zamykać krótki popup zawierający
  flagi Polski, Wielkiej Brytanii i Hiszpanii.
- Same flagi są nieklikalne i nie zmieniają języka. Nie dodawaj
  `SelectLanguage`, callbacków wyboru, use case'u, DataStore, Locale managera,
  `AppCompatDelegate` ani tłumaczeń.
- Zamykaj popup po ponownym dotknięciu globusa, dotknięciu poza panelem,
  systemowym Back lub opuszczeniu Dashboardu. Nie pokazuj stanu aktywnego ani
  informacji o sukcesie.

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
4. Pozostaw poprzedni
   `android-app/src/main/res/drawable-nodpi/dashboard_background.png`
   nienaruszony. Użyj dedykowanego
   `android-app/src/main/res/drawable-nodpi/dashboard_background_approved.png`.

### Właściciel i ograniczenia architektury

- Właścicielem jest istniejący ekran w
  `android-app/src/main/java/com/urkaaaz/screens/dashboard/`.
- Zachowaj `ScreenContent`, `AppScreen.Dashboard`, `AppNavigator` i
  `ScreenHost`. Nie twórz drugiego Dashboardu, nowego routera ani modułu
  Gradle.
- `MainActivity` pozostaje composition rootem: przekazuje istniejące callbacki
  odczytu profilu, resetu i nawigacji, bez budowania layoutu Dashboardu.
  Dashboard nie otrzymuje callbacku zmiany języka; wybór języka należy do
  przyszłego LocalizationSystem.
- `DashboardViewModel` przechowuje tylko stan prezentacyjny. Profil, reset i
  język pozostają własnością istniejących warstw aplikacyjnych/persistencji.
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
- Użyj `loadout_header_frame.png` dla panelu tytułowego i
  `match_hud_universal_frame.9.png` dla panelu statystyk profilu.
  Nałóż ramkę profilu na nieprzezroczyste, ciemne, zaokrąglone wypełnienie,
  aby środek NinePatch nie odsłaniał tła sceny.
  `dashboard_hud_portrait_frame.png` oprawia avatar. Zbuduj napis `URKAAAZ`
  jako żywy tekst w tytułowej ramce; nie używaj kolorowego
  `logo_main_menu.png`, którego kompozycja nie odpowiada zaakceptowanemu
  screenowi.
- Użyj `loadout_start_match_button` jako tła głównego przycisku `CAMPAIGN`
  oraz `loadout_start_match_button_xs_short` dla `BACK`; etykiety renderuj jako
  żywy tekst, bez assetu `dashboard_campaign_button.png` i
  `dashboard_back_button.png`.
- Zbuduj `SHOP` i `AD REWARD` na wspólnym `ui_button_dark_m` z żywymi etykietami.
  Shop używa istniejącego `icon_orcoins.png`; Ad Reward używa małej wektorowej
  ikony clapperboardu. Nie używaj ilustracyjnych
  `dashboard_shop_button.png` ani `dashboard_ad_reward_button.png`.
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
  - Test Mode → istniejący `LoadoutScreen` w trybie demo, a następnie mecz
    `DEMO_LEVEL`.
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
- Flagi w popupie są nieklikalnymi obrazami bez zaznaczenia aktywnego języka.
  Ich dotknięcie nie zmienia języka i nie pokazuje komunikatu sukcesu.
- Zamykaj popup po ponownym dotknięciu globusa, dotknięciu poza nim, systemowym
  Back lub opuszczeniu Dashboardu. Nie dodawaj integracji z niezaimplementowanym
  LocalizationSystem.

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
- otwierania i zamykania popupu globusa oraz widoczności flag;
- braku akcji językowej po dotknięciu flag;
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

Nie commituj ani nie pushuj bez osobnego polecenia użytkownika. Nie implementuj
pełnego LocalizationSystem ani systemu eventów w ramach tego zadania.
