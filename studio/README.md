# Urkaaaz Studio

Lokalne narzędzie deweloperskie (nie część gry). Architektura i decyzje:
[`docs/architecture/studio-architecture.md`](../docs/architecture/studio-architecture.md).

Stan: **krok 2 — wizualizator animacji (tylko odczyt)**. Zakładka Animations
wyświetla drzewo kategoria → cel → typ, przełącznik wariantu blue/red i podgląd
klatek (play/pause, krok ±1, FPS, tło). Zakładka Maps to nadal placeholder.
Serwer: `GET /api/health`, `GET /api/animations`,
`GET /api/animations/{id}/frames/{n}`. Znane cele i sekwencje bez loadera
w grze opisuje ręcznie utrzymywany `config/animations.json`. Reszta jest
w planie wdrożenia.

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
  alias `@studio/contracts` (tylko typy). Zakładki rejestruje `src/shell/tabs.ts`.
- Studio tylko raportuje stan gita; nigdy nie commituje ani nie zmienia gałęzi.
