# Health Sync

Wersja rozwojowa: diagnostyka Health Connect, zapamiętywany wybór metryk, podgląd oraz lokalny import historii z punktami wznowienia. Sen jest pokazany jako pełen czas sesji przypisany do dnia jej zakończenia w Europe/Warsaw, także z ewentualnymi przebudzeniami; dla nakładających się sesji kończących się tego samego dnia wynik nie jest sumowany. Przyznanie zgody Google potwierdzono na urządzeniu; tworzenie arkusza i import historii wymagają weryfikacji. Synchronizacja wartości z Google Sheets i import Takeout nie są jeszcze zaimplementowane. Wyniki i ograniczenia: [docs/feasibility.md](docs/feasibility.md).

Otwórz projekt w Android Studio, zainstaluj SDK Platform 36 i zaakceptuj licencje. Uruchom konfigurację `app` na telefonie lub zbuduj APK poleceniem `./gradlew.bat assembleDebug` z JDK 17–23. Po instalacji wybierz metryki i przyznaj wymagane zgody; diagnostyka próbuje odczytać kroki i kalorie po kliknięciu „Sprawdź”. Dane z Health Connect pozostają na urządzeniu.
