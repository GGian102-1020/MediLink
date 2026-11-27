package ar.edu.uade.medilink.android.cloudtest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.uade.medilink.android.MainActivity
import ar.edu.uade.medilink.android.data.ArchivosReceta
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic capture/provider results; real Render config and login, zero document upload. */
@RunWith(AndroidJUnit4::class)
class CloudCaptureTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){ui.waitUntil(240_000){runCatching{ui.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    private fun removeAttachment(){
        ui.waitUntil(10_000){runCatching{ui.onNodeWithText("Quitar adjunto").assertIsEnabled();true}.getOrDefault(false)}
        ui.onNodeWithText("Quitar adjunto").performClick()
        ui.waitUntil(10_000){ui.onAllNodesWithText("Quitar adjunto").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun localCameraAndPdfPreviewStayIndependentOfDisabledAnalysis(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val input=File(context.getExternalFilesDir(null),"cloud-test.private.json")
        val archivos=ArchivosReceta(context)
        val pdf=File(archivos.crearCaptura().also{it.delete()}.path.removeSuffix(".jpg")+".pdf")
        val document=PdfDocument();val page=document.startPage(PdfDocument.PageInfo.Builder(500,700,1).create())
        page.canvas.drawColor(Color.WHITE);document.finishPage(page)
        pdf.outputStream().use(document::writeTo);document.close()
        val pdfUri=archivos.uriCaptura(pdf)
        var probe:CloudRequestProbe?=null
        var cameraCalls=0;var documentCalls=0;var cancelNext=false;var step="login"
        val monitor=object:Instrumentation.ActivityMonitor(){
            @Suppress("DEPRECATION")
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult?{
                if(intent.action==MediaStore.ACTION_IMAGE_CAPTURE){
                    cameraCalls++
                    val uri=intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
                    assertEquals("${context.packageName}.recetas",uri.authority)
                    val bitmap=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    context.contentResolver.openOutputStream(uri)!!.use{assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it))};bitmap.recycle()
                    return Instrumentation.ActivityResult(Activity.RESULT_OK,null)
                }
                if(intent.action==Intent.ACTION_OPEN_DOCUMENT){
                    documentCalls++
                    assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/pdf"))
                    if(cancelNext){cancelNext=false;return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)}
                    return Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(pdfUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try{
            waitText("Iniciar sesión")
            ui.runOnUiThread{probe=CloudRequestProbe(ui.activity)}
            val credentials=JSONObject(input.readText())
            ui.onAllNodes(hasSetTextAction())[0].performTextInput(credentials.getString("email"))
            ui.onAllNodes(hasSetTextAction())[1].performTextInput(credentials.getString("password"))
            ui.onNodeWithText("Ingresar",ignoreCase=false).performClick()
            waitText("Mis recetas");ui.onNodeWithText("Mis recetas").performScrollTo().performClick()
            step="disabled server configuration";waitText("El análisis automático está desactivado")
            ui.onNodeWithText("Adjuntar y verificar receta").assertIsEnabled().performClick()
            step="camera contract and full image preview";waitText("Cámara");ui.onNodeWithText("Cámara").performClick()
            waitText("Fotografía capturada");assertEquals(1,cameraCalls)
            ui.onNodeWithText("Analizar y verificar receta con IA").assertIsNotEnabled()
            removeAttachment()
            step="PDF provider and local preview";ui.onNodeWithText("Adjuntar y verificar receta").performClick()
            waitText("Archivos o galería");ui.onNodeWithText("Archivos o galería").performClick()
            waitText(pdf.name);assertEquals(1,documentCalls)
            ui.onNodeWithText("Analizar y verificar receta con IA").assertIsNotEnabled()
            File(context.getExternalFilesDir(null),"cloud-capture-preview.png").outputStream().use{
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
            }
            removeAttachment()
            step="picker cancellation";cancelNext=true
            ui.onNodeWithText("Adjuntar y verificar receta").performClick();waitText("Archivos o galería")
            ui.onNodeWithText("Archivos o galería").performClick()
            ui.waitUntil(10_000){documentCalls==2}
            ui.onNodeWithText("Quitar adjunto").assertDoesNotExist()
            ui.onNodeWithText("Adjuntar y verificar receta").assertIsEnabled()

            step="reservation attachment entry"
            ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};waitText("Buscar")
            ui.onNodeWithText("Buscar").performClick();waitText("Amoxicilina")
            ui.onAllNodesWithText("Amoxicilina",substring=true).onFirst().performClick()
            ui.waitUntil(60_000){runCatching{ui.onAllNodesWithContentDescription("Consultar disponibilidad").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            ui.onAllNodesWithContentDescription("Consultar disponibilidad").onFirst().performClick()
            waitText("El análisis automático está desactivado")
            ui.onNodeWithText("Escanear").performScrollTo().assertIsEnabled().performClick()
            waitText("captura.jpg");assertEquals(2,cameraCalls)
            ui.onNodeWithText("Verificar y continuar").assertIsNotEnabled()
            step="reservation PDF selection without upload"
            ui.waitUntil(10_000){runCatching{ui.onNodeWithText("Elegir otro").assertIsEnabled();true}.getOrDefault(false)}
            ui.onNodeWithText("Elegir otro").performScrollTo().performClick()
            waitText(pdf.name);assertEquals(3,documentCalls)
            ui.onNodeWithText("Verificar y continuar").assertIsNotEnabled()
            repeat(2){ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()}
            step="logout";ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()}
            waitText("Cuenta");ui.onNodeWithText("Cuenta").performClick();waitText("Mi cuenta")
            ui.onNodeWithText("Cerrar sesión",substring=true).performScrollTo().performClick();waitText("Iniciar sesión")
            val traffic=requireNotNull(probe).verifyAndReport()
            File(context.getExternalFilesDir(null),"cloud-capture-network.json").writeText(traffic.toString(2))
        }catch(failure:Throwable){
            throw AssertionError("Cloud capture test failed at step: $step (${failure.javaClass.simpleName})"+
                if(step=="login") "" else ": "+failure.message.orEmpty().take(600))
        }finally{probe?.close();instrumentation.removeMonitor(monitor);pdf.delete()}
    }
}
