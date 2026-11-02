# C. Google Health API → wzbogacenie Sheets

**Brak implementacji; osobna zgoda jest potrzebna także przed diagnostycznym OAuth/odczytem.** API nie zastępuje standardowych pól Connect i domyślnie nie zapisuje do Connect. [Zakres](README.md) · [źródła do weryfikacji](shared.md).

## Katalog do wyboru, nie automatycznego włączenia

- [ ] Dla każdego kandydata potwierdzić definicję, jednostkę, właściciela kolumny, zakres czasu, główną sesję/drzemki, dostępność w stabilnym API i Takeout; beta oznaczać osobno. Tylko zatwierdzone pola mogą zasilać B. Sleep score, oceny składowe i profil pozostają wyłączone, bez własnych zamienników.
- [ ] Sen: zasypianie, czas po końcowym przebudzeniu, krótkie przebudzenia (czas/liczba), główna sesja/drzemka, czas/udział faz i czuwania, efektywność. Pochodne faz/czuwania najpierw liczyć z Connect, jeśli reprezentuje potrzebne dane. Oryginalne podsumowania producenta mogą mieć osobne nazwy po wyborze użytkownika i wyjaśnieniu różnicy.
- [ ] Nocne podsumowania: HRV RMSSD i dostępne wskaźniki snu głębokiego/non-REM, średnia/granice SpO₂, oddechy, zmiana temperatury skóry i baza. Nie utożsamiać ich z ostatnim pomiarem ani temperaturą ciała. Niedostępnych składowych nie obiecywać; sprawdzić wersję endpointu.
- [ ] Poza snem: strefy tętna Google, czas/energia w strefach, Active Zone Minutes — różne od czasu ćwiczeń i niekoniecznie równe intensywności Connect. Bez GPS, surowego ECG i pełnych serii w tej sekcji.

## Integracja i UI

- [ ] Po zgodzie: minimalne dodatkowe zakresy OAuth, konto i powiązanie Health; zabezpieczone dane autoryzacji, odnowienie, cofnięcie i wygaśnięcie w Testing. Najpierw diagnostyczny odczyt kilku znanych nocy bez zapisu i ocena zgodności z B.
- [ ] W Sync rozwijana sekcja „Dodatkowe dane Google Health”: osobny przycisk ręczny, historia, wybór pól, postęp, anulowanie/wznowienie, ostatni wynik i sukces. Oddzielny dziennik ręczny/auto; awaria API nie blokuje Connect. Nie mnożyć stałych przycisków ekranu głównego.
- [ ] Osobna automatyzacja, domyślnie wyłączona, natywny wybieracz preferowanej godziny (dla snu raz dziennie) i krótkie kontrolowane ponowienie przy przetwarzaniu nocy. WorkManager bez dokładnych alarmów; termin kalendarzowy z DST/restartem/zmianą ustawień, jawna strefa (domyślnie Europe/Warsaw), bez obietnicy punktualności.
- [ ] Historia na żądanie; potem przyrostowo i krótki zakres korekt (propozycja 3 ostatnie zakończone dni), bez codziennego pełnego importu. Paginacja, szczególnie snu; null, zero i processing jako różne stany. Zakresy/limity dopasować do rzeczywistych endpointów.
- [ ] Osobne checkpointy, kolejka i lokalny stan API na czas awarii Sheets. Wspólny serializowany zapis i własność kolumn; 429/5xx/timeout, wznowienie bez duplikacji, kontrola wersji i ręcznych zmian. Starszy eksport nie nadpisuje nowszej wersji API.
- [ ] Zmierzyć żądania, czas, transfer, CPU/API przed wyborem harmonogramu; nie zakładać dużego kosztu baterii wyłącznie dlatego, że API potrzebuje sieci.
- [ ] Testy: kilka nocy Health/Takeout, główna sesja/drzemki, null/zero/processing, paginacja, korekty, OAuth, harmonogram/DST/restart, niezależność Connect, współbieżność i ochrona danych użytkownika.

## Pomysł odłożony: SpO₂ API → Connect

- [ ] Oddzielnie ocenić uzupełnianie SpO₂ w Connect. Najpierw sprawdzić, dlaczego Google Health go nie zapisuje i jakie są ograniczenia techniczne/produktowe; brak wyjaśnienia nie dowodzi ich braku. Wymagane decyzja zakresowa, zgody, pochodzenie, wersje, konflikty i brak pętli. Użytkownik poprosił dotąd tylko o zapisanie pomysłu.
