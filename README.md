# Health Sync

Wersja rozwojowa: diagnostyka Health Connect oraz zapamiętywany wybór metryk i ich uprawnień. Nie synchronizuje jeszcze danych ani nie łączy się z Google Sheets. Wyniki i ograniczenia: [docs/feasibility.md](docs/feasibility.md).

Otwórz projekt w Android Studio, zainstaluj SDK Platform 36 i zaakceptuj licencje. Uruchom konfigurację `app` na telefonie lub zbuduj APK poleceniem `./gradlew.bat assembleDebug` z JDK 17–23. Po instalacji wybierz metryki i przyznaj wymagane zgody; diagnostyka próbuje odczytać kroki i kalorie po kliknięciu „Sprawdź”. Dane z Health Connect pozostają na urządzeniu.
