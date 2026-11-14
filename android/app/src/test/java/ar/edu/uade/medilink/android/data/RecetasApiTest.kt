package ar.edu.uade.medilink.android.data

import ar.edu.uade.medilink.android.ui.screens.mensajeResultadoReceta
import ar.edu.uade.medilink.android.ui.viewmodel.puedeVerMisRecetas
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class RecetasApiTest {
    @get:Rule val temporales = TemporaryFolder()
    private val servidor = MockWebServer()
    private val sesion = SessionStore()
    private lateinit var repo: MediLinkRepository

    @Before fun iniciar() {
        servidor.start()
        sesion.set(LoginDto("jwt-de-prueba", "Bearer", 3600,
            PerfilDto(1, 1, "USUARIO", "paciente@demo.test", "ACTIVA", "Ana", "Demo", null)))
        repo = MediLinkRepository(sesion, servidor.url("/").toString(), esperaEscaneoMs = 250)
    }

    @After fun cerrar() { servidor.shutdown() }

    @Test fun listaPaginadaVaciaConDatosYDetalle() = runBlocking {
        responder(200, ParityApiContractTest.paginaVacia)
        responder(200, """{"content":[$receta],"page":1,"size":20,"totalElements":21,"totalPages":2,"first":false,"last":true}""")
        responder(200, receta)
        assertTrue(repo.misPrescripciones(0).content.isEmpty())
        assertEquals("/api/me/prescripciones?page=0&size=20", solicitud().path)
        val pagina = repo.misPrescripciones(1)
        assertEquals(1, pagina.page)
        assertEquals("/api/me/prescripciones?page=1&size=20", solicitud().path)
        val detalle = repo.detallePrescripcion(42)
        assertEquals("/api/me/prescripciones/42", solicitud().path)
        assertEquals(pagina.content.single(), detalle)
        assertEquals("2026-11-12", detalle.fechaVencimiento)
        assertEquals(ItemPrescripcionDto(9, 5, "Caja x 30", 5, 1, 2, 2), detalle.items.single())
    }

    @Test fun vinculaPorCodigoYReconoceElReintentoIdempotente() = runBlocking {
        responder(200, """{"nueva":true,"prescripcion":$receta}""")
        responder(200, """{"nueva":false,"prescripcion":$receta}""")
        assertEquals("Receta vinculada correctamente.", mensajeResultadoReceta(repo.vincularReceta(" MLD-DEMO ")))
        assertEquals("Esta receta ya estaba vinculada a tu cuenta.", mensajeResultadoReceta(repo.vincularReceta("MLD-DEMO")))
        repeat(2) {
            val request = solicitud()
            assertEquals("POST", request.method)
            assertEquals("/api/me/recetas-demo/vinculacion", request.path)
            assertEquals("""{"codigoReceta":"MLD-DEMO"}""", request.body.readUtf8())
        }
    }

    @Test fun escaneoUsaMultipartConJwtYReportaEnvio() = runBlocking {
        responder(200, """{"nueva":true,"prescripcion":$receta,"codigo":"RECETA_VERIFICADA"}""")
        responder(200, """{"nueva":true,"prescripcion":$receta,"codigo":"RECETA_VERIFICADA_LEGADA"}""")
        var enviados = 0
        val resultado = repo.escanearReceta(adjunto().multipart()) { enviados++ }
        assertEquals(1, enviados)
        assertEquals("Receta verificada y vinculada correctamente.", mensajeResultadoReceta(resultado))
        assertEquals("Receta de emisión anterior verificada y vinculada.",
            mensajeResultadoReceta(repo.escanearReceta(adjunto().multipart()) {}))
        val request = solicitud()
        assertEquals("/api/me/recetas-demo/escaneo", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val cuerpo = request.body.readUtf8()
        assertTrue(cuerpo.contains("""name="archivo"; filename="receta.pdf""""))
        assertTrue(cuerpo.contains("Content-Type: application/pdf"))
        assertTrue(cuerpo.contains("%PDF-ficticio"))
    }

    @Test fun interpretaLosErroresRealesDelEscaneo() = runBlocking {
        val archivo = adjunto().multipart()
        for ((status, codigo, texto) in listOf(
            Triple(400, "VALIDATION_ERROR", "Mensaje de prueba"),
            Triple(413, "ARCHIVO_DEMASIADO_GRANDE", "tamaño"),
            Triple(422, "LECTURA_INCONCLUSA", "otra foto"),
            Triple(409, "RECETA_DEMO_DATOS_NO_COINCIDEN", "no coinciden"),
            Triple(409, "RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO", "código secundario"),
            Triple(409, "RECETA_DEMO_NO_VIGENTE", "vigencia"),
            Triple(429, "ESCANEO_LIMITE", "límite"),
            Triple(502, "ESCANEO_PROVEEDOR_ERROR", "Falló el servicio"),
            Triple(503, "ESCANEO_NO_CONFIGURADO", "no está habilitado"),
            Triple(504, "ESCANEO_TIMEOUT", "tardó demasiado"),
            Triple(403, "FORBIDDEN", "permiso"),
        )) {
            responder(status, """{"codigo":"$codigo","mensaje":"Mensaje de prueba"}""")
            val error = runCatching { repo.escanearReceta(archivo) {} }.exceptionOrNull() as HttpException
            assertEquals(status, error.code())
            assertTrue(mensajeDeReceta(error).contains(texto))
        }
    }

    @Test fun recetaAjenaYListadoNoDisponibleSonErrores404() = runBlocking {
        repeat(2) { responder(404, """{"codigo":"RESOURCE_NOT_FOUND"}""") }
        val ajena = runCatching { repo.vincularReceta("MLD-AJENA") }.exceptionOrNull() as HttpException
        val lista = runCatching { repo.misPrescripciones(0) }.exceptionOrNull() as HttpException
        assertEquals(404, lista.code())
        assertEquals("La receta no está disponible.", mensajeDeReceta(ajena))
        assertEquals("jwt-de-prueba", sesion.token)
    }

    @Test fun vencimientoDeSesionEliminaJwtYRestringeElAcceso() = runBlocking {
        assertTrue(puedeVerMisRecetas(sesion.login.value?.perfil?.rol))
        assertFalse(puedeVerMisRecetas("OPERADOR"))
        assertFalse(puedeVerMisRecetas("ADMINISTRADOR"))
        responder(401, """{"codigo":"INVALID_TOKEN"}""")
        val error = runCatching { repo.misPrescripciones(0) }.exceptionOrNull() as HttpException
        assertTrue(mensajeDeReceta(error).contains("Sesión vencida"))
        assertNull(sesion.token)
        assertFalse(puedeVerMisRecetas(sesion.login.value?.perfil?.rol))
        solicitud()
        Unit
    }

    @Test fun elEscaneoExpiraSinReenviarPorSuCuenta() = runBlocking {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val error = runCatching { repo.escanearReceta(adjunto().multipart()) {} }.exceptionOrNull()
        assertTrue(error is SocketTimeoutException)
        assertTrue(mensajeDeReceta(error as Exception).contains("tardó demasiado"))
        assertEquals(1, servidor.requestCount)
    }

    private fun solicitud() = requireNotNull(servidor.takeRequest(2, TimeUnit.SECONDS)).also {
        assertEquals("Bearer jwt-de-prueba", it.getHeader("Authorization"))
        assertFalse(it.getHeader("X-Correlation-Id").isNullOrBlank())
    }

    private fun adjunto() = AdjuntoReceta(
        temporales.newFile().apply { writeText("%PDF-ficticio") }, "receta.pdf", "application/pdf",
    )

    private fun responder(status: Int, json: String) {
        servidor.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }

    private val receta = """{"id":42,"codigoExterno":"MLD-DEMO","usuarioId":1,"fechaEmision":"2026-11-05","fechaVencimiento":"2026-11-12","estado":"ACTIVA","items":[{"id":9,"presentacionId":5,"descripcionPresentacion":"Caja x 30","cantidadAutorizada":5,"cantidadComprometida":1,"cantidadUtilizada":2,"cantidadDisponible":2}]}"""
}
