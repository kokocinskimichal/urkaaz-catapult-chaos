# Indeks dokumentów kontekstu agenta

Indeks jest używany przez komendę `/loadContext`. Rozdziela dokumenty
potrzebne do zrozumienia projektu od materiałów, które należy wczytać tylko
przy konkretnym zadaniu. Czytaj aktualną treść plików z repozytorium; ten
indeks wskazuje źródła prawdy, ale ich nie zastępuje.

## Zestaw bazowy

Przy każdym uruchomieniu `/loadContext` przeczytaj wszystkie poniższe pliki:

| Plik | Co zawiera |
|---|---|
| `AGENTS.md` | Zakres projektu, zasady pracy, granice architektury i pełny indeks dokumentacji. |
| `docs/architecture/architecture.md` | Architektura, moduły, kontrakty i kierunek zależności. |
| `docs/architecture/implementation-rules.md` | Reguły implementacji, jakości, testowania i walidacji. |
| `docs/architecture/project-map.md` | Mapa modułów, przepływów, ekranów i kluczowych plików. |
| `docs/project/current-state.md` | Aktualne funkcje, ograniczenia oraz odłożone decyzje. |
| `docs/project/knowledge.md` | Trwała wiedza implementacyjna i ważne szczegóły kodu. |

Jeśli plik jest za długi, aby wczytać go w jednym odczycie, przeczytaj go
kompletnie w kolejnych zakresach. Nie traktuj skróconego lub uciętego wyniku
odczytu jako pełnej lektury.

## Dokumenty dobierane do zadania

Wybierz najwęższy pasujący zestaw na podstawie prośby użytkownika. Jeśli
zadanie obejmuje kilka obszarów, wczytaj dokumenty dla każdego z nich.

| Obszar zadania | Dokumenty do wczytania |
|---|---|
| AI bossów i używanie czarów przez AI | `docs/systems/ai-boss-spell-improvement-plan.md` i `docs/systems/ai-boss-spell-improvement-implementation-prompt.md`. |
| Kontrakty poziomów, teren lub authoring map | `docs/architecture/level-definition-architecture.md`; dla migracji map JSON również `docs/campaign/map-json-migration-plan.md` i `docs/campaign/map-json-implementation-prompt.md`. |
| Przeimplementowanie ekranu kampanii / karuzela map | `docs/systems/campaign-map-carousel-reimplementation-plan.md`, `docs/systems/campaign-map-carousel-implementation-prompt.md`, `docs/systems/campaign-map-presentation.md` i `docs/systems/campaign-system.md`. |
| Konkretne poziomy kampanii | `docs/campaign/maps/chapter1/README.md` i `docs/campaign/maps/chapter1/COMPARISON.md` tylko dla rozdziału 1; dla innych rozdziałów odczytaj README i dokumenty danego rozdziału, jeśli istnieją. |
| Edytor map | `docs/map-editor/design.md`; dla generatora Random Map dołącz `docs/map-editor/random-map-generation-plan.md` i `docs/map-editor/random-map-generation-implementation-prompt.md`; przy zmianach ryzyk, migracji lub webowej implementacji dołącz odpowiednio `docs/map-editor/risks.md`, `docs/map-editor/web-migration-plan.md`, `docs/map-editor/web-implementation-prompt.md` i `docs/map-editor/web/README.md`. |
| Wydajność renderowania, bitmapy i animacje battlefield | `docs/performance/battlefield-rendering-optimization-plan.md` oraz `docs/performance/battlefield-rendering-implementation-prompt.md`; dla brakujących sprite'ów przy asynchronicznym ładowaniu `docs/performance/unit-animation-fallback-plan.md` i `docs/performance/unit-animation-fallback-implementation-prompt.md`. |
| Urkaaaz Studio (narzędzie lokalne, wizualizator animacji, migracja edytora) | `docs/architecture/studio-architecture.md` i `studio/README.md`; dla zakładki map dołącz `docs/map-editor/web/README.md`; dla formularza balansu dołącz `docs/architecture/studio-balance-form-implementation-plan.md` i `docs/architecture/studio-balance-form-implementation-prompt.md`. |
| Systemy gameplayowe lub prezentacyjne | Wczytaj dokładny projekt systemu z `docs/systems/` oraz jego prompt implementacyjny, jeśli istnieje. Dla ShopSystemu użyj `docs/systems/shop-system.md` i `docs/systems/shop-system-implementation-prompt.md`; dla interakcji jednostek użyj `docs/systems/units/interactions.md`; dla runtime'owej tekstury stroju użyj `docs/systems/team-color-animation-plan.md`; dla nagród kampanii użyj `docs/systems/reward-system.md` i `docs/systems/reward-system-implementation-plan.md`; pomysły na jednostki czytaj tylko przy pracy nad propozycjami jednostek; dla tutoriali i onboardingu użyj `docs/systems/tutorial-system.md` i `docs/systems/tutorial-system-implementation-prompt.md`. |
| LifeSystem | `docs/systems/life-system.md` i `docs/systems/life-system-implementation-prompt.md`. |
| Animacje amunicji | `docs/systems/ammunition-design.md` oraz odpowiedni brief animacji, np. `docs/systems/siege-bomb-animation-refresh.agp`; nie zmieniaj gameplayu przez samą wymianę klatek. |
| LocalizationSystem Androida (PL/EN/ES) | `docs/systems/localization-system.md` i `docs/systems/localization-system-implementation-prompt.md`; przed zmianą lokalizacji lub języka wczytaj oba. |
| Nowa grafika bossa finałowego Chapter 1 | `docs/systems/green-frontier-chapter1-final-boss-art.agp`; dotychczasowy Thorncrown jest kontekstem technicznym, nie wzorcem projektu postaci. |
| UI meczu, HUD lub ekran wyniku | `docs/ui/mechanics-gap-analysis.md` oraz odpowiedni dokument planu/promptu z `docs/systems/`, np. plan redesignu HUD-u albo popupu wyniku. |
| Globalne tempo ruchu jednostek i pocisków | `docs/systems/gameplay-tempo-multiplier-plan.md` i `docs/systems/gameplay-tempo-multiplier-implementation-prompt.md`. |
| Dashboard UI | `docs/systems/dashboard-screen.md` i `docs/systems/dashboard-screen-implementation-prompt.md`. |
| Koncepcja ekranu Ad Reward | `docs/systems/ad-reward-screen-mockup.agp`; nagroda jest planowana jako dodatkowe Life, ale koncept nie oznacza aktywnej integracji reklam. |
| Splash screen, runiczne tło lub branding firmy | `docs/systems/splash-screen-background.agp`; dokładne logo firmy znajduje się w `assets_src/splash/afacode_company_logo.png` i jest osobnym overlayem, nie częścią wygenerowanego tła. |
| Shop UI lub jego wizualny koncept | `docs/systems/shop-system.md`, `docs/systems/shop-system-implementation-prompt.md`, `docs/systems/shop-screen-mockup.agp` oraz `assets_src/shop/screen-concepts/shop_ui_reference.png`; referencja służy do kompozycji, a AGP jest briefem koncepcji, nie implementacją ekranu ani runtime assetem. |
| Promocyjny landing page Urkaaaz | `docs/systems/urkaaaz-website-landing-page.agp`; dołącz zaakceptowany screenshot kampanii oraz wskazane w AGP assety gry i logo wydawcy. |
| Migracja balansu do JSON | `docs/migration/game-balance-json-expansion-plan.md`. |
| Migracja architektury lub etap migracji | `docs/migration/README.md`, `docs/migration/roadmap.md` i dokumenty tylko tego etapu (`mN-*.md`); raport etapu czytaj, gdy potrzebny jest jego wynik lub uzasadnienie. |
| Historia zmian lub stan refaktoryzacji | `docs/project/CHANGELOG.md` albo `docs/project/refactor-progress.md`, odpowiednio do pytania. |
| Zapis lub przegląd pomysłów | `docs/project/idea-inbox.md`; dla pomysłów przekrojowych również `docs/project/ideas.md`. |
| Nowe lub niejednoznaczne terminy domenowe | `docs/reference/GLOSSARY.md`. |

Pełny spis dokumentów i ich pozostałych sekcji znajduje się w `AGENTS.md`.
Nie wczytuj wszystkich dokumentów migracji, promptów ani systemów bez
powiązania z bieżącym zadaniem.
