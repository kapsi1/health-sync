package pl.healthsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import pl.healthsync.data.HealthHistorySource
import pl.healthsync.data.RoomHistoryStore
import pl.healthsync.data.RoomPendingQueue
import pl.healthsync.data.SheetGatewayHttp
import pl.healthsync.data.SheetHttpError
import pl.healthsync.data.SheetSettings
import pl.healthsync.data.SyncDatabase
import pl.healthsync.domain.HistoryImport
import pl.healthsync.domain.SheetSync
import java.io.IOException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val Context.syncPreferences by preferencesDataStore(name = "background_sync")
private val intervalKey = intPreferencesKey("interval_hours")
private val statusKey = stringPreferencesKey("status")
private val logKey = stringPreferencesKey("history")
private const val WORK_NAME = "periodic-health-sync"

data class BackgroundState(val hours: Int, val status: String, val log: List<String> = emptyList())

class BackgroundSettings(private val context: Context) {
    val state = context.syncPreferences.data.map { BackgroundState(it[intervalKey] ?: 0, it[statusKey] ?: "Synchronizacja automatyczna jest wyłączona.", it[logKey]?.lines()?.filter(String::isNotBlank) ?: emptyList()) }
    suspend fun configure(hours: Int) {
        require(hours in listOf(0, 1, 3, 12, 24))
        context.syncPreferences.edit { it[intervalKey] = hours; it[statusKey] = if (hours == 0) "Synchronizacja automatyczna jest wyłączona." else "Zaplanowano synchronizację co $hours godz. Android może opóźnić jej uruchomienie." }
        val manager = WorkManager.getInstance(context)
        if (hours == 0) manager.cancelUniqueWork(WORK_NAME)
        else manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<BackgroundSyncWorker>(hours.toLong(), TimeUnit.HOURS).build())
    }
    suspend fun log(message: String, updateStatus: Boolean = false) {
        context.syncPreferences.edit {
            it[logKey] = appendSyncLog(it[logKey] ?: "", message, Instant.now())
            if (updateStatus && (it[intervalKey] ?: 0) != 0) it[statusKey] = message
        }
    }
    suspend fun status(message: String) = log(message, updateStatus = true)
}

class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = BackgroundSettings(applicationContext)
        try {
            if (settings.state.first().hours == 0) return Result.success()
            settings.log("Rozpoczęto synchronizację automatyczną.")
            if (HealthConnectClient.getSdkStatus(applicationContext) != HealthConnectClient.SDK_AVAILABLE) {
                settings.status("Health Connect jest niedostępny. Otwórz aplikację i sprawdź ustawienia."); return Result.success()
            }
            val client = HealthConnectClient.getOrCreate(applicationContext)
            val granted = client.permissionController.getGrantedPermissions()
            if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) != HealthConnectFeatures.FEATURE_STATUS_AVAILABLE || HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) {
                settings.status("Odczyt w tle wymaga zgody Health Connect. Otwórz aplikację i przyznaj zgodę."); return Result.success()
            }
            val selected = MetricSelection(applicationContext).selected.first()
            val metrics = MetricRegistry.all.filter { it.id in selected }
            if (metrics.any { it.permission !in granted }) { settings.status("Brakuje zgód Health Connect dla wybranych statystyk."); return Result.success() }
            val db = SyncDatabase.get(applicationContext)
            val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
            if (metrics.isNotEmpty()) HistoryImport(HealthHistorySource(client), RoomHistoryStore(db.importDao())).run(metrics.map { it.id }, today.minusDays(2), today.plusDays(1), true) {}
            val sheetId = SheetSettings(applicationContext).state.first().id
            if (sheetId == null) { settings.status("Dane zapisano lokalnie. Utwórz arkusz Google w aplikacji."); return Result.success() }
            val token = googleToken()
            if (token == null) { settings.status("Dane zapisano lokalnie. Otwórz aplikację i ponownie przyznaj zgodę Google."); return Result.success() }
            val gateway = SheetGatewayHttp(sheetId, token)
            syncLock.withLock { gateway.reconcileColumns(MetricRegistry.all.map { it.column }.toSet(), metrics.map { it.column }.toSet()) }
            val sync = SheetSync(gateway, RoomPendingQueue(db.importDao(), selected), syncLock)
            var total = 0
            repeat(10) {
                val count = sync.uploadBatch()
                total += count
                if (count == 0) { settings.status("Synchronizacja zakończona: przetworzono $total dni."); return Result.success() }
            }
            settings.status("Przetworzono $total dni; pozostała kolejka zostanie wysłana podczas kolejnej synchronizacji.")
            return Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { settings.log("Synchronizacja została przerwana.") }
            throw e
        }
        catch (e: SheetHttpError) {
            if (e.code == 401 || e.code == 403) { settings.status("Dostęp Google wygasł lub został odrzucony. Sprawdź zgodę w aplikacji."); return Result.success() }
            settings.status("Błąd Google Sheets (${e.code}); kolejka jest zachowana.")
            return if (e.code == 429 || e.code >= 500) Result.retry() else Result.success()
        } catch (_: IOException) { settings.status("Brak połączenia. Dane lokalne i kolejka są zachowane; ponowimy próbę."); return Result.retry() }
        catch (_: Exception) { settings.status("Synchronizacja zatrzymana. Otwórz aplikację i sprawdź zgody oraz dane."); return Result.success() }
    }

    private suspend fun googleToken(): String? = suspendCancellableCoroutine { continuation ->
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        Identity.getAuthorizationClient(applicationContext).authorize(request)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(if (it.hasResolution()) null else it.accessToken) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
