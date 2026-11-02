# Google Cloud — konfiguracja dostępu do Sheets

Dotyczy obecnej aplikacji Android i zakresu `drive.file`; przyszłe Google Health API/desktop mają osobny plan. Sekwencję OAuth/Picker porównano z kodem i dokumentacją producenta **2026-11-02**; ustawień prywatnego projektu Cloud nie sprawdzano.

1. W [Google Cloud Console](https://console.cloud.google.com/) wybierz własny projekt. Włącz **Google Sheets API**, a do wyboru istniejącego pliku także **Google Picker API** w tym samym projekcie. Obecny wariant Android używa klienta OAuth Android, bez osobnego klucza API w aplikacji.
2. Skonfiguruj ekran zgody/odbiorców w Google Auth Platform. Dla konta spoza organizacji wybierz odbiorców zewnętrznych; w trybie testowym dodaj konto telefonu do użytkowników testowych. Tryb wewnętrzny ogranicza konta do organizacji i nie korzysta z listy testowej; polityki administratora mogą ograniczać dostęp. Nazwy sekcji i wymagane formularze mogą się zmieniać.
3. Utwórz klienta **Android**: pakiet `pl.healthsync` i **SHA-1 certyfikatu faktycznie instalowanego APK**, odczytany przez `./gradlew.bat signingReport`. Nie kopiuj dawnego odcisku z innego komputera. Debug/release i różne klucze wymagają odpowiednich klientów; odcisk jest publiczny, klucz prywatny nie trafia do Git.
4. Jeśli konfigurator wymaga deklaracji zakresów, dodaj `https://www.googleapis.com/auth/drive.file`.
5. W aplikacji wybierz **Sprawdź zgodę Google**, zaakceptuj na właściwym koncie, następnie utwórz arkusz lub wybierz istniejący przez Picker. Sam ID/URL nie nadaje dostępu.
6. Sprawdź zapis małego zakresu do `daily`. Osobno sprawdź pracę w tle i zgodę Health Connect na tło; pomyślny OAuth na pierwszym planie nie dowodzi działania workera.

Przy problemach sprawdź projekt/API, pakiet i certyfikat, konto/odbiorców oraz dostęp do pliku. Dla nieprawidłowych nagłówków/dat popraw strukturę zgodnie z [kontraktem Sheets](data-contracts.md); kolejka pozostaje do ponowienia. Wynik tworzenia pliku o niepewnym statusie wyjaśnij przed kolejną próbą, aby nie powielać arkuszy. Nie dodawaj kont, tokenów i kluczy do repozytorium.

Źródła producenta: [Google Picker dla desktop/mobile](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker), [autoryzacja Android](https://developer.android.com/identity/authorization), [Sheets create](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/create). Polecenia środowiska: [development.md](development.md).
