package ar.edu.uade.medilink.android.cloudtest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.BuildConfig
import java.io.File
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only from the private staging copy, against the genuine Render backend. */
@RunWith(AndroidJUnit4::class)
class CloudSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var step = "launch"
    private var privateInputEntered = false
    private fun declineLocationPrompt() {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("permissioncontroller")) return
        fun containsLocation(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            if (node.text?.toString()?.lowercase()?.let { "location" in it || "ubicación" in it } == true) return true
            return (0 until node.childCount).any { node.getChild(it)?.let(::containsLocation) == true }
        }
        if (!containsLocation(root)) return
        fun deny(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            val text = node.text?.toString()?.replace('’', '\'')?.trim()?.lowercase()
            if (text == "don't allow" || text == "no permitir") return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            return (0 until node.childCount).any { node.getChild(it)?.let(::deny) == true }
        }
        deny(root)
    }
    private fun waitText(text: String, substring: Boolean = false) {
        ui.waitUntil(240_000) { declineLocationPrompt(); runCatching { ui.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun realGuestAndAuthenticatedNavigation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.getExternalFilesDir(null), "cloud-test.private.json")
        try {
            check(BuildConfig.BASE_URL == "https://medifind-api-ggian102-mey9.onrender.com/")
            step = "guest access"; waitText("Entrar como invitado")
            ui.onNodeWithText("Entrar como invitado").performClick()
            waitText("Buscar"); ui.onNodeWithText("Buscar").performClick()
            step = "live catalog"; waitText("Amoxicilina", true)
            ui.onAllNodesWithText("Amoxicilina", substring = true, ignoreCase = true).onFirst().performClick()
            step = "presentations"
            ui.waitUntil(60_000) { runCatching { ui.onAllNodesWithContentDescription("Consultar disponibilidad").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            ui.onAllNodesWithContentDescription("Consultar disponibilidad").onFirst().performClick()
            step = "live availability"; waitText("Farmacias con stock", true)
            ui.waitUntil(240_000) { runCatching { ui.onAllNodesWithText("Cargando...", substring = true).fetchSemanticsNodes().isEmpty() }.getOrDefault(false) }
            step = "login form"; ui.onNodeWithText("Ingresar").performClick(); waitText("Iniciar sesión")
            val credentials = JSONObject(input.readText())
            privateInputEntered = true
            val fields = ui.onAllNodes(hasSetTextAction())
            fields[0].performTextInput(credentials.getString("email"))
            fields[1].performTextInput(credentials.getString("password"))
            ui.onNodeWithText("Ingresar", ignoreCase = false).performClick()
            step = "real authenticated account"; waitText("Cuenta")
            ui.onNodeWithText("Cuenta").performClick(); waitText("Mi cuenta")
            ui.onAllNodesWithText(credentials.getString("email")).onFirst().assertExists()
            step = "persisted session"; ui.activityRule.scenario.recreate(); waitText("Cuenta")
            ui.onNodeWithText("Cuenta").performClick(); waitText("Mi cuenta")
            step = "reservations navigation"; ui.onAllNodesWithText("Reservas").onLast().performClick()
            waitText("Reservas", true)
            ui.onNodeWithText("Cuenta").performClick(); waitText("Mi cuenta")
            step = "logout"; ui.onNodeWithText("Cerrar sesión", substring = true).performScrollTo().performClick()
            waitText("Iniciar sesión")
        } catch (failure: Throwable) {
            try { File(context.getExternalFilesDir(null), "cloud-failure.png").outputStream().use { stream ->
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            } } catch (_: Throwable) { }
            // Do not print semantics trees/arguments which might contain login inputs.
            throw AssertionError("Cloud smoke failed at step: " + step + if (!privateInputEntered) ": " + failure.message.orEmpty().take(1800) else "")
        } finally { input.delete() }
    }
}
