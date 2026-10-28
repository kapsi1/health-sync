# Weryfikacja M0

Wersja diagnostyczna sprawdza dostępność Health Connect, funkcji historii i odczytu w tle oraz obecność próbek kroków i całkowitych kalorii z ostatnich 30 dni. Wyświetla źródła tylko z pierwszej strony maksymalnie 100 rekordów; nie jest to pełny import.

- 2026-10-28: `assembleDebug` zakończyło się poprawnie; debug APK zainstalowano i uruchomiono na Pixelu przez ADB. Użytkownik potwierdził działanie diagnostyki; dokładne wyniki dostępności i źródła danych nie zostały przekazane.
- Kolejna wersja z listą ośmiu metryk i zapisem wyboru w DataStore została zbudowana i zainstalowana. Uprawnienia dla pełnej listy wymagają sprawdzenia na urządzeniu.
- Odczyt danych historycznych i działanie w tle: **niezweryfikowane**; ekran sprawdza jedynie dostępność funkcji i zgody.
- Google Cloud OAuth i AuthorizationClient w workerze: **niezaimplementowane i niezweryfikowane**. Wymagają konfiguracji projektu OAuth i testu na urządzeniu.
- Synchronizacja Google Sheets: **niezaimplementowana**.
