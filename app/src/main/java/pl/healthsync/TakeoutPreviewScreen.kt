package pl.healthsync

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.healthsync.data.TakeoutInspector
import pl.healthsync.data.TakeoutPreview
import pl.healthsync.data.TakeoutType
import pl.healthsync.data.takeoutReadPermissions
import pl.healthsync.data.takeoutWritePermissions
import pl.healthsync.data.readVerifiedTakeout
import pl.healthsync.data.TakeoutImportService
import pl.healthsync.data.TakeoutJournalDatabase
import pl.healthsync.data.TakeoutActivityInspector
import pl.healthsync.data.ActivityPreviewDay
import pl.healthsync.data.ActivityPreviewStatus
import pl.healthsync.data.activityReadPermissions
import pl.healthsync.domain.ImportDecision
import pl.healthsync.domain.TakeoutArchive
import pl.healthsync.domain.TakeoutActivityKind
import pl.healthsync.domain.readTakeoutActivityArchive
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

/** Analysis is read-only; writing requires a separate explicit action. */
@Composable
internal fun TakeoutPreviewScreen(client: HealthConnectClient, onClose: () -> Unit, onQueueReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.of("Europe/Warsaw")
    val today = LocalDate.now(zone)
    var start by remember { mutableStateOf(today.minusMonths(6)) }
    var end by remember { mutableStateOf(today) }
    var types by remember { mutableStateOf(setOf(TakeoutType.SLEEP, TakeoutType.WEIGHT, TakeoutType.FAT)) }
    var archive by remember { mutableStateOf<TakeoutArchive?>(null) }
    var archiveUri by remember { mutableStateOf<Uri?>(null) }
    var archiveDigest by remember { mutableStateOf<String?>(null) }
    var accountLabel by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<TakeoutPreview?>(null) }
    var activityPreview by remember { mutableStateOf<Map<TakeoutActivityKind, List<ActivityPreviewDay>>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf<String?>(null) }
    val journal = remember { TakeoutJournalDatabase.get(context.applicationContext).journal() }
    val sessions by journal.observeSessions().collectAsState(initial = emptyList())
    val service = remember(client) { TakeoutImportService(context.applicationContext, client) }
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        message = "Zgody zaktualizowane. Ponów wybraną operację."
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true; preview = null; activityPreview = null; archive = null; archiveUri = null; archiveDigest = null; message = ""
            try {
                val verified = withContext(Dispatchers.IO) { readVerifiedTakeout(context, uri) }
                archive = verified.archive; archiveUri = uri; archiveDigest = verified.digest
                if (archive!!.foundTypes.isEmpty()) message = "W archiwum nie znaleziono obsługiwanych plików Google Health."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: "Nieprawidłowe archiwum." else "Nie można odczytać archiwum (${e.javaClass.simpleName})."
            } finally { busy = false }
        }
    }
    fun datePicker(value: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, year, month, day -> set(LocalDate.of(year, month + 1, day)); preview = null; activityPreview = null }, value.year, value.monthValue - 1, value.dayOfMonth).apply {
            datePicker.minDate = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }
    val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    Text("Analiza Google Takeout", style = MaterialTheme.typography.titleLarge)
    Text("Archiwum jest odczytywane na telefonie. Obsługiwane są sen, waga, tkanka tłuszczowa i wzrost. Przed zapisem zobaczysz podgląd.", style = MaterialTheme.typography.bodySmall)
    val active = sessions.firstOrNull { it.status == "active" }
    val undoing = sessions.firstOrNull { it.status == "undoing" }
    if (active != null || undoing != null) {
        val session = active ?: undoing!!
        Text(if (active != null) "Przerwany import: ${session.startDate} – ${session.endDate}." else "Przerwane wycofywanie importu: ${session.startDate} – ${session.endDate}.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            scope.launch {
                val types = session.selectedTypes.split(",").map { TakeoutType.valueOf(it) }.toSet()
                val missing = takeoutWritePermissions(types) - client.permissionController.getGrantedPermissions()
                if (missing.isNotEmpty()) { permissionLauncher.launch(missing); return@launch }
                busy = true; message = ""; progressDone = 0; progressTotal = 0
                try {
                    if (active != null) {
                        val count = withContext(Dispatchers.IO) { service.resume(session.id) { done, total -> progressDone = done; progressTotal = total } }
                        message = "Import wznowiony i zakończony: $count rekordów. Dane przygotowano do synchronizacji."
                        onQueueReady()
                    } else {
                        withContext(Dispatchers.IO) { service.undoConnect(session.id) }
                        message = "Wycofano własne rekordy. Niepewne wartości w arkuszu pozostawiono."
                        onQueueReady()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = "Nie udało się wznowić: ${e.message ?: e.javaClass.simpleName}."
                } finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (active != null) "Wznów import" else "Wznów wycofywanie") }
    }
    OutlinedButton(onClick = { fileLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (archive == null) "Wybierz archiwum ZIP" else "Wybierz inne archiwum") }
    archive?.let { data ->
        Text("W archiwum: sen ${data.sleeps.size}, waga ${data.weights.size}, tkanka tłuszczowa ${data.bodyFat.size}, wzrost ${data.heights.size}.", style = MaterialTheme.typography.bodySmall)
        if (data.sleeps.isNotEmpty() && data.thirdPartySleepIds == null) Text("Brak pliku źródeł snu: nie można bezpiecznie ustalić, czy sesje pochodzą z zewnętrznych aplikacji.", style = MaterialTheme.typography.bodySmall)
        Text("Porównaj dane", style = MaterialTheme.typography.titleMedium)
        TakeoutType.entries.forEach { type ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = type in types, enabled = !busy, onCheckedChange = { checked ->
                    types = if (checked) types + type else types - type; preview = null
                })
                Text(type.label)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { datePicker(start) { start = it } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Od ${start.format(dateFormat)}") }
            OutlinedButton(onClick = { datePicker(end) { end = it } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Do ${end.format(dateFormat)}") }
        }
        Button(onClick = {
            if (types.isEmpty() || start > end) message = "Wybierz rodzaje danych i poprawny zakres dat."
            else scope.launch {
                busy = true; preview = null; message = ""
                try {
                    val missing = takeoutReadPermissions(types) - client.permissionController.getGrantedPermissions()
                    if (missing.isNotEmpty()) {
                        busy = false
                        permissionLauncher.launch(missing)
                        return@launch
                    }
                    preview = withContext(Dispatchers.IO) { TakeoutInspector(client).inspect(data, types, start, end) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: "Nie udało się porównać danych." else "Nie udało się odczytać Health Connect (${e.javaClass.simpleName}). Sprawdź zgody i spróbuj ponownie."
                } finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Porównaj z Health Connect") }
        OutlinedButton(onClick = {
            scope.launch {
                val kinds = TakeoutActivityKind.entries.toSet()
                val missing = activityReadPermissions(kinds) - client.permissionController.getGrantedPermissions()
                if (missing.isNotEmpty()) { permissionLauncher.launch(missing); return@launch }
                busy = true; activityPreview = null; message = ""
                try {
                    val uri = requireNotNull(archiveUri)
                    val estimates = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { readTakeoutActivityArchive(it, kinds, start, end, zone) }
                            ?: error("Nie można otworzyć archiwum")
                    }
                    activityPreview = withContext(Dispatchers.IO) { TakeoutActivityInspector(client).inspect(estimates, start, end) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = "Nie udało się przeanalizować aktywności: ${e.message ?: e.javaClass.simpleName}."
                } finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Analizuj aktywność (bez zapisu)") }
    }
    if (busy) {
        Text("Nie wychodź z aplikacji", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (progressTotal > 0) {
                LinearProgressIndicator(progress = { progressDone.toFloat() / progressTotal }, modifier = Modifier.weight(1f))
                Text("$progressDone/$progressTotal", style = MaterialTheme.typography.bodySmall)
            } else LinearProgressIndicator(modifier = Modifier.weight(1f))
        }
    }
    if (message.isNotBlank()) Text(message, color = if (message.startsWith("Import zakończony") || message.startsWith("Import wznowiony") || message.startsWith("Wycofano") || message.startsWith("Zgody zaktualizowane")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    activityPreview?.let { result ->
        Text("Aktywność — analiza", style = MaterialTheme.typography.titleMedium)
        Text("Wyniki są szacunkowe; priorytet: opaska, telefon, Health, Fit. Różne wpisy kalorii w tej samej minucie są sumowane. Wpisy z Connect są pomijane. Analiza niczego nie zapisuje.", style = MaterialTheme.typography.bodySmall)
        if (result.isEmpty()) Text("Nie znaleziono danych aktywności w wybranym zakresie.", style = MaterialTheme.typography.bodySmall)
        result.forEach { (kind, days) ->
            val label = when (kind) { TakeoutActivityKind.STEPS -> "Kroki"; TakeoutActivityKind.DISTANCE -> "Dystans"; TakeoutActivityKind.TOTAL_CALORIES -> "Spalone kalorie"; TakeoutActivityKind.ACTIVE_CALORIES -> "Aktywne kalorie" }
            Text("$label: szacunków ${days.count { it.status == ActivityPreviewStatus.ESTIMATED }}, dni obecnych w Connect ${days.count { it.status == ActivityPreviewStatus.PRESENT_IN_CONNECT }}, niejednoznacznych ${days.count { it.status == ActivityPreviewStatus.AMBIGUOUS }}.", style = MaterialTheme.typography.bodySmall)
            var expanded by remember(result, kind) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ukryj dni" else "Pokaż dni: $label") }
            if (expanded) days.forEach { day ->
                val details = when (day.status) {
                    ActivityPreviewStatus.PRESENT_IN_CONNECT -> "Dane w Connect — pominięto"
                    ActivityPreviewStatus.AMBIGUOUS -> "Pominięto: ${day.estimate.reason ?: "niejednoznaczne dane"}"
                    ActivityPreviewStatus.ESTIMATED -> {
                        val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL")).apply { maximumFractionDigits = if (kind == TakeoutActivityKind.DISTANCE) 3 else 0 }
                        val unit = when (kind) { TakeoutActivityKind.STEPS -> "kroków"; TakeoutActivityKind.DISTANCE -> "km"; else -> "kcal" }
                        val amount = requireNotNull(day.estimate.value) / if (kind == TakeoutActivityKind.DISTANCE) 1000.0 else 1.0
                        "Szacunkowa suma: ${number.format(amount)} $unit"
                    }
                }
                Text("${day.date.format(dateFormat)} · $details", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    preview?.let { result ->
        Text("Wynik porównania", style = MaterialTheme.typography.titleMedium)
        result.counts.forEach { (type, counts) ->
            fun count(decision: ImportDecision) = counts[decision] ?: 0
            val replaced = if (type == TakeoutType.SLEEP) ", pominiętych wersji v1 ${count(ImportDecision.SUPERSEDED)}" else ""
            Text("${type.label}: kandydatów do importu ${count(ImportDecision.INSERT)}, istniejących ${count(ImportDecision.ALREADY_PRESENT)}, konfliktów ${count(ImportDecision.CONFLICT)}, z Connect ${count(ImportDecision.ORIGINATES_IN_CONNECT)}, nieznane źródło ${count(ImportDecision.UNKNOWN_SOURCE)}$replaced.", style = MaterialTheme.typography.bodySmall)
        }
        if (result.sleepConflicts.isNotEmpty()) {
            Text("Przyczyny konfliktów snu", style = MaterialTheme.typography.titleSmall)
            result.sleepConflicts.forEach { (reason, count) -> Text("${reason.label}: $count", style = MaterialTheme.typography.bodySmall) }
        }
        if (result.sleepOverlapGroups.isNotEmpty()) {
            val connect = result.sleepConnectComparison
            Text("W parach v1/v2 wybrano v2. Dane Health mogą pokazywać inny czas snu. Dla wszystkich wersji w eksporcie: ${connect[ImportDecision.INSERT] ?: 0} sesji nie nakłada się na Connect; ${connect[ImportDecision.CONFLICT] ?: 0} wymaga sprawdzenia względem Connect.", style = MaterialTheme.typography.bodySmall)
            var showOverlaps by remember(result) { mutableStateOf(false) }
            TextButton(onClick = { showOverlaps = !showOverlaps }) { Text(if (showOverlaps) "Ukryj sesje" else "Pokaż nakładające się sesje (${result.sleepOverlapGroups.size})") }
            if (showOverlaps) {
                val dateTime = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withZone(zone)
                result.sleepOverlapGroups.forEach { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { entry ->
                                val s = entry.session
                                val status = when (entry.connect.decision) {
                                    ImportDecision.INSERT -> "Brak nakładającej się sesji w Connect"
                                    ImportDecision.ALREADY_PRESENT -> "Sesja jest już w Connect"
                                    else -> entry.connect.reason?.label ?: "Wymaga sprawdzenia"
                                }
                                Text("${dateTime.format(s.start)} – ${dateTime.format(s.end)}\nSen: ${s.minutesAsleep ?: "brak"} min · Aktualizacja: ${dateTime.format(s.lastUpdated)}\nAlgorytm: ${s.algorithmVersion ?: "brak danych"}\n$status", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (result.candidates.isNotEmpty()) {
            OutlinedTextField(value = accountLabel, onValueChange = { accountLabel = it }, enabled = !busy,
                label = { Text("Konto źródłowe") }, supportingText = { Text("Podaj nazwę lub adres konta z eksportu. Używaj tej samej nazwy przy kolejnych eksportach.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                if (accountLabel.isBlank()) message = "Podaj nazwę konta źródłowego."
                else if (active != null || undoing != null) message = "Najpierw wznów przerwaną operację."
                else confirmImport = true
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Importuj ${result.candidates.size} rekordów do Connect") }
        }
    }
    val completed = sessions.filter { it.status == "complete" }
    if (active == null && undoing == null) completed.forEach { session ->
        OutlinedButton(onClick = { confirmUndo = session.id }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Wycofaj import ${session.startDate} – ${session.endDate}") }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { Text("Import do Health Connect") },
        text = { Text("Zostaną dodane tylko kandydaty z podglądu. Sesje snu v2 mogą różnić się od podsumowania w Health. Konflikty zostaną pominięte. Dostęp do archiwum zostanie zachowany, aby umożliwić wznowienie i wycofanie.") },
        confirmButton = { TextButton(onClick = {
            confirmImport = false
            val uri = archiveUri; val digest = archiveDigest; val selectedPreview = preview
            if (uri == null || digest == null || selectedPreview == null) message = "Wybierz archiwum i ponownie wykonaj porównanie."
            else scope.launch {
                try {
                    val missing = takeoutWritePermissions(types) - client.permissionController.getGrantedPermissions()
                    if (missing.isNotEmpty()) { permissionLauncher.launch(missing); return@launch }
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    busy = true; message = ""; progressDone = 0; progressTotal = 0
                    val count = withContext(Dispatchers.IO) { service.start(uri, digest, types, start, end, accountLabel, selectedPreview) { done, total -> progressDone = done; progressTotal = total } }
                    message = "Import zakończony: $count rekordów. Dane przygotowano do synchronizacji."
                    preview = null; onQueueReady()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = "Import zatrzymany: ${e.message ?: e.javaClass.simpleName}."
                } finally { busy = false }
            }
        }) { Text("Importuj") } }, dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Anuluj") } })
    confirmUndo?.let { sessionId -> AlertDialog(onDismissRequest = { confirmUndo = null }, title = { Text("Wycofaj import") },
        text = { Text("Usunięte zostaną tylko niezmienione rekordy zapisane przez Health Sync w tym imporcie. Komórki arkusza, których pochodzenia nie można potwierdzić, pozostaną bez zmian. Warto zachować kopię Connect przed wycofaniem.") },
        confirmButton = { TextButton(onClick = {
            confirmUndo = null
            scope.launch {
                val session = sessions.firstOrNull { it.id == sessionId } ?: return@launch
                val types = session.selectedTypes.split(",").map { TakeoutType.valueOf(it) }.toSet()
                val missing = takeoutWritePermissions(types) - client.permissionController.getGrantedPermissions()
                if (missing.isNotEmpty()) { permissionLauncher.launch(missing); return@launch }
                busy = true; message = ""
                try {
                    withContext(Dispatchers.IO) { service.undoConnect(sessionId) }
                    message = "Wycofano własne rekordy. Niepewne wartości w arkuszu pozostawiono."
                    onQueueReady()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = "Wycofywanie zatrzymane: ${e.message ?: e.javaClass.simpleName}."
                } finally { busy = false }
            }
        }) { Text("Wycofaj") } }, dismissButton = { TextButton(onClick = { confirmUndo = null }) { Text("Anuluj") } }) }
    TextButton(onClick = onClose, enabled = !busy) { Text("Wróć") }
}
