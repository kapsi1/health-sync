# Weryfikacja M0

Wersja diagnostyczna sprawdza dostępność Health Connect, funkcji historii i odczytu w tle oraz obecność próbek kroków i całkowitych kalorii z ostatnich 30 dni. Wyświetla źródła tylko z pierwszej strony maksymalnie 100 rekordów; nie jest to pełny import.

- 2026-10-28: `assembleDebug` zakończyło się poprawnie; debug APK zainstalowano i uruchomiono na Pixelu przez ADB. Użytkownik potwierdził działanie diagnostyki; dokładne wyniki dostępności i źródła danych nie zostały przekazane.
- Kolejna wersja z listą ośmiu metryk i zapisem wyboru w DataStore została zbudowana i zainstalowana. Uprawnienia dla pełnej listy wymagają sprawdzenia na urządzeniu.
- Użytkownik potwierdził działanie wyboru i uprawnień. Dodano podgląd agregatów z ostatnich 7 dni dla siedmiu metryk; zbudowano i zainstalowano, ale nie porównano jeszcze wyników z Health Connect. Podgląd snu, trwały import i testy strefy czasu pozostają do implementacji.
- Zgłoszono brak masy w podglądzie mimo danych w Google Health. Przyczyna pozostaje nieustalona. Dodano diagnostykę obecności rekordów masy w Health Connect oraz odczyt tkanki tłuszczowej (ostatni pomiar dnia w strefie Europe/Warsaw). Wynik na urządzeniu wymaga potwierdzenia. Test jednostkowy sprawdza wybór pomiaru i zmianę czasu oraz pusty zbiór.
- Po ponownym połączeniu Google Health z Health Connect użytkownik potwierdził odczyt masy, tkanki tłuszczowej i tętna. Starsza historia ma luki w Health Connect; automatyczne usuwanie jest wyłączone. Użytkownik potwierdził brakujące dane w eksporcie Google Takeout. Lista plików eksportu zawiera m.in. pliki CSV dystansu, tętna spoczynkowego i snu; import Takeout pozostaje do opracowania. Nie należy włączać eksportu do repozytorium.
- Podgląd snu pokazuje pełny czas sesji przypisany do dnia zakończenia w strefie Europe/Warsaw, w tym przebudzenia; nakładające się sesje kończące się tego samego dnia są oznaczane jako konflikt. Podgląd przeszukuje ograniczony zakres i nie jest importem historii. Kompilacja i testy jednostkowe dla przejścia przez północ, zmiany czasu oraz konfliktów przeszły; odczyt na urządzeniu wymaga weryfikacji.
- Odczyt danych historycznych i działanie w tle: **niezweryfikowane**; ekran sprawdza jedynie dostępność funkcji i zgody.
- Google Cloud OAuth i AuthorizationClient w workerze: **niezaimplementowane i niezweryfikowane**. Wymagają konfiguracji projektu OAuth i testu na urządzeniu.
- Synchronizacja Google Sheets: **niezaimplementowana**.
