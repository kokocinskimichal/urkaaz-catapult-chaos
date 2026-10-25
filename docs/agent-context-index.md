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
| Kontrakty poziomów, teren lub authoring map | `docs/architecture/level-definition-architecture.md`; dla migracji map JSON również `docs/campaign/map-json-migration-plan.md` i `docs/campaign/map-json-implementation-prompt.md`. |
| Konkretne poziomy kampanii | `docs/campaign/maps/chapter1/README.md` i `docs/campaign/maps/chapter1/COMPARISON.md` tylko dla rozdziału 1; dla innych rozdziałów odczytaj README i dokumenty danego rozdziału, jeśli istnieją. |
| Edytor map | `docs/map-editor/design.md`; przy zmianach ryzyk, migracji lub webowej implementacji dołącz odpowiednio `docs/map-editor/risks.md`, `docs/map-editor/web-migration-plan.md`, `docs/map-editor/web-implementation-prompt.md` i `docs/map-editor/web/README.md`. |
| Urkaaaz Studio (narzędzie lokalne, wizualizator animacji, migracja edytora) | `docs/architecture/studio-architecture.md` i `studio/README.md`; dla zakładki map dołącz `docs/map-editor/web/README.md`. |
| Systemy gameplayowe lub prezentacyjne | Wczytaj dokładny projekt systemu z `docs/systems/` oraz jego prompt implementacyjny, jeśli istnieje. Dla interakcji jednostek użyj `docs/systems/units/interactions.md`; pomysły na jednostki czytaj tylko przy pracy nad propozycjami jednostek. |
| UI meczu, HUD lub ekran wyniku | `docs/ui/mechanics-gap-analysis.md` oraz odpowiedni dokument planu/promptu z `docs/systems/`, np. plan redesignu HUD-u albo popupu wyniku. |
| Migracja architektury lub etap migracji | `docs/migration/README.md`, `docs/migration/roadmap.md` i dokumenty tylko tego etapu (`mN-*.md`); raport etapu czytaj, gdy potrzebny jest jego wynik lub uzasadnienie. |
| Historia zmian lub stan refaktoryzacji | `docs/project/CHANGELOG.md` albo `docs/project/refactor-progress.md`, odpowiednio do pytania. |
| Zapis lub przegląd pomysłów | `docs/project/idea-inbox.md`; dla pomysłów przekrojowych również `docs/project/ideas.md`. |
| Nowe lub niejednoznaczne terminy domenowe | `docs/reference/GLOSSARY.md`. |

Pełny spis dokumentów i ich pozostałych sekcji znajduje się w `AGENTS.md`.
Nie wczytuj wszystkich dokumentów migracji, promptów ani systemów bez
powiązania z bieżącym zadaniem.
