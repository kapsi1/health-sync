package pl.healthsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import pl.healthsync.data.SyncDatabase
import pl.healthsync.data.SheetGatewayHttp
import pl.healthsync.data.RoomPendingQueue
import pl.healthsync.domain.SheetSync
import pl.healthsync.domain.planSheetMerge
import pl.healthsync.domain.spreadsheetId
import pl.healthsync.data.SheetHttpError
import pl.healthsync.data.SheetSettings
import pl.healthsync.data.SheetState
import pl.healthsync.data.SheetsCreator
import pl.healthsync.data.SelectedMetricBackfill
import pl.healthsync.data.HealthChanges
import androidx.health.connect.client.HealthConnectClient

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
internal val syncLock = Mutex()
internal val sheetOperationLock = Mutex()

@Composable
fun GoogleAuthorization(activity: Activity, selected: Set<String>, visible: Boolean, syncRequest: Int, manualRunId: String?, importBusy: Boolean, onStatus: (String) -> Unit, onBusy: (Boolean) -> Unit, onSyncBusy: (Boolean) -> Unit) {
    var status by remember { mutableStateOf("Nie sprawdzono. Wymaga konfiguracji Google Cloud.") }
    var busy by remember { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf("check") }
    var sheetReference by rememberSaveable { mutableStateOf("") }
    var pendingSheetId by rememberSaveable { mutableStateOf("") }
    var showSheetCheck by rememberSaveable { mutableStateOf(false) }
    var showSwitchChoice by rememberSaveable { mutableStateOf(false) }
    var queueAllForSwitch by rememberSaveable { mutableStateOf(false) }
    val settings = remember { SheetSettings(activity.applicationContext) }
    val sheet by settings.state.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val client = remember { Identity.getAuthorizationClient(activity) }

    fun recordAuthorizationFailure() {
        val runId = manualRunId
        if (action == "sync" && runId != null) scope.launch {
            SyncJournal(activity.applicationContext).event(runId, "Ręczna", "Autoryzacja Google nie powiodła się. Sprawdź dostęp i spróbuj ponownie.", error = true, complete = true)
        }
    }

    fun authorized(token: String?) {
        if (token.isNullOrBlank()) { busy = false; status = "Nie otrzymano tokenu."; recordAuthorizationFailure(); return }
        if (action == "attach") {
            val id = pendingSheetId
            scope.launch {
                try {
                    sheetOperationLock.withLock {
                        syncLock.withLock {
                            val snapshot = SheetGatewayHttp(id, token).snapshot()
                            planSheetMerge(snapshot.headers, snapshot.dateCells, emptyList(), emptySet())
                            if (queueAllForSwitch && settings.state.first().id != id) {
                                val dao = SyncDatabase.get(activity.applicationContext).importDao()
                                MetricRegistry.all.forEach { dao.queueMetric(it.id) }
                            }
                            settings.save(id, "https://docs.google.com/spreadsheets/d/$id/edit")
                        }
                    }
                    status = "Podłączono arkusz. Zaimportuj dane i uruchom synchronizację."
                } catch (e: CancellationException) { throw e }
                catch (e: SheetHttpError) {
                    status = when (e.code) {
                        401, 403 -> "Brak dostępu do tego arkusza (HTTP ${e.code}). Wpisanie ID nie przyznaje dostępu; wybór pliku przez Google Picker jest w przygotowaniu."
                        400, 404 -> "Nie znaleziono arkusza lub zakładki daily (HTTP ${e.code}). Sprawdź ID i strukturę."
                        else -> "Nie udało się sprawdzić arkusza (HTTP ${e.code}). Spróbuj ponownie."
                    }
                } catch (_: IllegalArgumentException) {
                    status = "Arkusz ma nieprawidłowy nagłówek lub daty. Sprawdź zakładkę daily, jej pierwszą kolumnę i unikalność dat."
                } catch (_: Exception) {
                    status = "Nie udało się sprawdzić arkusza. Sprawdź połączenie i spróbuj ponownie."
                } finally { busy = false }
            }
            return
        }
        if (action == "sync") {
            val sheetId = sheet?.id
            if (sheetId == null) { busy = false; status = "Nie wybrano arkusza."; return }
            val selectedSnapshot = selected.toSet()
            scope.launch {
                var failure: String? = null
                try {
                    manualRunId?.let { SyncJournal(activity.applicationContext).event(it, "Ręczna", "Rozpoczęto wysyłanie do arkusza.") }
                    sheetOperationLock.withLock {
                    check(settings.state.first().id == sheetId) { "Arkusz zmienił się; uruchom synchronizację ponownie" }
                    val database = SyncDatabase.get(activity.applicationContext)
                    val gateway = SheetGatewayHttp(sheetId, token)
                    syncLock.withLock { gateway.reconcileColumns(MetricRegistry.all.map { it.column }.toSet(), MetricRegistry.all.filter { it.id in selectedSnapshot }.map { it.column }.toSet()) }
                    var changesAlert: String? = null
                    if (selectedSnapshot.isNotEmpty()) {
                        val client = HealthConnectClient.getOrCreate(activity.applicationContext)
                        changesAlert = HealthChanges(activity.applicationContext, client, database.importDao()).scan(selectedSnapshot)
                        SelectedMetricBackfill(activity.applicationContext, client, database.importDao()).run(selectedSnapshot) { status = it }
                    }
                    val sync = SheetSync(gateway, RoomPendingQueue(database.importDao(), selectedSnapshot) { MetricSelection(activity.applicationContext).selected.first() }, syncLock)
                    var total = 0
                    var rounds = 0
                    do {
                        val count = sync.uploadBatch()
                        total += count
                        rounds++
                        if (count == 0) break
                        status = "Wysłano porcje obejmujące $total dni…"
                    } while (rounds < 10)
                    status = if (rounds >= 10) "Wysłano porcje obejmujące $total dni. Uruchom ponownie, aby kontynuować kolejkę." else "Synchronizacja zakończona. Przetworzono $total dni."
                    if (changesAlert != null) status += " $changesAlert"
                    }
                } catch (e: CancellationException) { failure = "Synchronizacja została przerwana. Kolejka jest zachowana."; throw e }
                catch (e: SheetHttpError) {
                    status = when (e.code) {
                        401, 403 -> "Dostęp do arkusza wygasł lub został odrzucony (HTTP ${e.code}). Sprawdź zgodę i konto."
                        404 -> "Arkusz lub zakładka daily są niedostępne (HTTP 404). Sprawdź dostęp albo wybierz inny arkusz. Kolejka jest zachowana."
                        else -> "Błąd Google Sheets (HTTP ${e.code}). Kolejka została zachowana; spróbuj ponownie później."
                    }
                    failure = status
                } catch (e: IllegalStateException) {
                    failure = "Nie ukończono synchronizacji. Sprawdź wybór danych i zgody."
                    status = "Nie ukończono synchronizacji: ${e.message ?: "sprawdź dostęp do danych"}. Zapisane dane zostały zachowane."
                } catch (_: Exception) {
                    failure = "Nie udało się wysłać danych. Sprawdź połączenie i arkusz."
                    status = "Synchronizacja zatrzymana. Sprawdź sieć i unikalność dat w arkuszu; kolejka została zachowana."
                } finally {
                    busy = false
                    BackgroundSettings(activity.applicationContext).log("Synchronizacja ręczna: $status")
                    manualRunId?.let { SyncJournal(activity.applicationContext).event(it, "Ręczna", failure ?: status, error = failure != null, complete = true) }
                }
            }
            return
        }
        if (action != "create") { busy = false; status = "Zgoda przyznana. Test nie tworzy arkusza."; return }
        scope.launch {
            var marked = false
            try {
                settings.markPending()
                marked = true
                val created = SheetsCreator().create(token, selected.toSet())
                settings.save(created.id, created.url)
                status = "Arkusz utworzony. Możesz rozpocząć synchronizację."
            } catch (e: CancellationException) {
                throw e
            } catch (e: SheetHttpError) {
                if (e.code in 400..499 && e.code != 408) {
                    settings.clearPending()
                    status = "Nie udało się utworzyć arkusza (HTTP ${e.code}). Sprawdź uprawnienia oraz włączenie Sheets API."
                } else status = "Nie wiadomo, czy arkusz został utworzony (HTTP ${e.code}). Sprawdź Dysk Google przed ponowną próbą."
            } catch (_: Exception) {
                status = if (marked) "Nie wiadomo, czy arkusz został utworzony. Sprawdź Dysk Google przed ponowną próbą." else "Nie udało się zapisać stanu tworzenia arkusza."
            } finally { busy = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.data == null) {
            busy = false
            status = "Okno Google zamknęło się bez wyniku (kod Android: ${result.resultCode})."
            recordAuthorizationFailure()
        } else try {
            authorized(client.getAuthorizationResultFromIntent(result.data).accessToken)
        } catch (e: ApiException) {
            busy = false
            status = "Autoryzacja nie powiodła się. Kod Google: ${e.statusCode}; wynik okna Android: ${result.resultCode}."
            recordAuthorizationFailure()
        } catch (e: Exception) {
            busy = false
            status = "Nie uzyskano zgody (${e.javaClass.simpleName})."
            recordAuthorizationFailure()
        }
    }

    fun authorize(requestedAction: String) {
        action = requestedAction
        busy = true
        if (requestedAction == "sync") status = "Przygotowywanie wysyłki do arkusza…"
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        client.authorize(request).addOnSuccessListener { response ->
            if (response.hasResolution()) {
                val pending = response.pendingIntent
                if (pending == null) { busy = false; status = "Nie można rozpocząć autoryzacji." }
                else try { launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                catch (e: Exception) { busy = false; status = "Nie można otworzyć okna autoryzacji (${e.javaClass.simpleName})." }
            } else authorized(response.accessToken)
        }.addOnFailureListener { error ->
            busy = false
            recordAuthorizationFailure()
            status = if (error is ApiException) "Błąd rozpoczęcia autoryzacji. Kod Google: ${error.statusCode}." else "Błąd rozpoczęcia autoryzacji (${error.javaClass.simpleName})."
        }
    }

    LaunchedEffect(syncRequest) { if (syncRequest > 0 && !busy) authorize("sync") }
    LaunchedEffect(status) { onStatus(status) }
    LaunchedEffect(busy) { onBusy(busy); onSyncBusy(busy && action == "sync") }

    if (visible && showSwitchChoice) AlertDialog(
        onDismissRequest = { showSwitchChoice = false },
        title = { Text("Zmienić arkusz?") },
        text = { Text("Wybierz, czy wysłać do nowego arkusza całą historię zapisaną na telefonie, czy tylko dane oczekujące w kolejce. Dotychczasowy arkusz pozostanie bez zmian.") },
        confirmButton = { TextButton(onClick = { showSwitchChoice = false; queueAllForSwitch = true; authorize("attach") }) { Text("Cała historia") } },
        dismissButton = { Column {
            TextButton(onClick = { showSwitchChoice = false; queueAllForSwitch = false; authorize("attach") }) { Text("Tylko kolejka") }
            TextButton(onClick = { showSwitchChoice = false }) { Text("Anuluj") }
        } }
    )
    if (visible) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { authorize("check") }) { Text("Sprawdź zgodę Google") }
        when (val state: SheetState? = sheet) {
            null -> Text("Sprawdzanie ustawień arkusza…")
            else -> when {
                state.id != null && state.url != null -> {
                    Text("Połączony arkusz")
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }) { Text("Otwórz arkusz") }

                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { pendingSheetId = state.id; queueAllForSwitch = false; authorize("attach") }) { Text("Sprawdź dostęp") }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { showSheetCheck = !showSheetCheck }) { Text("Wybierz inny arkusz") }
                }
                else -> {
                    if (state.creationPending) Text("Poprzednia próba mogła utworzyć arkusz. Sprawdź Dysk Google — kolejna próba jest wstrzymana, aby uniknąć duplikatu.")
                    else Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { authorize("create") }) { Text("Utwórz arkusz") }
                }
            }
        }
        if (sheet != null && (sheet?.id == null || showSheetCheck)) {
            OutlinedTextField(value = sheetReference, onValueChange = { sheetReference = it }, label = { Text("ID lub link istniejącego arkusza") }, singleLine = true, enabled = !busy && !importBusy)
            Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = {
                val id = spreadsheetId(sheetReference)
                if (id == null) status = "Podaj poprawne ID lub link arkusza Google Sheets."
                else if (sheet?.id != null && sheet?.id != id) { pendingSheetId = id; showSwitchChoice = true }
                else { pendingSheetId = id; queueAllForSwitch = false; authorize("attach") }
            }) { Text("Podłącz arkusz") }
        }
        Text(status)
    }
}
