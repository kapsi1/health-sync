# Plan — indeks i zakres

Przegląd **2026-11-02**. Cel: prywatna aplikacja, spójne dane zdrowotne i użyteczne agregaty w Sheets oraz przyszłym dashboardzie. Obecny kod nie ma backendu; docelowe umiejscowienie wspólnej bazy/transferu jest do decyzji. Poprawność/wznowienie przed wydajnością. [Stan kodu](../architecture.md).

| Obszar | Pozostałe prace / status | Wykonane |
| --- | --- | --- |
| Rdzeń: UI, Connect, Sheets, tło, wydanie | Działa; [sync.md](sync.md) | [Core](../completed/core.md) |
| Kalorie całkowite i aktywne: historia 2026 i bieżące dni | Ustalony kontrakt do wdrożenia; [calories.md](calories.md) | — |
| Wybór źródeł, agregacji i wspólnej bazy | Analiza i rekomendacja do decyzji; [data-sources.md](data-sources.md) | — |
| A. Mobilny Takeout → Connect | Autoryzowane, w kodzie; [takeout.md](takeout.md) | [Takeout](../completed/takeout.md) |
| B. Desktopowy Takeout → dodatkowe kolumny | Niezaczęte; [desktop-import.md](desktop-import.md) | — |
| C. Google Health API → dane aplikacji | Niezaczęte; [google-health-api.md](google-health-api.md) | — |
| D. Arkusz odżywiania → Connect | Niezaczęty wariant opcjonalny; [nutrition.md](nutrition.md) | — |
| E. Dashboard, żywność i trasy | Niezaczęte; [dashboard.md](dashboard.md) | — |
| Wspólne zależności / dalsze możliwości | [shared.md](shared.md) | Istniejące: architektura |

## Zakres i zgody

- B–E oraz nowe wspólne mechanizmy wymagają osobnego zlecenia/zgody, C także przed diagnostycznym OAuth/odczytem. Plan nie jest zgodą. A było autoryzowane; bieżące zlecenie ma pierwszeństwo, bez ponownego pytania o tę samą pracę.
- Zlecenie z 2026-11-02 obejmuje analizę i plan kalorii, wspólnych uwag oraz ponowną ocenę źródła prawdy. Nie uruchamia teraz migracji, nowego OAuth ani kasowania danych Connect/API. Użytkownik dopuszcza zastąpienie/usunięcie obecnych tymczasowych danych arkusza; jest to wyjątek dla tej historii, nie ogólna zgoda na usuwanie pomiarów lub innych plików.
- **Obecna implementacja:** Connect→Room→Sheets, A uzupełnia Connect. **Projekt docelowy:** poprzednia reguła „Connect jedynym źródłem standardowych pól” podlega zmianie; rekomendacja własnej bazy i wybór per metryka są w [data-sources.md](data-sources.md), bez automatycznej zmiany działającego kodu.
- B/C: wcześniej ograniczone do wzbogacenia bez odpowiednika Connect; nowy plan dopuszcza projektowanie spójnej projekcji także dla istniejących kolumn. Wymagane jawne definicje, kwalifikacja, migracja i identyfikacja replik. Podobna nazwa nie dowodzi zgodności. **Sleep score, oceny składowe i Sleep Profile wyłączone** do nowej decyzji i potwierdzonej ścieżki późniejszego uzupełniania; bez własnych zamienników.
- Bez surowych serii w `daily`; szczegóły mogą służyć odtworzeniu Connect/obliczeniom. Pusta komórka jest lepsza niż nieporównywalna wartość; sam BMR nie trafia do kalorii całkowitych. Brak/błąd/odmowa nie oznaczają zera/usunięcia. „Uwagi” są wspólną kolumną dla wszystkich metryk — [kontrakt](shared.md#uwagi-do-dnia). Kierunek żywienia i zapis do zewnętrznych źródeł nadal wymagają jawnej decyzji, bez pętli.

Kolejność: kwalifikacja kalorii i wspólne uwagi → decyzja o źródłach/bazie oraz mała próba zgodności → korekta 2026 → kolejne zaakceptowane metryki/API/desktop. Poprzednie lata pozostają TODO w planie kalorii. Pozostała weryfikacja A zachowuje zakres; D niezależnie po ustaleniu roli E.

Checkboxy oznaczają tylko pozostałą pracę. Po ukończeniu przenieś zakres do `completed/`, pozostawiając osobno brakujące próby urządzenia. Dowody: [test-results](../test-results.md); źródła i odłożone decyzje: [shared.md](shared.md).
