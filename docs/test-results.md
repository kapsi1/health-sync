# Wyniki i zakres testów

## Potwierdzone na telefonie

Użytkownik potwierdził odczyt kroków, kalorii, dystansu, wagi, tkanki tłuszczowej i średniego tętna oraz zapis dat i wartości w Google Sheets. Potwierdził też synchronizację automatyczną, ukrywanie i odkrywanie kolumn, uzupełnianie historii, zmianę arkusza przez ID oraz wznowienie ręcznego importu po przerwaniu. Rzeczywista dostępność starszych danych zależy od danych zapisanych w Health Connect.

Testy instrumentacyjne uruchomione przez ADB przeszły: **16 testów**, obejmujących Room, sesje importu, migracje z wersji 1, 2 i 3 do wersji 4, zgodność dziennika i strukturę żądania sortowania. Testy wykorzystują oddzielne bazy i fikcyjne dane. Runner Gradle w trybie offline nie miał w pamięci podręcznej jednej z zależności, więc testy uruchomiono bezpośrednio przez AndroidJUnitRunner.

## Testy jednostkowe

29.10.2026 (jednostki w nagłówkach): build `testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --no-daemon` przeszedł. Test jednostkowy weryfikuje rozpoznawanie wcześniejszych nazw wagi i dystansu oraz nowe etykiety; na telefonie przeszły 4 testy instrumentacyjne `SheetsCreatorTest`. APK zainstalowano; prawdziwy arkusz oczekuje sprawdzenia.

29.10.2026 (krótsze nazwy i kalorie): build `testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --no-daemon` przeszedł. Testy jednostkowe sprawdzają rozpoznawanie poprzednich polskich nagłówków i ich kolizji oraz zaokrąglanie kalorii do liczb całkowitych, w tym połowy. Na telefonie przeszły 4 testy instrumentacyjne żądań Sheets i tworzenia arkusza, APK zainstalowano; porównanie na prawdziwym arkuszu pozostaje do wykonania.

29.10.2026 (średnie dzienne): build `testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --no-daemon` przeszedł. Testy jednostkowe obejmują średnią z wielu pomiarów, brak danych, datę warszawską/DST oraz rozdzielenie nowych kolumn średnich od ukrytych dawnych kolumn. Na telefonie przeszło 7 testów instrumentacyjnych, w tym migracje z baz 1–4 do 5 z fikcyjnymi danymi i zachowaniem dawnych wyników, oraz struktura żądania formatowania. APK zainstalowano, porównanie średnich i uzupełnienie historii w prawdziwym arkuszu wymaga sprawdzenia użytkownika.

29.10.2026 (format liczb): usunięto wymuszony `0.##`, gdyż Google Sheets wyświetla separator nawet dla liczb całkowitych; aplikacja resetuje wyłącznie format liczbowy swoich kolumn i pozostawia liczby zaokrąglone w kodzie. Build i testy jednostkowe przeszły, APK wgrano na telefon. Dodano test instrumentacyjny struktury żądania resetującego format — jeszcze nie został uruchomiony. Weryfikacja rzeczywistego arkusza pozostaje do wykonania.

29.10.2026 (polskie nagłówki): `testDebugUnitTest assembleDebug --offline --no-daemon` zakończone sukcesem, **54 testy jednostkowe**. Testy mapowania obejmują stare i polskie nazwy, zmianę kolejności, zachowanie własnych kolumn, ukrywanie, powtórzenie migracji, kolizję obu nazw oraz nagłówek utworzony formułą. Debug APK zainstalowano na telefonie przez `adb install -r`; rzeczywista migracja istniejącego arkusza oczekuje testu użytkownika.

29.10.2026 (poprawka snu): `testDebugUnitTest assembleDebug --offline --no-daemon` zakończone sukcesem, **49 testów jednostkowych**. Sen jest liczony z faz bez czuwania, osobny czas wybudzeń wyklucza początkowe i końcowe czuwanie i nie stosuje progu ponownego snu. Testy obejmują krótkie i długie wybudzenia, brak faz, UNKNOWN i luki, nieznane brzegi, niepełną dobę, kilka sesji bez doliczania przerwy, nakładające się lub wychodzące poza sesję fazy, DST i tekstowy format wybudzeń. Po ponownym podłączeniu telefonu aktualizacja została wgrana przez `adb install -r`; porównanie na rzeczywistych danych oczekuje testu użytkownika.

29.10.2026: `testDebugUnitTest assembleDebug --offline --no-daemon` zakończone sukcesem, **41 testów jednostkowych**. Eksport obejmuje zaokrąglanie liczb przed wysłaniem (kcal, dystans, średnia waga z długą częścią ułamkową, próg połowy, liczby ujemne i całkowite), odrzucenie NaN i format tekstowy obu czasów: ćwiczeń i snu. Pasek tabów ma własny wiersz bez górnego odstępu Material NavigationBar; wygląd i zapis do rzeczywistego arkusza wymagają potwierdzenia użytkownika.

Projekt obejmuje m.in. dzielenie historii na porcje, wznowienie po błędzie i następnego dnia, odczyt zmian i odzyskanie po wygaśnięciu tokenu, reguły snu, łączenie danych z arkuszem, zachowanie kolejki po zmianie obserwacji i ponowienie po niejednoznacznym zapisie. Liczniki wysyłki sprawdzają dodawanie i zapis istniejących wierszy, w tym ponowienie po timeout. Sortowanie ma testy jednostkowe: dodanie starszych dat, niepowodzenie sortowania i ponowienie bez duplikatów oraz pustą kolejkę. Test struktury żądania sortowania przeszedł na urządzeniu; użytkownik potwierdził sortowanie w prawdziwym arkuszu.

Polecenie: `./gradlew.bat testDebugUnitTest assembleDebug`. Testy instrumentacyjne wymagają przygotowanego SDK, podłączonego urządzenia i zależności Gradle lub uruchomienia zbudowanego APK testowego przez ADB.

## Jeszcze do sprawdzenia

Wznowienie po zamknięciu i ponownym otwarciu aplikacji na rzeczywistych danych; cofnięcie i ponowne przyznanie zgód; brak internetu i przywrócenie łączności; przywrócenie po wyczyszczeniu danych aplikacji; porównanie agregatów dla zmian czasu i wielu źródeł. Nie traktować wyników testów fikcyjnych jako potwierdzenia tych scenariuszy na danych użytkownika.
