package ar.edu.uade.medilink.android.data

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.ui.screens.ReservaConRecetaScreen
import ar.edu.uade.medilink.android.ui.theme.MediLinkTheme
import ar.edu.uade.medilink.android.ui.viewmodel.RecetaLista
import ar.edu.uade.medilink.android.ui.viewmodel.ReservaConRecetaVm
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeleccionRecetaAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun seleccionaRecetaDeSegundaPaginaYContinuaSinVolverASubirArchivo() {
        val resultado = AtomicReference<RecetaLista?>()
        val enviado = AtomicReference<String?>()
        val solicitudes = Collections.synchronizedList(mutableListOf<String>())
        val hoy = LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
        fun receta(id: Int, presentacion: Int) = """{"id":$id,"codigoExterno":"ADJ-guardada-$id","usuarioId":1,"fechaEmision":"$hoy","fechaVencimiento":"${hoy.plusDays(10)}","estado":"ACTIVA","items":[{"id":$id,"presentacionId":$presentacion,"descripcionPresentacion":"Levotiroxina ${if (presentacion == 9) 100 else 50} mcg · Caja x 50","cantidadAutorizada":2,"cantidadComprometida":0,"cantidadUtilizada":0,"cantidadDisponible":2}]}"""
        MockWebServer().use { servidor ->
            servidor.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    solicitudes.add("${request.method} ${request.path}")
                    val cuerpo = when {
                        request.path == "/api/presentaciones/9" -> """{"id":9,"medicamentoId":1,"medicamentoNombre":"Levotiroxina","concentracionTexto":"100 mcg","formaFarmaceutica":"Comprimidos","cantidadEnvase":50,"unidadExpendio":"Caja","condicionDispensa":"BAJO_RECETA","estado":"ACTIVA"}"""
                        request.path == "/api/me/recetas-demo/configuracion" -> """{"escaneoHabilitado":false}"""
                        request.path == "/api/me/prescripciones?page=0&size=20" -> """{"content":[${receta(10,8)}],"page":0,"size":20,"totalElements":2,"totalPages":2,"first":true,"last":false}"""
                        request.path == "/api/me/prescripciones?page=1&size=20" -> """{"content":[${receta(11,9)}],"page":1,"size":20,"totalElements":2,"totalPages":2,"first":false,"last":true}"""
                        request.path == "/api/me/recetas-demo/vinculacion" -> {
                            enviado.set(request.body.readUtf8())
                            """{"nueva":false,"prescripcion":${receta(11,9)}}"""
                        }
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(cuerpo)
                }
            }
            servidor.start()
            val url = servidor.url("/").newBuilder().host("localhost").build().toString()
            lateinit var vm: ReservaConRecetaVm
            compose.activityRule.scenario.onActivity { actividad ->
                val repo = MediLinkRepository(SessionStore(), url)
                vm = ReservaConRecetaVm(repo, ArchivosReceta(actividad), 9)
                actividad.setContent { MediLinkTheme { ReservaConRecetaScreen(vm) { resultado.set(it) } } }
            }
            compose.onNodeWithText("Elegir una receta de Mis recetas").performScrollTo().performClick()
            compose.waitUntil(10_000) { !vm.state.value.cargandoRecetas && vm.state.value.recetas.size == 2 && vm.state.value.presentacion != null }
            compose.waitForIdle()
            val instrumentacion = InstrumentationRegistry.getInstrumentation()
            val captura = instrumentacion.uiAutomation.takeScreenshot()
            java.io.File(instrumentacion.targetContext.cacheDir, "selector-recetas.png").outputStream().use {
                captura.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            captura.recycle()
            compose.onNodeWithText("Disponibles: 2 · ADJ-guardada-11").performScrollTo().performClick()
            compose.onNodeWithText("Verificar y continuar").performScrollTo().performClick()
            compose.waitUntil(10_000) { resultado.get() != null }
            assertEquals(RecetaLista("ADJ-guardada-11", 1), resultado.get())
            val json = org.json.JSONObject(requireNotNull(enviado.get()))
            assertEquals("ADJ-guardada-11", json.getString("codigoReceta"))
            assertEquals(9, json.getInt("presentacionId"))
            assertEquals(1, json.getInt("cantidad"))
            assertTrue(solicitudes.contains("GET /api/me/prescripciones?page=1&size=20"))
            assertEquals(1, solicitudes.count { it.startsWith("POST ") })
            assertFalse(solicitudes.any { it.contains("/escaneo") || it.contains("/recetas-adjuntas") })
        }
    }
}
