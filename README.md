# Health Sync

Wersja rozwojowa: diagnostyka Health Connect, zapamiętywany wybór metryk i ich uprawnień oraz podgląd ostatnich siedmiu dni. Sen jest pokazany jako pełen czas sesji przypisany do dnia jej zakończenia w Europe/Warsaw, także z ewentualnymi przebudzeniami; dla nakładających się sesji kończących się tego samego dnia wynik nie jest sumowany. Trwały import i synchronizacja z Google Sheets nie są jeszcze zaimplementowane. Wyniki i ograniczenia: [docs/feasibility.md](docs/feasibility.md).

Otwórz projekt w Android Studio, zainstaluj SDK Platform 36 i zaakceptuj licencje. Uruchom konfigurację `app` na telefonie lub zbuduj APK poleceniem `./gradlew.bat assembleDebug` z JDK 17–23. Po instalacji wybierz metryki i przyznaj wymagane zgody; diagnostyka próbuje odczytać kroki i kalorie po kliknięciu „Sprawdź”. Dane z Health Connect pozostają na urządzeniu.
