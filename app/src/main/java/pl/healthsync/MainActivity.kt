package pl.healthsync

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Job
import pl.healthsync.data.HealthHistorySource
import pl.healthsync.data.RoomHistoryStore
import pl.healthsync.data.SyncDatabase
import pl.healthsync.domain.HistoryImport
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
        setContent { MaterialTheme { DiagnosticScreen() } }
    }

    @Composable
    private fun DiagnosticScreen() {
        val scope = rememberCoroutineScope()
        val status = remember { HealthConnectClient.getSdkStatus(this) }
        val client = remember(status) { if (status == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(this) else null }
        var result by remember { mutableStateOf("Naciśnij „Sprawdź”, aby odczytać status i dostępność danych.") }
        var preview by remember { mutableStateOf("") }
        var reading by remember { mutableStateOf(false) }
        var historyStart by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Warsaw")).toString()) }
        var historyStatus by remember { mutableStateOf("Import działa na pierwszym planie; zapisane miesiące można wznowić.") }
        var historyJob by remember { mutableStateOf<Job?>(null) }
        val importer = remember(client) { client?.let { HistoryImport(HealthHistorySource(it), RoomHistoryStore(SyncDatabase.get(applicationContext).importDao())) } }
        val selections = remember { MetricSelection(applicationContext) }
        val selected by selections.selected.collectAsState(initial = emptySet())
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
            if (client != null) scope.launch { granted = client.permissionController.getGrantedPermissions() }
            result = "Zgody zaktualizowane. Naciśnij „Sprawdź”."
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Health Connect — diagnostyka", style = MaterialTheme.typography.headlineSmall)
            Text("Wersja rozwojowa: wybór metryk, lokalny import i ręczne wysyłanie do arkusza.")
            Text(when (status) {
                HealthConnectClient.SDK_AVAILABLE -> "Health Connect: dostępny"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect: wymaga instalacji lub aktualizacji"
                else -> "Health Connect: niedostępny na tym urządzeniu"
            })
            if (client != null) {
                Text("Statystyki", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { selections.set(MetricRegistry.all.map { it.id }.toSet()) } }) { Text("Zaznacz wszystkie") }
                    OutlinedButton(onClick = { scope.launch { selections.set(emptySet()) } }) { Text("Odznacz wszystkie") }
                }
                MetricRegistry.all.groupBy { it.category }.forEach { (category, metrics) ->
                    Text(category, style = MaterialTheme.typography.titleMedium)
                    metrics.forEach { metric ->
                        Row {
                            Checkbox(checked = metric.id in selected, onCheckedChange = { checked ->
                                scope.launch { selections.toggle(metric.id, checked) }
                            })
                            Column(Modifier.padding(top = 8.dp)) {
                                Text("${metric.label} (${metric.unit})")
                                Text(if (metric.permission in granted) "Zgoda przyznana" else "Wymaga zgody na odczyt", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val missing = MetricRegistry.all.filter { it.id in selected && it.permission !in granted }.map { it.permission }.toSet()
                Button(onClick = { launcher.launch(missing) }, enabled = missing.isNotEmpty()) { Text("Przyznaj brakujące zgody") }
                Button(onClick = {
                    scope.launch {
                        reading = true
                        try {
                            val current = client.permissionController.getGrantedPermissions()
                            granted = current
                            preview = DailyPreview(client).read(selected, current)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            preview = "Nie udało się odczytać danych (${e.javaClass.simpleName})."
                        } finally { reading = false }
                    }
                }, enabled = !reading && selected.isNotEmpty()) { Text(if (reading) "Odczyt..." else "Podgląd ostatnich 7 dni") }
                if (preview.isNotEmpty()) Text(preview)
                Text("Import historii", style = MaterialTheme.typography.titleLarge)
                Text("Import rozpoczyna się od wybranej daty. Wysłanie do arkusza obejmuje również starsze dane, które już są w kolejce. Import wymaga otwartej aplikacji.")
                val chosenDate = LocalDate.parse(historyStart)
                val displayedDate = chosenDate.format(DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("pl-PL")))
                OutlinedButton(onClick = {
                    val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                    val dialog = DatePickerDialog(this@MainActivity, { _, year, month, day ->
                        historyStart = LocalDate.of(year, month + 1, day).toString()
                    }, chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth)
                    val deviceZone = ZoneId.systemDefault()
                    dialog.datePicker.minDate = LocalDate.of(1900, 1, 1).atStartOfDay(deviceZone).toInstant().toEpochMilli()
                    dialog.datePicker.maxDate = today.atStartOfDay(deviceZone).toInstant().toEpochMilli()
                    dialog.show()
                }, enabled = historyJob == null) { Text("Data od: $displayedDate — wybierz") }
                fun startImport(refresh: Boolean) {
                    val start = try { LocalDate.parse(historyStart) } catch (_: Exception) { historyStatus = "Wpisz poprawną datę w formacie RRRR-MM-DD."; return }
                    val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                    if (start.year < 1900 || start > today) { historyStatus = "Data musi mieścić się między 1900-01-01 a dzisiaj."; return }
                    val metrics = MetricRegistry.all.filter { it.id in selected }
                    if (metrics.isEmpty()) { historyStatus = "Wybierz przynajmniej jedną statystykę."; return }
                    historyJob = scope.launch {
                        try {
                            val current = client.permissionController.getGrantedPermissions()
                            granted = current
                            check(metrics.all { it.permission in current }) { "Przyznaj zgody dla wszystkich wybranych statystyk." }
                            val earliestRead = if (metrics.any { it.id == "sleep_duration" }) start.minusDays(7) else start
                            if (earliestRead < today.minusDays(28)) {
                                check(available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) && HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in current) { "Przyznaj zgodę na starszą historię albo wybierz późniejszą datę." }
                            }
                            importer!!.run(metrics.map { it.id }, start, today.plusDays(1), refresh) { progress ->
                                val name = metrics.first { it.id == progress.metricId }.label
                                historyStatus = "${progress.processed}/${progress.total} porcji · $name · ${progress.range.start} – ${progress.range.endExclusive.minusDays(1)}${if (progress.skipped) " · wcześniej zapisano" else " · zapisano"}"
                            }
                            val summaries = SyncDatabase.get(applicationContext).importDao().summaries().associateBy { it.metricId }
                            val counts = metrics.joinToString("\n") { metric ->
                                val summary = summaries[metric.id]
                                if (summary == null) "${metric.label}: brak zapisanych dni" else "${metric.label}: ${summary.dayCount} dni, najstarszy ${summary.earliestDate}"
                            }
                            historyStatus = "Import zakończony lokalnie. Aby wysłać dane, użyj przycisku w sekcji Google Sheets. Podsumowanie obejmuje wszystkie wcześniej zapisane dane:\n$counts"
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            historyStatus = "Import przerwany. Zapisane porcje można wznowić."
                            throw e
                        } catch (e: Exception) {
                            historyStatus = "Import zatrzymany: ${e.message ?: e.javaClass.simpleName}. Zapisane porcje można wznowić."
                        } finally { historyJob = null }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startImport(false) }, enabled = historyJob == null) { Text("Importuj / wznów") }
                    OutlinedButton(onClick = { historyJob?.cancel() }, enabled = historyJob != null) { Text("Przerwij") }
                }
                OutlinedButton(onClick = { startImport(true) }, enabled = historyJob == null) { Text("Importuj ponownie") }
                Text(historyStatus)
                GoogleAuthorization(this@MainActivity, selected)
                Text("Diagnostyka", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { launcher.launch(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)) }, enabled = available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) { Text("Zezwól na starszą historię") }
                Button(onClick = { launcher.launch(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)) }, enabled = available(client, HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)) { Text("Zezwól na odczyt w tle") }
                Button(onClick = { scope.launch { result = diagnose(client) } }) { Text("Sprawdź") }
            }
            Text(result)
        }
    }
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
