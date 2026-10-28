# Wyniki i zakres testów

## Potwierdzone na telefonie

Użytkownik potwierdził odczyt kroków, kalorii, dystansu, wagi, tkanki tłuszczowej i średniego tętna oraz zapis dat i wartości w Google Sheets. Potwierdził też synchronizację automatyczną, ukrywanie i odkrywanie kolumn, uzupełnianie historii, zmianę arkusza przez ID oraz wznowienie ręcznego importu po przerwaniu. Rzeczywista dostępność starszych danych zależy od danych zapisanych w Health Connect.

Testy instrumentacyjne uruchomione przez ADB przeszły: **13 testów**, obejmujących Room, sesje importu i migracje z wersji 1, 2 i 3 do wersji 4. Testy wykorzystują oddzielne bazy i fikcyjne dane. Runner Gradle w trybie offline nie miał w pamięci podręcznej jednej z zależności, więc testy uruchomiono bezpośrednio przez AndroidJUnitRunner.

## Testy jednostkowe

Projekt obejmuje m.in. dzielenie historii na porcje, wznowienie po błędzie i następnego dnia, odczyt zmian i odzyskanie po wygaśnięciu tokenu, reguły snu, łączenie danych z arkuszem, zachowanie kolejki po zmianie obserwacji i ponowienie po niejednoznacznym zapisie. Liczniki wysyłki sprawdzają dodawanie i zapis istniejących wierszy, w tym ponowienie po timeout.

Polecenie: `./gradlew.bat testDebugUnitTest assembleDebug`. Testy instrumentacyjne wymagają przygotowanego SDK, podłączonego urządzenia i zależności Gradle lub uruchomienia zbudowanego APK testowego przez ADB.

## Jeszcze do sprawdzenia

Wznowienie po zamknięciu i ponownym otwarciu aplikacji na rzeczywistych danych; cofnięcie i ponowne przyznanie zgód; brak internetu i przywrócenie łączności; przywrócenie po wyczyszczeniu danych aplikacji; porównanie agregatów dla zmian czasu i wielu źródeł. Nie traktować wyników testów fikcyjnych jako potwierdzenia tych scenariuszy na danych użytkownika.
