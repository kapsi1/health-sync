package pl.healthsync.data

import androidx.room.Room
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.healthsync.domain.TakeoutWriteCandidate
import pl.healthsync.domain.TakeoutWriteReceipt

@RunWith(AndroidJUnit4::class)
class TakeoutJournalTest {
    private lateinit var db: TakeoutJournalDatabase
    private lateinit var dao: TakeoutJournalDao
    private val session = TakeoutImportSession("run", "content://example", "digest", "account", "2026-01-01", "2026-01-31", "sleep", "2026-02-01T00:00:00Z", "active")
    private val record = TakeoutImportRecord("run", "id", "sleep", "pending")

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TakeoutJournalDatabase::class.java).build()
        dao = db.journal()
    }
    @After fun close() = db.close()

    @Test fun recordIntentSurvivesReplayAndPreservesConfirmation() = runTest {
        dao.createSession(session)
        dao.prepare("run", listOf(record))
        assertEquals("pending", dao.records("run").single().status)
        dao.confirm("run", "id", "connect-id", "2026-02-01T00:00:00Z")
        dao.prepare("run", listOf(record))
        dao.confirm("run", "id", "connect-id", "2026-02-01T00:00:00Z")
        assertEquals("connect-id", dao.records("run").single().connectRecordId)
    }

    @Test fun replayCannotReplaceConfirmedRecordEvidence() = runTest {
        dao.createSession(session)
        dao.prepare("run", listOf(record))
        dao.confirm("run", "id", "connect-id", "2026-02-01T00:00:00Z")
        try { dao.confirm("run", "id", "different", "2026-02-01T00:00:00Z"); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals("connect-id", dao.records("run").single().connectRecordId)
    }

    @Test fun wrongTypeRollsBackAllIntents() = runTest {
        dao.createSession(session)
        dao.prepare("run", listOf(record))
        try { dao.prepare("run", listOf(record.copy(kind = "weight"), record.copy(clientRecordId = "other"))); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(listOf(record), dao.records("run"))
    }

    @Test fun changedContentCannotReusePendingIdentity() = runTest {
        dao.createSession(session)
        dao.prepare("run", listOf(record.copy(contentHash = "original")))
        try { dao.prepare("run", listOf(record.copy(contentHash = "changed"))); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals("original", dao.records("run").single().contentHash)
    }

    @Test fun entirePlanIsSavedBeforeWritesAndCannotFinishEarly() = runTest {
        val candidates = listOf(TakeoutWriteCandidate("a", "sleep", "hash-a"), TakeoutWriteCandidate("b", "weight", "hash-b"))
        dao.createPlan(session, candidates)
        assertEquals(2, dao.records("run").size)
        try { dao.finish("run"); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals("active", dao.session("run")?.status)
        dao.confirm("run", "a", "id-a", "2026-02-01T00:00:00Z")
        dao.confirm("run", "b", "id-b", "2026-02-01T00:00:00Z")
        dao.finish("run")
        assertEquals("complete", dao.session("run")?.status)
    }

    @Test fun invalidPlanCreatesNoSession() = runTest {
        try { dao.createPlan(session, listOf(TakeoutWriteCandidate("", "sleep", "hash"))); fail("Expected failure") } catch (_: IllegalArgumentException) { }
        assertNull(dao.session("run"))
    }

    @Test fun undoCheckpointIsResumableAndRequiresConfirmedIdentity() = runTest {
        dao.createPlan(session, listOf(TakeoutWriteCandidate("a", "sleep", "hash-a")))
        dao.confirm("run", "a", "id-a", "2026-02-01T00:00:00Z")
        dao.finish("run")
        dao.beginUndo("run")
        dao.beginUndo("run")
        try { dao.markReverted("run", "a", "wrong", "2026-02-01T00:00:00Z"); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals("confirmed", dao.record("run", "a")?.status)
        dao.markReverted("run", "a", "id-a", "2026-02-01T00:00:00Z")
        dao.finishUndo("run")
        assertEquals("reverted", dao.session("run")?.status)
    }

    @Test fun batchJournalPreservesReceiptsAndRejectsChangedContent() = runTest {
        val candidates = listOf(TakeoutWriteCandidate("a", "sleep", "hash-a"), TakeoutWriteCandidate("b", "weight", "hash-b"))
        dao.createPlan(session, candidates)
        val journal = RoomTakeoutWriteJournal(dao)
        journal.prepareBatch("run", candidates)
        assertTrue(journal.confirmedBatch("run", candidates).values.all { it == null })
        val receipt = TakeoutWriteReceipt("connect-a", "2026-02-01T00:00:00Z")
        journal.confirmBatch("run", listOf(candidates.first() to receipt))
        assertEquals(receipt, journal.confirmedBatch("run", candidates)["a"])
        try {
            journal.confirmBatch("run", listOf(candidates[1].copy(contentHash = "changed") to receipt))
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertNull(journal.confirmedBatch("run", candidates)["b"])
    }

    @Test fun migrationPreservesPendingRecord() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "takeout-journal-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE IF NOT EXISTS `TakeoutImportSession` (`id` TEXT NOT NULL, `archiveUri` TEXT NOT NULL, `archiveDigest` TEXT NOT NULL, `accountKeyHash` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `selectedTypes` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
            legacy.execSQL("CREATE TABLE IF NOT EXISTS `TakeoutImportRecord` (`sessionId` TEXT NOT NULL, `clientRecordId` TEXT NOT NULL, `kind` TEXT NOT NULL, `status` TEXT NOT NULL, `connectRecordId` TEXT, `connectLastModified` TEXT, PRIMARY KEY(`sessionId`, `clientRecordId`))")
            legacy.execSQL("INSERT INTO TakeoutImportRecord VALUES ('run', 'id', 'sleep', 'pending', NULL, NULL)")
            legacy.version = 1
        }
        val migrated = Room.databaseBuilder(context, TakeoutJournalDatabase::class.java, name).addMigrations(TakeoutJournalDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("", migrated.journal().records("run").single().contentHash)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
