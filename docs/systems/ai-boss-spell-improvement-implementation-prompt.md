# Prompt implementacyjny: AI bossów i czary

Zaimplementuj
[`ai-boss-spell-improvement-plan.md`](ai-boss-spell-improvement-plan.md)
bez zmiany pozostałych reguł meczu. Plan jest źródłem decyzji; przeczytaj go
w całości przed edycją.

## Potwierdzone wymagania

- Priorytet boss-target dotyczy tylko żywego `CENTRAL_OBJECTIVE` rozstrzyganego
  damage race. Mobile siege boss pozostaje sprzymierzony z RED, naciera na BLUE
  i nie może być celem ataku AI RED.
- Dla central objective wymagany jest rzeczywisty wynik w symulacji
  (`BossDamaged`), nie tylko komenda z innym kątem.
- AI ma mieć politykę dla każdego czaru, którego runtime efekt jest
  zaimplementowany i dostępny w scenariuszu. Nie oznacza to wymuszonego rzutu
  każdym czarem w każdym meczu; warunki celu, many, stocku i aktywnego efektu
  muszą być spełnione.
- AI nie implementuje efektów czarów ani bossów. Wysyła zwykłe komendy na
  podstawie `MatchSnapshot`; `MatchSession` i symulacja pozostają autorytetem
  walidacji i runtime'u.
- AI spell stock jest osobny, skończony i balance-driven. Nie pobieraj ani nie
  kopiuj `PlayerInventoryConfig` BLUE do RED. Początkowy stock wynosi jedno
  użycie każdego poziomowo dostępnego czaru specjalnego na AI match.
- Nie implementuj ośmiu brakujących SpellSystem effects jako części zadania.
  Polityka AI dla danego czaru staje się wymagana w tej samej zmianie, w której
  efekt runtime zostaje oznaczony jako zaimplementowany.

## Audyt i zachowanie worktree

Przed pracą sprawdź branch i `git status --short`; zachowaj istniejące zmiany
użytkownika. Przeczytaj:

```text
AGENTS.md
docs/agent-context-index.md
docs/architecture/architecture.md
docs/architecture/implementation-rules.md
docs/architecture/project-map.md
docs/project/current-state.md
docs/project/knowledge.md
docs/migration/m6-ai.md
docs/migration/m6-report.md
docs/systems/ai-boss-spell-improvement-plan.md
docs/systems/spell-system.md
docs/systems/spell-system-implementation-prompt.md
docs/systems/boss-system.md
docs/systems/campaign-system.md
docs/systems/loadout-system.md
```

Sprawdź `AiAgent`, `AiStrategies`, `AiMatchCoordinator`, przepływ
`LocalMatchGateway` → `MatchViewModel`, aktualne testy, `BossSnapshot`,
`BossEncounterConfig`, `BossSystem`, `SpellCatalog`, `MatchCommand.CastSpell`
i aktywne spell snapshots. Obecny target branch wybiera współrzędne bossów
centralnych, ale test porównuje tylko kąty. Nie uznawaj celu za poprawiony,
dopóki symulacja nie potwierdzi trafienia bossa.

## 1. Boss centralny i celowanie

- Zostaw obecne targetowanie poziomów bez bossa bez zmian.
- Wprowadź jawny cel `CENTRAL_OBJECTIVE` oparty na typowanym encounterze/
  `BossSnapshot`, nie na numerze levelu ani map markerze.
- Gdy centralny boss żyje, wszystkie strategie katapulty (`simple`,
  `situational`, `counterplay`) traktują damage race jako cel. Brak
  rozwiązania dla jednego ammo oznacza próbę innego legalnego ammo albo
  oczekiwanie — nie strzał w fortecę BLUE.
- Nie targetuj mobile siege bossów przez RED.
- Użyj współdzielonego, deterministycznego predyktora lotu zgodnego z fizyką
  symulacji, dostępny w dozwolonej warstwie. Nie kopiuj osobnej, niezgodnej
  fizyki do `game-ai`, UI ani Androida.
- Oceniaj kandydaty ammo/kąta/mocy względem rzeczywistego hitboxa boss
  snapshotu, aktualnego wiatru, flight profile, grawitacji, drag, terenu,
  immunitetów i reloadu. Amunicję przeładowywaną odfiltruj przed scoringiem.
- Zachowaj seedowane błędy profilu jako celowy rozrzut po wyznaczeniu
  trafialnego rozwiązania. Nie przyznawaj AI statystyk, many ani zasobów.

## 2. Polityki spell AI

- Dodaj rozszerzenie do istniejącego `AiStrategyRegistry` dla decyzji
  `SpellType -> AiSpellPolicy` (lub równoważnego rejestru zgodnego z obecnym
  kodem). Polityka czyta wyłącznie immutable snapshot, `GameBalanceConfig`,
  typowy target metadata i konfigurację AI.
- Przekaż agentowi `MatchScenario.availableSpells`. To dostępność poziomu,
  nie loadout BLUE. Nie kopiuj wybranego loadoutu gracza do przeciwnika.
- AI może rozpatrywać wyłącznie `SpellDefinition.isImplemented == true`.
  Nie wysyłaj castu, który `MatchSession` odrzuci jako
  `SPELL_NOT_IMPLEMENTED`, niedostępny, bez many, bez stocku, z niepoprawnym
  celem lub już aktywnym efektem.
- Dodaj osobny, ograniczony RED spell stock z kanonicznego balansu do
  konfiguracji/runtime/snapshotu. Każdy dostępny czar specjalny dostaje na
  początku meczu jeden stock; walidacja i decrement są atomowe w tym samym
  właścicielu gameplay state co pozostałe spell stocki. AI tylko odczytuje
  stock i emituje `MatchCommand.CastSpell`.
- Wybieraj najwyżej jeden cast w jednej decyzji przez deterministyczny
  utility score. Nie spamuj aktywnym efektem i respektuj mana/stock. Polityki
  korzystają z `SpellTargetCategory` i istniejącego `CastSpell.target`;
  jeśli nowy czar faktycznie wymaga world-position targetu, najpierw rozszerz
  kontrakt jawnie, nie koduj pozycji w stringach.
- Dla obecnych efektów dodaj przykładowe sytuacyjne warunki: HASTE przy
  użytecznych aktywnych sojusznikach, FORTRESS_SHIELD przy zagrożonej własnej
  fortecy bez aktywnej tarczy, RUNIC_BASTION przy nacisku wrogów blisko
  własnej bramy. Progi mają być typowane i balance-driven, nie ukryte w UI.
- Nowy SpellCatalog effect wymaga w tym samym PR runtime policy AI, testów
  pozytywnych/negatywnych i wpisu w rejestrze. Dodaj test kompletności:
  zestaw zaimplementowanych spell types musi być zgodny z obsługiwanymi
  policy IDs; nowy runtime czar bez polityki ma oblać test.

## 3. Testy i granice

Dodaj testy behawioralne dla:

- campaign central objective: AI command i pełna symulacja prowadzą do
  `BossDamaged`; sprawdź też, że zwykły scenariusz i mobile boss zachowują
  dotychczasowe cele;
- różnych profili i dostępnej amunicji przy bossie, w tym reloadu i
  immunitetu;
- skutecznego/błędnego castu dla każdego aktualnie zaimplementowanego czaru,
  dostępności poziomu, many, stocku, target category oraz blokady aktywnego
  efektu;
- `AiMatchCoordinator` kadencji/pausy i ograniczenia stocku RED;
- deterministyczności decyzji oraz replaya przy tym samym seedzie;
- braku mutacji simulation state przez AI i braku importów Androida w core.

Nie zmieniaj czasu meczu, boss victory rules, balance statystyk jednostek,
warunków SpellSystemu, UI loadoutu BLUE ani mechaniki pozostałych ośmiu
niezaimplementowanych czarów. Nie zwiększaj stocku AI zależnie od profilu.
Nie commituj ani nie pushuj.

## Walidacja

```bash
./gradlew test assembleDebug --quiet
```

Jeśli zmienisz `game-balance.json`, repository DTO lub Studio Balance, uruchom
również testy i build Studio (`npm test`, `npm run lint`, `npm run build`) oraz
sprawdź pełną zgodność aktualnego schema. Zakończ `git diff --check` i
`git status --short`.
