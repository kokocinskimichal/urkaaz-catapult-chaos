# Dokumentacja projektu

Wszystkie nowe dokumenty opisujące strukturę, decyzje i proces migracji należy
zapisywać w tym katalogu jako pliki Markdown.

## Dokumenty

| Plik | Zakres | Status |
|---|---|---|
| [`project-structure.md`](project-structure.md) | struktura katalogów i modułów po M1 | gotowy |
| [`m2-contracts.md`](m2-contracts.md) | zakres kontraktów i modeli bazowych M2 | zaakceptowany plan |
| [`m2-report.md`](m2-report.md) | raport implementacji i walidacji M2 | gotowy do odbioru |
| [`m3-domain.md`](m3-domain.md) | zakres przeniesienia domeny gry | zaakceptowany plan |
| [`m3-report.md`](m3-report.md) | raport implementacji i walidacji M3 | gotowy do odbioru |
| [`m4-simulation.md`](m4-simulation.md) | zakres deterministycznej symulacji | gotowy do odbioru |
| [`m4-report.md`](m4-report.md) | raport implementacji i walidacji M4 | zaakceptowany |
| [`m5-campaign.md`](m5-campaign.md) | zakres migracji scenariuszy kampanii | gotowy do odbioru |
| [`m5-report.md`](m5-report.md) | raport implementacji i walidacji M5 | zaakceptowany |
| [`m6-ai.md`](m6-ai.md) | zakres migracji AI | gotowy do odbioru |
| [`m6-report.md`](m6-report.md) | raport implementacji i walidacji M6 | zaakceptowany |
| [`m7-assets.md`](m7-assets.md) | zakres inwentaryzacji assetów | gotowy do odbioru |
| [`m7-report.md`](m7-report.md) | raport implementacji i walidacji M7 | zaakceptowany |
| [`m8-assets.md`](m8-assets.md) | zakres kopiowania assetów | gotowy do odbioru |
| [`m8-report.md`](m8-report.md) | raport implementacji i walidacji M8 | zaakceptowany |
| [`m9-ui.md`](m9-ui.md) | zakres migracji Android UI | gotowy do odbioru |
| [`m9-report.md`](m9-report.md) | raport implementacji i walidacji M9 | zaakceptowany |
| [`m10-local-integration.md`](m10-local-integration.md) | granica lokalnej integracji | gotowy do odbioru |
| [`m10-report.md`](m10-report.md) | raport implementacji i walidacji M10 | zaakceptowany |
| [`m11-comparison.md`](m11-comparison.md) | macierz porównania regresyjnego | gotowy do odbioru |
| [`m11-report.md`](m11-report.md) | raport implementacji i walidacji M11 | gotowy do odbioru |

## Zasady

- jeden dokument powinien opisywać jeden spójny temat;
- dokumenty powinny wskazywać właściciela odpowiedzialności i kierunek
  zależności;
- decyzje architektoniczne powinny być aktualizowane razem ze zmianą kodu;
- dokumenty etapów migracji powinny zawierać sposób samodzielnej weryfikacji;
- dokumentacja nie powinna kopiować implementacji, jeśli wystarczy opisać
  kontrakt, odpowiedzialność i przepływ danych.
