---
name: addSystem
description: Describe the architecture of a new Urkaaaz gameplay or presentation subsystem in a Markdown document with explicit contracts, ownership, state, events, tests, and integration boundaries.
argument-hint: "[optional system name]"
---

# /addSystem — dodawanie podsystemu Urkaaaz

Używaj tego skilla, gdy użytkownik wywoła `/addSystem` albo poprosi o
opisanie architektury nowego systemu do Urkaaaz.

Skill dotyczy podsystemu, a nie automatycznie nowego modułu Gradle. System ma
zostać opisany z przypisaniem do istniejącego modułu właściciela
odpowiedzialności, chyba że dokument architektury wykaże konieczność utworzenia
nowej granicy modułowej.

**Rezultatem skilla jest wyłącznie plik Markdown.** Skill nie implementuje
kodu, nie dodaje kontraktów Kotlin, nie modyfikuje runtime, nie dodaje testów
ani assetów. Dokument może zawierać plan przyszłej implementacji, ale plan nie
jest wykonywany przez ten skill.

Przykłady:

- `RelicSystem` — funkcjonalne relikty w `game-domain` / `game-simulation`;
- `DecorativeRelicCatalog` — dekoracyjne elementy mapy w `game-contracts`,
  `game-campaign` i istniejącym mapperze/renderingu;
- `SpellSystem` — zaklęcia, ich koszty, efekty i eventy;
- `TutorialSystem` — kroki tutoriala, warunki i prezentacja;
- `RewardSystem` — nagrody i granica z przyszłą persystencją.

## Kontrakt rozmowy

Zadawaj pytania pojedynczo. Nigdy nie łącz kilku pytań w jednej wiadomości.
Jeżeli użytkownik podał informację wcześniej, zachowaj ją jako źródło prawdy i
nie pytaj o nią ponownie.

Skill obsługuje wszystkie typy podsystemów Urkaaaz:

- gameplayowe;
- prezentacyjne;
- kampanijne i authoringowe;
- aplikacyjne;
- przekrojowe.

Przed przygotowaniem dokumentu:

1. podsumuj, jaki system rozumiesz;
2. wskaż brakujące decyzje;
3. zadaj pytania w kolejności poniżej;
4. przeanalizuj repozytorium i istniejące systemy;
5. wykryj możliwe duplikowanie funkcjonalności;
6. zaproponuj właściciela, moduł, wariant rozwiązania i plan dokumentu;
7. przedstaw skrócone podsumowanie;
8. poproś o jednoznaczną akceptację;
9. nie zapisuj dokumentu przed akceptacją kompletnego zakresu i planu.

Podsumowanie przed akceptacją ma być krótkie, ale musi zawierać:

- cel i typ systemu;
- proponowanego właściciela i moduł;
- najważniejsze wejścia i wyjścia;
- wykryte podobne rozwiązania lub duplikaty;
- rekomendowane rozwiązanie i jego najważniejszą przewagę;
- ścieżkę pliku Markdown.

### Kolejność pytań

1. Zapytaj o nazwę systemu i jego jednozdaniowy cel.
2. Zapytaj o typ systemu, jeżeli nie wynika jasno z opisu.
3. Zapytaj o oczekiwany efekt użytkowy lub gameplayowy.
4. Zapytaj o wejścia systemu: komendy, snapshoty, definicje poziomu, eventy,
   konfigurację albo dane użytkownika.
5. Zapytaj o wyjścia systemu: snapshot, eventy, komendy, render state,
   placementy albo wynik use case.
6. Zapytaj, czy system posiada własny runtime state oraz jaki jest jego cykl
   życia: utworzenie, reset, pauza, restart i zakończenie meczu.
7. Zapytaj o zasady deterministyczności, seeda, czasu i replayów, jeżeli system
   wpływa na symulację.
8. Zapytaj o interakcje z istniejącymi systemami i kolejność tych interakcji.
9. Zapytaj o błędy, odrzucenia komend, limity i przypadki brzegowe.
10. Zapytaj o konfigurację, balans, odblokowanie i przyszłą persystencję,
    jeżeli system ich wymaga.
11. Zapytaj o wymagania Android UI, assety, debugowanie i podgląd, jeżeli
    system ma warstwę prezentacyjną.
12. Zapytaj o zakres dokumentacji i kryteria akceptacji.

Nie pytaj użytkownika o właściciela modułowego, jeżeli można go ustalić przez
analizę repozytorium. W takim przypadku przedstaw propozycję i poproś o jej
akceptację w skróconym podsumowaniu.

Jeżeli system jest reliktem, dopytaj dodatkowo:

- czy chodzi o relikty dekoracyjne, funkcjonalne, czy obie kategorie;
- dla dekoracyjnych: asset key, placement, kotwicę, warstwę, skalę i brak
  wpływu na gameplay;
- dla funkcjonalnych: bonus, właściciela efektu, czas działania, stosowanie,
  konflikt z innymi reliktami, odblokowanie i wpływ na snapshot/eventy.

Nie zakładaj parametrów gameplayowych, balansu, czasu działania, limitów ani
zasad stosowania. Jeżeli agent proponuje wartość, oznacz ją jako propozycję i
uzyskaj akceptację przed implementacją.

## Analiza istniejących rozwiązań i duplikacji

Przed przygotowaniem podsumowania przeszukaj repozytorium pod kątem:

- istniejących systemów o podobnej odpowiedzialności;
- kontraktów, snapshotów, eventów i konfiguracji rozwiązujących ten sam
  problem;
- istniejących helperów, mapperów, factory, registry i lifecycle;
- podobnych nazw, zachowań i katalogów danych;
- funkcjonalności już dostępnych w domenie, symulacji, aplikacji, kampanii,
  AI lub Android UI.

Jeżeli występuje możliwe duplikowanie, nie opisuj bezpośrednio nowego systemu
jako domyślnego rozwiązania. Zgłoś:

1. co już istnieje;
2. na czym polega nakładanie odpowiedzialności;
3. jakie są ryzyka duplikacji;
4. rekomendowane rozwiązanie najlepiej pasujące do architektury;
5. alternatywy wraz z krótkim porównaniem czytelności, wydajności,
   testowalności i kosztu integracji.

Rekomendacją może być rozszerzenie istniejącego systemu, wydzielenie wspólnego
kontraktu, użycie istniejącego adaptera albo utworzenie nowego podsystemu.
Nie twórz nowej równoległej funkcjonalności bez zaakceptowania przez użytkownika
wybranego wariantu.

## Twarde zasady architektury

- `android-app` jest jedynym właścicielem zależności Android.
- `game-contracts`, `game-domain`, `game-simulation`, `game-application`,
  `game-campaign` i `game-ai` nie mogą importować Androida.
- UI wysyła `MatchUiAction` albo komendy aplikacyjne; nie mutuje stanu systemu
  ani domeny bezpośrednio.
- Runtime state należy do jednego właściciela. Nie twórz drugiej kopii stanu w
  Android UI, rendererze ani niezależnym singletonie.
- Systemy komunikują się przez jawne kontrakty, komendy, snapshoty i eventy.
- Nie dodawaj przypadkowych pól i warunków do `Match`, `MainActivity` ani
  ViewModelu zamiast osobnego podsystemu.
- Nie twórz równoległego katalogu amunicji, terenu, assetów, kampanii,
  nawigacji, balansu ani snapshotów, jeżeli istnieje odpowiedni właściciel.
- Błędy i odrzucone komendy muszą być jawne. Nie używaj szerokich `catch`,
  cichych fallbacków, losowych zamienników ani sukcesopodobnych wartości.
- Kontrakty mają być niemutowalne i możliwie niezależne od implementacji.
- Gameplay pozostaje w domenie/symulacji, a nie w Android UI.
- Prezentacja korzysta z istniejącego render state i katalogów assetów.
- System nie może mieszać runtime state z persystencją kampanii.

## Główne zasady Clean Architecture i Clean Code

### Clean Architecture

- Zależności mają kierunek do środka: warstwy zewnętrzne zależą od
  wewnętrznych, nigdy odwrotnie.
- `game-contracts`, `game-domain`, `game-simulation`, `game-application`,
  `game-campaign` i `game-ai` nie znają Androida.
- `android-app` jest adapterem dla lifecycle, UI, inputu, renderingu i
  mapowania danych.
- Warstwy komunikują się przez jawne komendy, snapshoty, eventy i kontrakty.
- Runtime state ma jednego właściciela; UI nie przechowuje równoległej kopii
  gameplayu.
- Snapshoty, definicje poziomów i kontrakty są niemutowalne.
- Definicje kampanii i konfiguracja są oddzielone od runtime state meczu.
- Reguły gameplayu pozostają w domenie lub symulacji, a nie w Android UI.
- Każdy system ma jednego właściciela odpowiedzialności i jasno określony
  zakres.
- Adaptery korzystają ze wspólnych kontraktów zamiast kopiować logikę do
  osobnych implementacji.

### Clean Code

- Używaj czytelnych nazw domenowych zamiast niejasnych nazw technicznych.
- Preferuj małe, wyspecjalizowane funkcje i klasy; nie twórz God Objectów.
- Zachowuj jedno źródło prawdy dla balansu, amunicji, terenu, assetów i
  nawigacji.
- Obsługuj błędy jawnie; nie używaj cichych fallbacków ani sukcesopodobnych
  wartości błędnych.
- Nie stosuj szerokich `catch`; obsługuj tylko znane i oczekiwane błędy.
- Zachowuj typowanie i unikaj `Any` oraz niepotrzebnych castów.
- Dla symulacji utrzymuj deterministyczność, kontrolowany seed, stały krok i
  reprodukowalne replaye.
- Wprowadzaj minimalną kompletną zmianę i nie dodawaj niezwiązanych refaktorów.
- Przed dodaniem abstrakcji wykorzystaj istniejące helpery, kontrakty i wzorce.
- Komentarze dodawaj tylko dla nieoczywistych decyzji lub ograniczeń.
- Projektuj reguły core tak, aby można je było testować bez Androida.
- Aktualizuj dokumentację razem ze zmianą architektoniczną lub nowym terminem.

## Wymagany opis architektury systemu

Plan musi zawierać:

| Element | Wymagane pytanie |
|---|---|
| Cel | Jaki problem rozwiązuje system? |
| Właściciel | Który moduł i klasa/system jest właścicielem? |
| Wejścia | Jakie kontrakty, komendy lub snapshoty konsumuje? |
| Wyjścia | Jakie snapshoty, eventy, komendy lub render state emituje? |
| Stan | Co jest runtime state, a co konfiguracją/definicją? |
| Czas | Czy system działa w ticku, reaktywnie, jednorazowo czy przez lifecycle? |
| Determinizm | Czy wymaga seeda, replayu albo kolejności eventów? |
| Konflikty | Jak rozstrzyga kolizje z innymi systemami? |
| Błędy | Jakie wejścia są odrzucane i jak są raportowane? |
| Testy | Jakie reguły i granice muszą być zweryfikowane? |
| Dokumentacja | Jakie trwałe decyzje trzeba zapisać? |

Nie przygotowuj finalnego dokumentu, dopóki właściciel, wejścia, wyjścia i
runtime state nie są jednoznaczne.

## Format pliku wynikowego

Po akceptacji utwórz jeden plik:

```text
docs/systems/<system-id>.md
```

`<system-id>` powinien być stabilnym identyfikatorem w kebab-case, np.
`relic-system.md`, `spell-system.md` albo `tutorial-system.md`. Jeżeli
odpowiedni dokument już istnieje, zaktualizuj go zamiast tworzyć drugi opis
tego samego systemu.

Dokument musi zawierać:

1. cel i zakres systemu;
2. zakres wyłączony z systemu;
3. właściciela odpowiedzialności i moduł;
4. kierunek zależności;
5. kontrakty wejścia i wyjścia;
6. runtime state, konfigurację i definicje;
7. lifecycle, reset, pauzę i zakończenie meczu;
8. przepływ komend, snapshotów i eventów;
9. deterministyczność, czas, seed i replay, jeśli dotyczą;
10. integrację z istniejącymi systemami;
11. obsługę błędów i odrzuceń;
12. wymagania Android UI i assetów, jeśli dotyczą;
13. plan testów i kryteria akceptacji;
14. otwarte decyzje i niedokończony zakres;
15. proponowaną listę plików do przyszłej implementacji;
16. krótkie porównanie rozważonych wariantów i uzasadnienie rekomendacji.

Dokument opisuje architekturę i decyzje. Nie powinien zawierać pozornie
zaimplementowanych klas, gotowych fragmentów kodu ani deklaracji, że system
działa, jeżeli nie został jeszcze zaimplementowany.

## Zasady kategorii systemów

### System gameplayowy

System gameplayowy musi:

- mieć jawny kontrakt wejścia/wyjścia;
- działać deterministycznie, jeśli wpływa na symulację;
- posiadać własne reguły, konfigurację i testy;
- emitować eventy zamiast mutować obcy system bezpośrednio;
- być podłączony do istniejącego cyklu ticków lub granicy komend;
- zachować zgodność z replayem i snapshotem.

### System prezentacyjny

System prezentacyjny musi:

- konsumować niemutowalny render state;
- nie zmieniać reguł gameplayu;
- używać istniejącego `BattlefieldAssetCatalog`, mappera i transformacji
  world-space/screen-space;
- respektować lifecycle Androida;
- mieć testy granicy ViewModel/mapper/controller, jeżeli zmienia UI.

### System kampanijny lub authoringowy

System kampanijny musi:

- przechowywać definicje i konfigurację, nie runtime state meczu;
- używać istniejących kontraktów `CampaignLevelDefinition`, `MapDefinition`
  i `MatchScenario`, gdy dotyczą poziomu;
- mieć stabilne identyfikatory;
- walidować dane przed udostępnieniem aplikacji;
- być deterministyczny i możliwy do odtworzenia.

### Relikty

Relikt dekoracyjny jest `MapObjectPlacement` lub istniejącym kontraktem
dekoracji. Musi mieć neutralny `assetKey`, placement world-space, kotwicę i
nie może wpływać na teren, kolizje, obrażenia, zasoby, AI ani wynik meczu.

Relikt funkcjonalny jest elementem gameplayu. Nie dodawaj jego efektu do
renderera ani bezpośrednio do UI. Zdefiniuj:

- stabilny identyfikator i definicję;
- właściciela aktywnego efektu;
- warunki nabycia, założenia i aktywacji;
- czas działania i zasady stosowania;
- konflikty, limity i kolejność względem amunicji, jednostek, wiatru oraz
  obrażeń;
- snapshot/read model i eventy;
- reset przy nowym meczu oraz granicę z persystencją.

## Workflow przygotowania dokumentu

Po akceptacji zakresu i planu:

1. przeczytaj `AGENTS.md`, `architecture.md`,
   `docs/PROJECT_KNOWLEDGE.md`, `AI_IMPLEMENTATION_RULES.md`,
   `AI_PROJECT_MAP.md` oraz dokumenty właściciela;
2. znajdź istniejące analogiczne systemy i reuse ich wzorce;
3. zidentyfikuj dokładne pliki i moduły do zmiany;
4. opisz proponowane kontrakty bez tworzenia ich w kodzie;
5. opisz właściciela runtime state bez dodawania runtime state;
6. opisz reguły domeny, symulacji i adapterów bez ich implementowania;
7. opisz komendy, eventy, snapshoty i lifecycle;
8. opisz przyszłą integrację AI, kampanii i Androida tylko wtedy, gdy zakres
   tego wymaga;
9. opisz przyszłe testy jednostkowe, integracyjne, replay i granice
   architektury;
10. utwórz lub zaktualizuj jeden plik `docs/systems/<system-id>.md`;
11. wykonaj kontrolę Markdown i `git diff --check`;
12. zatrzymaj się bez commita i pusha.

Nie dodawaj kodu, testów, assetów ani placeholderów sugerujących
zaimplementowaną funkcję.

## Plan testów opisywany w dokumencie

Dokument musi opisać przyszłe testy adekwatne do zakresu:

- konstrukcja i walidacja kontraktów;
- poprawna inicjalizacja i reset runtime state;
- wszystkie przejścia stanu;
- poprawne komendy i jawnie odrzucone komendy;
- eventy i snapshoty;
- deterministyczność przy tym samym wejściu/seederze;
- replay regression, jeżeli system wpływa na symulację;
- konflikty z istniejącymi systemami;
- brak mutacji obcych właścicieli stanu;
- mapper/render state, jeżeli system ma UI;
- brak Androida w core;
- brak drugiego równoległego katalogu lub kontraktu.

## Zakres dokumentacji

Skill tworzy lub aktualizuje wyłącznie dokument architektury systemu. Nie
aktualizuj automatycznie:

- `docs/PROJECT_KNOWLEDGE.md` — bieżący stan i ważne pliki;
- `architecture.md` — tylko jeżeli zmienia się granica odpowiedzialności;
- `GLOSSARY.md` — nowe terminy domenowe;
- raportów etapów migracji;
- kodu ani testów.

Wyjątkiem jest jawna prośba użytkownika o aktualizację dodatkowej dokumentacji.
Plik systemu ma opisywać właściciela, przepływ danych, kontrakty, ograniczenia,
przyszłe testy i niedokończone elementy. Nie kopiuj implementacji.

## Końcowy review

Przed zakończeniem sprawdź:

- Single Responsibility;
- dependency direction;
- brak Androida w core;
- brak gameplayu w UI;
- niemutowalność kontraktów;
- jeden właściciel runtime state;
- jawne komendy, eventy i snapshoty;
- deterministyczność i replay;
- brak cichych fallbacków;
- brak duplikacji systemów, katalogów i balansu;
- poprawny reset i lifecycle;
- testowalność;
- zgodność z `architecture.md` i `AGENTS.md`.

## Końcowa walidacja dokumentu

Z katalogu głównego repozytorium uruchom:

```bash
git diff --check
git status --short
```

Build i testy Gradle nie są wymagane dla zmiany wyłącznie dokumentacyjnej,
chyba że repozytorium posiada testy dokumentacji albo użytkownik zażąda ich
uruchomienia.

Nie twórz commita ani nie wykonuj push bez osobnego, wyraźnego polecenia
użytkownika. W końcowej odpowiedzi podaj:

- dokładną ścieżkę skilla;
- dokładną ścieżkę utworzonego lub zaktualizowanego pliku systemu;
- listę zmienionych plików;
- wynik `git diff --check`;
- potwierdzenie gałęzi;
- potwierdzenie, że nie wykonano commita ani push.
