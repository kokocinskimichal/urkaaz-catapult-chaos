# Brakujące mechaniki UI

Porównanie aktualnego UI projektu `urkaaz` z mechanikami obecnymi w `orc-catapult`.

| Nr | Obszar | Mechanika w `orc-catapult` | Stan w `urkaaz` | Lokalizacja w starym repo | Priorytet |
|---:|---|---|---|---|---|
| 1 | Fale | Wysłanie zgromadzonych jednostek jako fali | Brak wysyłania fal | `MainActivity.kt:1410-1414` | P0 |
| 2 | Jednostki | Przyciski Sapera, Defendera, Demolishera i goblinów | Brak przycisków jednostek | `MainActivity.kt:1370-1435` | P0 |
| 4 | Kampania | Rozdziały, poziomy, blokady, odblokowanie i ukończenie poziomów | Brak ekranowej kampanii i progresji | `MainActivity.kt:704-720`, `MainActivity.kt:1086-1143` | P0 |
| 5 | Loadout | Wybór jednostek, amunicji i zaklęć przed poziomem | Brak osobnego ekranu przygotowania | `MainActivity.kt:1949-2313` | P0 |
| 6 | Mana | Okrągły wskaźnik many i blokowanie zaklęć przy braku zasobu | Brak many | `ui/ManaGaugeView.kt`, `MainActivity.kt:2860-3110` | P0 |
| 7 | Nawigacja | Ekran startowy, dashboard, kampania, sklep i loadout | Brak; aplikacja uruchamia od razu bitwę | `ui/MainActivity.kt`, `ui/UiControllers.kt` | P0 |
| 8 | Supply | Okrągły wskaźnik Supply, koszt jednostek, blokady i opisy | Brak Supply i wskaźnika zasobów | `ui/SupplyGaugeView.kt`, `MainActivity.kt:2860-2930` | P0 |
| 9 | Tutorial | Przyciemnienie ekranu z wycięciem wokół wskazanego elementu | Brak | `ui/TutorialHighlightView.kt` | P0 |
| 10 | Tutorial | Dymek instrukcji, `NEXT` / `START` i automatyczne pozycjonowanie | Brak | `tutorial/TutorialOverlayController.kt`, `MainActivity.kt:1612-1938` | P0 |
| 11 | Tutorial | Tutorial zależny od poziomu i blokowanie paneli do czasu instrukcji | Brak | `MainActivity.kt:1612-1938` | P0 |
| 12 | Wynik poziomu | Nagroda w złocie, zapis ukończenia i przycisk następnego poziomu | Brak | `MainActivity.kt:2910-2960` | P0 |
| 13 | Zaklęcia | Przyciski zaklęć z kosztami many, stanem aktywności i blokadami | Brak UI zaklęć | `MainActivity.kt:1430-1490`, `MainActivity.kt:2960-3110` | P0 |
| 14 | Boss | HUD fazy marszu i zdrowia Siege Sovereign | Brak | `GameView.kt:1175-1188` | P1 |
| 15 | Cel centralny | Panel z nazwą celu, HP, maksymalnym HP i obrażeniami obu drużyn | Brak | `GameView.kt:1991-2235` | P1 |
| 16 | Forteca | Teksturowane i gradientowe paski HP | Są tylko tekstowe wartości HP | `ui/FortressHealthBarView.kt`, `MainActivity.kt:2808-2835` | P1 |
| 17 | HUD bitwy | Timer poziomu `MM:SS` | Brak timera | `MainActivity.kt:2810-2850` | P1 |
| 18 | HUD bitwy | Ikona kierunku i siły wiatru z kilkoma poziomami intensywności | Tylko tekst `WIND` | `MainActivity.kt:2830-2860` | P1 |
| 19 | HUD bitwy | Ostrzeżenie o nadlatującym pocisku | Brak ostrzeżenia | `MainActivity.kt:2860-2890` | P1 |
| 20 | HUD bitwy | Komunikat o aktywowanym zaklęciu | Brak | `MainActivity.kt:2860-2890` | P1 |
| 21 | Jednostki | Paski HP jednostek i wizualne stany `ARMING` / `DEAD` | Brak jednostek i ich HUD-u | `GameView.kt:1431-1737` | P1 |
| 22 | Kamera | Kadrowanie poziomu przy starcie na fortecę gracza | Kamera jest statyczna | `GameView.kt:840-888` | P1 |
| 22 | Kamera | Ustawianie widoku na cel tutorialu | Brak | `GameView.kt:709-731` | P1 |
| 23 | Katapulta | Okrągły wskaźnik reloadu `READY` / sekundy | Jest tylko tekstowy reload w HUD | `GameView.kt:2621-2700` | P1 |
| 24 | Katapulta | Pasek HP katapulty i tekst `HP x/100` | Brak | `GameView.kt:2621-2700` | P1 |
| 25 | Lifecycle | Obsługa Back/Escape i powrót między ekranami | Brak ekranowej nawigacji | `MainActivity.kt:677-702` | P1 |
| 26 | Sklep | Sklep zaklęć i specjalnej amunicji | Brak | `MainActivity.kt:723-984` | P1 |
| 27 | Sklep | Szczegóły przedmiotu, cena, stan posiadania i zakup | Brak | `MainActivity.kt:762-975` | P1 |
| 28 | Tutorial | Tryb ćwiczeń wymagający oddania kilku strzałów | Brak | `MainActivity.kt:1917-1938` | P1 |
| 29 | Wizualizacja | Tarcze fortec, lodowe pułapki i Runic Bastion z HP | Brak | `GameView.kt:1362-1430`, `GameView.kt:1773-1840` | P1 |
| 30 | Wizualizacja | Animacje idle i śmierci jednostek | Brak | `GameView.kt:1431-1737` | P1 |
| 31 | Wynik poziomu | Panel zwycięstwa/przegranej z animowanym pojawieniem | Jest prosty overlay tekstowy | `MainActivity.kt:2910-2960` | P1 |
| 32 | Złoto | Nagrody za poziomy i saldo gracza | Brak | `MainActivity.kt:2910-2960`, `refreshDashboard()` | P1 |
| 33 | Życia | Liczba żyć, utrata życia po porażce i czas do kolejnego życia | Brak | `MainActivity.kt:1024-1085`, `refreshDashboard()` | P1 |
| 34 | Awatar | Wybór i zapis awatara gracza | Brak | `MainActivity.kt:985-1017` | P2 |
| 35 | Debug | Panel debugowy, przełącznik hitboxów i wybór jednostek AI | Brak | `MainActivity.kt:1388-1490` | P2 |
| 36 | Dostępność | Dynamiczne `contentDescription` dla zasobów, jednostek i zaklęć | Częściowo tylko amunicja | `MainActivity.kt:2960-3110` | P2 |
| 37 | Feedback | Dźwięk śmierci Defendera | Brak | `MainActivity.kt:350-365`, `announceDefenderDeaths()` | P2 |
| 38 | Feedback | Wibracja przy nowym trafieniu | Brak | `MainActivity.kt:2808-2810`, `vibrateForNewImpact()` | P2 |
| 39 | Lifecycle | Zachowanie trybu chapter-shot po odtworzeniu Activity | Brak | `MainActivity.kt:672-676` | P2 |
| 40 | Lifecycle | Wymuszenie orientacji landscape i wybór języka | Brak | `MainActivity.kt:338-350` | P2 |
| 41 | Reset progresji | Dialog resetowania żyć i całego postępu | Brak | `MainActivity.kt:1024-1058` | P2 |
| 42 | Snapshot | Tryb czystego screenshotu z ukrytym HUD-em | Brak | `MainActivity.kt:1492-1508`, `hideChapterShotHud()` | P2 |
| 43 | Wizualizacja | Pęknięcia uszkodzonej katapulty | Brak | `GameView.kt:2541-2620` | P2 |

## Obecne już w `urkaaz`

| Mechanika | Stan |
|---|---|
| Celowanie katapultą gestem slingshot | Obecne |
| Ręczne przesuwanie powiększonej mapy | Obecne |
| Pinch-zoom | Obecne, wymaga dalszego dopracowania UX |
| Wybór amunicji | Obecny |
| Liczby ograniczonej amunicji | Obecne |
| Różne animacje lotu i trafienia | Obecne |
| HP fortec | Obecne w uproszczonej formie |
| Tekstowy reload | Obecny |
| Tekstowy wiatr | Obecny |
| Pauza i wznowienie | Obecne |
| Restart bitwy | Obecny |
| Ręczne FIRE | Obecne |
| Overlay wyniku bitwy | Obecny w uproszczonej formie |
| Pętla symulacji i podstawowe decyzje AI | Obecne |
