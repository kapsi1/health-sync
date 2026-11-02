# Wykonane — mobilny Takeout

Kod sprawdzony **2026-11-02**, A było autoryzowane. Kontrakt i decyzje: [takeout.md](../takeout.md); brakujące zadania: [plan A](../plan/takeout.md); obecne testy: [test-results](../test-results.md).

## Zaimplementowane

- Lokalny ZIP, daty/typy, walidacja/digest, odczyt i podgląd brakujących/istniejących/konfliktowych/nieobsługiwanych danych; osobne zatwierdzenie zapisu, wymagane zgody i nazwa konta źródłowego.
- Sen, waga/tłuszcz, tętno spoczynkowe, treningi, HRV, oddechy i temperatura skóry; osobny strumieniowy podgląd/zapis szacunków kroków, dystansu i kalorii. Wzrost wycofany z wyboru, zgodność dawnych sesji zachowana.
- Porównanie wszystkich dostępnych źródeł, ponowna kontrola po podglądzie i przed/po zapisie; trwały plan, ID/wersje/hash treści, zamiary i potwierdzenia w osobnej bazie Room. Odtwarzanie z planu zamiast ponownego wyboru źródeł.
- Wznowienie, anulowanie pozostałej części importu bez usuwania zapisanych rekordów i wycofanie własnych niezmienionych rekordów. Rekonsyliacja dotkniętych dat i standardowa kolejka Sheets; niepewne komórki arkusza pozostają.
- Reguły v1/v2 i rozłącznych v2; nocne podsumowania i pomijanie istniejącego dnia HRV; treningi opaska/autodetekcja; prywatne wykluczenie jednego treningu; kontrolowane szacunki aktywności. Szczegóły są w jednym [kontrakcie](../takeout.md).

## Potwierdzone i granice dowodów

| Dowód historyczny | Co potwierdza / czego nie potwierdza |
| --- | --- |
| Użytkownik: jedna sesja snu | Zapis do Connect, przeliczenie w Sheets i wycofanie z Connect; komórki zachowano. Nie dowodzi zgodności całej historii z Health. |
| Użytkownik: tętno spoczynkowe jednego dnia | Podgląd, zapis Connect, wznowienie rozpoznające dopisany offset bez duplikacji i wartość w arkuszu. Dalsze próby wycofywania pominięte na życzenie. |
| Telefon: rejestr zamiarów/potwierdzeń | Test na bazie w pamięci, z fikcyjnymi danymi; nie pełny import użytkownika. |
| Wykluczenie bieżni | Build/testy/instalacja i prywatne ustawienie zapisane; brak pełnej ręcznej próby importu i UI wykluczeń. |

## Ustalenia z historycznej analizy źródeł

To dowody dla badanego eksportu/urządzenia, **nie stan obecnego konta ani fixture'y**:

- Ponowne połączenie Health z Connect przywróciło odczyt wagi/tłuszczu/tętna; reinstalacja Health przywróciła historię chmurową i poprawiła bieżący eksport snu/wagi/tłuszczu. Część dawnego snu nadal nie trafiała do Connect, bez ustalonej przyczyny; Takeout zawierał brakującą historię.
- W porównanej nocy v2 granice odpowiadały Health, ale suma faz i wybudzeń różniła się; użytkownik świadomie wybrał v2. To nie dowód, że v2 zawsze odtwarza algorytm Health.
- HRV: 4274 rekordy w 48 dniach, w 47 dniach wiele wpisów; tylko 3 takie dni miały w Connect odpowiedni sen z fazami. Późniejsze pojedyncze wpisy Fitbit nie reprezentowały całej historii. Dlatego nie zastępuj istniejących próbek podsumowaniem.
- 48 jednoznacznych dni oddechów miało tę samą wartość w `daily_respiratory_rate` i podsumowaniu pełnego snu. Dla HRV/oddechów/temperatury w ostatniej próbce telefonu był pojedynczy wpis Fitbit na dzień; temperatury miały wartość i bazę z przedziałem nocy, ale nie wszystkie podsumowania Takeout miały bazę.
- `Daily Resting Heart Rate`: 82 wpisy Health i 4 pochodzące z Connect; wartości Health były ułamkowe, więc mapowanie do Connect zaokrągla BPM.
- Proste sumy opaski/kalorii nie odtwarzały Health. Reguła minutowa dopasowała zaokrąglony dystans jednego dnia, lecz zawyżyła kroki; porównanie kalorii 1–7 czerwca doprowadziło do dopuszczenia różnych wpisów tego samego źródła/minuty. Wynik pozostaje szacunkiem.
- Eksport zawierał też natlenienie, temperaturę/wzrost i inne kategorie. Liczba wierszy nie oznacza liczby braków; źródło, przedział i semantyka muszą być sprawdzone przed nowym typem. Priorytetami były liczniejsze treningi/HRV/natlenienie/oddechy/temperatura, bez przywracania szczegółowej synchronizacji tętna.
