# Health Sync

Wersja rozwojowa: diagnostyka Health Connect, wybór metryk, podgląd i lokalny import historii z punktami wznowienia. Użytkownik potwierdził zapis dziennych wartości do Google Sheets. Sen to pełen czas sesji przypisany do dnia jej zakończenia w Europe/Warsaw, także z ewentualnymi przebudzeniami; nakładające się sesje kończące się tego samego dnia nie są sumowane. Synchronizacja w tle, wykrywanie usuniętych historycznych rekordów, oraz import Takeout nie są jeszcze zaimplementowane. Wyniki i ograniczenia: [docs/feasibility.md](docs/feasibility.md).

Otwórz projekt w Android Studio, zainstaluj SDK Platform 36 i zaakceptuj licencje. Uruchom konfigurację `app` na telefonie lub zbuduj APK poleceniem `./gradlew.bat assembleDebug` z JDK 17–23. Po instalacji wybierz metryki i przyznaj wymagane zgody; diagnostyka próbuje odczytać kroki i kalorie po kliknięciu „Sprawdź”. Dane z Health Connect pozostają na urządzeniu.
