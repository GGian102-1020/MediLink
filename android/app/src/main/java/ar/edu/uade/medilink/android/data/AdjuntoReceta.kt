package ar.edu.uade.medilink.android.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal const val LIMITE_RECETA_BYTES = 6 * 1024 * 1024
internal val TIPOS_RECETA = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")
private const val ERROR_TAMANIO = "El archivo supera el máximo de 6 MiB."
private const val ERROR_FORMATO = "Elegí un PDF de una página o una imagen JPG, PNG o WebP."

internal data class AdjuntoReceta(val archivo: File, val nombre: String, val tipo: String) {
    val esImagen get() = tipo.startsWith("image/")

    fun multipart(): MultipartBody.Part = MultipartBody.Part.createFormData(
        "archivo", nombre.replace(Regex("[\u0000-\u001F\u007F]"), "_"),
        archivo.asRequestBody(tipo.toMediaType()),
    )
}

internal class ArchivosReceta(context: Context) {
    private val app = context.applicationContext
    private val carpeta get() = File(app.cacheDir, "recetas").apply { mkdirs() }
    private val resolver get() = app.contentResolver

    fun crearCaptura(): File = File.createTempFile("camara-", ".jpg", carpeta)

    fun uriCaptura(archivo: File): Uri =
        FileProvider.getUriForFile(app, "${app.packageName}.recetas", archivo)

    fun cargarCaptura(archivo: File): AdjuntoReceta {
        val tipo = archivo.inputStream().use { detectarTipoReceta(leerCabecera(it)) }
        require(tipo.startsWith("image/")) { ERROR_FORMATO }
        if (tipo == "image/jpeg") return prepararJpeg(archivo)
        require(archivo.length() <= LIMITE_RECETA_BYTES) { ERROR_TAMANIO }
        return AdjuntoReceta(archivo, "captura.${tipo.substringAfter('/').replace("jpeg", "jpg")}", tipo)
    }

    private fun prepararJpeg(archivo: File): AdjuntoReceta {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, opciones)
        val lado = maxOf(opciones.outWidth, opciones.outHeight)
        require(lado > 0) { "No se pudo leer la fotografía capturada." }
        val orientacion = ExifInterface(archivo.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )
        val adjunto = AdjuntoReceta(archivo, "captura.jpg", "image/jpeg")
        if (lado <= 2400 && archivo.length() <= LIMITE_RECETA_BYTES &&
            orientacion in ExifInterface.ORIENTATION_UNDEFINED..ExifInterface.ORIENTATION_NORMAL
        ) return adjunto

        opciones.inJustDecodeBounds = false
        opciones.inSampleSize = 1
        while (lado / opciones.inSampleSize > 4096) opciones.inSampleSize *= 2
        val original = requireNotNull(BitmapFactory.decodeFile(archivo.path, opciones)) {
            "No se pudo leer la fotografía capturada."
        }
        val matriz = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matriz.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matriz.setRotate(90f); matriz.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matriz.setRotate(-90f); matriz.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.setRotate(-90f)
        }
        val escala = minOf(1f, 2400f / maxOf(original.width, original.height))
        matriz.postScale(escala, escala)
        try {
            val foto = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
            try {
                val guardada = archivo.outputStream().use { foto.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                require(guardada) { "No se pudo preparar la fotografía capturada." }
                require(archivo.length() <= LIMITE_RECETA_BYTES) { ERROR_TAMANIO }
            } finally {
                if (foto !== original) foto.recycle()
            }
        } finally {
            original.recycle()
        }
        return adjunto
    }

    fun importar(uri: Uri): AdjuntoReceta {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "No se pudo abrir el documento seleccionado." }
        var nombre: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (indice >= 0) nombre = cursor.getString(indice)
                    val tamanio = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (tamanio >= 0 && !cursor.isNull(tamanio)) {
                        require(cursor.getLong(tamanio) <= LIMITE_RECETA_BYTES) { ERROR_TAMANIO }
                    }
                }
            }
        val tipoDeclarado = resolver.getType(uri)
        val destino = File.createTempFile("adjunto-", ".tmp", carpeta)
        try {
            val tipo = requireNotNull(resolver.openInputStream(uri)) { "No se pudo abrir el documento seleccionado." }
                .buffered().use { entrada ->
                    entrada.mark(12)
                    val detectado = detectarTipoReceta(leerCabecera(entrada), tipoDeclarado)
                    entrada.reset()
                    destino.outputStream().use { salida -> copiarReceta(entrada, salida) }
                    detectado
                }
            return AdjuntoReceta(destino, nombre?.takeIf { it.isNotBlank() } ?: "Receta adjunta", tipo)
        } catch (error: Exception) {
            destino.delete()
            throw error
        }
    }

    fun limpiarTemporalesAntiguos() {
        val ayer = System.currentTimeMillis() - 86_400_000L
        carpeta.listFiles()?.filter { it.isFile && it.lastModified() < ayer }?.forEach { it.delete() }
    }
}

private fun leerCabecera(entrada: InputStream): ByteArray {
    val cabecera = ByteArray(12)
    var total = 0
    while (total < cabecera.size) {
        val leidos = entrada.read(cabecera, total, cabecera.size - total)
        if (leidos == -1) break
        total += leidos
    }
    return cabecera.copyOf(total)
}

internal fun detectarTipoReceta(cabecera: ByteArray, declarado: String? = null): String {
    fun empieza(vararg firma: Int) = cabecera.size >= firma.size &&
        firma.indices.all { (cabecera[it].toInt() and 0xff) == firma[it] }

    val tipo = when {
        empieza(0x25, 0x50, 0x44, 0x46, 0x2d) -> "application/pdf"
        empieza(0xff, 0xd8, 0xff) -> "image/jpeg"
        empieza(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
        empieza(0x52, 0x49, 0x46, 0x46) && cabecera.size >= 12 &&
            String(cabecera, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> throw IllegalArgumentException(ERROR_FORMATO)
    }
    val especifico = declarado?.lowercase()?.replace("image/jpg", "image/jpeg")?.takeIf { it in TIPOS_RECETA }
    require(especifico == null || especifico == tipo) { "El formato del archivo no coincide con el tipo indicado." }
    return tipo
}

internal fun copiarReceta(entrada: InputStream, salida: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val cantidad = entrada.read(buffer, 0, minOf(buffer.size, LIMITE_RECETA_BYTES - total + 1))
        if (cantidad == -1) return
        total += cantidad
        require(total <= LIMITE_RECETA_BYTES) { ERROR_TAMANIO }
        salida.write(buffer, 0, cantidad)
    }
}
