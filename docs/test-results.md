# Wyniki i ograniczenia weryfikacji

## Porównanie Takeout i Connect — 2026-11-02

- Porównano wskazany eksport `test-data/` z aktualnymi rekordami Connect i prywatnym cache eksportu, dla wszystkich 14 statystyk. Wspólny zakres zakończonych dni: 2026-01-01–2026-10-28; eksport z 29 października jest migawką częściowego dnia. Pokrycie dni nie dowodzi kompletnej doby ani równoważności wartości.
- Analiza CSV Python (`csv.DictReader`) uwzględniała schematy/README, poprawną dobę Warsaw/DST, źródła, wersje snu, stan usunięcia treningów i obecność faktycznie wypełnionych pól. Nie czytano tras, kont ani sekretów. Prywatne porównanie wagi/tłuszczu używało skrótów czasu i wartości; różnic precyzji nie traktowano jako nowych zdarzeń.
- Tymczasowy `TakeoutCoverageReadOnlyProbe`: `assembleDebugAndroidTest --offline --no-daemon` **BUILD SUCCESSFUL**; `adb shell am instrument -w -e class pl.healthsync.TakeoutCoverageReadOnlyProbe pl.healthsync.test/androidx.test.runner.AndroidJUnitRunner` **OK (1 test)**. Dodatkowa, osobna próba `... -e class 'pl.healthsync.TakeoutCoverageReadOnlyProbe#refineWeights' ...` również **OK (1 test)**. Polecenia dokumentują eksperyment; test diagnostyczny usunięto następnie ze źródeł i odbudowano testowe APK. Nie jest to stały test regresyjny ani potwierdzenie implementacji API.
- Potwierdzono bogatsze pokrycie eksportu dla wielu metryk, a zarazem istnienie historycznych rekordów tylko w Connect. Uzupełniono [plan źródeł](plan/data-sources.md) o zachowanie takich oryginałów przed migracją. Sprawdzenie obecności pól treningów wykazało brak wartości w nowych `summary_*`, mimo starszych pól urządzenia i osobnych serii intensywności.
- Nie pobierano prywatnych danych Health API i nie odczytywano bezpośrednio komórek Sheets. Nie porównano wszystkich wartości i faz; nie wdrożono migracji, nie zmieniano pomiarów, uprawnień ani ustawień. Prywatny raport dla użytkownika zawiera liczności i zakresy; wartości pomiarów/identyfikatory nie trafiły do dokumentacji ani fixture'ów repozytorium.
- Walidacja dokumentacji: **155 lokalnych linków, w tym 12 kotwic, 0 braków**; `git diff --check` bez błędów. Tymczasowe skrypty i pliki porównania usunięto po zakończeniu analizy; zachowano prywatny raport dla użytkownika.

## Analiza kalorii i plan źródeł — 2026-11-02

- Tylko odczyt wskazanego `test-data/`: schematy README kalorii/aktywnej energii i podsumowań, zakresy dat oraz rozdzielenie źródeł. Polecenie analizy: skrypt Python podany przez stdin (`python -`) z `csv.DictReader`, lokalizacją doby Warsaw/DST i grupowaniem po źródle/dacie. Skrypt i prywatne wartości nie zostały zapisane do repo ani fixture'ów. Obecność CSV nie dowodzi kompletności dnia ani zgodności z odpowiedzią API.
- Telefon przez `adb`: odczyt deklaracji/uprawnień Google Health oraz paginowany odczyt `ActiveCaloriesBurnedRecord` dla 2026 w tymczasowym teście `ActiveEnergyReadOnlyProbe`. `assembleDebugAndroidTest --offline --no-daemon`: **BUILD SUCCESSFUL**; `adb shell am instrument -w -e class pl.healthsync.ActiveEnergyReadOnlyProbe pl.healthsync.test/androidx.test.runner.AndroidJUnitRunner`: **OK (1 test)**. Odczytana seria active pochodzi z telefonu, zaczyna się 2026-10-08; Google Health nie deklaruje zapisu active do Connect. Nie zapisywano ani nie usuwano rekordów zdrowotnych i nie zmieniano zgód.
- Dalszy odczyt `EnergyComparisonReadOnlyProbe` (tymczasowy, następnie usunięty): `assembleDebugAndroidTest --offline --no-daemon` **BUILD SUCCESSFUL**; `adb shell am instrument -w -e class pl.healthsync.EnergyComparisonReadOnlyProbe pl.healthsync.test/androidx.test.runner.AndroidJUnitRunner` **OK (1 test)**. Porównano total raw/przycięte/`aggregate(Instant)`/`aggregate(LocalDateTime)`/`aggregateGroupByPeriod`, źródło Fitbit, metadane offsetów/modyfikacji, rzeczywiste `HealthHistorySource.read` dla porcji miesięcznej i wielodniowej oraz prywatny cache Room. Ten sam dzień daje różne wyniki jako pierwszy i środkowy dzień lokalnego zapytania; suma pełnych rekordów Fitbit zgadza się z agregatem `Instant` Warsaw. Reprodukcja nie wymaga zmiany rekordów. Nie potwierdza to jeszcze wewnętrznej przyczyny w SDK ani zgodności surowej sumy Connect z aplikacją Health.
- W metadanych active potwierdzono natywny telefon Pixel 7a. Oficjalny [przewodnik native tracking](https://developer.android.com/health-and-fitness/health-connect/features/native-tracking) opisuje szacowanie active z kroków; dokładny wzór i użycie indywidualnej wagi nie zostały zweryfikowane. Obecność wagi w Connect od Fitbit potwierdzono bez zapisywania jej wartości w dokumentacji.
- Wskazany eksport Takeout kończy serie kalorii/active 2026-10-29, więc nie pozwala porównać rekordów z aplikacją Health dla późniejszych dni. Podejrzana mała energia ostatniego 15-minutowego rekordu Fitbit wymaga porównania z aktualnym eksportem/API; sama obserwacja nie dowodzi przyczyny różnicy.
- Wcześniejsza diagnostyka tej samej sesji: paginowany odczyt total za 2026 i porównanie agregatów z rekordami w wybranych dniach wykazały wartości bazowe SDK przy braku total/active oraz uzupełnianie luk; nie stanowi to pomiaru aktywności w brakujących okresach. Nie powielamy prywatnych wartości ani identyfikatorów w dokumentacji.
- Tymczasowy test usunięto ze źródeł; ponownie zbudowano i zainstalowano testowe APK bez diagnostyki. Komenda powyżej dokumentuje zakończony eksperyment, nie istniejący test regresyjny. Nie reinstalowano głównej aplikacji ani nie czyszczono jej danych.
- Przegląd oficjalnych katalogów/schematów API v4 i Connect oraz przewodników agregacji, kalorii, snu i temperatury: [zakres, źródła i ograniczenia](plan/data-sources.md). Wykryte niespójne opisy basal/BMR pozostają do rozstrzygnięcia. Nie wykonywano OAuth/odczytu prywatnego Health API ani bezpośredniego odczytu/zapisu Sheets; nie potwierdzono równoważności API/Takeout na koncie.
- Wynik prac: [plan kalorii](plan/calories.md), wspólne [uwagi dnia](plan/shared.md#uwagi-do-dnia) i [analiza źródeł](plan/data-sources.md). Nie wdrożono nowej kwalifikacji, kolumny ani migracji. Walidator Python: **153 lokalne linki, w tym 12 kotwic, 0 braków**; wskazane symbole istnieją, `git diff --check` bez błędów. To nie test docelowego zachowania.

## Przegląd rdzenia — 2026-11-02

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
