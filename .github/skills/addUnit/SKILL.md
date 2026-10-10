---
name: addUnit
description: Add a new dedicated Urkaaaz unit with centralized balance configuration, gameplay interactions, AI and campaign wiring, UI assets, tests, and copy-ready asset AGPs.
argument-hint: "[optional unit name]"
---

# /addUnit — dodawanie nowej jednostki

Używaj tego skilla, gdy użytkownik wywoła `/addUnit` albo poprosi o dodanie
nowej jednostki do Urkaaaz! Catapult Chaos.

## Kontrakt rozmowy

Zadawaj pytania pojedynczo. Nigdy nie łącz kilku pytań w jednej wiadomości.
Jeżeli użytkownik podał daną informację wcześniej, zachowaj ją jako źródło
prawdy i nie pytaj o nią ponownie.

Kolejność pytań:

1. Zapytaj o nazwę jednostki i krótki opis fantasy/komediowego motywu.
2. Zapytaj o rolę jednostki: `FRONTLINE`, `RAIDER`, `RANGED`, `SIEGE`,
   `SUPPORT` albo `SPECIAL`.
3. Zapytaj o podstawowe parametry balansu: koszt Supply, HP, prędkość,
   obrażenia, cooldown, zasięg i obrażenia fortecy. Jeśli użytkownik nie zna
   wartości, zaproponuj wartości względem najbardziej podobnej istniejącej
   jednostki, ale nie zatwierdzaj ich bez akceptacji.
4. Zapytaj o pełne zachowanie w walce: cele, priorytety, kontakt, zasięg,
   efekt specjalny, reakcje na każdą istniejącą jednostkę i zachowanie wobec
   fortecy.
5. Zapytaj, czy jednostka wraca do garrisonu, ginie po ataku, może być
   wielokrotnie użyta oraz czy ma ograniczenie liczby sztuk.
6. Zapytaj o poziom/chapter odblokowania, dostępność w kampanii i zasady AI.
7. Zapytaj o wymagane assety. Domyślny zestaw obejmuje osobne pliki:
   ikonę, sprite idle, sprite walk, sprite attack, sprite death oraz
   warianty lewej i prawej strony, jeśli renderer tego wymaga.
8. Zapytaj o referencje wizualne: istniejące jednostki, paletę, sylwetkę,
   skalę, kierunek patrzenia i humorystyczny detal.

Nie wymyślaj gameplayowego efektu wyłącznie na podstawie wyglądu jednostki.
Nie zakładaj domyślnie animacji, liczby klatek, rozmiaru canvasa ani rodzaju
assetu. Brakujące parametry dopytaj osobno.

## Architektura implementacji

Przed zmianami przeczytaj:

- `AGENTS.md`;
- `architecture.md`;
- `docs/PROJECT_KNOWLEDGE.md`;
- `game-domain/src/main/kotlin/com/urkaaaz/domain/UnitConfigurations.kt`;
- `game-domain/src/main/kotlin/com/urkaaaz/domain/UnitDefinitions.kt`;
- `game-domain/src/main/kotlin/com/urkaaaz/domain/Combatants.kt`;
- `game-domain/src/test/kotlin/com/urkaaaz/domain/DomainModelsTest.kt`;
- `game-simulation/src/main/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulation.kt`;
- `game-simulation/src/test/kotlin/com/urkaaaz/simulation/DeterministicMatchSimulationTest.kt`;
- `game-application/src/main/kotlin/com/urkaaaz/application/MatchSession.kt`;
- `game-ai/src/main/kotlin/com/urkaaaz/ai/AiAgent.kt`;
- `game-campaign/src/main/kotlin/com/urkaaaz/campaign/CampaignLevelDefinition.kt`;
- `android-app/src/main/java/com/urkaaaz/ui/UnitPanelView.kt`;
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldAssetCatalog.kt`;
- `android-app/src/main/java/com/urkaaaz/ui/BattlefieldRenderer.kt`.

Nowa jednostka musi zachować obecny wzorzec:

1. Dodaj wszystkie wartości balansu wyłącznie do
   `game-domain/.../UnitConfigurations.kt`.
2. Dodaj dedykowaną klasę w `UnitDefinitions.kt`, np. `BomberUnit`.
   Klasa pobiera konfigurację przez konstruktor i zawiera tylko zachowanie
   specyficzne dla jednostki. Nie kopiuj parametrów balansu do tej klasy.
3. Dodaj mapowanie do `UnitFactory`. Factory musi tworzyć jednostkę zarówno
   z `UnitConfiguration`, jak i z identyfikatora komendy.
4. Nie dodawaj `UnitType` enum ani równoległego katalogu parametrów.
5. Wspólne reguły pozostaw w istniejących mechanizmach symulacji. Nową logikę
   dodaj polimorficznie lub przez metodę klasy jednostki, a nie przez kolejny
   centralny `when` po identyfikatorze.
6. Zaktualizuj `UnitSnapshot`, mapowanie renderera, factory, AI, kampanię,
   komendy i dokumentację tylko na odpowiednich granicach modułów.
7. Dodaj testy konfiguracji, factory, klasy jednostki, interakcji z każdą
   istniejącą klasą jednostki, ataku fortecy, kosztu i odblokowania.

## Interakcje jednostek

Przed implementacją przygotuj macierz:

| Nowa jednostka jako atakujący | Defender | Sapper | Demolisher | Raging Boar | Slingmaster | Fortress |
|---|---|---|---|---|---|---|
| Zachowanie | jawna reguła | jawna reguła | jawna reguła | jawna reguła | jawna reguła | jawna reguła |

Nie uznawaj jednostki za ukończoną, dopóki każda komórka nie ma jawnego
zachowania: obrażenia, brak obrażeń, detonacja, spowolnienie, priorytet,
odrzucenie celu albo inna zaakceptowana reguła.

## Workflow assetów i akceptacji

Nie generuj całego zestawu assetów jednym AGP. Pracuj etapami i zatrzymaj się
po każdym etapie, prosząc użytkownika o `zatwierdzam`, `popraw` albo wskazanie
plików do poprawy.

Kolejność etapów:

1. `unit icon` — jeden osobny transparentny PNG;
2. `first frame` — zaakceptowana klatka referencyjna wyglądu jednostki;
3. `idle animation`;
4. `walk animation`;
5. `attack animation`;
6. `death animation`;
7. opcjonalne osobne efekty, np. pocisk, bomba, aura lub eksplozja.

Jeśli użytkownik dostarczy asset, najpierw go zweryfikuj i zarchiwizuj w
`assets_src/user_reference/`. Nie podmieniaj assetu w grze przed akceptacją.
Po akceptacji skopiuj go do właściwego `res/drawable-nodpi/`, podłącz renderer,
uruchom build i pokaż wynik do kontroli.

Każdy transparentny asset musi:

- być osobnym PNG w prawdziwym trybie RGBA;
- mieć niezależny kanał alpha i alpha `0` poza obiektem;
- mieć minimum 50 px, preferowane 96 px, pełnej przezroczystości od krawędzi;
- nie mieć clippingu, halo, tła, checkboardu, tekstu ani watermarku;
- zachować wspólną skalę i dolną kotwicę z istniejącymi jednostkami;
- nie zawierać kolażu ani niezatwierdzonych dodatkowych obiektów.

Sprite sheet jest zabroniony, chyba że istniejący renderer jawnie wymaga
arkusza. W takim przypadku AGP musi określić dokładną siatkę, liczbę klatek,
odstępy, kolejność i walidację każdej komórki.

## Copy-ready AGP

Po zebraniu wymagań przygotuj AGP wyłącznie dla aktualnego etapu. Każdy AGP
ma zawierać:

- `THEMATICS`;
- `VISUAL STYLE`;
- `HUMOROUS ACCENTS`;
- `REFERENCE IMAGE INPUT`;
- `OUTPUT FILES`;
- `MUST-HAVE`;
- `MUST-HAVE VALIDATION BEFORE DELIVERY`;
- pełny `NEGATIVE PROMPT`;
- docelową ścieżkę Androida;
- jednoznaczną regułę: walidacja musi zakończyć się przed pokazaniem preview.

Używaj tego szablonu i uzupełniaj go zaakceptowanymi danymi:

```text
Generate exactly the current asset stage for the Urkaaaz unit "<UNIT_NAME>".
Do not generate any later stage, contact sheet, sprite sheet, collage, text,
label, logo, watermark, or unrequested prop.

THEMATICS
- Role: <ROLE>.
- Gameplay fantasy: <GAMEPLAY_FANTASY>.
- The unit must be readable at battlefield scale and clearly distinct from
  existing units.

VISUAL STYLE
- Match the accepted Urkaaaz unit scale, perspective, lighting direction,
  silhouette readability, and grounded lower anchor.
- Preserve the accepted reference proportions and facing direction.
- Use the approved palette: <PALETTE>.

HUMOROUS ACCENTS
- Use only this approved visual joke: <APPROVED_HUMOR>.
- Do not add any other joke, text, symbol, prop, or character.

REFERENCE IMAGE INPUT
- Use the attached accepted Urkaaaz unit reference as the visual reference.
- Preserve its style, scale, lower anchor, lighting, and material language.

OUTPUT FILES
- Export only: <EXACT_FILENAMES>.
- Each file must be an independent PNG.
- Required canvas: <WIDTH>x<HEIGHT> px.
- Required frame count/order: <FRAME_CONTRACT>.
- Android destination: <ANDROID_DESTINATION>.

MUST-HAVE
- True RGBA PNG with a real independent alpha channel.
- Alpha 0 outside the unit; no background, halo, checkerboard, or matte.
- Minimum 50 px transparent margin on every side; prefer 96 px.
- No clipping and no object touching a canvas edge.
- Stable lower gameplay anchor and approved facing direction.
- No text, labels, logo, watermark, collage, atlas, or extra elements.

MUST-HAVE VALIDATION BEFORE DELIVERY
- Verify exact file count and exact filenames.
- Verify PNG format, exact dimensions, RGBA mode, and alpha channel.
- Verify transparent margins, no clipping, no halo, and no background pixels.
- Verify frame order, consistent canvas, scale, anchor, and facing direction.
- Reject and regenerate any file that fails one test.
- Show the preview only after every validation passes.

NEGATIVE PROMPT
text, letters, labels, logo, watermark, signature, UI, border, frame,
checkerboard, white background, black background, colored matte, halo,
glowing outline outside the approved effect, clipping, cropped feet,
multiple units, second character, extra weapon, extra prop, collage,
contact sheet, sprite sheet, atlas, inconsistent scale, inconsistent anchor,
wrong facing direction, photorealism, unrelated style, unapproved effects
```

## Finalizacja

Po zaakceptowaniu assetów:

1. podłącz jednostkę do `UnitFactory`, konfiguracji kampanii, AI i matchu;
2. podłącz ikony oraz animacje do istniejącego katalogu assetów i renderera;
3. dodaj testy jednostkowe, symulacyjne, kontraktowe i integracyjne;
4. zaktualizuj `docs/PROJECT_KNOWLEDGE.md`, `GLOSSARY.md` i dokumentację
   projektu, jeśli pojawiły się nowe terminy;
5. uruchom:

   ```bash
   ./gradlew test assembleDebug --quiet
   git diff --check
   git status --short
   ```

W końcowej odpowiedzi podaj zaakceptowane parametry, listę zmienionych plików,
wynik walidacji, zaimportowane assety oraz pełny AGP aktualnego lub następnego
niezaakceptowanego etapu. Nie zgłaszaj ukończenia, jeśli brakuje assetów,
testów, macierzy interakcji albo podłączenia do factory.
