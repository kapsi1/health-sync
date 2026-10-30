package pl.healthsync.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportDaoTest {
    private lateinit var db: SyncDatabase
    private lateinit var dao: ImportDao
    private val checkpoint = ImportCheckpoint("steps", "2026-10-01", "2026-11-01", "2026-11-01T00:00:00Z")
    private val row = DailyObservation("2026-10-10", "steps", 0.0, "kroki", "2026-11-01T00:00:00Z")

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SyncDatabase::class.java).build()
        dao = db.importDao()
    }
    @After fun close() = db.close()

    @Test fun commitIsIdempotentAndZeroIsPreserved() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        dao.commitChunk(checkpoint, listOf(row))
        assertEquals(listOf(row), dao.observations("steps"))
        assertEquals(listOf(checkpoint), dao.checkpoints("steps"))
        assertEquals(listOf(UploadQueue(row.date)), dao.queued())
    }

    @Test fun invalidChunkDoesNotCommitCheckpointOrData() = runTest {
        try {
            dao.commitChunk(checkpoint, listOf(row, row.copy(date = "2026-12-01")))
            fail("Expected invalid chunk")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.checkpoints("steps").isEmpty())
        assertTrue(dao.observations("steps").isEmpty())
        assertTrue(dao.queued().isEmpty())
    }

    @Test fun emptyChunkDoesNotDeleteExistingObservations() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        dao.commitChunk(checkpoint, emptyList())
        assertEquals(listOf(row), dao.observations("steps"))
    }

    @Test fun explicitUndoReconciliationRemovesOnlyTheMissingLocalMetric() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        dao.commitChunk(ImportCheckpoint("weight_avg", "2026-10-01", "2026-11-01", "2026-11-01T00:00:00Z"), listOf(row.copy(metricId = "weight_avg", value = 70.0)))
        dao.reconcileAfterUndo("weight_avg", row.date, null)
        assertTrue(dao.observations("weight_avg").isEmpty())
        assertEquals(listOf(row), dao.observations("steps"))
        assertTrue(dao.checkpoints("weight_avg").isEmpty())
        assertEquals(1, dao.checkpoints("steps").size)
    }

    @Test fun databaseFailureRollsBackDataAndQueue() = runTest {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_checkpoint BEFORE INSERT ON ImportCheckpoint BEGIN SELECT RAISE(ABORT, 'test'); END")
        try {
            dao.commitChunk(checkpoint, listOf(row))
            fail("Expected database error")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(dao.checkpoints("steps").isEmpty())
        assertTrue(dao.observations("steps").isEmpty())
        assertTrue(dao.queued().isEmpty())
    }

    @Test fun enablingMetricQueuesSavedHistoryAgain() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        val queue = RoomPendingQueue(dao, setOf("steps"))
        queue.acknowledge(queue.snapshot(10))
        assertTrue(dao.queued().isEmpty())
        assertEquals("2026-10-01", dao.earliestImportedStart())
        dao.queueMetric("steps")
        assertEquals(0.0, queue.snapshot(10).rows.single().cells.getValue("steps"), 0.0)
    }

    @Test fun changedSelectionDoesNotAcknowledgeOldUpload() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        var current = setOf("steps")
        val queue = RoomPendingQueue(dao, setOf("steps")) { current }
        val before = queue.snapshot(10)
        current = setOf("steps", "weight_avg")
        try { queue.acknowledge(before); fail("Expected changed selection") } catch (_: IllegalStateException) { }
        assertEquals(1, dao.queued().size)
    }

    @Test fun modifiedDataRemainsInQueueAfterEarlierSnapshotWasUploaded() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        val queue = RoomPendingQueue(dao, setOf("steps"))
        val original = queue.snapshot(10)
        assertEquals(0.0, original.rows.single().cells.getValue("steps"), 0.0)
        dao.commitChunk(checkpoint, listOf(row.copy(value = 15.0, observedAtUtc = "2026-11-01T00:00:01Z")))
        queue.acknowledge(original)
        assertEquals(1, dao.queued().size)
        val current = queue.snapshot(10)
        assertEquals(15.0, current.rows.single().cells.getValue("steps"), 0.0)
        queue.acknowledge(current)
        assertTrue(dao.queued().isEmpty())
    }
    @Test fun manualSessionResumesAndNewSessionInvalidatesAtomically() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        val ranges = listOf("2026-10-01" to "2026-11-01")
        val started = dao.prepareManualSession("2026-10-01", "2026-11-01", listOf("steps"), ranges)
        assertEquals("2026-11-01", started.endExclusive)
        assertTrue(dao.checkpoints("steps").isEmpty())
        dao.commitChunk(checkpoint, listOf(row))
        val resumed = dao.prepareManualSession("2026-10-01", "2026-11-02", listOf("steps"), ranges)
        assertEquals("2026-11-01", resumed.endExclusive)
        assertEquals(1, dao.checkpoints("steps").size)
        dao.finishManualSession(started.start, started.endExclusive, started.metricIds)
        assertNull(dao.manualSession())
        assertEquals(listOf(row), dao.observations("steps"))
    }

    @Test fun failedManualSessionCreationDoesNotInvalidateCheckpoint() = runTest {
        dao.commitChunk(checkpoint, listOf(row))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_manual BEFORE INSERT ON ManualImportSession BEGIN SELECT RAISE(ABORT, 'test'); END")
        try {
            dao.prepareManualSession("2026-10-01", "2026-11-01", listOf("steps"), listOf("2026-10-01" to "2026-11-01"))
            fail("Expected database error")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(listOf(checkpoint), dao.checkpoints("steps"))
        assertNull(dao.manualSession())
    }
}
