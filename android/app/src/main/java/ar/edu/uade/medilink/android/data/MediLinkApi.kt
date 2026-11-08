package ar.edu.uade.medilink.android.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MediLinkApi {
    @GET("api/medicamentos")
    suspend fun medicamentos(
        @Query("nombre") nombre: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<MedicamentoDto>

    @GET("api/medicamentos/{id}/presentaciones")
    suspend fun presentaciones(
        @Path("id") id: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<PresentacionDto>

    @GET("api/presentaciones/{id}")
    suspend fun presentacion(@Path("id") id: Long): PresentacionDto

    @GET("api/presentaciones/{id}/disponibilidad")
    suspend fun disponibilidad(
        @Path("id") id: Long,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): List<DisponibilidadDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginDto

    @POST("api/auth/register")
    suspend fun registro(@Body request: RegisterRequest): RegistroDto

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/reservas")
    suspend fun crearReserva(
        @Header("Idempotency-Key") key: String,
        @Body request: CrearReservaRequest,
    ): ReservaDto

    @GET("api/reservas/mias")
    suspend fun misReservas(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): PageDto<ReservaDto>

    @POST("api/reservas/{codigo}/cancelacion")
    suspend fun cancelar(@Path("codigo") codigo: String): ReservaDto
}
