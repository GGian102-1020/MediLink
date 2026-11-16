package ar.edu.uade.medilink.android.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints de cuenta y reservas del paciente. */
interface ParityApi {
    @GET("api/me") suspend fun me(): MeDto
    @POST("api/auth/change-password") suspend fun cambiarClave(@Body body: CambiarClaveRequest): Response<Unit>

    @GET("api/reservas/mias") suspend fun reservasUsuario(
        @Query("estado") estado: String?, @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): PageDto<ReservaDto>
    @GET("api/reservas/mias/{codigo}") suspend fun detalleUsuario(@Path("codigo") codigo: String): ReservaDto
}
