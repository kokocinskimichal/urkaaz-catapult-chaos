# Urkaaaz — bieżący stan funkcji, ograniczeń i decyzji

## Stan ogólny

Projekt działa jako modularna aplikacja Android z lokalnym, deterministycznym
meczem. Implementacja obejmuje kontrakty, domenę, symulację, aplikację,
kampanię, AI oraz działający match screen. Gałąź robocza: `develop`.

## Obecnie działające

- Start → dashboard → campaign → match screen przez lekką nawigację
  single-activity.
- Typowany wybór campaign level przez `game-campaign`.
- Lokalny przepływ:
  `MatchUiAction → MatchViewModel → LocalMatchGateway → MatchSession →
  simulation → MatchSnapshot`.
- Deterministyczna symulacja pocisków, terenu, kolizji, obrażeń, eventów,
  restartu i replay regression.
- Celowanie slingshot, ręczny FIRE, pan mapy i pinch-zoom.
- Timer meczu, wskaźnik kierunku/siły wiatru, pauza/wznowienie i restart.
- Dynamiczny wiatr z początkowym opóźnieniem, przejściem i kolejnymi zmianami.
- Wybór amunicji w popup gridzie; niedostępna/depleted amunicja jest
  przygaszona i zablokowana.
- `AmmunitionCatalog` jako wspólna definicja dla gracza, AI i symulacji.
- Dedykowane jednostki: Defender, Sapper, Demolisher, Raging Boar,
  Slingmaster; balans w `UnitConfigurations.kt`.
- Rekrutacja, supply, garrison, przesuwanie jednostek, fale i ruch w symulacji.
- Wspólny standard rozmiarów meczu: fortece mają 70% poprzedniej szerokości i
  hitboxu, a jednostki mają 3x poprzedni rozmiar prezentacji; mechanika
  separacji i kolizji jednostek pozostaje stabilna.
- Ruch jednostek w marszu i podczas przegrupowania garnizonu używa globalnego
  mnożnika `2.0`.
- Interakcje jednostek: melee, ataki dystansowe, detonacja Demolishera i atak
  Sapera na fortecę.
- AI rekrutuje jednostki, wysyła fale, dobiera kontrę Sapera i zmienia
  temperament oraz próg fali.
- Legacy-style sprite'y, animacje idle/walk, HP bary jednostek, cache assetów
  i zakotwiczenie stóp do wspólnej linii terenu.
- Debug panel z `Show hitboxes` i trybem `SNAPSHOT`.
- Hitbox fortecy jako wspólny profil prostokątów dla trafień bezpośrednich i
  obrażeń obszarowych; katapulty nie są celami obrażeń.
- Testy jednostkowe i integracyjne dla kontraktów, domeny, symulacji,
  aplikacji, AI oraz wybranych granic Android UI.
- Level Definition System (LDS) jest zaimplementowany w kontraktach,
  `game-campaign`, symulacji i mapperze Androida. Obejmuje niemutowalne
  `MapDefinition`, builder authoringu, typowany `MapDefinitionValidator`,
  registry kampanii, deterministyczny profil terenu, placementy fortec,
  katapult i obiektów oraz `InitialCameraAnchor`.
- Każda materializowana definicja znajduje się bezpośrednio w swoim pliku
  `ChapterXLevelY.kt` jako własny `LevelAuthoringRequest`, z jawnymi próbkami
  terenu, placementami i metadanymi. Dane przechodzą przez
  `LevelDefinitionBuilder`, `CampaignLevelDefinition` i `MatchScenario`, a
  symulacja nie losuje geometrii runtime.
- Wszystkie 100 poziomów kampanii mają osobne typowane pliki Kotlin
  pogrupowane w chapterach 1–10 oraz jawny `CampaignMapDefinitions` index.
  `DemoLevelDefinitions.default` jest niezależną od kampanii mapą `demo-map`,
  która korzysta z tych samych kontraktów i produkcyjnego validatora LDS.
  Poziomy kampanii mają deterministycznie różne profile terenu, wysokości
  fundamentów i kotwice obiektów; strefy fortec pozostają płaskie i chronione.
  Profile terenu są zapisane jawnie w plikach poziomów; szerokie, gładkie
  wzgórza i doliny zostały zmaterializowane podczas authoringu przy limicie
  40 world units między sąsiednimi próbkami.

## Ograniczenia i niedokończone obszary

- `LoadoutScreen`, `ShopScreen` i `ResultScreen` pozostają placeholderami.
- Dashboard i campaign mają działającą nawigację, ale nie pełną funkcjonalność
  produkcyjnej progresji.
- Wszystkie 100 poziomów kampanii jest tymczasowo odblokowane dla emulatora;
  brak trwałego repozytorium progresji i produkcyjnych reguł unlock.
- Wynik meczu jest uproszczonym overlayem; brakuje pełnego panelu nagród,
  zapisu ukończenia, następnego poziomu, złota i żyć.
- Brak pełnego loadoutu jednostek, zaklęć i amunicji przed poziomem.
- Brak pełnego systemu many, zaklęć, tutoriala, bossów, centralnego celu i
  wszystkich legacy widgetów HUD.
- Brak pełnej zgodności legacy dla wszystkich mechanik, garrisonu i fal,
  choć podstawowe jednostki i fale są już zaimplementowane.
- Brak pełnego legacy renderingu, części wizualizacji fortec/katapult,
  ostrzeżeń pocisku, komunikatów zaklęć, audio śmierci i wibracji trafienia.
- Brak sieci, logowania, matchmakingu i architektury serwerowej.
- Nawigacja jest celowo lekkim single-activity screen hostem; migracja do
  Android Navigation lub Compose jest możliwa, ale odroczona.

## Jawnie odroczone decyzje

1. Produkcyjny model persystencji kampanii, odblokowań, złota i żyć.
2. Docelowa technologia nawigacji: obecny `AppNavigator` versus Android
   Navigation/Compose.
3. Zakres i właściciel przyszłego backendu/network play.
4. Pełna migracja tutoriala, sklepu, loadoutu, zaklęć, bossów i wyników.
5. Zakres zgodności wizualnej i regresyjnej ze starą aplikacją poza obecnym
   deterministycznym wycinkiem M11.
6. Finalna polityka assetów/audio dla elementów, których status lub źródło
   nie jest jeszcze zamknięte.

## Stan etapów migracji

- M0 — inwentaryzacja starego projektu: wykonane.
- M1 — nowy projekt i granice modułów: zaakceptowane.
- M2 — kontrakty: wykonane.
- M3 — domena: wykonane.
- M4 — deterministyczna symulacja: wykonana.
- M5 — kampania: wykonana.
- M6 — AI: wykonane.
- M7/M8 — inwentaryzacja i kopiowanie assetów: wykonane dla obecnego zakresu.
- M9 — Android UI: wykonane dla obecnego match screen i nawigacji.
- M10 — integracja lokalna: wykonana.
- M11 — porównanie regresyjne: wykonany deterministyczny wycinek; pełna
  zgodność legacy nie jest celem bieżącego zakresu.
- M12 — usuwanie starej architektury: pozostaje etapem przyszłym.

## Dowody i polecenia

Standardowa bramka projektu:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

`SimulationReplayRegressionTest` uruchamia tę samą sekwencję komend i ticków
dwukrotnie oraz porównuje snapshoty i eventy. Zmiany Android UI powinny
uwzględniać `architecture.md` i odpowiednie testy graniczne.
