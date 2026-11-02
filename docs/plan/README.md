# Plan — indeks i zakres

Przegląd **2026-11-02**. Cel: prywatna aplikacja bez backendu, standardowe dane Connect i użyteczne agregaty w Sheets. Poprawność/wznowienie przed wydajnością. [Stan kodu](../architecture.md).

| Obszar | Pozostałe prace / status | Wykonane |
| --- | --- | --- |
| Rdzeń: UI, Connect, Sheets, tło, wydanie | Działa; [sync.md](sync.md) | [Core](../completed/core.md) |
| A. Mobilny Takeout → Connect | Autoryzowane, w kodzie; [takeout.md](takeout.md) | [Takeout](../completed/takeout.md) |
| B. Desktopowy Takeout → dodatkowe kolumny | Niezaczęte; [desktop-import.md](desktop-import.md) | — |
| C. Google Health API → dodatkowe kolumny | Niezaczęte; [google-health-api.md](google-health-api.md) | — |
| D. Arkusz odżywiania → Connect | Niezaczęty wariant opcjonalny; [nutrition.md](nutrition.md) | — |
| E. Dashboard, żywność i trasy | Niezaczęte; [dashboard.md](dashboard.md) | — |
| Wspólne zależności / dalsze możliwości | [shared.md](shared.md) | Istniejące: architektura |

## Zakres i zgody

- B–E oraz nowe wspólne mechanizmy wymagają osobnego zlecenia/zgody, C także przed diagnostycznym OAuth/odczytem. Plan nie jest zgodą. A było autoryzowane; bieżące zlecenie ma pierwszeństwo, bez ponownego pytania o tę samą pracę.
- Connect jest podstawowym źródłem standardowych rekordów; A uzupełnia Connect, potem zwykły eksport do Sheets. Brak dnia nie uprawnia B/C do zastąpienia standardowego pola chmurą.
- B/C: tylko zatwierdzone wzbogacenie, równoważna definicja w Takeout i wspieranym API oraz brak odpowiednika Connect. Podobna nazwa nie dowodzi zgodności. **Sleep score, oceny składowe i Sleep Profile wyłączone** do nowej decyzji i potwierdzonej ścieżki późniejszego uzupełniania; bez własnych zamienników.
- Bez surowych serii w `daily`; szczegóły mogą służyć odtworzeniu Connect/obliczeniom. Zachowujemy dane użytkownika; brak nie oznacza zera/usunięcia. Źródło prawdy i kierunek żywienia wymagają osobnej decyzji, bez pętli.

Kolejność po zgodach: weryfikacja A → katalog semantyki/własności kolumn → diagnostyka i C → B; D niezależnie, po ustaleniu roli E.

Checkboxy oznaczają tylko pozostałą pracę. Po ukończeniu przenieś zakres do `completed/`, pozostawiając osobno brakujące próby urządzenia. Dowody: [test-results](../test-results.md); źródła i odłożone decyzje: [shared.md](shared.md).
