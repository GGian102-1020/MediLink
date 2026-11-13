package ar.edu.uade.medilink.android.data

import ar.edu.uade.medilink.android.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** La sesion reside en memoria. No se guardan JWT ni credenciales en disco. */
class SessionStore {
    private val mutableLogin = MutableStateFlow<LoginDto?>(null)
    val login = mutableLogin.asStateFlow()
    val token: String? get() = mutableLogin.value?.accessToken

    fun set(login: LoginDto) { mutableLogin.value = login }
    fun clear() { mutableLogin.value = null }
}

class MediLinkRepository(val session: SessionStore = SessionStore()) {
    /** Origen geográfico solo en memoria; no se persiste en disco ni se comparte sin acción del usuario. */
    val origen = MutableStateFlow<PuntoGeo?>(null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val currentToken = session.token
            val request = chain.request().newBuilder()
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .apply {
                    if (currentToken != null) header("Authorization", "Bearer $currentToken")
                }
                .build()
            val response = chain.proceed(request)
            // Si la API revoco o vencio este JWT, la UI vuelve al modo no autenticado.
            if (response.code == 401 && currentToken != null && session.token == currentToken) {
                session.clear()
            }
            response
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(MediLinkApi::class.java)
    val parity = retrofit.create(ParityApi::class.java)

    suspend fun medicamentos(nombre: String, pagina: Int) =
        api.medicamentos(nombre.ifBlank { null }, pagina, 20)

    suspend fun presentaciones(id: Long, pagina: Int) = api.presentaciones(id, pagina, 20)
    suspend fun presentacion(id: Long) = api.presentacion(id)
    suspend fun disponibilidad(id: Long, lat: Double? = null, lng: Double? = null) =
        api.disponibilidad(id, lat, lng)

    suspend fun login(correo: String, clave: String) {
        val response = api.login(LoginRequest(correo.trim(), clave))
        session.set(response)
    }

    suspend fun registro(
        nombre: String, apellido: String, correo: String, clave: String,
        datos: DatosPersonales? = null,
    ) {
        api.registro(RegisterRequest(
            nombre = nombre.trim(), apellido = apellido.trim(), correo = correo.trim(), contrasena = clave,
            dni = datos?.dni?.filter(Char::isDigit),
            fechaNacimiento = datos?.fechaNacimiento?.toString(),
            telefono = datos?.telefono?.trim()?.ifBlank { null },
            obraSocial = datos?.obraSocial?.trim()?.ifBlank { null },
            numeroAfiliado = datos?.numeroAfiliado?.trim()?.ifBlank { null }
                ?.takeIf { !datos.obraSocial.isNullOrBlank() },
        ))
    }

    suspend fun logout() {
        try {
            if (session.token != null) {
                // Si falla la red igualmente se descarta el JWT local.
                api.logout()
            }
        } finally {
            session.clear()
            origen.value = null
        }
    }

    suspend fun reservar(body: CrearReservaRequest, claveIdempotencia: String) =
        api.crearReserva(claveIdempotencia, body)

    suspend fun misReservas(pagina: Int, estado: String? = null) = parity.reservasUsuario(estado, pagina)
    suspend fun miPerfil() = parity.me()
    suspend fun cambiarClave(actual: String, nueva: String) = parity.cambiarClave(CambiarClaveRequest(actual, nueva))
    suspend fun detalleReserva(codigo: String) = parity.detalleUsuario(codigo)
    suspend fun cancelar(codigo: String) = api.cancelar(codigo)
}

/** Mensaje legible basado en los errores REST reales de MediLink. */
fun mensajeDeError(error: Throwable): String = when (error) {
    is IllegalArgumentException -> error.message ?: "Datos incorrectos."
    is IOException -> "No se pudo conectar con MediLink. Revisá el servidor y adb reverse."
    is HttpException -> {
        val apiMessage = try {
            val json = error.response()?.errorBody()?.string()
            if (json.isNullOrBlank()) null else JsonParser.parseString(json)
                .asJsonObject.get("mensaje")?.takeIf { !it.isJsonNull }?.asString
        } catch (_: Exception) {
            null
        }
        apiMessage ?: when (error.code()) {
            401 -> "Sesión vencida. Volvé a iniciar sesión."
            403 -> "No tenés permiso para esta operación."
            404 -> "No se encontró la información solicitada."
            409 -> "La operación no pudo completarse por un conflicto."
            else -> "Error del servidor (${error.code()})."
        }
    }
    else -> "Ocurrió un error inesperado."
}

/** Mantiene la misma clave al reintentar la misma solicitud. */
class IntentoReserva {
    private var solicitud: CrearReservaRequest? = null
    private var clave: String? = null

    fun clavePara(nuevaSolicitud: CrearReservaRequest): String {
        if (solicitud != nuevaSolicitud || clave == null) {
            solicitud = nuevaSolicitud
            clave = UUID.randomUUID().toString()
        }
        return requireNotNull(clave)
    }

    fun confirmar() {
        solicitud = null
        clave = null
    }
}
