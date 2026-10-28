package pl.healthsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.healthsync.data.SheetHttpError
import pl.healthsync.data.SheetSettings
import pl.healthsync.data.SheetState
import pl.healthsync.data.SheetsCreator

private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

@Composable
fun GoogleAuthorization(activity: Activity) {
    var status by remember { mutableStateOf("Nie sprawdzono. Wymaga konfiguracji Google Cloud.") }
    var busy by remember { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf("check") }
    val settings = remember { SheetSettings(activity.applicationContext) }
    val sheet by settings.state.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val client = remember { Identity.getAuthorizationClient(activity) }

    fun authorized(token: String?) {
        if (token.isNullOrBlank()) { busy = false; status = "Nie otrzymano tokenu."; return }
        if (action != "create") { busy = false; status = "Zgoda przyznana. Test nie tworzy arkusza."; return }
        scope.launch {
            var marked = false
            try {
                settings.markPending()
                marked = true
                val created = SheetsCreator().create(token)
                settings.save(created.id, created.url)
                status = "Arkusz utworzony. Synchronizacja danych jest w przygotowaniu."
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
        } else try {
            authorized(client.getAuthorizationResultFromIntent(result.data).accessToken)
        } catch (e: ApiException) {
            busy = false
            status = "Autoryzacja nie powiodła się. Kod Google: ${e.statusCode}; wynik okna Android: ${result.resultCode}."
        } catch (e: Exception) {
            busy = false
            status = "Nie uzyskano zgody (${e.javaClass.simpleName})."
        }
    }

    fun authorize(requestedAction: String) {
        action = requestedAction
        busy = true
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
            status = if (error is ApiException) "Błąd rozpoczęcia autoryzacji. Kod Google: ${error.statusCode}." else "Błąd rozpoczęcia autoryzacji (${error.javaClass.simpleName})."
        }
    }

    Column {
        Text("Google Sheets", style = MaterialTheme.typography.titleLarge)
        Text("Aplikacja prosi o dostęp do plików, z którymi pracuje. Utworzenie arkusza nie wysyła jeszcze danych zdrowotnych.")
        OutlinedButton(enabled = !busy, onClick = { authorize("check") }) { Text("Sprawdź zgodę Google") }
        when (val state: SheetState? = sheet) {
            null -> Text("Sprawdzanie ustawień arkusza…")
            else -> when {
                state.id != null && state.url != null -> {
                    Text("Arkusz: Health Connect – Daily Sync")
                    OutlinedButton(onClick = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }) { Text("Otwórz arkusz") }
                }
                state.creationPending -> Text("Poprzednia próba mogła utworzyć arkusz. Sprawdź Dysk Google — kolejna próba jest wstrzymana, aby uniknąć duplikatu.")
                else -> Button(enabled = !busy, onClick = { authorize("create") }) { Text("Utwórz arkusz") }
            }
        }
        Text(status)
    }
}
