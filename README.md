# Health Sync

Początkowy etap M0: aplikacja diagnostyczna Health Connect. Nie synchronizuje jeszcze danych ani nie łączy się z Google Sheets. Wyniki i ograniczenia: [docs/feasibility.md](docs/feasibility.md).

Otwórz projekt w Android Studio, zainstaluj SDK Platform 36 i zaakceptuj licencje. Uruchom konfigurację `app` na telefonie lub zbuduj APK poleceniem `./gradlew.bat assembleDebug` z JDK 17–23. Po instalacji uruchom aplikację, udziel zgody na kroki i kalorie i wybierz „Sprawdź”. Dane z Health Connect pozostają na urządzeniu.
