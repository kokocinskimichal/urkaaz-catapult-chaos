# Prompt implementacyjny: typografia etykiet przycisków per język

> Plan i proponowany kontrakt: [`localized-button-typography-plan.md`](localized-button-typography-plan.md).

## Zadanie

Zaimplementuj konfigurowalne rozmiary `sp` dla etykiet przycisków Androida
osobno dla EN, PL i ES oraz umożliw ich edycję w nowej zakładce Typography
Urkaaaz Studio. Rozwiązuj rzeczywiste problemy przepełniania bez zmiany tekstów
tłumaczeń, wymiarów kontrolek ani nawigacji.

## Potwierdzone wymagania

- `strings.xml`, `values-pl/strings.xml` i `values-es/strings.xml` pozostają
  jedynym źródłem tekstów. Nie zmieniaj treści ani argumentów tłumaczeń.
- Rozmiary są osobnymi zasobami Android `dimen` w `sp`, rozdzielonymi na
  `values/`, `values-pl/` i `values-es/`.
- Zakres v1 to wyłącznie etykiety tych przycisków, które audyt EN/PL/ES
  potwierdzi jako przepełnione. Nie refaktoruj wszystkich `TextView`.
- Każdy zarządzany string resource ma jawny `R.dimen` używany przez jego
  przycisk. Nie korzystaj z `Resources.getIdentifier`.
- Zmiana języka nadal używa istniejącego `LocalizedContext` i `recreate()`.
  Rozmiar jest prezentacyjny i nie trafia do domeny, balansu gry ani
  `game-application`.
- Studio edytuje wyłącznie rozmiary. Tłumaczenia są widoczne jako podgląd
  tylko do odczytu.
- Zachowaj istniejące rozmiary jako wartości początkowe; zmniejszaj tylko
  etykiety, które nie mieszczą się w swoim przycisku.
- Wartości są podawane w `sp`, aby nadal respektować systemową skalę tekstu.
- Nie commituj ani nie pushuj.

## Audyt przed zmianami

Sprawdź `git status --short` i zachowaj wszystkie istniejące zmiany użytkownika.
Przeczytaj obowiązujące `AGENTS.md`, `docs/architecture/architecture.md`,
`docs/architecture/implementation-rules.md`,
`docs/systems/localization-system.md`,
`docs/systems/localized-button-typography-plan.md`,
`docs/architecture/studio-architecture.md` i `studio/README.md`.
Prześledź wszystkie widoczne dla gracza przyciski w Android UI, ich string
resource IDs, szerokości, obecne wartości `textSize` oraz lokalizowane teksty.
Zapisz listę wyłącznie tych etykiet, które rzeczywiście wymagają korekty.

`values-pl` i `values-es` są zasobami wybieranymi przez istniejący
`LocalizedContext`. Nie twórz drugiego systemu języka ani nie kopiuj tekstów
do Studio.

## Kontrakt Android resources

Dodaj osobne pliki:

```text
android-app/src/main/res/values/button_typography.xml
android-app/src/main/res/values-pl/button_typography.xml
android-app/src/main/res/values-es/button_typography.xml
```

Wpisy mają używać konwencji:

```xml
<dimen name="button_text_size_dashboard_campaign_action">28sp</dimen>
```

Sufiks odpowiada istniejącej nazwie string resource. Wszystkie trzy pliki
muszą zawierać ten sam zestaw zarządzanych wpisów; baza `values/` określa EN.
Server może wyprowadzać klucz podglądu z tej konwencji i weryfikować go wobec
`strings.xml`. W XML dopuszczaj wyłącznie zarządzane `<dimen>` w `sp`;
nie zapisuj opisów, tekstów ani innych stylów w tych plikach.

Przepnij tylko wybrane przyciski na jawne `R.dimen` zasoby, np. przekazując
rozmiar do istniejącego helpera budującego przycisk. Nie zmieniaj paddingu,
szerokości ani wysokości przycisku. Jeżeli ten sam klucz tekstu jest używany
w kilku kontrolkach o różnych ograniczeniach, dodaj stabilne control IDs i
jawną mapę control ID → string/dimen resources z testami. Nie wyprowadzaj
identyfikatorów zasobów dynamicznie.

Plan proponuje walidację `8–32sp`, ale granice nie zostały jeszcze
zatwierdzone. Sprawdź wartości obecne i testowane; jeśli poprawne dopasowanie
wymaga wyjścia poza ten zakres, zapytaj użytkownika przed zmianą granic lub
cichym ograniczaniem wartości.

## Studio Typography

Dodaj zakładkę **Typography** przez istniejący rejestr `studio/web/src/shell/tabs.ts`.
Nie dodawaj edytora tłumaczeń ani nie łącz rozmiarów z Balance.

Zakładka powinna:

- prezentować klucz stabilny oraz tekst EN/PL/ES pobrany tylko do odczytu z
  istniejących plików `strings.xml`;
- pokazywać osobne pole liczbowe `font size (sp)` dla każdego języka;
- prezentować porównawczy podgląd etykiety w przycisku i umożliwiać reset
  pojedynczego języka lub całego wiersza do wczytanej baseline;
- ograniczać edycję do etykiet z typografii przycisków.

Dodaj `typographyCatalog` w Studio Server z:

- `GET /api/typography` — wartości trzech locale, teksty podglądu,
  `expectedHash` i błędy synchronizacji, jeśli zasoby nie są spójne;
- `POST /api/typography/validate` — autorytatywna walidacja bez zapisu;
- `PUT /api/typography` — zapis wartości tylko przy zgodnym `expectedHash`.

Użyj parsera XML z walidacją struktury; nie modyfikuj zasobów przez
niezweryfikowane podmiany tekstowe. Server może zapisywać wyłącznie trzy
`button_typography.xml` w allowlistowanych katalogach Androida. Zmiana wszystkich
locale jest jedną transakcją: przygotuj pliki tymczasowe, atomowo podmień
z rollbackiem przy błędzie i odrzuć częściowy zapis. Konflikt ma zwracać
`409` bez utraty draftu.

## Walidacja

Server odrzuca:

- brakujące lub zduplikowane klucze w którymkolwiek locale;
- wpis wymiaru bez odpowiadającego string resource albo klucz spoza zakresu
  przycisków wybranego w audycie;
- nieobsługiwane tagi XML, nieznane atrybuty, nieznane locale i jednostki inne
  niż `sp`;
- niekończące się, niedodatnie lub poza zatwierdzonym zakresem wartości;
- próbę zmiany `strings.xml` albo ścieżki poza allowlistą.

Ścieżka błędu powinna identyfikować locale i resource name. Wszystkie trzy
locale mają takie same zarządzane klucze i wartości liczbowe. Dla każdej
etykiety podgląd powinien korzystać z rzeczywistego tłumaczenia, nie z ręcznej
kopii.

## Testy

### Android

- sprawdź kompletność `dimen` keys w EN/PL/ES i ich zgodność z istniejącymi
  string resources;
- testuj odczyt locale-specific `R.dimen` przez zlokalizowany kontekst;
- sprawdź, że przepięte przyciski nadal używają poprawnego string resource,
  zachowują `contentDescription`, minimum 48 dp targetu i nie ucinają tekstu;
- potwierdź, że zmiana nie wpływa na język, nawigację, komendy ani symulację.

### Studio

- testy modelu pokrywają niezależną zmianę każdej komórki, zapis dziesiętnych
  wartości `sp` i reset do baseline;
- testy Server obejmują brak/duplikat/nieznany klucz, brak stringa,
  niepoprawne XML/jednostki, wartości graniczne, konflikt hasha i rollback;
- testy UI potwierdzają, że tekst jest tylko do odczytu, a per-language inputs
  nie zmieniają pozostałych locale.

### Odbiór wizualny

Na tej samej konfiguracji urządzenia zrzuty EN/PL/ES muszą potwierdzić, że
wszystkie wybrane etykiety mieszczą się w niezmienionych przyciskach. Zachowaj
pełne tłumaczenia i istniejącą typografię poza wskazanymi etykietami.

## Dokumentacja i kryteria akceptacji

Zaktualizuj bezpośrednio powiązane dokumenty:
`docs/systems/localization-system.md`,
`docs/systems/localized-button-typography-plan.md`,
`docs/architecture/studio-architecture.md`, `studio/README.md`,
`docs/project/current-state.md`, `docs/project/knowledge.md` i
`docs/project/CHANGELOG.md`. Zaktualizuj `AGENTS.md` i
`docs/agent-context-index.md`, jeśli nowe pliki lub zakres tego wymagają.

Zmiana jest gotowa, gdy:

1. Android wybiera osobny rozmiar dla każdej zarządzanej etykiety i locale
   przez natywne zasoby `dimen`.
2. Wszystkie wskazane EN/PL/ES etykiety mieszczą się bez zmiany tekstu i
   wymiarów przycisków.
3. Studio pokazuje istniejące tłumaczenia tylko do odczytu i zapisuje rozmiary
   dla każdego języka z walidacją, kontrolą hasha i atomową transakcją.
4. Pozostałe string resources, tutorial JSON, HUD i niepowiązane `TextView`
   pozostają bez zmian.

Uruchom:

```bash
./gradlew test assembleDebug --quiet
cd studio && npm test && npm run lint && npm run build
git diff --check
git status --short
```

Nie implementuj przy okazji zmian treści tłumaczeń, nowych locale, globalnej
typografii ani napraw niezwiązanych z tym zadaniem.
