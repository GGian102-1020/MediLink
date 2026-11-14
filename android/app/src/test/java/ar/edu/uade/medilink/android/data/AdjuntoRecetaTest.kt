package ar.edu.uade.medilink.android.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream

class AdjuntoRecetaTest {
    @Test fun identificaPdfYLasImagenesAdmitidasPorContenido() {
        assertEquals("application/pdf", detectarTipoReceta("%PDF-1.5".toByteArray(), "application/pdf"))
        assertEquals("image/jpeg", detectarTipoReceta(byteArrayOf(-1, -40, -1), "image/jpg"))
        assertEquals("image/png", detectarTipoReceta(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        assertEquals("image/webp", detectarTipoReceta("RIFF0000WEBP".toByteArray(), "application/octet-stream"))
    }

    @Test fun rechazaTipoIncorrectoYArchivoVacio() {
        assertThrows(IllegalArgumentException::class.java) {
            detectarTipoReceta("GIF89a".toByteArray(), "image/png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            detectarTipoReceta("%PDF-1.5".toByteArray(), "image/png")
        }
        assertThrows(IllegalArgumentException::class.java) { detectarTipoReceta(byteArrayOf()) }
    }

    @Test fun cortaElStreamSiSuperaElLimiteAunqueNoHayaMetadatos() {
        val salida = OutputStream.nullOutputStream()
        copiarReceta(ByteArrayInputStream(ByteArray(LIMITE_RECETA_BYTES)), salida)
        val error = assertThrows(IllegalArgumentException::class.java) {
            copiarReceta(ByteArrayInputStream(ByteArray(LIMITE_RECETA_BYTES + 1)), salida)
        }
        assertTrue(error.message!!.contains("6 MiB"))
    }
}
