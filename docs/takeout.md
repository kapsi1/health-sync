# Kontrakt importu Google Takeout

Stan kodu sprawdzony: **2026-11-02**. Mapa plików: [architektura](architecture.md); pozostała praca: [plan A](plan/takeout.md); dowody: [wykonany zakres](completed/takeout.md). Dotyczy importu mobilnego do Connect; desktopowe wzbogacenie arkusza jest osobnym, niezaczętym modułem.

## Granice operacji

- Wejście: wskazany lokalny ZIP, typy, daty i stała nazwa konta źródłowego. Etykieta konta jest normalizowana/haszowana do identyfikacji; używaj tej samej przy kolejnych eksportach. Analiza nie zapisuje rekordów; zapis wymaga jawnego zatwierdzenia podglądu.
- Obsługiwane w UI: sen, waga, tłuszcz, tętno spoczynkowe, treningi, HRV, oddechy podczas snu i temperatura skóry. Oddzielna analiza/zapis: dzienne szacunki kroków, dystansu, kalorii łącznych i aktywnych. Wzrost pozostaje tylko dla zgodności dawnych sesji (wznowienie/wycofanie).
- Standardowe dane trafiają do Connect, a następnie przez `HistoryImport` do Room/kolejki Sheets, również dla dat starszych niż 3 dni. Nie zapisuj tych pól bezpośrednio z ZIP do arkusza. Archiwum nie jest wysyłane do serwera aplikacji.
- Porównuj wszystkie dostępne źródła; brak zgody na historię nie dowodzi braku rekordów. Zgody zapisu tylko dla wybranych typów. `dataOrigin` jest Health Sync; nie podszywaj się pod Fitbit. Mapowanie zachowuje dostępne czasy/offsety, ale tworzy `Metadata.unknownRecordingMethod`; pełne odwzorowanie metody/urządzenia nie jest gotowe. Braków nie dopowiadaj.
- Waliduj schemat konkretnego eksportu (README, nagłówki, typy, jednostki, wartości i UTC). Data w nazwie pliku nie jest datą pomiaru/sesji. Nieobsługiwane i niejednoznaczne dane raportuj w podglądzie.

## Tożsamość i trwałość

1. `readVerifiedTakeout` sprawdza digest ZIP przed/po odczycie. Start ponownie sprawdza archiwum i porównuje świeżych kandydatów z zatwierdzonym podglądem.
2. `TakeoutJournalDatabase` zapisuje pełny plan przed zapisem zewnętrznym: typ, stabilne `clientRecordId`, wersję/hash treści oraz sesję/konto/archiwum. Tożsamość obejmuje typ i konto; brak źródłowego ID pomiaru wymaga istniejącej reguły klucza, nie numeru wiersza.
3. `TakeoutBatchWriteCoordinator` sprawdza rekordy przed i po porcji (serwisy używają 10 rekordów); potwierdza faktyczne ID Connect i czas modyfikacji. Po przerwaniu lub niejednoznacznym zapisie najpierw ponownie odczytuje, zamiast ślepo ponawiać.
4. Wznowienie wymaga tego samego digestu i odtwarza plan przez `reconstructTakeoutPlan`/`matchRecordedTakeoutPlan`. Zapisane ID/hash muszą pasować jednoznacznie. Nie obliczaj ponownie zmiennych wykluczeń i priorytetów źródeł.
5. Po zakończeniu odczytaj dotknięte daty przez Connect i dopiero zakończ sesję. Późniejsze oryginały zapisane przez inną aplikację nie są deduplikowane przez nasze ID; obecny import kontroluje kolizje, ale automatyczne zastępowanie własnej kopii oryginałem nie jest gotowe.

## Reguły typów i konfliktów

| Typ | Reguła |
| --- | --- |
| Waga/tłuszcz | Porównanie konkretnych pomiarów: typ, czas, jednostka, wartość i źródło; nie pomijaj całego dnia tylko dlatego, że jest inne ważenie. Nie przepisywać rekordów pochodzących z Connect. |
| Tętno spoczynkowe | `Daily Resting Heart Rate`, zachowanie chwili pomiaru; istniejący dzień pomijany, niejednoznaczne duplikaty blokowane. Wartość ułamkowa jest zaokrąglana do całkowitego BPM wymaganego przez rekord. |
| HRV/oddechy/temperatura | Gotowe podsumowania Google Health, nie fikcyjne chwilowe pomiary z ogólnych średnich. Dzień z dowolnym istniejącym wpisem danego typu jest pomijany; nie zastępuj gęstych próbek HRV. Do mapowania potrzebna jedna weryfikowalna sesja v2 z >3 h snu tego dnia; to przybliżenie przedziału, nie dowód zgodności z Health. Temperatura skóry wymaga wartości i bazy do wyliczenia delty; brak bazy oznacza niedostępny wynik. |
| Treningi | Odtworzenie sesji i rodzaju, bez GPS i szczegółowych pomiarów. Istniejące dopasowanie sprawdzaj przed oceną nakładania (porównanie czasu ignoruje ułamki sekund); wiele dopasowań pozostaje konfliktem. W tym samym rodzaju treningu opaska ma priorytet nad autodetekcją, także gdy jedna automatyczna sesja nakłada się na kilka rozłącznych sesji opaski. Inne rodzaje i nakładające się sesje opaski wymagają rozstrzygnięcia; nie usuwaj cudzych rekordów. |

Connect może dopisać brakujący offset. Porównanie dopuszcza jedynie offset zgodny z warszawskim czasem danej chwili i sprawdza pozostałą treść; wycofanie nadal kontroluje czas modyfikacji. Konkretne tolerancje i porównania utrzymuj z testami w `TakeoutCompare`, `HealthConnectTakeoutGateway` i `TakeoutRecordMapping`.

Wykluczenia treningów są po stabilnym ID w prywatnych `takeout_exclusions/exercise_ids`; podgląd i start importu czytają ten sam zbiór. Użytkownik wykluczył jeden rekord bieżni, zachowując automatyczny spacer. Nie uogólniaj tego na rodzaj ćwiczenia. ID jest tylko na urządzeniu; po wyczyszczeniu danych wymaga odtworzenia, UI zarządzania wykluczeniami nie istnieje.

## Sen i wybór wersji

- Połącz `UserSleeps`/`UserSleepStages` po użytkowniku i `sleep_id`. `minutes_asleep` waliduje fazy, nie tworzy sztucznego krótszego przedziału. `algorithm_version` odróżnia v1/v2; `data_source` opisuje metodę zapisu, nie aplikację lub urządzenie.
- Użytkownik świadomie wybrał v2 mimo różnic sumy faz i wybudzeń względem Health. W jednoznacznej parze wybieraj v2. Rozłączne v2 w grupie z jedną nakładającą się v1 są osobnymi sesjami, z kontrolą zakresu v1 i kolizji Connect; przy wznowieniu rozpoznawaj własne niezmienione sesje tej grupy.
- Pojedyncza v1 bez alternatywy jest dopuszczona, jeśli dane i źródło są poprawne i brak kolizji. Jej ogólny stan snu nie dowodzi zerowych wybudzeń. Wzajemnie nakładające się v2, wiele v1 i pozostałe niejednoznaczności są konfliktami.
- Atrybuty zewnętrznych źródeł łącz po `sleep_id`. Brak atrybutów dla sesji sam w sobie nie dowodzi własnego źródła. Brak całego pliku atrybutów lub wskazanie zewnętrznego źródła wyklucza automatyczny zapis.
- Score, oceny składowe i Sleep Profile są poza zakresem. Obliczanie statystyk po imporcie: [kontrakty danych](data-contracts.md).

## Szacunkowa aktywność dzienna

- W Connect zapisujemy jeden rekord przedziałowy typu na dzień w `Europe/Warsaw`, z poprawną długością doby DST. Jakiekolwiek istniejące dane danego typu nakładające się na dzień oznaczają pominięcie całego dnia i pokazanie go do sprawdzenia — użytkownik przyjął takie założenie kompletności.
- CSV nie opisuje granic interwałów ani deduplikacji producenta. Algorytm minutowy jest zatwierdzonym szacunkiem, bez gwarancji zgodności z Health i bez odtworzenia rozkładu godzinowego. Connect nie przechowuje osobnej flagi „szacunek”; oznaczenie jest w podglądzie/historii.
- Priorytet implementacji: WEARABLE → PHONE → GOOGLE_HEALTH → GOOGLE_FIT. Mapowanie jawnych etykiet w `TakeoutActivityCsv`: `Charge 6`, `MobileTrack`, `Google Health App`, `Google Fit`; `Fit Health Connect` i `Phone Health Connect` są wykluczone. Rodzaj urządzenia i aplikacja to różne cechy; nie zgaduj klasyfikacji nieznanej nazwy.
- Kalorie łączne/aktywne: sumuj różne wpisy jednego źródła w minucie. Identyczny czas tego samego źródła jest niejednoznaczny; dla kroków/dystansu także kilka wpisów jednego źródła w minucie blokuje dzień. Niefinitywne/ujemne dane i nieznane źródła blokują oszacowanie.
- Przed zapisem ponownie sprawdź podgląd i kolizje; wznowienie, rejestr i wycofanie używają osobnego gatewaya aktywności i wspólnego dziennika Takeout.

## Przerwanie, anulowanie i wycofanie

- Przerwanie/błąd pozostawia aktywną sesję do wznowienia. Nowy import blokuje sesja `active` lub `undoing`.
- „Anuluj import” porzuca dalszy plan i ustawia `cancelled`; zapisane rekordy i dowody pozostają. Anulowanej sesji nie można wznowić ani automatycznie wycofać. Część zapisanych danych może jeszcze nie być przeliczona do kolejki — potrzebny ponowny import dat z Connect.
- Wycofanie dotyczy sesji `complete`/`undoing`: usuwa tylko własny, dokładnie zidentyfikowany, niezmieniony rekord po sprawdzeniu pochodzenia, wersji, pełnej treści i czasu modyfikacji. Po usunięciu ponowny odczyt potwierdza brak; potem następuje rekonsyliacja dotkniętych dni w Room. Niepewne komórki Sheets pozostają.
- Docelowa decyzja: czyścić wyłącznie własne niezmienione komórki, gdy można to udowodnić; sama zgodność wartości nie wystarcza. Rejestr własności i bezpieczne warunkowe czyszczenie nie są zaimplementowane.
- Użytkownik zrezygnował z dalszych ręcznych testów wycofywania; w razie problemów przewiduje kopię Health Connect i ponowną synchronizację. Zachowaj funkcję i testy automatyczne, a nieprzeprowadzonych prób nie oznaczaj jako zakończone.

## Limity wejścia

ZIP: do 512 MiB; do 30 000 wpisów i 512 MiB danych po rozpakowaniu. Główny parser zachowuje do 64 MiB plików danych; inspector ogranicza porównanie do 100 000 rekordów. Aktywność jest analizowana strumieniowo: do 366 dni, 1,5 mln wybranych wierszy (parser pojedynczego CSV ma limit 2 mln). Zmiana limitów wymaga sprawdzenia pamięci i czasu, nie samego zwiększenia stałej. Źródło: `TakeoutFile`, `TakeoutArchive`, `TakeoutActivityArchive`, `TakeoutActivityCsv`, `TakeoutInspector`.
