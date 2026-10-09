# M5 — Scenariusze kampanii

## Zakres

M5 przenosi statyczne definicje poziomów kampanii do modułu `game-campaign`.
Poziom opisuje biom, wymiary terenu, dostępne jednostki, amunicję, czary,
początkową amunicję, parametry przeciwnika, wiatr, reguły zwycięstwa i klucze
kroków tutoriala.

Każda definicja może utworzyć neutralny `MatchScenario` dla warstwy
aplikacyjnej i symulacji.

## Granice odpowiedzialności

Moduł nie zawiera zasobów Androida, widoków, nawigacji, persystencji, stanu
sklepu, żyć, progresji złota ani zapisu odblokowanych poziomów. Dane tutoriala
używają stabilnych kluczy zamiast identyfikatorów zasobów Androida.

## Zgodność ze starą wersją

Progi odblokowania i wartości strojenia zostały przeniesione ze starego
`CampaignLevelDefinition`, ale stara struktura pakietów i zależności Androida
nie zostały skopiowane.
