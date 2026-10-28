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

    @Query("SELECT metricId, MIN(date) AS earliestDate, COUNT(*) AS dayCount FROM DailyObservation GROUP BY metricId")
    abstract suspend fun summaries(): List<ObservationSummary>

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

@Database(entities = [DailyObservation::class, ImportCheckpoint::class, UploadQueue::class], version = 1, exportSchema = false)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun importDao(): ImportDao

    companion object {
        @Volatile private var instance: SyncDatabase? = null
        fun get(context: Context): SyncDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SyncDatabase::class.java, "health-sync.db")
                .build().also { instance = it }
        }
    }
}
