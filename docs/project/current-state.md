# Urkaaaz — bieżący stan funkcji, ograniczeń i decyzji

## Stan ogólny

Projekt działa jako modularna aplikacja Android z lokalnym, deterministycznym
meczem. Implementacja obejmuje kontrakty, domenę, symulację, aplikację,
kampanię, AI oraz działający match screen. Gałąź robocza: `develop`.

## Obecnie działające

- Start → dashboard → campaign → match screen przez lekką nawigację
  single-activity.
- Typowany wybór campaign level przez `game-campaign`; zwycięstwo kampanii
  zapisuje ukończenie poziomu i atomowo rozlicza nagrody.
- `CampaignChapterRegistry` definiuje dziesięć rozdziałów, ich biomy oraz
  fortress/boss landmarks. Campaign screen pokazuje mapę Chapter 1,
  postęp, rozdziały ukończone/bieżący/podgląd oraz znaczniki stanów poziomów;
  start wymaga zaznaczenia dostępnego poziomu i przycisku **START LEVEL**.
- Studio ma zakładkę Campaign do edycji pozycji X/Y wszystkich 10 markerów
  danego rozdziału. Wspólny JSON rozdziałów i osobny layout JSON są czytane
  przez Studio i grę; Chapter 2–10 są tylko do odczytu do czasu dodania map.
- Campaign i Loadout współdzielą `ScreenChromeMetrics` dla insetu ekranu
  (`4 dp`) oraz wymiarów i odstępu Back buttona; treść Loadout ma osobny
  wewnętrzny padding.
- Dashboard udostępnia potwierdzony reset lokalnego postępu gracza: kampanii,
  złota, odblokowań, stocków, zapisanych loadoutów i paragonów nagród.
- Shop jest ekranem natywnych kontrolek, dostępny z Dashboardu i Loadoutu.
  Używa dwóch kategorii `SPELLS`/`AMMUNITION`, kupuje pojedynczy stock za
  OrCoins i wraca do wybranego przedmiotu bez autoekwipowania.
- Dashboard używa zaakceptowanego układu, osobnego tła i istniejących assetów
  przycisków/ramki. Globus otwiera krótką listę flag PL/UK/ES; flagi nie
  zmieniają jeszcze języka. `AD REWARD` pozostaje nieaktywny, a dwa boczne
  sloty eventów są ukryte i nie mają jeszcze źródła danych.
- Lokalny przepływ:
  `MatchUiAction → MatchViewModel → LocalMatchGateway → MatchSession →
  simulation → MatchSnapshot`.
- Deterministyczna symulacja pocisków, terenu, kolizji, obrażeń, eventów,
  restartu i replay regression.
- SpellSystem ma działające runtime effects dla `FORTRESS_SHIELD`, HASTE i
  `RUNIC_BASTION`; Bastion jest jedną na drużynę, ma 360 HP, blokuje jednostki
  i pociski oraz odtwarza animacje creation, idle i destruction.
- Celowanie slingshot, ręczny FIRE, pan mapy i pinch-zoom.
- Timer meczu, wskaźnik kierunku/siły wiatru, pauza/wznowienie i restart.
- Dynamiczny wiatr z początkowym opóźnieniem, przejściem i kolejnymi zmianami.
- Wybór amunicji w popup gridzie; niedostępna/depleted amunicja jest
  przygaszona i zablokowana.
- Profile jednostek, amunicji, czarów, many, bossów i pozostałe parametry
  runtime pochodzą ze ścisłego `game-balance.json` schema 4. Aplikacyjny loader
  przekazuje jeden typowany `GameBalanceConfig` do symulacji, AI, kampanii,
  nagród, loadoutu i projekcji Androida.
- Studio Balance edytuje wszystkie istniejące pola schema 4, w tym nullable
  `shopCostGold`, przez zakładki i typowane kontrolki; surowy JSON nie jest
  prezentowany, a stabilne ID i struktura profili pozostają stałe. Walidacja i zapis używają istniejącego
  API z kontrolą SHA-256 i `expectedHash`.
- Dedykowane jednostki: Defender, Sapper, Demolisher, Raging Boar,
  Slingmaster; `UnitCatalog` tworzy ich definicje zachowań z profili JSON.
- `BossSystem` obsługuje central objectives oraz mobile siege bossy na
  poziomach 5–50. Profile legacy, w tym HP, obrażenia, prędkość, enrage i
  odporność na `PLAGUE_CAULDRON`, pochodzą z
  `game-domain/src/main/resources/balance/game-balance.json`; Studio ma zakładkę
  Balance i kategorię Bosses w Animations. Bossy renderują się statycznie;
  animacje są odrębnymi przyszłymi krokami.
- Kampania po zwycięstwie na poziomie 50 udostępnia zwykłe poziomy 51–54, ale
  blokuje poziomy 55–100 bez kompletnych profili bossów i assetów.
- Studio Progress edytuje developer preset z poziomem kampanii, liczbą żyć i
  złotem. Preset stosuje wyłącznie jawnie zaznaczone pola, jednorazowo w
  debug buildzie po rebuildzie i restarcie; liczba żyć jest wyświetlana, ale
  nie jest jeszcze konsumowana ani regenerowana przez mechanikę gry.
- Rekrutacja, supply, garrison, przesuwanie jednostek, fale i ruch w symulacji.
- Wspólny standard rozmiarów meczu: fortece mają 70% poprzedniej szerokości i
  hitboxu, a jednostki mają 3x poprzedni rozmiar prezentacji. Jednostki używają
  typowanych prostokątnych hitboxów obejmujących około 80% widocznego ciała.
- Ruch jednostek w marszu i podczas przegrupowania garnizonu używa globalnego
  mnożnika `2.0`.
- Interakcje jednostek: melee, ataki dystansowe i eksplozje Demolishera.
  Kontakt z jednostką powoduje natychmiastową detonację; przy froncie fortecy
  lub Bastionu Demolisher wykonuje rzut do przodu przez `0.5` sekundy,
  przesuwając się o `90` jednostek świata przed wybuchem. Saper i pozostałe
  jednostki atakują fortecę lub aktywny Bastion zgodnie ze swoimi profilami.
- AI używa typowanych profili `CADET`, `VETERAN` i `ELITE` z kampanii.
  Strategie katapulty/amunicji, rekrutacji i fal są składane przez
  `AiStrategyRegistry`; ELITE leaduje ruchome cele i kontruje zagrożenia.
  `AiMatchCoordinator` w aplikacji zarządza seedem i reakcją; postęp ELITE
  jest ograniczony do poziomu 100 i nie przyznaje bonusów zasobów ani statystyk.
  CADET ma błąd celowania ±18°/±30 mocy, aby jego strzały były wyraźnie mniej
  celne.
- Legacy-style sprite'y, animacje idle/walk, HP bary jednostek, cache assetów
  i zakotwiczenie stóp do wspólnej linii terenu.
- Dostępny bezpośrednio na ekranie meczu checkbox `Show hitboxes`; pokazuje
  prostokątne hitboxy jednostek oraz obrysy terenu, fortec, bastionów runicznych,
  bossów i pocisków. Panel `DEBUG` nadal udostępnia tryb `SNAPSHOT`.
- Hitbox fortecy jako wspólny profil prostokątów dla trafień bezpośrednich i
  obrażeń obszarowych; katapulty nie są celami obrażeń.
- Testy jednostkowe i integracyjne dla kontraktów, domeny, symulacji,
  aplikacji, AI oraz wybranych granic Android UI.
- Level Definition System (LDS) używa niemutowalnego kontraktu
  `MapDefinition`, ścisłego `MapDefinitionRepository` i typowanego
  `MapDefinitionValidator`. `CampaignLevelDefinition` nadal składa typowaną
  konfigurację gameplayową poziomu, natomiast geometria map kampanii jest
  ładowana z JSON schema 1, nie z per-level Kotlin `LevelAuthoringRequest`.
- Chapter 1 Levels 1–5 mają dedykowane JSON-y w `game-campaign` resources i
  `android-app` assets. Runtime wybiera plik `chapterN-levelX`, jeśli istnieje;
  poziomy bez dedykowanej mapy używają przejściowego `demo-map`. Geometria nie
  jest losowana podczas meczu, a definicje demo i kampanii korzystają ze
  wspólnego kontraktu oraz walidatora.
- Zapis mapy kampanii w Studio Maps wybiera Chapter i globalny numer Level,
  zapisuje identyfikator `chapterN-levelX` w obu kopiach JSON, a kampania
  ładuje mapę danego poziomu, gdy plik istnieje; brakujące mapy nadal używają
  tymczasowego `demo-map`.
- W standardowym świecie fortecy mają środki `x=600` i `x=6600`; katapulty
  są wyprowadzane odpowiednio w `x=490` i `x=6710`, a chronione fundamenty,
  launch/aim anchors oraz pozycje garnizonu zachowują relację do nowych środków.
- Studio Maps ma **Random Map** z suwakiem intensywności terenu 100–600
  world units (domyślnie 160) i presetami gęstości dekoracji. Losowanie
  zachowuje biom i Relics, zapisuje seed w metadata,
  jest odwracalne i pozostaje szkicem do czasu jawnego Save. Teren i dekoracje
  można edytować bez blokady fazowej; wysokość `Y` dekoracji jest niezależna
  od profilu terenu i może być ustawiana ręcznie, a cap ma wyłącznie
  przełącznik widoczności podglądu.

## Ustalony kierunek dalszego rozwoju

Kolejne większe funkcjonalności będą projektowane jako niezależne podsystemy,
z własną odpowiedzialnością, kontraktami, stanem, eventami, testami i
integracją z pozostałymi warstwami. Rozszerzenia AI, takie jak strategie
zaklęć, dołączają przez registry; `SpellSystem`, `TutorialSystem` i pełna
`CampaignSystem` pozostają kolejnymi obszarami. Decyzje AI należą do `game-ai`,
a lifecycle agenta do `game-application`; nazwa podsystemu nie oznacza
automatycznie nowego modułu Gradle. Podsystemy mają korzystać z komend,
snapshotów i eventów zamiast mutować bezpośrednio stan innych podsystemów.

## Ograniczenia i niedokończone obszary

- `LoadoutScreen` asynchronicznie odczytuje, waliduje i zapisuje loadout.
  `ResultScreen` pozostaje placeholderem.
- Chapter 1 ma zatwierdzoną mapę i assety campaign UI; mapy Chapter 2–10 nie
  są jeszcze dostępne, więc te rozdziały używają listy poziomów.
- Mechanika zużywania i regeneracji żyć pozostaje poza obecnym zakresem.
  Campaign progress, nagrody i lokalne zakupy/stock Shop są zapisywane w
  DataStore.
- Match result popup prezentuje rozliczoną nagrodę i pozwala przejść do
  następnego poziomu po zapisaniu ukończenia; przegrana i błąd zapisu mają
  osobne ścieżki retry/powrotu.
- Local profile i loadout są zapisywane przez DataStore. Shop rozlicza
  wykorzystany stock po wszystkich wynikach meczu oraz przed jawnym stopem lub
  restartem; przymusowe zamknięcie procesu przed zapisem pozostaje ograniczeniem.
- SpellSystem nie obejmuje jeszcze pozostałych ośmiu efektów katalogu ani
  castowania zaklęć przez AI; brakuje też tutoriala i wszystkich legacy
  widgetów HUD.
- Brak pełnej zgodności legacy dla wszystkich mechanik, garrisonu i fal,
  choć podstawowe jednostki i fale są już zaimplementowane.
- Brak pełnego legacy renderingu, części wizualizacji fortec/katapult,
  ostrzeżeń pocisku, komunikatów zaklęć, audio śmierci i wibracji trafienia.
- Brak sieci, logowania, matchmakingu i architektury serwerowej.
- Nawigacja jest celowo lekkim single-activity screen hostem; migracja do
  Android Navigation lub Compose jest możliwa, ale odroczona.

## Jawnie odroczone decyzje

1. Mapy i walidacja wizualnych anchorów dla rozdziałów kampanii 2–10 oraz
   system żyć pozostają odroczone.
2. Docelowa technologia nawigacji: obecny `AppNavigator` versus Android
   Navigation/Compose.
3. Zakres i właściciel przyszłego backendu/network play.
4. Pełna migracja tutoriala, pozostałych zaklęć i wyników; animacje bossów
   oraz zawartość bossów
   poziomów 55–100 są odłożone.
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
uwzględniać `docs/architecture/architecture.md` i odpowiednie testy graniczne.
