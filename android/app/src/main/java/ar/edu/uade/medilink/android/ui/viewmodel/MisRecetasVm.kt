package ar.edu.uade.medilink.android.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.medilink.android.data.AdjuntoReceta
import ar.edu.uade.medilink.android.data.ArchivosReceta
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PrescripcionDto
import ar.edu.uade.medilink.android.data.VinculacionRecetaDto
import ar.edu.uade.medilink.android.data.mensajeDeReceta
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class AccionReceta { PREPARANDO, VINCULANDO, ENVIANDO, ANALIZANDO }

internal fun puedeVerMisRecetas(rol: String?) = rol == "USUARIO"

internal data class MisRecetasState(
    val lista: List<PrescripcionDto> = emptyList(),
    val cargando: Boolean = false,
    val hayMas: Boolean = false,
    val errorLista: String? = null,
    val detalle: PrescripcionDto? = null,
    val cargandoDetalle: Boolean = false,
    val errorDetalle: String? = null,
    val medicamentos: Map<Long, String> = emptyMap(),
    val adjunto: AdjuntoReceta? = null,
    val foto: Boolean = false,
    val esperandoSelector: Boolean = false,
    val accion: AccionReceta? = null,
    val resultado: VinculacionRecetaDto? = null,
    val error: String? = null,
) {
    val ocupado get() = accion != null || esperandoSelector
}

internal class MisRecetasVm(
    private val repository: MediLinkRepository,
    private val archivos: ArchivosReceta,
) : ViewModel() {
    private val mutable = MutableStateFlow(MisRecetasState())
    val state = mutable.asStateFlow()
    private var pagina = 0
    private var carga: Job? = null
    private var cargaDetalle: Job? = null
    private var captura: File? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { archivos.limpiarTemporalesAntiguos() }
        actualizar()
    }

    fun actualizar() = cargar(reiniciar = true)

    fun mas() {
        if (mutable.value.hayMas && !mutable.value.cargando) cargar(reiniciar = false)
    }

    private fun cargar(reiniciar: Boolean) {
        carga?.cancel()
        val solicitada = if (reiniciar) 0 else pagina
        mutable.update { it.copy(cargando = true, errorLista = null) }
        carga = viewModelScope.launch {
            try {
                val respuesta = repository.misPrescripciones(solicitada)
                pagina = respuesta.page + 1
                mutable.update {
                    it.copy(lista = if (reiniciar) respuesta.content else it.lista + respuesta.content,
                        cargando = false, hayMas = !respuesta.last)
                }
                cargarNombres(respuesta.content)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(cargando = false, errorLista = mensajeDeReceta(error)) }
            }
        }
    }

    fun abrirDetalle(id: Long) {
        cargaDetalle?.cancel()
        mutable.update { it.copy(detalle = null, cargandoDetalle = true, errorDetalle = null) }
        cargaDetalle = viewModelScope.launch {
            try {
                val detalle = repository.detallePrescripcion(id)
                mutable.update { it.copy(detalle = detalle, cargandoDetalle = false) }
                cargarNombres(listOf(detalle))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(cargandoDetalle = false, errorDetalle = mensajeDeReceta(error)) }
            }
        }
    }

    fun cerrarDetalle() {
        cargaDetalle?.cancel()
        mutable.update { it.copy(detalle = null, cargandoDetalle = false, errorDetalle = null) }
    }

    private suspend fun cargarNombres(recetas: List<PrescripcionDto>) {
        val ids = recetas.flatMap { receta -> receta.items.map { it.presentacionId } }.distinct()
        for (id in ids) {
            if (id in mutable.value.medicamentos) continue
            try {
                val nombre = repository.presentacion(id).medicamentoNombre
                mutable.update { it.copy(medicamentos = it.medicamentos + (id to nombre)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // La descripción de la presentación sigue disponible en la prescripción.
            }
        }
    }

    fun abrirArchivos() {
        mutable.update { it.copy(esperandoSelector = true, error = null) }
    }

    fun abrirCamara(): Uri {
        val archivo = archivos.crearCaptura()
        captura = archivo
        mutable.update { it.copy(esperandoSelector = true, error = null) }
        return archivos.uriCaptura(archivo)
    }

    fun falloSelector(mensaje: String) {
        finalizarCaptura(false)
        mutable.update { it.copy(esperandoSelector = false, error = mensaje, resultado = null) }
    }

    fun finalizarArchivos(uri: Uri?) {
        mutable.update { it.copy(esperandoSelector = false) }
        if (uri != null) preparar(foto = false) { archivos.importar(uri) }
    }

    fun finalizarCaptura(tomada: Boolean) {
        val archivo = captura
        captura = null
        mutable.update { it.copy(esperandoSelector = false) }
        if (archivo == null) return
        if (tomada) preparar(foto = true, temporal = archivo) { archivos.cargarCaptura(archivo) }
        else archivo.delete()
    }

    private fun preparar(foto: Boolean, temporal: File? = null, cargar: () -> AdjuntoReceta) {
        mutable.update { it.copy(accion = AccionReceta.PREPARANDO, error = null, resultado = null) }
        viewModelScope.launch {
            var preparado: AdjuntoReceta? = null
            try {
                withContext(Dispatchers.IO) { preparado = cargar() }
                mutable.value.adjunto?.archivo?.delete()
                mutable.update { it.copy(adjunto = preparado, foto = foto) }
                preparado = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(error = if (error is IllegalArgumentException) error.message
                    else "No se pudo abrir el archivo. Probá con otro documento.") }
            } finally {
                preparado?.archivo?.delete()
                if (mutable.value.adjunto?.archivo != temporal) temporal?.delete()
                mutable.update { it.copy(accion = null) }
            }
        }
    }

    fun quitarAdjunto() {
        if (mutable.value.ocupado) return
        mutable.value.adjunto?.archivo?.delete()
        mutable.update { it.copy(adjunto = null, foto = false, error = null, resultado = null) }
    }

    fun vincular(codigo: String) {
        if (mutable.value.ocupado) return
        if (codigo.isBlank()) {
            mutable.update { it.copy(resultado = null, error = "Ingresá el código de tu receta.") }
            return
        }
        ejecutar(AccionReceta.VINCULANDO) { repository.vincularReceta(codigo) }
    }

    fun escanear() {
        val adjunto = mutable.value.adjunto ?: return
        ejecutar(AccionReceta.ENVIANDO) {
            repository.escanearReceta(adjunto.multipart()) {
                mutable.update { if (it.accion == AccionReceta.ENVIANDO) it.copy(accion = AccionReceta.ANALIZANDO) else it }
            }
        }
    }

    private fun ejecutar(accion: AccionReceta, operacion: suspend () -> VinculacionRecetaDto) {
        if (mutable.value.ocupado) return
        mutable.update { it.copy(accion = accion, resultado = null, error = null) }
        viewModelScope.launch {
            try {
                val resultado = operacion()
                if (accion == AccionReceta.ENVIANDO) mutable.value.adjunto?.archivo?.delete()
                mutable.update {
                    if (accion == AccionReceta.ENVIANDO) it.copy(resultado = resultado, adjunto = null, foto = false)
                    else it.copy(resultado = resultado)
                }
                actualizar()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(error = mensajeDeReceta(error)) }
            } finally {
                mutable.update { it.copy(accion = null) }
            }
        }
    }

    override fun onCleared() {
        mutable.value.adjunto?.archivo?.delete()
        captura?.delete()
    }
}
