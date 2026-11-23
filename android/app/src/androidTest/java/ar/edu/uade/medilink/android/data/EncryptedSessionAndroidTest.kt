package ar.edu.uade.medilink.android.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedSessionAndroidTest {
    @Test fun restoresEncryptedSessionUsingKeystoreAndErasesItOnLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "session-test"
        val disk = EncryptedSessionPersistence(context, name)
        val login = LoginDto("private-jwt-example", "Bearer", 1800,
            PerfilDto(1, 1, "USUARIO", "demo@test.local", "ACTIVA", "Ana", "Paz", null))
        try {
            SessionStore(disk).set(login)
            val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("encrypted", null)
            assertNotNull(raw)
            assertFalse(raw!!.contains(login.accessToken))
            val restored = SessionStore(EncryptedSessionPersistence(context, name))
            assertEquals(login, restored.login.value)
            restored.clear()
            assertNull(SessionStore(EncryptedSessionPersistence(context, name)).token)
        } finally { disk.write(null) }
    }
    @Test fun corruptStorageIsDiscardedWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("session-test", Context.MODE_PRIVATE).edit()
            .putString("encrypted", "invalid").commit()
        assertNull(SessionStore(EncryptedSessionPersistence(context, "session-test")).token)
    }
}
