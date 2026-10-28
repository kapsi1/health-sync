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
}
