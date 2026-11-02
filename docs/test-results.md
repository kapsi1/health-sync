# Wyniki i ograniczenia weryfikacji

## Aktualna weryfikacja — 2026-11-02

- Przegląd dokumentacji względem kodu: konfiguracja Gradle/manifest, katalog 14 metryk, odczyt/agregacja, import/zmiany/tło, Room i migracje, kolejka/Sheets oraz ścieżki Takeout (podgląd, plan, zapis, wznowienie, anulowanie, wycofanie).
- `testDebugUnitTest assembleDebug --offline --no-daemon`: **BUILD SUCCESSFUL**, **157 testów JVM w 39 zestawach**, 0 błędów/niepowodzeń/pominięć; liczba z XML w `app/build/test-results/testDebugUnitTest/`. Użyto lokalnego Gradle 8.11.1, JDK 21 i przygotowanego cache `.tools/gradle-home`. Po błędzie połączenia z daemonem na localhost w sandboxie to samo polecenie przeszło poza nim.
- Ostrzeżenia builda: Kapt używa języka 1.9 przy Kotlinie 2.0; `LocalClipboardManager` jest przestarzały. Nie blokowały builda; nie zmieniano kodu aplikacji w ramach przeglądu dokumentacji.
- Spójność dokumentacji: **109 odnośników lokalnych i 51 wskazań plików źródłowych bez braków**; `git diff --check` bez błędów. Nie jest to test zachowania aplikacji.
- Nie uruchamiano teraz testów instrumentacyjnych ani importu/zapisu na prawdziwych danych telefonu i Sheets. Testy JVM nie dowodzą zgodności całej historii z Google Health.

## Pokrycie i dowody historyczne

| Obszar | Dowód / granice |
| --- | --- |
| Historia, zmiany, sen/parametry, Sheets | Testy JVM: porcje/wznowienie, tokeny i odzyskanie, daty/DST, braki/konflikty, nagłówki/format/scalanie, rewizja kolejki, timeout, sortowanie i liczniki |
| Takeout | Testy JVM parserów ZIP/CSV, mapowania/porównań, snu/nocnych podsumowań/treningów, planu, aktywności, porcjowego zapisu, odzyskania i wycofania |
| Room i żądania Sheets | Historyczne instrumentacyjne na fikcyjnych danych: transakcje/sesje, migracje Sync 1–4→5, rejestr Takeout, dziennik i struktury żądań; liczby/ograniczenia w [archiwum prób](completed/verification-2026-10.md) |
| Rzeczywiste Connect/Sheets | Potwierdzenia użytkownika dla podstawowej synchronizacji/tła/kolumn i ograniczonych prób Takeout: [rdzeń](completed/core.md), [Takeout](completed/takeout.md) |

Polecenia i dobór testów: [development.md](development.md). Historycznych liczb 16/41/49/54 ani instalacji APK nie traktuj jako wyniku obecnego kodu lub testu wszystkich funkcji.

## Nadal niesprawdzone lub sprawdzone tylko częściowo

- Wznowienie rzeczywistej historii po zamknięciu/restarcie aplikacji; przerwanie i kontynuację bez restartu użytkownik potwierdził.
- Brak/przywrócenie sieci, cofnięcie/ponowne przyznanie zgód, retry po timeout, restart telefonu, duży zakres i współbieżność na urządzeniu.
- Odtworzenie po wyczyszczeniu danych aplikacji z tym samym arkuszem; Picker: anulowanie, odmowa i nieprawidłowa struktura pliku.
- Nowy katalog/UI i najnowsze aliasy/etykiety wagi/dystansu, zaokrąglenie dawnych kcal, migracja czasów/kolumn/średnich na rzeczywistym arkuszu. Wcześniejsze polskie nazwy i automatyczny format bez końcowego przecinka potwierdzono.
- Agregaty dla DST, innej strefy urządzenia i wielu źródeł; większy zakres snu, nocne HRV/oddechy i temperatura skóry. Własne algorytmy nie gwarantują zgodności z Google Health.
- Rzeczywiste stare zmiany wagi/tłuszczu i odzyskanie po wygaśnięciu tokenu.
- Pozostałe scenariusze Takeout, w tym dzienne szacunki, treningi, wznowienie i anulowanie: konkretna lista w [planie A](plan/takeout.md). Dalsze ręczne testy wycofywania odłożono na życzenie użytkownika.

Nie zmieniaj tych punktów na potwierdzone bez nowego dowodu. Brak automatycznego rozliczania usunięć/zmian dat i brak bezpiecznego czyszczenia komórek Sheets to luki implementacji, opisane w planie.
