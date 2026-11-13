# Plan: rozmiary tekstu przycisków zależne od języka

> Prompt implementacyjny: [`localized-button-typography-implementation-prompt.md`](localized-button-typography-implementation-prompt.md).

## Status

**Plan — niezaimplementowany.** Zakres obejmuje Android UI i możliwość
ustawiania rozmiarów w Urkaaaz Studio. Nie edytuje tłumaczeń ani nie zmienia
obecnych wymiarów przycisków.

## Cel

Zapewnić, że etykiety przycisków mieszczą się w istniejących kontrolkach w
językach EN, PL i ES, bez skracania tłumaczeń i bez zmiany układu ekranu.
Rozmiar ma być konfigurowalny oddzielnie dla każdego zarządzanego przycisku i
języka, a ustawienie ma respektować systemową skalę tekstu przez jednostkę
Android `sp`.

## Obecny stan

- Teksty Android UI są w `strings.xml`, `values-pl/strings.xml` i
  `values-es/strings.xml`; LocalizationSystem nie udostępnia edytora tłumaczeń
  w Studio.
- Rozmiary tekstu części kontrolek są ustawiane programowo w Android UI.
  `DashboardScreen.darkActionButton` na przykład używa jednego rozmiaru dla
  różnych tłumaczeń.
- `LocalizedContext` już wybiera locale zasobów i ekran odtwarza się po zmianie
  języka. Android potrafi w tym samym cyklu wybrać locale-specific `dimen`.
- Studio jest lokalnym narzędziem czytającym i zapisującym pliki repozytorium;
  nie jest częścią APK i nie edytuje obecnie zasobów lokalizacyjnych.

## Proponowany kontrakt zasobów

Nie dodawać metadanych do wartości `<string>` — format Android `strings.xml`
nie zapewnia przenośnego pola na rozmiar tekstu. Zachować teksty jako jedyne
źródło tłumaczeń, a rozmiary przechowywać w osobnych plikach wymiarów w tych
samych katalogach locale:

```text
android-app/src/main/res/values/button_typography.xml
android-app/src/main/res/values-pl/button_typography.xml
android-app/src/main/res/values-es/button_typography.xml
```

Wielkość jest zasobem `dimen` z wartością w `sp`, np.:

```xml
<dimen name="button_text_size_dashboard_campaign_action">28sp</dimen>
```

Nazwa wiąże wpis z istniejącym kluczem tekstu, np.
`dashboard_campaign_action`. Android UI odwołuje się do wygenerowanego
`R.dimen` jawnie; nie używa `Resources.getIdentifier`. Rozmiary są ustawiane
per etykieta, nie jako globalny mnożnik języka. Dla każdej zarządzanej etykiety
wszystkie trzy locale mają jawny wpis; `values/` pozostaje bazą EN, a
`values-pl/` i `values-es/` nie polegają na przypadkowym fallbacku do
angielskiego rozmiaru.

W v1 jeden klucz string resource oznacza jeden rozmiar typograficzny we
wszystkich miejscach jego użycia. Audyt musi potwierdzić, że współdzielone
etykiety nie występują w przyciskach o niezgodnych ograniczeniach. Jeśli
występują, należy przed implementacją rozszerzyć kontrakt o jawny stable
control ID → string resource mapping; nie wyprowadzać identyfikatora zasobu
dynamicznie.

Pierwszy zakres obejmuje wyłącznie etykiety przycisków, które audyt wizualny
wskaże jako przepełnione. Nie obejmuje nazw ekranów, HUD-u, opisów
dostępności ani tekstów tutoriala. Napisy i `contentDescription` pozostają
niezmienione.

## Edycja w Studio

Dodać osobną zakładkę **Typography** — nie zakładkę edycji tłumaczeń.
Formularz:

- pokazuje stabilny klucz etykiety oraz jej tłumaczenie EN/PL/ES jako podgląd
  tylko do odczytu;
- pozwala ustawić `font size (sp)` niezależnie dla każdego locale;
- pokazuje porównawczy podgląd etykiety w przycisku, bez edycji tekstu,
  szerokości kontrolki ani assetu tła;
- pozwala przywrócić pojedynczą wartość lub cały wiersz do wczytanej baseline;
- wyświetla błędy walidacji przy odpowiedniej etykiecie i języku.

Studio i Android edytują/czytają te same trzy pliki `button_typography.xml`.
Server `typographyCatalog` zapewnia odczyt, walidację i zapis; nowe trasy
powinny odpowiadać istniejącemu wzorcowi edytorów:

- `GET /api/typography` — wpisy i wspólny hash trzech plików locale;
- `POST /api/typography/validate` — walidacja bez zapisu;
- `PUT /api/typography` — zapis tylko przy zgodnym `expectedHash`.

Zapis trzech plików jest jedną transakcją: najpierw walidacja, następnie
atomowa podmiana z rollbackiem przy błędzie. Server ogranicza ścieżki do
trzech zatwierdzonych katalogów zasobów Androida; nie pozwala edytować
`strings.xml` ani innych plików. Podgląd tekstów pochodzi z istniejących
zasobów EN/PL/ES.

Studio nie uruchamia Gradle ani nie zmienia działającej aplikacji. Zapisane
wartości obowiązują po przebudowaniu i ponownym uruchomieniu APK.

## Walidacja i właściciele

- `strings.xml` pozostaje właścicielem tekstu; per-locale `dimen` jest
  właścicielem wartości typograficznej.
- `android-app` stosuje rozmiar dla jawnie wskazanego zasobu w komponencie
  przycisku. Nie wprowadzać rozmiaru do `game-domain`, balansu gry ani
  `game-application`.
- XML musi zawierać tylko obsługiwane wpisy `<dimen>`; duplikaty, nieznane
  klucze i nieznane elementy są odrzucane.
- Wszystkie trzy pliki muszą zawierać ten sam zestaw zarządzanych kluczy.
  Każdy klucz musi mieć odpowiadający mu string resource we wszystkich trzech
  katalogach locale.
- Wartość musi być skończona, dodatnia i zapisana wyłącznie w `sp`.
  Proponowany zakres walidacji to `8–32sp`; należy go potwierdzić na podstawie
  audytu istniejących kontrolek przed implementacją.
- Nie używać automatycznego skracania tłumaczeń jako substytutu właściwego
  rozmiaru. Zachować co najmniej 48 dp celu dotykowego oraz pełne opisy
  dostępności.
- Zmiana języka nadal korzysta z istniejącego `LocalizedContext` i
  `recreate()`; nie dodawać drugiego źródła języka ani nie zmieniać
  `LanguageSettings`.

## Testy i kryteria akceptacji

### Android

- Każdy zarządzany przycisk wskazuje właściwy `R.dimen`; rozmiar pobierany jest
  z aktywnego locale.
- Testy sprawdzają kompletność kluczy, poprawne jednostki i odczyt rozmiarów
  EN/PL/ES przy istniejącym `LocalizedContext`.
- Testy lub screenshot review potwierdzają, że etykiety mieszczą się w
  niezmienionych wymiarach przycisków, nie są ucinane i pozostają czytelne.
- Zmiana rozmiarów nie zmienia tekstów, nawigacji, komend ani symulacji.

### Studio

- Model formularza zachowuje wartości dziesiętne w `sp`, zmienia wyłącznie
  wskazaną komórkę i resetuje ją do baseline.
- Validator odrzuca brakujące i zduplikowane klucze, błędne locale, brak
  odpowiadającego string resource, nieobsługiwane tagi/jednostki, wartości
  poza zakresem oraz nieznane pola.
- Zmiana pliku poza Studio powoduje jawny konflikt `409`; zapis trzech locale
  nie może zakończyć się częściowo.
- Podgląd prezentuje oryginalny tekst z zasobów, bez możliwości edycji
  tłumaczeń.

### Odbiór wizualny

Przed implementacją zinwentaryzować konkretne przepełnione przyciski i
zarejestrować ich aktualny układ. Po edycji zweryfikować EN/PL/ES na tych
samych ekranach i rozdzielczościach oraz przy standardowej skali systemowej.
Wartości początkowe powinny odpowiadać obecnemu rozmiarowi bazowemu, a
zmniejszać należy wyłącznie etykiety, które tego wymagają.

## Kolejność implementacji

1. Audyt przycisków, ich string resource IDs, stałych szerokości i językowych
   zrzutów; ustalić pierwszą listę zarządzanych etykiet oraz zatwierdzić
   zakres `sp`.
2. Dodać locale-specific `button_typography.xml` z kompletnym zestawem
   wartości początkowych.
3. Przepiąć tylko wskazane button labels z hard-coded `textSize` na jawne
   `R.dimen`; dodać testy locale i kontraktu.
4. Dodać typowany parser/walidator i atomowe API Studio z `expectedHash`.
5. Dodać zakładkę Typography z podglądem EN/PL/ES i niezależną edycją,
   przetestować zapis/konflikt i wykonać odbiór wizualny.
6. Zaktualizować dokumentację LocalizationSystem, Studio, bieżącego stanu i
   wiedzy projektu.

## Poza zakresem

- Edycja tekstów tłumaczeń, nowych locale lub tytułów rozdziałów.
- Automatyczne generowanie lokalizacji, hot reload albo zapis do APK w czasie
  działania.
- Globalna zmiana czcionki, koloru, odstępów, paddingu lub wymiaru przycisków.
- Edytor typografii dla wszystkich `TextView`, HUD-u i treści tutoriali.
- Zmiany w balansie, domenie, scenariuszach, replayu lub symulacji.
