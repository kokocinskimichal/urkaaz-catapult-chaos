# Edytor map w Urkaaaz Studio

```sh
cd studio
npm run studio
npm test
npm run lint
npm run build
```

Edytor jest zakładką **Maps**. Kod, testy, model domenowy, kodek JSON, renderer
Canvas i transformacje znajdują się w `studio/web/src/tabs/maps`. Canvas
pokazuje świat 7200 × 4800. Profil terenu, fundamenty i dekoracje są edytowalne
w dowolnej kolejności. Rolką myszy powiększa się teren i jego tło razem;
przeciągnięcie pustego obszaru przesuwa mapę, a `Shift` + przeciągnięcie
pozwala przesuwać ją także nad obiektami. `Y` dekoracji jest niezależną
współrzędną world-space;
można ją zmieniać pionowym przeciągnięciem lub polem numerycznym, a edycja
terenu nie przesuwa obiektów. Relics pozostają ograniczone do gleby. Cap ma
osobny przełącznik **Show cap**/**Hide cap** i nie jest częścią danych mapy.

**Random Map** losuje profil terenu, niezależne wysokości fundamentów i
drzewa/skały/propsy dla aktualnego biomu. **Terrain intensity** ma płynny
suwak 100–600 world units (domyślnie 160), a **Decoration density** ma presety
Low/Medium/High (domyślnie Medium).
Każde losowanie używa seeda, zapisuje go w `generationMetadata`, jest jedną
operacją Undo/Redo i zachowuje istniejące Relics. Generator zmienia wyłącznie
lokalny szkic; zapis dla wybranego Chapter/Level nadal odbywa się przez
**Save to repository** i istniejące API z kontrolą hasha.

Mapy wczytuje się z listy repozytorium. Zapis waliduje je po stronie przeglądarki
i serwera, sprawdza hash obu kopii oraz aktualizuje
`game-campaign/src/main/resources/maps/` i `android-app/src/main/assets/maps/`.
Konflikt plików zwraca `409` i nie nadpisuje zewnętrznych zmian. Autosave
pozostaje lokalny w `localStorage` i nie zapisuje po cichu pliku repozytorium.
Akcja Save otwiera dialog z dropdownami Chapter i globalnego Level. Wybrany
poziom zapisuje mapę pod ID `chapter<ordinal>-level<level>`; nadpisanie
istniejącej mapy korzysta z jej bieżącego hash pair. Kampania wybiera mapę o
tym ID, gdy jest dostępna w zasobach, a w przeciwnym razie zachowuje
tymczasowy `demo-map`.

Indeks i obrazy assetów są udostępniane bez kopiowania przez Studio API,
bezpośrednio z `game-assets` i wybranych drawable Androida. Import i eksport
przez pobieranie plików przeglądarki zostały zastąpione odczytem i zapisem
repozytorium.

Kodek zachowuje początkową kotwicę i zoom kamery oraz opcjonalne
`generationMetadata.authoringVersion`; starsze mapy bez tych pól otrzymują
domyślne kadrowanie fortecy gracza i zoom 1×. Walidator Kotlin nadal pozostaje
autorytatywny dla runtime i jest sprawdzany przez bramkę Gradle.
