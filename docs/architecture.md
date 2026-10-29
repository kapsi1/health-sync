# Architektura Health Sync

Health Connect jest obecnie jedynym zaimplementowanym źródłem synchronizacji. Aplikacja odczytuje dostępne, wybrane statystyki i przelicza je na dni w strefie `Europe/Warsaw`; nie pobiera historii przechowywanej wyłącznie w Google Health/Fitbit. Docelowo Connect pozostaje podstawowym źródłem prawdy po uzupełnieniu historii z Takeout przez aplikację mobilną. Plan obejmuje osobne wzbogacanie arkusza o zatwierdzone dodatkowe pola API, jednorazowy desktopowy import tych samych pól z Takeout oraz niezależny proces odżywiania z innego arkusza do Connect. Żadna z tych czterech nowych funkcji nie jest zaimplementowana; przed rozpoczęciem każdej z nich trzeba zapytać użytkownika i uzyskać zgodę. Zakres i kolejność opisuje `health_connect_sheets_agent_plan.md`. Sleep score jest wyłączony ze względu na brak równoważnego pola API.

## Przepływ danych

1. `MetricRegistry` opisuje statystyki, uprawnienia i kolumny. Wybór jest zapisywany w DataStore.
2. `HealthHistorySource` odczytuje dane z Health Connect. `HistoryImport` dzieli ręczny import na miesięczne porcje, zapisuje wyniki, a po przerwaniu wznawia trwałą sesję. Wznowienie z identyczną datą i wyborem statystyk pomija ukończone porcje; nowe niezależne uruchomienie przelicza wybrany zakres.
3. Room przechowuje obserwacje dzienne, checkpointy, sesję importu i kolejkę dni. Zapis porcji, checkpointu i oznaczenie jej do wysłania są transakcyjne. Brak odczytanej wartości nie kasuje wcześniejszej.
4. `SheetSync` przed każdą porcją pobiera aktualny stan zakładki `daily`, sprawdza nagłówki i unikalność dat, a następnie dodaje nowe wiersze lub zapisuje wybrane komórki istniejących. Po wysyłce sortuje wiersze zakładki `daily` rosnąco według daty: nagłówek pozostaje na miejscu, a wszystkie kolumny przemieszczają się razem z wierszami. Kolejkę potwierdza dopiero po udanym sortowaniu, o ile lokalne dane nie zmieniły się w międzyczasie. Po niejednoznacznym wyniku ponowna próba ponownie odczytuje arkusz.
5. WorkManager okresowo przelicza ostatnie trzy dni oraz sprawdza listę zmian wagi i tkanki tłuszczowej. Starsze zmiany są przeliczane dla ich dat. Po wygaśnięciu tokenu aplikacja odtwarza poprzednio importowany zakres, porcjowo.

Operacje na arkuszu są serializowane między synchronizacją ręczną, tłem i zmianą pliku. Kolejność kolumn jest ustalana na podstawie nagłówków. Kolumny wyłączonych statystyk są ukrywane, a po włączeniu dodawane albo odkrywane; pozostałe zakładki i kolumny pozostają pod kontrolą użytkownika.

Eksport zaokrągla wartości liczbowe do maksymalnie dwóch miejsc dziesiętnych przed wysłaniem przez API; Room zachowuje pełną precyzję. Format kolumn jest tylko uzupełnieniem prezentacji. `exercise_hours`, `sleep_hours` i `sleep_awake_hours` są zapisywane jako tekst `hh:mm:ss`, z godzinami bez zawijania po 24. Dawne liczbowe czasy są konwertowane przy uzgadnianiu kolumn, bez zmiany tekstów i formuł.

`sleep_hours` sumuje fazy SLEEPING/LIGHT/DEEP/REM. Opcjonalne `sleep_awake_hours` sumuje AWAKE/AWAKE_IN_BED/OUT_OF_BED tylko między pierwszą i ostatnią fazą snu w danej sesji, bez minimalnej długości ponownego snu. Zasypianie i końcowe czuwanie są wyłączone. Oba wyniki sumuje się dla sesji zakończonych danego dnia w Europe/Warsaw. Luka między oddzielnymi sesjami nie jest automatycznie traktowana jako czuwanie. Brak faz, nieznane fazy, luki, nieprawidłowe przedziały i nakładające się fazy nie są zgadywane. Pełny czas snu wymaga kompletnej osi faz sesji; wybudzenia mogą być dostępne przy kompletnych fazach wewnętrznych mimo nieznanych brzegów. Gdy jednej sesji brakuje wyniku, nie eksportujemy częściowej sumy jako pełnej statystyki dobowej. Nakładające się sesje wciąż powodują błąd wymagający rozstrzygnięcia, zamiast podwójnego liczenia. Brak nowego wyniku nie usuwa starych danych z Room/Sheets; dawne wartości liczące pełną sesję wymagają ponownego importu, a przy niekompletnych fazach mogą pozostać do ręcznego wyjaśnienia.

## Dostęp i diagnostyka

Health Connect wymaga osobnych zgód na typy danych, starszą historię i odczyt w tle. Google Identity uzyskuje uprawnienie `drive.file`; aplikacja nie ma własnego serwera ani stałego klucza w kodzie. ID lub URL istniejącego arkusza nie nadaje dostępu. Androidowy Google Picker pozwala wybrać plik i przyznać dostęp; użytkownik potwierdził działanie na urządzeniu; funkcja wymaga włączenia Google Picker API w projekcie Cloud.

Dziennik nie zawiera wartości pomiarów ani tokenów. Liczniki wysyłki rozróżniają dodane wiersze i zapisy do istniejących. Zapis do istniejącego wiersza nie oznacza, że wartość uległa zmianie, ponieważ przed wysłaniem nie jest porównywana zawartość poszczególnych komórek. Przy niejednoznacznej odpowiedzi serwera licznik obejmuje wyłącznie zakończone porcje aktualnej próby.

Usunięcie pomiaru wagi lub tkanki tłuszczowej może zostać zasygnalizowane, ale nie powoduje usunięcia komórki w arkuszu. Automatyczne rozliczanie usunięć i zmiany daty pomiaru wymaga dalszej implementacji.
