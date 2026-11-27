package ar.edu.uade.medilink.android.cloudtest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.MediLinkAppVm
import java.io.File
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real cloud GETs only, apart from login. Does not submit a prescription or reservation. */
@RunWith(AndroidJUnit4::class)
class CloudAvailabilityFirstTest {
    private var step="login"
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String) {
        ui.waitUntil(240_000) {
            val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            if(root?.packageName.toString().contains("permissioncontroller")) {
                fun deny(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
                    val t=n.text?.toString()?.replace('’','\'')?.lowercase()
                    if(t=="don't allow"||t=="no permitir")return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    return (0 until n.childCount).any{n.getChild(it)?.let(::deny)==true}
                };root?.let(::deny)
            }
            runCatching{ui.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)
        }
    }
    private fun click(text:String) {
        waitText(text);val node=ui.onAllNodesWithText(text,substring=true).onFirst()
        runCatching{node.performScrollTo()};node.performClick()
    }
    private fun login() {
        val input=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"cloud-test.private.json")
        val c=JSONObject(input.readText()); input.delete()
        waitText("Iniciar sesión");val fields=ui.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(c.getString("email"));fields[1].performTextInput(c.getString("password"))
        ui.onNodeWithText("Ingresar").performClick();waitText("Cuenta")
    }
    private fun chooseMedicine(name:String,concentration:String) {
        click("Buscar");waitText("Ej.: ibuprofeno")
        ui.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(name)
        waitText("Ver presentaciones y stock");ui.onNode(hasText(name) and !hasSetTextAction()).performClick()
        click(concentration);waitText("Farmacias con stock");waitText("Farmacia San Martín")
    }
    @Test fun prescriptionAvailabilityAndMapComeBeforeValidationButReservationRemainsLocked() {
        try {
            login();step="catalog and availability";chooseMedicine("Amoxicilina","250 mg/5 ml")
            ui.onAllNodesWithText("Verificá tu receta").assertCountEquals(0)
            step="map selection";click("Ver en mapa");waitText("Farmacia San Martín");click("Reservar acá")
            waitText("Adjuntar receta");ui.onAllNodes(hasText("Confirmar reserva") and hasClickAction()).assertCountEquals(0)
            step="prescription attachment";click("Adjuntar receta");waitText("Paso 1 de 2")
            ui.onNodeWithText("Verificar y continuar").assertIsNotEnabled()
        } catch(failure:Throwable) {throw AssertionError("Cloud availability-first failed at $step: ${failure.javaClass.simpleName}"+if(step=="login") "" else ": "+failure.message.orEmpty().take(1500))}
    }
    @Test fun overTheCounterReservationActionIsUnchangedAndNotSubmitted() {
        try {
            login();chooseMedicine("Ibuprofeno","400 mg");click("Elegir")
            waitText("Confirmar reserva");ui.onNode(hasText("Confirmar reserva") and hasClickAction()).assertExists()
            ui.onAllNodesWithText("Adjuntar receta").assertCountEquals(0)
        } catch(_:Throwable) {throw AssertionError("Cloud OTC navigation failed; private inputs omitted")}
    }
    @Test fun selectedPharmacySurvivesSyntheticValidationWithoutSendingAnyBusinessWrite() {
        val originals=IdentityHashMap<Retrofit,Call.Factory>();val simulated=AtomicInteger()
        try {
            waitText("Iniciar sesión")
            // Simulate the validation response in this test process. Every other business POST is blocked.
            ui.runOnUiThread {
                val repository=ViewModelProvider(ui.activity)[MediLinkAppVm::class.java].repository
                for(name in listOf("api","parity","recetas","escaneo")) {
                    val service=repository.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(repository)
                    val handler=Proxy.getInvocationHandler(service)
                    val retrofit=handler.javaClass.declaredFields.first{it.type==Retrofit::class.java}.apply{isAccessible=true}.get(handler) as Retrofit
                    if(originals.containsKey(retrofit))continue
                    val original=retrofit.callFactory();originals[retrofit]=original
                    val client=(original as OkHttpClient).newBuilder().addInterceptor { chain ->
                        val request=chain.request();val path=request.url.encodedPath
                        if(path=="/api/me/recetas-demo/vinculacion"&&request.method=="POST") {
                            val buffer=okio.Buffer();request.body!!.writeTo(buffer);val selection=JSONObject(buffer.readUtf8())
                            check(selection.getLong("presentacionId")==7L&&selection.getInt("cantidad")==1)
                            check(selection.getString("codigoReceta")=="MLD-SYNTHETIC-TEST")
                            simulated.incrementAndGet()
                            val json="""{"nueva":false,"codigo":"RECETA_VERIFICADA","prescripcion":{"id":999999,"codigoExterno":"MLD-SYNTHETIC-TEST","usuarioId":999999,"fechaEmision":"2026-11-23","fechaVencimiento":"2027-02-21","estado":"ACTIVA","items":[{"id":999999,"presentacionId":7,"descripcionPresentacion":"Amoxicilina sintética","cantidadAutorizada":1,"cantidadComprometida":0,"cantidadUtilizada":0,"cantidadDisponible":1}]}}"""
                            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic validation")
                                .body(json.toResponseBody("application/json".toMediaType())).build()
                        } else {
                            check(request.method=="GET"||path in setOf("/api/auth/login","/api/auth/logout")){"Business writes are forbidden in this fixture"}
                            chain.proceed(request)
                        }
                    }.build()
                    Retrofit::class.java.getDeclaredField("callFactory").apply{isAccessible=true}.set(retrofit,client)
                }
            }
            login();step="selection before synthetic validation";chooseMedicine("Amoxicilina","250 mg/5 ml")
            click("Ver en mapa");waitText("Farmacia San Martín");click("Reservar acá");click("Adjuntar receta")
            step="synthetic validation";waitText("Paso 1 de 2");click("Tengo un código de receta electrónica")
            ui.onAllNodes(hasSetTextAction()).onLast().performTextInput("MLD-SYNTHETIC-TEST")
            click("Verificar y continuar");waitText("Receta verificada")
            ui.onNodeWithText("Elegida").assertExists()
            ui.onNode(hasText("Confirmar reserva") and hasClickAction()).assertIsEnabled()
            check(simulated.get()==1){"The synthetic validation must be intercepted exactly once"}
            // Deliberately DO NOT press Confirmar reserva.
        } catch(failure:Throwable) {throw AssertionError("Cloud synthetic validation failed at $step (${failure.javaClass.simpleName}); private inputs omitted")}
        finally {ui.runOnUiThread {for((retrofit,factory) in originals)Retrofit::class.java.getDeclaredField("callFactory").apply{isAccessible=true}.set(retrofit,factory)}}
    }
}
