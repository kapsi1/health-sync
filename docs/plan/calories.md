# Kalorie całkowite i aktywne — spójność historii i bieżących danych

Plan, **2026-11-02**. Zlecenie obejmuje analizę i zapis planu; poniższy kontrakt nie jest jeszcze zaimplementowany. [Indeks](README.md) · [Obecny kontrakt/kod](../data-contracts.md) · [Wybór źródeł](data-sources.md) · [Dowody i ograniczenia analizy](../test-results.md).

## Ustalone wymagania

- Jedna definicja każdej kolumny dla historii 2026 i przyszłych dni. Nie wymagamy ponownej instalacji Fit. Dni bez opaski muszą być obsługiwane bez dopowiadania aktywności.
- Priorytet: porównywalność i brak mylących wartości. **Brak porównywalnych danych oznacza pustą komórkę, także gdy telefon potrafi podać inny szacunek.** Użytkownik wybrał tę regułę zamiast zastępowania danych opaski modelem telefonu.
- Sam BMR ani zastępczy wynik agregacji Connect nie trafiają do kolumny „Spalone kalorie”. BMR wolno zachować prywatnie jako składnik modelu, poza eksportem jako rzekoma pełna energia dobowa.
- Obecne dane arkusza są tymczasowe; użytkownik nie wprowadzał ręcznych zmian i dopuszcza ich zastąpienie/usunięcie, a nawet utworzenie arkusza od nowa. Nie wymaga to ponownego pytania o ochronę tych samych starych wartości przy realizacji zleconej korekty. Zapis planu sam w sobie nie uruchamia teraz usuwania ani importu.
- Jedna nowa kolumna **„Uwagi”** obejmuje problemy wszystkich statystyk dnia, nie tylko kalorii. Jej wspólny kontrakt: [shared.md](shared.md#uwagi-do-dnia).

## Docelowy kontrakt obu kolumn

| Pole | Znaczenie | Warunek eksportu |
| --- | --- | --- |
| `total_calories` / `total_kcal` / „Spalone kalorie” | Całkowity dobowy szacunek wydatku energetycznego, z częścią spoczynkową i aktywnością; kcal | Zakończona doba, porównywalny model/rodzina źródeł, potwierdzona użyteczność i kompletność danych o aktywności. Samo istnienie 24 h rekordów lub dodatniej sumy nie wystarcza. Sam BMR → puste. |
| `active_calories` / `active_kcal` / „Aktywne kalorie” | Dobowy szacunek energii aktywnej **ponad BMR**, nie tylko kalorie treningów ani dawne Fitbit `activityCalories`; kcal | Ta sama polityka źródeł/modelu w historii i bieżącym okresie. Wynik częściowy lub sam model telefonu bez wykazanej porównywalności → puste. Jawne, wiarygodne zero przy potwierdzonym obserwowaniu aktywności może pozostać zerem. |

Reguły wspólne:

- Doba `[start, endExclusive)` w `Europe/Warsaw`, z rzeczywistą długością DST. Dzisiaj pozostaje nieukończone: wartości dobowe w arkuszu puste i uwaga „Dzień w toku”; podgląd aplikacji może osobno pokazywać wynik bieżący, wyraźnie częściowy.
- Definicja nie zmienia się przy zmianie transportu API ↔ Takeout ↔ Connect. To samo zdarzenie dostępne kilkoma drogami liczymy raz. Nie sumujemy wszystkich źródeł ani nie wybieramy większej sumy jako „lepszej”.
- Polityka początkowa: jedna zweryfikowana rodzina modeli Google/Fitbit dla danych opaski. API jest kandydatem na dostawcę oficjalnego wyniku; Takeout lub Connect mogą go zastąpić tylko po potwierdzeniu zgodności definicji, pochodzenia, doby, deduplikacji i jakości. Sama etykieta `Google Health App` nie dowodzi użycia opaski.
- Nie przełączamy automatycznie kolumny na model telefonu, gdy brak opaski. Zachowujemy oryginały do diagnostyki, a w arkuszu brak i krótka uwaga. Kroki/dystans mogą nadal mieć własne poprawne dane telefonu; ich obecność nie dowodzi poprawności kalorii.
- Nie odtwarzamy aktywnych kalorii jako `total - arbitralny BMR`, z kroków/dystansu ani z sum samych treningów. Zależność total = basal + active można sprawdzać wyłącznie dla tego samego modelu, zakresu i wersji źródła, z udokumentowanymi zasadami producenta.
- Sama zmienność, dodatnie aktywne kcal, stałość wyniku, liczba rekordów, obecność tętna czy długość zapisanych przedziałów nie dowodzą kompletności dnia. Tętno może pomagać w analizie, lecz nie staje się samodzielnym miernikiem noszenia.
- Wymagamy informacji o rzeczywiście obserwowanym zakresie i sposobie reprezentacji bezruchu/braków. Brak próbki nie jest zerem. Nie wymagamy 1440 dodatnich/minutowych rekordów aktywnych kalorii: źródła mogą pomijać bezruch. Jeżeli protokół nie pozwala odróżnić luki od braku aktywności, wynik jest niepewny i pozostaje pusty.
- Wykluczenie okresów nienoszenia przez API nie dowodzi kompletnej doby; suma pozostałych minut może nadal być częściowa. Modelowanie spoczynku jest dopuszczalne tylko jako jawny składnik zatwierdzonego modelu, nie jako zamiennik nieznanej aktywności.
- Każdą kolumnę oceniamy osobno. Poprawne total nie pozwala zgadnąć active i odwrotnie. Brak jednej nie usuwa poprawnej drugiej; uwaga wskazuje, którego pola dotyczy ograniczenie.
- Stany wyniku obejmują co najmniej: poprawny, potwierdzone zero, tylko BMR, częściowy, nieporównywalne źródło, konflikt, brak danych, brak dostępu, błąd, dzień w toku. Tylko pierwsze dwa mogą zasilać liczby, z dodatkowym zakazem samego BMR dla total.

## Co wykazała analiza i czego jeszcze nie potwierdzono

Bez prywatnych wartości/ID rekordów; szczegóły uruchomienia w [test-results.md](../test-results.md).

- Connect potrafi zwrócić sumę total przy braku rekordów total i active oraz uzupełnić luki istniejących rekordów. Obecny `HealthHistorySource` eksportuje agregat SDK bez takiej kwalifikacji.
- Cała odczytana seria `ActiveCaloriesBurnedRecord` w 2026 pochodzi z natywnego śledzenia Connect na telefonie i zaczyna się **2026-10-08**; metadane wskazują telefon Pixel 7a, nie opaskę. Producent opisuje te kalorie jako szacunek z kroków, uruchamiany po nadaniu aplikacji odczytu typu: [native tracking](https://developer.android.com/health-and-fitness/health-connect/features/native-tracking). Nie potwierdzono wzoru ani użycia wagi/tętna przez ten model. Zainstalowany Google Health deklaruje odczyt active i zapis total, ale nie deklaruje zapisu active do Connect.
- Powtórny odczyt tych samych rekordów total wykazał zależność wyniku od pozycji dnia w zakresie zapytania: agregacja lokalna pierwszego dnia porcji różni się od sumy rekordów i agregacji `Instant` dla warszawskiej doby; ten sam dzień wewnątrz większej porcji daje inny wynik. Sprawdzone rekordy Fitbit mają poprawne offsety Warsaw. To odrębny problem ścieżki odczytu, nie tylko braków/noszenia, zaokrąglenia czy starego cache. Nie ustalono jeszcze wewnętrznej przyczyny w SDK/systemie.
- W wskazanym eksporcie `Active Energy Burned` seria `Google Health App` występuje od **2026-05-07 do 2026-10-29**, również przed początkiem obecnej kolumny. Osobna `Phone Health Connect` zaczyna się 2026-10-08. Współistniejące serie różnią się; nie wolno ich sklejać w miejscu tej daty ani sumować.
- Obecność wcześniejszej serii daje **kandydatów do uzupełnienia**, nie potwierdzenie porównywalności każdego dnia. README CSV nie opisuje granic interwałów, kompletności ani całej deduplikacji. Nie potwierdzono zgodności z rzeczywistą odpowiedzią Health API; nie wykonano OAuth/odczytu konta przez API.
- W tym eksporcie nie ma wcześniejszych aktywnych kalorii przed 2026-05-07. Braków nie odtwarzać przez odejmowanie bazowego szacunku od total. Inne udostępnione źródło można ocenić według tego samego kontraktu.
- Obecny importer aktywności pomija dzień przy dowolnym istniejącym rekordzie typu; nie naprawia częściowej doby. Szacunki z `TakeoutActivityDayAccumulator` i rekord dobowy nie stają się przez import dowodem kompletności ani oryginalnym przebiegiem godzinowym.

## Etapy do realizacji

1. [ ] Zbudować katalog semantyki i kwalifikacji obu pól: źródło pierwotne, transport, model/wersja, urządzenie/metoda zapisu, czas/offset, kompletność, dane o noszeniu, korekty i repliki. Rozdzielić nieznane pochodzenie od znanego telefonu/opaski. Ustalić na małej próbce konkretną, testowalną regułę kwalifikacji producenta; brak dowodu pozostawia pole puste, bez arbitralnego progu kroków/tętna/liczby minut.
2. [ ] Najpierw usunąć zależność wyniku total od granic porcji zapytania: porównać identyczny dzień osobno, jako pierwszy i środkowy dzień, w porcji miesięcznej oraz w ostatnich 3 dniach. Zweryfikować dobowe `aggregate` z granicami `Instant` Warsaw lub inną poprawną alternatywę; `aggregateGroupByPeriod` wymaga czasu lokalnego, więc nie podmieniać w nim typu filtra na ślepo. Dopiero potem odczytać ponownie 2026 z Connect i wskazanego Takeout, z paginacją i poprawną strefą. Zachować audyt źródeł i przyczyn odrzucenia prywatnie. Porównać okresy: Fit, bazowe wpisy, częściowa opaska, brak opaski, powrót zapisów i energia telefonu od października. Nie wymagać instalowania Fit.
3. [ ] Po zleceniu integracji API wykonać minimalny odczyt porównawczy v4: active i total, rodziny źródeł (w szczególności `google-wearables`), oryginalne/reconciled/rollup, granice doby i noszenie. Nie zakładać zgodności CSV z gotowym rollupem. Zweryfikować sprzeczne opisy BMR/basal w dokumentacji przed użyciem tej funkcji.
4. [ ] Zaplanować wspólny wynik dnia z jakością i powodami oraz kolumnę „Uwagi”, niezależnie od docelowego umiejscowienia bazy. Nie przepuszczać niekwalifikowanego agregatu SDK. Nie zmieniać znaczenia starego wyniku przez samą zmianę etykiety.
5. [ ] Przygotować podgląd **całej historii 2026 obu kolumn**, nie tylko uzupełnianie pustych active przed październikiem: zachowaj/przelicz/wyczyść wraz z powodem. Także obecne active telefonu wymagają zastąpienia wynikiem porównywalnym lub wyczyszczenia. Nie obiecywać zapełnienia wszystkich dat.
6. [ ] Zrealizować jawną przebudowę tymczasowych danych: nowe wartości, puste niekwalifikowane komórki i uwagi, albo uzgodnione odtworzenie arkusza. Użytkownik zezwolił na zastąpienie obecnych danych; zachować inne pliki/zakładki spoza zakresu. Nowa generacja wyników, checkpointów i kolejki musi uniemożliwić ponowne wysłanie starego cache po korekcie.
7. [ ] Zapisywać również jawne wyczyszczenia i wiersze zawierające wyłącznie uwagi; null nowej oceny nie może zachować starej mylącej liczby. Rozróżniać świadome wycofanie wyniku w migracji/kwalifikacji od błędu/odmowy/pustego niepełnego odczytu. Nie usuwać poprawnej historii tylko dlatego, że źródło chwilowo nie odpowiada; oznaczyć jej nieaktualność.
8. [ ] Przeliczenia historii i bieżące uruchomienia muszą korzystać z tej samej wersji kontraktu, kwalifikacji i reguł pochodzenia. Obsłużyć korekty starsze niż dotychczasowe ostatnie 3 dni; późniejszy import Takeout/API unieważnia właściwe wyniki, nie całą historię.
9. [ ] **TODO — poprzednie lata:** po 2026 sprawdzić ich dostępność, pochodzenie, kompletność i zgodność z tym samym kontraktem. Najpierw raport zakresu i różnic, bez automatycznego rozszerzania migracji ani wymuszania dzisiejszego modelu na nieznanych dawnych danych.

## Connect po korekcie

- [ ] Oddzielić naprawę arkusza od naprawy Connect. Wynik zastępczy bez rekordu nie daje się usunąć; usuwanie samych cudzych rekordów nie wyłącza bazowego wyliczania SDK.
- [ ] Najpierw prywatna lista dokładnych dni/typów/źródeł i podgląd zmian. Bez zbiorczego kasowania całej historii aktywności lub prawidłowych kroków, snu itp. Zezwolenie na wymianę arkusza nie oznacza zezwolenia na kasowanie pomiarów Connect/chmury.
- [ ] Health Sync może programistycznie usuwać własne rekordy; wpisy Fit/Fitbit wymagają operacji użytkownika w systemowym interfejsie (ewentualnie autoryzowanej automatyzacji). Zbiorcze zakresy sprawdzić w wersji urządzenia, nie zakładać dowolnego filtra dat i źródła.
- [ ] Uzupełniać Connect tylko wtedy, gdy znaczenie, przedziały i pochodzenie można wiernie odwzorować; nie udawać oryginalnego Fitbit i nie rozciągać niepewnej części doby na pełną. Kopie/API/Takeout/Connect identyfikować jako repliki tego samego zdarzenia. Ograniczenia ogólne: [data-sources.md](data-sources.md#synchronizacja-z-connect-i-api).

## Testy i kryteria ukończenia

- [ ] Syntetyczne testy: BMR z zapisami i bez zapisów; zmienny niepełny total; częściowe noszenie; dzień bez opaski z aktywnością telefonu; nieznane źródło; potwierdzony bezruch/zero; opóźniona synchronizacja; duplikaty różnych transportów; konflikt źródeł; odmowa historii/błąd; dzień w toku; granice/DST/podróż.
- [ ] Nie mieszać modeli active po obu stronach daty migracji. Sprawdzić API/CSV z tym samym zakresem i definicją bez zakładania, że brak active oznacza zero. Różnica total-active nie staje się pomiarem BMR.
- [ ] Niezależność wyniku dnia od początku/końca i długości porcji: odczyt pojedynczy, 3-dniowy, miesięczny, także po DST. Porównać raw/clip/Instant/local/period z offsetami i źródłami; osobno zbadać podejrzany ostatni kwadrans Fitbit. Na źródłach z nakładaniem zachować deduplikację zamiast zastępować SDK prostą sumą.
- [ ] Testy Room/kolejki/Sheets: wartości i uwagi z jednej rewizji, świadome wyczyszczenie liczby, wiersz samych uwag, usunięcie rozwiązanego problemu, powtórzenie bez dopisywania uwag, wznowienie/timeout, stary cache i równoległy worker nie cofają migracji.
- [ ] Próba małego zakresu na telefonie i w arkuszu, potem 2026; podać potwierdzone definicje i pozostawione braki. Test automatyczny nie dowodzi kompletności prywatnych źródeł. Nie przywracać odłożonych ręcznych testów wycofywania Takeout.

Gotowe, gdy historia i bieżące dni mają tę samą interpretację, BMR-only i nieporównywalne szacunki nie tworzą liczb, braki są jawne, a aktualizacja uwag/komórek jest powtarzalna. Pełne wypełnienie kalendarza nie jest kryterium sukcesu.

## Sprawdzone źródła producenta

Przegląd **2026-11-02** obejmuje definicje i publiczne schematy, nie odpowiedzi prywatnego konta API:

- [Google Health: total/active/BMR](https://developers.google.com/health/data-types/calories), [różnica względem dawnych Fitbit activityCalories](https://developers.google.com/health/migration/api-specifications).
- [API: agregacja, rodziny źródeł, noszenie i doby](https://developers.google.com/health/aggregate-data), [filtry](https://developers.google.com/health/filters).
- [Connect: agregacja i priorytety](https://developer.android.com/health-and-fitness/health-connect/aggregate-data), [kod wyliczania total w lukach](https://android.googlesource.com/platform/packages/modules/HealthFitness/+/refs/heads/main/service/java/com/android/server/healthconnect/storage/datatypehelpers/DeriveTotalCaloriesBurnedHelper.java).
- [API Connect: zakres usuwania własnych rekordów](https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient), [usuwanie w interfejsie](https://support.google.com/android/answer/12201232?hl=en).
