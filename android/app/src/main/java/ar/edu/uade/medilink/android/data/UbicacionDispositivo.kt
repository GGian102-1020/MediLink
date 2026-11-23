package ar.edu.uade.medilink.android.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val PERMISOS_UBICACION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

internal data class UbicacionDispositivo(val punto: PuntoGeo, val precisionMetros: Int, val aproximada: Boolean) {
    val etiqueta: String get() = if (aproximada) "Tu ubicación aproximada (±$precisionMetros m)" else "Tu ubicación (±$precisionMetros m)"
}

/** Solicitud única con GPS/red en paralelo; no depende de Google Play Services. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "MissingPermission")
internal suspend fun obtenerUbicacion(context: Context): UbicacionDispositivo = withContext(Dispatchers.Main.immediate) {
    val precisa = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    require(precisa || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        "Falta el permiso de ubicación. Podés buscar por dirección."
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val proveedores = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { it in manager.allProviders && (precisa || it != LocationManager.GPS_PROVIDER) && manager.isProviderEnabled(it) }
    require(proveedores.isNotEmpty()) { "Activá la ubicación del dispositivo o buscá una dirección." }
    suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val recibidas = mutableListOf<Location>()
        lateinit var listener: LocationListener
        lateinit var vencimiento: Runnable
        fun limpiar() { handler.removeCallbacks(vencimiento); manager.removeUpdates(listener) }
        fun mejor(): MedicionUbicacion? = mejorUbicacion(recibidas.map {
            MedicionUbicacion(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else Float.NaN,
                (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000)
        })
        fun terminar(medicion: MedicionUbicacion?) {
            if (!cont.isActive) return
            limpiar()
            if (medicion == null) cont.resumeWithException(IllegalArgumentException(
                "No pudimos obtener una ubicación reciente. Probá al aire libre o buscá por dirección."))
            else cont.resume(UbicacionDispositivo(PuntoGeo(medicion.latitud, medicion.longitud).redondeado(),
                kotlin.math.ceil(medicion.precisionMetros.toDouble()).toInt(), !precisa || medicion.precisionMetros > 200))
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!cont.isActive) return
                recibidas.add(location)
                val mejor = mejor()
                // Con permiso preciso esperamos al GPS si la red solo entrega una posición amplia.
                if (mejor != null && (!precisa || mejor.precisionMetros <= 50)) terminar(mejor)
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        vencimiento = Runnable { terminar(mejor()) }
        cont.invokeOnCancellation { handler.post { limpiar() } }
        handler.postDelayed(vencimiento, 25_000)
        var iniciados = 0
        proveedores.forEach { proveedor ->
            try { manager.requestLocationUpdates(proveedor, 1_000L, 0f, listener, Looper.getMainLooper()); iniciados++ }
            catch (_: SecurityException) { /* Puede cambiar el permiso durante la solicitud. */ }
            catch (_: IllegalArgumentException) { /* Algunos equipos publican proveedores no disponibles. */ }
        }
        if (iniciados == 0) terminar(null)
    }
}
