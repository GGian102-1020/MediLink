package ar.edu.uade.medilink.android.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

data class PrescripcionDto(
    val id: Long,
    val codigoExterno: String,
    val usuarioId: Long,
    val fechaEmision: String,
    val fechaVencimiento: String,
    val estado: String,
    val items: List<ItemPrescripcionDto>,
)

data class ItemPrescripcionDto(
    val id: Long,
    val presentacionId: Long,
    val descripcionPresentacion: String,
    val cantidadAutorizada: Int,
    val cantidadComprometida: Int,
    val cantidadUtilizada: Int,
    val cantidadDisponible: Int,
)

data class VincularRecetaRequest(val codigoReceta: String, val presentacionId: Long? = null, val cantidad: Int? = null)

data class VinculacionRecetaDto(
    val nueva: Boolean,
    val prescripcion: PrescripcionDto,
    val codigo: String?,
)

internal class EnvioReceta(val enviado: () -> Unit)

internal interface RecetasApi {
    @GET("api/me/recetas-demo/configuracion")
    suspend fun configuracion(): ConfiguracionRecetasDto
    @POST("api/me/recetas-demo/vinculacion")
    suspend fun vincular(@Body body: VincularRecetaRequest): VinculacionRecetaDto

    @Multipart
    @POST("api/me/recetas-demo/escaneo")
    suspend fun escanear(@Part archivo: MultipartBody.Part, @Tag envio: EnvioReceta,
        @Part("presentacionId") presentacionId: okhttp3.RequestBody? = null,
        @Part("cantidad") cantidad: okhttp3.RequestBody? = null): VinculacionRecetaDto

    /** Receta (PDF/imagen) adjunta para reservar un medicamento BAJO_RECETA. */
    @Multipart
    @POST("api/me/recetas-adjuntas")
    suspend fun adjuntar(
        @Part archivo: MultipartBody.Part,
        @Part("presentacionId") presentacionId: okhttp3.RequestBody,
        @Part("cantidad") cantidad: okhttp3.RequestBody,
    ): RecetaAdjuntaDto

    @GET("api/me/prescripciones")
    suspend fun listar(@Query("page") pagina: Int, @Query("size") tamanio: Int): PageDto<PrescripcionDto>

    @GET("api/me/prescripciones/{id}")
    suspend fun detalle(@Path("id") id: Long): PrescripcionDto
}

data class ConfiguracionRecetasDto(val escaneoHabilitado: Boolean)

internal data class RecetaAdjuntaDto(
    val codigoPrescripcion: String,
    val prescripcionId: Long,
    val nombreArchivo: String,
    val contentType: String,
    val tamanoBytes: Long,
    val fechaVencimiento: String,
)
