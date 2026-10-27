# Urkaaaz Studio

Lokalne narzędzie deweloperskie (nie część gry). Architektura i decyzje:
[`docs/architecture/studio-architecture.md`](../docs/architecture/studio-architecture.md).

Stan: **kroki 1–4 — szkielet, wizualizator animacji i edytor map**. Zakładka Animations
wyświetla drzewo kategoria → cel → typ, odtwarza klatki i dla sekwencji
oznaczonych w `teamTextureTargets` przełącza teksturę BLUE/RED oraz nakładkę
masek (obecnie Defender oraz idle/move Demolishera). Zakładka Maps to
edytor map kampanii: ładuje mapy przez `GET /api/maps`, zapisuje obie kopie
JSON przez `PUT /api/maps/{id}` po walidacji TypeScript i kontroli hasha.
Szkice nadal zapisują się w `localStorage`, niezależnie od plików repozytorium.
Assety są indeksowane i serwowane bezpośrednio ze źródeł przez `/api/assets`.
Serwer udostępnia też `POST /api/maps/validate`, `GET /api/health`,
`GET /api/animations`, `GET /api/animations/{id}/frames/{n}`, team masks i
team textures. Znane
cele i sekwencje bez loadera w grze opisuje ręcznie utrzymywany
`config/animations.json`; palety wskazują
`android-app/src/main/assets/animations/<unit>/palettes.json`. Maski są
automatycznym draftem i wymagają przeglądu artystycznego.

```sh
cd studio
npm install
npm run studio   # serwer http://127.0.0.1:4310 + frontend http://127.0.0.1:5180
npm test         # Vitest: server + web
npm run lint     # tsc --noEmit
npm run build    # build frontendu (studio/web/dist, ignorowany przez git)
```

- `server/` — Node ≥ 24 uruchamia TypeScript natywnie (`node src/main.ts`),
  bez zależności runtime. Słucha tylko na `127.0.0.1` i odrzuca nagłówki `Host`
  spoza loopbacku. `STUDIO_PORT` zmienia port, `STUDIO_REPO_ROOT` wskazuje
  korzeń repozytorium (domyślnie wykrywany po `settings.gradle.kts`).
- `web/` — React/Vite. Importuje kontrakty API z `server/src/contracts` przez
  alias `@studio/contracts` (tylko typy). Zakładki rejestruje `src/shell/tabs.ts`;
  edytor map i jego testy znajdują się w `src/tabs/maps/`.
- `server/src/services/mapCatalog.ts` — bezpieczny odczyt i zapis obu kopii
  definicji map z kontrolą sumy pary plików oraz wycofaniem częściowego zapisu.
- `server/src/services/terrainAssets.ts` — dynamiczny indeks oraz bezpośrednie
  serwowanie assetów z `game-assets` i zatwierdzonych drawable Androida.
- Studio raportuje stan gita, ale nigdy nie commituje ani nie zmienia gałęzi.
