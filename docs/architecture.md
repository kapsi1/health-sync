# Architektura Health Sync

Health Connect jest jedynym źródłem synchronizacji. Aplikacja odczytuje dostępne, wybrane statystyki i przelicza je na dni w strefie `Europe/Warsaw`; nie pobiera historii przechowywanej wyłącznie w Google Health/Fitbit. Import Takeout pozostaje wstrzymany.

## Przepływ danych

1. `MetricRegistry` opisuje statystyki, uprawnienia i kolumny. Wybór jest zapisywany w DataStore.
2. `HealthHistorySource` odczytuje dane z Health Connect. `HistoryImport` dzieli ręczny import na miesięczne porcje, zapisuje wyniki, a po przerwaniu wznawia trwałą sesję. Wznowienie z identyczną datą i wyborem statystyk pomija ukończone porcje; nowe niezależne uruchomienie przelicza wybrany zakres.
3. Room przechowuje obserwacje dzienne, checkpointy, sesję importu i kolejkę dni. Zapis porcji, checkpointu i oznaczenie jej do wysłania są transakcyjne. Brak odczytanej wartości nie kasuje wcześniejszej.
4. `SheetSync` przed każdą porcją pobiera aktualny stan zakładki `daily`, sprawdza nagłówki i unikalność dat, a następnie dodaje nowe wiersze lub zapisuje wybrane komórki istniejących. Potwierdza kolejkę dopiero po udanej wysyłce, o ile lokalne dane nie zmieniły się w międzyczasie. Po niejednoznacznym wyniku ponowna próba ponownie odczytuje arkusz.
5. WorkManager okresowo przelicza ostatnie trzy dni oraz sprawdza listę zmian wagi i tkanki tłuszczowej. Starsze zmiany są przeliczane dla ich dat. Po wygaśnięciu tokenu aplikacja odtwarza poprzednio importowany zakres, porcjowo.

Operacje na arkuszu są serializowane między synchronizacją ręczną, tłem i zmianą pliku. Kolejność kolumn jest ustalana na podstawie nagłówków. Kolumny wyłączonych statystyk są ukrywane, a po włączeniu dodawane albo odkrywane; pozostałe zakładki i kolumny pozostają pod kontrolą użytkownika.

## Dostęp i diagnostyka

Health Connect wymaga osobnych zgód na typy danych, starszą historię i odczyt w tle. Google Identity uzyskuje uprawnienie `drive.file`; aplikacja nie ma własnego serwera ani stałego klucza w kodzie. ID lub URL istniejącego arkusza nie nadaje dostępu. Androidowy Google Picker pozwala wybrać plik i przyznać dostęp; użytkownik potwierdził działanie na urządzeniu; funkcja wymaga włączenia Google Picker API w projekcie Cloud.

Dziennik nie zawiera wartości pomiarów ani tokenów. Liczniki wysyłki rozróżniają dodane wiersze i zapisy do istniejących. Zapis do istniejącego wiersza nie oznacza, że wartość uległa zmianie, ponieważ przed wysłaniem nie jest porównywana zawartość poszczególnych komórek. Przy niejednoznacznej odpowiedzi serwera licznik obejmuje wyłącznie zakończone porcje aktualnej próby.

Usunięcie pomiaru wagi lub tkanki tłuszczowej może zostać zasygnalizowane, ale nie powoduje usunięcia komórki w arkuszu. Automatyczne rozliczanie usunięć i zmiany daty pomiaru wymaga dalszej implementacji.
