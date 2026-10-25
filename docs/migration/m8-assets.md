# M8 — Kopiowanie assetów

## Zakres wykonany

Do nowego projektu skopiowano pierwszą kompletną grupę assetów pocisku:

```text
anim_ammunition_rock_flight_000.png ... anim_ammunition_rock_flight_031.png
anim_ammunition_rock_impact_000.png ... anim_ammunition_rock_impact_031.png
```

Lokalizacja docelowa:

```text
android-app/src/main/res/drawable-nodpi/
```

Zachowano oryginalne nazwy, kolejność klatek, format PNG, kanał alfa i
skalowanie `nodpi`.

Dodano również assety używane przez pierwszy renderer:

```text
terrain_soil_fill_00.png
terrain_grass_cap.png
fortress_chapter_01_left.png
fortress_chapter_01_right.png
catapult_left.png
catapult_right.png
```

## Manifest

Źródło, cel, liczba klatek i właściciel są zapisane w:

```text
docs/m8-asset-manifest.csv
```

## Świadomie odłożone

Pozostałe assety nie zostały skopiowane automatycznie. Nowy projekt nie ma
jeszcze renderera ani ekranów, dlatego kopiowanie fortec, jednostek, terenu,
czarów i assetów UI bez właściciela byłoby przedwczesne. Ich migracja nastąpi
po utworzeniu odpowiednich adapterów w kolejnych etapach.
