# Urkaaaz Studio — architektura

Status: **kroki 1–6 wdrożone** (szkielet, wizualizator animacji, indeks assetów,
edytor map gameplayowych oraz edytor map kampanii w `studio/`; patrz
`studio/README.md`). Studio to lokalne narzędzie deweloperskie, nie część gry.

Wdrożone zakładki: **Animacje**, **Mapy** i **Campaign**. Zakładka **Balans**
pozostaje odroczona (sekcja 11).

## 1. Cel i zakres

Urkaaaz Studio to jedna aplikacja webowa z zakładkami, która zastępuje
rozproszone narzędzia pomocnicze:

| Zakładka | Rola | Stan wyjściowy |
|---|---|---|
| Mapy | edycja `MapDefinition` JSON (teren, fundamenty, dekoracje, relikty) | edytor przeniesiony do Studio |
| Kampania / mapy rozdziałów | podgląd mapy z ramką i edycja pozycji markerów poziomów | **wdrożone** (sekcja 10) |
| Balans | edycja parametrów silnika, uruchamianie symulacji, raporty | **odroczone** (`game-balance` na niescalonej gałęzi `feature/balance-simulation`) |
| Wizualizator animacji | podgląd istniejących animacji | wdrożony |

Poza zakresem (na teraz): uwierzytelnianie, praca wielu użytkowników, hosting,
generowanie nowych grafik, edycja kodu gry, wbudowywanie Studio w APK.

Zasady nadrzędne:

1. Studio **nie jest źródłem prawdy** — źródłem są pliki repozytorium. Studio
   je czyta, waliduje i zapisuje.
2. Studio nie zawiera reguł gry poza walidacją kontraktu mapy w TypeScript
   (sekcja 6). Autorytatywny pozostaje walidator Kotlin, sprawdzany w testach.
3. Studio nie importuje kodu Androida i nie jest zależnością żadnego modułu
   gry (kierunek zależności: Studio → repozytorium/CLI, nigdy odwrotnie).
4. Nie wprowadzamy drugiego, równoległego kontraktu terenu, assetów ani
   nawigacji — Studio korzysta z istniejących.

## 2. Architektura wysokiego poziomu

```mermaid
flowchart LR
  subgraph Browser["Przeglądarka (studio-web)"]
    Shell["Shell + router zakładek"]
    Maps["Tab: Mapy"]
    Campaign["Tab: Campaign"]
    Viz["Tab: Animacje"]
    Shell --> Maps & Campaign & Viz
  end
  subgraph Local["Lokalny backend (studio-server, Node)"]
    Api["HTTP API /api/*"]
    Maps["mapCatalog (zapis map)"]
    CampaignCatalog["campaignMapCatalog (rozdziały i układy map)"]
    Assets["terrainAssets (indeks i PNG)"]
    Anim["animationCatalog"]
    Api --> Maps & CampaignCatalog & Assets & Anim
  end
  Browser -- "fetch /api" --> Api
  Fs --> Repo[("Repozytorium\nmapy, assety")]
  Index --> Repo
  Anim --> Repo
```

Odroczona zakładka Balans może później dodać `JobRunner` (uruchamianie Gradle).
Zakładka Campaign używa osobnego routera i katalogu danych; nie rozszerza
kontraktu gameplayowej mapy `MapDefinition`.

- **`studio-web`** — React/Vite (przeniesiony frontend dawnego `map-editor-web`). Czysty
  frontend; nie dotyka systemu plików.
- **`studio-server`** — mały lokalny serwer Node/TypeScript nasłuchujący
  wyłącznie na `127.0.0.1`. Jedyny komponent z dostępem do plików i procesów.
- Hosting w przyszłości: ten sam `studio-server` można uruchomić zdalnie, ale
  wymaga to osobnej decyzji (auth, izolacja zapisu). Kontrakt API jest od
  początku projektowany tak, by to nie zmieniało frontendu.

## 3. Struktura katalogów

```text
studio/
  web/                      # frontend (React/Vite)
    src/
      shell/                # layout, router zakładek, status zapisu
      tabs/
        maps/               # domena, format, rendering, state i UI edytora map
        animations/
        campaign/            # edytor rozmieszczenia markerów map kampanii (plan)
        # balance/ — odroczone
      shared/               # klient API, komponenty, typy kontraktów
  server/                   # lokalny backend
    src/
      api/                  # routery per zasób
      services/             # mapCatalog, terrainAssets, animationCatalog
      contracts/            # schematy żądań/odpowiedzi (współdzielone z web)
  README.md
```

Kod edytora map został przeniesiony do `studio/web/src/tabs/maps`; logika
domenowa i testy zachowują dotychczasowe kontrakty.

## 4. Backend: kontrakty API

Wszystkie ścieżki są względne wobec korzenia repozytorium i weryfikowane po
stronie serwera (sekcja 5). Format: JSON, chyba że zaznaczono inaczej.

| Metoda i ścieżka | Opis |
|---|---|
| `GET /api/health` | wersja, korzeń repozytorium, gałąź git, czy drzewo jest czyste |
| `GET /api/maps` | lista map z `game-campaign/.../maps/` |
| `GET /api/maps/{id}` | zawartość mapy |
| `PUT /api/maps/{id}` | walidacja + zapis mapy (patrz 5 i 6) |
| `POST /api/maps/validate` | walidacja bez zapisu (walidator TypeScript, sekcja 6) |
| `GET /api/campaign/chapters` | wspólny katalog rozdziałów z `game-campaign` |
| `GET /api/campaign/layouts/{chapterId}` | układ mapy rozdziału wraz z hashem albo jawny brak layoutu |
| `PUT /api/campaign/layouts/{chapterId}` | walidacja i zapis pozycji markerów z `expectedHash` |
| `POST /api/campaign/layouts/validate` | walidacja layoutu bez zapisu |
| `GET /api/campaign/assets/{assetKey}` | dozwolony asset mapy, ramki lub markera do podglądu |
| `GET /api/assets/terrain/index` | zunifikowany indeks assetów terenu |
| `GET /api/assets/terrain/{path}` | plik PNG ze źródeł assetów (z kontrolą ścieżki) |
| `GET /api/animations` | katalog animacji (sekcja 8) |
| `GET /api/animations/{id}/frames/{n}` | pojedyncza klatka PNG |

Odroczone: endpoint listy poziomów/kamieni milowych
`GET /api/campaign/levels` oraz zakładka Balans:
`GET/PUT /api/balance/parameters`,
`POST /api/balance/runs` (zwraca `jobId`), `GET /api/jobs/{jobId}` (log, SSE).

Błędy mają stały kształt: `{ code, message, details[] }`. `details` niesie
wyniki walidacji (ścieżka pola, reguła), tak by frontend mógł je pokazać przy
elemencie. API Campaign powinno rozróżniać co najmniej brak grafiki mapy,
nieprawidłowy layout i konflikt hasha.

## 5. Bezpieczny dostęp do plików

`mapCatalog.ts` jest jedyną usługą zapisującą definicje map. Reguły:

1. **Lista dozwolonych korzeni zapisu**: wyłącznie
   `game-campaign/src/main/resources/maps/`,
   `game-campaign/src/main/resources/campaign-map-layouts/`,
   `android-app/src/main/assets/maps/`, plik(i) katalogu parametrów oraz
   pliki konfiguracji Studio. Wszystko inne jest tylko do odczytu. Assety
   kampanii w `assets_src/campaign/` są w tym edytorze tylko do odczytu.
2. Id mapy jest ograniczone do `[a-z0-9-]+`; katalogi docelowe są sprawdzane
   po `realpath`, a dowiązania wychodzące poza korzeń repozytorium są odrzucane.
3. Obie kopie są najpierw przygotowywane w plikach tymczasowych; przy błędzie
   podmiany przywracane są wszystkie już zmienione pliki.
4. Przed zapisem serwer sprawdza **hash zawartości** przesłany przez klienta
   (optymistyczna współbieżność). Rozbieżność = `409`, bo plik zmieniono poza
   Studio (np. w IDE lub przez agenta).
5. Studio **nigdy** nie wykonuje `git commit`/`push` ani nie zmienia gałęzi.
   Commit zostaje po stronie użytkownika (`/push`). `GET /api/health`
   pokazuje stan drzewa, by użytkownik widział, co zmieniło Studio.
6. Undo: poza historią w pamięci edytora (jak dziś) Studio opiera się na gicie
   (`git diff` / `git checkout` wykonuje użytkownik); osobnego magazynu kopii
   nie wprowadzamy.

### Duplikacja map

Mapy istnieją dziś w dwóch kopiach:
`game-campaign/src/main/resources/maps` i `android-app/src/main/assets/maps`.
Studio zapisuje **obie kopie jednym żądaniem** (oba pliki tymczasowe, kopie
zapasowe i rollback przy błędzie). Hash obejmuje zawartość obu kopii, więc
wykrywa także rozjazd między nimi. Docelowo warto usunąć duplikat (wspólne
źródło w `game-campaign`, kopiowane przy buildzie) — decyzja użytkownika: na
razie duplikat zostaje.

### Layouty map kampanii

Layout mapy kampanii opisuje prezentację rozdziału, a nie teren ani obiekty
gameplayowe. Studio zapisuje jeden plik JSON na rozdział pod
`game-campaign/src/main/resources/campaign-map-layouts/`; nie zapisuje go przez
`/api/maps` i nie tworzy drugiej kopii w `android-app/src/main/assets`.
`game-campaign` pakuje te zasoby do aplikacji; po buildzie/ponownym
uruchomieniu Android odczytuje ten sam układ.

`CampaignChapterRegistry` i backend Studio czytają wspólny katalog rozdziałów
z `game-campaign/src/main/resources/campaign-chapters.json`. Nie utrzymują
osobnych list ID, nazw, zakresów poziomów ani biomów w TypeScript.
Kotlinowe `CampaignMapLayoutDefinition` i odpowiadający mu typ TypeScript
zawierają identyfikator rozdziału, wymagany klucz assetu mapy oraz dokładnie
dziesięć par znormalizowanych współrzędnych X/Y dla globalnych leveli w
rozdziale. Dla chapterów bez mapy nie istnieje jeszcze layout JSON. Typ
landmarku (forteca/boss) pochodzi z katalogu rozdziałów, nie z edytowalnego
layoutu. Androidowy katalog assetów mapuje neutralne klucze na drawable IDs;
te ID nie przekraczają granicy modułu.

`campaignMapCatalog.ts` zapisuje wyłącznie layouty przez `PUT` z oczekiwanym
hashem, ścieżką ograniczoną do katalogu layoutów, walidacją i atomową
podmianą pliku. Zapis jednego rozdziału nie zmienia definicji jego leveli,
`MapDefinition`, bitmap ani katalogu chapterów.

## 6. Walidacja

Decyzja: walidacja mapy działa **tylko w TypeScript** (przeglądarka i serwer
współdzielą ten sam moduł). Zapis mapy: `walidacja schematu i reguł kontraktu
→ zapis`; błąd walidacji blokuje zapis. Brak wywołania Kotlina z linii poleceń.

Walidator Kotlin pozostaje autorytatywny (zgodnie z
`docs/map-editor/web/README.md`). Ryzyko rozjazdu TypeScript ↔ Kotlin
ograniczają testy: Kotlin musi akceptować każdą mapę z repozytorium zapisaną
przez Studio, a test TypeScript waliduje wszystkie mapy referencyjne z
`game-campaign/.../maps/`. Rozjazd wykrywa bramka `./gradlew test`.

## 7. Zunifikowany indeks assetów

W źródłach istnieje indeks `game-assets/.../terrain/asset-index.txt`
(92 wpisy); dawny edytor generował dodatkowy indeks 132 wpisów przez dołączenie
reliktów z Androida, a podgląd używa także tła, czap, gleb, fortec i katapult.

Implementacja:

- `terrainAssets.ts` buduje indeks **w locie** z indeksu `game-assets` i
  zatwierdzonych reliktów z `android-app/.../drawable-nodpi/`.
- Stare `sync-assets.mjs` i kopie `public/assets` zostały usunięte; Studio
  serwuje pliki bezpośrednio ze źródeł.
- Tła, czapy, gleby, forteca i katapulta są serwowane z tych samych źródeł,
  ale nie trafiają do indeksu dekoracji.
- Klucze assetów dekoracji używane w mapach pozostają bez zmian.

## 8. Zakładka: wizualizator animacji

Wizualizator **tylko odtwarza** istniejące animacje; niczego nie generuje.

### Źródło i parsowanie

Źródłem są pliki `android-app/src/main/res/drawable-nodpi/anim_*.png`, nazwane
według konwencji z `AGENTS.md`:

```text
anim_<category>_<target>_<type>[_<variant>]_<frame:3>.png
```

`AnimationCatalogService` parsuje nazwy wyrażeniem regularnym, grupuje klatki w
sekwencje i zwraca:

```text
AnimationSequence {
  id          // anim_character_orc_move_blue
  category    // character | spell | ammunition
  target      // orc, fire_rain, rock, ...
  type        // idle | move | attack | death | flight | impact | ...
  variant?    // blue | red | ...
  frameCount, firstIndex
}
```

Reguły spójności (te same co `AnimationAssetNamingTest`): indeksy klatek są
ciągłe od `000`, brak dziur i duplikatów. Sekwencje naruszające konwencję są
pokazywane w osobnej sekcji „Niezgodne z konwencją”, a nie ukrywane.

### UI

- Drzewo: **kategoria → cel → typ**; ammunition/pociski jako osobna kategoria.
- Jeden wspólny podgląd (canvas) przełączający się między animacjami wybranego
  celu; kontrolki: play/pause, klatka po klatce, FPS, tło (jasne/ciemne/siatka),
  przełącznik wariantu drużynowego.
- Pole „tylko animacje bez loadera”: oznacza sekwencje, których nie wczytuje
  `BattlefieldAssetCatalog` (dziś `anim_ammunition_fire_rain_impact`).
  Informacja pochodzi z listy w konfiguracji Studio, nie z analizy kodu.

Ta zakładka jest tylko do odczytu, więc jest najlepszym pierwszym krokiem do
wdrożenia serwera (brak ryzyka zapisu).

## 9. Zakładka: Maps — edytor map

Zmiany względem dzisiejszego edytora:

| Obszar | Poprzedni edytor | Studio |
|---|---|---|
| Wczytanie mapy | import pliku z dysku | `GET /api/maps/{id}` |
| Zapis | pobranie pliku JSON (`anchor.download`) | `PUT /api/maps/{id}` z hashem |
| Szkic | `localStorage` | `localStorage` pozostaje jako autosave; nigdy nie nadpisuje pliku |
| Indeks assetów | `fetch("/assets/terrain/asset-index.txt")` | `GET /api/assets/terrain/index` |
| Walidacja | przeglądarka + ręcznie Kotlin | wspólny moduł TypeScript (przeglądarka i serwer) przed zapisem |
| Asset sync | `scripts/sync-assets.mjs` | zbędny |

Przeniesiono model domenowy, kodek JSON, renderer canvas, transformy oraz testy
Vitest. Podmieniono wyłącznie warstwę IO. Edytor, assety i testy są w
`studio/web/src/tabs/maps`; zamrożony układ
fortec/katapult z `AGENTS.md` obowiązuje bez zmian i nadal wymaga równoległej
aktualizacji symulacji, rendera Androida, podglądu w edytorze i testów.

Moduły Kotlin `map-editor-domain/-format/-rendering` (pozostałość po edytorze
desktopowym) są poza zakresem Studio; ich status należy ocenić osobno.

## 10. Zakładka: Campaign — edytor map rozdziałów

Zakładka edytuje wyłącznie warstwę prezentacyjną mapy kampanii. Nie zmienia
`MapDefinition`, terenu, fortec gameplayowych, definicji poziomów, progresji
ani reguł gry. Zapisane pozycje są używane w aplikacji po kolejnym buildzie lub
ponownym uruchomieniu. Rozdziały bez mapy nie są edytowalne; obecnie mapę ma
Chapter 1.

### Właściciele i zależności

| Odpowiedzialność | Właściciel |
|---|---|
| ID, tytuły, zakresy, biomy i typy landmarków rozdziałów | `campaign-chapters.json` oraz `CampaignChapterRegistry` w `game-campaign` |
| Wzorzec layoutu: klucz tła i znormalizowane pozycje markerów | `CampaignMapLayoutDefinition` i `CampaignMapLayoutRepository` w `game-campaign` |
| Lista rozdziałów, walidacja/zapis layoutu, kontrola ścieżki i konfliktów | `campaignMapCatalog.ts` w `studio-server` |
| Select, podgląd, przeciąganie, pola X/Y i niezapisane szkice | `CampaignTab` / `CampaignMapCanvas` w `studio-web` |
| Rozdzielczość klucza assetu do drawable, ramka i rendering gry | Adapter prezentacji w `android-app` |

Przepływ danych:

```text
game-campaign/src/main/resources/campaign-chapters.json
game-campaign/src/main/resources/campaign-map-layouts/<chapterId>.json
       ├── CampaignChapterRegistry / CampaignMapLayoutRepository (game-campaign)
       │       └── CampaignScreen → Android asset catalog → rendered campaign map
       └── campaignMapCatalog.ts (studio-server)
               └── CampaignTab (studio-web)
                       └── PUT layout + expectedHash → canonical layout JSON
```

`game-campaign/src/main/resources/campaign-chapters.json` jest jedynym źródłem
metadanych rozdziałów. Zmiana chapter selectora nie wprowadza drugiej listy w
UI Studio ani hard-coded mapowań rozdziałów w serwerze.

### UI i edycja

- Zakładka **Campaign** rejestruje się w istniejącym `studio/web/src/shell/tabs.ts`.
- Górny select pokazuje wszystkie rozdziały z `GET /api/campaign/chapters`.
- Po wybraniu rozdziału zakładka pobiera jego layout i mapę. Podgląd składa
  tło, transparentną ramkę i markery w kolejności zgodnej z aplikacją.
- Wszystkie 10 markerów jest stałych: 8 fortec i 2 bossy na rozdziałowych
  pozycjach 5 i 10. W trybie edycji mają numery i grafiki, bez stanu progresji
  gracza. Zmieniać można tylko X/Y; dodawanie/usuwanie, skala i rotacja są
  poza zakresem.
- Przeciąganie markera i pola numeryczne X/Y aktualizują ten sam szkic.
  Współrzędne są znormalizowane do źródłowego obrazu mapy, dzięki czemu
  center-crop i resize nie zmieniają zapisanych punktów.
- Preview korzysta z tej samej kolejności warstw oraz transformacji
  center-crop co Android. Testy TypeScript i Kotlin porównują znane punkty
  kontrolne, by uniknąć rozjazdu współrzędnych.
- Jeśli zmiana rozdziału następuje ze szkicem, Studio pyta **Save / Discard /
  Cancel**. Save zapisuje bieżący rozdział; Discard ładuje ostatni zapis;
  Cancel pozostaje przy bieżącym rozdziale. Plik repozytorium zmienia się
  dopiero po jawnym zapisie.
- Jeżeli rozdział nie ma mapy, Studio pokazuje wyraźny komunikat i blokuje
  edycję położeń. Nie używa mapy Chapter 1 jako pozornego zastępstwa.
- Nakładanie się lub bliskość markerów daje ostrzeżenie, ale nie blokuje
  zapisu. Nie ustalamy dodatkowego minimalnego odstępu.

### Kontrakt, walidacja i zapis

`campaign-chapters.json` jest wspólnym wejściem dla typowanego
`CampaignChapterRegistry` oraz backendu Studio. `CampaignMapLayoutDefinition`
jest niemutowalnym, serializowalnym opisem warstwy kampanijnej z `chapterId`,
`backgroundAssetKey` i listą pozycji `{ level, x, y }`. Nie zawiera Android
`R.drawable`, postępu gracza ani statusu markerów.

Walidacja blokująca zapis sprawdza poprawny `chapterId`, mapę istniejącą dla
rozdziału i dokładnie jeden marker dla każdego globalnego poziomu w zakresie
`firstLevel..lastLevel`; X i Y muszą być liczbami skończonymi w zakresie
`[0,1]`. Podgląd zgłasza ostrzeżenie, gdy widoczne granice markerów nachodzą
na siebie, ale nie blokuje zapisu. Brak layoutu/grafiki jest jawnym stanem
`map_artwork_missing`, nie sukcesopodobnym pustym layoutem. Nieprawidłowy JSON,
niezgodny chapter ID, błędny hash i błędy IO mają osobne odpowiedzi API; błąd
nie zmienia źródła.

`CampaignMapLayoutRepository` waliduje ten sam format po stronie Kotlin przed
udostępnieniem aplikacji. Android nadal jest jedynym właścicielem drawable
resource IDs. Gra używa pliku z zasobów `game-campaign`, dlatego po zapisaniu
w Studio nowe pozycje pojawiają się w kolejnym buildzie/uruchomieniu, a nie
przez hot reload zdalnego pliku.

### Draft, lifecycle i wpływ na grę

Edytor posiada jedynie stan draftu aktualnie wybranego rozdziału i stan
bieżącego select/zoom;
nie przechowuje runtime game state. Draft zmienia się przy drag lub wpisie X/Y.
Jawny Save wysyła zwalidowany layout z `expectedHash`; sukces aktualizuje hash
i lokalny stan draftu, konflikt `409` wymaga ponownego odczytu i decyzji
użytkownika. Reset/odrzucenie draftu przywraca ostatni zatwierdzony layout.
Nie ma seedów, czasu symulacji ani replayów, bo edytor nie zmienia
deterministycznego gameplayu.

Zmiana dotyczy tylko markerów ekranu mapy kampanii. Nie przesuwa fortec
gameplayowych ani powiązanych katapult, hitboxów, FHP czy mage placements;
nie uruchamia zmian w `MapDefinition` i nie wymaga regeneracji bitmap.

### Wdrożenie i testy

1. **Wdrożone.** `campaign-chapters.json` jest wspólnym źródłem metadanych
   czytanym przez `CampaignChapterRegistry` i Studio.
2. **Wdrożone.** `CampaignMapLayoutDefinition` i `CampaignMapLayoutRepository` walidują layouty
   w zasobach `game-campaign`; kotwice Chapter 1 przeniesiono z Android
   `CampaignMapPresentationCatalog`.
3. **Wdrożone.** Kontrakty TypeScript, API, `expectedHash`, zapis atomowy,
   walidacja oraz allowlist assetów są w `studio-server`.
4. **Wdrożone.** Zakładka `Campaign` ma select rozdziału, mapę z ramką, 10
   neutralnych markerów, drag, pola X/Y, ostrzeżenia, Save/Discard/Cancel i
   widoczny stan operacji.
5. **Wdrożone.** Android odczytuje te same layouty z zasobów
   `game-campaign`; rozdziały bez artwork zachowują listę poziomów, a Studio
   blokuje ich edycję.

Testy obejmują dekodowanie i walidację rozdziałów/layoutów, pełne zakresy
poziomów, znormalizowane X/Y, brak mapy, TypeScript API i konflikty hasha,
atomiczny zapis, bezpieczeństwo ścieżek, transformację canvas, ostrzeżenia
overlap oraz rejestrację zakładki. Studio dodatkowo przechodzi `npm test`,
`npm run lint` i `npm run build`; gra przechodzi `./gradlew test assembleDebug
--quiet`.

### Rozważone warianty

| Wariant | Zalety | Koszty / ryzyka | Decyzja |
|---|---|---|---|
| Rozszerzyć istniejące `MapsTab` i `MapDefinition` | Maksymalne użycie istniejącego UI | Miesza prezentacyjny layout z terenem gameplayu i istniejącą walidacją; błędna odpowiedzialność | Odrzucony |
| Dodać osobny Studio JSON, pozostawiając Kotlin chapter registry i anchors hard-coded | Najmniejsza początkowa zmiana | Dubluje ID rozdziałów/pozycji; bez synchronizacji Studio może zapisać dane, których gra nie używa | Odrzucony |
| Wspólny chapter JSON + osobny campaign map layout JSON, używany przez Studio i grę | Jedno źródło prawdy, testowalne na obu platformach, zachowuje granice `game-campaign`/Android | Wymaga jednorazowej migracji rejestru i nowego API/serializatora | Rekomendowany |

## 11. Zakładka: balans

**Odroczone** — poza pierwszymi wersjami Studio. Szkic na później.

Opiera się na module `game-balance` i wstrzykiwalnym katalogu parametrów
silnika (`GameBalance.kt`), obecnych na gałęzi `feature/balance-simulation`,
która **nie jest jeszcze scalona z `develop`**. Zakładka może być
zaimplementowana dopiero po scaleniu (lub na tej gałęzi).

- Odczyt/zapis parametrów przez `GET/PUT /api/balance/parameters`; serwer
  waliduje zakresy tym samym kodem Kotlin, co gra.
- Symulacje (`POST /api/balance/runs`) uruchamia `JobRunner` jako proces
  Gradle; wynik (raport z `BalanceReport`) jest strumieniowany jako log i
  zapisywany jako artefakt poza repozytorium (np. `build/studio/`).
- Zasada zachowana z wcześniejszych ustaleń: Studio nie zmienia parametrów
  po cichu — każda zmiana jest widoczna jako diff w git, a HP fortec nie jest
  obniżane bez wyraźnej decyzji użytkownika.

## 12. Uruchamianie i testy

- `npm run studio` (w korzeniu `studio/`): startuje serwer na `127.0.0.1:<port>`
  i frontend (Vite z proxy `/api`). Proces developerski wybiera wolne porty
  rozpoczynając od API `4310` i web `5180`; `STUDIO_PORT` i
  `STUDIO_WEB_PORT` zmieniają porty preferowane.
- Wymagania: Node (jak dziś). JDK 17 + Gradle wrapper tylko dla bramki
  walidacyjnej gry, nie dla działania Studio.
- Testy: Vitest dla frontendu i serwera; testy `mapCatalog` na katalogu
  tymczasowym (ścieżki, atomowość, konflikt hasha); testy katalogu animacji na
  prawdziwych nazwach plików (reużycie reguł z `AnimationAssetNamingTest`).
- Studio jest poza `./gradlew test assembleDebug`; do bramki walidacyjnej
  dochodzą testy i build Studio (`npm test`, `npm run lint`, `npm run build`).

## 13. Plan wdrożenia

1. **Szkielet**: `studio/` (shell, router zakładek, `studio-server` z
   `/api/health`). **Wdrożone**.
2. **Animacje** (tylko odczyt): `AnimationCatalogService` + zakładka.
   **Wdrożone**: `studio/config/animations.json` (znane cele, sekwencje bez
   loadera), nieznany cel → typ to ostatni token i flaga `targetConfigured=false`.
3. **Indeks assetów**: dynamiczny indeks i serwowanie plików ze źródeł.
   **Wdrożone**.
4. **Mapy**: przeniesienie edytora, API odczytu/walidacji/zapisu obu kopii
   z hashem. **Wdrożone**.
5. Usunięcie dawnej aplikacji i asset sync oraz aktualizacja dokumentacji.
   **Wdrożone**.
6. **Campaign map editor**: wspólny chapter catalog i layout JSON,
   bezpieczne API Studio, framed map preview z markerami, drag + X/Y oraz
   odczyt tych samych layoutów przez Android. **Wdrożone**.

Odroczona: zakładka Balans (po scaleniu `feature/balance-simulation`).

Każdy krok jest osobno wdrażalny i kończy się działającą aplikacją.

## 14. Decyzje i ryzyka

| # | Temat | Decyzja | Ryzyko / uwaga |
|---|---|---|---|
| 1 | Walidacja map | tylko TypeScript; Kotlin autorytatywny w testach | rozjazd TS ↔ Kotlin; ograniczany wspólnymi mapami referencyjnymi i bramką `./gradlew test` |
| 2 | Dwie kopie map | Studio zapisuje obie kopie jednym żądaniem; duplikat zostaje | dwufazowy zapis musi być atomowy w parze |
| 3 | Indeksy assetów | jeden indeks budowany w locie ze źródeł; usunąć `sync-assets.mjs` i kopie | trzeba nadać jawne kategorie i biomy plikom spoza indeksu |
| 4 | `feature/balance-simulation` | zakładka Balans pominięta w pierwszych wersjach | gałąź nadal niescalona |
| 5 | Równoległe zmiany plików | hash zawartości + `409` | brak blokad plików |
| 6 | Gradle z serwera | nie dotyczy pierwszych wersji (brak zadań Gradle) | wróci razem z zakładką Balans |
| 7 | Hosting zdalny | poza zakresem; Studio działa lokalnie | wymagałby auth i innego modelu undo |
| 8 | Edycja map kampanii | wspólny chapter JSON i layout JSON zapisujący znormalizowane pozycje | Chapter 2–10 czekają na artwork; pozycje stają się widoczne w grze po buildzie/ponownym uruchomieniu |
| 9 | Lokalizacja kodu | katalog `studio/` w tym repozytorium | — |
| 10 | Sekwencje bez loadera | mały ręcznie utrzymywany plik konfiguracji Studio | lista może się zdezaktualizować |
