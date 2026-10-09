# M3 — Przeniesienie domeny gry

Status planu: `ACCEPTED`

## Cel

M3 przenosi znaczenie podstawowych pojęć i reguł gry do czystego modułu
`game-domain`. Domena nie zna Androida, renderingu, assetów ani starego
`GameEngine`.

## Zakres implementacji

W M3 przenosimy:

- wartości świata i pozycje;
- prędkość pocisku;
- zdrowie;
- zasoby;
- celowanie;
- granice świata;
- amunicję i efekty amunicji;
- katapulty;
- fortece;
- pociski;
- jednostki;
- typy jednostek;
- czary i kategorie czarów;
- loadout czarów;
- lifecycle stanu meczu.

## Właściciel kodu

```text
game-domain/src/main/kotlin/com/urkaaaz/domain/
```

Domena może korzystać wyłącznie z neutralnych kontraktów i standardowej
biblioteki Kotlin/JVM. Nie może importować:

```text
android.*
androidx.*
GameEngine
GameView
Canvas
Bitmap
Context
```

## Reguły domenowe

### Zdrowie

`Health` pilnuje, aby:

- maksimum było dodatnie;
- bieżące zdrowie mieściło się w zakresie `0..maximum`;
- obrażenia nie obniżały zdrowia poniżej zera;
- leczenie nie przekraczało maksimum.

### Zasoby

`ResourceWallet` obsługuje:

- złoto;
- manę;
- ograniczoną amunicję;
- amunicję nielimitowaną;
- sprawdzenie i zużycie zasobu.

### Celowanie

`Aim` waliduje:

- kierunek w zakresie `0..180` stopni;
- siłę w zakresie `0..100`.

### Jednostki i obiekty

`Catapult`, `Fortress`, `Unit` i `Projectile` posiadają:

- typowany identyfikator;
- drużynę;
- pozycję;
- stan zdrowia, jeśli dotyczy;
- operacje domenowe, takie jak obrażenia, celowanie i przesunięcie.

### Lifecycle meczu

`MatchState` obsługuje przejścia:

```text
CREATED
    └── start() → PLAYER_TURN
                         └── finish() → FINISHED
```

Nie pozwala rozpocząć meczu, który nie jest w fazie `CREATED`.

## Źródła starego projektu

Znaczenie i parametry zostały przeanalizowane na podstawie:

```text
domain/Catapult.kt
domain/Projectile.kt
domain/WorldModels.kt
domain/MatchModels.kt
domain/ObjectiveModels.kt
ammo/AmmoType.kt
units/UnitType.kt
spells/SpellType.kt
```

Nie skopiowano:

- zależności Androida;
- `R.string` i `R.drawable`;
- mutowalnych kolekcji silnika;
- renderingu;
- callbacków UI;
- klasy `GameEngine`;
- starego podziału pakietów.

## Testy M3

Testy sprawdzają:

- granice zdrowia;
- walidację celowania;
- pozycję wylotu pocisku z katapulty;
- zużywanie amunicji ograniczonej;
- zachowanie amunicji nielimitowanej;
- przesunięcie pocisku z uwzględnieniem wiatru;
- lifecycle meczu;
- niezmienność założeń maksimum zdrowia jednostki i fortecy.

## Bramka M3

M3 można zaakceptować, gdy:

- podstawowe modele domenowe istnieją w `game-domain`;
- wartości gameplayowe nie wymagają Androida;
- reguły zdrowia, zasobów, celowania i lifecycle są testowane;
- domena nie importuje starego pakietu gry;
- nie skopiowano `GameEngine`;
- testy domeny i pozostałych modułów przechodzą;
- APK nadal się buduje.

Po akceptacji można rozpocząć M4 — przeniesienie symulacji.

