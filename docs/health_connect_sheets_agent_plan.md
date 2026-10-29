# Health Sync — aktualny plan

**Aktualizacja:** 29.10.2026

**Cel:** prywatna aplikacja Android, bez własnego serwera, synchronizująca wybrane dzienne statystyki z Health Connect do Google Sheets. Interfejs jest po polsku. Poprawność danych i możliwość wznowienia mają pierwszeństwo przed wygodą i wydajnością.

Oznaczenia: **[x]** zaimplementowane i potwierdzone w podanym zakresie; **[~]** częściowo wykonane albo wymagające dalszych testów; **[ ]** do wykonania. Testy na urządzeniu i testy automatyczne oznaczamy osobno. Stan opiera się na kodzie i dotychczasowych potwierdzeniach użytkownika.

## Uzgodniony zakres i zasady

- Tylko użyteczne agregaty dzienne w zakładce `daily`; bez eksportu pojedynczych pomiarów tętna i innych szczegółowych serii.
- Health Connect jest źródłem danych. Dane, które istnieją wyłącznie w Google Health/Fitbit lub Takeout, nie są dostępne dla tej synchronizacji.
- Użytkownik ponownie rozważa import z Google Takeout lub Google Health API (29.10). Na razie porównanie podejść i projekt zasad łączenia źródeł; implementacja wymaga wyboru źródła i reguł konfliktów. Zapis importu do Connect jest osobną, opcjonalną funkcją.
- Aplikacja korzysta z OAuth użytkownika i `drive.file`, bez własnego backendu i bez sekretów w repozytorium. Wpisanie ID nie daje samo w sobie dostępu do pliku.
- Zachowuj inne zakładki i kolumny użytkownika. Brak rekordu lub odmowa dostępu nie oznaczają zera ani polecenia usunięcia istniejącej wartości. Nie kasuj ręcznie wpisanych danych w wyniku braku danych źródłowych.
- Dzień jest identyfikowany przez `date` w strefie `Europe/Warsaw`; powtórzenia i ponowienia nie mogą tworzyć duplikatów. Pomiary snu przypisuje się do dnia zakończenia sesji.
- Automatyczna synchronizacja jest opcjonalna, uruchamiana przez WorkManager w odstępach 1, 3, 12 albo 24 godzin. Android może opóźnić uruchomienie. Każdy przebieg sprawdza ostatnie 3 dni i zmiany wagi oraz tkanki tłuszczowej; nie obiecujemy natychmiastowego wyzwalania po zmianie.

## Stan funkcji

### Aplikacja i interfejs

- [x] Cztery zakładki: Sync, Dane, Arkusz, Dziennik; logo w nagłówku i ikona aplikacji.
- [x] Ciemny motyw, systemowe kolory dynamiczne na Androidzie 12+ i awaryjna paleta na starszym systemie; dopasowanie podpisu nawigacji do większego tekstu.
- [x] Dane: 22 statystyki dzienne, grupy, liczniki i przełączniki także dla wyboru częściowego; przycisk brakujących zgód i link do ustawień Health Connect.
- [x] Data początkowa z natywnym wybieraczem, zapamiętywanie daty, wspólny przycisk importu i wysyłki, pasek rzeczywistego postępu porcji, przerwanie importu.
- [x] Kontekstowa prośba o starszą historię i odczyt w tle; konfiguracja automatycznej synchronizacji i czas ostatniego uruchomienia.
- [x] Dziennik ręczny i automatyczny, grupowanie etapów i ponowień, filtr błędów oraz kopiowanie nowych szczegółów bez wartości pomiarów lub tokenów; poprzednia historia zachowana. Nowe wpisy odróżniają wynik ostatniej próby od wcześniejszych błędów; starsze wpisy z niejednoznacznym wynikiem nie są zgadywane.
- [~] Dziennik wskazuje odpowiednią zakładkę przy typowych błędach i zachowuje kody HTTP. Pokazuje osobno liczbę dodanych wierszy i zapisów do istniejących (bez twierdzenia, że wartości się zmieniły). Szczegółowe działania naprawcze pozostają do uzupełnienia; użytkownik potwierdził działanie liczników w rzeczywistym arkuszu.
- [x] Pasek postępu jest dostępny we wszystkich zakładkach; przerwany import jest oznaczony i można go wznowić. Użytkownik potwierdził działanie paska i importu na urządzeniu.

### Health Connect i import

- [x] Odczyt i agregacja dostępnych danych, wybór metryk i zgód, lokalna baza Room, checkpointy i kolejka; brak rekordu nie nadpisuje danych zerem.
- [x] Starsza historia zależy od dostępności funkcji i zgody Health Connect. Data początku jest wybierana ręcznie; brak osobnego trybu „cała dostępna historia”.
- [x] Dodanie metryki uruchamia uzupełnianie wybranego wcześniej zakresu, a odznaczenie nie usuwa wartości.
- [x] Import jest dzielony na porcje i ma trwałą sesję wznowienia; rozpoczęcie pełnego przeliczenia i unieważnienie punktów kontrolnych są atomowe. Po przerwaniu wznawia porcja po porcji, a po ukończeniu kolejne uruchomienie ponownie odczytuje wybrany zakres. Automatyczne testy obejmują przerwanie, następny dzień i migrację bazy.
- [~] Użytkownik potwierdził wznowienie importu na rzeczywistych danych; ponowne uruchomienie aplikacji przed wznowieniem pozostaje do sprawdzenia.
- [~] Lista zmian Health Connect wykrywa nowe i zmienione rekordy wagi i tłuszczu, także sprzed ostatnich 3 dni. Wygaśnięcie tokenu uruchamia porcjowe odtwarzanie dotychczas importowanego zakresu. Przypadki na prawdziwych danych wymagają dalszego testu.
- [ ] Bezpieczne przetwarzanie usunięć i zmian daty pomiaru: minimalne mapowanie ID na dzień, potwierdzony ponowny odczyt i dopiero wtedy ewentualne wyczyszczenie odpowiedniej komórki; brak zgody lub błąd nigdy nie oznacza usunięcia.
- [~] Wsparcie większej liczby typów danych wyłącznie po ustaleniu sensownej dziennej statystyki, jednostek, uprawnień i sposobu unikania duplikacji źródeł.

### Google Sheets

- [x] Autoryzacja Google, tworzenie arkusza, otwieranie, sprawdzanie dostępu oraz ponowne podłączanie przez ID lub URL; użytkownik potwierdził otwieranie i zmianę arkusza.
- [x] Przy zmianie arkusza wybór całej lokalnej historii lub samej kolejki; synchronizacja dostępu do arkusza między operacją ręczną i tłem.
- [x] Aktualizacja po dacie i wznowienie kolejki, bez zmiany obcych zakładek i kolumn. Istnieją testy logiki ponowienia oraz timeoutu po zapisie.
- [x] Sortowanie wierszy `daily` rosnąco według daty, razem ze wszystkimi kolumnami, także gdy kolejka jest pusta. Testy jednostkowe obejmują starsze daty i ponowienie po błędzie sortowania; użytkownik potwierdził działanie na prawdziwym arkuszu.
- [x] Kolumny wyłączonych metryk są ukrywane, a włączonych dodawane lub odkrywane; uzupełnianie historii potwierdzone przez użytkownika.
- [x] Zaokrąglanie liczb przy eksporcie w Health Sync do maksymalnie 2 miejsc po przecinku (także kcal, dystans i waga); pełna precyzja pozostaje w lokalnej bazie. Czas ćwiczeń i snu jako tekst `hh:mm:ss`, z migracją dawnych liczbowych czasów w arkuszu. Testy jednostkowe; nowy wygląd wymaga potwierdzenia na urządzeniu. Ponowny import zakresu przepisuje wcześniejsze wartości liczbowe z nową precyzją.
- [~] Zaimplementowano Google Picker dla Androida do wyboru pliku i nadania dostępu `drive.file`; w projekcie Cloud trzeba włączyć Google Picker API. Użytkownik potwierdził działanie na urządzeniu; pozostaje sprawdzić anulowanie, odmowę i nieprawidłową strukturę arkusza. Nazwa arkusza jest pobierana po otwarciu zakładki, a ID można edytować w osobnym oknie. Kolejka zostaje zachowana przy błędach.
- [~] Przetestować przywrócenie po wyczyszczeniu danych aplikacji z tym samym arkuszem oraz odmowę i ponowne przyznanie dostępu.

### Testy, wydanie i dokumentacja

- [x] Build debug i testy jednostkowe; użytkownik potwierdził ręczną synchronizację, cykliczne działanie w tle, podstawowe wybieranie danych, zmiany arkusza i ostatnią wersję interfejsu.
- [x] Testy instrumentacyjne Room i migracji 1→4, 2→4 i 3→4 przeszły na podłączonym telefonie (16 testów instrumentacyjnych w całym zestawie, w tym dziennika i żądania sortowania).
- [~] Zweryfikować wznowienie na rzeczywistych danych po przerwaniu i restarcie aplikacji.
- [ ] Sprawdzić brak sieci, odwołanie zgód, retry po timeout, restart telefonu, duży zakres historii oraz skutki współbieżnych operacji na urządzeniu.
- [ ] Zweryfikować wybrane agregaty ze źródłem Health Connect, w tym strefę czasu, zmianę czasu, sesje snu i różne źródła danych.
- [x] README, instrukcja Google Cloud, `docs/architecture.md` i `docs/test-results.md` opisują aktualny stan, ograniczenia i testy.
- [ ] Przygotować powtarzalną instrukcję wydania oraz podpisany release APK, jeśli użytkownik będzie go potrzebował; nie umieszczać kluczy podpisu w repozytorium.

## Rozważane dodatkowe źródła danych

- [x] Użytkownik potwierdził, że reinstalacja Google Health przywróciła historię z chmury i poprawiła eksport bieżącego snu, wagi i tłuszczu do Connect; część dawnego snu nadal nie trafia do Connect. Nie znamy przyczyny i nie zakładamy braku danych w chmurze.
- [ ] Google Health API: najpierw diagnostyczny odczyt znanej daty, następnie ewentualna synchronizacja wybranych agregatów; dodatkowy OAuth, stronicowanie, wznowienie i jawna reguła pierwszeństwa źródeł.
- [ ] Takeout: lokalny import wybranego archiwum przez systemowy wybieracz, podgląd zakresu i liczników, walidacja schematu CSV, jednostek i stref czasowych, wznowienie i deduplikacja. Daty danych odczytywać z rekordów, nie z nazwy pliku. Bez domyślnego zapisu do Connect.
- [ ] Sen: ustalić zgodną definicję `sleep_hours` dla Connect, API i Takeout. Obecny kod liczy pełen czas sesji (może obejmować czuwanie); dla rzeczywistego czasu snu wykorzystać dostępne podsumowanie lub fazy i określić regułę dla sesji bez faz. Bez eksportu szczegółów i wyników punktowych snu, o ile użytkownik ich nie wybierze.
- [ ] Opcjonalne odtworzenie danych z Takeout/API w Connect: oddzielna zgoda zapisu, podgląd, stabilne identyfikatory, rozpoznawanie istniejących rekordów i możliwość wycofania wyłącznie własnego importu. Źródłem zapisu będzie Health Sync; nie podszywać się pod Fitbit i nie przepisywać danych odczytanych z Connect z powrotem do Connect.

## Dalsze możliwości, bez zmiany obecnego zakresu

- Zmiana strefy czasu z kontrolowaną przebudową dat; reguły Wi-Fi i ładowania; powiadomienia o problemach bez danych zdrowotnych na ekranie blokady.
- Opcjonalne wykresy, dashboard, eksport CSV i kopia ustawień.
- Więcej dziennych statystyk Connect po wyborze zakresu; obecne 22 statystyki nie obejmują wszystkich typów danych platformy. Zachować zasadę bez szczegółowych serii pomiarów.

## Techniczne zasady implementacji

- Kotlin, Compose i Material 3; Health Connect, Room, DataStore, WorkManager, AuthorizationClient i Google Sheets API.
- Nie zakładać, że brak starszych danych w Health Connect oznacza ich brak w Google Health; nie pobierać prywatnych eksportów ani nie dodawać ich do repozytorium.
- Uwzględniać błąd sieci, wygaśnięcie zgody, przerwanie procesu i ponowienie po niejednoznacznym wyniku operacji Google Sheets.
- Nie zapisywać tokenów ani wartości zdrowotnych w diagnostyce. Użytkownik może dodawać własne zakładki i kolumny.
- Zmianę obecnych założeń wprowadzaj do planu razem z kodem; nie przedstawiaj testów lub integracji jako wykonanych bez dowodu.
