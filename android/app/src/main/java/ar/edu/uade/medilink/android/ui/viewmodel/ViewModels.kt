package ar.edu.uade.medilink.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ar.edu.uade.medilink.android.data.CrearReservaRequest
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.data.IntentoReserva
import ar.edu.uade.medilink.android.data.MedicamentoDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PresentacionDto
import ar.edu.uade.medilink.android.data.ReservaDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VmsFactory(
    private val repository: MediLinkRepository,
    private val id: Long = 0,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        CatalogoVm::class.java -> CatalogoVm(repository)
        PresentacionesVm::class.java -> PresentacionesVm(repository, id)
        DisponibilidadVm::class.java -> DisponibilidadVm(repository, id)
        AuthVm::class.java -> AuthVm(repository)
        ReservasVm::class.java -> ReservasVm(repository)
        else -> throw IllegalArgumentException("ViewModel desconocido: $modelClass")
    } as T
}

data class CatalogoState(
    val lista: List<MedicamentoDto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val ultimaPagina: Boolean = true,
)

class CatalogoVm(private val repository: MediLinkRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogoState())
    val state = mutable.asStateFlow()
    private var consulta = ""
    private var siguientePagina = 0

    init { buscar("") }

    fun buscar(nombre: String) {
        if (mutable.value.cargando) return
        consulta = nombre.trim()
        siguientePagina = 0
        cargar(reset = true)
    }

    fun mas() {
        if (!mutable.value.ultimaPagina && !mutable.value.cargando) cargar(reset = false)
    }

    private fun cargar(reset: Boolean) {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cargando = true, error = null)
            try {
                val result = repository.medicamentos(consulta, siguientePagina)
                mutable.value = CatalogoState(
                    lista = if (reset) result.content else mutable.value.lista + result.content,
                    ultimaPagina = result.last,
                )
                siguientePagina++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }
}

data class PresentacionesState(
    val lista: List<PresentacionDto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val ultimaPagina: Boolean = true,
)

class PresentacionesVm(private val repository: MediLinkRepository, private val medicamentoId: Long) : ViewModel() {
    private val mutable = MutableStateFlow(PresentacionesState())
    val state = mutable.asStateFlow()
    private var siguientePagina = 0

    init { cargar() }

    fun mas() {
        if (!mutable.value.ultimaPagina && !mutable.value.cargando) cargar()
    }

    fun reintentar() {
        if (!mutable.value.cargando) cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cargando = true, error = null)
            try {
                val response = repository.presentaciones(medicamentoId, siguientePagina)
                mutable.value = PresentacionesState(
                    lista = mutable.value.lista + response.content,
                    ultimaPagina = response.last,
                )
                siguientePagina++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }
}

data class DisponibilidadState(
    val presentacion: PresentacionDto? = null,
    val farmacias: List<DisponibilidadDto> = emptyList(),
    val cargando: Boolean = false,
    val reservando: Boolean = false,
    val error: String? = null,
    val confirmacion: ReservaDto? = null,
)

class DisponibilidadVm(private val repository: MediLinkRepository, private val presentacionId: Long) : ViewModel() {
    private val mutable = MutableStateFlow(DisponibilidadState())
    val state = mutable.asStateFlow()
    private val intento = IntentoReserva()

    init { actualizar() }

    fun actualizar() {
        if (mutable.value.cargando) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cargando = true, error = null)
            try {
                val presentacion = repository.presentacion(presentacionId)
                val farmacias = repository.disponibilidad(presentacionId)
                mutable.value = mutable.value.copy(
                    presentacion = presentacion, farmacias = farmacias, cargando = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    fun reservar(farmaciaId: Long, cantidad: Int, codigoPrescripcion: String?) {
        if (mutable.value.reservando || cantidad < 1) return
        val body = CrearReservaRequest(
            farmaciaId = farmaciaId,
            presentacionId = presentacionId,
            cantidad = cantidad,
            codigoPrescripcion = codigoPrescripcion?.trim()?.ifBlank { null },
        )
        val key = intento.clavePara(body)
        viewModelScope.launch {
            mutable.value = mutable.value.copy(reservando = true, error = null)
            try {
                val reserva = repository.reservar(body, key)
                intento.confirmar()
                mutable.value = mutable.value.copy(reservando = false, confirmacion = reserva)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Para esta misma solicitud el reintento conservara Idempotency-Key.
                mutable.value = mutable.value.copy(reservando = false, error = mensajeDeError(e))
            }
        }
    }
}

data class AuthState(
    val cargando: Boolean = false,
    val error: String? = null,
    val inicioExitoso: Boolean = false,
    val registroExitoso: Boolean = false,
)

class AuthVm(private val repository: MediLinkRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AuthState())
    val state = mutable.asStateFlow()

    fun login(correo: String, clave: String) {
        if (mutable.value.cargando) return
        viewModelScope.launch {
            mutable.value = AuthState(cargando = true)
            try {
                repository.login(correo, clave)
                mutable.value = AuthState(inicioExitoso = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = AuthState(error = mensajeDeError(e))
            }
        }
    }

    fun registrar(nombre: String, apellido: String, correo: String, clave: String) {
        if (mutable.value.cargando) return
        viewModelScope.launch {
            mutable.value = AuthState(cargando = true)
            try {
                repository.registro(nombre, apellido, correo, clave)
                mutable.value = AuthState(registroExitoso = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = AuthState(error = mensajeDeError(e))
            }
        }
    }
}

data class ReservasState(
    val lista: List<ReservaDto> = emptyList(),
    val cargando: Boolean = false,
    val cancelandoCodigo: String? = null,
    val error: String? = null,
    val ultimaPagina: Boolean = true,
)

class ReservasVm(private val repository: MediLinkRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ReservasState())
    val state = mutable.asStateFlow()
    private var siguientePagina = 0

    init { actualizar() }

    fun actualizar() {
        if (mutable.value.cargando || mutable.value.cancelandoCodigo != null) return
        siguientePagina = 0
        cargar(limpiar = true)
    }

    fun mas() {
        if (!mutable.value.ultimaPagina && !mutable.value.cargando) cargar(limpiar = false)
    }

    private fun cargar(limpiar: Boolean) {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cargando = true, error = null)
            try {
                val page = repository.misReservas(siguientePagina)
                mutable.value = ReservasState(
                    lista = if (limpiar) page.content else mutable.value.lista + page.content,
                    ultimaPagina = page.last,
                )
                siguientePagina++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, error = mensajeDeError(e))
            }
        }
    }

    fun cancelar(codigo: String) {
        if (mutable.value.cancelandoCodigo != null) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cancelandoCodigo = codigo, error = null)
            try {
                val reserva = repository.cancelar(codigo)
                mutable.value = mutable.value.copy(
                    lista = mutable.value.lista.map {
                        if (it.codigoVisible == codigo) reserva else it
                    },
                    cancelandoCodigo = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cancelandoCodigo = null, error = mensajeDeError(e))
            }
        }
    }
}
