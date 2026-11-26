package ar.edu.uade.medilink.android.cloudtest

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Render/Neon data, including when the target is the already published rc1 APK. */
@RunWith(AndroidJUnit4::class)
class CloudMapTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) {
        ui.waitUntil(240_000) {
            val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            if(root?.packageName.toString().contains("permissioncontroller")) {
                fun deny(n: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                    val label=n.text?.toString()?.replace('’','\'')?.lowercase()
                    if(label=="don't allow"||label=="no permitir")return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    return (0 until n.childCount).any{n.getChild(it)?.let(::deny)==true}
                };root?.let(::deny)
            }
            runCatching { ui.onAllNodesWithText(text, substring=true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
    private fun findMap(view: View): MapView? {
        if(view is MapView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun publishedAppDrawsActualNeonPharmacyMarker() {
        waitText("Entrar como invitado");ui.onNodeWithText("Entrar como invitado").performClick()
        waitText("Buscar");ui.onNodeWithText("Buscar").performClick()
        waitText("Amoxicilina");ui.onAllNodesWithText("Amoxicilina",substring=true).onFirst().performClick()
        ui.waitUntil(60_000){runCatching{ui.onAllNodesWithContentDescription("Consultar disponibilidad").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
        ui.onAllNodesWithContentDescription("Consultar disponibilidad").onFirst().performClick()
        waitText("Ver en mapa");ui.onAllNodesWithText("Ver en mapa",substring=true).onFirst().performScrollTo().performClick()
        waitText("Farmacia San Martín")
        var verified=false
        ui.waitUntil(60_000){ui.runOnUiThread {
            val map=findMap(ui.activity.window.decorView)
            verified=map?.overlays?.filterIsInstance<Marker>()?.any {
                kotlin.math.abs(it.position.latitude-(-34.6188733))<0.0000001 &&
                kotlin.math.abs(it.position.longitude-(-58.4377466))<0.0000001
            }==true
        };verified}
        assertTrue(verified)
        // The existing external route action is still visible; it is not invoked on real location.
        ui.onNodeWithText("Cómo llegar",substring=true).assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"cloud-map-verified.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
