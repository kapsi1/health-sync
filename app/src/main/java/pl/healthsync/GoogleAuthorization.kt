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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import pl.healthsync.data.SyncDatabase
import pl.healthsync.data.SheetGatewayHttp
import pl.healthsync.data.RoomPendingQueue
import pl.healthsync.domain.SheetSync
import pl.healthsync.domain.SheetBatchResult
import pl.healthsync.domain.planSheetMerge
import pl.healthsync.domain.spreadsheetId
import pl.healthsync.domain.pickedSpreadsheetId
import pl.healthsync.data.SheetHttpError
import pl.healthsync.data.SheetSettings
import pl.healthsync.data.SheetsCreator
import pl.healthsync.data.SelectedMetricBackfill
import pl.healthsync.data.HealthChanges
import androidx.health.connect.client.HealthConnectClient

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
internal val syncLock = Mutex()
internal val sheetOperationLock = Mutex()

@Composable
fun GoogleAuthorization(activity: Activity, selected: Set<String>, visible: Boolean, syncRequest: Int, manualRunId: String?, importBusy: Boolean, onStatus: (String) -> Unit, onBusy: (Boolean) -> Unit, onSyncBusy: (Boolean) -> Unit) {
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf("check") }
    var sheetReference by rememberSaveable { mutableStateOf("") }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var editError by remember { mutableStateOf("") }
    var nameUnavailable by remember { mutableStateOf(false) }
    var nameRefresh by remember { mutableIntStateOf(0) }
    var pendingSheetId by rememberSaveable { mutableStateOf("") }
    var showSwitchChoice by rememberSaveable { mutableStateOf(false) }
    var queueAllForSwitch by rememberSaveable { mutableStateOf(false) }
    var pendingPickerToken by remember { mutableStateOf<String?>(null) }
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
                    nameRefresh++
                    status = "Podłączono arkusz. Zaimportuj dane i uruchom synchronizację."
                } catch (e: CancellationException) { throw e }
                catch (e: SheetHttpError) {
                    status = when (e.code) {
                        401, 403 -> "Brak dostępu do tego arkusza (HTTP ${e.code}). Wpisanie ID nie przyznaje dostępu; wybierz plik z Dysku Google i przyznaj dostęp."
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
                    syncLock.withLock { gateway.reconcileColumns(MetricRegistry.managedColumns, MetricRegistry.all.filter { it.id in selectedSnapshot }.map { it.column }.toSet()) }
                    var changesAlert: String? = null
                    if (selectedSnapshot.isNotEmpty()) {
                        val client = HealthConnectClient.getOrCreate(activity.applicationContext)
                        changesAlert = HealthChanges(activity.applicationContext, client, database.importDao()).scan(selectedSnapshot)
                        SelectedMetricBackfill(activity.applicationContext, client, database.importDao()).run(selectedSnapshot) { status = it }
                    }
                    val sync = SheetSync(gateway, RoomPendingQueue(database.importDao(), selectedSnapshot) { MetricSelection(activity.applicationContext).selected.first() }, syncLock)
                    var total = SheetBatchResult(0, 0)
                    var rounds = 0
                    do {
                        val result = sync.uploadBatch()
                        total += result
                        rounds++
                        if (result.processed == 0) break
                        status = "Wysłano porcje obejmujące ${total.processed} dni…"
                    } while (rounds < 10)
                    val summary = "Dodano wierszy: ${total.added}; zapisano w istniejących: ${total.existing}."
                    status = if (rounds >= 10) "$summary Mogą pozostawać dane w kolejce; uruchom ponownie synchronizację." else "Synchronizacja zakończona. $summary"
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
                } catch (e: IllegalArgumentException) {
                    failure = "Nieprawidłowe nagłówki lub daty w arkuszu. Sprawdź zakładkę daily i usuń powtórzone nazwy."
                    status = failure!!
                } catch (e: IllegalStateException) {
                    failure = "Nie ukończono synchronizacji. Sprawdź wybór danych i zgody."
                    status = "Nie ukończono synchronizacji: ${e.message ?: "sprawdź dostęp do danych"}. Zapisane dane zostały zachowane."
                } catch (_: Exception) {
                    failure = "Nie udało się wysłać danych. Sprawdź połączenie i arkusz."
                    status = "Synchronizacja zatrzymana. Sprawdź sieć i unikalność dat w arkuszu; kolejka została zachowana."
                } finally {
                    busy = false
                    withContext(NonCancellable) {
                        val finalStatus = failure ?: status
                        BackgroundSettings(activity.applicationContext).log("Synchronizacja ręczna: $finalStatus")
                        manualRunId?.let { SyncJournal(activity.applicationContext).event(it, "Ręczna", finalStatus, error = failure != null, complete = true) }
                    }
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

    fun handleAuthorization(result: AuthorizationResult) {
        if (action != "picker") { authorized(result.accessToken); return }
        val id = pickedSpreadsheetId(result.tokenResponseParams?.getString("picked_file_ids"))
        if (id == null) {
            busy = false
            status = "Nie wybrano arkusza lub Google nie zwrócił poprawnego identyfikatora."
            return
        }
        if (result.accessToken.isNullOrBlank()) {
            busy = false
            status = "Nie otrzymano zgody Google na dostęp do wybranego arkusza."
            return
        }
        pendingSheetId = id
        if (sheet?.id != null && sheet?.id != id) {
            pendingPickerToken = result.accessToken
            busy = false
            showSwitchChoice = true
        } else {
            queueAllForSwitch = false
            action = "attach"
            authorized(result.accessToken)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.data == null) {
            busy = false
            status = "Okno Google zamknęło się bez wyniku (kod Android: ${result.resultCode})."
            recordAuthorizationFailure()
        } else try {
            handleAuthorization(client.getAuthorizationResultFromIntent(result.data))
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
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        if (requestedAction == "picker") {
            builder.setOptOutIncludingGrantedScopes(true)
                .setPrompt(AuthorizationRequest.Prompt.CONSENT or AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES, "application/vnd.google-apps.spreadsheet")
        }
        val request = builder.build()
        client.authorize(request).addOnSuccessListener { response ->
            if (response.hasResolution()) {
                val pending = response.pendingIntent
                if (pending == null) { busy = false; status = "Nie można rozpocząć autoryzacji." }
                else try { launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                catch (e: Exception) { busy = false; status = "Nie można otworzyć okna autoryzacji (${e.javaClass.simpleName})." }
            } else handleAuthorization(response)
        }.addOnFailureListener { error ->
            busy = false
            recordAuthorizationFailure()
            status = if (error is ApiException) "Błąd rozpoczęcia autoryzacji. Kod Google: ${error.statusCode}." else "Błąd rozpoczęcia autoryzacji (${error.javaClass.simpleName})."
        }
    }

    fun confirmSwitch(allHistory: Boolean) {
        showSwitchChoice = false
        queueAllForSwitch = allHistory
        val pickerToken = pendingPickerToken
        pendingPickerToken = null
        if (pickerToken == null) authorize("attach")
        else { action = "attach"; busy = true; authorized(pickerToken) }
    }

    LaunchedEffect(visible, sheet?.id, nameRefresh) {
        val id = sheet?.id
        if (!visible || id == null) return@LaunchedEffect
        nameUnavailable = false
        try {
            val token = suspendCancellableCoroutine<String?> { continuation ->
                val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
                client.authorize(request)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(if (it.hasResolution()) null else it.accessToken) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            if (token == null) { nameUnavailable = true; return@LaunchedEffect }
            val name = SheetGatewayHttp(id, token).spreadsheetName()
            settings.saveNameIfCurrent(id, name)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { nameUnavailable = true }
    }
    LaunchedEffect(syncRequest) { if (syncRequest > 0 && !busy) authorize("sync") }
    LaunchedEffect(status) { onStatus(status) }
    LaunchedEffect(busy) { onBusy(busy); onSyncBusy(busy && action == "sync") }

    if (visible && showEditSheet) AlertDialog(
        onDismissRequest = { showEditSheet = false; editError = "" },
        title = { Text("Podłącz arkusz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sheetReference, onValueChange = { sheetReference = it; editError = "" },
                    label = { Text("ID lub link arkusza") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, isError = editError.isNotEmpty())
                if (editError.isNotEmpty()) Text(editError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = {
            val id = spreadsheetId(sheetReference)
            if (id == null) editError = "Podaj poprawne ID lub link arkusza Google Sheets."
            else {
                showEditSheet = false
                editError = ""
                if (sheet?.id == id) status = ""
                else if (sheet?.id != null) { pendingSheetId = id; pendingPickerToken = null; showSwitchChoice = true }
                else { pendingSheetId = id; queueAllForSwitch = false; authorize("attach") }
            }
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { showEditSheet = false; editError = "" }) { Text("Anuluj") } }
    )
    if (visible && showSwitchChoice) AlertDialog(
        onDismissRequest = { showSwitchChoice = false; pendingPickerToken = null },
        title = { Text("Zmienić arkusz?") },
        text = { Text("Wybierz, czy wysłać do nowego arkusza całą historię zapisaną na telefonie, czy tylko dane oczekujące w kolejce. Dotychczasowy arkusz pozostanie bez zmian.") },
        confirmButton = { TextButton(onClick = { confirmSwitch(true) }) { Text("Cała historia") } },
        dismissButton = { Column {
            TextButton(onClick = { confirmSwitch(false) }) { Text("Tylko kolejka") }
            TextButton(onClick = { showSwitchChoice = false; pendingPickerToken = null }) { Text("Anuluj") }
        } }
    )
    if (visible) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val state = sheet
        if (state == null) {
            Text("Sprawdzanie ustawień arkusza…")
        } else {
            Text(if (state.id == null) "Brak podłączonego arkusza:" else "Podłączony arkusz:")
            OutlinedTextField(
                value = state.id.orEmpty(), onValueChange = {}, readOnly = true,
                label = { Text("ID arkusza") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = {
                    IconButton(enabled = !busy && !importBusy, onClick = { sheetReference = state.id.orEmpty(); editError = ""; showEditSheet = true }) {
                        Icon(painterResource(R.drawable.edit_pencil), contentDescription = "Edytuj ID lub link arkusza")
                    }
                }
            )
            if (state.id != null) {
                OutlinedTextField(value = state.name ?: if (nameUnavailable) "Nie udało się pobrać nazwy" else "Pobieranie…", onValueChange = {}, readOnly = true,
                    label = { Text(if (nameUnavailable && state.name != null) "Nazwa arkusza — ostatnio zapisana" else "Nazwa arkusza") },
                    modifier = Modifier.fillMaxWidth())
            }
            if (state.id != null && state.url != null) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }) { Text("Otwórz arkusz") }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { authorize("picker") }) { Text("Wybierz z Dysku Google") }
            if (state.creationPending) {
                Text("Poprzednia próba mogła utworzyć arkusz. Sprawdź Dysk Google — kolejna próba jest wstrzymana, aby uniknąć duplikatu.")
            } else if (state.id == null) {
                Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && !importBusy, onClick = { authorize("create") }) { Text("Utwórz arkusz") }
            }
        }
        if (status.isNotEmpty()) Text(status)
    }
}
