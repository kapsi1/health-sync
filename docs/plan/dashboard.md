# E. Desktopowy dashboard zdrowia i aktywności

**Brak implementacji; osobne zlecenie/zgoda przed rozpoczęciem.** Umiejscowienie i technologia pozostają do decyzji. Cel: prywatny dashboard dostępnych danych zdrowotnych oraz baza żywności, dziennik i planer, potencjalnie we wspólnej aplikacji/bazie. [Indeks](README.md) · [wariant arkuszowy żywienia D](nutrition.md).

## Dane zdrowotne i architektura

- [ ] Widoki dzienne/historii, pomiary, trendy, wykresy, sen, aktywność i treningi — w zakresie faktycznie dostępnym w Connect, Takeout i API. Pokazywać jednostki, pochodzenie, braki i ograniczenia; nie obiecywać pełnej kopii Google Health.
- [ ] Wybrać wariant źródła wyników według [analizy](data-sources.md): rekomendowana własna baza z oryginałami i regułami per metryka, zamiast globalnego pierwszeństwa API/Connect. Zaprojektować transfer telefon↔dashboard, autorytatywne miejsce zapisu, repliki, konflikty i offline. Connect jest lokalny na Androidzie; API nie jest jego pełną kopią. Analiza jest zlecona, wariant i implementacja pozostają do decyzji.
- [ ] Uzgodnić rolę Sheets: formuły, ręczne dane, eksport/udostępnianie i osobny arkusz posiłków. Porównać dalszą synchronizację, eksport opcjonalny i zastąpienie dashboardem. Wyjątek już uzgodniony: obecny tymczasowy arkusz zdrowia można przebudować według [planu kalorii](calories.md); nie dotyczy to innych arkuszy/posiłków. Dashboard i Sheets mają korzystać z tego samego wyniku i wspólnych [uwag dnia](shared.md#uwagi-do-dnia).
- [ ] Wybrać osobne repo/aplikację lub wydzielony katalog. Ocenić wspólny Kotlin/JVM dla parserów ZIP/CSV, modeli, walidacji, obliczeń, porównań/deduplikacji i testów; obecna domena używa Java I/O/java.time. Connect, URI/zgody Android, Room i UI są platformowe. Dla samego JS lub późniejszego iOS ocenić Kotlin Multiplatform i zmiany API. Nie wydzielać modułu przed uzgodnieniem.
- [ ] Przeglądarka z lokalnym serwisem tylko na loopback i/lub natywne opakowanie, bez publicznego backendu. Chronić endpointy/sesje/tokeny przed innymi stronami/programami; oddzielić pobieranie, prywatną bazę i mapę. Zaplanować wersjonowanie schematu, kopie i odtwarzanie.

## Żywność, dziennik i planer

- [ ] Baza: produkty/potrawy, nazwa, porcje, jednostki, energia i makra, opcjonalnie dalsze składniki; wyszukiwanie, edycja, kategorie/warianty. Wartości na 100 g/100 ml lub porcję; brak to nie zero, zachować źródło/jednostki. Katalogi zewnętrzne i kody kreskowe ocenić osobno: dostępność, jakość, licencje i koszty.
- [ ] Dziennik: posiłki/składniki/ilości/daty, opcjonalnie czas i rodzaj, sumy porcji/posiłku/dnia i historia obok danych zdrowotnych. Receptury i liczba porcji; ustalić wersjonowanie oraz wpływ edycji produktu/receptury na historię i wartości zapamiętane w chwili wpisu.
- [ ] Planer na wybrane dni: produkty/receptury, sumy, kopiowanie/edycja; plan oddzielony od zjedzonego, przeniesienie do dziennika jawne. Cele/lista zakupów opcjonalnie po uzgodnieniu; niepełnych składników nie prezentować jako pełnych sum.
- [ ] Jedna baza: oddzielić wpisy użytkownika, plan i odczyty Connect/API. Uzgodnić eksport spożytych posiłków lub sum do Connect, edycje/usunięcia/konflikty/offline. Nie wysyłać planu jako spożytego ani dublować arkusza/innych aplikacji.
- [ ] Import obecnego arkusza: najpierw obejrzeć wskazany plik i uzgodnić produkty/receptury/jednostki/porcje/historię/wyniki formuł. Podgląd, walidacja, braki, ID i deduplikacja, powtarzalność i zachowanie oryginału. Nie zakładać struktury ani automatycznie jej zmieniać; późniejszą współpracę ustalić po analizie.
- [ ] Testy: jednostki/porcje, nieznane wartości, receptury i korekty, historia/plan, granice dnia, ponowny import arkusza, duplikaty, błędy formuł, aktualizacje Connect i odtwarzanie bazy.

## AI do wprowadzania jedzenia

- [ ] Opis językiem naturalnym → propozycja produktów/ilości/jednostek/składników i wartości, z pochodzeniem/niepewnością → poprawka/zatwierdzenie użytkownika → zapis. Pytać o brakujące ilości i niejednoznaczności; szacunku nie przedstawiać jako etykiety ani nie wymyślać braków.
- [ ] Rozważyć MCP, publiczne API/bazy i lokalny katalog. Najpierw wyszukiwanie strukturalne z minimalnymi wynikami dla modelu, internet gdy źródła nie wystarczą. Ocenić polskie produkty, jakość, licencje, przechowywanie/cache, limity, koszty i klucze; zachować źródło/datę i ręczny wybór lub wpisanie etykiety.
- [ ] Osobno uzgodnić AI i integracje: dostawca/model lokalny lub chmurowy, koszty, klucze i dane wysyłane na zewnątrz. Minimalizować zapytania, chronić prywatność, uwzględnić niedostępność źródeł i cache zgodny z licencją.
- [ ] Testy: wiele produktów/języków/jednostek, porcje i receptury, niejednoznaczność/braki/rozbieżne źródła, błędne odpowiedzi narzędzi, tokeny/koszty, prywatność i poprawki przed zapisem.

## FatSecret — osobny zakres importu

- [ ] Najpierw zweryfikować dostępność ręcznego eksportu CSV według [pomocy FatSecret](https://www.fatsecret.com/fatsecret-app-help/reports-page/use-export-or-print-function). Na wskazanej próbce zbadać daty, szczegółowość, format/jednostki, posiłki/porcje/kcal/makra i możliwość przeniesienia produktów/receptur. Raport nie musi zawierać pełnej bazy/historii; braków nie zgadywać.
- [ ] Oddzielnie ocenić [API dziennika](https://platform.fatsecret.com/docs/v2/food_entries.get): uwierzytelnienie istniejącego konta, dostępne funkcje, zakres historii, limity/opłaty, licencja/przechowywanie. Nie zakładać dostępu dla każdego konta/planu. Jednorazowy CSV i późniejsza synchronizacja API wymagają osobnych ustaleń.
- [ ] Plik/zakres lub konto, podgląd/mapowanie do dziennika/bazy, walidacja, błędy/braki, pochodzenie i stabilne ID tam, gdzie są; deduplikacja kolejnych importów i danych arkusza/Connect. Nie zapisywać automatycznie do Connect ani nie zmieniać FatSecret; kierunek, korekty/usunięcia/konflikty uzgodnić przed implementacją.

## Treningi i trasy

- [ ] Paginowana lista z filtrem daty/typu: czas, rodzaj/nazwa, aktywny czas, dystans/kcal/średnie tętno i dostępne pola. Szczegóły i jawny brak GPS; brakujące opcjonalne pola pozostają puste.
- [ ] Zweryfikować `exportExerciseTcx` z `alt=media` dla wybranego treningu. Zachować oryginalny TCX, metadane/źródło, dokładność czasu i dostępne współrzędne/wysokości/dystans/inne pola. Obsłużyć braki/pauzy GPS, granicę dnia/strefy, duże pliki i błędny XML, bez dopowiadania trasy.
- [ ] Ręczna historia z postępem/przerwaniem/wznowieniem, aktualizacja nowych/zmienionych treningów, trwałe ID konta/treningu i idempotencja. Osobno polityka usunięcia/zmiany w chmurze — brak wyniku/błąd nie usuwa lokalnej kopii.
- [ ] Interaktywna mapa: dopasowanie, początek/koniec, zoom/przesuwanie i szczegóły. Opcjonalne punkty/wykresy wysokości/tempa tylko z istniejących danych. Pusta trasa nie blokuje podsumowania.
- [ ] Dostawca: sprawdzić klucz, billing/limity/koszty Google Maps albo bibliotekę i dostawcę kafelków OSM z atrybucją i zgodnością zasad. OSM nie gwarantuje darmowego/dowolnego hostingu. Pobieranie kafelków ujawnia dostawcy obszar/IP; wyjaśnić przepływ, nie wysyłać całej trasy do dodatkowych usług. Offline tylko przy dozwolonych danych/dostawcy, bez założenia pobierania obszarów z publicznych kafelków OSM.
- [ ] Desktop OAuth przez przeglądarkę systemową, minimalne zgody aktywności/lokalizacji; Cloud, faktyczna dostępność API, wygaśnięcie Testing i zabezpieczone poświadczenia. Żadnych sekretów/tokenów w repo, HTML, logach i nazwach plików.
- [ ] Lokalne miejsce przechowywania, zabezpieczenie/kopie, usuwanie cache/historii i informacja o pozostających plikach. Bez publicznego udostępniania/analityki/dodatkowych usług tras. Eksport GPX/TCX/inne jako oddzielna opcja z zachowaniem oryginału.
- [ ] Zgody/sieć/limity, błędy pobrania, brak GPS i retry; osobne błędy Health i map, bez logowania współrzędnych/pomiarów. Syntetyczne testy paginacji/aktualizacji/przerwania/TCX/GPS/braków/DST/API, ochrony loopback, mapy i usuwania danych; rzeczywista trasa dopiero po skonfigurowaniu konta i zgodzie na dostęp.

Przed prototypem ustalić: pierwsze widoki/zakres, transfer z telefonu, rolę arkusza, repo/katalog, UI przeglądarkowe/natywne, technologię i wspólny kod, mapy, przechowywanie/eksport. Zacząć od ograniczonej próbki, bez obietnicy kompletności źródeł.
