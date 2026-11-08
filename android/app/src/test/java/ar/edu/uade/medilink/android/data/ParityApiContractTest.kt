package ar.edu.uade.medilink.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Contrato HTTP básico independiente del backend real; no carga bases ni credenciales. */
class ParityApiContractTest {
    private val server = MockWebServer()
    private lateinit var api: ParityApi

    @Before fun setup() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(ParityApi::class.java)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun filtroEstadoEnReservasUsuario() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(paginaVacia))
        api.reservasUsuario("ACTIVA", 2)
        assertEquals("/api/reservas/mias?estado=ACTIVA&page=2&size=20", server.takeRequest().path)
    }

    @Test fun filtrosDeAdminYUrlRelativa() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(paginaVacia))
        api.adminList("api/admin/medicamentos", mapOf("page" to "0", "size" to "20", "nombre" to "ibu"))
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/admin/medicamentos?"))
        assertTrue(request.path!!.contains("nombre=ibu"))
    }

    @Test fun cambiarClaveNoFiltraLaRespuesta() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.cambiarClave(CambiarClaveRequest("antigua", "nueva"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/change-password", request.path)
        assertTrue(request.body.readUtf8().contains("\"contrasenaActual\":\"antigua\""))
    }

    @Test fun cancelarFarmaciaRespetaBodyDelBackend() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200)
            .setBody("""{"id":1,"codigoVisible":"RSV-TEST","estado":"CANCELADA"}"""))
        api.cancelarOperativa(4, "RSV-TEST",
            CancelacionOperativaRequest("STOCK_INCONSISTENTE", cantidadStockReal = 3))
        val request = server.takeRequest()
        assertEquals("/api/farmacias/4/reservas/RSV-TEST/cancelacion-operativa", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"causa\":\"STOCK_INCONSISTENTE\""))
        assertTrue(body.contains("\"cantidadStockReal\":3"))
    }

    companion object {
        const val paginaVacia = """{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"first":true,"last":true}"""
    }
}
