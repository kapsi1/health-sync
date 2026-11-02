# Development i weryfikacja

## Środowisko

Jeden moduł `:app`. JDK 17–23 dla Gradle 8.11.1 (sprawdzono lokalnie 21); kod JVM 17, Kotlin 2.0.21, AGP 8.9.2. Android SDK Platform 36, minSdk 26/targetSdk 35. Wersje i zależności są w plikach Gradle, nie aktualizuj ich przy samej zmianie dokumentacji.

SDK: ustaw `sdk.dir` w ignorowanym `local.properties` lub `ANDROID_HOME`; JDK przez Android Studio/`JAVA_HOME`. Zgody/licencje i zależności muszą być przygotowane. Konfiguracja Google: [google-cloud.md](google-cloud.md). Nieśledzone `.tools/` zawierało lokalny JDK/Gradle/cache; nie zakładaj ich obecności na innym komputerze.

## Polecenia z katalogu repozytorium (PowerShell)

```powershell
# Test obszaru — przykład, dobierz klasy do zmiany
./gradlew.bat testDebugUnitTest --tests 'pl.healthsync.TakeoutPlanTest'
# Szerszy zestaw JVM i APK
./gradlew.bat testDebugUnitTest assembleDebug
# Tylko budowa testowego APK / pełne uruchomienie na urządzeniu
./gradlew.bat assembleDebugAndroidTest
./gradlew.bat connectedDebugAndroidTest
# Odcisk certyfikatu bieżącej konfiguracji
./gradlew.bat signingReport
git diff --check
```

Na Unix użyj `./gradlew`. `--offline --no-daemon` można dodać, gdy wszystkie zależności są już w cache; offline nie pobiera braków. Jeżeli sandbox blokuje połączenie Gradle z własnym daemonem na localhost, jest to problem środowiska, a nie wynik testu — uruchom w dozwolonym środowisku i zachowaj dokładny rezultat.

APK: `app/build/outputs/apk/debug/app-debug.apk`; raporty JVM: `app/build/reports/tests/testDebugUnitTest/`, XML: `app/build/test-results/testDebugUnitTest/`. Instalacja na wskazanym urządzeniu: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Nie czyść danych aplikacji w ramach zwykłej walidacji: usuwa to ustawienia, plany i wykluczenia.

## Dobór walidacji

| Zmiana | Weryfikacja |
| --- | --- |
| Sama dokumentacja | Lokalne odnośniki, istnienie ścieżek/symboli, statusy i zachowane decyzje, `git diff --check`; build opcjonalny |
| Reguły domenowe | Odpowiednie `*Test` w `app/src/test/java/pl/healthsync`; granice, braki, idempotencja, niejednoznaczny wynik |
| Wspólny przepływ/kontrakt | Zestaw JVM + `assembleDebug`; przy zmianie Room także instrumentacyjne migracje/transakcje |
| Android/Room/HTTP request | `app/src/androidTest/java/pl/healthsync`; urządzenie i skonfigurowane zależności. Test żądania lub fikcyjnej bazy nie dowodzi działania prawdziwej integracji |
| Zachowanie danych/UI na telefonie | Uzgodniony mały zakres, porównanie z Connect/Sheets, potem szerszy import. Nie uruchamiaj prywatnych zapisów/importów tylko w celu poprawienia dokumentacji |

Nazwy testów zwykle odpowiadają plikom domenowym; wyszukuj przez `rg --files app/src/test app/src/androidTest`. Wynik nowego uruchomienia i pozostałe ograniczenia zapisuj w [test-results.md](test-results.md); historyczne dowody są w `completed/`. Release podpisany i publikacja nie są jeszcze opisanym procesem wydania: [plan](plan/sync.md).
