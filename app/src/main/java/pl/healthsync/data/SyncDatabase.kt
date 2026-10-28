package pl.healthsync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Dao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Entity(primaryKeys = ["date", "metricId"])
data class DailyObservation(val date: String, val metricId: String, val value: Double, val unit: String, val observedAtUtc: String)

@Entity(primaryKeys = ["metricId", "rangeStart", "rangeEndExclusive"])
data class ImportCheckpoint(val metricId: String, val rangeStart: String, val rangeEndExclusive: String, val finishedAtUtc: String)

@Entity
data class UploadQueue(@PrimaryKey val date: String, val dirty: Boolean = true)

data class ObservationSummary(val metricId: String, val earliestDate: String?, val dayCount: Int)

@Dao
abstract class ImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun saveObservations(rows: List<DailyObservation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun saveCheckpoint(checkpoint: ImportCheckpoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun markDirty(rows: List<UploadQueue>)

    @Query("SELECT * FROM DailyObservation WHERE metricId = :metricId ORDER BY date")
    abstract suspend fun observations(metricId: String): List<DailyObservation>

    @Query("SELECT * FROM ImportCheckpoint WHERE metricId = :metricId ORDER BY rangeStart")
    abstract suspend fun checkpoints(metricId: String): List<ImportCheckpoint>

    @Query("SELECT EXISTS(SELECT 1 FROM ImportCheckpoint WHERE metricId = :metricId AND rangeStart = :start AND rangeEndExclusive = :end)")
    abstract suspend fun isCompleted(metricId: String, start: String, end: String): Boolean

    @Query("DELETE FROM ImportCheckpoint WHERE metricId = :metricId AND rangeStart = :start AND rangeEndExclusive = :end")
    abstract suspend fun invalidate(metricId: String, start: String, end: String)

    @Transaction
    open suspend fun invalidateRanges(metricIds: List<String>, ranges: List<Pair<String, String>>) {
        for (metric in metricIds) for ((start, end) in ranges) invalidate(metric, start, end)
    }

    @Query("SELECT * FROM UploadQueue ORDER BY date")
    abstract suspend fun queued(): List<UploadQueue>

    @Query("SELECT DISTINCT q.date FROM UploadQueue q JOIN DailyObservation o ON q.date = o.date WHERE q.dirty = 1 AND o.metricId IN (:metrics) ORDER BY q.date LIMIT :limit")
    abstract suspend fun queuedDates(metrics: List<String>, limit: Int): List<String>

    @Query("SELECT * FROM DailyObservation WHERE date = :date ORDER BY metricId")
    abstract suspend fun observationsForDate(date: String): List<DailyObservation>

    @Query("DELETE FROM UploadQueue WHERE date = :date")
    protected abstract suspend fun deleteQueued(date: String)

    @Transaction
    open suspend fun acknowledgeIfUnchanged(date: String, snapshot: List<DailyObservation>) {
        if (observationsForDate(date) == snapshot) deleteQueued(date)
    }

    @Query("SELECT metricId, MIN(date) AS earliestDate, COUNT(*) AS dayCount FROM DailyObservation GROUP BY metricId")
    abstract suspend fun summaries(): List<ObservationSummary>

    @Query("SELECT MIN(rangeStart) FROM ImportCheckpoint")
    abstract suspend fun earliestImportedStart(): String?

    @Query("INSERT OR REPLACE INTO UploadQueue(date, dirty) SELECT date, 1 FROM DailyObservation WHERE metricId = :metricId")
    abstract suspend fun queueMetric(metricId: String)

    /** Commit only successfully read values. Absence never deletes or overwrites previous values. */
    @Transaction
    open suspend fun commitChunk(checkpoint: ImportCheckpoint, rows: List<DailyObservation>) {
        val start = LocalDate.parse(checkpoint.rangeStart)
        val end = LocalDate.parse(checkpoint.rangeEndExclusive)
        require(checkpoint.metricId.isNotBlank() && start < end)
        require(rows.all { row ->
            val date = LocalDate.parse(row.date)
            row.metricId == checkpoint.metricId && date >= start && date < end && row.value.isFinite()
        })
        require(rows.map { it.date }.distinct().size == rows.size)
        saveObservations(rows)
        markDirty(rows.map { it.date }.distinct().map { UploadQueue(it) })
        saveCheckpoint(checkpoint)
    }
}

@Database(entities = [DailyObservation::class, ImportCheckpoint::class, UploadQueue::class], version = 3, exportSchema = false)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun importDao(): ImportDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `HeartSample` (`recordId` TEXT NOT NULL, `timeUtc` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `bpm` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`recordId`, `timeUtc`, `ordinal`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `HeartChunkCheckpoint` (`rangeStart` TEXT NOT NULL, `rangeEndExclusive` TEXT NOT NULL, `finishedAtUtc` TEXT NOT NULL, PRIMARY KEY(`rangeStart`, `rangeEndExclusive`))")
            }
        }
        // Keep legacy tables intact so upgrading never deletes existing measurements.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
        @Volatile private var instance: SyncDatabase? = null
        fun get(context: Context): SyncDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SyncDatabase::class.java, "health-sync.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
