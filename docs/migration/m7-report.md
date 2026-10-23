# M7 — Raport inwentaryzacji assetów

**Status:** `ACCEPTED`

## Zaimplementowano

- narzędzie `tools/inventory_assets.py`;
- deterministyczne skanowanie `res/` i `assets_src/` starego projektu;
- klasyfikację assetów według kategorii gameplayu i UI;
- odczyt formatu, rozdzielczości PNG, kanału alfa i density/scaling;
- decyzje `copy_or_process`, `recreate_or_review` i `review`;
- pełny rejestr CSV `docs/m7-asset-inventory.csv`.

## Zasady M8

M8 może kopiować wyłącznie pozycje z jawną decyzją i docelowym właścicielem.
Assety bez znanego źródła/licencji, preview, debugowe i nieużywane warianty
pozostają poza automatycznym kopiowaniem.

## Weryfikacja

Uruchom:

```bash
python3 tools/inventory_assets.py \
  /Users/michalkokocinski/workspace/orc-catapult \
  docs/m7-asset-inventory.csv
```

Następnie sprawdź:

```bash
wc -l docs/m7-asset-inventory.csv
head -5 docs/m7-asset-inventory.csv
```

Etap zatrzymuje się na `READY_FOR_REVIEW` i wymaga jawnej akceptacji przed M8.
