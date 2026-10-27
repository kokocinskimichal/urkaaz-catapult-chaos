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
pokazuje świat 7200 × 4800; teren edytuje się przez przeciąganie węzłów profilu,
a zablokowanie terenu odsłania cap.

Mapy wczytuje się z listy repozytorium. Zapis waliduje je po stronie przeglądarki
i serwera, sprawdza hash obu kopii oraz aktualizuje
`game-campaign/src/main/resources/maps/` i `android-app/src/main/assets/maps/`.
Konflikt plików zwraca `409` i nie nadpisuje zewnętrznych zmian. Autosave
pozostaje lokalny w `localStorage` i nie zapisuje po cichu pliku repozytorium.

Indeks i obrazy assetów są udostępniane bez kopiowania przez Studio API,
bezpośrednio z `game-assets` i wybranych drawable Androida. Import i eksport
przez pobieranie plików przeglądarki zostały zastąpione odczytem i zapisem
repozytorium.

Kodek zachowuje początkową kotwicę i zoom kamery oraz opcjonalne
`generationMetadata.authoringVersion`; starsze mapy bez tych pól otrzymują
domyślne kadrowanie fortecy gracza i zoom 1×. Walidator Kotlin nadal pozostaje
autorytatywny dla runtime i jest sprawdzany przez bramkę Gradle.
