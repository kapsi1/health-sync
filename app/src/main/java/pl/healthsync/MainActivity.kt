package pl.healthsync

import android.app.DatePickerDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import pl.healthsync.data.SheetSettings
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID
import pl.healthsync.data.HealthHistorySource
import pl.healthsync.data.RoomHistoryStore
import pl.healthsync.data.SyncDatabase
import pl.healthsync.domain.HistoryImport
import pl.healthsync.domain.chunks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.temporal.ChronoUnit

private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
private val caloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(this)
                else darkColorScheme(background = Color(0xFF0D1117), surface = Color(0xFF161E29), primary = Color(0xFF8AB9F4), onPrimary = Color(0xFF0D294B))
            MaterialTheme(colorScheme = colors) { DiagnosticScreen() }
        }
    }

    @Composable
    private fun DiagnosticScreen() {
        val scope = rememberCoroutineScope()
        val status = remember { HealthConnectClient.getSdkStatus(this) }
        val client = remember(status) { if (status == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(this) else null }
        var result by remember { mutableStateOf("Naciśnij „Sprawdź”, aby odczytać status i dostępność danych.") }
        var historyStart by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Warsaw")).toString()) }
        var historyStatus by remember { mutableStateOf("") }
        var progressDone by remember { mutableIntStateOf(0) }
        var progressTotal by remember { mutableIntStateOf(0) }
        var historyJob by remember { mutableStateOf<Job?>(null) }
        val importer = remember(client) { client?.let { HistoryImport(HealthHistorySource(it), RoomHistoryStore(SyncDatabase.get(applicationContext).importDao())) } }
        val selections = remember { MetricSelection(applicationContext) }
        val selected by selections.selected.collectAsState(initial = emptySet())
        val backgroundSettings = remember { BackgroundSettings(applicationContext) }
        val background by backgroundSettings.state.collectAsState(initial = BackgroundState(0, "Sprawdzanie ustawień…"))
        val savedStart by backgroundSettings.startDate.collectAsState(initial = null)
        var dateLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(savedStart) { if (!dateLoaded && savedStart != null) { historyStart = savedStart!!; dateLoaded = true } }
        val journal = remember { SyncJournal(applicationContext) }
        val runs by journal.runs.collectAsState(initial = emptyList())
        var clockNow by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); clockNow = Instant.now() } }
        var manualRunId by remember { mutableStateOf<String?>(null) }
        var syncRequest by remember { mutableIntStateOf(0) }
        var googleBusy by remember { mutableStateOf(false) }
        var syncBusy by remember { mutableStateOf(false) }
        val importDao = remember { SyncDatabase.get(applicationContext).importDao() }
        val pendingImport by importDao.observeManualSession().collectAsState(initial = null)
        val canResume = pendingImport?.let { it.start == historyStart && it.metricIds.split(",").toSet() == selected } == true
        val sheetSettings = remember { SheetSettings(applicationContext) }
        val currentSheet by sheetSettings.state.collectAsState(initial = null)
        var pendingPermission by remember { mutableStateOf("") }
        var permissionResult by remember { mutableIntStateOf(0) }
        var requestedHours by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var granted by remember { mutableStateOf(emptySet<String>()) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, client) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && client != null) scope.launch {
                    try { granted = client.permissionController.getGrantedPermissions() } catch (_: Exception) { granted = emptySet() }
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            if (client != null) scope.launch { granted = client.permissionController.getGrantedPermissions(); permissionResult++ }
            result = "Zgody zaktualizowane. Naciśnij „Sprawdź”."
        }
        Scaffold(bottomBar = {
            Column {
            if (tab != 0 && (historyJob != null || syncBusy)) {
                Surface(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Wróć do synchronizacji" }.clickable { tab = 0 }, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (historyJob != null) {
                            LinearProgressIndicator(progress = { if (progressTotal > 0) progressDone.toFloat() / progressTotal else 0f }, modifier = Modifier.weight(1f))
                            Text("$progressDone/$progressTotal", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            BoxWithConstraints {
                val measurer = rememberTextMeasurer()
                val labelStyle = MaterialTheme.typography.labelSmall
                val availableWidth = with(LocalDensity.current) { (maxWidth / 4 - 16.dp).roundToPx() }
                val syncLabel = if (measurer.measure("Synchronizuj", style = labelStyle).size.width <= availableWidth) "Synchronizuj" else "Sync"
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
                    listOf(syncLabel, "Dane", "Arkusz", "Dziennik").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier.weight(1f).height(56.dp)
                                .background(if (tab == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, shape = MaterialTheme.shapes.small)
                                .selectable(selected = tab == index, role = Role.Tab, onClick = { tab = index }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                color = if (tab == index) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                }
            }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(painterResource(R.drawable.health_sync_mark), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Text("Health Sync", style = MaterialTheme.typography.headlineSmall)
            }
            if (client == null) Text(when (status) {
                HealthConnectClient.SDK_AVAILABLE -> "Health Connect: dostępny"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect: wymaga instalacji lub aktualizacji"
                else -> "Health Connect: niedostępny na tym urządzeniu"
            })
            if (client != null) {
                if (tab == 1) {
                val missing = MetricRegistry.all.filter { it.id in selected && it.permission !in granted }.map { it.permission }.toSet()
                if (missing.isNotEmpty()) {
                    Text("Brakuje zgód dla wybranych danych.")
                    Button(onClick = { launcher.launch(missing) }) { Text("Przyznaj brakujące zgody") }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wybierz wszystkie", style = MaterialTheme.typography.titleMedium)
                            Text("Wybrano ${selected.size} z ${MetricRegistry.all.size}", style = MaterialTheme.typography.bodySmall)
                        }
                        SelectionSwitch(selected.size, MetricRegistry.all.size) { scope.launch { selections.set(if (selected.size == MetricRegistry.all.size) emptySet() else MetricRegistry.all.map { it.id }.toSet()) } }
                    }
                }
                MetricRegistry.all.groupBy { it.category }.forEach { (category, metrics) ->
                    var expanded by rememberSaveable(category) { mutableStateOf(metrics.any { it.id in selected && it.permission !in granted }) }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
                                Column {
                                    Text(category, style = MaterialTheme.typography.titleMedium)
                                    Text("Wybrano ${metrics.count { it.id in selected }} z ${metrics.size}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            SelectionSwitch(metrics.count { it.id in selected }, metrics.size) { scope.launch { selections.set(if (metrics.all { it.id in selected }) selected - metrics.map { it.id }.toSet() else selected + metrics.map { it.id }) } }
                        }
                        if (expanded) metrics.forEach { metric ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) { Text(metric.label); if (metric.id in selected && metric.permission !in granted) Text("Brak zgody", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                Switch(checked = metric.id in selected, onCheckedChange = { checked -> scope.launch { selections.toggle(metric.id, checked) } }, thumbContent = if (metric.id in selected) {{ Text("✓", style = MaterialTheme.typography.labelSmall) }} else null)
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    try { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                    catch (_: ActivityNotFoundException) { result = "Nie można otworzyć Health Connect. Znajdź go w ustawieniach telefonu." }
                }) { Text("Otwórz ustawienia Health Connect") }
                if (result.startsWith("Nie można otworzyć")) Text(result, style = MaterialTheme.typography.bodySmall)

                }
                if (tab == 0) {
                Text("Importuj od", style = MaterialTheme.typography.labelLarge)
                val chosenDate = LocalDate.parse(historyStart)
                val displayedDate = chosenDate.format(DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("pl-PL")))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                    val dialog = DatePickerDialog(this@MainActivity, { _, year, month, day ->
                        historyStart = LocalDate.of(year, month + 1, day).toString()
                        dateLoaded = true
                        scope.launch { backgroundSettings.saveStartDate(historyStart) }
                    }, chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth)
                    val deviceZone = ZoneId.systemDefault()
                    dialog.datePicker.minDate = LocalDate.of(1900, 1, 1).atStartOfDay(deviceZone).toInstant().toEpochMilli()
                    dialog.datePicker.maxDate = today.atStartOfDay(deviceZone).toInstant().toEpochMilli()
                    dialog.show()
                }, enabled = historyJob == null && !googleBusy, modifier = Modifier.weight(1f)) { Text(displayedDate, maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = {
                    historyStart = LocalDate.now(ZoneId.of("Europe/Warsaw")).toString()
                    dateLoaded = true
                    scope.launch { backgroundSettings.saveStartDate(historyStart) }
                }, enabled = historyJob == null && !googleBusy) { Text("Dzisiaj", maxLines = 1, softWrap = false) }
                }
                fun startImport() {
                    val start = try { LocalDate.parse(historyStart) } catch (_: Exception) { historyStatus = "Wpisz poprawną datę w formacie RRRR-MM-DD."; return }
                    val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                    if (start.year < 1900 || start > today) { historyStatus = "Data musi mieścić się między 1900-01-01 a dzisiaj."; return }
                    val metrics = MetricRegistry.all.filter { it.id in selected }
                    if (metrics.isEmpty()) { historyStatus = "Wybierz przynajmniej jedną statystykę."; return }
                    historyStatus = ""
                    progressDone = 0
                    progressTotal = metrics.size * chunks(start, today.plusDays(1)).size
                    val runId = UUID.randomUUID().toString()
                    manualRunId = runId
                    historyJob = scope.launch {
                        try {
                            journal.event(runId, "Ręczna", "Rozpoczęto import od $start.")
                            val current = client.permissionController.getGrantedPermissions()
                            granted = current
                            check(metrics.all { it.permission in current }) { "Przyznaj zgody dla wszystkich wybranych statystyk." }
                            val earliestRead = if (metrics.any { it.id == "sleep_duration" }) start.minusDays(7) else start
                            if (earliestRead < today.minusDays(28)) {
                                check(available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) && HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in current) { "Przyznaj zgodę na starszą historię albo wybierz późniejszą datę." }
                            }
                            importer!!.runManual(metrics.map { it.id }, start, today.plusDays(1)) { progress ->
                                progressDone = progress.processed
                                progressTotal = progress.total
                            }
                            backgroundSettings.log("Rozpoczęto synchronizację ręczną.")
                            journal.event(runId, "Ręczna", "Import zakończony, dane zapisano lokalnie.")
                            syncRequest++
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            historyStatus = "Import przerwany. Zapisane porcje można wznowić."
                            withContext(NonCancellable) { journal.event(runId, "Ręczna", "Import przerwany. Zapisane dane zachowano.", error = true, complete = true) }
                            throw e
                        } catch (e: Exception) {
                            historyStatus = "Import zatrzymany: ${e.message ?: e.javaClass.simpleName}. Zapisane porcje można wznowić."
                            journal.event(runId, "Ręczna", "Import zatrzymany (${e.javaClass.simpleName}). Sprawdź zgody i spróbuj ponownie.", error = true, complete = true)
                        } finally { historyJob = null }
                    }
                }
                fun beginManual() {
                    historyStatus = ""
                    if (currentSheet?.id == null) { historyStatus = "Najpierw utwórz lub podłącz arkusz w zakładce Arkusz."; return }
                    val metrics = MetricRegistry.all.filter { it.id in selected }
                    val start = LocalDate.parse(historyStart)
                    val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                    val earliest = if (metrics.any { it.id == "sleep_duration" }) start.minusDays(7) else start
                    val missing = metrics.map { it.permission }.filterNot { it in granted }.toMutableSet()
                    if (earliest < today.minusDays(28) && HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY !in granted) {
                        if (!available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) { historyStatus = "Odczyt starszej historii nie jest dostępny na tym urządzeniu."; return }
                        missing += HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
                    }
                    if (missing.isNotEmpty()) { pendingPermission = "manual"; launcher.launch(missing) }
                    else startImport()
                }
                LaunchedEffect(permissionResult) {
                    if (permissionResult > 0 && pendingPermission == "manual") {
                        pendingPermission = ""
                        val metrics = MetricRegistry.all.filter { it.id in selected }
                        val start = LocalDate.parse(historyStart)
                        val earliest = if (metrics.any { it.id == "sleep_duration" }) start.minusDays(7) else start
                        val historyNeeded = earliest < LocalDate.now(ZoneId.of("Europe/Warsaw")).minusDays(28)
                        if (metrics.all { it.permission in granted } && (!historyNeeded || HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted)) startImport()
                        else historyStatus = "Nie przyznano wszystkich wymaganych zgód Health Connect. Sprawdź je i spróbuj ponownie."
                    }
                    if (permissionResult > 0 && pendingPermission == "background") {
                        pendingPermission = ""
                        if (HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted) backgroundSettings.configure(requestedHours)
                        else historyStatus = "Nie przyznano zgody na odczyt w tle. Synchronizacja automatyczna nie została włączona."
                    }
                }
                if (pendingImport != null && historyJob == null && !syncBusy) {
                    Text(if (canResume) "Poprzedni import został przerwany. Możesz go wznowić." else "Istnieje przerwany import z inną datą lub wyborem danych. Rozpoczęcie nowego importu zastąpi możliwość jego wznowienia; zapisane dane pozostaną.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { beginManual() }, enabled = historyJob == null && !googleBusy, modifier = Modifier.fillMaxWidth()) { Text(if (historyJob != null || googleBusy) "Synchronizowanie…" else if (canResume) "Wznów import i synchronizuj" else "Importuj i synchronizuj") }
                if (historyJob != null) {
                    Text("Nie wychodź z aplikacji", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { if (progressTotal > 0) progressDone.toFloat() / progressTotal else 0f }, modifier = Modifier.weight(1f))
                        Text("$progressDone/$progressTotal", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { historyJob?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Przerwij import") }
                } else if (googleBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (historyStatus.isNotBlank()) {
                    Text(historyStatus, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun configureAuto(hours: Int) {
                            if (hours != 0 && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in granted) {
                                if (available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)) {
                                    requestedHours = hours; pendingPermission = "background"
                                    launcher.launch(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))
                                } else historyStatus = "Odczyt w tle nie jest dostępny na tym urządzeniu."
                            } else scope.launch { backgroundSettings.configure(hours) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Synchronizuj w tle", modifier = Modifier.weight(1f).padding(top = 12.dp))
                            Switch(checked = background.hours != 0, onCheckedChange = { configureAuto(if (it) 3 else 0) })
                        }
                        if (background.hours != 0) {
                            Text("Częstotliwość", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf(1 to "1 h", 3 to "3 h", 12 to "12 h", 24 to "24 h").forEach { (hours, label) ->
                                    FilterChip(selected = background.hours == hours, onClick = { configureAuto(hours) }, label = { Text(label) })
                                }
                            }
                        }
                        val lastAuto = runs.filter { it.kind == "Automatyczna" && it.complete && it.events.isNotEmpty() }
                            .maxByOrNull { it.events.last().at }
                        val lastTime = lastAuto?.events?.lastOrNull()?.at?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        val label = if (lastAuto?.lastError != false) "Ostatnia próba" else "Ostatnia"
                        Text(if (lastTime == null) "Brak zapisanej automatycznej synchronizacji" else "$label: ${relativeTime(lastTime, clockNow)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
                GoogleAuthorization(this@MainActivity, selected, tab == 2, syncRequest, manualRunId, historyJob != null, { if (syncRequest > 0) historyStatus = if (isGoogleError(it)) it else "" }, { googleBusy = it }, { syncBusy = it })
                if (tab == 3) {
                    var errorsOnly by rememberSaveable { mutableStateOf(false) }
                    val clipboard = LocalClipboardManager.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !errorsOnly, onClick = { errorsOnly = false }, label = { Text("Wszystkie") })
                        FilterChip(selected = errorsOnly, onClick = { errorsOnly = true }, label = { Text("Błędy") })
                    }
                    val visibleRuns = runs.filter { !errorsOnly || it.error }
                    if (visibleRuns.isEmpty()) Text(if (errorsOnly) "Brak zapisanych błędów." else "Brak nowych uruchomień synchronizacji.")
                    visibleRuns.forEach { run ->
                        var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text("${if (expanded) "▴" else "▾"}  ${run.kind} · ${if (!run.complete) "w toku" else if (run.lastError == true) "nieudana" else if (run.lastError == null && run.error) "z błędem lub ponowieniem" else if (run.error) "zakończona po ponowieniu" else "zakończona"}")
                                }
                                if (!expanded) run.events.lastOrNull()?.let { Text("${formatJournalTime(it.at)} · ${it.message}", style = MaterialTheme.typography.bodySmall) }
                                if (expanded) {
                                    run.events.forEach { event -> Text("${formatJournalTime(event.at)} — ${event.message}", style = MaterialTheme.typography.bodySmall) }
                                    if (run.complete && run.error && run.lastError != false) {
                                        when (recoveryDestination(run.events.lastOrNull()?.message.orEmpty())) {
                                            RecoveryDestination.SHEET -> TextButton(onClick = { tab = 2 }) { Text("Sprawdź arkusz i dostęp Google") }
                                            RecoveryDestination.DATA -> TextButton(onClick = { tab = 1 }) { Text("Sprawdź wybór danych i zgody") }
                                            RecoveryDestination.SYNC -> TextButton(onClick = { tab = 0 }) { Text("Przejdź do synchronizacji") }
                                        }
                                    }
                                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(run.events.joinToString("\n") { "${formatJournalTime(it.at)} — ${it.message}" })) }) { Text("Kopiuj szczegóły") }
                                }
                            }
                        }
                    }
                    if (background.log.isNotEmpty()) {
                        var oldExpanded by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { oldExpanded = !oldExpanded }) { Text(if (oldExpanded) "Ukryj wcześniejszą historię" else "Pokaż wcześniejszą historię") }
                        if (oldExpanded) background.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = { scope.launch { result = diagnose(client) } }) { Text("Sprawdź Health Connect") }
                    if (!result.startsWith("Naciśnij")) Text(result, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }
    }
}

private fun isGoogleError(message: String): Boolean = listOf("Nie ", "Nieznany", "Błąd", "Dostęp", "Arkusz lub", "Autoryzacja", "Okno Google", "Synchronizacja zatrzymana").any(message::startsWith)

internal fun relativeTime(time: Instant, now: Instant): String {
    val minutes = java.time.Duration.between(time, now).toMinutes()
    if (minutes < 0) return time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    if (minutes == 0L) return "przed chwilą"
    if (minutes < 60) return "$minutes ${plural(minutes, "minutę", "minuty", "minut")} temu"
    val hours = minutes / 60
    if (hours < 24) return "$hours ${plural(hours, "godzinę", "godziny", "godzin")} temu"
    val days = hours / 24
    return "$days ${if (days == 1L) "dzień" else "dni"} temu"
}

private fun plural(number: Long, one: String, few: String, many: String): String = when {
    number == 1L -> one
    number % 10 in 2..4 && number % 100 !in 12..14 -> few
    else -> many
}

private fun formatJournalTime(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
} catch (_: Exception) { value }

@Composable
private fun SelectionSwitch(count: Int, total: Int, onClick: () -> Unit) {
    val selected = count == total
    val partial = count in 1 until total
    Switch(modifier = Modifier.semantics { if (partial) stateDescription = "Częściowo wybrane" }, checked = selected || partial, onCheckedChange = { onClick() },
        thumbContent = if (selected || partial) {{ Text(if (partial) "−" else "✓", style = MaterialTheme.typography.labelSmall) }} else null)
}

private fun available(client: HealthConnectClient, feature: Int) =
    client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

private suspend fun diagnose(client: HealthConnectClient): String = try {
    val granted = client.permissionController.getGrantedPermissions()
    val range = TimeRangeFilter.between(Instant.now().minus(30, ChronoUnit.DAYS), Instant.now())
    val lines = mutableListOf(
        "Starsza historia: ${if (available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) "obsługiwana" else "nieobsługiwana"}; zgoda: ${if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) "tak" else "nie"}",
        "Odczyt w tle: ${if (available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)) "obsługiwany" else "nieobsługiwany"}; zgoda: ${if (HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted) "tak" else "nie"}"
    )
    if (stepsPermission in granted) {
        val records = client.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageSize = 100)).records
        lines += "Kroki: ${if (records.isEmpty()) "brak rekordów w próbce" else "znaleziono rekordy; źródła: ${records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString()}"}"
    } else lines += "Kroki: brak zgody na odczyt"
    if (caloriesPermission in granted) {
        val records = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range, pageSize = 100)).records
        lines += "Kalorie łącznie: ${if (records.isEmpty()) "brak rekordów w próbce" else "znaleziono rekordy; źródła: ${records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString()}"}"
    } else lines += "Kalorie łącznie: brak zgody na odczyt"
    lines.joinToString("\n\n")
} catch (e: Exception) { "Nie udało się sprawdzić Health Connect: ${e.javaClass.simpleName}. Sprawdź uprawnienia i spróbuj ponownie." }

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Aplikacja zapisuje wybór metryk i dzienne wartości na urządzeniu po przyznaniu zgód Health Connect. Po wybraniu wysyłki zapisuje wybrane wartości w Twoim arkuszu Google Sheets, korzystając z udzielonej zgody. Dane nie są przekazywane innym serwerom aplikacji.", modifier = Modifier.padding(24.dp)) } }
    }
}
