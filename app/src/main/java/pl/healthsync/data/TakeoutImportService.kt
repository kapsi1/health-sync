package pl.healthsync.data

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.healthsync.domain.HistoryImport
import pl.healthsync.domain.TakeoutBatchWriteCoordinator
import pl.healthsync.domain.TakeoutUndoCoordinator
import pl.healthsync.sheetOperationLock
import pl.healthsync.SyncJournal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun takeoutWritePermissions(types: Set<TakeoutType>): Set<String> = buildSet {
    addAll(takeoutReadPermissions(types))
    if (TakeoutType.SLEEP in types) add(HealthPermission.getWritePermission(SleepSessionRecord::class))
    if (TakeoutType.WEIGHT in types) add(HealthPermission.getWritePermission(WeightRecord::class))
    if (TakeoutType.FAT in types) add(HealthPermission.getWritePermission(BodyFatRecord::class))
    if (TakeoutType.HEIGHT in types) add(HealthPermission.getWritePermission(HeightRecord::class))
    if (TakeoutType.RESTING_HEART_RATE in types) add(HealthPermission.getWritePermission(RestingHeartRateRecord::class))
    if (TakeoutType.EXERCISE in types) add(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
    if (TakeoutType.HRV in types) add(HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class))
    if (TakeoutType.BREATHING in types) add(HealthPermission.getWritePermission(RespiratoryRateRecord::class))
    if (TakeoutType.SKIN_TEMPERATURE in types) add(HealthPermission.getWritePermission(SkinTemperatureRecord::class))
}

internal val takeoutSessionLock = Mutex()

/** All writes require a user-reviewed preview and a durable plan; never invoked by background sync. */
internal class TakeoutImportService(private val context: Context, private val client: HealthConnectClient) {
    private val dao = TakeoutJournalDatabase.get(context).journal()
    private val journal = SyncJournal(context)

    suspend fun start(uri: Uri, originalDigest: String, types: Set<TakeoutType>, start: LocalDate, end: LocalDate,
                      accountLabel: String, reviewed: TakeoutPreview, progress: (Int, Int) -> Unit): Int = takeoutSessionLock.withLock {
        check(dao.unfinishedSessions().isEmpty()) { "Istnieje przerwana operacja Takeout. Najpierw ją wznów." }
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutWritePermissions(types))) { "Brakuje zgody na odczyt lub zapis wybranych danych." }
        val key = takeoutAccountKey(accountLabel)
        val verified = readVerifiedTakeout(context, uri)
        check(verified.digest == originalDigest) { "Archiwum zmieniło się. Przeprowadź analizę ponownie." }
        val now = TakeoutInspector(client).inspect(verified.archive, types, start, end)
        val reviewedPlan = reviewed.candidates.map { mapTakeoutSelection(it, key) }
        val currentPlan = now.candidates.map { mapTakeoutSelection(it, key) }
        val originalCandidates = reviewedPlan.map { takeoutPayloadCandidate(it.record) }.toSet()
        val currentCandidates = currentPlan.map { takeoutPayloadCandidate(it.record) }.toSet()
        check(currentPlan.isNotEmpty() && originalCandidates.size == reviewedPlan.size && currentCandidates.size == currentPlan.size && originalCandidates == currentCandidates) {
            "Dane zmieniły się od podglądu. Porównaj je ponownie."
        }
        val id = UUID.randomUUID().toString()
        val session = TakeoutImportSession(id, uri.toString(), verified.digest, key, start.toString(), end.toString(),
            types.map { it.name }.sorted().joinToString(","), Instant.now().toString(), "active")
        dao.createPlan(session, currentPlan.map { takeoutPayloadCandidate(it.record) })
        execute(session, currentPlan, progress)
    }

    suspend fun resume(sessionId: String, progress: (Int, Int) -> Unit): Int = takeoutSessionLock.withLock {
        val session = dao.session(sessionId) ?: error("Nie znaleziono sesji importu")
        check(session.status == "active") { "Import nie jest aktywny" }
        val types = session.selectedTypes.split(",").map { TakeoutType.valueOf(it) }.toSet()
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutWritePermissions(types))) { "Brakuje zgody na odczyt lub zapis wybranych danych." }
        val verified = readVerifiedTakeout(context, Uri.parse(session.archiveUri))
        check(verified.digest == session.archiveDigest) { "Archiwum zmieniło się od rozpoczęcia importu." }
        val all = reconstructTakeoutPlan(verified.archive, LocalDate.parse(session.startDate), LocalDate.parse(session.endDate), types, session.accountKeyHash)
        val plan = matchRecordedTakeoutPlan(all, dao.records(session.id))
        execute(session, plan, progress)
    }

    /** Leaves the session in undoing state until the sheet and local cache are reconciled. */
    suspend fun undoConnect(sessionId: String): Map<String, Set<LocalDate>> = takeoutSessionLock.withLock {
        val session = dao.session(sessionId) ?: error("Nie znaleziono sesji importu")
        check(session.status == "complete" || session.status == "undoing") { "Najpierw zakończ lub wznów import" }
        val types = session.selectedTypes.split(",").map { TakeoutType.valueOf(it) }.toSet()
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutWritePermissions(types))) { "Brakuje zgody na odczyt lub zapis wybranych danych." }
        val verified = readVerifiedTakeout(context, Uri.parse(session.archiveUri))
        check(verified.digest == session.archiveDigest) { "Archiwum zmieniło się od rozpoczęcia importu." }
        val all = reconstructTakeoutPlan(verified.archive, LocalDate.parse(session.startDate), LocalDate.parse(session.endDate), types, session.accountKeyHash)
        val plan = matchRecordedTakeoutPlan(all, dao.records(session.id))
        sheetOperationLock.withLock {
            val runId = "takeout-undo-${session.id}"
            journal.event(runId, "Takeout", "Rozpoczęto wycofywanie importu.")
            try {
            val undoJournal = RoomTakeoutUndoJournal(dao)
            TakeoutUndoCoordinator(undoJournal, HealthConnectTakeoutGateway(client, context.packageName, plan)).run(session.id)
            val affected = affectedTakeoutDates(plan)
            val importer = HistoryImport(HealthHistorySource(client), RoomHistoryStore(SyncDatabase.get(context).importDao()))
            importer.reconcileAfterUndo(affected)
            // Sheet cells are deliberately preserved when their provenance cannot be proven.
            undoJournal.finish(session.id)
            runCatching { journal.event(runId, "Takeout", "Wycofano własne rekordy; niepewne komórki arkusza pozostawiono.", complete = true) }
            affected
            } catch (e: Exception) {
                withContext(NonCancellable) { journal.event(runId, "Takeout", if (e is CancellationException) "Wycofywanie przerwane; można je wznowić." else "Wycofywanie zatrzymane (${e.javaClass.simpleName}); można je wznowić.", error = true, complete = true) }
                throw e
            }
        }
    }

    private suspend fun execute(session: TakeoutImportSession, plan: List<TakeoutPlannedRecord>, progress: (Int, Int) -> Unit): Int = sheetOperationLock.withLock {
        val runId = "takeout-${session.id}"
        journal.event(runId, "Takeout", "Rozpoczęto zapis do Health Connect: ${plan.size} rekordów.")
        try {
        val coordinator = TakeoutBatchWriteCoordinator(RoomTakeoutWriteJournal(dao), HealthConnectTakeoutGateway(client, context.packageName, plan))
        val ordered = plan.sortedWith(compareBy<TakeoutPlannedRecord>({ takeoutPayloadCandidate(it.record).kind }, { takeoutRecordTime(it.record) }))
        coordinator.run(session.id, ordered.map { takeoutPayloadCandidate(it.record) }, batchSize = 10, progress = progress)
        // Re-read historical days through the standard path, then leave them in the normal upload queue.
        val importer = HistoryImport(HealthHistorySource(client), RoomHistoryStore(SyncDatabase.get(context).importDao()))
        affectedTakeoutDates(plan).forEach { (metric, dates) ->
            dates.sorted().forEach { date -> importer.run(listOf(metric), date, date.plusDays(1), refresh = true) { } }
        }
        dao.finish(session.id)
        runCatching { journal.event(runId, "Takeout", "Zapis zakończony; odświeżono dane w kolejce arkusza.", complete = true) }
        plan.size
        } catch (e: Exception) {
            withContext(NonCancellable) { journal.event(runId, "Takeout", if (e is CancellationException) "Import przerwany; można go wznowić." else "Import zatrzymany (${e.javaClass.simpleName}); można go wznowić.", error = true, complete = true) }
            throw e
        }
    }
}
