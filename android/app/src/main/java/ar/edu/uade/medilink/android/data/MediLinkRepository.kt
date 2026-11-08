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

    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MediLinkApi::class.java)

    suspend fun medicamentos(nombre: String, pagina: Int) =
        api.medicamentos(nombre.ifBlank { null }, pagina, 20)

    suspend fun presentaciones(id: Long, pagina: Int) = api.presentaciones(id, pagina, 20)
    suspend fun presentacion(id: Long) = api.presentacion(id)
    suspend fun disponibilidad(id: Long) = api.disponibilidad(id)

    suspend fun login(correo: String, clave: String) {
        val response = api.login(LoginRequest(correo.trim(), clave))
        if (response.perfil.rol != "USUARIO") {
            // La app de esta etapa es solo para pacientes; no se almacena el token.
            throw IllegalArgumentException("Esta version es solo para cuentas de pacientes.")
        }
        session.set(response)
    }

    suspend fun registro(nombre: String, apellido: String, correo: String, clave: String) {
        api.registro(RegisterRequest(nombre.trim(), apellido.trim(), correo.trim(), clave))
    }

    suspend fun logout() {
        try {
            if (session.token != null) {
                // Si falla la red igualmente se descarta el JWT local.
                api.logout()
            }
        } finally {
            session.clear()
        }
    }

    suspend fun reservar(body: CrearReservaRequest, claveIdempotencia: String) =
        api.crearReserva(claveIdempotencia, body)

    suspend fun misReservas(pagina: Int) = api.misReservas(pagina, 20)
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
