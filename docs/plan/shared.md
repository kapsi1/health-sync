# Wspólne zależności i dalsze możliwości

[Indeks i granice zgód](README.md). Nowe wspólne mechanizmy B–E wymagają osobnego zlecenia/zgody; nie zastępuje to autoryzacji bieżących poprawek istniejącej ścieżki A.

## Pozostałe decyzje i zależności

- [ ] Katalog definicji, zgodności i własności kolumn dla B/C; rejestr pochodzenia, identyfikacji/wersji i stany poszczególnych importów/odczytów/wysyłek. Wspólny zapis chroni strukturę, daty, wybór kolumn i dane użytkownika także po odtworzeniu ustawień. Istniejący rejestr Takeout nie jest ogólnym rejestrem własności Sheets.
- [ ] Rozstrzygnąć na właściwym etapie: CLI czy HTML; wybrane pola; godzina i strefa API; główna noc/drzemki; źródło i harmonogram żywienia; polityka usunięć/konfliktów. Obecne sumy wszystkich sesji kończących się danego dnia nie zmieniają się po cichu w „główną noc”.
- [ ] Weryfikować definicje na kilku dniach przed całym archiwum. Surowe serie mogą służyć odtworzeniu Connect i obliczeniom, bez rozszerzania eksportu `daily` o serie.
- [ ] Dalsze możliwości, poza obecnym zakresem: konfigurowalna strefa z kontrolowaną przebudową dat, reguły Wi‑Fi/ładowania, powiadomienia bez danych na blokadzie, eksport CSV, kopia ustawień i dodatkowe wykresy/statystyki. Dashboard ma [osobny plan E](dashboard.md).

## Źródła zewnętrzne

Linki są punktami startowymi; schematy, zakresy OAuth, limity i ceny trzeba sprawdzić ponownie przed implementacją. Przegląd 2026-11-02 potwierdził dostępność stron katalogów [Connect](https://developer.android.com/health-and-fitness/health-connect/data-types) i [Google Health API](https://developers.google.com/health/data-types), a nie zgodność wszystkich kandydatów z prywatnym eksportem.

- Connect: [sen](https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions), [zapis](https://developer.android.com/health-and-fitness/health-connect/write-data), [agregacja i priorytety](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).
- Google Health: [data points v4](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints), [sen](https://developers.google.com/health/data-types/sleep), [OAuth](https://developers.google.com/health/setup), [treningi](https://developers.google.com/health/data-types/workouts), [eksport TCX](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/exportExerciseTcx).
- [WorkManager](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest.Builder): preferowana pora nie gwarantuje punktualności.
- Mapy: [Google Maps — użycie i rozliczenia](https://developers.google.com/maps/documentation/javascript/usage-and-billing), [polityka kafelków OSM](https://operations.osmfoundation.org/policies/tiles/).
- README konkretnego Takeout: UserSleeps, UserSleepStages, UserSleepScores, Sleep Profile (historycznie dostarczone 29.10.2026). Nie są w repozytorium; ich wersja i nazwy nie dowodzą zgodności CSV z API ani znaczenia daty pliku.
