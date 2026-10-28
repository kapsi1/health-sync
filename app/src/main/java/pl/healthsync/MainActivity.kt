package pl.healthsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.Instant
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
            Text("Wersja rozwojowa: wybór metryk i diagnostyka. Synchronizacja z arkuszem nie jest jeszcze dostępna.")
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
                                Text(if (metric.permission in granted) "Zgoda przyznana · odczyt w przygotowaniu" else "Wymaga zgody na odczyt", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val missing = MetricRegistry.all.filter { it.id in selected && it.permission !in granted }.map { it.permission }.toSet()
                Button(onClick = { launcher.launch(missing) }, enabled = missing.isNotEmpty()) { Text("Przyznaj brakujące zgody") }
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
        setContent { MaterialTheme { Text("Wersja rozwojowa zapisuje wybór metryk na urządzeniu. Po przyznaniu zgody diagnostyka odczytuje próbkę kroków i całkowitych kalorii z Health Connect i pokazuje jedynie obecność rekordów oraz nazwy pakietów źródeł. Nie zapisuje ani nie wysyła danych zdrowotnych. Pozostałe metryki i synchronizacja z arkuszem są w przygotowaniu.", modifier = Modifier.padding(24.dp)) } }
    }
}
