package ar.edu.uade.medilink.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class RecuperacionApiTest {
    private val server = MockWebServer()
    private val session = SessionStore()
    private lateinit var repository: MediLinkRepository
    @Before fun setup() {
        server.start()
        session.set(LoginDto("test-token", "Bearer", 1800,
            PerfilDto(1, 1, "USUARIO", "demo@mail.test", "ACTIVA", "Ana", "Paz", null)))
        repository = MediLinkRepository(session, server.url("/").toString())
    }
    @After fun close() { server.shutdown() }

    @Test fun requestAcceptsNeutral202ResponseWithoutDroppingSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"mensaje":"Si la cuenta existe, recibirás un código."}"""))
        repository.recuperarClave(" demo@mail.test ")
        val request = server.takeRequest()
        assertEquals("/api/auth/password-recovery", request.path)
        assertEquals("""{"correo":"demo@mail.test"}""", request.body.readUtf8())
        assertEquals("test-token", session.token)
    }
    @Test fun invalidCodeDoesNotDropAnExistingSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"codigo":"RECOVERY_INVALID"}"""))
        val error = runCatching { repository.restablecerClave("demo@mail.test", "12345678", "NewPassword123!") }.exceptionOrNull()
        assertTrue(error is HttpException)
        assertEquals("test-token", session.token)
    }
    @Test fun successfulResetRequiresSigningInAgain() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        repository.restablecerClave("demo@mail.test", "12345678", "NewPassword123!")
        assertEquals("/api/auth/password-reset", server.takeRequest().path)
        assertNull(session.token)
    }
}
