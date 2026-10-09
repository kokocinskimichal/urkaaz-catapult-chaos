# M8 — Kopiowanie assetów

## Zakres wykonany

Do nowego projektu skopiowano pierwszą kompletną grupę assetów pocisku:

```text
rock_flight_00.png ... rock_flight_31.png
rock_impact_00.png ... rock_impact_31.png
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
fortress_left.png
fortress_right.png
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
