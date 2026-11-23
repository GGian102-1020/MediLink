package ar.edu.uade.medilink.android.data

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.net.SocketTimeoutException

internal fun mensajeDeReceta(error: Exception): String {
    if (error is SocketTimeoutException) {
        return "La solicitud tardó demasiado. Podés reintentar o vincular por código."
    }
    if (error !is HttpException) return mensajeDeError(error)
    val cuerpo = runCatching {
        error.response()?.errorBody()?.string()?.let { JsonParser.parseString(it).asJsonObject }
    }.getOrNull()
    fun texto(campo: String) = cuerpo?.get(campo)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString
    return when (texto("codigo")) {
        "LECTURA_INCONCLUSA" -> "No se pudo leer la receta completa. Probá con otra foto o ingresá el código."
        "RECETA_DEMO_DATOS_NO_COINCIDEN" -> "Los datos del documento no coinciden con la receta registrada."
        "RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO" -> "El código secundario impreso no coincide con el registrado."
        "RECETA_DEMO_NO_VIGENTE" -> "La receta está fuera de vigencia."
        "RECETA_MEDICAMENTO_NO_COINCIDE" -> "La receta no corresponde a este medicamento y presentación. Adjuntá la receta correcta o elegí el medicamento indicado."
        "RECETA_CANTIDAD_INSUFICIENTE" -> texto("mensaje") ?: "La receta no tiene unidades suficientes para la cantidad solicitada."
        "ESCANEO_CREDENCIALES_INVALIDAS" -> "El análisis de recetas necesita que se revise su configuración. Tu archivo sigue disponible para reintentar."
        "ESCANEO_SIN_SALDO" -> "El servicio de análisis no tiene saldo disponible. Tu archivo sigue disponible para reintentar."
        "ESCANEO_MODELO_NO_DISPONIBLE" -> "El modelo de análisis no está disponible. Tu archivo sigue disponible para reintentar."
        "ESCANEO_LIMITE" -> "Alcanzaste el límite de escaneos. Esperá un momento o ingresá el código."
        "ESCANEO_PROVEEDOR_ERROR" -> "Falló el servicio de análisis. Podés reintentar o ingresar el código."
        "ESCANEO_NO_CONFIGURADO" -> "El escaneo con IA no está habilitado. Podés vincular por código."
        "ESCANEO_TIMEOUT" -> "El servicio de análisis tardó demasiado. Podés reintentar o ingresar el código."
        else -> when (error.code()) {
            400 -> texto("mensaje")?.takeIf(String::isNotBlank) ?: "Revisá el código o el formato del archivo."
            401 -> "Sesión vencida. Volvé a iniciar sesión."
            403 -> "No tenés permiso para esta operación."
            404 -> "La receta no está disponible."
            413 -> "El archivo supera el tamaño o la resolución admitidos."
            else -> "No se pudo completar la solicitud (${error.code()})."
        }
    }
}
