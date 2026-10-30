# Urkaaaz Studio

Lokalne narzędzie deweloperskie (nie część gry). Architektura i decyzje:
[`docs/architecture/studio-architecture.md`](../docs/architecture/studio-architecture.md).

Stan: **kroki 1–6 — szkielet, wizualizator animacji oraz edytory Map i Campaign**.
Zakładka Animations wyświetla drzewo kategoria → cel → typ, odtwarza klatki
oraz dla sekwencji
oznaczonych w `teamTextureTargets` przełącza teksturę BLUE/RED oraz nakładkę
masek (obecnie Defender oraz idle/move Demolishera). Zakładka Maps to edytor
gameplayowych `MapDefinition`: ładuje mapy przez `GET /api/maps`, zapisuje obie kopie
JSON przez `PUT /api/maps/{id}` po walidacji TypeScript i kontroli hasha.
Szkice nadal zapisują się w `localStorage`, niezależnie od plików repozytorium.
Zakładka Campaign korzysta ze wspólnego katalogu rozdziałów z
`game-campaign`, pokazuje mapę z ramką i pozwala przesuwać 10 markerów oraz
wpisywać ich znormalizowane pozycje X/Y. Layouty są zapisywane osobno dla
rozdziałów przez bezpieczne API z hashem; grafiki map brakujących chapterów
wyłączają edycję do czasu dodania artworku.
Assety są indeksowane i serwowane bezpośrednio ze źródeł przez `/api/assets`.
Serwer udostępnia też `POST /api/maps/validate`, `GET /api/health`,
`GET /api/animations`, `GET /api/animations/{id}/frames/{n}`, team masks i
team textures oraz `/api/campaign/*`. Znane
cele i sekwencje bez loadera w grze opisuje ręcznie utrzymywany
`config/animations.json`; palety wskazują
`android-app/src/main/assets/animations/<unit>/palettes.json`. Maski są
automatycznym draftem i wymagają przeglądu artystycznego.

```sh
cd studio
npm install
npm run studio   # wybiera wolne porty od 4310 (API) i 5180 (web)
npm test         # Vitest: server + web
npm run lint     # tsc --noEmit
npm run build    # build frontendu (studio/web/dist, ignorowany przez git)
```

- `server/` — Node ≥ 24 uruchamia TypeScript natywnie (`node src/main.ts`),
  bez zależności runtime. Słucha tylko na `127.0.0.1` i odrzuca nagłówki `Host`
  spoza loopbacku. `npm run studio` szuka kolejnego wolnego portu, jeśli
  domyślne `4310` lub `5180` jest zajęte. `STUDIO_PORT` i `STUDIO_WEB_PORT`
  ustawiają preferowane porty; `STUDIO_REPO_ROOT` wskazuje korzeń repozytorium
  (domyślnie wykrywany po `settings.gradle.kts`).
- `web/` — React/Vite. Importuje kontrakty API z `server/src/contracts` przez
  alias `@studio/contracts` (tylko typy). Zakładki rejestruje `src/shell/tabs.ts`;
  edytory map gameplayowych i map kampanii znajdują się odpowiednio w
  `src/tabs/maps/` i `src/tabs/campaign/`.
- `server/src/services/mapCatalog.ts` — bezpieczny odczyt i zapis obu kopii
  definicji map z kontrolą sumy pary plików oraz wycofaniem częściowego zapisu.
- `server/src/services/campaignMapCatalog.ts` — wspólny chapter catalog,
  bezpieczny odczyt/zapis layoutów campaign i allowlistowane podglądy assetów.
- `server/src/api/campaign.ts` — API listy chapterów, layoutów, walidacji i art assetów.
- `server/src/services/availablePort.ts` — wybór wolnego portu startowego dla
  lokalnego procesu Studio.
- `server/src/services/terrainAssets.ts` — dynamiczny indeks oraz bezpośrednie
  serwowanie assetów z `game-assets` i zatwierdzonych drawable Androida.
- Studio raportuje stan gita, ale nigdy nie commituje ani nie zmienia gałęzi.
