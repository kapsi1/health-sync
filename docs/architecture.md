# Architektura i mapa kodu

Stan kodu sprawdzony: **2026-11-02**. Jeden moduł Android `:app`, pakiet `pl.healthsync`; brak backendu i modułu desktopowego/API. Kotlin + Compose/Material 3, Health Connect, Room, DataStore, WorkManager i Google Identity/Sheets. Wersje: [app/build.gradle.kts](../app/build.gradle.kts), [build.gradle.kts](../build.gradle.kts).

## Przepływy

```text
MainActivity / BackgroundSyncWorker
  → HealthHistorySource → HistoryImport → RoomHistoryStore → SyncDatabase
  → RoomPendingQueue → SheetSync → SheetGatewayHttp → Google Sheets/daily

TakeoutPreviewScreen → lokalny ZIP → parsery → Inspector → podgląd
  → zatwierdzenie → ImportService → trwały plan w TakeoutJournalDatabase
  → BatchWriteCoordinator → HealthConnectTakeout…Gateway → Health Connect
  → ponowny odczyt dotkniętych dat przez HistoryImport → zwykła kolejka Sheets
```

Takeout jest ręczną operacją; worker go nie uruchamia. Dane zapisane w chmurze Google Health, których nie ma w Connect, nie są automatycznie pobierane. Przyszłe źródła: [plan](plan/README.md).

## Gdzie zmieniać kod

Wszystkie poniższe ścieżki są względem [app/src/main/java/pl/healthsync](../app/src/main/java/pl/healthsync/).

| Obszar | Pliki / symbole i odpowiedzialność |
| --- | --- |
| UI i składanie zależności | `MainActivity.kt`: Sync, Dane, Arkusz, Dziennik, ręczny import, zgody; `TakeoutPreviewScreen.kt`: lokalny ZIP, analiza, zapis i sesje; `GoogleAuthorization.kt`: OAuth, Picker, wybór/tworzenie pliku i wysyłka |
| Katalog i wybór | `MetricRegistry.kt`: ID, etykiety, jednostki, kolumny, aliasy i zgody; `MetricSelection.kt`: wybór i zlecenia backfill; `data/SelectedMetricBackfill.kt`: ich realizacja |
| Odczyt Connect | `data/HealthHistorySource.kt`: agregaty i paginowane rekordy; `DailyPreview.kt`: ograniczony podgląd, nie trwały import |
| Reguły agregacji | `SleepDaily.kt`, `SleepRecordMapping.kt`, `BodyFatDaily.kt`, `domain/SleepRespiratory.kt`; definicje w [kontraktach](data-contracts.md) |
| Historia i zmiany | `domain/HistoryImport.kt`: miesięczne porcje, checkpointy, wznowienie; `domain/ChangesScan.kt`, `ChangesRecovery.kt` + `data/HealthChanges.kt`: zmiany wagi/tłuszczu i odtwarzanie po wygaśnięciu tokenu |
| Lokalny zapis i wysyłka | `data/SyncDatabase.kt`: encje/DAO/migracje; `RoomHistoryStore.kt`: transakcje importu; `RoomPendingQueue.kt`: wybór komórek i rewizje |
| Sheets | `domain/SheetSync.kt`: orkiestracja snapshot → plan → zapis → sortowanie → potwierdzenie; `SheetMerge.kt`, `SheetHeaders.kt`, `SheetColumns.kt`, `SheetDisplay.kt`: czyste reguły; `data/SheetGatewayHttp.kt`, `SheetsCreator.kt`, `SheetSettings.kt`: HTTP, plik i ustawienia |
| Automatyzacja | `BackgroundSync.kt`: ustawienia i `BackgroundSyncWorker`; ostatnie 3 dni (dziś i dwa poprzednie), backfill, zmiany historyczne i kolejka |
| Dziennik i obsługa błędów | `SyncJournal.kt`, `BackgroundLog.kt`, `RecoveryDestination.kt`; bez wartości zdrowotnych/tokenów; HTTP i cel naprawy |
| Takeout: odczyt i decyzje | `domain/TakeoutArchive.kt`, `TakeoutCsv.kt`, `TakeoutRecords.kt`, `TakeoutCompare.kt`, `TakeoutExercises.kt`, `TakeoutNightly.kt`; aktywność dzienna: `TakeoutActivity*.kt`; `data/TakeoutFile.kt`, `TakeoutInspector.kt`, `TakeoutActivityInspector.kt` |
| Takeout: zapis i odtwarzanie | `data/TakeoutImportService.kt`, `TakeoutActivityImportService.kt`: operacje; `TakeoutRecordMapping.kt`: mapowanie do Connect; `TakeoutPlan.kt`: rekonstrukcja zamrożonego planu i daty do przeliczenia |
| Takeout: gwarancje zapisu | `domain/TakeoutBatchWriteCoordinator.kt`, `TakeoutWriteCoordinator.kt`, `TakeoutUndoCoordinator.kt`: porty i koordynacja; `data/HealthConnectTakeoutGateway.kt`, `HealthConnectTakeoutActivityGateway.kt`: kontrola rzeczywistych rekordów; `TakeoutJournalDatabase.kt`: rejestr zamiarów i potwierdzeń |

`domain/` skupia logikę i porty testowane na JVM; `data/` implementuje Android/Room/HTTP. Część czystych reguł nadal leży w pakiecie głównym. Nie jest to osobny moduł Gradle; przy małej poprawce zachowaj istniejący układ.

## Trwały stan

| Magazyn | Zawartość / punkt dostępu |
| --- | --- |
| Room `health-sync.db`, wersja 5 | `DailyObservation` (dzień + metricId), `ImportCheckpoint`, `UploadQueue`, `ManualImportSession`; `SyncDatabase` |
| Room `takeout-import-journal.db`, wersja 2 | Sesje, digest ZIP, hash konta, zamrożone ID/hash treści, potwierdzone ID Connect i czas modyfikacji; `TakeoutJournalDatabase` |
| DataStore `settings` | Wybrane statystyki i zlecenia uzupełniania historii; `MetricSelection` |
| DataStore `sheet_settings`, `background_sync`, `health_changes`, `sync_journal` | Wybrany plik i niepewne utworzenie; interwał/status; tokeny/checkpointy zmian; dziennik synchronizacji |
| SharedPreferences `takeout_settings`, `takeout_exclusions` | Nazwa konta źródłowego (`source_account`) i wykluczenia ćwiczeń (`exercise_ids`); brak UI do zarządzania wykluczeniami |

Migracje Sync 1→2→3→4→5 i Takeout 1→2 są jawne. Sync 4→5 archiwizuje dawne lokalne „ostatnie pomiary” jako `legacy_last:…`; nowe znaczenie wymaga ponownego importu. Nie zmieniaj semantyki starych wartości przez samą zmianę etykiety.

## Transakcje, wznowienie i współbieżność

- `HistoryImport`: globalny `historyImportLock`; `commitChunk` zapisuje obserwacje, checkpoint i kolejkę transakcyjnie. `prepareManualSession` atomowo rozpoczyna pełne przeliczenie albo wznawia pasującą sesję. Nowe uruchomienie po ukończeniu ponownie odczytuje zakres.
- `sheetOperationLock` w `GoogleAuthorization.kt` serializuje w jednym procesie operacje ręczne, worker, zmianę pliku oraz zapis/wycofanie Takeout i przeliczenie. `takeoutSessionLock` serializuje operacje Takeout; serwisy pozyskują go przed blokadą arkusza. Koordynatory mają również własne mutexy. Nie wprowadzaj odwrotnej kolejności ani ponownego wejścia do tej samej blokady.
- `SheetSync` czyta aktualny snapshot przed każdą porcją (domyślnie 100 dni), zapisuje i sortuje całe wiersze. `RoomPendingQueue` potwierdza tylko niezmienioną rewizję; zmiana wyboru statystyk przerywa wysyłkę. Timeout po zapisie wymaga nowego odczytu arkusza przed ponowieniem.
- Mutex telefonu nie chroni przed edycją arkusza z innego klienta. Obecna ścieżka aktualizuje zarządzane komórki; nie ma pełnego rejestru pochodzenia i atomowego warunkowego czyszczenia komórek.
- Takeout zapisuje plan przed skutkiem zewnętrznym, sprawdza rekordy przed i po zapisie, potwierdza ID i treść. Wznowienie używa tego samego ZIP i zapisanego planu; szczegóły i rozróżnienie przerwania/anulowania/wycofania: [takeout.md](takeout.md).

## Dostęp i granice

Zgody Health Connect obejmują wybrane typy oraz osobno historię i tło. OAuth Google używa `drive.file`; ID/link nie nadaje dostępu, Picker może go nadać. Token jest uzyskiwany przez Google Identity, bez stałego sekretu w APK. Tworzenie pliku z niejednoznacznym wynikiem blokuje kolejną próbę do wyjaśnienia (`creationPending`).

Usunięcia oraz zmiany dat pomiarów nie są automatycznie rozliczane w Sheets. Standardowy pusty odczyt zachowuje stare dane; wycofanie Takeout ma osobną rekonsyliację lokalną, ale zachowuje niepewne komórki arkusza. Pełne definicje: [dane](data-contracts.md), [Takeout](takeout.md). Dowody testowe i pozostałe scenariusze: [test-results.md](test-results.md).
