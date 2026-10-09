# M6 — Migracja AI

## Zakres

M6 przenosi decydowanie AI do osobnego modułu `game-ai`. Agent otrzymuje
wyłącznie `MatchSnapshot` i zwraca te same `MatchCommand`, które może
wygenerować człowiek.

Obsługiwane decyzje:

- wybór amunicji;
- wybór celu i parametrów strzału;
- wybór jednostki do rozmieszczenia;
- temperament;
- reakcja na niski poziom zdrowia fortecy;
- poziom trudności i kontrolowany błąd celowania.

## Granice

AI nie wykonuje komend, nie zna `GameEngine`, `MatchSimulation`, Androida,
UI ani assetów. Wykonanie i walidacja komendy pozostają odpowiedzialnością
warstwy aplikacyjnej i symulacji.

Losowość jest tworzona z jawnego ziarna, dzięki czemu decyzje mogą być
odtwarzane w testach.
