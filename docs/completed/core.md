# Wykonane — rdzeń aplikacji

Kod sprawdzony **2026-11-02**. To wykonany zakres, bez deklaracji pełnej weryfikacji telefonu. Reguły: [kontrakty](../data-contracts.md); pliki: [architektura](../architecture.md); pozostałe prace: [plan](../plan/sync.md).

- UI: Sync/Dane/Arkusz/Dziennik, logo/ikona, ciemny motyw, kolory dynamiczne Android 12+ i awaryjne, większe podpisy; grupy, liczniki i częściowy wybór 14 metryk.
- Historia: wybieracz/zapis daty, import+wysyłka, postęp we wszystkich zakładkach, przerwanie i trwałe wznowienie; atomowy reimport, Room/checkpointy/kolejka i backfill nowych metryk.
- Dane: agregaty Connect, sen z faz/wybudzenia i parametry nocne, jawne migracje zamiast reinterpretacji starych wyników.
- Sheets: OAuth/tworzenie/otwarcie/dostęp, ID/URL/Picker; przy zmianie pliku cała historia albo kolejka, ochrona niepewnego tworzenia; zapis po dacie, sortowanie pełnych wierszy, widoczność, aliasy/nagłówki i migracje formatów.
- Tło: WorkManager opt-in 1/3/12/24 h, ostatnie 3 dni, zmiany wagi/tłuszczu i porcjowe odzyskanie po wygaśnięciu tokenu.
- Dziennik: ręczny/auto, etapy/retry, błędy/kopiowanie bez pomiarów/tokenów, zachowane dawne wpisy; wynik ostatniej próby, HTTP/cel naprawy i liczniki dodanych/istniejących wierszy.

Użytkownik potwierdził: odczyt kroków/kcal/dystansu/wagi/tłuszczu i dawnego średniego tętna; ręczny zapis i tło, podstawowy wybór/UI, pasek/przerwanie/wznowienie, zmianę/otwieranie arkusza i Picker, sortowanie/kolumny/backfill, wcześniejsze polskie nagłówki, poprawkę końcowego przecinka, liczniki i czas wybudzeń. To nie potwierdza nowego katalogu, wszystkich definicji ani restartu przed wznowieniem.

Dowody automatyczne i dokładne luki: [test-results](../test-results.md), [historyczne próby](verification-2026-10.md). Takeout: [osobny wykaz](takeout.md). API/desktop/żywienie nie są wykonane.
