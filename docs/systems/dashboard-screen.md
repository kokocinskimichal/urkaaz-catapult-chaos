# DashboardScreen — architektura i plan przebudowy

## Status i cel

Przebudowa istniejącego ekranu według zaakceptowanego przez użytkownika
screenshotu jest zaimplementowana. Globus otwiera działający selektor PL/EN/ES,
którego właścicielem jest LocalizationSystem; lista tłumaczeń została
zaakceptowana. Eventy gry pozostają odłożone. Ten dokument opisuje granice
istniejącego ekranu, a nie drugi Dashboard, nowy moduł Gradle ani system
gameplayowy.

Cel: odtworzyć zaakceptowaną kompozycję Dashboardu, zachować istniejące
przepływy profilu, kampanii, sklepu i resetu, korzystać z selektora języka
LocalizationSystem oraz pozostawić dwa niewidoczne punkty rozszerzenia dla
przyszłych ikon eventów gry.

Typ: system prezentacyjny Androida. Nie wpływa na symulację, kampanię ani
balans.

## Zakres

- Przebudowa istniejącego `DashboardScreen` według zaakceptowanego przez
  użytkownika pełnoekranowego screenshotu i osobno dostarczonego obrazu tła.
- Zachowanie live summary profilu: poziom, życia, złoto i status regeneracji
  `N/A`; wartości zawsze pochodzą z danych aplikacji, nie ze screenshotu.
- Zachowanie Back, Campaign, Shop i potwierdzanego RESET.
- Umieszczenie przycisku `TEST MODE` na Dashboardzie, który otwiera niezależny
  od postępu gracza wybór poziomów kampanii 1–54.
- Zachowanie widocznego przycisku `AD REWARD` wyłącznie jako elementu
  prezentacji; nie dodawać integracji reklam ani nagród.
- Użycie istniejącego selektora globusa LocalizationSystem w nagłówku. Ekran
  nie tworzy własnej persystencji ani drugiego mechanizmu wyboru języka.
- Utworzenie dwóch pustych, ukrytych anchorów na przyszłe ikony eventów:
  symetrycznie po lewej i prawej stronie głównego przycisku Campaign.
- Użycie istniejących assetów Dashboardu i wspólnych helperów Android UI.

## Poza zakresem

- Implementacja systemu reklam, pobierania nagród za reklamy ani zakupów.
- Implementacja katalogu sklepu lub zakupów. Shop pozostaje istniejącym
  placeholderem nawigacyjnym.
- Implementacja systemu eventów gry, harmonogramu eventów, ich ikon,
  dostępności, limitów, backendu ani persystencji.
- Wprowadzanie eventów do `MatchEvent` albo do runtime state symulacji.
- Własna implementacja LocalizationSystem w ramach zadania Dashboardu;
  Dashboard korzysta z jego istniejącego portu i callbacku use case'u.
- Zmiana trybu nawigacji, migracja do Compose lub dodawanie modułu Gradle.
- Zmiana reguł progresji, resetu, życia lub nagród.

## Zaakceptowany kierunek wizualny

Źródłem prawdy dla układu jest screenshot Dashboardu zaakceptowany przez
użytkownika. Osobno dostarczone tło jest właściwą warstwą sceny. Oba obrazy
zachowano w `assets_src/dashboard/`; nie należy ich odtwarzać z pamięci ani
zastępować nową grafiką.

Hierarchia ekranu:

1. Nagłówek: Back, branding Urkaaaz, portret gracza, poziom/życia/złoto/status
   regeneracji i globus; po jego dotknięciu pojawia się krótka lista trzech flag.
2. Główny, centralny przycisk Campaign.
3. Bezpośrednio pod Campaign: tylko drugorzędne przyciski Shop i Ad Reward;
   razem zajmują tę samą szerokość co przycisk Campaign.
4. Test Mode w prawym dolnym rejonie, bezpośrednio nad przyciskiem RESET.
5. Mały przycisk RESET w prawym dolnym rejonie, oddzielony od głównej
   nawigacji.
6. Dwa puste, niewidoczne sloty eventów po bokach przycisku Campaign.

Profil i status pozostają tekstem UI aktualizowanym z danych. Grafika tła,
przyciski i ramki są osobnymi assetami, nie jednym spłaszczonym screenshotem.

## Właściciel i kierunek zależności

Właścicielem ekranu jest `android-app`:

| Odpowiedzialność | Właściciel |
|---|---|
| Składanie Dashboardu, widoki, lifecycle ekranu | `DashboardScreen` / `ScreenContent` |
| Stan prezentacyjny Dashboardu i mapowanie akcji | `DashboardViewModel`, `DashboardUiState`, `DashboardUiAction` |
| Przekazanie callbacków nawigacji, profilu i resetu | `MainActivity` |
| Bieżący ekran i stos Back | `AppNavigator` / `ScreenHost` |
| Odczyt summary profilu | istniejący `DeveloperProgressSummary` i jego właściciel aplikacyjny |
| Atomowy reset postępu | istniejący `PlayerProgressResetRepository` i adapter Androida |
| Trwały język, wybór języka i zastosowanie Locale | LocalizationSystem; Dashboard jest jego klientem, nie właścicielem |

`MainActivity` pozostaje cienkim composition root. Ekran nie importuje ani nie
wywołuje domeny czy symulacji. Dashboard nie staje się właścicielem profilu,
persistencji ani języka.

## Istniejące rozwiązania i ryzyko duplikacji

- `DashboardScreen`, `DashboardViewModel`, `DashboardUiState` i
  `DashboardUiAction` już istnieją. Przebudowa rozszerza je i usuwa
  nieużywaną prezentację, zamiast tworzyć `DashboardScreenV2`.
- `AppScreen.Dashboard`, `AppNavigator` i `ScreenHost` już obsługują lifecycle
  i nawigację. Nie tworzyć równoległego routera ani nowego kontraktu ekranu.
- Odczyt profilu i reset są już podłączone przez `MainActivity`. Nie dodawać
  drugiego repozytorium ani lokalnej kopii postępu.
- `localization-system.md` i jego prompt opisują właściciela języka. Selektor
  jest już podłączony do `SetLanguage` i osobnego DataStore; Dashboard nie
  dodaje drugiego źródła prawdy ani własnego Locale managera.
- W kodzie i zasobach nie znaleziono systemu eventów Dashboardu. Dwa ukryte
  sloty są wyłącznie przyszłymi punktami montażowymi, a nie nowym systemem
  eventowym.

Rekomendacja: rozbudować istniejący ekran i korzystać z istniejących granic
aplikacji. To zachowuje nawigację i pojedynczego właściciela profilu, a zarazem
nie łączy przyszłych eventów ani lokalizacji z DashboardViewModel bardziej,
niż wymaga tego prezentacja.

## Wejścia i wyjścia

### Wejścia

- `DeveloperProgressSummary` ładowany przez istniejący callback
  `loadProgress`.
- Wynik istniejącego `PlayerProgressResetRepository.reset()` przekazany przez
  `onResetProgress`.
- Callbacki nawigacyjne Back, Campaign i Shop.
- Akcje dotykowe widoków ekranu.

Dashboard otrzymuje bieżący `AppLanguage` i `SetLanguage` z composition rootu.
Nie otrzymuje listy eventów, definicji eventu ani eventowego runtime state.
Widoczność popupu i ukryte sloty nie konsumują danych eventowych.

### Wyjścia

- Back → istniejący stos `AppNavigator`; gdy Dashboard jest ekranem głównym,
  akcja kończy aplikację zamiast wracać do splash screenu.
- Campaign → istniejący `CampaignScreen`; wybranie poziomu prowadzi dalej do
  Loadout.
- Shop → `ShopScreen`; powrót po zakupie prowadzi do Dashboardu.
- TEST MODE → osobny `TestLevelPickerScreen` z poziomami 1–54 pogrupowanymi
  według rozdziałów. Wybór otwiera Loadout z dostępnością wybranego levelu,
  ale bez zapisu loadoutu. Mecz korzysta z rzeczywistej definicji kampanii,
  otwiera Debug Tools i nie zapisuje postępu, nagród ani zużycia stocku.
  Akcja powrotu z wyniku meczu prowadzi do selektora Test Mode.
- RESET → dialog potwierdzenia, a dopiero po akceptacji wywołanie istniejącego
  resetu postępu.
- Globus → otwarcie lub zamknięcie selektora; wybór flagi wysyła
  `DashboardUiAction.SelectLanguage`, a zapisany wybór odtwarza Dashboard w
  nowym języku.
- `AD REWARD` pozostaje widoczny, ale nie uruchamia reklamy, nagrody ani nowego
  use case'u.

Nie emituje komend gameplayowych, `MatchEvent` ani snapshotów silnika.

## Kontrakty, stan i lifecycle

Ekran zachowuje obecne screen-owned `DashboardUiState` i `DashboardUiAction`.
Stan ekranu ogranicza się do danych prezentacyjnych i bieżących operacji:

- stan odczytu summary: ładowanie, gotowe dane albo jawny błąd;
- aktualne `currentLevel`, `lives` i `gold` po udanym odczycie;
- stan resetu, aby blokować wielokrotne wywołanie podczas zapisu;
- aktywny język, stan zapisu i błąd wyboru języka w `DashboardUiState`;
- widoczność popupu flag jako tymczasowy stan należący do `DashboardScreen`;

Przy wejściu/wznowieniu ekranu summary jest odczytywane asynchronicznie.
`onPause()` anuluje pracę należącą do ekranu. Błąd odczytu nie może wyglądać
jak poprawny profil domyślny: pokaż jawny stan błędu i pozwól ponowić odczyt.
Po udanym resecie profil jest odczytywany ponownie; błąd resetu pozostaje jawny.
RESET czyści wyłącznie postęp gracza; ustawienie języka w osobnym DataStore
pozostaje bez zmian.

### Popup flag

- Globus w nagłówku przełącza widoczność krótkiego popupu z trzema flagami:
  Polski (`PL`), Wielkiej Brytanii (`EN`) i Hiszpanii (`ES`).
- Flagi są klikalnymi celami co najmniej `48 dp`; aktywna flaga jest
  podświetlona i ma opis dostępności z nazwą języka.
- `Saved` zamyka popup i wywołuje `Activity.recreate()`. `Unchanged` zamyka
  popup bez odtworzenia; `Failure` pokazuje komunikat na Dashboardzie i
  pozostawia popup dostępny do ponowienia.
- Popup zamyka się po ponownym dotknięciu globusa, dotknięciu poza panelem,
  systemowym Back lub opuszczeniu Dashboardu.

## Ukryte sloty przyszłych eventów

Dashboard zawiera dwa stabilne, puste anchory overlay po obu stronach głównego
przycisku Campaign. Mają stałe pozycje względem ekranu/CTA i są ukryte przez
`GONE` albo równoważny stan bez widocznego renderowania. Nie wpływają na
pozycjonowanie ani rozmiar istniejących elementów.

W bieżącym zadaniu sloty:

- nie mają ikony, ramki, tekstu, badge'a ani placeholderowej grafiki;
- nie są klikalne i nie mają accessibility labeli;
- nie otrzymują fałszywych ani przykładowych eventów;
- nie definiują kontraktu, harmonogramu, źródła danych ani właściciela
  przyszłego systemu eventów.

Ich jedynym celem jest zachowanie symetrycznych punktów integracji dla
przyszłych okazjonalnych ikon eventów bez zmiany obecnego wyglądu.

## Assety i wspólne komponenty

Najpierw użyć istniejących zasobów Androida bez przerysowywania:

- `dashboard_background_approved.png`, którego źródłem jest
  `assets_src/dashboard/dashboard_background_approved.png`;
- zaakceptowany screenshot referencyjny:
  `assets_src/dashboard/screen-concepts/dashboard_screen_approved_reference.png`;
- The shared `shared_header_frame_low_profile.png` for the `URKAAAZ` title
  frame. It is also used by Shop and future screen headers; keep source and
  Android drawable copies identical. Loadout retains its distinct
  two-panel `loadout_header_frame.png`. Title text remains a live UI overlay;
- `match_hud_universal_frame.9.png` for the player-summary panel, layered over
  a lightly translucent dark rounded fill so its transparent center keeps the
  scene subtly visible. Keep the profile frame 40 dp high; let the 72 dp avatar
  image extend beyond its 64 dp portrait frame, centered vertically so it
  protrudes evenly above and below the profile panel. Distribute the
  level/lives/gold/regeneration groups across four equal-width sections;
- `dashboard_hud_portrait_frame.png` for the avatar;
- `loadout_start_match_button` dla głównego, złotego przycisku Campaign i
  `loadout_start_match_button_xs_short` dla Back; ich tekst jest żywym UI;
- `ui_button_dark_m` dla Shop, Ad Reward i Test Mode oraz istniejąca rodzina
  `ui_button_dark_*` dla resetu;
- `icon_lives_heart.png`, `icon_orcoins.png` i `player_avatar_01`.

Wszystkie powyższe istniejące assety drawable znajdują się w
`android-app/src/main/res/drawable-nodpi/`, z wyjątkiem selektorów XML
przycisków w `res/drawable/`. Zaakceptowany obraz tła
`dashboard_background_approved.png` jest jedynym runtime tłem Dashboardu;
źródło i screenshot referencyjny pozostają w `assets_src/dashboard/`.

Nieużywane, stare kopie runtime usunięto z Android resources:
`dashboard_background.png`, `dashboard_hud_top_panel.png`,
`dashboard_back_button.png`, `dashboard_campaign_button.png`,
`dashboard_shop_button.png`, `dashboard_ad_reward_button.png` i
`logo_main_menu.png`. Zamiast starych przycisków ekran składa etykiety na
wspólnych tłach; `CAMPAIGN` używa złotego tła, a `SHOP` i `AD REWARD` ciemnych.

Wspólne helpery:

- `ScreenChromeMetrics` dla bezpiecznych marginesów i wspólnych wymiarów
  przycisku Back;
- `UiTypography` dla Cinzel SemiBold w żywym tekście UI;
- istniejący `ScreenContent`, `AppNavigator` i `ScreenHost`.
- `MainActivity` pozostaje właścicielem immersive landscape i pozwala tłu
  wypełniać short-edge display cutouts; kontrolki pozostają odsunięte od
  wycięć ekranu.

Katalog drawable zawiera wektorowe ikony globusa, flag PL/UK/ES, resetu,
clapperboardu i regeneracji. Dashboard używa istniejących assetów; nie dodawać
duplikatów ani nowych bitmap przycisków i ramek.

## Obsługa błędów

- Błąd odczytu `DeveloperProgressSummary`: nie pokazywać liczb 1/5/0 jako
  odczytanych danych; zaprezentować jawny stan błędu i ponowienie.
- `PlayerProgressResetResult.Failure` lub znany błąd IO/argumentu: nie pokazywać
  sukcesu; zachować możliwość ponowienia zgodnie z bieżącym wzorcem.
- Błąd zapisu języka jest widoczny; nie odtwarza się Activity ani nie
  nadpisuje nieobsługiwanego zapisu.
- Nie używać szerokich `catch`, cichych fallbacków ani drugiej kopii profilu.
- Brak eventów nie jest błędem: sloty pozostają ukryte.

## Plan testów i kryteria akceptacji

### Testy Android

- Mapowanie `DeveloperProgressSummary` na poziom, życia i złoto.
- Stan ładowania i jawny błąd odczytu bez podstawiania przykładowych wartości.
- Back, Campaign i Shop wywołują właściwe callbacki.
- RESET wymaga potwierdzenia, blokuje duplikat podczas operacji, pokazuje
  wynik i odświeża summary po sukcesie.
- `AD REWARD` pozostaje widoczny i nie uruchamia ad callbacku ani nagrody.
- Globus otwiera selektor PL/UK/ES; wybór zmienia UI, zapisuje preferencję i
  wraca na Dashboard. Nieobsługiwany zapis nie jest nadpisywany.
- Dwa sloty eventów są niewidoczne/puste, bez kliknięć i bez przesunięcia
  istniejącej kompozycji.
- Ekran respektuje lifecycle `ScreenContent`; nie dodaje nawigacji ani
  zależności Android UI do modułów core.

`DashboardViewModelTest` pokrywa stan początkowy, poprawny odczyt summary i
błąd bez wartości domyślnych. Kompozycja widoków i widoczność ukrytych anchorów
pozostają kryteriami przeglądu UI; nie dodano osobnego testu instrumentation.

### Kryteria akceptacji

1. Dashboard odtwarza zaakceptowaną kompozycję i używa istniejącego tła,
   logo, przycisków, ramek oraz ikon profilu.
2. Poziom, życia, złoto i `N/A` pochodzą z aktualnego stanu aplikacji.
3. Campaign, Shop, Back i RESET zachowują opisane przepływy; reset jest
   potwierdzany i nie zmienia ustawień języka.
4. `AD REWARD` jest widoczny, ale nie implementuje reklam ani nagród.
5. Globus otwiera krótki selektor flag Polski, Wielkiej Brytanii i Hiszpanii;
   wybór zmienia UI, zapisuje język i po `recreate()` pozostaje na Dashboardzie.
6. Oba punkty eventowe są całkowicie niewidoczne i nie zmieniają layoutu.
7. Nie powstaje drugi ekran, nowy moduł, źródło prawdy dla języka ani system
   eventów.

## Pliki implementacji

- `android-app/src/main/java/com/urkaaaz/screens/dashboard/DashboardScreen.kt`
- `android-app/src/main/java/com/urkaaaz/screens/dashboard/DashboardViewModel.kt`
- `android-app/src/main/java/com/urkaaaz/screens/dashboard/DashboardUiState.kt`
- `android-app/src/main/java/com/urkaaaz/screens/dashboard/DashboardUiAction.kt`
- `android-app/src/main/res/drawable-nodpi/dashboard_background_approved.png`
- `assets_src/dashboard/dashboard_background_approved.png`
- `assets_src/dashboard/screen-concepts/dashboard_screen_approved_reference.png`
- `android-app/src/main/res/drawable/ic_dashboard_language_globe.xml`
- `android-app/src/main/res/drawable/ic_dashboard_language_flag_poland.xml`
- `android-app/src/main/res/drawable/ic_dashboard_language_flag_uk.xml`
- `android-app/src/main/res/drawable/ic_dashboard_language_flag_spain.xml`
- `android-app/src/main/res/drawable/ic_dashboard_ad_reward.xml`
- `android-app/src/main/res/drawable/ic_dashboard_life_regeneration.xml`
- `android-app/src/main/res/drawable/ic_dashboard_reset.xml`
- `android-app/src/test/java/com/urkaaaz/screens/dashboard/DashboardViewModelTest.kt`
- `android-app/src/main/res/values/strings.xml`
- `docs/systems/localization-system.md` i
  `docs/systems/localization-system-implementation-prompt.md` jako źródło
  kontraktu selektora, jego persystencji i tłumaczeń.

Nie zmieniać `AppScreen` ani tworzyć nowego modułu, chyba że implementacja
wykaże konkretną, wcześniej nieznaną granicę odpowiedzialności.

## Rozważone warianty

| Wariant | Czytelność | Testowalność | Koszt integracji | Decyzja |
|---|---|---|---|---|
| Przebudować istniejący Dashboard w `android-app/screens/dashboard` | Wysoka | Wysoka | Niski | Rekomendowany: reuse nawigacji, profilu, resetu, helperów i assetów. |
| Dodać Dashboard v2/nowy moduł lub przejść na Compose | Niska dla obecnej architektury | Średnia | Wysoki | Odrzucony: duplikowałby istniejący ekran i nie rozwiązuje nowego problemu granic. |
| Dodać system eventów gry razem z placeholderami | Niska, nadmierny zakres | Niska bez określenia kontraktu | Wysoki | Odrzucony: użytkownik chce teraz tylko niewidoczne anchory. |
| Zaimplementować niezależny magazyn języka w DashboardViewModel | Niska | Niska | Średni | Odrzucony: Dashboard konsumuje `SetLanguage` i nie tworzy drugiego źródła prawdy. |

## Odłożony zakres

- Dashboard nie jest właścicielem `LanguageSettings`, DataStore ani tłumaczeń;
  całość realizuje LocalizationSystem, z którego Dashboard korzysta przez
  `DashboardViewModel`.
- Okazjonalne eventy gry nie mają jeszcze właściciela, definicji ani źródła
  danych; sloty pozostają puste i ukryte.
- Specyfikacja przyszłych eventów i ich źródło pozostają poza tym zakresem.
