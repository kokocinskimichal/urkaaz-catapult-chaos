# Urkaaaz — bieżący stan funkcji, ograniczeń i decyzji

## Stan ogólny

Projekt działa jako modularna aplikacja Android z lokalnym, deterministycznym
meczem. Implementacja obejmuje kontrakty, domenę, symulację, aplikację,
kampanię, AI oraz działający match screen. Gałąź robocza: `develop`.

## Obecnie działające

- Splash → dashboard → campaign → match screen przez lekką nawigację
  single-activity.
- Splash trwa około 2,5 sekundy, pokazuje dostarczone logo gry na tle
  pełnoekranowej grafiki oraz dokładne logo AFACODE jako osobny overlay
  wyśrodkowany pod logo gry, bez owalnej ramki ani tła.
  Pełnoekranowe tło jest zapisane
  w `assets_src/splash/splash_background.png` i załadowane jako runtime asset;
  prompt źródłowy znajduje się w `docs/systems/splash-screen-background.agp`.
- Typowany wybór campaign level przez `game-campaign`; zwycięstwo kampanii
  zapisuje ukończenie poziomu i atomowo rozlicza nagrody.
- `CampaignChapterRegistry` definiuje dziesięć rozdziałów, ich biomy oraz
  fortress/boss landmarks. Campaign screen pokazuje po jednej pełnoekranowej
  mapie rozdziału; między ukończonymi, bieżącym i najwyżej jednym następnym
  podglądem można przechodzić strzałkami lub gestem poziomym. HUD pokazuje
  tytuł, postęp i globalny numer rozdziału. Rozdziały 1–4 używają własnych map,
  a 5–10 tymczasowo mapy Green Frontier i jej kotwic jako fallbacku wyłącznie
  prezentacyjnego. Markery poziomów, ich statusy i autoryzacja Start Level
  nadal pochodzą z istniejącej progresji i `CampaignSystem`. Rozdziały 3–5
  mają kolejno biomy Copperwood, Ashen March i Sunken Marshes; ich zakresy
  poziomów i spotkania bossów pozostały bez zmian.
- Studio ma zakładkę Campaign do edycji pozycji X/Y wszystkich 10 markerów
  danego rozdziału. Wspólny JSON rozdziałów i osobny layout JSON są czytane
  przez Studio i grę; Chapters 5–10 są tylko do odczytu do czasu dodania map.
- Campaign i Loadout współdzielą `ScreenChromeMetrics` dla insetu ekranu
  (`4 dp`) oraz wymiarów i odstępu Back buttona; treść Loadout ma osobny
  wewnętrzny padding.
- Dashboard udostępnia potwierdzony reset lokalnego postępu gracza: kampanii,
  złota, odblokowań, stocków, zapisanych loadoutów i paragonów nagród.
- Shop jest ekranem natywnych kontrolek, dostępny z Dashboardu i Loadoutu.
  Używa dwóch kategorii `SPELLS`/`AMMUNITION`, kupuje pojedynczy stock za
  OrCoins i wraca do wybranego przedmiotu bez autoekwipowania.
- Dashboard używa zaakceptowanego układu, osobnego tła i istniejących assetów
  przycisków/ramki. Przycisk z flagą aktualnie wybranego języka otwiera
  selektor PL/UK/ES; wybór zmienia cały język UI, zapisuje się w osobnym
  DataStore i przetrwa reset profilu. Tytuły rozdziałów są mapowane z
  lokalizowanych zasobów, a Studio pokazuje stabilne ID. Tłumaczenia PL/ES
  zostały zaakceptowane, a stałe panele zweryfikowano wizualnie na emulatorze
  w trzech językach. `AD REWARD` pozostaje nieaktywny, a dwa boczne sloty
  eventów są ukryte i nie mają jeszcze źródła danych. `TEST MODE` otwiera
  picker grywalnych poziomów 1–54 pogrupowanych po rozdziałach, bez względu na
  postęp gracza. Loadout używa dostępności wybranego poziomu i przechowuje
  wybory tylko w pamięci; mecz używa rzeczywistego scenariusza, ma dostępne
  Debug Tools i nie zapisuje postępu, nagród ani użycia stocku.
- Interaktywne etykiety Androida mają osobne locale-specific rozmiary `sp`;
  Studio → Typography udostępnia 106 kontrolek w grupach Common i per ekran.
  Dashboardowy `TEST MODE` ma `14sp` EN, `11sp` PL i `9sp` ES; tłumaczenia
  ani wymiary kontrolek nie są edytowane.
- Lokalny przepływ:
  `MatchUiAction → MatchViewModel → LocalMatchGateway → MatchSession →
  simulation → MatchSnapshot`.
- Deterministyczna symulacja pocisków, terenu, kolizji, obrażeń, eventów,
  restartu i replay regression.
- TutorialSystem v1 ma deterministyczny engine i JSON `match.basics`, trwałe
  terminal statuses w `tutorial.*`, Android Hint/Spotlight z target registry,
  pauzę ticków bez zmiany replayu, AI suspension oraz Studio Tutorials editor.
  Tutorial uruchamia się automatycznie zgodnie z triggerem; Dashboard nie ma
  listy ani kontrolki powtórki. Dynamiczne targety przenoszą konkretny
  `targetInstanceId`; teksty `match.basics` PL/EN/ES są do akceptacji. Pozostałe
  tutorial content definitions pozostają poza v1 do czasu akceptacji. Hint ma
  maksymalnie 280 dp szerokości, korzysta z `match_hud_universal_frame` i
  istniejących assetów przycisków; `AUTO` centruje go w szerszym bocznym
  obszarze z odstępem 24 dp, a nad/pod targetem umieszcza go tylko jako fallback.
  Zaakceptowane `SELECT_AMMUNITION`, `DEPLOY_UNIT` i `SEND_WAVE` są obserwowane
  jako akcje gracza; Studio filtruje step targets według ekranu, a serwer
  waliduje ich zgodność z triggerem i scope.
- Pociski przeciwnych drużyn zderzają się podczas lotu, używając sumy
  amunicyjnych promieni kolizji. Kontakt usuwa oba pociski bez impactu,
  obrażeń ani deformacji terenu; pociski sojusznicze nie kolidują.
- SpellSystem ma działające runtime effects dla `FORTRESS_SHIELD`, HASTE i
  `RUNIC_BASTION`; Bastion jest jedną na drużynę, ma 360 HP, blokuje jednostki
  i pociski oraz odtwarza animacje creation, idle i destruction.
- Celowanie slingshot, ręczny FIRE, pan mapy i pinch-zoom.
- Timer meczu, wskaźnik kierunku/siły wiatru, pauza/wznowienie i restart.
- Dynamiczny wiatr z początkowym opóźnieniem, przejściem i kolejnymi zmianami.
- Wybór amunicji w popup gridzie; niedostępna/depleted amunicja jest
  przygaszona i zablokowana.
- Profile jednostek, amunicji, czarów, many, bossów i pozostałe parametry
  runtime pochodzą ze ścisłego `game-balance.json` schema 8. Aplikacyjny loader
  przekazuje jeden typowany `GameBalanceConfig` do symulacji, AI, kampanii,
  nagród, loadoutu i projekcji Androida.
- Campaign Balance może nadpisywać maksymalne HP fortec BLUE i RED niezależnie
  per globalny poziom 1–100. Brak nadpisania dziedziczy standardową wartość
  z balansu; Studio edytuje sparse overrides w Runtime → Campaign. Demo
  zachowuje osobne 5000 HP BLUE i 500 HP RED. Efektywne wartości trafiają do
  scenariusza/session, SimulationConfig, snapshotu i konfiguracji replaya.
- `runtime.campaign.contentAvailability` konfiguruje pierwsze poziomy
  dostępności wszystkich kanonicznych jednostek, typów amunicji i czarów.
  `CampaignLevelDefinition` oraz dostępność Haste/Heal w milestone registry
  korzystają z tych samych progów; nagrody pozostają za poziomy 5 i 7.
  Progi nie zmieniają entitlementów, efektów czarów ani zakupów w Shop.
- `runtime.combat.gameplaySpeedMultiplier` ma zakres `1.0–1.5` i wartość
  domyślną `1.2`; skaluje ruch jednostek/mobilego bossa oraz czas lotu
  pocisków i odłamków, nie zegary meczu, przeładowania ani cooldowny.
- Studio Balance edytuje wszystkie istniejące pola schema 8, w tym nullable
  `shopCostGold`, mnożnik tempa, per-level fortress HP i progi dostępności
  jednostek, amunicji oraz czarów, przez zakładki i typowane kontrolki; surowy
  JSON nie jest prezentowany, a stabilne ID i struktura profili pozostają
  stałe. Walidacja i zapis używają istniejącego API z kontrolą SHA-256 i
  `expectedHash`.
- AI RED rozwiązuje strzały do żywych central objectives z trajektorią
  uwzględniającą balistykę, wiatr i teren. AI sytuacyjnie używa HASTE,
  FORTRESS_SHIELD i RUNIC_BASTION; stock specjalnych czarów RED jest
  balance-driven i odseparowany od inventory BLUE.
- Dedykowane jednostki: Defender, Sapper, Demolisher, Raging Boar,
  Slingmaster; `UnitCatalog` tworzy ich definicje zachowań z profili JSON.
- `BossSystem` obsługuje central objectives oraz mobile siege bossy na
  poziomach 5–50. Profile legacy, w tym HP, obrażenia, prędkość, enrage i
  odporność na `PLAGUE_CAULDRON`, pochodzą z
  `game-domain/src/main/resources/balance/game-balance.json`; Studio ma zakładkę
  Balance i kategorię Bosses w Animations. Ancient War Oak ma 75-klatkową
  pętlę idle, 60-klatkowe ataki w lewo i prawo oraz 60-klatkową animację
  śmierci. Po pokonaniu dowolnego bossa kamera centruje się na nim z łagodnym
  zoomem; podsumowanie poziomu czeka na koniec animacji lub 4-sekundowego holda
  statycznej grafiki dla bossów bez własnej sekwencji śmierci. Pozostałe
  animacje bossów są odrębnymi przyszłymi krokami.
- Kampania po zwycięstwie na poziomie 50 udostępnia zwykłe poziomy 51–54, ale
  blokuje poziomy 55–100 bez kompletnych profili bossów i assetów.
- Studio Progress edytuje developer preset z poziomem kampanii, liczbą żyć i
  złotem. Preset stosuje wyłącznie jawnie zaznaczone pola, jednorazowo w
  debug buildzie po rebuildzie i restarcie. Jawnie ustawione życia synchronizują
  timer i granty LifeSystemu; niezaznaczone wartości profilu pozostają bez zmian.
  Bundled preset jest obecnie jednorazowo ustawiony na poziom 1 i 10 000 złota
  do weryfikacji tutoriala.
- LifeSystem rozpoczyna nowy profil z 5/5 żyć; każdy kampanijny wynik poza
  `BLUE_WIN` zużywa jedno życie, a jedno życie regeneruje się co 15 minut
  lokalnego czasu ściennego, także offline. Zegar chroni przed cofnięciem czasu;
  przyspieszenie wall-clock do przodu pozostaje ograniczeniem trybu lokalnego.
  Skończone i czasowe unlimited granty są idempotentne i współdzielą profile
  DataStore, ale Ad Reward i event sources nie są jeszcze podłączone.
- Campaign pozostaje przeglądalna przy 0 żyć, lecz CTA i aplikacyjny start
  poziomu są zablokowane; aktywny unlimited grant omija blokadę. Dashboard
  pokazuje countdown / `N/A` / `∞`, a nieudane rozliczenie porażki wymaga retry
  i blokuje opuszczenie popupu wyniku.
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
  Prosta rekrutacja wybiera najmniej reprezentowany, dostępny i osiągalny
  supply'owo typ jednostki. Wszystkie profile grupują minimum trzy jednostki
  garnizonowe przed wysłaniem zwykłej fali; counterplay może wysłać mniejszą
  grupę pod presją. Gotowość wynika z jawnego stanu garnizonu, niezależnego od
  chwilowego ruchu podczas przegrupowania.
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
- Chapter 1 Levels 1–10 mają dedykowane JSON-y w `game-campaign` resources i
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
zaklęć, dołączają przez registry; `TutorialSystem` v1 jest zaimplementowany
przez `game-application`, `android-app` i Studio. Katalog zawiera wyłącznie
`match.basics`; pozostałe teksty/kroki czekają na osobną akceptację.
`SpellSystem` i pełna `CampaignSystem` pozostają obszarami dalszego rozwoju.
Decyzje AI należą do `game-ai`, a lifecycle agenta do `game-application`;
nazwa podsystemu nie oznacza
automatycznie nowego modułu Gradle. Podsystemy mają korzystać z komend,
snapshotów i eventów zamiast mutować bezpośrednio stan innych podsystemów.

## Ograniczenia i niedokończone obszary

- `LoadoutScreen` asynchronicznie odczytuje, waliduje i zapisuje loadout.
  `ResultScreen` pozostaje placeholderem.
- Chapters 1–4 mają zatwierdzone mapy; mapy Chapter 5–10 nie są jeszcze
  dostępne. CampaignScreen pokazuje dla nich tymczasowo Green Frontier, ale
  Studio nadal oznacza brak autorskich layoutów jako brak mapy.
- Ad Reward, eventowy grant życia, backend i synchronizacja cloud nie są
  zaimplementowane. Wymuszone zamknięcie procesu przed zapisem wyniku może
  utracić zdarzenie; lokalny zegar nie chroni przed ręcznym przyspieszeniem
  czasu do przodu. Campaign progress, życie, nagrody i lokalne zakupy/stock
  Shop są zapisywane w istniejącym DataStore.
- Match result popup prezentuje rozliczoną nagrodę i pozwala przejść do
  następnego poziomu po zapisaniu ukończenia; przegrana i błąd zapisu mają
  osobne ścieżki retry/powrotu.
- Local profile i loadout są zapisywane przez DataStore. Shop rozlicza
  wykorzystany stock po wszystkich wynikach meczu oraz przed jawnym stopem lub
  restartem; przymusowe zamknięcie procesu przed zapisem pozostaje ograniczeniem.
- SpellSystem nie obejmuje jeszcze pozostałych ośmiu efektów katalogu ani
  castowania zaklęć przez AI. TutorialSystem v1 działa dla `match.basics`;
  screen tutorials i tutoriale pozostałych mechanik nie są jeszcze częścią
  zaakceptowanej treści. Legacy widgety HUD pozostają poza zakresem.
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
4. Treści TutorialSystemu poza `match.basics`, pozostałe zaklęcia i wyniki;
   dodatkowe animacje bossów oraz zawartość bossów poziomów 55–100 są odłożone.
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
