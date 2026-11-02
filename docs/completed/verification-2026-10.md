# Weryfikacja historyczna — październik 2026

Skrócony zapis dawnych `feasibility.md` i `test-results.md`. **Nie jest wynikiem aktualnego uruchomienia.** Aktualne definicje: [dane](../data-contracts.md), [Takeout](../takeout.md); najnowsza weryfikacja i otwarte próby: [test-results](../test-results.md). Liczby opisują różne zestawy, nie należy ich sumować.

## M0 i kolejne etapy (od 28.10)

- 28.10: `assembleDebug`, instalacja i start APK na Pixelu przez ADB; użytkownik potwierdził diagnostykę. Diagnostyka sprawdzała Connect, historię/tło i próbki kroków/kcal z 30 dni, z listą źródeł tylko pierwszych maks. 100 rekordów — nie był to pełny import; dokładnych wyników nie przekazano.
- Kolejne etapy: 8 metryk/wybór DataStore i zgody potwierdzone; 7-dniowy podgląd nie był jeszcze porównany z Connect. Zgłoszony brak masy w Health Connect ustąpił po ponownym połączeniu Google Health; luki starej historii potwierdzono w Takeout. Wczesne blokady Takeout i brak tła/importu dotyczyły ówczesnego etapu, obecnie są nieaktualne.
- Wczesny sen oznaczał całą sesję (także czuwanie); testy północy/DST/konfliktu przeszły. Ta definicja jest zastąpiona fazami snu i nie może być użyta do nowego kodu.
- Room: 4 testy na Pixelu, baza w pamięci — idempotencja, zero, pusty odczyt i rollback. Wtedy trwały importer nie był jeszcze podłączony. Później dodano historię miesięczną/checkpointy, ręczny zapis Sheets i WorkManager; pierwotne zastrzeżenia „niezaimplementowane” nie opisują obecnego kodu.
- OAuth pierwszoplanowy zadziałał po ustawieniu zewnętrznych odbiorców; początkowo AuthorizationClient w workerze był niesprawdzony. Użytkownik później potwierdził prawdziwy arkusz, ręczny zapis, tło, kolumny, historię, zmianę ID i sortowanie. Test przywrócenia po wyczyszczeniu danych pozostał otwarty.
- Zmiany wagi/tłuszczu najpierw tylko sygnalizowały wygasły token; później dodano porcjową rekonsyliację. Usunięcia i zmiany dat nadal nie są automatycznie rozliczane.
- Rozszerzony dawny katalog obejmował m.in. piętra, tętno min/max, wzrost, nawodnienie, VO₂ max i beztłuszczową masę. Te pozycje później usunięto z aktywnego wyboru; dawnych prób nie przypisuj nowemu katalogowi.

## Zachowane wyniki automatyczne i instalacje

Polecenia 29.10: `testDebugUnitTest assembleDebug --offline --no-daemon`; gdzie zaznaczono „test APK”, także `assembleDebugAndroidTest`. Przy tych zmianach debug APK budowano i instalowano przez `adb install -r`; instalacja nie dowodzi porównania danych.

| Zakres | Wynik zapisany w dawnej dokumentacji | Ograniczenie w chwili próby |
| --- | --- | --- |
| Zestaw instrumentacyjny przed migracją do 5 | 16 testów: Room, sesje, migracje 1/2/3→4, dziennik i sortowanie; przez AndroidJUnitRunner/ADB, ponieważ offline runner Gradle nie miał jednej zależności | Fikcyjne dane i osobne bazy |
| 29.10 — polskie nagłówki | 54 jednostkowe + build; stare/nowe nazwy, kolejność, obce kolumny, ukrywanie, powtórzenie, kolizje i formuła nagłówka | Rzeczywista migracja wtedy do próby; wcześniejsze nazwy później potwierdzone |
| 29.10 — sen z faz i wybudzenia | 49 jednostkowych + build; krótkie/długie wybudzenia, braki/UNKNOWN/luki/brzegi, niepełna doba, wiele sesji, nakładanie/wyjście poza sesję, DST i format tekstu | Po ponownym podłączeniu telefonu APK wgrano; większy zakres snu nieporównany |
| 29.10 — format eksportu i taby | 41 jednostkowych + build; kcal, dystans, waga, połowy/ujemne/całkowite, NaN, czasy ćwiczeń/snu | Wygląd i zapis rzeczywistego arkusza do sprawdzenia |
| 29.10 — krótsze nazwy/kcal | Jednostkowe + build/test APK; aliasy/kolizje i zaokrąglanie połówek; 4 instrumentacyjne Sheets | Porównanie na arkuszu do próby |
| 29.10 — średnie dzienne | Jednostkowe + build/test APK; wiele próbek, braki, dzień warszawski/DST, nowe i stare kolumny; 7 instrumentacyjnych, w tym migracje baz 1–4→5 i formatowanie | Uzupełnienie historii i średnie na rzeczywistym arkuszu do próby |
| 29.10 — automatyczny format liczb | Jednostkowe + build, APK wgrano; test struktury resetu formatu dodany | Ten nowy test instrumentacyjny jeszcze nieuruchomiony; wygląd później potwierdzony |
| 29.10 — jednostki w nazwach | Jednostkowe + build/test APK; aliasy wagi/dystansu; 4 instrumentacyjne `SheetsCreatorTest` | Najnowsze etykiety/migracja na rzeczywistym arkuszu do próby |

Zestawy domenowe obejmowały też porcje historii/wznowienie następnego dnia, zmiany i odzyskanie tokenu, scalanie Sheets, rewizję kolejki, timeout po append, liczniki i sortowanie po błędzie bez duplikatów. Test struktury sortowania przeszedł na urządzeniu. Dowody późniejszego Takeout i decyzje źródłowe zebrano w [completed/takeout.md](takeout.md).
