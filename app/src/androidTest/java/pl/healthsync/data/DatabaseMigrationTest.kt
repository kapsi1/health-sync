package pl.healthsync.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun migrationFromVersion3KeepsDailyData() = checkMigration(3)

    @Test fun migrationFromVersion1KeepsDailyData() = checkMigration(1)

    @Test fun migrationFromVersion2KeepsDailyDataAndLegacySamples() = checkMigration(2)

    private fun checkMigration(version: Int) = runTest {
        val name = "health-sync-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL("CREATE TABLE IF NOT EXISTS `DailyObservation` (`date` TEXT NOT NULL, `metricId` TEXT NOT NULL, `value` REAL NOT NULL, `unit` TEXT NOT NULL, `observedAtUtc` TEXT NOT NULL, PRIMARY KEY(`date`, `metricId`))")
            legacy.execSQL("CREATE TABLE IF NOT EXISTS `ImportCheckpoint` (`metricId` TEXT NOT NULL, `rangeStart` TEXT NOT NULL, `rangeEndExclusive` TEXT NOT NULL, `finishedAtUtc` TEXT NOT NULL, PRIMARY KEY(`metricId`, `rangeStart`, `rangeEndExclusive`))")
            legacy.execSQL("CREATE TABLE IF NOT EXISTS `UploadQueue` (`date` TEXT NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            legacy.execSQL("INSERT INTO DailyObservation VALUES ('2026-10-28', 'steps', 0, 'kroki', '2026-10-28T10:00:00Z')")
            legacy.execSQL("INSERT INTO UploadQueue VALUES ('2026-10-28', 1)")
            legacy.execSQL("INSERT INTO ImportCheckpoint VALUES ('steps', '2026-10-01', '2026-11-01', '2026-11-01T00:00:00Z')")
            if (version == 2) {
                legacy.execSQL("CREATE TABLE `HeartSample` (`recordId` TEXT NOT NULL, `timeUtc` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `bpm` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`recordId`, `timeUtc`, `ordinal`))")
                legacy.execSQL("CREATE TABLE `HeartChunkCheckpoint` (`rangeStart` TEXT NOT NULL, `rangeEndExclusive` TEXT NOT NULL, `finishedAtUtc` TEXT NOT NULL, PRIMARY KEY(`rangeStart`, `rangeEndExclusive`))")
                legacy.execSQL("INSERT INTO HeartSample VALUES ('fake', '2026-10-28T10:00:00Z', 0, '2026-10-28', 'fake.source', 70, 1)")
            }
            legacy.version = version
        } finally { legacy.close() }
        val migrated = Room.databaseBuilder(context, SyncDatabase::class.java, name).addMigrations(SyncDatabase.MIGRATION_1_2, SyncDatabase.MIGRATION_2_3, SyncDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(0.0, migrated.importDao().observations("steps").single().value, 0.0)
            assertEquals(1, migrated.importDao().queued().size)
            assertEquals(1, migrated.importDao().checkpoints("steps").size)
            if (version == 2) {
                migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM HeartSample").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
