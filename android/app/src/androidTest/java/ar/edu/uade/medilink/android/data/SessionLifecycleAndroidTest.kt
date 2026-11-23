package ar.edu.uade.medilink.android.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.MediLinkAppVm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba optativa con la API local y la cuenta ficticia documentada del seed. */
@RunWith(AndroidJUnit4::class)
class SessionLifecycleAndroidTest {
    @Test fun backgroundAndActivityRecreationKeepTheAuthenticatedPatient() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSession") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SessionStore(EncryptedSessionPersistence(context))
        val repository = MediLinkRepository(store)
        repository.login("juan.perez@mail.test", "Password123!")
        val token = store.token
        assertNotNull(token)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[MediLinkAppVm::class.java]
                assertEquals(token, vm.repository.session.token)
                assertEquals("USUARIO", vm.repository.session.login.value?.perfil?.rol)
            }
        }
        // Se deja esta sesión de demostración para comprobar además am kill y relanzamiento.
        Unit
    }
}
