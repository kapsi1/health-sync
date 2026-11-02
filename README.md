# Health Sync

Prywatna aplikacja Android (Kotlin/Compose, interfejs po polsku), bez własnego serwera:

- Health Connect → lokalne agregaty dzienne w Room → Google Sheets (`daily`), ręcznie i przez WorkManager.
- Lokalny Google Takeout ZIP → podgląd i zatwierdzony import brakujących rekordów do Connect → zwykła synchronizacja do arkusza. Dostępne są wznowienie, anulowanie dalszego importu i wycofanie własnych, niezmienionych rekordów; zakres prób na telefonie jest ograniczony.
- 14 wybieranych statystyk; definicje i ograniczenia w [kontraktach danych](docs/data-contracts.md). Brak danych nie oznacza zera ani automatycznego usunięcia poprzedniej wartości.

Google Health API, desktopowy import wzbogacenia, żywienie i dashboard są w [planie](docs/plan/README.md), bez implementacji w tym repozytorium.

## Uruchomienie

1. Otwórz projekt w Android Studio; przygotuj JDK 17–23 (lokalnie używano 21), Android SDK Platform 36 i zaakceptuj licencje. Skonfiguruj `sdk.dir` w nieśledzonym `local.properties` lub `ANDROID_HOME`.
2. Zbuduj: `./gradlew.bat assembleDebug` (PowerShell) lub `./gradlew assembleDebug` (Unix). Konfiguracja aplikacji: minSdk 26, targetSdk 35; dostępność Health Connect i funkcji sprawdzana jest osobno.
3. Zainstaluj APK z `app/build/outputs/apk/debug/app-debug.apk`, wybierz statystyki i przyznaj zgody. Dostęp do Sheets wymaga [konfiguracji Google Cloud](docs/google-cloud.md).
4. Automatyzacja jest domyślnie wyłączona; dostępne interwały: 1, 3, 12 i 24 godziny. Android może opóźniać uruchomienia.

## Dokumentacja

[Indeks i dobór plików do zadania](docs/README.md) · [Architektura i mapa kodu](docs/architecture.md) · [Development i testy](docs/development.md) · [Plan](docs/plan/README.md) · [Wyniki weryfikacji](docs/test-results.md)

Agenci: zacznij od [AGENTS.md](AGENTS.md). Prywatnych eksportów, danych zdrowotnych, tokenów i kluczy nie dodawaj do repozytorium.
