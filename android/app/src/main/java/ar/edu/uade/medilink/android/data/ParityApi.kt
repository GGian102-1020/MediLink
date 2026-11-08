package ar.edu.uade.medilink.android.data

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

/** Endpoints correspondientes a pantallas React (User, Operator, Admin). */
interface ParityApi {
    @GET("api/me") suspend fun me(): MeDto
    @POST("api/auth/change-password") suspend fun cambiarClave(@Body body: CambiarClaveRequest): Response<Unit>

    @GET("api/reservas/mias") suspend fun reservasUsuario(
        @Query("estado") estado: String?, @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): PageDto<ReservaDto>
    @GET("api/reservas/mias/{codigo}") suspend fun detalleUsuario(@Path("codigo") codigo: String): ReservaDto

    @GET("api/farmacias/{id}/reservas") suspend fun reservasFarmacia(
        @Path("id") id: Long, @Query("estado") estado: String?,
        @Query("page") page: Int, @Query("size") size: Int = 20,
    ): PageDto<ReservaDto>
    @GET("api/farmacias/{id}/reservas/{codigo}") suspend fun detalleFarmacia(
        @Path("id") id: Long, @Path("codigo") codigo: String,
    ): ReservaDto
    @POST("api/farmacias/{id}/reservas/{codigo}/retiro") suspend fun retirar(
        @Path("id") id: Long, @Path("codigo") codigo: String,
    ): ReservaDto
    @POST("api/farmacias/{id}/reservas/{codigo}/cancelacion-operativa") suspend fun cancelarOperativa(
        @Path("id") id: Long, @Path("codigo") codigo: String,
        @Body body: CancelacionOperativaRequest,
    ): ReservaDto
    @GET("api/farmacias/{id}/stock") suspend fun stock(
        @Path("id") id: Long, @Query("page") page: Int, @Query("size") size: Int = 20,
    ): PageDto<StockDto>
    @PUT("api/farmacias/{id}/stock/{presentacionId}") suspend fun ajustarStock(
        @Path("id") id: Long, @Path("presentacionId") presentacionId: Long,
        @Body body: AjusteStockRequest,
    ): StockDto
    @GET("api/farmacias/{id}/stock/movimientos") suspend fun movimientos(
        @Path("id") id: Long, @Query("presentacionId") presentacionId: Long?,
        @Query("page") page: Int, @Query("size") size: Int = 20,
    ): PageDto<MovimientoStockDto>

    // @Url solo se invoca con las rutas internas fijas declaradas en AdminSection.
    @GET suspend fun adminList(
        @Url path: String, @QueryMap filters: Map<String, String>,
    ): AdminPage
    @POST suspend fun adminCreate(@Url path: String, @Body body: Any): JsonObject
    @PUT suspend fun adminUpdate(@Url path: String, @Body body: Any): JsonObject
    @POST suspend fun adminChangeState(@Url path: String, @Body body: CambioEstadoRequest): JsonObject
    @GET suspend fun adminDetail(@Url path: String): JsonObject
    @GET suspend fun adminReservations(@Url path: String,
        @QueryMap filters: Map<String, String>): PageDto<ReservaDto>
    @GET suspend fun adminSuspensions(@Url path: String,
        @QueryMap filters: Map<String, String>): PageDto<SuspensionDto>
}
