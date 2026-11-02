# D. Odżywianie z arkusza → Connect

**Brak implementacji; przed rozpoczęciem osobna zgoda, wskazany arkusz i uzgodnienie struktury.** Wariant przejściowy/opcjonalny wobec [dashboardu E](dashboard.md). Najpierw ustalić źródło prawdy i kierunek synchronizacji; rola Connect dla żywienia wymaga osobnej decyzji. [Indeks](README.md).

- [ ] Wybrać arkusz przez Picker/ID/link z faktycznym dostępem; zakładka/range i mapowanie dat, ID, kolumn, jednostek, posiłków lub sum. W wariancie arkuszowym źródłem zaimportowanych wpisów jest arkusz, Connect centralnym magazynem; nie modyfikować źródła ani jego formuł.
- [ ] Czytać wyniki formuł jako liczby, odróżniać błędy i puste od zera. Walidować kcal/kJ, g/mg, daty i dodatnie ilości. Sama data nie daje godziny posiłku.
- [ ] Podstawy: data/czas, trwałe ID, opcjonalna nazwa/typ, energia spożyta, białko, węglowodany, tłuszcz. Opcjonalnie błonnik, cukry, rodzaje tłuszczów, cholesterol, kofeina, witaminy i minerały; nawodnienie osobno jako `HydrationRecord`. Energia spożyta nie jest energią spaloną.
- [ ] Wybrać posiłki **albo** sumę dobową, nigdy oba poziomy naraz. Jeśli brak ID, uzgodnić nadanie lub klucz dzienny; numer wiersza nie jest tożsamością, sortowanie nie może duplikować rekordów.
- [ ] `WRITE_NUTRITION` i opcjonalnie nawodnienie; podgląd przed zapisem, stabilne ID/wersje, wznowienie i pochodzenie. Korekty aktualizują tylko własne rekordy, z ochroną przed duplikatami innych aplikacji/ręcznych wpisów.
- [ ] Uzgodnić politykę usunięć; rozpoznawać je dopiero po pełnym udanym odczycie zakresu. Brak dostępu, pusty/błędny/niepełny wynik nie kasuje danych; usuwać tylko własne rekordy.
- [ ] Niezależny przycisk, harmonogram, ostatni sukces/błąd; interwał po ocenie arkusza, bez domyślnego wiązania z nocnym API. Zapis Connect nie czeka na chmurę Health; osobno zbadać, co Health wyświetla.
- [ ] Ewentualny późniejszy eksport żywienia do `daily` czyta Connect jako wybraną statystykę; nie zapisuje zwrotnie do arkusza źródłowego ani do Connect. Connect, API i żywienie mają niezależne ustawienia/checkpointy.
- [ ] Testy: próbka i korekta, sortowanie/usunięcie wierszy, posiłki kontra sumy, formuły, jednostki/strefy, sieć/zgody, ponowienie po zapisie i restarcie, ochrona obcych wpisów i wycofanie własnej partii.
