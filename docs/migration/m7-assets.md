# M7 — Inwentaryzacja assetów

## Cel

M7 rejestruje assety starego projektu przed ich kopiowaniem. Etap rozdziela
źródła runtime, źródła do przetworzenia, artefakty podglądowe i zasoby, które
trzeba odtworzyć po stronie nowego UI.

## Rejestr

Pełny rejestr znajduje się w:

```text
docs/m7-asset-inventory.csv
```

Został wygenerowany przez:

```bash
python3 tools/inventory_assets.py \
  /Users/michalkokocinski/workspace/orc-catapult \
  docs/m7-asset-inventory.csv
```

Każdy wiersz zawiera ścieżkę źródłową, kategorię, typ, format, rozdzielczość,
informację o kanale alfa, density/scaling, użycie oraz decyzję migracyjną.

## Kategorie

```text
terrain, fortresses, catapults, units, projectiles, spells,
effects, ui, icons, audio, fonts, backgrounds, tutorial, unclassified
```

## Reguły decyzji

- `copy_or_process` — kandydat do skopiowania lub przetworzenia w M8;
- `recreate_or_review` — zasób Androida, który wymaga właściciela w nowym UI;
- `review` — nazwa lub pochodzenie wymaga ręcznej decyzji;
- pliki preview, debug, tymczasowe i contact sheety nie są kopiowane automatycznie;
- M7 nie zmienia starego projektu i nie kopiuje assetów do `android-app`.

## Źródło i licencja

Rejestr przechowuje ścieżkę źródłową, ale nie rozstrzyga praw do assetu.
Pozycje bez potwierdzonego źródła lub licencji muszą pozostać w stanie
`review` przed M8.
