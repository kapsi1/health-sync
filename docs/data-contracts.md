# Kontrakty danych i arkusza

Stan kodu sprawdzony: **2026-11-02**. Punkty wejścia: `MetricRegistry`, `HealthHistorySource`, `SleepDaily`, `SleepRespiratory`, `SheetHeaders`, `SheetDisplay`, `SheetGatewayHttp` — ścieżki w [architekturze](architecture.md).

## Statystyki

14 aktywnych pozycji. `metricId` jest kluczem wyboru/Room; `column` jest stałym kluczem eksportu, a polska etykieta — nagłówkiem Sheets. Nie utożsamiaj tych trzech nazw. Dokładne etykiety, aliasy, jednostki i zgody utrzymuje `MetricRegistry`.

| metricId | column | Znaczenie |
| --- | --- | --- |
| `steps` | `steps` | Dzienna suma kroków Connect |
| `total_calories` | `total_kcal` | Całkowita energia spalona, kcal |
| `active_calories` | `active_kcal` | Aktywna energia, kcal |
| `distance` | `distance_km` | Dystans, km |
| `exercise_duration` | `exercise_hours` | Zagregowany czas ćwiczeń Connect, lokalnie godziny |
| `resting_heart_rate` | `resting_hr_bpm` | Średnie tętno spoczynkowe Connect, BPM |
| `hrv_rmssd` | `hrv_rmssd_avg_ms` | Nocne podsumowanie lub średnia próbek dopasowanych do snu; ms |
| `oxygen_saturation` | `oxygen_saturation_avg_percent` | Średnia arytmetyczna próbek dnia, % |
| `respiratory_rate_sleep` | `respiratory_sleep_avg` | Podsumowanie lub średnia oddechów/min w fazach snu |
| `skin_temperature_delta` | `skin_temperature_delta_c` | Zmiana temperatury skóry względem bazy, °C; nie temperatura ciała |
| `sleep_duration` | `sleep_hours` | Suma faz faktycznego snu, lokalnie godziny |
| `sleep_awake` | `sleep_awake_hours` | Czuwanie wewnątrz snu, lokalnie godziny |
| `weight_avg` | `weight_avg_kg` | Średnia wagi Connect, kg |
| `body_fat` | `body_fat_avg_percent` | Średnia arytmetyczna próbek dnia, % |

Kroki, kalorie, dystans, ćwiczenia, tętno spoczynkowe i waga używają agregacji SDK Connect. Pozostałe mają własną logikę na paginowanych rekordach. Własne średnie próbek nie są ważone czasem; rekordy różnych źródeł mają równą wagę, więc duplikaty mogą wpływać na wynik. Nie odtwarzaj szczegółowej synchronizacji tętna usuniętej na życzenie użytkownika. Piętra, przewyższenie, dobowe tętno średnie/min/max, VO₂ max, beztłuszczowa masa, wzrost i nawodnienie nie są aktywnymi statystykami.

## Dzień, braki i historia

- Uzgodniona strefa: `Europe/Warsaw`, przedziały półotwarte `[start, endExclusive)`. Sen przypisujemy do dnia zakończenia całej sesji. Import Takeout przyjmuje końcową datę włącznie i przelicza ją na wyłączny koniec przedziału.
- Własne odczyty rekordów używają granic warszawskich jako `Instant`; agregacja SDK w `HealthHistorySource` przekazuje granice `LocalDateTime`. Zachowanie na urządzeniu w innej strefie i dla wielu źródłowych offsetów pozostaje do weryfikacji — nie deklaruj potwierdzonej zgodności wszystkich ścieżek.
- Zero to wartość; pusty odczyt, brak zgody i błąd to różne stany. Standardowy import zapisuje tylko odczytane wyniki i nie kasuje starych obserwacji/komórek. Niepełny wynik dobowy nie zastępuje pełnego.
- Dodanie statystyki zleca backfill dotychczas wybranego zakresu; odznaczenie zachowuje dane. Nie ma osobnego trybu „cała dostępna historia”; datę początku wybiera użytkownik.
- Zmiany Connect są śledzone tylko dla wagi/tłuszczu. Pierwszy token obserwuje zmiany od chwili utworzenia. Po wygaśnięciu: odtwarzanie wcześniej importowanego zakresu, maks. 12 miesięcznych porcji na przebieg; token tworzony przed odczytem i aktywowany po jego zakończeniu. Wcześniejsze daty wymagają osobnego importu. Usunięcia są sygnalizowane; usuwanie i zmiany dat nie są rozliczane automatycznie.

## Sen

- `sleep_hours`: fazy SLEEPING/LIGHT/DEEP/REM; `sleep_awake_hours`: AWAKE/AWAKE_IN_BED/OUT_OF_BED tylko pomiędzy pierwszą i ostatnią fazą snu, bez zasypiania, końcowego czuwania i progu długości ponownego snu.
- Pełny czas snu wymaga kompletnej osi faz całej sesji. Wybudzenia mogą być znane przy nieznanych brzegach, jeśli kompletna jest część wewnętrzna. Ogólny stan SLEEPING (`ASLEEP_UNSPECIFIED` w modelu domenowym) pozwala policzyć sen, lecz nie dowodzi braku wybudzeń — ich wynik pozostaje nieznany.
- Nie zgadujemy brakujących/UNKNOWN faz, luk, nieprawidłowych przedziałów i nakładających się faz. Brak wyniku jednej sesji wyklucza pełną sumę dobową tej statystyki. Przerwa między oddzielnymi sesjami nie jest czuwaniem. Nakładające się sesje w obrębie ocenianej doby są konfliktem, nie podwójną sumą.
- Dawna wersja liczyła pełną sesję. Historię poprawia ponowny import; przy niekompletnych fazach stara wartość może pozostać i wymagać ręcznego wyjaśnienia.

## HRV, oddechy i temperatura skóry

- HRV/oddechy preferują pojedyncze podsumowanie Fitbit lub własne podsumowanie `takeout-v1-…` na dzień. Kilka podsumowań tego samego dnia jest niejednoznaczne. Rozpoznawanie podsumowania Fitbit jest heurystyczne, bo Connect nie udostępnia jawnej flagi.
- HRV: gęste rekordy Fitbit są klasyfikowane jako próbki (liczba w dniu, sąsiednie próbki w odstępie <6 h lub obecność w fazach snu). Do średniej wybierana jest najdłuższa sesja z ponad 3 h faz snu; remis nie wybiera sesji. Pozostałe próbki oddechów liczone są w jawnych fazach snu, z przypisaniem do dnia końca sesji. To przybliżenia, bez gwarancji zgodności z podsumowaniem Google Health.
- Temperatura skóry: pojedyncza delta w rekordzie Fitbit/własnym Takeout jest podsumowaniem przypisanym do dnia końca sesji; kilka podsumowań blokuje wynik. Gdy brak podsumowania, średnia pozostałych delt jest przypisana według czasu próbki. Wymagana dostępność funkcji temperatury skóry Connect.
- Nowe pola oddechów/temperatury skóry nie przejmują dawnych wartości dobowych oddechów ani temperatury ciała. `MetricSelection.migrate` przenosi wybór, a aliasy/archiwizacja kolumn zachowują stare dane bez zmiany ich znaczenia.

## Sheets: struktura i własność

- Jedna zarządzana zakładka `daily`, jeden wiersz na datę, pierwsza kolumna `Data` (dawniej `date`). Powtórzone daty, nieprawidłowe nagłówki, kolizje aliasów i nagłówki aplikacji wyliczane formułą blokują synchronizację.
- Polskie etykiety i dawne nazwy techniczne/etykiety są mapowane na stałe `column`; migracja nazw odbywa się w miejscu, bez zmiany kolejności. `Data` pozostaje pierwsza; inne kolumny można przesuwać. Dowolne przemianowanie zarządzanego nagłówka nie jest obsługiwane. Nazwy statystyk są zarezerwowane, a formuły wyszukujące stary tekst mogą wymagać aktualizacji.
- Kolumny wyłączonych i archiwalnych statystyk są ukrywane, nie usuwane; włączenie dodaje/odkrywa kolumnę. Inne kolumny i zakładki zachowujemy. Sortowanie rosnąco według daty przesuwa całe wiersze wraz z kolumnami użytkownika; działa również przy pustej kolejce.
- Bieżąca synchronizacja zapisuje wybrane zarządzane komórki bez porównywania ich treści. Nie ma ogólnej ochrony ręcznych zmian w tych komórkach. Licznik „zapisy do istniejących wierszy” nie oznacza liczby zmienionych wartości; przy niejednoznacznym błędzie obejmuje tylko zakończone porcje aktualnej próby.
- Własność zapisu potrzebna do bezpiecznego czyszczenia po wycofaniu nie jest jeszcze dowodzona; identyczna wartość nie dowodzi braku edycji. Niepewne komórki pozostają. Zadanie: [Takeout](plan/takeout.md).

## Format i migracje

- Room zachowuje pełną precyzję. Eksport odrzuca wartości niefinitywne, zaokrągla kcal do liczb całkowitych, pozostałe liczby do maks. 2 miejsc. Zarządzane kolumny liczbowe mają automatyczny format Sheets (dawny `0.##` powodował końcowy przecinek).
- Czasy ćwiczeń/snu/wybudzeń są tekstem `hh:mm:ss`, bez zawijania godzin po 24. Podczas synchronizacji dawne liczbowe czasy są konwertowane; teksty i formuły są zachowywane w tej migracji.
- Dawne kcal otrzymują format bez ułamka, ale trwałe zaokrąglenie ich wartości wymaga ponownego importu okresu. Tak samo ponowny import uzupełnia nowe średnie i poprawioną definicję snu; sam format nie przelicza danych.
- Dawne kolumny „ostatni pomiar” pozostają ukryte, a średnie mają osobne kolumny. Migracja Room 4→5 archiwizuje stare obserwacje/checkpointy pod `legacy_last:…`; rekordy źródłowe Connect pozostają bez zmian.
