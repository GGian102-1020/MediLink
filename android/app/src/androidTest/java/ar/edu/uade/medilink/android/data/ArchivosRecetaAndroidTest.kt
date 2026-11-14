package ar.edu.uade.medilink.android.data

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchivosRecetaAndroidTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val archivos = ArchivosReceta(contexto)

    @Test fun camaraRecibeUnaUriPrivadaYConservaLaFotoCompleta() {
        val foto = archivos.crearCaptura()
        try {
            val uri = archivos.uriCaptura(foto)
            assertEquals("content", uri.scheme)
            assertEquals("${contexto.packageName}.recetas", uri.authority)
            val camara = ActivityResultContracts.TakePicture()
            val intent = camara.createIntent(contexto, uri)
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
            @Suppress("DEPRECATION")
            assertEquals(uri, intent.extras?.get(MediaStore.EXTRA_OUTPUT))
            assertFalse(camara.parseResult(Activity.RESULT_CANCELED, null))
            val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                contexto.contentResolver.openOutputStream(uri)!!.use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
                }
                val resultado = archivos.cargarCaptura(foto)
                assertTrue(resultado.esImagen)
                val dimensiones = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(resultado.archivo.path, dimensiones)
                assertEquals(600, dimensiones.outWidth)
                assertEquals(900, dimensiones.outHeight)
            } finally {
                bitmap.recycle()
            }
        } finally {
            foto.delete()
        }
    }

    @Test fun selectorAceptaPdfEImagenPorUriYAdmiteCancelacion() {
        val selector = ActivityResultContracts.OpenDocument()
        val intent = selector.createIntent(contexto, TIPOS_RECETA)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(TIPOS_RECETA, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertNull(selector.parseResult(Activity.RESULT_CANCELED, null))
        for ((contenido, tipo) in listOf(
            "%PDF-fixture".toByteArray() to "application/pdf",
            byteArrayOf(-1, -40, -1, 0, 1) to "image/jpeg",
        )) {
            val original = archivos.crearCaptura()
            try {
                original.writeBytes(contenido)
                val uri = archivos.uriCaptura(original)
                val seleccionado = selector.parseResult(Activity.RESULT_OK, Intent().setData(uri))
                // FileProvider declara image/jpeg para una captura con extensión .jpg.
                // Un PDF de Archivos usa una extensión y un MIME acordes con su contenido.
                val documento = if (tipo == "application/pdf") {
                    val pdf = java.io.File(original.parentFile, "${original.nameWithoutExtension}.pdf")
                    assertTrue(original.renameTo(pdf))
                    try {
                        archivos.importar(archivos.uriCaptura(pdf))
                    } finally {
                        pdf.delete()
                    }
                } else archivos.importar(requireNotNull(seleccionado))
                try {
                    assertEquals(tipo, documento.tipo)
                    assertArrayEquals(contenido, documento.archivo.readBytes())
                } finally {
                    documento.archivo.delete()
                }
            } finally {
                original.delete()
            }
        }
    }
}
