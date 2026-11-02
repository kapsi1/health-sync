# Źródła danych i własna baza — analiza decyzji

**2026-11-02: rekomendacja do decyzji, nie wdrożona architektura.** Zlecono analizę i plan, nie budowę wspólnej bazy, nowy OAuth ani zapisy do Connect/API. [Indeks](README.md) · [Kalorie](calories.md) · [Obecna architektura](../architecture.md).

## Co ma być źródłem prawdy

Rekomendacja: **własna baza jest źródłem wyników i decyzji naszej aplikacji; Connect, API, Takeout i przyszłe źródła dostarczają oryginalne obserwacje**. Sheets i dashboard pokazują tę samą wersjonowaną projekcję. Nie oznacza to, że własna baza zna fizjologiczną prawdę lub powinna zastąpić algorytmy producenta.

Nie ustanawiać globalnego „API zawsze wygrywa” ani „Connect zawsze wygrywa”. Wybrać właściciela/definicję każdej metryki i rozróżnić oryginalny pomiar, podsumowanie producenta, własną pochodną oraz kopię transportową. Priorytet ma znaczenie dopiero między semantycznie równoważnymi i zakwalifikowanymi kandydatami. Brak kandydata nie pozwala podstawić podobnie nazwanego pola.

Dotychczasowe Connect→Room→Sheets pozostaje opisem działającego kodu. Reguła „Connect jako jedyne źródło standardowych pól, API tylko wzbogaca” nie jest już założeniem obowiązującym przyszłe projektowanie; użytkownik zlecił jej ponowną ocenę. Wybór poniższego wariantu bazy i umiejscowienie zapisu wymagają decyzji przed implementacją.

## Porównanie wariantów

| Kryterium | Connect nadrzędny | Google Health API nadrzędne | Własna baza z regułami per metryka |
| --- | --- | --- | --- |
| Dostęp i offline | Lokalnie na Androidzie; komputer wymaga pośrednictwa telefonu | Odczyt z telefonu/komputera; OAuth, sieć i synchronizacja urządzenia z chmurą | Lokalny odczyt offline; trzeba dostarczyć adaptery i transfer telefon↔komputer |
| Źródła | Rekordy lokalnych aplikacji i urządzeń, jeśli zostały zapisane i udostępnione | Dane dostępne na koncie Health; nie pełna kopia Connect | Zachowanie obu stron i danych własnych; większa odpowiedzialność za identyfikację i jakość |
| Podsumowania Google | Część niedostępna lub bez jawnej semantyki podsumowania | Oryginalne nocne/dzienne podsumowania i modele producenta | Można preferować podsumowanie API, zachowując osobno własne obliczenie |
| Agregacja | Priorytety aplikacji użytkownika; total może uzupełniać luki | Reconciliation, rodziny źródeł, filtry noszenia, własne reguły doby | Jawny kontrakt, pochodzenie, jakość i wersja wyniku; nie kopiować bez potrzeby algorytmów Google |
| Edycje/usuwanie | Aplikacja zarządza własnymi rekordami; cudze kontroluje użytkownik/system | Operacje zależą od typu i własności danych; nie wszystkie pola są zapisywalne | Korekty użytkownika można zachować jako osobną warstwę; publikacja do dostawcy jest oddzielnym skutkiem |
| Historia i awarie | Zgody historii, lokalna dostępność i luki synchronizacji | Zależność od konta, zakresów, limitów i przetwarzania producenta | Zachowane oryginały, importy i korekty pozwalają przeliczać historię; potrzebne kopie i migracje |
| Koszt | Najmniejszy przy obecnym kodzie, lecz ogranicza desktop i metryki chmurowe | Prostszy model dla Google Health, lecz nie obejmuje wszystkich typów/źródeł | Największy koszt implementacji; najlepsze dopasowanie do wielu źródeł i dashboardu z zapisem |

**API przed Connect** jest sensowne dla zdefiniowanego podsumowania Google, którego Connect nie ma lub reprezentuje inaczej. **Connect przed API** jest sensowne dla oryginalnych lokalnych pomiarów innych aplikacji, własnych wpisów i typów nieobecnych w katalogu Health. Bezpośrednio porównywalne kopie nie stają się nowymi pomiarami.

## Semantyka: podobna nazwa nie oznacza tego samego

Przegląd publicznych dokumentów Google **2026-11-02**, dla v4; beta jawnie oznaczona. To katalog istotny dla aplikacji i planowanego dashboardu, nie obietnica importu wszystkich typów. Obecne obliczenia aplikacji opisują [kontrakty](../data-contracts.md). Poniższe rozróżnienia nie zmieniają ich automatycznie.

### Energia, aktywność i czas

| Dane | Connect / obecna aplikacja | Google Health API / różnica |
| --- | --- | --- |
| Całkowita energia | `TotalCaloriesBurnedRecord`; SDK może uzupełnić lukę lub wyliczyć wynik bez takich rekordów | `total-calories` jest pochodną active i bazowego metabolizmu, dostępna jako rollup. Wynik po filtrze noszenia nie musi opisywać pełnej doby. Kontrakt jakości: [kalorie](calories.md). |
| Aktywna energia | `ActiveCaloriesBurnedRecord` bez BMR; obecne źródło na telefonie nie jest opaską | `active-energy-burned` również wyklucza bazową energię, ale ten sam typ nie dowodzi tego samego modelu wejściowego. Dawne Fitbit `activityCalories` obejmowały także BMR w okresach aktywności — nie mapować wprost. |
| BMR / basal | `BasalMetabolicRateRecord` jest tempem wydatku (moc); `BASAL_CALORIES_TOTAL` to energia za przedział | `kcal/day` jest tempem, `kcal` energią. Dokumentacja API jest niespójna co do dostępności basal/BMR; nie opierać wdrożenia na samej nazwie pola w schemacie. |
| Kroki/dystans | SDK rozstrzyga nakładanie według priorytetów aplikacji | API oferuje surowe/reconciled/rollup oraz rodziny źródeł. Nie sumować replik z Connect. API distance podaje milimetry, Connect jednostkę długości; konwersja do km to osobny etap. |
| Czas ćwiczeń | `EXERCISE_DURATION_TOTAL` / sesje; nie jest miarą intensywności | `exercise` ma sesję, zdarzenia pauz i podsumowania. Czas ścienny, aktywny, w ruchu oraz czas w strefach wymagają osobnych pól i reguł. |
| Minuty aktywne / strefy | `ActivityIntensityRecord` i jego agregaty są odrębne od sesji treningu | `active-minutes`, `active-zone-minutes`, `time-in-heart-rate-zone` i `activity-level` nie są zamienne. Active Zone Minutes mogą naliczać 2 jednostki za minutę wyższej intensywności; nie przedstawiać jako fizycznego czasu treningu. |
| Przewyższenie | `ElevationGainedRecord`, nie wysokość bezwzględna GPS | API `altitude` oznacza przyrost wysokości w mm, mimo nazwy sugerującej wysokość n.p.m.; nie utożsamiać z GPS altitude. |
| VO₂ max | `Vo2MaxRecord` z metodą pomiaru | API rozróżnia `vo2-max`, `daily-vo2-max`, `run-vo2-max`; podsumowanie/oszacowanie z biegu nie staje się nowym niezależnym testem fizjologicznym. |

Źródła: [energia](https://developers.google.com/health/data-types/calories), [migracja dawnych kalorii Fitbit](https://developers.google.com/health/migration/api-specifications), [treningi](https://developers.google.com/health/data-types/workouts), [typy i jednostki v4](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints), [Connect: agregacja](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).

### Sen i pomiary fizjologiczne

| Dane | Connect / obecna aplikacja | Google Health API / różnica |
| --- | --- | --- |
| Sen i wybudzenia | Sesja i fazy; aplikacja sumuje rzeczywisty sen i czuwanie wewnątrz snu, wszystkie prawidłowe sesje według dnia końca | API ma podsumowanie, `mainSleep`, `processed`, krótkie wybudzenia i odcinki out-of-bed mogące nakładać się na fazy. Czas w łóżku, okres snu, faktyczny sen, główna noc i suma dobowa to odrębne wielkości. Nie dodawać nakładających się krótkich wybudzeń drugi raz. |
| Zasypianie / efektywność | Nie wyliczamy czasu intencji zaśnięcia z braku rekordów | API `minutesToFallAsleep=0` przy automatycznym śnie może być domyślne; nie dowodzi natychmiastowego zaśnięcia. Efektywność jest wyliczana na innym etapie niż końcowy podział faz, więc nie musi równać się ilorazowi odtworzonemu z końcowych minut. |
| HRV | `HeartRateVariabilityRmssdRecord`: RMSSD; aplikacja preferuje rozpoznane podsumowanie lub własną średnią próbek przy śnie | API ma próbki RMSSD/SDNN oraz `daily-heart-rate-variability`, w tym osobne miary deep-sleep/non-REM. SDNN ≠ RMSSD; nocne podsumowanie producenta ≠ dowolna średnia próbek ≠ HRV tylko w głębokim śnie. |
| Tętno spoczynkowe | `RestingHeartRateRecord`; obecnie średnia rekordów dobowych SDK | API `daily-resting-heart-rate` to wynik modelu z informacją o metodzie (ze snem lub tylko danymi czuwania). Nie jest średnim tętnem całej doby ani minimum tętna. |
| SpO₂ | `OxygenSaturationRecord` jest chwilowym pomiarem; aplikacja liczy średnią próbek dnia | API `daily-oxygen-saturation` opisuje nocne podsumowanie producenta, obok próbek `oxygen-saturation`. Dolna/górna granica podsumowania to granice przedziału ufności, nie minimum/maksimum. Brak zapisów w Connect nie oznacza braku podsumowania w chmurze. |
| Oddechy | `RespiratoryRateRecord` i własny wybór próbek/faz snu | API ma podsumowania dobowe i `respiratory-rate-sleep-summary` z rozróżnieniem faz/całego snu. W Takeout niektóre pola mają **milli-breaths/min**, wymagają przeliczenia /1000. Nie zamieniać nocnego modelu w dobową średnią ani wymyślony chwilowy pomiar. |
| Temperatura skóry | `SkinTemperatureRecord`: bazowa temperatura i delty; aplikacja eksportuje deltę | API `daily-sleep-temperature-derivations`: nocny wynik i osobista baza; delta = nightly − baseline, nie absolutna temperatura ciała. Wynik producenta nie jest dowolną średnią surowych czujników. `skin-temperature-sensors` to osobne **v4beta**, zależne od sprzętu. |
| Waga / tłuszcz | Konkretne ważenia i %; obecnie średnie dobowe w arkuszu | API również ma pomiary, m.in. wagę w gramach. Ostatni pomiar, średnia ważona, arytmetyczna, osobista baza i dzienny model to różne definicje; transport nie może zmienić uzgodnionej średniej. |

Źródła: [API sen, domyślne zasypianie i efektywność](https://developers.google.com/health/data-types/sleep), [vitals i podsumowania](https://developers.google.com/health/data-types/vitals), [temperatura: delta, model i beta](https://developers.google.com/health/data-types/skin-temperature), [schematy v4](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints), [Connect: katalog rekordów](https://developer.android.com/health-and-fitness/health-connect/data-types). Schematy Takeout sprawdzono w README wskazanego eksportu, nie zakładając identyczności z API.

### Typy bez bezpośredniego odpowiednika

- API oferuje m.in. gotowe nocne podsumowania, ECG, powiadomienia o nieregularnym rytmie, katalog żywności i dane dziennika żywieniowego. Standardowe rekordy Connect nie odwzorowują całej tej semantyki; `NutritionRecord` nie jest katalogiem produktów/receptur ani planem posiłków. Dostęp do ECG/pozostałych nowych pól nie jest objęty niniejszym wdrożeniem.
- Connect ma m.in. rekordy ciśnienia, masy kostnej, wody w organizmie, mocy, kadencji rowerowej, pchnięć wózka i planowanych treningów. W sprawdzonym katalogu Health API v4 brak odpowiadających im samodzielnych typów o tej samej semantyce; podobna informacja wewnątrz sesji nie oznacza pełnego odpowiednika serii. API nie może zatem być jedynym magazynem wszystkich przyszłych danych.
- Sleep score, jego składowe i Sleep Profile pozostają wyłączone zgodnie z wcześniejszą decyzją. Obecność w Takeout/UI lub sama nazwa „score” nie jest obietnicą stabilnego API. „Sleep efficiency score” nie jest ogólną oceną snu.

Porównanie dotyczy [katalogu API](https://developers.google.com/health/data-types) i [katalogu Connect](https://developer.android.com/health-and-fitness/health-connect/data-types), nie wszystkich przyszłych wersji i nie faktycznych danych konta. Dostępność typu ≠ dostępność na urządzeniu ≠ zapis przez Google Health ≠ zgoda użytkownika.

## Agregacja i Takeout

- API `list`, `reconcile` i rollup nie są tym samym widokiem. Rodziny `all-sources`, `google-sources`, `google-wearables` zmieniają wejście; `google-sources` może uwzględniać także dane pochodzące z Connect, więc nie jest synonimem „tylko opaska”. Źródło transportowe i pierwotne trzeba przechować oddzielnie. [Filtry API](https://developers.google.com/health/filters).
- API `rollUp` grupuje czas fizyczny UTC, `dailyRollUp` czas cywilny z offsetów danych. Obecna stała doba Warsaw nie musi odpowiadać dniowi producenta podczas podróży. Nie przeklejać daty podsumowania ani nie traktować każdego dnia jako 86400 sekund. [Reguły agregacji](https://developers.google.com/health/aggregate-data).
- SDK Connect stosuje priorytety aktywności/snu użytkownika. Pozostałe agregaty i własne średnie aplikacji nie otrzymują automatycznie tej samej deduplikacji; duplikaty wpływają na próbki. Deduplikacja rekordów nie usuwa różnicy modeli ani nie dowodzi kompletności.
- Takeout i API dotyczą tego samego konta, lecz brak potwierdzonej gwarancji zgodności reprezentacji 1:1. CSV może zachować kilka źródeł bez finalnego reconciliation/metadanych; API może zwrócić gotową pochodną. Eksport jest migawką. Nie zakładać dodatkowych pomiarów w API ani równości sumy CSV z oficjalnym rollupem; porównać dokładnie ten sam okres, definicję i rodzinę źródeł.

## Niespójności dokumentacji do rozstrzygnięcia przed implementacją

- [Przewodnik kalorii](https://developers.google.com/health/data-types/calories) opisuje `basal-metabolic-rate` i mówi o braku basal-energy w API; [schemat v4](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints) zawiera `BasalEnergyBurned`, a [główny katalog](https://developers.google.com/health/data-types) nie wymienia basal/BMR jako dostępnego typu. Sama obecność komunikatu w schemacie nie potwierdza działającego endpointu. Wymagana próba dozwolonego odczytu i sprawdzenie możliwości, bez obietnicy wyliczenia active przez odejmowanie.
- Rozróżnić v4/v4beta, opis typu, operacje dopuszczone dla typu i faktyczne odpowiedzi. Szeroki zakres OAuth `writeonly` nie dowodzi, że wszystkie fizjologiczne pochodne danego zakresu można zapisać/edytować.

## Minimalna własna baza — plan do decyzji

- [ ] Zachować oryginalne obserwacje lub bezstratną reprezentację potrzebną do ponownego przeliczenia: źródło pierwotne/transport, konto, ID/wersja, czas/offset, semantyka/jednostka, status przetwarzania i pochodzenie importu. Obecne `DailyObservation(date, metricId, value, ...)` nie jest takim magazynem; Room i dziennik Takeout nie wymagają od razu wymiany, ale same nie wystarczają.
- [ ] Przed przejściem na API jako źródło domyślne zachować jednorazowo historię istniejącą tylko w Connect. Porównanie wskazanego Takeout z Connect wykazało takie rekordy, mimo ogólnie bogatszego eksportu Google Health — [dowody i ograniczenia](../test-results.md). Nie utożsamiać rezygnacji z naszego adaptera z wyłączeniem połączenia Health↔Connect: część eksportu pochodzi już z kopii Connect.
- [ ] Przechowywać osobno wynik kanoniczny, wersję reguł, wejścia, kompletność i przyczyny decyzji. Korekta użytkownika to jawna warstwa, nie nadpisanie oryginału. Jedno miejsce oblicza wynik dla Sheets i dashboardu; nie budować dwóch konkurencyjnych agregatorów na telefonie i komputerze.
- [ ] Wybrać umiejscowienie autorytatywnego zapisu: telefon z repliką desktop, prywatna baza desktop z adapterem Android albo wersjonowane repliki. Rozstrzygnąć offline, konflikty, transport i kopie. Nie dodawać publicznego backendu bez osobnej decyzji; własna baza nie wymaga publicznej chmury.
- [ ] Każda metryka dostaje definicję, źródło preferowane, równoważne źródła zastępcze i warunki odmowy. Podsumowania Google mogą pochodzić z API, lokalne pomiary z Connect, historia ze zweryfikowanego Takeout. Brak równoważności oznacza osobne pole lub brak, nie zamianę po cichu.
- [ ] Zmiany/usunięcia: trwałe identyfikatory/repliki, korekty i znaczniki usunięcia, potwierdzenie od dostawcy i powtórny odczyt. Błąd/odmowa/niepełna strona/puste okno nie usuwa źródłowej historii ani nie staje się zerem.
- [ ] Zacząć od kalorii i wspólnych uwag; potem osobno zaakceptowane nocne SpO₂ i inne metryki. Nie uzależniać poprawy jakości arkusza od ukończenia całego dashboardu, katalogu żywności lub wszystkich adapterów.

## Synchronizacja z Connect i API

- [ ] Traktować synchronizację jako **kierunkowe, jawne mapowanie typu**, a nie pełną kopię baz. Dla każdego kierunku ustalić właściciela oryginału, dopuszczone operacje, aktualizacje/usunięcia, ID i warunek równoważności.
- [ ] Zapisywać do Connect tylko metryki wiernie reprezentowalne. Nocne podsumowanie SpO₂ nie staje się chwilową saturacją o północy; nie dopisywać wymyślonych próbek, faz, rozkładu godzinowego ani flag urządzenia. Gdy brak wiernej reprezentacji, podsumowanie pozostaje we własnej bazie/arkuszu z właściwą nazwą.
- [ ] Health Sync zapisuje pod własnym `dataOrigin`. Nie podszywać się pod Fitbit. Zachować istniejące zatwierdzone decyzje dawnych importów (np. v2 snu i zamrożony plan wznowienia); porównanie źródeł nie uruchamia ich automatycznej migracji.
- [ ] Oznaczać własne repliki: dane Connect→Google Health→API→aplikacja nie mogą wracać jako kolejny pomiar. Nie wystarczy polegać na bieżącym priorytecie Connect lub podobnej wartości. Późniejszy oryginał zastępuje własną kopię dopiero po sprawdzeniu tożsamości i treści.
- [ ] Edycja w dashboardzie najpierw tworzy trwałą operację z wynikiem oczekującym/potwierdzonym/odrzuconym. Nie deklarować zapisu w chmurze przy samym lokalnym sukcesie. Typ tylko do odczytu zachowuje lokalną korektę z jawnym statusem; nie wymuszać edycji cudzych rekordów.
- [ ] Brak technicznej możliwości identycznego wyniku w natywnym UI Connect (np. własne bazowe total SDK) nie uzasadnia zapisu zera lub fikcyjnych rekordów. Docelowo zgodne są wierne rekordy i nasze projekcje; nie obiecujemy identycznych liczb w każdym interfejsie producenta.

## Decyzje przed implementacją i próba zgodności

- [ ] Wybrać wariant bazy/transportu i pierwsze metryki; przygotować niewielki, konkretny projekt migracji zamiast ogólnego systemu dla wszystkich typów naraz.
- [ ] Dla pól z różną definicją wybrać jawne nazwy: np. nocne SpO₂ Google versus średnia chwilowych próbek dnia. Nie przejmować dotychczasowej kolumny samą zmianą źródła. Wyjaśnić czy utrzymujemy obie miary czy przebudowujemy jedną według nowego kontraktu.
- [ ] Sprawdzić próbkę API/Takeout/Connect: to samo zdarzenie trzema drogami, dzień bez opaski, częściowy dzień, noc z drzemką, dzień podróży/DST, opóźniona korekta, usunięcie i brak zgody. Źródła publiczne potwierdzają semantykę, nie dostępność prywatnej historii.
- [ ] W próbie API zweryfikować nie tylko istnienie typu, lecz także wypełnienie pól: w zbadanym eksporcie nowe `summary_*` treningów są puste, choć starsze pola opaski i odrębne serie intensywności występują. Nie zakładać gotowego podsumowania stref dla każdego treningu. Porównywać wyłącznie wspólny zakres zakończonych dni, bez uznawania świeższych danych Connect za braki starszej migawki Takeout.
- [ ] Zachować prywatność: brak danych zdrowotnych/tokenów/tras w repo, fixture'ach i logach; syntetyczne testy; kopie i odtwarzanie prywatnej bazy przed migracjami. Nie kasować Connect/API na podstawie zgody dotyczącej tymczasowego arkusza.
