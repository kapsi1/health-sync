package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryDestinationTest {
    @Test fun routesKnownProblemsToRelevantScreen() {
        assertEquals(RecoveryDestination.SHEET, recoveryDestination("Arkusz jest niedostępny (HTTP 404)."))
        assertEquals(RecoveryDestination.SHEET, recoveryDestination("Autoryzacja Google nie powiodła się."))
        assertEquals(RecoveryDestination.DATA, recoveryDestination("Brakuje zgód Health Connect."))
        assertEquals(RecoveryDestination.SYNC, recoveryDestination("Brak połączenia."))
    }
}
