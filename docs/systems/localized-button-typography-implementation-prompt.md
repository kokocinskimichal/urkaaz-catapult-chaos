# Prompt implementacyjny: typografia etykiet kontrolek per język

> Plan: [`localized-button-typography-plan.md`](localized-button-typography-plan.md).

## Status

**Wdrożono pełny katalog 106 etykiet interaktywnych kontrolek** EN/PL/ES,
z grupami Common i per ekran w Studio Typography.

## Wymagania

Utrzymuj rozmiar `sp` niezależnie dla każdego widocznego tekstu w
interaktywnym elemencie Android UI: przyciskach/CTA, klikalnych kartach i
zakładkach, kaflach wyboru jednostek/amunicji/czarów, checkboxach, dialogach,
overlayach, akcjach wyniku meczu, tutoriala i ekranu testowego. Studio grupuje
etykiety w **Common**, **Dashboard**, **Campaign**, **Loadout**, **Shop**,
**Match**, **Tutorial** i **Test mode**.

Nie obejmuj tekstów wyłącznie w `contentDescription`, nieinteraktywnych
nagłówków i opisów, samodzielnych komunikatów statusu ani czysto liczbowych
badge'y i kontrolek ikonowych bez widocznego tekstu. Nie zmieniaj tłumaczeń,
wymiarów kontrolek, celów dotykowych, nawigacji ani gameplayu.

### Kontrakt

- Teksty EN/PL/ES pozostają wyłącznie w Android `strings.xml`.
- Każde miejsce użycia ma stabilny `controlId`, kategorię i `stringKey`.
  Ponowne użycie jednego stringa w innych kontrolkach może mieć inny
  `controlId` i niezależny rozmiar.
- `studio/server/src/contracts/typography.ts` jest wspólnym katalogiem
  Studio; `android-app/.../ButtonTypographyControl.kt` zawiera typowane,
  jawne referencje `R.string` i `R.dimen`. Utrzymuj oba katalogi zgodne.
- Zasoby `button_typography.xml` w `values/`, `values-pl/` i `values-es/`
  używają `button_text_size_<controlId>` i mają identyczny zestaw wpisów.
- Nie używaj `Resources.getIdentifier`. Rozmiary waliduj w zakresie `8–32sp`.
- Zachowuj dotychczasowe autosize; dla autosize ustawiony rozmiar jest
  maksimum. Tłumaczenia pozostają pełne i nie są edytowane w Studio.

### Studio

Zakładka Typography pobiera tłumaczenia z istniejących zasobów, pokazuje
`controlId` i `stringKey`, a wpisy prezentuje w zwijanych grupach Common i
ekranowych. Podgląd jest tylko do odczytu; argumenty formatowanych napisów
mają przykładowe wartości. Rozmiary locale można zmieniać niezależnie,
walidować i resetować do baseline.

Server obsługuje `GET /api/typography`,
`POST /api/typography/validate` i `PUT /api/typography`. Waliduj katalog,
string resources dla każdego locale, bezpieczną strukturę XML, zakres `sp`
i expected hash. Zapisuj trzy locale atomowo z rollbackiem; nie pozwalaj
edytować innych plików.

### Walidacja zmian katalogu

Każda nowa kontrolka wymaga równoczesnej zmiany:

1. wspólnego katalogu Studio;
2. Android `ButtonTypographyControl`;
3. plików wymiarów EN/PL/ES;
4. callsite Androida i testu kompletności.

Test Androida sprawdza `R.string`, `R.dimen`, tłumaczenia i lokalne rozmiary
dla każdego wpisu. Testy Studio obejmują grupowanie, podgląd formatów,
walidację, konflikt hasha i zapis wszystkich locale. Przed odbiorem wizualnym
sprawdź podgląd Studio oraz kontrolki o ograniczonej szerokości w Androidzie.

Pełna bramka projektu:

```bash
./gradlew test assembleDebug --quiet
cd studio && npm test && npm run lint && npm run build
git diff --check
git status --short
```

Nie commituj ani nie pushuj bez wyraźnej prośby.
