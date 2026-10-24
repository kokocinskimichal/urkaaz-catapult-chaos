---
name: loadContext
description: Load Urkaaaz's core Markdown context and the task-specific documentation listed in its curated index.
argument-hint: "[optional task area]"
---

# /loadContext — wczytanie kontekstu projektu

Używaj tego skilla, gdy użytkownik wywoła `/loadContext`. Jego celem jest
wczytanie najważniejszych dokumentów Markdown Urkaaaz na początku sesji i
dokumentów szczegółowych pasujących do bieżącego zadania.

## Workflow

1. Przeczytaj `AGENTS.md` oraz `docs/agent-context-index.md`.
2. Przeczytaj w całości wszystkie pliki wymienione w sekcji „Zestaw bazowy”
   indeksu, także gdy część informacji jest już widoczna w kontekście rozmowy.
3. Na podstawie bieżącej prośby użytkownika i opcjonalnego argumentu komendy
   dobierz dokumenty z sekcji „Dokumenty dobierane do zadania” i przeczytaj je
   w całości.
4. Jeśli użytkownik wywołał komendę bez określonego zadania, wczytaj tylko
   zestaw bazowy. Nie pytaj o temat; użytkownik może rozpocząć zadanie później.
5. Jeśli odczyt narzędzia zwrócił uciętą treść, kontynuuj odczyt kolejnych
   zakresów do końca pliku. Nie uznawaj częściowego odczytu za zakończony.
6. Jeśli potrzebny dokument nie istnieje lub nie można go odczytać, wskaż
   dokładną ścieżkę i ograniczenie; nie zastępuj go domysłami.
7. Nie zmieniaj plików i nie rozpoczynaj implementacji — ten skill służy tylko
   do wczytania kontekstu. Po zakończeniu krótko potwierdź, które dokumenty
   bazowe i zadaniowe zostały wczytane.

Nie wczytuj całego katalogu `docs/` ani wszystkich dokumentów wymienionych w
`AGENTS.md`. Wybieraj dokumenty zgodnie z indeksem i zakresem aktualnego
zadania. Skill nie zastępuje instrukcji implementacyjnych ani skilli
specjalistycznych dla konkretnych zmian.
