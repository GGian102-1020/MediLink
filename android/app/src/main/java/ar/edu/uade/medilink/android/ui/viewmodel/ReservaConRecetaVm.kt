package ar.edu.uade.medilink.android.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.medilink.android.data.AdjuntoReceta
import ar.edu.uade.medilink.android.data.ArchivosReceta
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PresentacionDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receta lista para reservar: código de la prescripción y cantidad que autoriza. */
data class RecetaLista(val codigo: String, val cantidad: Int)

internal data class RecetaPasoState(
    val presentacion: PresentacionDto? = null,
    val adjunto: AdjuntoReceta? = null,
    /** true si el adjunto es una foto tomada con la cámara (escaneo). */
    val escaneado: Boolean = false,
    val preparando: Boolean = false,
    val enviando: Boolean = false,
    val error: String? = null,
    /** Se completa al tocar "Continuar": la pantalla navega a las farmacias con stock. */
    val lista: RecetaLista? = null,
)

/**
 * Paso 1 de una reserva BAJO_RECETA, antes de elegir farmacia: el paciente adjunta la receta
 * (PDF/imagen o escaneo con la cámara) e indica la cantidad. Al continuar se sube el archivo,
 * que crea su prescripción, y con ese código se reserva después en la farmacia elegida.
 * Volver y continuar con el mismo archivo y cantidad reutiliza la receta ya subida.
 */
internal class ReservaConRecetaVm(
    private val repository: MediLinkRepository,
    private val archivos: ArchivosReceta,
    private val presentacionId: Long,
) : ViewModel() {
    private val mutable = MutableStateFlow(RecetaPasoState())
    val state = mutable.asStateFlow()
    private var captura: File? = null
    private var subida: Triple<File, Int, String>? = null

    init {
        archivos.limpiarTemporalesAntiguos()
        viewModelScope.launch {
            try { mutable.update { it.copy(presentacion = repository.presentacion(presentacionId)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = mensajeDeError(e)) } }
        }
    }

    fun abrirCamara(): Uri {
        val archivo = archivos.crearCaptura()
        captura = archivo
        mutable.update { it.copy(error = null) }
        return archivos.uriCaptura(archivo)
    }

    fun finalizarCaptura(tomada: Boolean) {
        val archivo = captura ?: return
        captura = null
        if (tomada) preparar(escaneado = true, temporal = archivo) { archivos.cargarCaptura(archivo) }
        else archivo.delete()
    }

    fun finalizarArchivo(uri: Uri?) {
        if (uri != null) preparar(escaneado = false) { archivos.importar(uri) }
    }

    fun falloSelector(mensaje: String) {
        captura?.delete(); captura = null
        mutable.update { it.copy(error = mensaje) }
    }

    fun quitarAdjunto() {
        mutable.value.adjunto?.archivo?.delete()
        subida = null
        mutable.update { it.copy(adjunto = null, escaneado = false, error = null) }
    }

    private fun preparar(escaneado: Boolean, temporal: File? = null, cargar: () -> AdjuntoReceta) {
        mutable.update { it.copy(preparando = true, error = null) }
        viewModelScope.launch {
            var preparado: AdjuntoReceta? = null
            try {
                preparado = withContext(Dispatchers.IO) { cargar() }
                mutable.value.adjunto?.archivo?.delete()
                subida = null
                mutable.update { it.copy(adjunto = preparado, escaneado = escaneado) }
                preparado = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(error = if (e is IllegalArgumentException) e.message
                    else "No se pudo abrir el archivo. Probá con otro documento.") }
            } finally {
                preparado?.archivo?.delete()
                if (mutable.value.adjunto?.archivo != temporal) temporal?.delete()
                mutable.update { it.copy(preparando = false) }
            }
        }
    }

    /** Sube la receta adjunta (si no se subió ya con esta cantidad) y habilita continuar. */
    fun continuar(cantidad: Int) {
        val estado = mutable.value
        val adjunto = estado.adjunto ?: return
        if (estado.enviando || cantidad < 1) return
        val previa = subida
        if (previa != null && previa.first == adjunto.archivo && previa.second == cantidad) {
            mutable.update { it.copy(lista = RecetaLista(previa.third, cantidad)) }
            return
        }
        mutable.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val codigo = repository.adjuntarReceta(adjunto, presentacionId, cantidad).codigoPrescripcion
                subida = Triple(adjunto.archivo, cantidad, codigo)
                mutable.update { it.copy(enviando = false, lista = RecetaLista(codigo, cantidad)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(enviando = false, error = mensajeDeError(e)) } }
        }
    }

    /** Alternativa: receta electrónica ya vinculada a la cuenta (código impreso en la receta). */
    fun continuarConCodigo(codigo: String, cantidad: Int) {
        val limpio = codigo.trim()
        if (limpio.isEmpty() || cantidad < 1) return
        mutable.update { it.copy(error = null, lista = RecetaLista(limpio, cantidad)) }
    }

    /** La pantalla ya navegó: se limpia para que al volver no se vuelva a disparar. */
    fun navegacionHecha() = mutable.update { it.copy(lista = null) }

    override fun onCleared() {
        captura?.delete()
        mutable.value.adjunto?.archivo?.delete()
    }
}
