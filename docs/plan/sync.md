# Rdzeń — utrzymanie i wydanie

[Indeks planu](README.md) · [Wykonane](../completed/core.md) · [Kontrakt danych](../data-contracts.md)

## Pozostałe prace

- [ ] Bezpiecznie obsłużyć usunięcia i zmianę daty pomiaru: minimalne mapowanie ID→dzień, pełny potwierdzony ponowny odczyt i dopiero ewentualne czyszczenie właściwej komórki. Brak zgody, pusty niepełny odczyt i błąd nigdy nie oznaczają usunięcia.
- [ ] Uzupełnić szczegółowe działania naprawcze w dzienniku; zachować kody HTTP, kierowanie do właściwej zakładki oraz rozróżnienie ostatniej próby od wcześniejszych błędów i zapisów do istniejących wierszy od rzeczywistych zmian wartości.
- [ ] Wykonać pozostałe próby urządzenia opisane w [wynikach testów](../test-results.md): restart/wznowienie, sieć, zgody, Picker, odtworzenie ustawień, duża historia, współbieżność i agregaty. Testów fikcyjnych nie traktować jako dowodu na danych użytkownika.
- [ ] Jeśli użytkownik potrzebuje wydania: powtarzalna instrukcja i podpisany release APK, bez kluczy podpisu w repozytorium.
- [ ] Nowe statystyki dodawać dopiero po ustaleniu użytecznej definicji dziennej, jednostek, zgód i zasad duplikacji źródeł; obecny katalog nie oznacza obsługi wszystkich typów Connect.

## Kryteria ukończenia

Każda zmiana definicji ma jawny plan migracji/reimportu, testy granic dat i braków; usunięcia nie niszczą danych przy utracie dostępu. Wyniki urządzenia podają dokładny scenariusz i zakres, a instrukcja wydania daje się powtórzyć bez prywatnych plików w Git.
