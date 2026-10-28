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
        val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            result = "Zgody zaktualizowane. Naciśnij „Sprawdź”."
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Health Connect — diagnostyka", style = MaterialTheme.typography.headlineSmall)
            Text("Etap M0: odczyt próbki z ostatnich 30 dni. Dane nie są wysyłane ani zapisywane przez tę wersję.")
            Text(when (status) {
                HealthConnectClient.SDK_AVAILABLE -> "Health Connect: dostępny"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect: wymaga instalacji lub aktualizacji"
                else -> "Health Connect: niedostępny na tym urządzeniu"
            })
            if (client != null) {
                Button(onClick = { launcher.launch(setOf(stepsPermission, caloriesPermission)) }) { Text("Zezwól na kroki i kalorie") }
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
        setContent { MaterialTheme { Text("Wersja diagnostyczna odczytuje wyłącznie kroki i całkowite kalorie z Health Connect po przyznaniu zgody. Wyświetla tylko informację o obecności rekordów i nazwach pakietów źródeł; nie zapisuje ani nie wysyła danych.", modifier = Modifier.padding(24.dp)) } }
    }
}
