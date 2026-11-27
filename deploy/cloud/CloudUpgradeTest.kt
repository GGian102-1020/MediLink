package ar.edu.uade.medilink.android.cloudtest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.MediLinkAppVm
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

private fun tokenHash(activity:MainActivity):String {
    val token=ViewModelProvider(activity)[MediLinkAppVm::class.java].repository.session.token
    check(!token.isNullOrBlank()){"No persisted session"}
    return MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)}
}

@RunWith(AndroidJUnit4::class)
class CloudUpgradeBeforeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){ui.waitUntil(240_000){runCatching{ui.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    @Test fun prepareRc1AuthenticatedSession(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val version=context.packageManager.getPackageInfo(context.packageName,0).versionCode
        assertEquals(InstrumentationRegistry.getArguments().getString("fromVersionCode")?.toInt()?:3,version)
        waitText("Iniciar sesión")
        val input=JSONObject(File(context.getExternalFilesDir(null),"cloud-test.private.json").readText())
        ui.onAllNodes(hasSetTextAction())[0].performTextInput(input.getString("email"))
        ui.onAllNodes(hasSetTextAction())[1].performTextInput(input.getString("password"))
        ui.onNodeWithText("Ingresar",ignoreCase=false).performClick();waitText("Cuenta")
        var hash="";ui.runOnUiThread{hash=tokenHash(ui.activity)}
        File(context.filesDir,"cloud-upgrade-proof.json").writeText(JSONObject().put("tokenSha256",hash).put("versionCode",version).toString())
    }
}

@RunWith(AndroidJUnit4::class)
class CloudUpgradeAfterTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){ui.waitUntil(240_000){runCatching{ui.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    @Test fun rc2RetainsExactlyTheSameSessionWithoutLogin(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val version=context.packageManager.getPackageInfo(context.packageName,0).versionCode
        assertEquals(InstrumentationRegistry.getArguments().getString("toVersionCode")?.toInt()?:4,version)
        val proof=File(context.filesDir,"cloud-upgrade-proof.json")
        val expected=JSONObject(proof.readText()).getString("tokenSha256")
        waitText("Cuenta");var after="";ui.runOnUiThread{after=tokenHash(ui.activity)}
        assertEquals(expected,after)
        ui.activityRule.scenario.recreate();waitText("Cuenta")
        ui.runOnUiThread{after=tokenHash(ui.activity)};assertEquals(expected,after)
        ui.onNodeWithText("Cuenta").performClick();waitText("Mi cuenta")
        val input=JSONObject(File(context.getExternalFilesDir(null),"cloud-test.private.json").readText())
        ui.onAllNodesWithText(input.getString("email")).onFirst().assertExists()
        val repository=ViewModelProvider(ui.activity)[MediLinkAppVm::class.java].repository
        runBlocking{repository.miPerfil()} // Real authenticated Render request using the retained token.
        File(context.getExternalFilesDir(null),"cloud-upgrade-result.json").writeText(JSONObject()
            .put("fromVersionCode",InstrumentationRegistry.getArguments().getString("fromVersionCode")?.toInt()?:3).put("toVersionCode",version).put("sameTokenAfterUpgrade",true)
            .put("retainedAfterActivityRecreation",true).put("authenticatedProfileVisible",true)
            .put("authenticatedBackendRequest",true).toString(2))
        ui.onNodeWithText("Cerrar sesión",substring=true).performScrollTo().performClick();waitText("Iniciar sesión")
        proof.delete()
    }
}
