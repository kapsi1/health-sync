# B. Desktopowy Takeout → wzbogacenie Sheets

**Brak implementacji; przed rozpoczęciem uzyskać osobne zlecenie/zgodę.** Ustalić CLI czy lokalny HTML/JS. [Zakres i kolejność](README.md) · [wspólne zależności](shared.md).

- [ ] Wybrać przydatne pola z Takeout według [katalogu semantyki i roli źródeł](data-sources.md). Historyczny wariant B obejmował wyłącznie wzbogacenie bez odpowiednika Connect; przyszła wspólna baza może używać Takeout także do historii standardowych pól po jawnej zmianie kontraktu. Obecny import mobilny A pozostaje bez zmian. Kalorie mają [osobny plan](calories.md); sleep score, oceny i Sleep Profile nadal są wyłączone. Niedostępne pola raportować, nie zgadywać.
- [ ] Wspólnie z C zdefiniować katalog: definicja CSV↔API, jednostka, nagłówek, właściciel i wersja agregacji. Łączyć sesje/podsumowania po źródłowych ID; dzień snu z końca sesji w Europe/Warsaw, nie `score_time`/daty pliku. Główna sesja i suma dobowa wymagają osobnych nazw, jeśli różnią się regułą.
- [ ] Lokalny odczyt wskazanych plików, konto źródłowe, zakres i wybór kolumn; podgląd zmian/konfliktów i osobny krok zatwierdzonego zapisu. OAuth dopasowany do platformy, bez sekretu klienta w HTML/APK.
- [ ] Dopasować istniejące `daily` po dacie i rozpoznanych nagłówkach. Dotychczasowy wariant wzbogacenia wypełnia puste komórki i chroni istniejące wartości/formuły. Odrębna, już dopuszczona przez użytkownika przebudowa obecnych tymczasowych danych jest opisana w [kaloriach](calories.md); nie przenosić tej zgody na dowolne przyszłe arkusze. Sortować całe wiersze, zachowując pozostałe kolumny/zakładki.
- [ ] Prywatny manifest: wersja importera/schematu, źródło, ID/wersje, daty i zarządzane komórki, bez tokenów; zabezpieczyć i wykluczyć z Git. Duży import porcjować, wznawiać i powtarzać idempotentnie.
- [ ] Uzgodnić przejęcie historii przez C: API aktualizuje tylko rozpoznane automatyczne komórki według wersji, bez nadpisania ręcznych/formuł/nieznanych. Telefon i komputer nie dzielą mutexu — na czas desktopowego zapisu wstrzymać mobilne wysyłanie i ponownie sprawdzić strukturę.
- [ ] Testy: schematy i strefy, starsze daty, powtórzenia, formuły, przerwanie po wysyłce, zgodność kilku nocy z API oraz przejęcie historii przez C.

Gotowe, gdy próbka ma potwierdzoną zgodność definicji z C, powtórzenie nie duplikuje danych, a zapis zachowuje dane użytkownika mimo konfliktów i przerwania.
