package ar.edu.uade.medilink.android.cloudtest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import java.io.File
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
}
