# Plan: rozmiary tekstu kontrolek zależne od języka

> Prompt implementacyjny: [`localized-button-typography-implementation-prompt.md`](localized-button-typography-implementation-prompt.md).

## Status

**Wdrożono pełny katalog 106 etykiet interaktywnych kontrolek** dla EN, PL i
ES. Studio grupuje je według ekranu oraz w kategorii Common. Teksty pozostają
w `strings.xml`; Studio edytuje wyłącznie locale-specific `dimen`.

## Zakres

Typografią zarządza każdy lokalizowany tekst widoczny jako etykieta lub wartość
w interaktywnym elemencie Android UI: przyciski i CTA, klikalne zakładki i
karty, nazwy w kaflach wyboru jednostek/amunicji/czarów, etykiety checkboxów,
akcje dialogów, overlayów, wyniku meczu, tutoriala i ekranu testowego.

Poza zakresem są teksty wyłącznie w `contentDescription`, nieinteraktywne
nagłówki i opisy, samodzielne komunikaty statusu oraz czysto liczbowe badge'e
i kontrolki ikonowe bez widocznego tekstu. Układ kontrolek, treść tłumaczeń,
nawigacja i mechanika gry nie zmieniają się.

Kategorie Studio:

- **Common** — etykiety współdzielone przez ekrany, w tym Wstecz i nazwy
  jednostek, amunicji oraz czarów w kartach;
- **Dashboard**, **Campaign**, **Loadout**, **Shop**, **Match**, **Tutorial**
  i **Test mode** — pozostałe etykiety przypisane do ekranu lub obszaru.

## Kontrakt Androida i katalog kontrolek

Teksty pozostają w `strings.xml`, `values-pl/strings.xml` i
`values-es/strings.xml`. Rozmiary są osobnymi zasobami w:

```text
android-app/src/main/res/values/button_typography.xml
android-app/src/main/res/values-pl/button_typography.xml
android-app/src/main/res/values-es/button_typography.xml
```

Każda wizualna kontrolka ma stabilny `controlId`, kategorię i `stringKey`.
`controlId` jest osobny od klucza tłumaczenia, bo ten sam string może być
wyświetlany w różnych kontrolkach i wymagać innego rozmiaru. Przykładowy
zasób:

```xml
<dimen name="button_text_size_dashboard_test_mode">14sp</dimen>
```

Wspólny katalog `BUTTON_TYPOGRAPHY_CONTROLS` w kontrakcie Studio definiuje
identyfikator, kategorię i klucz tłumaczenia; Android ma odpowiadający mu
typowany `ButtonTypographyControl` z jawnymi referencjami `R.string` i
`R.dimen`. Nie używać `Resources.getIdentifier`. Wszystkie trzy locale muszą
zawierać identyczny zestaw control IDs i jawne wartości. Walidowany zakres to
`8–32sp`; `sp` zachowuje systemową skalę tekstu. Karty z istniejącym
autosize używają ustawionej wartości jako maksimum i zachowują dotychczasowe
zmniejszanie do rozmiaru, który mieści się w karcie.

Początkowe rozmiary zachowują dotychczasową typografię kontrolek; pierwszy
potwierdzony problem Dashboardu ma wartości `TEST MODE`: EN `14sp`, PL `11sp`,
ES `9sp`. Pozostałe wartości można dostroić osobno w każdym locale.

## Studio Typography

Zakładka **Typography** pokazuje kontrolki w zwijanych grupach **Common** i
ekranowych. Każdy wpis pokazuje `controlId`, klucz string resource oraz
podgląd tłumaczenia EN/PL/ES tylko do odczytu. Formatowane przykłady, takie jak
poziom, koszt lub czas, otrzymują przykładowe argumenty w podglądzie.

Użytkownik może zmieniać `font size (sp)` niezależnie dla każdego locale,
walidować draft oraz przywracać pojedynczą wartość do wczytanej baseline.
Studio nie edytuje tłumaczeń, wymiarów kontrolek ani assetów. Zapis obowiązuje
po przebudowaniu i ponownym uruchomieniu APK.

Server `typographyCatalog` zapewnia:

- `GET /api/typography` — katalog, podglądy i wspólny hash plików locale;
- `POST /api/typography/validate` — walidację bez zapisu;
- `PUT /api/typography` — zapis tylko przy zgodnym `expectedHash`.

Server waliduje wszystkie control IDs wobec wspólnego katalogu, obecność
tłumaczeń w trzech locale, strukturę XML i zakres wartości. Trzy pliki są
zapisywane jako jedna transakcja z rollbackiem przy błędzie; ścieżki są
ograniczone do zatwierdzonych plików `button_typography.xml`.

## Testy i kryteria akceptacji

### Android

- każdy wpis katalogu ma odpowiadający `R.string` i `R.dimen` w EN/PL/ES;
- test sprawdza poprawne locale rozmiaru i dostępność tłumaczenia dla
  wszystkich control IDs;
- etykiety zachowują pełny tekst, `contentDescription`, cele dotykowe,
  zachowanie nawigacji i istniejące autosize;
- zmiana języka nie wpływa na stan, komendy ani symulację.

### Studio

- grupowanie zachowuje kolejność kategorii i wszystkie wpisy katalogu;
- model zachowuje niezależne wartości locale, liczby dziesiętne i reset do
  baseline;
- podgląd nie zmienia tłumaczeń i formatuje argumenty przykładowe;
- API odrzuca brakujące, zduplikowane i nieznane kontrolki, brak tłumaczeń,
  nieobsługiwane XML/jednostki, wartości poza zakresem i nieznane pola;
- zmiana pliku poza Studio powoduje jawny konflikt `409`; zapis locale nie może
  zakończyć się częściowo.

## Dokumentacja i odbiór

Utrzymuj zgodność z `docs/systems/localization-system.md`,
`docs/architecture/studio-architecture.md`, `studio/README.md`,
`docs/project/current-state.md` i `docs/project/knowledge.md`. Rozszerzenie
katalogu wymaga równoczesnej zmiany kontraktu Studio, `ButtonTypographyControl`,
trzech plików `button_typography.xml` i testu kompletności.

Po zmianie rozmiarów sprawdź w Studio podgląd EN/PL/ES, a w Androidzie
potwierdź dopasowanie etykiet w kontrolkach o ograniczonej szerokości. Studio
nie uruchamia Gradle ani nie zmienia działającej aplikacji.
