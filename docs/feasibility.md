# Weryfikacja M0

Wersja diagnostyczna sprawdza dostępność Health Connect, funkcji historii i odczytu w tle oraz obecność próbek kroków i całkowitych kalorii z ostatnich 30 dni. Wyświetla źródła tylko z pierwszej strony maksymalnie 100 rekordów; nie jest to pełny import.

- 2026-10-28: `assembleDebug` zakończyło się poprawnie; debug APK zainstalowano i uruchomiono na Pixelu przez ADB. Odczyt danych i zgody na urządzeniu **niezweryfikowane** — wymagają interakcji użytkownika.
- Odczyt danych historycznych i działanie w tle: **niezweryfikowane**; ekran sprawdza jedynie dostępność funkcji i zgody.
- Google Cloud OAuth i AuthorizationClient w workerze: **niezaimplementowane i niezweryfikowane**. Wymagają konfiguracji projektu OAuth i testu na urządzeniu.
- Synchronizacja Google Sheets: **niezaimplementowana**.
