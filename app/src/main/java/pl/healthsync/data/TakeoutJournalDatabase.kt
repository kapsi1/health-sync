package pl.healthsync.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import pl.healthsync.domain.TakeoutWriteCandidate
import pl.healthsync.domain.TakeoutWriteJournal
import pl.healthsync.domain.TakeoutWriteReceipt
import pl.healthsync.domain.TakeoutUndoEntry
import pl.healthsync.domain.TakeoutUndoJournal

/** The archive itself is not copied into the journal. */
@Entity
internal data class TakeoutImportSession(
    @PrimaryKey val id: String,
    val archiveUri: String,
    val archiveDigest: String,
    val accountKeyHash: String,
    val startDate: String,
    val endDate: String,
    val selectedTypes: String,
    val createdAt: String,
    val status: String,
)

@Entity(primaryKeys = ["sessionId", "clientRecordId"])
internal data class TakeoutImportRecord(
    val sessionId: String,
    val clientRecordId: String,
    val kind: String,
    val status: String,
    @ColumnInfo(defaultValue = "''") val contentHash: String = "",
    val connectRecordId: String? = null,
    val connectLastModified: String? = null,
)

@Dao
internal abstract class TakeoutJournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun createSession(session: TakeoutImportSession)

    @Query("SELECT * FROM TakeoutImportSession WHERE id = :id")
    abstract suspend fun session(id: String): TakeoutImportSession?

    @Query("SELECT * FROM TakeoutImportSession WHERE status = 'active' ORDER BY createdAt DESC")
    abstract suspend fun activeSessions(): List<TakeoutImportSession>

    @Query("SELECT * FROM TakeoutImportSession WHERE status IN ('active', 'undoing') ORDER BY createdAt DESC")
    abstract suspend fun unfinishedSessions(): List<TakeoutImportSession>

    @Query("SELECT * FROM TakeoutImportSession ORDER BY createdAt DESC")
    abstract fun observeSessions(): Flow<List<TakeoutImportSession>>

    @Query("UPDATE TakeoutImportSession SET status = :status WHERE id = :id AND status = 'active'")
    protected abstract suspend fun setSessionStatus(id: String, status: String): Int

    @Query("UPDATE TakeoutImportSession SET status = 'undoing' WHERE id = :id AND status = 'complete'")
    protected abstract suspend fun startUndoUpdate(id: String): Int

    @Query("UPDATE TakeoutImportSession SET status = 'reverted' WHERE id = :id AND status = 'undoing'")
    protected abstract suspend fun finishUndoUpdate(id: String): Int

    @Query("UPDATE TakeoutImportRecord SET status = 'reverted' WHERE sessionId = :sessionId AND clientRecordId = :clientId AND status = 'confirmed' AND connectRecordId = :recordId AND connectLastModified = :lastModified")
    protected abstract suspend fun markRevertedUpdate(sessionId: String, clientId: String, recordId: String, lastModified: String): Int

    @Query("SELECT * FROM TakeoutImportRecord WHERE sessionId = :sessionId ORDER BY clientRecordId")
    abstract suspend fun records(sessionId: String): List<TakeoutImportRecord>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIntents(records: List<TakeoutImportRecord>): List<Long>

    @Query("UPDATE TakeoutImportRecord SET status = 'confirmed', connectRecordId = :recordId, connectLastModified = :lastModified WHERE sessionId = :sessionId AND clientRecordId = :clientId AND status = 'pending'")
    protected abstract suspend fun updateConfirmation(sessionId: String, clientId: String, recordId: String, lastModified: String): Int

    @Query("SELECT * FROM TakeoutImportRecord WHERE sessionId = :sessionId AND clientRecordId = :clientId")
    abstract suspend fun record(sessionId: String, clientId: String): TakeoutImportRecord?

    @Query("SELECT * FROM TakeoutImportRecord WHERE sessionId = :sessionId AND clientRecordId IN (:clientIds)")
    abstract suspend fun recordsFor(sessionId: String, clientIds: List<String>): List<TakeoutImportRecord>

    /** Freeze the entire reviewed plan and its exact archive identity before the first write. */
    @Transaction
    open suspend fun createPlan(session: TakeoutImportSession, plan: List<TakeoutWriteCandidate>) {
        require(session.status == "active" && session.id.isNotBlank() && session.archiveDigest.isNotBlank() && session.accountKeyHash.isNotBlank())
        require(plan.isNotEmpty() && plan.map { it.clientId }.distinct().size == plan.size)
        require(plan.all { it.clientId.isNotBlank() && it.kind.isNotBlank() && it.contentHash.isNotBlank() })
        createSession(session)
        prepare(session.id, plan.map { TakeoutImportRecord(session.id, it.clientId, it.kind, "pending", it.contentHash) })
    }

    @Transaction
    open suspend fun finish(sessionId: String) {
        check(records(sessionId).isNotEmpty() && records(sessionId).all { it.status == "confirmed" }) { "Import nie został jeszcze potwierdzony" }
        check(setSessionStatus(sessionId, "complete") == 1) { "Sesja importu nie jest aktywna" }
    }

    @Transaction
    open suspend fun beginUndo(sessionId: String) {
        val state = session(sessionId)?.status
        check(state == "complete" || state == "undoing") { "Najpierw zakończ lub wznów import" }
        if (state == "complete") check(startUndoUpdate(sessionId) == 1)
    }

    @Transaction
    open suspend fun markReverted(sessionId: String, clientId: String, recordId: String, lastModified: String) {
        check(session(sessionId)?.status == "undoing")
        if (markRevertedUpdate(sessionId, clientId, recordId, lastModified) == 0) {
            val row = record(sessionId, clientId)
            check(row?.status == "reverted" && row.connectRecordId == recordId && row.connectLastModified == lastModified)
        }
    }

    @Transaction
    open suspend fun finishUndo(sessionId: String) {
        val rows = records(sessionId)
        check(rows.isNotEmpty() && rows.all { it.status == "reverted" }) { "Nie wycofano wszystkich rekordów" }
        check(finishUndoUpdate(sessionId) == 1)
    }

    /** Record intent durably before attempting any external write; never replace prior evidence. */
    @Transaction
    open suspend fun prepare(sessionId: String, pending: List<TakeoutImportRecord>) {
        check(session(sessionId)?.status == "active") { "Sesja importu nie jest aktywna" }
        require(pending.all { it.sessionId == sessionId && it.status == "pending" && it.connectRecordId == null && it.connectLastModified == null })
        require(pending.map { it.clientRecordId }.distinct().size == pending.size)
        insertIntents(pending)
        for (row in pending) {
            val saved = record(sessionId, row.clientRecordId)
            check(saved?.kind == row.kind && saved.contentHash == row.contentHash) { "Niezgodna zawartość zapisanego rekordu" }
        }
    }

    /** Replaying an acknowledgement is safe; changing already saved evidence is not. */
    @Transaction
    open suspend fun confirm(sessionId: String, clientId: String, recordId: String, lastModified: String) {
        require(recordId.isNotBlank() && lastModified.isNotBlank())
        if (updateConfirmation(sessionId, clientId, recordId, lastModified) == 0) {
            val saved = record(sessionId, clientId)
            check(saved?.status == "confirmed" && saved.connectRecordId == recordId && saved.connectLastModified == lastModified) {
                "Nie można potwierdzić zapisu rekordu"
            }
        }
    }

    @Transaction
    open suspend fun confirmAll(sessionId: String, entries: List<Pair<TakeoutWriteCandidate, TakeoutWriteReceipt>>) {
        for ((candidate, receipt) in entries) {
            val row = record(sessionId, candidate.clientId)
            check(row?.kind == candidate.kind && row.contentHash == candidate.contentHash)
            confirm(sessionId, candidate.clientId, receipt.recordId, receipt.lastModified)
        }
    }
}

internal class RoomTakeoutWriteJournal(private val dao: TakeoutJournalDao) : TakeoutWriteJournal {
    override suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate) {
        dao.prepare(sessionId, listOf(TakeoutImportRecord(sessionId, candidate.clientId, candidate.kind, "pending", candidate.contentHash)))
    }
    override suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate): TakeoutWriteReceipt? {
        val row = dao.record(sessionId, candidate.clientId) ?: return null
        check(row.kind == candidate.kind && row.contentHash == candidate.contentHash)
        if (row.status != "confirmed") return null
        return TakeoutWriteReceipt(checkNotNull(row.connectRecordId), checkNotNull(row.connectLastModified))
    }
    override suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt) {
        dao.confirm(sessionId, candidate.clientId, receipt.recordId, receipt.lastModified)
    }
    override suspend fun prepareBatch(sessionId: String, candidates: List<TakeoutWriteCandidate>) {
        if (candidates.isEmpty()) return
        dao.prepare(sessionId, candidates.map { TakeoutImportRecord(sessionId, it.clientId, it.kind, "pending", it.contentHash) })
    }
    override suspend fun confirmedBatch(sessionId: String, candidates: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteReceipt?> {
        if (candidates.isEmpty()) return emptyMap()
        val rows = dao.recordsFor(sessionId, candidates.map { it.clientId }).associateBy { it.clientRecordId }
        return candidates.associate { candidate ->
            val row = checkNotNull(rows[candidate.clientId])
            check(row.kind == candidate.kind && row.contentHash == candidate.contentHash)
            candidate.clientId to if (row.status == "confirmed") TakeoutWriteReceipt(checkNotNull(row.connectRecordId), checkNotNull(row.connectLastModified)) else null
        }
    }
    override suspend fun confirmBatch(sessionId: String, entries: List<Pair<TakeoutWriteCandidate, TakeoutWriteReceipt>>) {
        if (entries.isNotEmpty()) dao.confirmAll(sessionId, entries)
    }
}

internal class RoomTakeoutUndoJournal(private val dao: TakeoutJournalDao) : TakeoutUndoJournal {
    override suspend fun begin(sessionId: String) = dao.beginUndo(sessionId)
    override suspend fun pending(sessionId: String): List<TakeoutUndoEntry> = dao.records(sessionId).filter { it.status == "confirmed" }.map {
        TakeoutUndoEntry(TakeoutWriteCandidate(it.clientRecordId, it.kind, it.contentHash), TakeoutWriteReceipt(checkNotNull(it.connectRecordId), checkNotNull(it.connectLastModified)))
    }
    override suspend fun markReverted(sessionId: String, entry: TakeoutUndoEntry) = dao.markReverted(sessionId, entry.candidate.clientId, entry.receipt.recordId, entry.receipt.lastModified)
    override suspend fun finish(sessionId: String) = dao.finishUndo(sessionId)
}

@Database(entities = [TakeoutImportSession::class, TakeoutImportRecord::class], version = 2, exportSchema = false)
internal abstract class TakeoutJournalDatabase : RoomDatabase() {
    abstract fun journal(): TakeoutJournalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `TakeoutImportRecord` ADD COLUMN `contentHash` TEXT NOT NULL DEFAULT ''")
            }
        }
        @Volatile private var instance: TakeoutJournalDatabase? = null
        fun get(context: Context): TakeoutJournalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TakeoutJournalDatabase::class.java, "takeout-import-journal.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
