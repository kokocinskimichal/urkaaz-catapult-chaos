# Prompt implementacji Fire Rain Presentation

Skopiuj cały prompt poniżej do nowego zadania w repozytorium Urkaaaz.

---

## Prompt

Przeprowadź kompletną implementację systemu **Fire Rain Presentation** w
repozytorium Urkaaaz, zgodnie z:

- `docs/systems/fire-rain-presentation.md` — źródło prawdy dla zatwierdzonej
  architektury, zakresu, etapów, kryteriów akceptacji i otwartych decyzji;
- `architecture.md`, `AI_IMPLEMENTATION_RULES.md`, `AI_PROJECT_MAP.md` oraz
  `docs/PROJECT_KNOWLEDGE.md` — reguły i kontekst projektu;
- istniejącymi kontraktami, mapperem, katalogiem assetów, widokiem,
  rendererem i ich testami.

Wykonaj zadanie od specyfikacji brakujących decyzji aż do integracji, testów i
dokumentacji. Pracuj etapami z tego planu, nie omijaj bramek zgody i nie
deklaruj ukończenia kroków, których rzeczywiście nie wykonano.

### Kontekst i niezmienne wymagania

- Rozpad `FIRE_RAIN` jest już odpowiedzialnością symulacji. Użyj istniejącego
  `ProjectileSplit`, identyfikatorów pocisków i `EffectSnapshot`; nie
  implementuj ponownie rozpadu.
- Wyjątek gameplayowy zatwierdzony przez użytkownika: ticki płonącego pola
  ranią jednostki obu drużyn. Bezpośredni impact przy uderzeniu zachowuje
  dotychczasowe zasady. Czas `LingeringArea` pozostaje obecnie równy 3 s;
  ewentualny późniejszy dobór czasu należy do testów balansu.
- Po `ProjectileSplit` pocisk główny nadal istnieje i kontynuuje symulację,
  ale ma być ukryty wizualnie. Widoczne zostaje sześć mniejszych fragmentów.
- Zatwierdzony art direction: styl pasujący do istniejących sprite'ów
  Urkaaaz; użytkownik pozwolił dołączać `fire_rain_icon.png` jako referencję
  wizualną do promptów AnimGen.
  Fragmenty to małe, ciemne kamienne odłamki z pomarańczowym żarem.
- Nie rysuj ani nie generuj ręcznie klatek bazowych lub animacji. Sekwencje
  animacji ma wygenerować AnimGen; przygotowuj wyłącznie prompty tekstowe.
- Płomień wygasa w ostatnich 500 ms czasu z `EffectSnapshot`. Podczas pauzy
  lokalne zegary prezentacji zatrzymują się i po wznowieniu kontynuują od tej
  samej klatki.
- Nie dodawaj nowych gameplayowych eventów, pól do `MatchSnapshot`, drugiego
  katalogu amunicji, równoległego renderera ani logiki gameplayowej do
  Android UI.
- Wygeneruj jeden neutralny zestaw prezentacyjny współdzielony przez BLUE i
  RED, obejmujący: lot pojemnika, błysk rozpadu, lot fragmentu oraz zapłon,
  pętlę i wygaszenie ognia.
- Wszystkie efekty battlefield są w world-space i korzystają z istniejącej
  transformacji kamery dokładnie raz. HUD pozostaje bez zmian.
- Rozszerz istniejący `BattlefieldAssetCatalog`, `RenderStateMapper`,
  `BattlefieldView` i `BattlefieldRenderer` tylko w zakresie potrzebnym do
  tej funkcji. Render state jest niemutowalny; symulacja pozostaje jedynym
  właścicielem pozycji, ruchu, czasu i stanu gameplayowego.
- Nie używaj ROCK ani starych klatek FIRE_RAIN jako cichego fallbacku.
  Brakujące, uszkodzone lub niekompletne assety mają dawać jawny, konkretny
  błąd.
- Zachowaj wszystkie istniejące, niepowiązane zmiany użytkownika. Nie
  nadpisuj, nie cofaj i nie porządkuj ich przy okazji.

### Tryb pracy i bramki zgody

1. Zacznij od odczytania dokumentów oraz `git status --short`. Sprawdź
   istniejące zmiany i integruj się z aktualnym stanem bez ich cofania.
2. Wypisz sobie etapy i ich zależności, a następnie wykonuj je kolejno.
   Pytaj użytkownika pojedynczo wyłącznie o nierozstrzygnięte decyzje
   wymagane do kontynuacji. Nie pytaj ponownie o decyzje już zapisane w
   dokumentacji lub historii.
3. Przed każdą płatną generacją AnimGen uzyskaj świeżą wycenę i pokaż jej
   koszt w kredytach oraz dostępne saldo. **Zatrzymaj się i czekaj na
   wyraźną zgodę dla tego konkretnego zadania**, zanim wyślesz żądanie
   tworzące animację. Zgoda na jedną fazę nie zatwierdza kolejnych faz.
   Jeśli wycena wzrośnie, ponownie poproś o zgodę.
4. Generuj tylko jedno zadanie naraz. Po każdej fazie pokaż kompletny
   podgląd i czekaj na osobną, wyraźną zgodę na import. Nie importuj
   niezaakceptowanego wyniku. Zmiana promptu lub ustawień oznacza nowe
   zadanie, nową wycenę i nową zgodę.
5. Po dojściu do bramki, której nie można przekroczyć bez odpowiedzi lub
   zgody użytkownika, zatrzymaj się. Podaj dokładnie, co jest potrzebne i
   co zostało ukończone; po otrzymaniu odpowiedzi kontynuuj od tej bramki,
   nie powtarzając bez potrzeby zaakceptowanych etapów.
6. Nie kupuj kredytów, nie uruchamiaj płatnej generacji bez zgody, nie
   importuj bez akceptacji podglądu i nie commituj ani nie pushuj, chyba że
   użytkownik osobno i wyraźnie o to poprosi.

### Etap 1 — specyfikacja i prompty animacji

Przejrzyj sekcje 5, 7, 13 i 14 dokumentu architektury. Nie pytaj ponownie o
decyzje zatwierdzone wyżej. Przygotuj i zapisz osobne prompty tekstowe dla
lotu pojemnika, błysku rozpadu, lotu fragmentu oraz zapłonu/pętli/wygaszenia
pola ognia. Nie twórz ani nie przerabiaj obrazów lub klatek. Możesz dołączyć
`fire_rain_icon.png` jako referencję obrazu tylko w trybie obsługiwanym przez
wybrany model; nie używaj starych klatek FIRE_RAIN.

Prompty tekstowe pokaż użytkownikowi do korekty. Nie używaj istniejących
klatek FIRE_RAIN jako źródeł.
Parametry canvasu, anchoru, marginesów, rozmiaru i eksportu dobierz po
inspekcji renderera oraz live capabilities modelu.
Jeśli model wymaga osobnej klatki pierwszej i nie obsługuje ikony jako
referencji obrazu, zatrzymaj się i poproś o decyzję zamiast tworzyć obraz
wejściowy.

Zapisz prompty tekstowe w `docs/systems/fire-rain-animgen-prompts.md`.
Prompty nie oznaczają zgody na płatną generację; przed wyceną i tworzeniem
animacji nadal wymagane są osobne bramki zgody.

### Etap 2 — generowanie i przegląd w AnimGen

Użyj oficjalnego AnimGen Public API pod
`https://api.animgen.com/v1`. Przed użyciem API przeczytaj aktualne oficjalne
dokumenty endpointów i pobierz bieżący katalog modeli. Nie zakładaj z góry
identyfikatora modelu, jego limitów, kosztów ani obsługiwanych ustawień.

Wymagania bezpieczeństwa i płatności:

- Wymagaj klucza `ANIMGEN_API_KEY` z prywatnego środowiska albo odczytaj
  wpis macOS Keychain `AnimGen API Key`. Nigdy nie proś użytkownika o
  wklejenie klucza do rozmowy, nie drukuj go, nie przekazuj w argumentach
  poleceń i nie zapisuj go w repozytorium.
- Sprawdź uprawnienia konta i saldo. Nie kupuj kredytów.
- Wysyłaj wyłącznie zatwierdzone źródło wizualne i prompt bieżącej fazy.
  Nie wysyłaj kodu repozytorium, niezwiązanych assetów ani sekretów.
- Sprawdź aktualny model i jego obsługę klatek wejściowych, proporcji,
  rozdzielczości, przezroczystości oraz eksportu klatek. Użyj
  `frames_zip`, jeśli obsługuje go wybrany model.
- Zbuduj request dla jednej fazy. Użyj identycznego body do wyceny i
  tworzenia. Najpierw pobierz świeżą wycenę, pokaż ją użytkownikowi i czekaj
  na jego zgodę. Przy tworzeniu użyj idempotency key i zachowaj klucz,
  request oraz task ID w prywatnym katalogu sesji.
- Odpytuj zadanie zgodnie z `Retry-After` i z ograniczonym terminem.
  Wznawiaj niejednoznaczne żądanie tym samym kluczem i body; nigdy nie
  twórz po cichu drugiego płatnego zadania.
- Pobieraj wyniki po identyfikatorach assetów. Traktuj podpisane URL-e jako
  tymczasowe sekrety: nie loguj ich i nie wysyłaj do nich klucza API.
- Po zakończeniu pokaż podgląd wszystkich klatek. Oceń clipping, przezroczyste
  tło, alpha, halo, spójność anchoru i skali, czytelność sylwetki, ciągłość
  ruchu i dopasowanie do briefu. Nie naprawiaj clippingu przez przycinanie.
  Odrzuć wadliwy wynik i przed kolejną płatną próbą poproś o nową zgodę na
  nową wycenę.

Generuj i zatwierdzaj fazy osobno. Wynik jednej fazy nie jest zgodą na
wygenerowanie ani import pozostałych.

### Etap 3 — walidacja i archiwizacja assetów

Po akceptacji podglądu oraz zgodzie na import sprawdź integralność archiwum,
liczbę i kolejność klatek, ciągłość indeksów od zera, format PNG, wymiary,
rzeczywisty kanał RGBA, gdy wymagano alpha, stabilny anchor i bezpieczne
marginesy. Obejrzyj każdą klatkę i kontakt sheet. Zachowaj zatwierdzone
oryginały/podglądy w `assets_src/ammunition/fire-rain/`, jeśli ta lokalizacja
jest zgodna z aktualnym układem repozytorium, i kopiuj do runtime tylko
zatwierdzone, zwalidowane klatki.

Zastosuj nazwy zgodne z istniejącymi konwencjami Android drawable i
`BattlefieldAssetCatalog`. Nie zmieniaj ani nie zastępuj istniejących
animacji bez wyraźnej decyzji użytkownika.

### Etap 4 — katalog assetów i render state

Rozszerz istniejący `BattlefieldAssetCatalog` o osobne fazy nowego zestawu
FIRE_RAIN i walidację kompletności. Brak fazy, dziura w indeksach lub
niepoprawny plik ma wskazywać konkretną fazę i indeks; nie pomijaj błędu i
nie podstawiaj innej amunicji.

Rozszerz wyłącznie Androidowy read model/render state o dane potrzebne
widokowi:

- `matchId` do wykrywania nowego meczu;
- eventy `ProjectileSplit` do jednorazowego efektu rozpadu;
- aktywne efekty FIRE_RAIN mapowane ze wszystkich pasujących
  `EffectSnapshot`.

Nie dodawaj gameplayowych kontraktów ani pól do `MatchSnapshot`. Zapewnij
obsługę wielu rozpadów i wielu równoczesnych pól ognia w tym samym batchu.

### Etap 5 — tracker i lifecycle widoku

W `BattlefieldView` dodaj lub rozszerz minimalny stan prezentacyjny,
kluczowany stabilnymi ID:

- śledź efekt rozpadu po ID eventu;
- oznacz sześć potomnych projectile ID jako fragmenty, dopóki występują w
  snapshotach;
- śledź fazę i lokalny postęp animacji ognia według `effectId`;
- czyść wpisy po zniknięciu odpowiadającego pocisku/efektu;
- resetuj cały stan przy zmianie `matchId` i zwolnij go po zakończeniu lub
  zniszczeniu widoku;
- zastosuj uzgodnione zachowanie pause/resume.

Snapshot pozostaje jedynym źródłem pozycji i czasu gameplayowego. Tracker
nie przechowuje kopii pozycji, prędkości, obrażeń ani czasu efektów. Obsłuż
powtórzone renderowanie tych samych danych bez restartowania lub
duplikowania animacji.

### Etap 6 — rendering

Rozszerz istniejący `BattlefieldRenderer`, zachowując warstwy i kamerę
projektu. Rysuj pojemnik do chwili rozpadu, potem błysk i sześć fragmentów w
ich aktualnych pozycjach; pocisk główny po evencie jest niewidoczny.
Każde `EffectSnapshot` FIRE_RAIN powinno mieć niezależny zapłon i pętlę
zgodną z jego pozostałym czasem, z fade w ostatnich 500 ms. Prezentacja nie
wydłuża czasu gameplayowego. Użyj zatwierdzonej kolejności warstw; nie
dodawaj kontrolek HUD.

Zweryfikuj ręcznie podgląd pan/zoom/resize i widoczność sześciu torów
fragmentów po zniknięciu głównego pocisku z prezentacji.

### Etap 7 — testy, dokumentacja i finalna walidacja

Dodaj lub rozszerz focused testy dla:

- kompletności i poprawności każdej sekwencji assetów oraz jawnych błędów;
- mappera dla jednego i wielu eventów rozpadu oraz wielu równoczesnych
  efektów;
- przypisania animacji fragmentów po ich projectile ID oraz wizualnego
  ukrycia pocisku głównego po rozpadzie;
- retencji i czyszczenia efektów po zniknięciu ID;
- resetu `matchId`, pauzy/wznowienia zgodnie z decyzją oraz powtórzonych
  snapshotów/eventów;
- ticków obrażeń FIRE_RAIN dla jednostek obu drużyn przy niezmienionych
  zasadach bezpośredniego impactu;
- world-space i wspólnej transformacji kamery, jeśli istniejące testy
  pozwalają sprawdzić tę granicę;
- braku mutacji gameplayu i braku zależności Androida w core.

Zaktualizuj `docs/systems/fire-rain-presentation.md` o rozstrzygnięte
decyzje i rzeczywiście dostarczone zachowanie oraz `docs/PROJECT_KNOWLEDGE.md`
o trwałe fakty dotyczące assetów i renderera. Nie opisuj planowanych
elementów jako ukończonych.

Uruchom wymagane sprawdzenia:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Nie commituj ani nie pushuj. W podsumowaniu podaj ukończone etapy, zmienione
pliki, wyniki testów i walidacji oraz ewentualne elementy zablokowane przez
brak assetu, błąd AnimGen albo oczekującą zgodę użytkownika.

### Zasady zatrzymania

Zatrzymaj się i zgłoś konkretny blocker, jeśli brakuje klucza API lub zakresu
uprawnień, model nie obsługuje wymaganej jakości/alpha, saldo jest
niewystarczające, wynik jest niekompletny, użytkownik nie zaakceptował
wyceny lub podglądu, albo nie można bezpiecznie wznowić niejednoznacznego
zadania. Nie symuluj ukończenia brakujących etapów placeholderami.

---
