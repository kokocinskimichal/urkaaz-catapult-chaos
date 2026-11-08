# Prompt implementacyjny: ciągłość animacji postaci

Przeczytaj najpierw
[`unit-animation-fallback-plan.md`](unit-animation-fallback-plan.md),
`AGENTS.md`,
`docs/architecture/architecture.md` i
`docs/architecture/implementation-rules.md`. Następnie sprawdź aktualne
implementacje `AsyncAssetCache`, `BitmapFrameSequence`,
`BattlefieldAssetCatalog`, `BattlefieldRenderer`, `BattlefieldView` oraz start
meczu z `MatchScreenController`.

## Potwierdzona diagnoza

Na emulatorze Pixel 8 log `UnitAnimationFallback` wykazał:

```text
type=DEFENDER state=MOVING animation=move frame=33/60
frameStatus=PENDING fallbackStatus=PENDING
```

oraz późniejsze braki klatki wcześniej obecnej w cache:

```text
frame=0/60 frameStatus=CACHE_MISS_AFTER_READY
fallbackStatus=PENDING
```

Gdy oba źródła bitmapy są niedostępne, renderer zastępuje postać kolorowym
okręgiem. To błąd prezentacji; nie zmieniaj stanu jednostki ani symulacji.
Pierwsze żądanie ruchu może wskazywać dowolny indeks sekwencji, np. 33, więc
nie zakładaj, że samo przygotowanie klatki 0 wystarczy.

## Zadanie

Zaimplementuj mały, poziomowy zestaw gotowych wizualnych fallbacków oraz
zachowanie ostatniej gotowej prezentacji jednostki podczas braków klatek.
Zapobiegaj kolorowemu okręgowi dla zwykłego stanu asynchronicznego; zachowaj
jawne błędy rzeczywiście brakujących lub uszkodzonych wymaganych assetów.

## Wymagania

1. Zestaw startowy wyznacz z istniejących kontraktów:
   - BLUE: zatwierdzone `PlayerLoadoutConfig.unitTypes`;
   - RED: poziomowe `MatchScenario.availableUnits`;
   - demo/test: rzeczywisty dozwolony zestaw danej sesji.
   Przekaż typy osobno dla drużyn, bez przygotowywania nieużywanych kombinacji
   typ/drużyna. Nie twórz drugiej definicji dostępnych jednostek.
2. Przygotuj w `BattlefieldAssetCatalog` po jednym bezpiecznym sprite'cie
   startowym dla każdego wymaganego typu/drużyny. Użyj istniejącego fallbacku,
   jeśli jest dostępny; dla typów bez niezależnej grafiki zapasowej przygotuj
   klatkę idle. Dekodowanie, maski i tekstury muszą być gotowe poza `onDraw`.
3. Przed `MatchCommand.Start` pokaż ekran ładowania ze spinnerem i postępem
   dla tych wymaganych assetów. Uruchom sesję dopiero po sukcesie. Błędy
   dekodowania pokaż jawnie z retry/powrotem; nie ukrywaj ich pustym sukcesem.
   Użyj lifecycle ekranu i nie dodawaj kontrolek do `MainActivity`.
4. W warstwie UI/renderingu przechowuj ostatnią gotową prezentację jednostki
   per `matchGeneration` i `entityId`. Zachowaj razem bitmapę i pełne parametry
   rysowania użytej sekwencji: rozmiar/skala, pozycję X, kotwicę i dolny
   padding. Ogranicz pamięć do 4 MiB access-order LRU ważonego
   `Bitmap.allocationByteCount`. Przy `PENDING`, `CACHE_MISS_AFTER_READY` lub
   `QUEUE_REJECTED` rysuj ją do czasu uzyskania nowej klatki. Dla pierwszej
   klatki lub po evikcji użyj przygotowanego sprite'a startowego.
5. Czyść stan po zniknięciu jednostki, restarcie generacji, zmianie typu/drużyny
   i zakończeniu death retention. Zablokuj przenikanie klatek między encjami.
6. Zachowaj obecny bounded LRU oraz prefetch dwóch klatek. Nie zwiększaj
   globalnego budżetu bez pomiaru, nie preloaduj pełnych sekwencji i nie
   resetuj globalnych zegarów animacji. Hold ostatnich prezentacji ma własny
   limit 4 MiB i po evikcji wraca do przypiętego fallbacku.
7. Zachowaj debugowe logi `UnitAnimationFallback` tylko dla rzeczywistego
   okręgu. Dodaj ograniczony wpis `unit_sprite_frame_held` dla pending/cache
   miss, aby emulator pozwalał potwierdzić, że trzymanie klatki zadziałało.
   Ogranicz duplikaty do początku epizodu lub zmiany przyczyny; nie loguj co
   render tick.

## Testy i weryfikacja

- Rozszerz `AsyncAssetCacheTest` o statusy rozróżniające cold pending,
  odrzucenie kolejki i cache miss po wcześniejszej gotowości.
- Dodaj testy state-hold: ta sama jednostka zachowuje ostatni frame przy
  pending, nowa jednostka/drużyna/generacja nie dziedziczy go, po gotowości
  bieżąca klatka zastępuje poprzednią, wpisy respektują byte budget/LRU, a
  reset/retencja śmierci usuwa wpis.
- Sprawdź debug i release gating logów oraz jawne zachowanie dla missing/failed
  assetu.
- Na emulatorze uruchom test poziomu 1 i poziomu 13; obserwuj
  `adb logcat -v time -s UnitAnimationFallback`. Potwierdź, że brak klatki może
  wywołać `unit_sprite_frame_held`, ale nie `unit_sprite_fallback`, a sylwetka
  pozostaje widoczna. Zapisz krótki wynik i odróżnij cold loads od
  `CACHE_MISS_AFTER_READY`.

Uruchom:

```bash
./gradlew test assembleDebug --quiet
git diff --check
git status --short
```

Nie commituj ani nie pushuj bez wyraźnej prośby użytkownika.
