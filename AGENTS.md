# Health Sync — instrukcje dla agentów

Dotyczy całego repozytorium i dowolnego agenta. Dokumentacja po polsku; identyfikatory kodu bez tłumaczenia.

## Odczyt

- Pierwsza sesja: [README](README.md) i [architektura](docs/architecture.md). Potem wybieraj pliki przez [indeks](docs/README.md); nie wczytuj całego `docs/` ani archiwum.
- Przed zmianą zachowania przeczytaj właściwy kontrakt ([dane/Sheets](docs/data-contracts.md), [Takeout](docs/takeout.md)) i sekcję [planu](docs/plan/README.md). Najpierw szukaj symboli przez `rg`, potem czytaj potrzebne fragmenty.
- Kod/konfiguracja opisują implementację, plan zamiary, raport testu dowody. Rozbieżność sprawdź w kodzie i popraw opis; niespełnioną decyzję użytkownika zachowaj jako otwartą pracę.
- Bieżące zlecenie określa autoryzowany zakres. A (Takeout) było autoryzowane; B–E i nowe wspólne mechanizmy wymagają osobnego zlecenia/zgody, także diagnostyczne API — [zasady](docs/plan/README.md). Nie pytaj ponownie o już zleconą pracę.

## Implementacja i walidacja

- Najmniejsza kompletna zmiana, istniejące wzorce/narzędzia; bez niepowiązanych refaktoryzacji, zależności i zmian danych użytkownika.
- Zachowuj [kontrakty](docs/data-contracts.md): Connect→Room→Sheets; Takeout najpierw uzupełnia Connect. Brak/błąd/odmowa nie oznacza zera/usunięcia; zachowuj cudze kolumny i zakładki. Wznowienie odtwarza zapisany plan, nie zmienne decyzje źródłowe.
- Prywatne archiwa czytaj tylko ze wskazanej lokalizacji, bez samodzielnego przeszukiwania komputera. Pomiarów, tras, kont, tokenów i sekretów nie zapisuj do dokumentacji/fixture'ów/logów; używaj danych syntetycznych.
- Dobierz testy według [development](docs/development.md). Dla samych docs: linki, symbole, spójność i `git diff --check`; build opcjonalny. Rozdzielaj implementację, test automatyczny i potwierdzenie telefonu; stare wyniki nie potwierdzają nowej sesji. Dalsze ręczne próby wycofywania Takeout użytkownik odłożył; funkcję/testy automatyczne zachowaj.

## Zapis dokumentacji

- Aktualizuj docs w tym samym zadaniu, gdy zmienia się zachowanie, kontrakt, architektura, polecenia, ograniczenia lub decyzje; bez wpisów kronikarskich przy zmianach bez wpływu.
- Jedno miejsce dla faktu: mapa kodu → `architecture.md`; reguły/migracje → `data-contracts.md`/`takeout.md`; środowisko → `development.md`/`google-cloud.md`; przyszłość → `plan/`; wykonane → `completed/`; dowody/luki testów → `test-results.md`.
- Plan zawiera tylko pozostałą pracę. Wykonany zakres przenieś do `completed/` z odnośnikiem; brakujący test ręczny pozostaw jako osobne zadanie, bez oznaczania całej implementacji jako niewykonanej.
- Poprawiaj istniejącą regułę zamiast dopisywać kolejne „Aktualizacja:”. Zachowaj decyzje, wyjątki, ograniczenia i dowody; dawne definicje oznacz jako historyczne. Szczegóły oczywiste z kodu zastępuj ścieżką/symbolem.
- Względne linki i stabilne symbole; nowy obszar dodaj do indeksu docs, plan także do indeksu planu. Nie twórz kopii instrukcji dla innych agentów.
- Weryfikacja: data, komenda, wynik, ograniczenia; liczbę testów odczytaj z raportu, nie sumuj dawnych zestawów. Datę przeglądu zmieniaj tylko dla sprawdzonego opisu. Zmienne informacje zewnętrzne sprawdzaj u producenta przed implementacją i podaj zakres sprawdzenia.
