# Health Sync — aktualny plan

**Aktualizacja:** 29.10.2026

**Cel:** prywatna aplikacja Android, bez własnego serwera. Health Connect jest podstawowym źródłem prawdy dla standardowych danych zdrowotnych i źródłem synchronizacji do Google Sheets. Plan obejmuje uzupełnienie jego historii z Takeout, osobne wzbogacanie arkusza o wybrane dane Google Health API oraz odrębny import odżywiania z arkusza do Connect. Interfejs jest po polsku. Poprawność danych i możliwość wznowienia mają pierwszeństwo przed wygodą i wydajnością.

Oznaczenia: **[x]** zaimplementowane i potwierdzone w podanym zakresie; **[~]** częściowo wykonane albo wymagające dalszych testów; **[ ]** do wykonania. Testy na urządzeniu i testy automatyczne oznaczamy osobno. Stan opiera się na kodzie i dotychczasowych potwierdzeniach użytkownika.

## Uzgodniony zakres i zasady

- Tylko użyteczne agregaty dzienne w zakładce `daily`; bez eksportu pojedynczych pomiarów tętna i innych szczegółowych serii.
- Health Connect jest podstawowym źródłem prawdy: standardowe rekordy z Takeout mają najpierw uzupełnić Connect przez Health Sync, a dopiero później trafić zwykłą synchronizacją do arkusza. Sam brak dnia w Connect nie kwalifikuje metryki do bezpośredniego importu desktopowego/API.
- Dodatkowe pola, których Connect nie reprezentuje w równoważnej postaci, mogą wzbogacać arkusz z Google Health API. Jednorazowy desktopowy import Takeout obejmuje wyłącznie zatwierdzone pola dostępne także w API, z tą samą definicją. **Sleep score, oceny składowe i Sleep Profile są wyłączone**, dopóki nie będzie wspieranej ścieżki późniejszego uzupełniania z API i nowej decyzji użytkownika.
- **Bramka rozpoczęcia nowych funkcji:** przed rozpoczęciem implementacji każdej z nowych sekcji A–E oraz ich wspólnych mechanizmów poniżej zapytać użytkownika i poczekać na jego zgodę. Dodanie szczegółów do planu nie jest zgodą na implementację. Bieżąca, osobno zlecona poprawka `sleep_hours` i dodanie czasu wybudzeń są autoryzowane do wykonania teraz.
- Aplikacja korzysta z OAuth użytkownika i `drive.file`, bez własnego backendu i bez sekretów w repozytorium. Wpisanie ID nie daje samo w sobie dostępu do pliku.
- Zachowuj inne zakładki i kolumny użytkownika. Brak rekordu lub odmowa dostępu nie oznaczają zera ani polecenia usunięcia istniejącej wartości. Nie kasuj ręcznie wpisanych danych w wyniku braku danych źródłowych.
- Dzień jest identyfikowany przez `date` w strefie `Europe/Warsaw`; powtórzenia i ponowienia nie mogą tworzyć duplikatów. Pomiary snu przypisuje się do dnia zakończenia sesji.
- Automatyczna synchronizacja jest opcjonalna, uruchamiana przez WorkManager w odstępach 1, 3, 12 albo 24 godzin. Android może opóźnić uruchomienie. Każdy przebieg sprawdza ostatnie 3 dni i zmiany wagi oraz tkanki tłuszczowej; nie obiecujemy natychmiastowego wyzwalania po zmianie.

## Stan funkcji

### Aplikacja i interfejs

- [x] Cztery zakładki: Sync, Dane, Arkusz, Dziennik; logo w nagłówku i ikona aplikacji.
- [x] Ciemny motyw, systemowe kolory dynamiczne na Androidzie 12+ i awaryjna paleta na starszym systemie; dopasowanie podpisu nawigacji do większego tekstu.
- [x] Dane: 23 statystyki dzienne (w tym nowy czas wybudzeń), grupy, liczniki i przełączniki także dla wyboru częściowego; przycisk brakujących zgód i link do ustawień Health Connect.
- [x] Data początkowa z natywnym wybieraczem, zapamiętywanie daty, wspólny przycisk importu i wysyłki, pasek rzeczywistego postępu porcji, przerwanie importu.
- [x] Kontekstowa prośba o starszą historię i odczyt w tle; konfiguracja automatycznej synchronizacji i czas ostatniego uruchomienia.
- [x] Dziennik ręczny i automatyczny, grupowanie etapów i ponowień, filtr błędów oraz kopiowanie nowych szczegółów bez wartości pomiarów lub tokenów; poprzednia historia zachowana. Nowe wpisy odróżniają wynik ostatniej próby od wcześniejszych błędów; starsze wpisy z niejednoznacznym wynikiem nie są zgadywane.
- [~] Dziennik wskazuje odpowiednią zakładkę przy typowych błędach i zachowuje kody HTTP. Pokazuje osobno liczbę dodanych wierszy i zapisów do istniejących (bez twierdzenia, że wartości się zmieniły). Szczegółowe działania naprawcze pozostają do uzupełnienia; użytkownik potwierdził działanie liczników w rzeczywistym arkuszu.
- [x] Pasek postępu jest dostępny we wszystkich zakładkach; przerwany import jest oznaczony i można go wznowić. Użytkownik potwierdził działanie paska i importu na urządzeniu.

### Health Connect i import

- [x] Odczyt i agregacja dostępnych danych, wybór metryk i zgód, lokalna baza Room, checkpointy i kolejka; brak rekordu nie nadpisuje danych zerem.
- [x] Starsza historia zależy od dostępności funkcji i zgody Health Connect. Data początku jest wybierana ręcznie; brak osobnego trybu „cała dostępna historia”.
- [~] Siedem statystyk wcześniej oznaczonych „ostatni pomiar” korzysta ze średniej arytmetycznej pomiarów z danej doby: HRV, SpO₂, oddechy, temperatura ciała, VO₂ max, tłuszcz i beztłuszczowa masa. Brak wyników pozostaje brakiem; każdy rekord ma równą wagę, również potencjalne duplikaty źródeł. Nowe kolumny średnich pozostają odrębne od ukrytego archiwum starych pomiarów; migracja bazy 4→5 zabezpiecza wcześniejsze obserwacje/checkpointy przed ponownym wysłaniem jako średnia. Automatyczne testy i test migracji na fikcyjnej bazie przeszły; porównanie na rzeczywistych danych czeka na użytkownika. W celu uzupełnienia historii należy ponownie zaimportować żądany zakres.
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
- [x] Polskie nagłówki jak w aplikacji (`Data`, `Dystans` itd.) i niezmienne identyfikatory w kodzie; migracja starych nazw podczas synchronizacji, bez zmiany kolejności i innych zakładek. Rozpoznawane są wyłącznie polskie i wcześniejsze techniczne nazwy; `Data` pozostaje pierwsza. Nazwy statystyk są zarezerwowane, kolizje lub nagłówki będące formułami blokują synchronizację. Formuły użytkownika odwołujące się do wcześniejszych nazw tekstowych mogą wymagać zmiany. Testy jednostkowe mapowania, mieszanych nazw, kolejności i konfliktów; użytkownik potwierdził nazwy w rzeczywistym arkuszu.
- [x] Poprawka końcowego przecinka dla liczb całkowitych: w kolumnach liczbowych aplikacji usuwamy wymuszony format `0.##`, przywracając automatyczny format Sheets. Użytkownik potwierdził wygląd w prawdziwym arkuszu.
- [~] Skrócone nazwy kolumn i statystyk: `Waga`, `% tłuszczu`, `Spalone kalorie`, `Oddechy/min` oraz pozostałe nazwy bez słowa „średnia”; dawne nagłówki rozpoznawane jako aliasy i zmieniane w miejscu. Obie kolumny kalorii są zaokrąglane przy zapisie do pełnej liczby; format wyświetlania obejmuje również wcześniejsze komórki. Pozostałe liczby do 2 miejsc, pełna precyzja w lokalnej bazie. Wcześniejsze ułamkowe wartości kalorii w Sheets zostają trwale zaokrąglone dopiero po ponownym imporcie odpowiedniego okresu. Testy jednostkowe i instrumentacyjne przeszły; porównanie na prawdziwym arkuszu pozostaje do potwierdzenia.
- [~] Nazwy `Waga kg` i `Dystans km` w aplikacji i arkuszu; poprzednie `Waga`, `Średnia masa ciała` i `Dystans` są rozpoznawane jako aliasy i migrowane w miejscu. Build i testy przeszły; wersja wgrana na telefon, rzeczywista migracja arkusza oczekuje potwierdzenia.
- [x] Czas ćwiczeń, snu i wybudzeń jako tekst `hh:mm:ss`, z migracją dawnych liczbowych czasów w arkuszu.
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

## Nowe funkcje — szczegółowy zakres, jeszcze niezaimplementowane

Użytkownik potwierdził, że reinstalacja Google Health przywróciła historię z chmury i poprawiła eksport bieżącego snu, wagi i tłuszczu do Connect. Część dawnego snu nadal nie trafia do Connect; nie zakładamy konkretnej przyczyny. Poniższe sekcje opisują zatwierdzony kierunek planu, nie gotowe funkcje.

### A. Uzupełnianie Health Connect z Takeout przez Health Sync

**Implementacja rozpoczęta za zgodą użytkownika.** Przygotowano parsery, wybór lokalnego ZIP, zakresu i typów oraz podgląd porównania z Health Connect. Użytkownik potwierdził próbny zapis jednej sesji snu do Connect, przeliczenie w arkuszu i wycofanie jej z Connect. Komórki arkusza pozostały, zgodnie z zasadą zachowania wartości o niepotwierdzonym pochodzeniu. Wznawianie i pozostałe typy nadal wymagają prób.

W kodzie podłączono do interfejsu zapis i odtwarzanie planu, sprawdzanie zawartości przed zapisem i po nim oraz ponowny odczyt dat do lokalnej kolejki. Wycofywanie własnych rekordów weryfikuje ID, treść i czas modyfikacji; niepewne komórki arkusza są zachowywane. Próbny zapis i wycofanie jednej sesji snu w Connect oraz zachowanie komórek użytkownik potwierdził; dalsze scenariusze wymagają testów.

Decyzja użytkownika: po wycofaniu czyścić tylko wartości wcześniej zapisane przez aplikację, jeżeli nie zostały następnie zmienione; niepewne komórki zachować. Sama zgodność wartości nie dowodzi braku wcześniejszej edycji. Należy zaprojektować trwały rejestr własnych zapisów i zachowawczą kontrolę zmian pliku, a także uwzględnić brak atomowego warunkowego czyszczenia w istniejącej ścieżce Sheets; bez dowodu bezpieczeństwa pozostawić komórkę i pokazać ostrzeżenie.

W rzeczywistym eksporcie wykryto nakładające się sesje snu o różnych granicach i podsumowaniach. Podgląd podaje przyczynę konfliktu oraz pokazuje alternatywne sesje i ich stan względem Connect. Nie wybiera ich automatycznie; sposób rozstrzygania wymaga decyzji użytkownika. Przygotowano również osobny rejestr zamiarów i potwierdzonych zapisów, przetestowany w pamięci na telefonie; nie jest on jeszcze podłączony do procesu importu.

W badanym eksporcie pary alternatywnych sesji mają różne wartości `algorithm_version` (`sleep_session_v1` i `sleep_session_v2`); pole `data_source` oznacza metodę zapisu, a nie nazwę aplikacji czy urządzenia. Porównać przykłady z widokiem Health i nie zakładać, że najnowszy timestamp lub wyższa wersja algorytmu zawsze wskazuje właściwy wynik.

Porównanie pojedynczej nocy z Health wykazało, że granice sesji v2 odpowiadają widokowi Health, ale suma faz i czas wybudzeń różnią się od podsumowania w aplikacji. Użytkownik świadomie wybrał import wersji v2 mimo tych różnic. W parach v1/v2 wybierać v2; wielokrotne v2, brak v2 oraz kolizje z Connect pozostają konfliktami. Osobne atrybuty źródeł zewnętrznych należy łączyć po `sleep_id`; ich brak dla danej sesji nie dowodzi, że nie pochodziła ze źródła zewnętrznego. Jeśli pliku atrybutów brakuje lub wskazuje źródło zewnętrzne, nie kwalifikować sesji automatycznie do zapisu. Wybór v2 i mapowanie rekordów są podłączone do zapisu, który wymaga próby na telefonie.

Po pytaniu użytkownika o pozostałe dane: w dostarczonym eksporcie są także pliki kroków, dystansu, kalorii, aktywnych kalorii, tętna spoczynkowego, HRV, natlenienia, oddechów, temperatury i wzrostu, a także inne kategorie. Nie wszystkie są automatycznie możliwe do odtworzenia w Connect: należy sprawdzić źródło, semantykę i przedział każdego rekordu, szczególnie dla wartości dziennych i interwałowych. Historyczne dane odżywiania oraz pliki wyszczególnione w sekcjach B–E wymagają osobnych decyzji zakresowych. Nie przywracać szczegółowej synchronizacji tętna usuniętej na prośbę użytkownika.

- [ ] Osobny, jednorazowy proces importu w aplikacji mobilnej: systemowy wybieracz archiwum/plików, wybór typów i zakresu dat, analiza bez zapisu, podgląd liczby brakujących, istniejących, konfliktowych i nieobsługiwanych rekordów, następnie jawne uruchomienie zapisu.
- [ ] Zakres: sen, waga, tłuszcz i wzrost. Odczyt pierwszych trzech typów sprawdzono na danych użytkownika; próbny import snu potwierdzono w Connect i arkuszu. Obsługa wzrostu jest zaimplementowana i przetestowana jednostkowo, wymaga próby na telefonie. Kolejne standardowe typy dodawać wyłącznie po zdefiniowaniu mapowania. Odtwarzać źródłowe rekordy, nie fikcyjne pojedyncze pomiary z dziennych średnich lub sum. Brak obsługi danego typu ma być widoczny, nie pomijany po cichu.
- [ ] Korzystać z README konkretnej wersji eksportu; walidować nagłówki, typy, jednostki, zakresy wartości i znaczniki czasu. Nazwy typu `UserSleeps_2026-05-07.csv` nie określają dat sesji. Pliki odczytywać lokalnie; nie wysyłać archiwum do serwera i nie dodawać prywatnych eksportów do repozytorium.
- [ ] Sen: połączyć `UserSleeps` i `UserSleepStages` przez identyfikator użytkownika i `sleep_id`; zachować UTC, offsety, początek/koniec, źródłową metodę zapisu i zgodne fazy. `minutes_asleep` służy do walidacji faktycznego czasu snu, nie do utworzenia sztucznego przedziału snu o tej długości. Braki, sprzeczne podsumowania i nieznane fazy pokazać jako konflikty; bez zgadywania czuwania. Nie importować score ani miesięcznego profilu.
- [ ] Porównywać istniejące rekordy wszystkich dostępnych źródeł po rodzaju, tożsamości, czasie/przedziale, jednostkach i wartości; sam fakt istnienia danych tego dnia nie wystarcza. Tolerancje czasu i wartości muszą być jawne i testowane; nie scalać różnych ważeń tego samego dnia. Nakładanie sesji wymaga rozstrzygnięcia zamiast częściowego dopisywania tej samej nocy.
- [ ] Nadać stabilne `clientRecordId` związane z typem, źródłowym kontem i ID rekordu; aktualizacje rozpoznawać po źródłowej wersji/czasie modyfikacji. Chronić przed duplikatami po powtórzeniu tego samego ZIP, kolejnym eksporcie, przerwaniu procesu i ponowieniu po niejednoznacznym wyniku zapisu.
- [ ] Uzyskiwać zgody zapisu wyłącznie dla wybranych typów i odczytu historii do porównania; przy braku dostępu nie twierdzić, że stare rekordy nie istnieją. `dataOrigin` będzie Health Sync; prawidłowo zachować dostępną informację o urządzeniu i metodzie, nie podszywać się pod Fitbit.
- [ ] Trwała sesja importu i rejestr zapisanych ID/wersji, checkpoint dopiero po potwierdzonym zapisie, możliwość wznowienia i wycofania konkretnego importu wyłącznie dla własnych rekordów. Od czasu importu zmodyfikowane rekordy wymagają osobnego rozstrzygnięcia przy wycofaniu.
- [ ] Jeśli Health później zapisze oryginały, rozpoznać konflikt i zaproponować zastąpienie własnej kopii oryginałem. ID naszego importu nie deduplikuje zapisu innej aplikacji; nie polegać wyłącznie na priorytetach snu/aktywności, szczególnie przy wadze. Nie przepisywać danych odczytanych z Connect z powrotem do Connect ani tworzyć pętli przez Health.
- [ ] Po zapisie ponownie odczytać dotknięte daty przez standardową ścieżkę Connect → Room → arkusz, także daty spoza ostatnich 3 dni. Nie zapisywać tych samych podstawowych pól bezpośrednio z Takeout do arkusza. Wymagana zgodna obsługa faz i nakładających się sesji przed importem snu.
- [ ] Testy: import i powtórzenie, różne konta/wersje, błędny CSV, UTC/DST, przerwanie po zapisie, cofnięcie zgody, późniejszy duplikat Fitbit, bezpieczne wycofanie i ponowna synchronizacja starszych dat. Najpierw próbka kilku sesji, później duży zakres po ręcznym porównaniu.

### B. Desktopowy import Takeout bezpośrednio do arkusza — tylko wzbogacenie

**Rozpoczęcie: najpierw zapytać użytkownika i uzyskać zgodę na implementację tej sekcji. Wybór CLI lub lokalnej strony HTML/JS pozostaje do ustalenia z użytkownikiem.**

- [ ] Zakres jest przecięciem: pola obecne w Takeout, obsługiwane przez Google Health API i pozbawione równoważnej standardowej reprezentacji w Connect. Wybrane przydatne statystyki snu są głównym zastosowaniem. Brak pojedynczej daty w Connect nie stanowi wyjątku — standardowe dane uzupełnia sekcja A.
- [ ] **Nie importować `overall_score`, ocen składowych, Sleep Profile i innych pól dostępnych wyłącznie w Takeout.** Użytkownik wybrał spójność z przyszłą aktualizacją przez API. Nie wymyślać zamiennika sleep score z efektywności snu.
- [ ] Wspólny z modułem API katalog pól, nagłówki, jednostki i wersja definicji agregacji. Przed wyborem metryki potwierdzić zgodność semantyczną jej CSV z konkretnym polem stabilnego API; podobna nazwa nie wystarcza. Pola niewystępujące w danym archiwum raportować jako niedostępne.
- [ ] Lokalne przetwarzanie wskazanych plików; profil źródłowego konta, zakres dat i wybór kolumn, podgląd zmian bez zapisu, raport konfliktów. W CLI oddzielne polecenie zapisu po podglądzie; w HTML odpowiednik zatwierdzenia. Nie przechowywać sekretu klienta w stronie lub APK; sposób autoryzacji dopasować do wybranej platformy.
- [ ] Połączyć sesje i podsumowania po źródłowych identyfikatorach. Dzień snu wynika z zakończenia sesji w Europe/Warsaw, nie z `score_time`/daty wygenerowania pliku. Pola sesji głównej i sumy dobowe nie mogą korzystać z różnych reguł bez osobnych nazw.
- [ ] W istniejącej zakładce `daily` dopasowywać po `date` i nagłówkach. Dodawać wyłącznie wybrane kolumny wzbogacenia, wypełniać puste komórki, zachować ręczne wartości i formuły; konflikty istniejących wartości wymagają podglądu i decyzji. Sortować pełne wiersze, zachować cudze kolumny i zakładki. Standardowe pola, np. `sleep_hours`, waga, kcal, nie są celem tej ścieżki.
- [ ] Raport/manifest pochodzenia bez tokenów: wersja importera i schematu, typ źródła, identyfikatory/wersje rekordów, daty i komórki zarządzane. Zabezpieczyć prywatne artefakty i wykluczyć je z Git. Powtórny import musi być idempotentny.
- [ ] Przygotować uzgodniony mechanizm współpracy z mobilnym API: API może aktualizować wyłącznie rozpoznane komórki automatycznego importu zgodnie z regułą wersji; nieznane/ręczne/formuły to konflikt. Nie zakładać wspólnego mutexu między komputerem i telefonem — na czas zapisu desktopowego wstrzymać mobilne wysyłanie i ponownie sprawdzić strukturę arkusza przed zapisem.
- [ ] Testy plików i stref czasu, dodawania starszych dat, powtórzeń, formuł, przerw po wysyłce, zgodności z wynikiem przyszłego API oraz przejęcia importowanej historii przez API. Duży import dzielić na porcje i umożliwić wznowienie.

### C. Google Health API → arkusz — osobna synchronizacja wzbogacenia

**Rozpoczęcie: najpierw zapytać użytkownika i uzyskać zgodę na implementację tej sekcji, również przed diagnostyczną integracją/OAuth.**

- [ ] API wzbogaca wybrane dodatkowe pola; nie zastępuje podstawowych pól Connect i nie zapisuje automatycznie do Connect. Brak danych Connect, odmowa zgody lub błąd nie powodują przełączenia `sleep_hours`, wagi itp. na inne źródło.
- [ ] Pełny katalog kandydatów do wyboru, nie automatyczne włączenie wszystkich. Dla każdego pola podać definicję, jednostkę, właściciela kolumny, źródło, zakres czasu, obsługę sesji głównej/drzemek i dostępność w stabilnym API oraz Takeout. Funkcje tylko beta oznaczyć i nie obiecywać jako gotowych. Wybranie pełnej listy nie oznacza eksportu szczegółowych serii.
- [ ] Kandydaci snu: czas zasypiania, czas po końcowym przebudzeniu, krótkie przebudzenia i ich czas/liczba, informacja o głównej sesji/drzemce, czas/udział faz i czuwania, efektywność. Fazy i śródsenne czuwanie są reprezentowane w Connect — ich dzienne pochodne w pierwszej kolejności obliczać z Connect, a nie tworzyć konkurencyjne kolumny z API. Oryginalne podsumowania producenta mogą mieć osobną nazwę tylko po świadomym wyborze i wyjaśnieniu różnicy definicji.
- [ ] Kandydaci dodatkowych nocnych podsumowań: dzienne/nocne HRV RMSSD, dostępne wskaźniki dla snu głębokiego/non-REM, nocna średnia i granice SpO₂, oddechy podczas snu, temperatura skóry względem bazy i dostępne wartości bazowe. Nie utożsamiać ich z ostatnim pomiarem HRV/SpO₂/temperatury ciała z Connect. Dodatkowe składowe HRV/temperatury wymagają sprawdzenia wersji API; jeśli stabilny endpoint ich nie zwraca, pozostają niedostępne.
- [ ] Kandydaci spoza snu: wyliczane przez Google strefy tętna, czas i energia w strefach oraz Active Zone Minutes — różne od zwykłych minut ćwiczeń i niekoniecznie identyczne z intensywnością Connect. Tylko po wyborze użytkownika, bez tras GPS, surowego ECG lub pełnych serii pomiarów w obecnym zakresie.
- [ ] Sleep score, jego oceny składowe i miesięczny profil wyłączone; brak ich w obecnie udokumentowanym schemacie nie może być zastąpiony własnym przybliżeniem. Tylko pola zatwierdzone dla API mogą zasilać też desktopowy import B.
- [ ] Dodatkowe zgody OAuth tylko dla wybranych grup; zweryfikować konto i powiązanie Google Health. Zabezpieczone przechowywanie danych autoryzacji, odnowienie dostępu, obsługa cofnięcia zgody i wygaśnięcia w trybie Testing; żadnych tokenów w logach i sekretów w kodzie. Najpierw po zgodzie użytkownika diagnostyczny odczyt kilku znanych nocy bez zapisu.
- [ ] W zakładce Sync osobna, rozwijana sekcja „Dodatkowe dane Google Health”: osobny przycisk ręcznej synchronizacji, zakres historii, wybór pól, postęp, anulowanie/wznowienie, ostatni wynik i ostatni sukces. Błąd API nie blokuje Connect. Dziennik odróżnia oba źródła i operacje ręczne/auto; nie mnożyć stałych przycisków na głównym ekranie.
- [ ] Osobny przełącznik automatyzacji i natywny wybieracz preferowanej godziny, początkowo wyłączone. Dla snu raz dziennie, z kontrolowanym krótkim ponowieniem, jeśli wybrana noc nadal jest przetwarzana. Godzina jest preferowaną porą, nie gwarancją dokładności Androida; użyć WorkManager, bez żądania alarmów dokładnych. Obliczać następny termin kalendarzowo, uwzględnić zmianę czasu, restart i zmianę ustawienia; ustalić strefę harmonogramu w UI (domyślnie Europe/Warsaw).
- [ ] Nie uruchamiać ponownego pełnego importu historii codziennie. Początkowy backfill na żądanie, potem przyrostowo plus krótki zakres ponownego odczytu na spóźnione/reinterpretowane dane (propozycja 3 ostatnie zakończone dni). Dopasować pola i limity do endpointów; sen wymaga paginacji również przy pozornie dużym limicie strony. Rozróżnić brak danych od zera i nieukończonego przetwarzania.
- [ ] Oddzielne checkpointy, kolejka i stan API; po sukcesie pobrania zachować dane lokalnie na czas awarii Sheets. Wspólny serializowany zapis do arkusza i jawne przypisanie kolumn do źródła. Obsłużyć 429, 5xx, timeout po zapisie i wznowienie bez duplikatów. Nie nadpisywać nowszej wersji danych starszym eksportem ani zmienionych ręcznie komórek.
- [ ] Pomierzyć czas, liczbę żądań, transfer i koszt CPU/API dla małego zakresu przed wyborem domyślnego harmonogramu. API wymaga sieci, lecz nie zakładać bez pomiaru dużego kosztu baterii — obliczenia producenta są wykonywane w chmurze.
- [ ] Testy: zgodność pól z Takeout i kilkoma nocami Health, sesje główne/drzemki, null/zero, stan processing, paginacja, korekty historii, odnowienie OAuth, opóźnienia harmonogramu, DST/restart, niezależność od synchronizacji Connect, współbieżność i ochrona kolumn użytkownika.

### D. Osobny arkusz odżywiania → Health Sync → Health Connect

**Rozpoczęcie: najpierw zapytać użytkownika i uzyskać zgodę na implementację tej sekcji. Przed mapowaniem odczytać wskazany przez użytkownika arkusz i uzgodnić jego strukturę.**

- [ ] Osobny arkusz źródłowy wybrany przez Picker/ID/link z autoryzacją dostępu; zakładka/range i mapowanie kolumn, jednostek, dat, ID i formatu posiłków albo sum dziennych. Arkusz jest źródłem prawdy dla zaimportowanych wpisów żywieniowych; Connect pozostaje centralnym magazynem. Nie modyfikować arkusza z posiłkami ani jego formuł.
- [ ] Odczytywać wyniki formuł jako liczby, rozpoznawać błędy formuł i nie traktować pustych komórek jako zera. Walidować jednostki kcal/kJ, g/mg, daty i dodatnie ilości. Jeśli znana jest tylko data, nie udawać znajomości godziny konkretnego posiłku.
- [ ] Podstawowe pola: data/czas, trwałe ID, opcjonalna nazwa i typ posiłku, energia spożyta, białko, węglowodany, tłuszcz. Dodatkowo opcjonalne błonnik, cukry, rodzaje tłuszczów, cholesterol, kofeina, witaminy i minerały. Nawodnienie jako osobny `HydrationRecord`, jeśli użytkownik wybierze. Energia spożyta nigdy nie jest zapisem do kalorii spalonych.
- [ ] Wybór jednego poziomu szczegółowości: poszczególne posiłki albo jeden rekord sumy dobowej, nigdy oba naraz. Przy braku trwałych ID uzgodnić sposób ich nadania lub trwały klucz dzienny; numer wiersza nie jest ID. Sortowanie/wstawienie wierszy nie może duplikować wpisów.
- [ ] Osobna zgoda `WRITE_NUTRITION` i ewentualnie nawodnienia, podgląd próbki przed zapisem, stabilne identyfikatory i wersje, wznowienie i rejestr pochodzenia. Zmiana wartości źródłowej aktualizuje wyłącznie własny rekord, nie wpis innych aplikacji.
- [ ] Usunięcia: pusty/błędny/niepełny odczyt arkusza lub utrata dostępu nigdy nie kasują danych. Rozpoznawać usunięcie dopiero po pełnym udanym odczycie uzgodnionego zakresu i uzgodnieniu polityki z użytkownikiem. Usuwać tylko własne rekordy. Unikać dublowania ręcznie wprowadzonych posiłków i importu tych samych danych przez inne aplikacje.
- [ ] Niezależny przycisk ręczny, harmonogram i historia ostatniego sukcesu/błędu; nie wiązać domyślnie z porą nocnego API. Interwał ustalić po obejrzeniu arkusza i częstości jego aktualizacji. Zapis do Connect nie wymaga potwierdzania przez chmurę Health; osobno przetestować, co Health faktycznie odczytuje i wyświetla.
- [ ] Jeżeli później żywienie będzie eksportowane do arkusza `daily`, odczytywać je z Connect jako wybraną statystykę, bez automatycznego zapisu z powrotem do źródłowego arkusza lub ponownego wstawiania do Connect. Trzy procesy (Connect, API, żywienie) mają niezależne ustawienia i checkpointy.
- [ ] Testy: próbny posiłek i korekta, zmiana kolejności wierszy, usunięcie, sumy kontra posiłki, błędy formuł, jednostki/strefy czasu, brak sieci, cofnięcie dostępu, ponowienie po zapisie, ponowny import po restarcie, ochrona obcych wpisów i wycofanie własnej partii.

### E. Desktopowa aplikacja tras i treningów Google Health

**Rozpoczęcie: najpierw zapytać użytkownika i uzyskać zgodę na implementację tej sekcji. Umiejscowienie projektu pozostaje do decyzji użytkownika.**

- [ ] Cel: prywatna aplikacja uruchamiana na komputerze, która pobiera historię treningów z Google Health API, a ich zapisane trasy i dostępne metadane pokazuje w przeglądarce. Nie wymaga zmian w Health Sync na telefonie ani zapisu tras do arkusza.
- [ ] Rozważyć osobne repozytorium/aplikację jako domyślną propozycję: niezależny interfejs, uprawnienia lokalizacji, cykl życia i przechowywanie danych; wspólny moduł Google Health API tylko jeśli faktycznie pozwoli bezpiecznie współdzielić kod. Alternatywa: wydzielony katalog w tym repozytorium. Wyboru nie przesądzać bez użytkownika.
- [ ] Wybrać technologię desktopową i sposób uruchomienia przeglądarki: lokalny serwis związany tylko z interfejsem loopback i/lub natywne opakowanie; aplikacja bez publicznego serwera. Chronić lokalne endpointy, sesje i tokeny przed dostępem innych stron i programów. Rozdzielić proces pobierania, prywatną bazę i interfejs mapy.
- [ ] Lista treningów z paginacją oraz datą/czasem, rodzajem, nazwą, czasem aktywności, dystansem, kaloriami, średnim tętnem i innymi dostępnymi polami. Filtrowanie po dacie/typie, wybór treningu i ekran szczegółów. Pola opcjonalne pozostają puste; trening bez trasy jest dostępny na liście i wyraźnie oznaczony.
- [ ] Pobierać zapisane trasy konkretnych treningów przez `exportExerciseTcx` z `alt=media`. Zachować oryginalny TCX oraz metadane/źródło, dokładność czasu i dostępne punkty (współrzędne, wysokość, dystans; inne pola wyłącznie jeśli występują). Obsłużyć niepełny zapis GPS, braki, pauzy, granicę dat, strefy czasu, duże pliki i błędny XML bez dopowiadania trasy.
- [ ] Lista sesji/wybrany okres: ręczne pobranie historii z postępem, przerwaniem i wznowieniem; aktualizacja nowych lub zmienionych treningów bez ponownego pobierania wszystkiego. Trwałe ID konta i treningu oraz bezpieczna idempotencja. Osobno zaplanować zachowanie po usunięciu/zmianie treningu w chmurze — brak wyniku lub błąd nie usuwa lokalnej kopii automatycznie.
- [ ] Interaktywna mapa z dopasowaniem widoku do trasy, zaznaczonym początkiem/końcem, możliwością powiększania i poruszania; szczegóły treningu obok. Opcjonalnie znaczniki punktów i wykresy wysokości lub tempa, wyłącznie dla rzeczywiście dostępnych danych. Pusta trasa nie blokuje podsumowania.
- [ ] Dostawca map do ustalenia: Google Maps JavaScript API wymaga konfiguracji klucza i konta rozliczeniowego; sprawdzić aktualne limity i ewentualne koszty przed wyborem. Alternatywa: mapa OpenStreetMap z odpowiednią biblioteką i dostawcą kafelków, z widocznym uznaniem autorstwa i poszanowaniem jego zasad. Dane OSM nie oznaczają dowolnego ani gwarantowanego bezpłatnego hostingu kafelków.
- [ ] Lokalne wyświetlanie współrzędnych na mapie nadal pobiera kafelki od wybranego dostawcy; może ujawniać oglądany obszar oraz adres IP. Wyjaśnić ten przepływ, nie wysyłać całej trasy do dodatkowych usług. Mapy offline tylko z danych i dostawcy, którzy na to zezwalają; nie zakładać możliwości pobierania obszarów z publicznego serwera kafelków OSM.
- [ ] Uwierzytelnianie desktopowe przez Google OAuth z przeglądarką systemową. Do odczytu listy aktywności i eksportu GPS potrzebne odpowiednie zgody aktywności oraz lokalizacji; zakres możliwie ograniczony do odczytu. Sprawdzić ustawienia Cloud, dostępność API i wygaśnięcie tokenów w trybie testowym. Nie umieszczać sekretów ani tokenów w repozytorium, HTML, logach i nazwach plików; zabezpieczyć lokalne poświadczenia.
- [ ] Dane tras i treningów są wrażliwe: przechowywać lokalnie w wybranym miejscu, zapewnić opcję usuwania cache i lokalnej historii oraz czytelne informacje, które pliki zostają na komputerze. Bez automatycznego publicznego udostępniania, analityki i przesyłania tras do niepowiązanych usług. Eksport TCX/GPX lub inne formaty — oddzielna opcja do ustalenia, z zachowaniem oryginału.
- [ ] Obsłużyć cofniętą zgodę, brak sieci, limity API, błędy pobrania trasy, brak GPS i ponowne próby. Oddzielić błędy dostępu do Google Health od błędów dostawcy map, nie logować współrzędnych ani wartości zdrowotnych.
- [ ] Testy na fikcyjnych trasach: paginacja, aktualizacja, przerwanie, format TCX i brak GPS, brakujące współrzędne, granice dat i DST, błędy API, ograniczenie lokalnego serwera, wczytanie mapy oraz usunięcie lokalnych danych. Rzeczywistą trasę sprawdzić dopiero po skonfigurowaniu konta i zgodzie użytkownika na dostęp.
- [ ] Przed rozpoczęciem uzgodnić: osobne repozytorium czy katalog w obecnym, preferencję Google Maps lub map OSM, zapisywanie i czas przechowywania tras lokalnie oraz ewentualny eksport plików. Rozpocząć od prototypu odczytu kilku treningów i jednej trasy, bez obiecywania historycznej kompletności przed sprawdzeniem API.

### Wspólne zależności, decyzje i kolejność

**Rozpoczęcie implementacji wspólnych mechanizmów dla A–E również wymaga wcześniejszego zapytania użytkownika i jego zgody.**

- [~] Poprawność obecnego snu: zaimplementowano `sleep_hours` z faz SLEEPING/LIGHT/DEEP/REM oraz opcjonalną statystykę `sleep_awake_hours`, która sumuje AWAKE/AWAKE_IN_BED/OUT_OF_BED wyłącznie pomiędzy fazami snu (bez zasypiania i końcowego czuwania). Użytkownik potwierdził działanie czasu wybudzeń. Brak/nieznane fazy, luki i nakładanie nie są zgadywane; niepełny wynik dobowy nie jest wysyłany jako pełny. Brak wyniku nie kasuje wcześniejszej wartości, więc stara wartość z wersji liczącej czas sesji może pozostać do wyjaśnienia danych. Historię przelicza ponowny import; porównanie poprawności czasu snu dla większego zakresu i rozstrzyganie duplikatów między źródłami pozostają do wykonania przed A.
- [ ] Następnie proponowana kolejność po kolejnych zgodach: A i test kilku historycznych nocy → katalog metryk, zgodności i własności kolumn → odczyt diagnostyczny API i C → desktop B → odżywianie D jako niezależne zadanie. Odczyt diagnostyczny przed desktopem pozwala sprawdzić, które pola rzeczywiście będzie można kontynuować z API.
- [ ] Decyzje odłożone do odpowiedniego etapu: CLI czy HTML; konkretne zaznaczone pola z pełnego katalogu; godzina API i strefa harmonogramu; polityka głównego snu/drzemek w dodatkowych polach; struktura i harmonogram arkusza odżywiania; reguły potwierdzania usunięć/konfliktów. Nie wymuszają blokowania samej aktualizacji planu.
- [ ] Rejestr pochodzenia i minimalne dane potrzebne do identyfikacji/wersjonowania rekordów; osobne stany importów, odczytów i wysyłek. Wspólny zapis Sheets musi chronić strukturę, kolejność dat i pola użytkownika oraz aktualne wybory kolumn, również po odtworzeniu ustawień.
- [ ] Obecna statystyka dzienna sumuje sesje kończące się danego dnia w Europe/Warsaw. Rozdzielenie „główna noc” i „wszystkie sesje” musi być jawne i nie może po cichu zmieniać istniejących kolumn.
- [ ] Pełne archiwa mogą zawierać szczegóły potrzebne do obliczenia statystyk lub odtworzenia sesji Connect, ale nie wprowadza to eksportu surowych serii do Sheets. Zweryfikować definicje na kilku wybranych dniach, dopiero potem importować całość; nie deklarować zgodności wyłącznie na podstawie nazwy pola.

### Źródła do ponownej weryfikacji przed implementacją

- [Typy danych Connect](https://developer.android.com/health-and-fitness/health-connect/data-types), [sesje snu](https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions), [zapis](https://developer.android.com/health-and-fitness/health-connect/write-data), [agregacja i priorytety](https://developer.android.com/health-and-fitness/health-connect/aggregate-data).
- [Typy Google Health API](https://developers.google.com/health/data-types), [schemat stabilnego API](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints), [sen](https://developers.google.com/health/data-types/sleep), [OAuth](https://developers.google.com/health/setup).
- [Treningi i trasy Google Health API](https://developers.google.com/health/data-types/workouts), [eksport TCX](https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/exportExerciseTcx), [koszty Google Maps](https://developers.google.com/maps/documentation/javascript/usage-and-billing), [zasady kafelków OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/).
- [Harmonogram WorkManager](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest.Builder). Preferowana godzina nie gwarantuje punktualnego uruchomienia.
- README Takeout dostarczone przez użytkownika 29.10: UserSleeps, UserSleepStages, UserSleepScores, Sleep Profile. Zawartość prywatnych CSV i konkretna zgodność z API pozostają do sprawdzenia; README nie określają znaczenia daty w nazwie pliku.

## Dalsze możliwości, bez zmiany obecnego zakresu

- Zmiana strefy czasu z kontrolowaną przebudową dat; reguły Wi-Fi i ładowania; powiadomienia o problemach bez danych zdrowotnych na ekranie blokady.
- Opcjonalne wykresy, dashboard, eksport CSV i kopia ustawień.
- Więcej dziennych statystyk Connect po wyborze zakresu; obecne 23 statystyki nie obejmują wszystkich typów danych platformy. Zachować zasadę bez szczegółowych serii pomiarów.

## Techniczne zasady implementacji

- Kotlin, Compose i Material 3; Health Connect, Room, DataStore, WorkManager, AuthorizationClient i Google Sheets API.
- Nie zakładać, że brak starszych danych w Health Connect oznacza ich brak w Google Health; prywatne eksporty czytać wyłącznie po wskazaniu przez użytkownika, nie przeszukiwać samodzielnie komputera i nie dodawać eksportów do repozytorium.
- Uwzględniać błąd sieci, wygaśnięcie zgody, przerwanie procesu i ponowienie po niejednoznacznym wyniku operacji Google Sheets.
- Nie zapisywać tokenów ani wartości zdrowotnych w diagnostyce. Użytkownik może dodawać własne zakładki i kolumny.
- Zmianę obecnych założeń wprowadzaj do planu razem z kodem; nie przedstawiaj testów lub integracji jako wykonanych bez dowodu.
