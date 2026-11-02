# A. Takeout → Health Connect — pozostałe prace

**Implementacja autoryzowana i częściowo zweryfikowana.** Parsery, podgląd, zapis, trwały plan, wznowienie, anulowanie i wycofanie są w kodzie; nie implementuj ich ponownie. [Wykonany zakres i dowody](../completed/takeout.md) · [kontrakt](../takeout.md) · [indeks](README.md).

## Weryfikacja istniejącej ścieżki

- [ ] Sprawdzić na małym uzgodnionym zbiorze: powtórny import, kolejne eksporty i konta, przerwanie po zapisie/restart, utratę zgody, zachowanie URI/digestu i ponowne przeliczenie dawnych dat. Następnie duży zakres po ręcznym porównaniu.
- [ ] Sen: większy zakres i porównanie faz/czasów z Connect/Health; rozłączne v2 w jednej grupie v1, pojedyncza v1, brakujące/zewnętrzne źródła, kolizje między aplikacjami. Nie obiecywać równości z podsumowaniem Health.
- [ ] HRV/oddechy/temperatura: próbny import i porównanie w Connect/Sheets, w tym gęste HRV, sesje przez północ i DST, brak bazy temperatury i kilka podsumowań. Sprawdzić też obecne agregacje nocne po zmianie katalogu 14 statystyk.
- [ ] Treningi: priorytet opaski wobec jednej/kilku sesji automatycznych, istniejące dopasowania z ułamkami sekund, pozostałe nakładania i trwałość wykluczeń. Nie wykluczać zachowanego automatycznego spaceru wskutek wykluczenia pojedynczej bieżni.
- [ ] Dzienne szacunki: ponownie porównać regułę minutową na reprezentatywnych dniach i jawnie pokazać różnice. Pierwsza ręczna próba zapisu jednego dnia i wyniku w Sheets; test kalorii po dopuszczeniu sumowania różnych czasów tego samego źródła w minucie. Zachować oznaczenie szacunku i pomijanie dni z danymi Connect.
- [ ] Potwierdzić na urządzeniu anulowanie dalszego importu, zachowanie już zapisanych rekordów i odświeżenie ich dat osobnym importem Connect. Sesji `cancelled` nie traktować jako wznowialnej/automatycznie wycofywalnej.

Dalsze **ręczne testy wycofywania** pominięto na życzenie użytkownika; nie dodawaj ich automatycznie do zakresu tych prób. Istniejące testy automatyczne pozostają.

## Brakujące mechanizmy

- [ ] Zaprojektować trwały rejestr własnych zapisów Sheets i zachowawczą kontrolę późniejszych edycji. Docelowo czyścić po wycofaniu tylko udowodnione własne, niezmienione komórki; przy braku dowodu zachować i ostrzec. Sama równość wartości i procesowy mutex nie zapewniają bezpieczeństwa; obecna ścieżka nie ma atomowego warunkowego czyszczenia.
- [ ] Rozpoznawać później dopisany oryginał Fitbit/Health i proponować zastąpienie własnej kopii, bez usuwania cudzych rekordów i bez polegania tylko na priorytetach Connect. Minimalne dane pochodzenia/wersji mają zapobiegać duplikacji i pętlom.
- [ ] Zdefiniować i przetestować mapowanie dostępnych metadanych urządzenia/metody zapisu. Obecne rekordy mają `unknownRecordingMethod`; `data_source` snu opisuje metodę, nie aplikację/urządzenie. Zachować `dataOrigin` Health Sync i nie odgadywać braków.
- [ ] Jeśli użytkownik potrzebuje zarządzania wykluczeniami: uzgodnić UI i odtworzenie po wyczyszczeniu danych. Obecny prywatny zbiór stabilnych ID działa bez interfejsu.
- [ ] Kolejne typy rozważać po zdefiniowaniu mapowania i ocenie liczby rzeczywiście brakujących, bezpiecznych rekordów; sama obecność 1–2 wpisów w eksporcie nie uzasadnia wdrożenia. Nie przywracać usuniętej szczegółowej synchronizacji tętna; wzrost zachować tylko dla dawnych sesji.

## Kryteria ukończenia

Podgląd i zapis stosują te same decyzje; wznowienie zachowuje zatwierdzony plan. Import jest idempotentny mimo niejednoznacznego wyniku zapisu; konflikty i braki są widoczne. Próbka faktycznie dociera przez Connect→Room→Sheets, a ograniczenia poprawności i niewykonane scenariusze są zapisane w [test-results](../test-results.md).
