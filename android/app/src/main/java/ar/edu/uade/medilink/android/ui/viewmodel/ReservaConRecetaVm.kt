package ar.edu.uade.medilink.android.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.medilink.android.data.AdjuntoReceta
import ar.edu.uade.medilink.android.data.ArchivosReceta
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PresentacionDto
import ar.edu.uade.medilink.android.data.PrescripcionDto
import ar.edu.uade.medilink.android.data.impedimentoParaReserva
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.data.mensajeDeReceta
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
    val analizando: Boolean = false,
    val escaneoHabilitado: Boolean? = null,
    val errorConfiguracion: String? = null,
    val esperandoSelector: Boolean = false,
    val error: String? = null,
    val recetas: List<PrescripcionDto> = emptyList(),
    val cargandoRecetas: Boolean = false,
    val errorRecetas: String? = null,
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
        viewModelScope.launch(Dispatchers.IO) { archivos.limpiarTemporalesAntiguos() }
        viewModelScope.launch {
            try { mutable.update { it.copy(presentacion = repository.presentacion(presentacionId)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = mensajeDeError(e)) } }
        }
        consultarEscaneo()
    }

    fun consultarEscaneo() {
        viewModelScope.launch {
            try {
                val configuracion=repository.configuracionRecetas()
                mutable.update { it.copy(escaneoHabilitado=configuracion.escaneoHabilitado,errorConfiguracion=null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.update { it.copy(escaneoHabilitado=null,errorConfiguracion="No pudimos consultar el análisis de recetas. Reintentá en esta pantalla.") }
            }
        }
    }

    fun cargarRecetas() {
        if (mutable.value.cargandoRecetas) return
        mutable.update { it.copy(cargandoRecetas = true, errorRecetas = null) }
        viewModelScope.launch {
            try {
                val todas = mutableListOf<PrescripcionDto>()
                var pagina = 0
                do {
                    val respuesta = repository.misPrescripciones(pagina++)
                    todas.addAll(respuesta.content)
                } while (!respuesta.last)
                mutable.update { it.copy(recetas = todas.distinctBy { receta -> receta.id }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(errorRecetas = mensajeDeError(e)) } }
            finally { mutable.update { it.copy(cargandoRecetas = false) } }
        }
    }

    fun continuarConReceta(id: Long, cantidad: Int) {
        val receta = mutable.value.recetas.firstOrNull { it.id == id } ?: return
        val impedimento = receta.impedimentoParaReserva(presentacionId, cantidad)
        if (impedimento != null) { mutable.update { it.copy(error = impedimento) }; return }
        continuarConCodigo(receta.codigoExterno, cantidad)
    }

    fun abrirCamara(): Uri {
        val archivo = archivos.crearCaptura()
        captura = archivo
        mutable.update { it.copy(error = null, esperandoSelector = true) }
        return archivos.uriCaptura(archivo)
    }

    fun finalizarCaptura(tomada: Boolean) {
        mutable.update { it.copy(esperandoSelector = false) }
        val archivo = captura ?: return
        captura = null
        if (tomada) preparar(escaneado = true, temporal = archivo) { archivos.cargarCaptura(archivo) }
        else archivo.delete()
    }

    fun finalizarArchivo(uri: Uri?) {
        mutable.update { it.copy(esperandoSelector = false) }
        if (uri != null) preparar(escaneado = false) { archivos.importar(uri) }
    }

    fun abrirArchivo() { mutable.update { it.copy(esperandoSelector = true,error=null) } }

    fun falloSelector(mensaje: String) {
        captura?.delete(); captura = null
        mutable.update { it.copy(error = mensaje, esperandoSelector = false) }
    }

    fun quitarAdjunto() {
        if (mutable.value.enviando || mutable.value.preparando) return
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

    /** Analiza la receta y valida presentación y cantidad antes de elegir farmacia. */
    fun continuar(cantidad: Int) {
        val estado = mutable.value
        val adjunto = estado.adjunto ?: return
        if (estado.enviando || estado.preparando || estado.escaneoHabilitado != true || cantidad !in 1..10) return
        val previa = subida
        if (previa != null && previa.first == adjunto.archivo && previa.second == cantidad) {
            mutable.update { it.copy(lista = RecetaLista(previa.third, cantidad)) }
            return
        }
        mutable.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            try {
                val resultado = repository.escanearRecetaParaReserva(adjunto,presentacionId,cantidad) {
                    mutable.update { it.copy(analizando=true) }
                }
                val codigo=resultado.prescripcion.codigoExterno
                subida = Triple(adjunto.archivo, cantidad, codigo)
                mutable.update { it.copy(enviando = false, analizando=false, lista = RecetaLista(codigo, cantidad)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(enviando = false, analizando=false, error = mensajeDeReceta(e)) } }
        }
    }

    /** Alternativa: receta electrónica ya vinculada a la cuenta (código impreso en la receta). */
    fun continuarConCodigo(codigo: String, cantidad: Int) {
        val limpio = codigo.trim()
        if (limpio.isEmpty() || cantidad !in 1..10 || mutable.value.enviando) return
        mutable.update { it.copy(error = null, enviando=true,analizando=false) }
        viewModelScope.launch {
            try {
                val resultado=repository.vincularRecetaParaReserva(limpio,presentacionId,cantidad)
                mutable.update { it.copy(enviando=false,lista=RecetaLista(resultado.prescripcion.codigoExterno,cantidad)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(enviando=false,error=mensajeDeReceta(e)) } }
        }
    }

    /** La pantalla ya navegó: se limpia para que al volver no se vuelva a disparar. */
    fun navegacionHecha() = mutable.update { it.copy(lista = null) }

    override fun onCleared() {
        captura?.delete()
        mutable.value.adjunto?.archivo?.delete()
    }
}
