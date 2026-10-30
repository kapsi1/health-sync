package pl.healthsync.data

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.healthsync.SyncJournal
import pl.healthsync.domain.*
import pl.healthsync.sheetOperationLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal const val ACTIVITY_SESSION_PREFIX = "ACTIVITY:"
internal fun activitySessionKinds(text: String): Set<TakeoutActivityKind> {
    require(text.startsWith(ACTIVITY_SESSION_PREFIX))
    return text.removePrefix(ACTIVITY_SESSION_PREFIX).split(",").map { TakeoutActivityKind.valueOf(it) }.toSet()
}

internal class TakeoutActivityImportService(private val context: Context, private val client: HealthConnectClient) {
    private val dao = TakeoutJournalDatabase.get(context).journal()
    private val journal = SyncJournal(context)

    private fun readVerifiedActivity(uri: Uri, digest: String, kinds: Set<TakeoutActivityKind>, start: LocalDate, end: LocalDate): Map<TakeoutActivityKind, Map<LocalDate, TakeoutActivityEstimate>> {
        check(readVerifiedTakeout(context, uri).digest == digest) { "Archiwum zmieniło się od analizy" }
        val values = context.contentResolver.openInputStream(uri)?.use { readTakeoutActivityArchive(it, kinds, start, end, ZoneId.of("Europe/Warsaw")) }
            ?: error("Nie można otworzyć archiwum")
        check(readVerifiedTakeout(context, uri).digest == digest) { "Archiwum zmieniło się podczas analizy" }
        return values
    }

    private fun plan(values: Map<TakeoutActivityKind, Map<LocalDate, TakeoutActivityEstimate>>, key: String): List<TakeoutPlannedRecord> =
        values.flatMap { (kind, days) -> days.mapNotNull { (date, estimate) ->
            estimate.value?.takeIf { it > 0 }?.let { mapTakeoutActivity(kind, date, it, key) }
        } }

    suspend fun start(uri: Uri, digest: String, kinds: Set<TakeoutActivityKind>, start: LocalDate, end: LocalDate, account: String,
        reviewed: Map<TakeoutActivityKind, List<ActivityPreviewDay>>, progress: (Int, Int) -> Unit): Int = takeoutSessionLock.withLock {
        check(dao.unfinishedSessions().isEmpty()) { "Najpierw wznów przerwaną operację Takeout" }
        check(client.permissionController.getGrantedPermissions().containsAll(activityWritePermissions(kinds))) { "Brak zgody na zapis lub odczyt aktywności" }
        val key = takeoutAccountKey(account)
        val values = readVerifiedActivity(uri, digest, kinds, start, end)
        val fresh = TakeoutActivityInspector(client).inspect(values, start, end)
        fun candidates(preview: Map<TakeoutActivityKind, List<ActivityPreviewDay>>) = preview.flatMap { (kind, days) -> days.filter { it.status == ActivityPreviewStatus.ESTIMATED }.map { day -> mapTakeoutActivity(kind, day.date, requireNotNull(day.estimate.value), key) } }
        val old = candidates(reviewed.filterKeys { it in kinds }).map { takeoutPayloadCandidate(it.record) }
        val new = candidates(fresh.filterKeys { it in kinds })
        check(old.isNotEmpty() && old.size == old.toSet().size && old.toSet() == new.map { takeoutPayloadCandidate(it.record) }.toSet()) { "Archiwum lub dane Connect zmieniły się. Wykonaj analizę ponownie." }
        val session = TakeoutImportSession(UUID.randomUUID().toString(), uri.toString(), digest, key, start.toString(), end.toString(),
            ACTIVITY_SESSION_PREFIX + kinds.map { it.name }.sorted().joinToString(","), Instant.now().toString(), "active")
        dao.createPlan(session, new.map { takeoutPayloadCandidate(it.record) })
        execute(session, new, progress)
    }

    suspend fun resume(sessionId: String, progress: (Int, Int) -> Unit): Int = takeoutSessionLock.withLock {
        val session = dao.session(sessionId) ?: error("Nie znaleziono sesji")
        check(session.status == "active") { "Import nie jest aktywny" }
        val kinds = activitySessionKinds(session.selectedTypes)
        check(client.permissionController.getGrantedPermissions().containsAll(activityWritePermissions(kinds))) { "Brak zgody na aktywność" }
        val values = readVerifiedActivity(Uri.parse(session.archiveUri), session.archiveDigest, kinds, LocalDate.parse(session.startDate), LocalDate.parse(session.endDate))
        execute(session, matchRecordedTakeoutPlan(plan(values, session.accountKeyHash), dao.records(session.id)), progress)
    }

    suspend fun undoConnect(sessionId: String) = takeoutSessionLock.withLock {
        val session = dao.session(sessionId) ?: error("Nie znaleziono sesji")
        check(session.status == "complete" || session.status == "undoing") { "Najpierw zakończ lub wznów import" }
        val kinds = activitySessionKinds(session.selectedTypes)
        check(client.permissionController.getGrantedPermissions().containsAll(activityWritePermissions(kinds))) { "Brak zgody na aktywność" }
        val values = readVerifiedActivity(Uri.parse(session.archiveUri), session.archiveDigest, kinds, LocalDate.parse(session.startDate), LocalDate.parse(session.endDate))
        val plan = matchRecordedTakeoutPlan(plan(values, session.accountKeyHash), dao.records(session.id))
        sheetOperationLock.withLock {
            val run = "takeout-activity-undo-${session.id}"
            journal.event(run, "Takeout", "Rozpoczęto wycofywanie importu aktywności.")
            try {
                val undo = RoomTakeoutUndoJournal(dao)
                TakeoutUndoCoordinator(undo, HealthConnectTakeoutActivityGateway(client, context.packageName, plan)).run(session.id)
                HistoryImport(HealthHistorySource(client), RoomHistoryStore(SyncDatabase.get(context).importDao())).reconcileAfterUndo(affectedTakeoutDates(plan))
                undo.finish(session.id)
                runCatching { journal.event(run, "Takeout", "Wycofano rekordy aktywności; niepewne komórki zachowano.", complete = true) }
            } catch (e: Exception) {
                withContext(NonCancellable) { journal.event(run, "Takeout", "Wycofywanie aktywności zatrzymane (${e.javaClass.simpleName}); można je wznowić.", error = true, complete = true) }
                throw e
            }
        }
    }

    private suspend fun execute(session: TakeoutImportSession, plan: List<TakeoutPlannedRecord>, progress: (Int, Int) -> Unit): Int = sheetOperationLock.withLock {
        val run = "takeout-activity-${session.id}"
        journal.event(run, "Takeout", "Rozpoczęto zapis szacunkowej aktywności: ${plan.size} rekordów.")
        try {
            val ordered = plan.sortedWith(compareBy({ takeoutPayloadCandidate(it.record).kind }, { takeoutRecordTime(it.record) }))
            TakeoutBatchWriteCoordinator(RoomTakeoutWriteJournal(dao), HealthConnectTakeoutActivityGateway(client, context.packageName, plan))
                .run(session.id, ordered.map { takeoutPayloadCandidate(it.record) }, batchSize = 10, progress = progress)
            val importer = HistoryImport(HealthHistorySource(client), RoomHistoryStore(SyncDatabase.get(context).importDao()))
            affectedTakeoutDates(plan).forEach { (metric, dates) -> dates.sorted().forEach { date -> importer.run(listOf(metric), date, date.plusDays(1), refresh = true) { } } }
            dao.finish(session.id)
            runCatching { journal.event(run, "Takeout", "Zapis szacunkowej aktywności zakończony; kolejkę odświeżono.", complete = true) }
            plan.size
        } catch (e: Exception) {
            withContext(NonCancellable) { journal.event(run, "Takeout", if (e is CancellationException) "Import aktywności przerwany; można go wznowić." else "Import aktywności zatrzymany (${e.javaClass.simpleName}); można go wznowić.", error = true, complete = true) }
            throw e
        }
    }
}
