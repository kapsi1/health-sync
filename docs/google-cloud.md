# Google Cloud — konfiguracja testu OAuth

Aplikacja korzysta z `drive.file`, tworzy lub podłącza arkusz i synchronizuje do niego wybrane dane zdrowotne. Konfiguracja jest prywatna; nie dodawaj kluczy, tokenów ani danych konta do repozytorium.

1. W [Google Cloud Console](https://console.cloud.google.com/) utwórz lub wybierz własny projekt.
2. W **APIs & Services → Library** włącz **Google Sheets API**.
3. W **Google Auth Platform** skonfiguruj ekran zgody oraz grupę odbiorców. Tryb **wewnętrzny** pozwala na autoryzację tylko kontom należącym do organizacji projektu. Jeśli używasz konta spoza organizacji, wybierz typ **zewnętrzny**; w statusie testowym dodaj konto używane na telefonie jako użytkownika testowego. W trybie wewnętrznym lista użytkowników testowych nie jest używana; polityki administratora mogą dodatkowo ograniczać dostęp. Przygotuj dane wymagane przez aktualny formularz Google.
4. W **Clients → Create client** wybierz typ **Android** i wpisz nazwę pakietu `pl.healthsync` oraz odcisk SHA-1 certyfikatu bieżącej wersji debug:

   `49:0A:7D:62:32:60:96:03:69:F4:93:FA:7A:46:86:5A:64:FD:E9:F6`

   To publiczny odcisk certyfikatu aktualnie zbudowanego APK, nie klucz prywatny. Po zmianie klucza debug lub komputera odcisk może być inny; wersja release potrzebuje osobnego klienta dla swojego certyfikatu.
5. W sekcji **Data Access** dodaj zakres `https://www.googleapis.com/auth/drive.file`, jeżeli wymaga tego konfigurator Google.
6. Na telefonie uruchom aplikację i wybierz **Sprawdź zgodę Google**. Potwierdź wymagane zgody na właściwym koncie. Wynik testu nie dowodzi jeszcze działania Sheets ani pracy w tle.

Źródła: [autoryzacja Android](https://developer.android.com/identity/authorization), [Sheets API create](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/create).
