# M4 — Warstwa symulacji

## Zakres

M4 wprowadza pierwszy deterministyczny wycinek symulacji bez kopiowania
starego `GameEngine`. Warstwa obsługuje upływ czasu, ruch pocisków, wiatr,
grawitację, kolizje z terenem, kolizje z fortecami, obrażenia, rewizję kraterów,
eventy i publiczne snapshoty.

## Granice odpowiedzialności

- `game-domain` zawiera wartości i reguły, takie jak ruch pocisku i zdrowie;
- `game-simulation` odpowiada za orkiestrację i rozstrzyganie kolizji;
- `game-contracts` udostępnia snapshoty i eventy;
- Android, rendering, assety, persystencja i AI pozostają poza tym wycinkiem.

## Determinizm

Symulacja postępuje wyłącznie po wywołaniu
`advance(deltaMilliseconds)`. Nie korzysta z zegara ściennego, korutyn,
źródła losowości ani zależności Androida. Dla tego samego stanu początkowego
oraz tej samej sekwencji komend i kroków czasu eventy i snapshoty są identyczne.

## Celowe ograniczenia

M4 używa płaskiego terenu bazowego z deterministycznymi próbkami kraterów oraz
dwóch domyślnych fortec i katapult. Jednostki, magazyny amunicji, zmiana tury,
efekty specjalne i reguły kampanii pozostają w kolejnych etapach.
