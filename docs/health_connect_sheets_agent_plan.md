# Health Connect → Google Sheets Sync

**Plan implementacji dla agenta programistycznego**  
**Wersja:** 1.0 · 17.10.2026  
**Platforma docelowa:** Android, w pierwszej kolejności Google Pixel 7a z aktualnym Androidem  
**Język interfejsu:** polski  
**Cel:** prywatna aplikacja APK, bez własnego serwera, synchronizująca wybrane dane zdrowotne z Health Connect do arkusza Google Sheets.

## 0. Instrukcja dla agenta

Zaimplementuj **działającą aplikację**, nie jedynie makiety ani pseudokod. Realizuj kolejne zadania w podanej kolejności, uruchamiaj testy i dokumentuj wyniki. Jeśli środowisko nie pozwala na faktyczne zalogowanie do Google lub użycie prawdziwego Health Connect, zastosuj interfejsy i testowe implementacje repozytoriów; nie przedstawiaj integracji jako przetestowanej na urządzeniu. Nie umieszczaj kluczy, tokenów, danych zdrowotnych ani plików z sekretami w repozytorium. Nie wymagaj backendu ani usługi zewnętrznej poza Google APIs.

**Priorytety:** poprawność danych > bezpieczny dostęp > wznowienie synchronizacji > ergonomia > wydajność. Pierwsza wersja ma być realnie używalna.

## 1. Wymagania użytkownika

1. Odczyt danych z **Health Connect** lokalnie na telefonie, w tym danych udostępnianych przez Google Health/Fitbit Charge 6.
2. Wybór statystyk z listy przełączników w aplikacji. Wybór musi dać się później zmienić.
3. Ręczna synchronizacja oraz automatyczne synchronizacje w tle.
4. Zapis do jednego wskazanego / utworzonego arkusza Google Sheets. Jeden dzień to jeden wiersz; ponowne odczyty aktualizują wiersz zamiast tworzyć duplikat.
5. **Pełny import dostępnej historii**, także danych starszych niż 30 dni; opcja „Cała dostępna historia” oraz ograniczenie dat od/do.
6. Możliwość zaznaczenia **wszystkich statystyk obsługiwanych przez aplikację** i sprowadzenia całej ich dostępnej historii.
7. Po zaznaczeniu nowej statystyki aplikacja pobiera także jej starsze dane; po odznaczeniu przestaje ją aktualizować, ale **domyślnie nie usuwa** starych wartości z arkusza.
8. Brak duplikatów, utraty danych przy przerwanym imporcie oraz niejawnego zamieniania braku danych na zero.
9. Możliwość zainstalowania własnego debug/release APK bez publikowania w Google Play.

**Rozgraniczenie pojęć:** „cała historia” oznacza pełny okres danych **rzeczywiście dostępnych przez Health Connect** dla danej kategorii i przyznanych uprawnień. Nie oznacza całej historii przechowywanej wyłącznie w chmurze Fitbit/Google Health. „Wszystkie statystyki” w MVP oznaczają wszystkie pozycje katalogu MVP; w dalszym etapie katalog należy rozszerzyć. Eksport **surowych pomiarów** (np. każdy odczyt tętna) to odrębna funkcja, zaplanowana po MVP.

## 2. Najważniejsze ograniczenia zewnętrznych API

- Health Connect wymaga uprawnienia odczytu **dla każdego typu rekordów**. Przed żądaniem należy je zadeklarować w `AndroidManifest.xml`; żądać tylko tych, które odpowiadają wybranym statystykom.
- Odczyt historii sprzed domyślnego 30-dniowego okna wymaga `HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY` / manifestowego `android.permission.health.READ_HEALTH_DATA_HISTORY`; sprawdź `FEATURE_READ_HEALTH_DATA_HISTORY` przez `getFeatureStatus` i poproś o dodatkową zgodę. W przypadku braku funkcji/uprawnienia pokaż ograniczenie, nie udawaj pełnego eksportu.
- Odczyt w tle wymaga `READ_HEALTH_DATA_IN_BACKGROUND` i dostępnej funkcji `FEATURE_READ_HEALTH_DATA_IN_BACKGROUND`. Jeśli urządzenie jej nie obsługuje lub użytkownik odmówi, nadal ma działać ręczny odczyt na pierwszym planie; tło wyświetla stosowny status.
- Health Connect przechowuje jedynie dane zapisane przez aplikacje/urządzenie, możliwe jest też automatyczne usuwanie starszych danych. **Nie wolno obiecywać odzyskania danych, których w Health Connect nie ma.**
- Google Health **zapisuje** do Health Connect m.in. kroki, całkowite kcal, dystans, ćwiczenia, tętno, tętno spoczynkowe, sen oraz wagę; **nie zapisuje obecnie aktywnych kcal** mimo że potrafi je z Health Connect odczytać. Aktywne kcal mogą być dostępne z innego źródła lub pozostać puste.
- Sumowanie kroków i aktywności pochodzących z wielu źródeł może zawyżać wyniki. Preferuj `aggregate` / `aggregateGroupByPeriod`, które dla części danych honoruje priorytet źródeł ustawiony przez użytkownika. Nie sumuj samodzielnie nakładających się rekordów.
- Synchronizacja okresowa WorkManager (min. 15 min) jest **best effort**: Android może ją przesuwać. Health Connect nie oferuje gwarantowanych natychmiastowych webhooków po zmianie.
- Google Sheets API nie oferuje prostego atomowego `upsert` po dacie. Indeks dat i logika wznowień są odpowiedzialnością aplikacji.
- Dostęp do Sheets ma być przez **OAuth użytkownika**, najlepiej scope `https://www.googleapis.com/auth/drive.file` i arkusz **utworzony przez aplikację**. Nie używaj klucza API ani service account z kluczem prywatnym w APK. Jeśli OAuth wymaga interakcji, worker zatrzymuje próbę i prosi użytkownika o ponowną autoryzację przy otwarciu aplikacji.
- Zbadaj konfigurację ekranu zgody OAuth: dla zewnętrznego projektu w trybie **Testing** refresh tokeny mogą wygasać po 7 dniach; nie buduj automatyzacji zakładającej ich bezterminową ważność.

## 3. Architektura i technologie

- **Kotlin**, Android SDK, **Jetpack Compose** + Material 3, ViewModel, Kotlin Coroutines/Flow.
- `androidx.health.connect:connect-client` (aktualna wersja kompatybilna z urządzeniem; zweryfikuj przed pinowaniem), Health Connect Client.
- **WorkManager** dla okresowego `PeriodicWorkRequest` i jednorazowego importu; constraint `NetworkType.CONNECTED` dla wysyłki do Sheets. Import z Health Connect nie powinien być uzależniony od sieci, jeśli można odłożyć upload.
- **Room** dla dziennych obserwacji, kolejek eksportu, checkpointów, mapowań wierszy; **DataStore** dla ustawień, wybranych metryk, interwału i identyfikatora arkusza.
- **Google Identity Services / AuthorizationClient** dla autoryzacji; Google Sheets REST API v4 przez OkHttp/Retrofit lub oficjalny klient. Żadnych statycznych tokenów w kodzie.
- Warstwy: `ui` → `domain/usecase` → `data/healthconnect`, `data/local`, `data/sheets`, `sync`; użyj interfejsów umożliwiających fake repositories.
- `MetricRegistry` jako pojedyncze źródło prawdy: `id`, polska etykieta, jednostka, rekord HC, wymagane permission, funkcja odczytu/agregacji, strategia agregacji, docelowa kolumna Sheets, status dostępności.
- Jeden koordynator synchronizacji oraz unikalny WorkManager job; zabezpiecz równoległe uruchomienia i konflikty między workerem a akcją ręczną.

Przykładowy przepływ:

```text
Fitbit Charge 6 → Google Health → Health Connect
                                  ↓
                             MetricRegistry
                                  ↓
                       odczyt/agregacja per dzień
                                  ↓
                            Room (cache + queue)
                                  ↓
                  SyncWorker + OAuth + Sheets API
                                  ↓
                  Google Sheets: zakładka `daily`
```

## 4. MVP: zakres funkcji

### 4.1. Wybór statystyk

Katalog MVP (każda pozycja z osobnym checkboxem i statusem):

| `metric_id` | Nazwa w aplikacji | Źródło / agregacja | Kolumna `daily` |
|---|---|---|---|
| `steps` | Kroki | `StepsRecord.COUNT_TOTAL` | `steps` |
| `total_calories` | Spalone kcal łącznie | `TotalCaloriesBurnedRecord.ENERGY_TOTAL` | `total_kcal` |
| `active_calories` | Aktywne kcal | `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, tylko gdy dostępne | `active_kcal` |
| `distance` | Dystans | `DistanceRecord.DISTANCE_TOTAL` | `distance_km` |
| `heart_rate_avg` | Średnie tętno | `HeartRateRecord.BPM_AVG` | `heart_rate_avg_bpm` |
| `resting_heart_rate` | Tętno spoczynkowe | `RestingHeartRateRecord.BPM_AVG` | `resting_hr_bpm` |
| `sleep_duration` | Czas snu | `SleepSessionRecord.SLEEP_DURATION_TOTAL`; jasno opisz datowanie | `sleep_hours` |
| `weight_avg` | Średnia masa ciała | `WeightRecord.WEIGHT_AVG` | `weight_avg_kg` |

Sprawdź nazwy aktualnych stałych w użytej wersji SDK, zanim napiszesz kod. Przeliczaj kcal i km w warstwie domenowej, nie przy odczycie arkusza. W przypadku snu ustal w MVP **jedną zrozumiałą regułę przypisania do daty** i dokumentuj ją; jeśli agregacja SDK nie spełni tej reguły, zastosuj paginowany odczyt sesji i agregację z testami sesji przechodzących przez północ. Nie przesuwaj całego MVP z powodu bardziej zaawansowanej analizy faz snu.

Przycisk **„Zaznacz wszystkie”** wybiera cały katalog dostępnych metryk MVP, a przycisk **„Odznacz wszystkie”** zatrzymuje przyszłe odczyty. Po zmianie wyboru aplikacja powinna poprosić tylko o brakujące uprawnienia. Status metryki: `gotowe`, `brak danych`, `wymaga uprawnień`, `funkcja nieobsługiwana`, `błąd`. Nie utożsamiaj `brak danych` z brakiem pozwolenia.

### 4.2. Ekrany

1. **Start:** status Health Connect, status połączenia Google, nazwa/link arkusza, ostatni odczyt, ostatni poprawny upload, liczba zsynchronizowanych dni, przycisk **Synchronizuj teraz**.
2. **Statystyki:** checkboxy z kategoriami (Aktywność, Serce, Sen, Ciało), jednostkami i statusami, „Zaznacz wszystkie”.
3. **Historia:** `Cała dostępna historia` (domyślnie dla pierwszego importu), `Własny zakres dat`; **Importuj ponownie**, postęp oraz liczniki dni/metryk. „Cała” ma oznaczać od najwcześniejszej dostępnej historii, bez sztucznego odcięcia na 30 dni; implementacyjnie czytaj porcjami od dolnej granicy pozwalanej przez Health Connect / najwcześniejszej daty obsługiwanej przez aplikację i podawaj ją w UI. Możliwość zmiany daty początkowej także na wcześniejszą.
4. **Ustawienia:** częstotliwość (ręcznie, 1 h, 3 h, 12 h, 24 h), wybrany arkusz, strefa czasu (domyślnie `Europe/Warsaw`), tryb działania przy braku danych i diagnostyka.
5. **Przydatny widok pierwszego uruchomienia:** instrukcja włączenia Google Health → Health Connect, bez żądania niepowiązanych zgód.

### 4.3. Pierwsza konfiguracja

1. Sprawdź dostępność HC i flag historii / działania w tle; przejdź do odpowiednich ustawień, jeśli konieczne.
2. Użytkownik wybiera metryki i przyznaje **wyłącznie potrzebne** uprawnienia HC; osobno zatwierdza historię oraz tło.
3. Autoryzuje konto Google do pracy z arkuszem. Aplikacja tworzy nowy arkusz `Health Connect – Daily Sync` przez Sheets API. Zapisz `spreadsheetId` i URL; w MVP **nie trzeba** obsługiwać dowolnych istniejących arkuszy.
4. Utwórz `daily` z nagłówkiem `date`, stabilnymi nazwami kolumn metryk, opcjonalnym `synced_at_utc` i wersją schematu (metadane nie powinny być przypadkowo traktowane jak metryki).
5. Rozpocznij pełny import wybranych metryk, pokaż prawdziwy postęp i umożliwiaj przerwanie oraz wznowienie.

### 4.4. Cała historia: wymagania techniczne

- Odczyt możliwie **wszystkich dostępnych dat** z Health Connect, również sprzed 30 dni, przy wymaganym uprawnieniu i dostępnej funkcji.
- Dziel import na **małe, trwałe porcje** (np. miesiąc; ewentualnie większe okna `aggregateGroupByPeriod` dzielone na 1 dzień). Nie ładuj wielu lat surowych rekordów jednocześnie do RAM.
- Dla dziennych agregacji użyj `aggregateGroupByPeriod` z `Period.ofDays(1)` i **lokalnym `LocalDateTime`**, a nie `Instant` (ważne dla dzielenia na dni kalendarzowe). Jawnie testuj zmianę czasu letni/zimowy w `Europe/Warsaw`.
- Puste okresy nie muszą być zwracane przez API; `null` oznacza brak pomiaru, a **0** stosuj tylko gdy dane wiarygodnie wskazują zero.
- Zapisuj checkpointy per `metric_id` i przedział dat, żeby wznowienie po restarcie nie powtarzało całej pracy; porcję oznacz jako wykonaną **dopiero po bezpiecznym zapisaniu wyników lokalnie**.
- Nowa metryka ma własny import historyczny, niezależny od już pobranych. Cofnięcie uprawnienia nie powinno kasować lokalnych danych i wartości z Sheets.
- Historia po prostu może być nieobecna z powodu wcześniejszego braku synchronizacji, usunięcia pomiarów albo ustawień retencji HC. Pokaż datę najstarszego **faktycznie znalezionego** rekordu i liczbę dni z pomiarami.

### 4.5. Zapis i aktualizacja Google Sheets

- **Klucz logiczny:** `date` w formacie `YYYY-MM-DD`, ze stałą strefą czasu arkusza (`Europe/Warsaw` w MVP).
- Jeden wiersz na dzień; wiersz przechowuje wartości różnych metryk; `null` to pusty element, nie `0` i nie napis `null`.
- Podczas zmiany wyboru metryk dodawaj nowe, **stabilne** kolumny z registry. Nie przemieszczaj kolumn ani nie kasuj historii po odznaczeniu.
- Dla nowego dnia: `spreadsheets.values.append`; dla istniejącego: `values.update` / `values.batchUpdate`. Przed zapisem odczytaj obecne daty/wiersze i buduj indeks; nie polegaj na tym, że ostatnie append na pewno się nie udało po timeout.
- **Idempotencja po awarii:** jeśli upload się udał, lecz aplikacja nie otrzymała odpowiedzi, ponów najpierw odczyt arkusza i aktualizację istniejącego wiersza zamiast ponownego append. Nie wolno produkować podwójnych dat.
- Gdy aktualizujesz niepełny zestaw metryk, **nie czyść innych kolumn**. Wyjątkiem jest jawne ponowne przeliczenie aktywnej metryki, kiedy dane źródłowe usunięto: wtedy można wyczyścić jej wartość.
- Błędy 401/403 wymagające ponownej zgody oznacz jako `wymaga interakcji`; dla 429/5xx retry z exponential backoff, honoruj `Retry-After`; żadnego nieskończonego agresywnego odpytywania.
- Unikaj osobnego zapytania do API na każdą komórkę; grupuj operacje. Nie nadpisuj ręcznie dodanych obcych zakładek użytkownika.

### 4.6. Synchronizacja cykliczna

- WorkManager: domyślnie co **3 godziny** (`ExistingPeriodicWorkPolicy.UPDATE` lub aktualny odpowiednik). Użytkownik może zmienić interwał lub wyłączyć tło.
- Każda synchronizacja przelicza **ostatnie 7–14 dni** dla aktywnych metryk; takie ponowne odczyty wychwytują opóźnione przesyłanie danych z opaski.
- Co pewien czas wykonuj szerszą rekoncyliację (np. ostatnie 90 dni), jeśli jest dostępne wymagane uprawnienie; na MVP dopuszczalne jest uruchamianie tego ręcznie, ale musi istnieć **pełny import powtórny**.
- Jeśli tło lub autoryzacja Google nie działa, zachowaj lokalną kolejkę synchronizacji, pokaż wyraźny status i spróbuj po przywróceniu warunków.
- Nie obiecuj wyzwalania „od razu po pojawieniu się danych” w MVP.

## 5. Trwały model danych

Proponowane tabele Room:

```text
MetricSelection(metricId PK, enabled, granted, backfillRequired)
DailyObservation(date, metricId, valueNullable, unit, observedAtUtc, PK(date, metricId))
ImportCheckpoint(metricId, rangeStart, rangeEnd, state, finishedAtUtc,
                 PK(metricId, rangeStart, rangeEnd))
UploadQueue(date PK, dirty, lastAttemptUtc, errorCode)
SyncRun(id PK, startedAtUtc, finishedAtUtc, status, detail)
```

Dodatkowe ustawienia w DataStore: `spreadsheetId`, `spreadsheetUrl`, `timezoneId`, `syncInterval`, `historyStart`, `selectedMetrics`, wersja schematu arkusza. Nie przechowuj identyfikowalnych, surowych danych zdrowotnych w logach, Analytics ani crash reportach.

Przykładowa tabela Sheets (liczby fikcyjne):

```csv
date,steps,total_kcal,active_kcal,distance_km,heart_rate_avg_bpm,resting_hr_bpm,sleep_hours,weight_avg_kg
2026-10-15,10500,3000,,7.8,78,62,7.1,81.9
2026-10-16,7900,2750,,5.2,74,60,6.5,
```

## 6. Kolejność realizacji MVP (zadania dla agenta)

**M0. Spike weryfikacyjny (obowiązkowy):** zbuduj minimalistyczny ekran sprawdzający status Health Connect, flagę historii, flagę tła, obecność danych kroków/kcal i ich źródła; ręcznie zweryfikuj na Pixelu. Zbadaj skuteczność `AuthorizationClient` w workerze po uprzednim przyznaniu zgody oraz konfigurację Google Cloud OAuth. Wyniki wpisz do `docs/feasibility.md`.

**M1. Szkielet i uprawnienia:** aplikacja Compose, nawigacja, registry metryk, onboarding HC, zgodna z dokumentacją obsługa rationale/privacy policy, wybór metryk i ponowne sprawdzanie przyznanych uprawnień.

**M2. Agregacje i historia:** obsługa 8 metryk MVP (w tym opcjonalnego braku danych), konwersje jednostek, grupowanie per lokalny dzień, `READ_HEALTH_DATA_HISTORY`, import porcjowany, checkpointy i status importu.

**M3. Google i arkusz:** konfiguracja OAuth Android, włączenie Sheets API w projekcie Google Cloud, tworzenie arkusza scope `drive.file`, budowa zakładki i stabilnego schematu.

**M4. Bezpieczny upsert:** zapis Room → Sheets, indeks dat, atomowe z perspektywy aplikacji przekazywanie porcjowanych zmian, wznawianie po niejednoznacznym wyniku sieci, unikanie duplikatów.

**M5. Synchronizacja w tle:** WorkManager, retry, stan połączenia, odmowa uprawnień, brak sieci, ponowna autoryzacja, ręczna akcja sync, zmiana interwału i metryk.

**M6. Testy i wydanie:** testy unit/instrumented, realny test na Pixelu, README konfiguracji Google Cloud, instrukcja instalacji i debug APK + podpisany release APK (bez sekretów i lokalnego keystore w repozytorium).

**M7. Import historii z Google Takeout (wymagany):** osobna ścieżka uzupełniająca luki w Health Connect. Po wdrożeniu trwałego modelu danych i bezpiecznego zapisu do Sheets użytkownik wybiera lokalne archiwum Takeout lub wspierany zestaw plików przez systemowy selektor. Import działa lokalnie, porcjowo, z podglądem zakresów, mapowaniem formatów i licznikami błędów; nie wysyła surowego archiwum ani nie zapisuje go w repozytorium. Formaty i jednostki należy sprawdzić na danych testowych i wersji rzeczywistego eksportu za zgodą użytkownika. Zapisuj pochodzenie i datę importu, zachowaj brak danych jako null i nie sumuj tych samych pomiarów z różnych źródeł. Dane Health Connect mają pierwszeństwo dla danej metryki i dnia, ale dopiero po udanym odczycie: błąd lub brak zgody nie oznaczają braku danych. W przypadku niejednoznacznych lub różnych wartości pokaż konflikt przed zastąpieniem; import ma być wznawialny i idempotentny. Dodaj testy ponownego importu, konfliktów, częściowych archiwów, strefy czasu i błędnych plików. Nie obiecuj obsługi metryk bez ustalenia ich semantyki.

## 7. Definicja ukończenia MVP (Definition of Done)

- [ ] Zainstalowany APK na Pixelu 7a odczytuje rzeczywiste dane z Health Connect.
- [ ] Lista co najmniej 8 metryk z §4.1 pozwala włączać/wyłączać odczyt **bez reinstalacji**.
- [ ] Użytkownik może wybrać „Cała dostępna historia”, przyznać zgodę i otrzymać w arkuszu także rekordy starsze niż 30 dni **o ile są obecne w HC**; jeśli nie ma dostępu, aplikacja to wyjaśnia.
- [ ] Zaznaczenie nowej metryki powoduje pobranie jej historycznych wartości; wyłączenie nie kasuje wcześniejszych kolumn.
- [ ] `date` jest unikalne; podwójny import, retry po timeout i dwie synchronizacje nie dodają duplikatów.
- [ ] Wartości dzień po dniu zgadzają się z agregacją Health Connect dla ustalonego zakresu i strefy (z tolerancją uzasadnioną różnicą czasu aktualizacji).
- [ ] Synchronizacja co 3 godziny działa w granicach polityki Androida, a „Synchronizuj teraz” działa na żądanie.
- [ ] Brak internetu, cofnięcie zgody i restart telefonu nie niszczą importu ani danych.
- [ ] Brak danych ≠ 0. Aktywne kcal bez źródła pozostają puste.
- [ ] Użytkownik może otworzyć własny arkusz z poziomu aplikacji.
- [ ] README, instrukcja OAuth i uprawnień, testy oraz APK są dostarczone.

**Obowiązkowe testy:** przejście przez północ; zmiana CET↔CEST; `null` vs `0`; wieloźródłowe kroki; dwie sesje snu jednej nocy; pomiary starsze niż 30 dni; brak zgody historycznej i jej późniejsze przyznanie; wyłączenie/włączenie nowej metryki; timeout po udanym append; token OAuth nieważny; duży zakres 2–5 lat; brak synchronizacji Fitbit przez kilka dni; brak danych w danej kategorii.

## 8. Etap II: dodatkowe funkcjonalności (kolejność sugerowana)

### P1. Pełniejsza synchronizacja zmian i korekt historycznych

- `getChangesToken` i `getChanges` z osobnym tokenem per typ danych (gdy potrzebne do wykrycia dat zmienionych i usuniętych pomiarów).
- Tokeny zmian mają ograniczony okres ważności (dokumentacja podaje 30 dni); po wygaśnięciu pełne przeliczenie odpowiedniego zakresu.
- Przenośne mapowanie zmienionego/usuniętego rekordu na **dzień źródłowy**; regularna weryfikacja wybranych starszych okresów, nie tylko ostatniego tygodnia.
- Powiadomienia Android o awariach wymagających uwagi, bez ujawniania wrażliwych liczb na ekranie blokady.

### P2. Rozszerzalny katalog danych

- Pozostałe typy HC, m.in. HR min/max, HRV, tlen, oddechy, fazy snu, liczba/piętra, ćwiczenia i czas ćwiczeń, masa/tłuszcz, nawodnienie, żywienie, VO2max, ciśnienie i inne **dopiero po opracowaniu semantyki i pozwoleniach**.
- Dynamiczne grupowanie kategorii i wyszukiwanie, podgląd „ostatni pomiar”, odróżnienie dostępności typu od obecności danych, jednostki i strategie `sum/avg/min/max/latest/count`.
- Zapobieganie dublowaniu źródeł; opcjonalny wybór źródła (Google Health vs inne aplikacje), z opisem wpływu na agregację i priorytety HC.
- Obsługa atrybutów złożonych i rekordów, których nie da się sensownie przedstawić w jednej liczbie dziennej, przez osobne zakładki/formaty.

### P3. Pełny eksport surowych rekordów

- Osobny przycisk **„Eksportuj wszystkie dostępne rekordy”** z wyborem kategorii, okresu i lokalizacji (CSV/JSON/ZIP na telefonie; opcjonalnie Google Drive).
- Paginowany streaming `readRecords`, metadane źródła i znaczniki czasu, stabilne ID do deduplikacji, checkpointy, poprawna obsługa serii (np. wiele pomiarów tętna na minutę).
- Oddzielny format/zakładki od `daily` i jawne ostrzeżenie o rozmiarze oraz wrażliwości danych.
- Import Google Takeout jako oddzielna ścieżka jest wymagany w M7; nie przypisuj jego danych możliwościom Health Connect.

### P4. Dashboard i analiza

- Automatyczne wykresy Sheets dla kroków, kalorii, snu, wagi i tętna; wybór zakresu dat i średnich kroczących 7/30 dni.
- Własny dashboard w Compose, statystyki tygodniowe i miesięczne, porównania aktywności z masą ciała, widok luk synchronizacji.
- Eksport CSV i tworzenie kopii zapasowych ustawień.

### P5. Wygoda i utrzymanie

- Wybór **istniejącego** arkusza przez bezpieczny Picker przy scope `drive.file`, migracje schematu i kilku arkuszy.
- Harmonogram tylko przez Wi-Fi, tylko podczas ładowania, zmienna częstotliwość, przycisk pełnej rekonsyliacji.
- Diagnostyka konfliktów, zmiana strefy czasu z kontrolowaną przebudową dat (bez duplikatów), polityka retencji lokalnych danych.
- Opcjonalny podział na profile i synchronizację z innymi backendami, ale bez komplikowania MVP.

## 9. Ryzyka i decyzje projektowe

| Ryzyko | Zalecane zachowanie |
|---|---|
| Historia nie jest w HC | Jasna informacja i alternatywa Takeout, nie „zero” |
| Historia/tło nieobsługiwane przez urządzenie | Wykrycie przez flagę i fallback, brak fałszywego sukcesu |
| Google Health nie zapisuje aktywnych kcal | Metryka opcjonalna, brak danych oznaczony jako pusty |
| Opóźnienie synchronizacji opaski | Reagregacja ostatnich dni |
| Duplikaty kroków z Pixela i opaski | HC Aggregate API / priorytety źródeł |
| OAuth wymaga ponownej zgody | Worker przerywa i pokazuje `Wymaga zalogowania` |
| Przerwanie importu kilku lat | Checkpointy + kolejka offline |
| Zmiana metryk i schematu | Stabilne ID/kolumny, bez kasowania innych danych |
| Ręczna edycja arkusza przez użytkownika | Aktualizuj wyłącznie własną zakładkę / własne kolumny; dokumentuj konflikt |
| Usunięte źródłowe rekordy | Pełny resync + P1 Changes API; żadnych niezweryfikowanych wartości |

## 10. Artefakty wymagane na zakończenie prac

1. Repozytorium źródłowe z możliwym do zbudowania projektem Android Studio.
2. `README.md`: konfiguracja Kotlin/Android/Google Cloud, OAuth (client ID Android, cert SHA-1/SHA-256 dla debug/release według wymagań), Sheets API, wymagane uprawnienia, instrukcja instalacji.
3. `docs/architecture.md`, `docs/metric-registry.md`, `docs/feasibility.md`, `docs/test-results.md`.
4. Testy automatyczne (repozytoria fake, agregacje, migracje metryk, idempotencja upsert, worker) oraz checklist testu na urządzeniu.
5. Działający instalowalny APK wygenerowany na końcu, jeśli build environment to umożliwia; w przeciwnym razie jasny opis blokera wraz z logami builda i instrukcją lokalnego zbudowania.
6. Lista funkcji dostarczonych, pozostałych problemów oraz propozycja realizacji etapów P1–P5.

## 11. Źródła i dokumentacja do sprawdzenia przed implementacją

- Health Connect: odczyt i historia: https://developer.android.com/health-and-fitness/health-connect/read-data
- Health Connect: funkcje i dostępność: https://developer.android.com/health-and-fitness/health-connect/features/availability
- Health Connect: typy danych: https://developer.android.com/health-and-fitness/health-connect/data-types
- Health Connect: agregacje i priorytety: https://developer.android.com/health-and-fitness/health-connect/aggregate-data
- Health Connect: synchronizacja i Changes API: https://developer.android.com/health-and-fitness/health-connect/sync-data
- Health Connect: codelab (historia/tło): https://developer.android.com/codelabs/health-connect
- Google Health ↔ Health Connect: https://support.google.com/googlehealth/answer/14506680?hl=en-GB
- Google Sheets create: https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/create
- Google Sheets append: https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/append
- Google OAuth Android: https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient
- Google OAuth token lifecycle: https://developers.google.com/identity/protocols/oauth2/

---

**Główna zasada projektu:** dane zdrowotne mają pozostać pod kontrolą użytkownika, a każda synchronizacja ma być **wznawialna, idempotentna i uczciwa co do dostępności danych**. Najpierw doprowadź MVP do stabilnego działania na prawdziwym telefonie; dopiero potem rozszerzaj katalog i analitykę.
