# Wspólne zależności i dalsze możliwości

[Indeks i granice zgód](README.md). Nowe wspólne mechanizmy B–E wymagają osobnego zlecenia/zgody; nie zastępuje to autoryzacji bieżących poprawek istniejącej ścieżki A.

## Pozostałe decyzje i zależności

- [ ] Wybrać docelową rolę źródeł i prywatnej bazy według [analizy](data-sources.md); porównanie API/Connect i podobnie nazwanych pól jest utrzymywane tam. Zacząć od [kalorii](calories.md), bez budowy całego dashboardu jako warunku korekty arkusza.
- [ ] Katalog definicji, zgodności i własności kolumn dla wszystkich adapterów; rejestr pochodzenia, identyfikacji/wersji i stany poszczególnych importów/odczytów/wysyłek. Wspólny zapis chroni strukturę, daty, wybór kolumn i dane użytkownika także po odtworzeniu ustawień. Istniejący rejestr Takeout nie jest ogólnym rejestrem własności Sheets. Wyjątek dla obecnego tymczasowego arkusza: [zakres](README.md#zakres-i-zgody).
- [ ] Rozstrzygnąć na właściwym etapie: CLI czy HTML; wybrane pola; godzina i strefa API; główna noc/drzemki; źródło i harmonogram żywienia; polityka usunięć/konfliktów. Obecne sumy wszystkich sesji kończących się danego dnia nie zmieniają się po cichu w „główną noc”.
- [ ] Weryfikować definicje na kilku dniach przed całym archiwum. Surowe serie mogą służyć odtworzeniu Connect i obliczeniom, bez rozszerzania eksportu `daily` o serie.
- [ ] Dalsze możliwości, poza obecnym zakresem: konfigurowalna strefa z kontrolowaną przebudową dat, reguły Wi‑Fi/ładowania, powiadomienia bez danych na blokadzie, eksport CSV, kopia ustawień i dodatkowe wykresy/statystyki. Dashboard ma [osobny plan E](dashboard.md).

## Uwagi do dnia

Ustalony kontrakt do implementacji, **2026-11-02**: jedna zarządzana kolumna `notes` z nagłówkiem **„Uwagi”**, obejmująca wszystkie statystyki. Nie jest piętnastą wybieraną metryką liczbową ani osobną kolumną dla każdej metryki.

- [ ] Zachować ustrukturyzowane przyczyny po dniu i metryce: kod problemu, źródło, jakość/kompletność, aktualność oraz wersja oceny. Krótki polski tekst arkusza jest projekcją tego stanu, nie magazynem surowych rekordów i nie dziennikiem całej synchronizacji.
- [ ] Wpisy jednoznacznie wskazują pole, np. „Kalorie całkowite: tylko szacunek spoczynkowy”, „Aktywne kalorie: nieporównywalny szacunek telefonu”, „Sen: niekompletne fazy”, „SpO₂: brak nocnego podsumowania”. Nie twierdzić „brak opaski”, jeśli wiadomo tylko, że brak próbek. Globalnego błędu sieci nie powielać bez związku z każdym dniem.
- [ ] Budować uwagi deterministycznie: stabilna kolejność po metryce/kodzie, brak duplikatów, osobne komunikaty oddzielone średnikiem. Ponowne przeliczenie zastępuje własną projekcję; rozwiązany problem znika, zamiast pozostawać w stale rosnącym tekście.
- [ ] Obsłużyć dzień z samymi uwagami i pustymi liczbami, wartości i uwagi z jednej rewizji oraz wyczyszczenie rozwiązanego problemu. Brak odpowiedzi źródła nie dowodzi rozwiązania; zachować ostatni potwierdzony wynik z informacją o nieaktualności, bez fabrykowania zera.
- [ ] „Uwagi” pozostają wspólnym miejscem także dla przyszłych danych API/Takeout i innych źródeł. Nie doklejać tekstu do komórek liczbowych ani ukrywać problemów w komentarzach Sheets. Komunikaty odnoszą się do faktycznie ocenianych/wybranych pól, bez zalewania uwagami o każdej niewłączonej statystyce.
- [ ] Dodać kolumnę do rozpoznawania nagłówków, modelu wyniku dnia, Room/kolejki, zapisu i sortowania całych wierszy. Zarezerwować nagłówek/klucz i sprawdzać kolizje. Nie przenosić prywatnych ID, kont, tokenów ani surowych serii do tekstu uwag.
- [ ] Testy syntetyczne: kilka problemów różnych metryk, powtórzenie, usunięcie jednej przyczyny, błąd źródła, dzień z samymi uwagami, zmiana wyboru statystyk, sortowanie, timeout i wznowienie. Migracja obecnego tymczasowego arkusza może zastąpić stare wyniki według nowego kontraktu; przyszłe własne notatki użytkownika wymagałyby odrębnej polityki.

## Źródła zewnętrzne

Linki są punktami startowymi; schematy, zakresy OAuth, limity i ceny trzeba sprawdzić ponownie przed implementacją. Zakres przeglądu semantyki 2026-11-02 i wykryte rozbieżności dokumentacji utrzymuje [porównanie źródeł](data-sources.md). Nie potwierdza on odpowiedzi prywatnego konta API ani zgodności wszystkich kandydatów z eksportem.

- Connect: [sen](https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions), [zapis](https://developer.android.com/health-and-fitness/health-connect/write-data), [agregacja i priorytety](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).
- Google Health: [data points v4](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints), [sen](https://developers.google.com/health/data-types/sleep), [OAuth](https://developers.google.com/health/setup), [treningi](https://developers.google.com/health/data-types/workouts), [eksport TCX](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/exportExerciseTcx).
- [WorkManager](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest.Builder): preferowana pora nie gwarantuje punktualności.
- Mapy: [Google Maps — użycie i rozliczenia](https://developers.google.com/maps/documentation/javascript/usage-and-billing), [polityka kafelków OSM](https://operations.osmfoundation.org/policies/tiles/).
- README konkretnego Takeout: UserSleeps, UserSleepStages, UserSleepScores, Sleep Profile (historycznie dostarczone 29.10.2026). Nie są w repozytorium; ich wersja i nazwy nie dowodzą zgodności CSV z API ani znaczenia daty pliku.
