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

data class VincularRecetaRequest(val codigoReceta: String)

data class VinculacionRecetaDto(
    val nueva: Boolean,
    val prescripcion: PrescripcionDto,
    val codigo: String?,
)

internal class EnvioReceta(val enviado: () -> Unit)

internal interface RecetasApi {
    @POST("api/me/recetas-demo/vinculacion")
    suspend fun vincular(@Body body: VincularRecetaRequest): VinculacionRecetaDto

    @Multipart
    @POST("api/me/recetas-demo/escaneo")
    suspend fun escanear(@Part archivo: MultipartBody.Part, @Tag envio: EnvioReceta): VinculacionRecetaDto

    @GET("api/me/prescripciones")
    suspend fun listar(@Query("page") pagina: Int, @Query("size") tamanio: Int): PageDto<PrescripcionDto>

    @GET("api/me/prescripciones/{id}")
    suspend fun detalle(@Path("id") id: Long): PrescripcionDto
}
