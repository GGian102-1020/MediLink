package ar.edu.uade.medilink.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PuntoGeo
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.ui.theme.MediColors
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun DisponibilidadDto.punto(): PuntoGeo? = try {
    if (latitud == null || longitud == null) null else PuntoGeo(latitud, longitud)
} catch (_: IllegalArgumentException) { null }

/** LocationManager evita exigir Play Services. No pide localización en segundo plano. */
@Suppress("DEPRECATION")
internal suspend fun obtenerUbicacion(context: Context): PuntoGeo = suspendCancellableCoroutine { cont ->
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        cont.resumeWith(Result.failure(IllegalArgumentException("Falta el permiso de ubicación.")))
        return@suspendCancellableCoroutine
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { locationManager.isProviderEnabled(it) }
            ?: throw IllegalArgumentException("Activá la ubicación del dispositivo o buscá una dirección.")
        if (Build.VERSION.SDK_INT >= 30) {
            val executor = Executors.newSingleThreadExecutor()
            val cancellationSignal = android.os.CancellationSignal()
            cont.invokeOnCancellation { cancellationSignal.cancel(); executor.shutdown() }
            locationManager.getCurrentLocation(provider, cancellationSignal, executor) { location: Location? ->
                executor.shutdown()
                if (cont.isActive) {
                    if (location == null) cont.resumeWith(Result.failure(IllegalArgumentException("No se obtuvo ubicación.")))
                    else cont.resume(PuntoGeo(location.latitude, location.longitude).redondeado())
                }
            }
        } else {
            val location = locationManager.getLastKnownLocation(provider)
                ?: throw IllegalArgumentException("No hay ubicación reciente. Buscá una dirección.")
            cont.resume(PuntoGeo(location.latitude, location.longitude).redondeado())
        }
    } catch (e: SecurityException) {
        if (cont.isActive) cont.resumeWith(Result.failure(e))
    } catch (e: Exception) {
        if (cont.isActive) cont.resumeWith(Result.failure(e))
    }
}

@Suppress("DEPRECATION")
internal suspend fun geocodificar(context: Context, texto: String): PuntoGeo = withContext(Dispatchers.IO) {
    require(texto.trim().length >= 3) { "Ingresá una dirección completa." }
    // Geocoder del sistema; sin consultas directas al Nominatim público.
    val resultado = Geocoder(context, Locale.forLanguageTag("es-AR"))
        .getFromLocationName("$texto, Ciudad Autónoma de Buenos Aires, Argentina", 3)
        ?.firstOrNull() ?: throw IllegalArgumentException("No se encontró la dirección. Probá con otra.")
    PuntoGeo(resultado.latitude, resultado.longitude).redondeado()
}

private fun textoPaso(tipo: String, modificador: String, calle: String?): String {
    val via = calle?.let { " por $it" }.orEmpty()
    val giro = when (modificador) {
        "left" -> "a la izquierda"; "right" -> "a la derecha"
        "slight left" -> "levemente a la izquierda"; "slight right" -> "levemente a la derecha"
        "sharp left" -> "bien a la izquierda"; "sharp right" -> "bien a la derecha"
        "uturn" -> "en U"; else -> "derecho"
    }
    return when (tipo) {
        "depart" -> "Comenzá el recorrido$via"
        "arrive" -> "Llegaste al destino"
        "turn", "end of road", "on ramp", "off ramp" -> "Girá $giro$via"
        "roundabout", "rotary", "roundabout turn" -> "Atravesá la rotonda$via"
        "fork", "merge" -> "Continuá $giro$via"
        else -> "Seguí$via"
    }
}

private data class RutaDemo(val puntos: List<PuntoGeo>, val metros: Double, val segundos: Double,
    val pasos: List<String>)

private suspend fun calcularRuta(origen: PuntoGeo, destino: PuntoGeo, aPie: Boolean): RutaDemo =
    withContext(Dispatchers.IO) {
        val base = if (aPie) "https://routing.openstreetmap.de/routed-foot/route/v1/driving"
            else "https://router.project-osrm.org/route/v1/driving"
        val url = "$base/${origen.lng},${origen.lat};${destino.lng},${destino.lat}" +
            "?overview=full&geometries=geojson&steps=true"
        val request = Request.Builder().url(url)
            .header("User-Agent", "MediLink-Android-Academic-Demo/0.2")
            .build()
        val client = OkHttpClient()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalArgumentException("No respondió el servicio de rutas.")
            val root = JSONObject(response.body?.string() ?: "")
            if (root.optString("code") != "Ok" || root.getJSONArray("routes").length() == 0)
                throw IllegalArgumentException("No se encontró un recorrido.")
            val ruta = root.getJSONArray("routes").getJSONObject(0)
            val coords = ruta.getJSONObject("geometry").getJSONArray("coordinates")
            val puntos = (0 until coords.length()).map { i ->
                val xy = coords.getJSONArray(i)
                PuntoGeo(xy.getDouble(1), xy.getDouble(0))
            }
            val pasos = mutableListOf<String>()
            val legs = ruta.getJSONArray("legs")
            for (i in 0 until legs.length()) {
                val steps = legs.getJSONObject(i).getJSONArray("steps")
                for (j in 0 until steps.length()) {
                    val paso = steps.getJSONObject(j)
                    val maniobra = paso.getJSONObject("maneuver")
                    val calle = paso.optString("name").takeIf { it.isNotBlank() }
                    pasos += textoPaso(maniobra.optString("type"), maniobra.optString("modifier"), calle)
                }
            }
            RutaDemo(puntos, ruta.getDouble("distance"), ruta.getDouble("duration"), pasos)
        }
    }


/** Centro de CABA, usado si no hay origen ni farmacias con coordenadas. */
private val CENTRO_CABA = GeoPoint(-34.6037, -58.3816)
/** Con 500+ farmacias el mapa se vuelve ilegible: se dibujan las más cercanas. */
private const val MAX_MARCADORES = 80

private fun PuntoGeo.geo() = GeoPoint(lat, lng)

private fun distanciaLegible(km: Double) =
    if (km < 1) "a ${(km * 1000).toInt()} m" else "a ${"%.1f".format(Locale.forLanguageTag("es-AR"), km)} km"

/** Pin redondo de la marca dibujado a mano: círculo de color con borde blanco y cruz. */
private fun iconoFarmacia(context: Context, relleno: Int, tamanoDp: Int): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val lado = (tamanoDp * d).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = lado / 2f
    p.color = android.graphics.Color.argb(60, 0, 0, 0)
    c.drawCircle(r, r + d, r - d, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r - 2 * d, p)
    p.color = relleno
    c.drawCircle(r, r, r - 4.5f * d, p)
    p.color = android.graphics.Color.WHITE
    val brazo = r * 0.42f
    val grosor = r * 0.16f
    c.drawRoundRect(r - grosor, r - brazo, r + grosor, r + brazo, grosor / 2, grosor / 2, p)
    c.drawRoundRect(r - brazo, r - grosor, r + brazo, r + grosor, grosor / 2, grosor / 2, p)
    return BitmapDrawable(context.resources, bmp)
}

/** Punto azul con halo para el origen (ubicación del usuario o dirección buscada). */
private fun iconoOrigen(context: Context): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val lado = (34 * d).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = lado / 2f
    p.color = android.graphics.Color.argb(70, 6, 117, 221)
    c.drawCircle(r, r, r, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r * 0.48f, p)
    p.color = MediColors.Blue.toArgb()
    c.drawCircle(r, r, r * 0.34f, p)
    return BitmapDrawable(context.resources, bmp)
}

/**
 * Mapa nativo (osmdroid + OpenStreetMap) con las farmacias que tienen stock.
 * Tocar un pin muestra la farmacia; desde ahí se ve la ruta o se reserva.
 */
@Composable
fun MapasScreen(
    repository: MediLinkRepository,
    presentacionId: Long,
    farmaciaInicial: Long?,
    logueado: Boolean,
    pedirSesion: (String) -> Unit,
    onReservar: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val origen by repository.origen.collectAsState()
    val origenEtiqueta by repository.origenEtiqueta.collectAsState()
    var farmacias by remember { mutableStateOf<List<DisponibilidadDto>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var seleccionId by rememberSaveable { mutableStateOf(farmaciaInicial ?: -1L) }
    var aPie by rememberSaveable { mutableStateOf(true) }
    var ruta by remember { mutableStateOf<RutaDemo?>(null) }
    var calculando by remember { mutableStateOf(false) }
    var ubicando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var camaraInicial by rememberSaveable { mutableStateOf(false) }
    val seleccion = farmacias.find { it.farmaciaId == seleccionId }

    LaunchedEffect(presentacionId, origen) {
        cargando = true
        try {
            farmacias = repository.disponibilidad(presentacionId, origen?.lat, origen?.lng)
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
        finally { cargando = false }
    }

    fun usarMiUbicacion() {
        scope.launch {
            ubicando = true
            try {
                repository.origen.value = obtenerUbicacion(context)
                repository.origenEtiqueta.value = "Tu ubicación"
                ruta = null; error = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = mensajeDeError(e) }
            finally { ubicando = false }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) usarMiUbicacion() else error = "Sin permiso de ubicación no podemos mostrarte la ruta."
    }
    val pedirUbicacion = {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            usarMiUbicacion()
        else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val calcular: (DisponibilidadDto, Boolean) -> Unit = { f, pie ->
        val desde = repository.origen.value
        val hasta = f.punto()
        if (desde != null && hasta != null) scope.launch {
            calculando = true
            try { ruta = calcularRuta(desde, hasta, pie); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = mensajeDeError(e) }
            finally { calculando = false }
        }
    }

    // --- Mapa osmdroid ---
    val mapa = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName // política de uso de teselas de OSM
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 11.0
            maxZoomLevel = 19.0
            isTilesScaledToDpi = true
            controller.setZoom(13.0)
            controller.setCenter(CENTRO_CABA)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observador)
        mapa.onResume()
        onDispose { lifecycle.removeObserver(observador); mapa.onPause(); mapa.onDetach() }
    }
    val iconoNormal = remember { iconoFarmacia(context, MediColors.Navy.toArgb(), 30) }
    val iconoElegido = remember { iconoFarmacia(context, MediColors.Primario.toArgb(), 44) }
    val iconoYo = remember { iconoOrigen(context) }

    // Redibuja pines, origen y ruta cuando cambia algo.
    LaunchedEffect(farmacias, seleccionId, origen, ruta) {
        mapa.overlays.clear()
        mapa.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { seleccionId = -1L; ruta = null; return true }
            override fun longPressHelper(p: GeoPoint?) = false
        }))
        ruta?.let { r ->
            mapa.overlays.add(Polyline(mapa).apply {
                setPoints(r.puntos.map { it.geo() })
                outlinePaint.color = MediColors.Primario.toArgb()
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            })
        }
        val visibles = farmacias.filter { it.punto() != null }.take(MAX_MARCADORES).toMutableList()
        seleccion?.takeIf { s -> visibles.none { it.farmaciaId == s.farmaciaId } }?.let { visibles += it }
        // El elegido se agrega último para que quede encima.
        visibles.sortedBy { it.farmaciaId == seleccionId }.forEach { f ->
            val p = f.punto() ?: return@forEach
            mapa.overlays.add(Marker(mapa).apply {
                position = p.geo()
                icon = if (f.farmaciaId == seleccionId) iconoElegido else iconoNormal
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                setOnMarkerClickListener { _, _ ->
                    if (seleccionId != f.farmaciaId) { seleccionId = f.farmaciaId; ruta = null }
                    true
                }
            })
        }
        origen?.let { o ->
            mapa.overlays.add(Marker(mapa).apply {
                position = o.geo(); icon = iconoYo; infoWindow = null
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        mapa.invalidate()
    }
    // Cámara inicial: la farmacia pedida, o el origen, o la primera farmacia.
    LaunchedEffect(farmacias) {
        if (camaraInicial || farmacias.isEmpty()) return@LaunchedEffect
        val foco = seleccion?.punto()?.geo() ?: origen?.geo() ?: farmacias.firstNotNullOfOrNull { it.punto() }?.geo()
        if (foco != null) { mapa.controller.setZoom(if (seleccion != null) 16.5 else 15.0); mapa.controller.setCenter(foco) }
        camaraInicial = true
    }
    // Al elegir otra farmacia, la cámara va hacia ella.
    LaunchedEffect(seleccionId) {
        if (camaraInicial) seleccion?.punto()?.let { mapa.controller.animateTo(it.geo(), 16.5, 450L) }
    }
    // Con ruta, se encuadra el recorrido completo.
    LaunchedEffect(ruta) {
        val r = ruta ?: return@LaunchedEffect
        if (r.puntos.size > 1) mapa.post {
            // La tarjeta tapa ~la mitad inferior del mapa y el encabezado la franja superior:
            // se agranda la caja hacia abajo para que el recorrido quede en la zona visible.
            val caja = BoundingBox.fromGeoPoints(r.puntos.map { it.geo() })
            val alto = kotlin.math.max(caja.latitudeSpan, 0.002)
            val ancho = kotlin.math.max(caja.longitudeSpanWithDateLine, 0.002)
            mapa.zoomToBoundingBox(BoundingBox(caja.latNorth + alto * 0.35, caja.lonEast + ancho * 0.15,
                caja.latSouth - alto * 1.25, caja.lonWest - ancho * 0.15), true, 40)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize())

        // Encabezado flotante: cuántas farmacias y desde dónde.
        Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp).padding(end = 64.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    if (cargando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(if (cargando) "Buscando farmacias…" else plural(farmacias.size, "farmacia con stock", "farmacias con stock"),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (origen != null) "Cerca de: ${origenEtiqueta ?: "tu ubicación"}"
                        else if (farmacias.size > MAX_MARCADORES) "Activá tu ubicación para ver las cercanas"
                        else "Tocá un pin para ver la farmacia",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Botón "mi ubicación".
        SmallFloatingActionButton(onClick = {
            val o = origen
            if (o != null && !ubicando) mapa.controller.animateTo(o.geo(), 15.5, 450L) else pedirUbicacion()
        }, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(48.dp)) {
            if (ubicando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación")
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 4.dp) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { error = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar aviso",
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            AnimatedVisibility(visible = seleccion == null && !cargando && farmacias.isNotEmpty(),
                enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Tocá una farmacia en el mapa para ver dónde queda y cómo llegar.",
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            AnimatedVisibility(visible = seleccion != null,
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                seleccion?.let { f ->
                    TarjetaFarmaciaMapa(f, ruta = ruta, aPie = aPie, calculando = calculando, hayOrigen = origen != null,
                        onCerrar = { seleccionId = -1L; ruta = null },
                        onModo = { pie -> aPie = pie; if (ruta != null) calcular(f, pie) },
                        onComoLlegar = { if (origen == null) pedirUbicacion() else calcular(f, aPie) },
                        onAbrirExterno = {
                            f.punto()?.let { p ->
                                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}" +
                                    "&travelmode=${if (aPie) "walking" else "driving"}")
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                catch (_: Exception) { error = "No se encontró una app para abrir la ruta." }
                            }
                        },
                        onReservar = {
                            if (logueado) onReservar(f.farmaciaId)
                            else pedirSesion("Para reservar en ${f.nombre} necesitás iniciar sesión con tu cuenta de paciente.")
                        })
                }
            }
        }
    }
}

@Composable
private fun TarjetaFarmaciaMapa(
    f: DisponibilidadDto,
    ruta: RutaDemo?,
    aPie: Boolean,
    calculando: Boolean,
    hayOrigen: Boolean,
    onCerrar: () -> Unit,
    onModo: (Boolean) -> Unit,
    onComoLlegar: () -> Unit,
    onAbrirExterno: () -> Unit,
    onReservar: () -> Unit,
) {
    val forma = RoundedCornerShape(topStart = 24.dp, topEnd = 40.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    Surface(shape = forma, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(f.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(f.direccion, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCerrar, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EstadoPildora(plural(f.cantidadDisponible, "unidad", "unidades"),
                    if (f.cantidadDisponible <= 3) TonoPildora.ALERTA else TonoPildora.EXITO)
                f.distanciaKm?.let {
                    Text(distanciaLegible(it), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (ruta != null) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (aPie) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.DirectionsCar,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("${kotlin.math.max(1, kotlin.math.round(ruta.segundos / 60).toInt())} min",
                                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("${"%.1f".format(Locale.forLanguageTag("es-AR"), ruta.metros / 1000)} km " +
                                if (aPie) "caminando" else "en auto",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onAbrirExterno) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Navegar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (hayOrigen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModoChip("A pie", Icons.AutoMirrored.Filled.DirectionsWalk, aPie) { onModo(true) }
                    ModoChip("En auto", Icons.Default.DirectionsCar, !aPie) { onModo(false) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onComoLlegar, enabled = !calculando, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)) {
                    if (calculando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(if (ruta != null) "Recalcular" else "Cómo llegar", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(onClick = onReservar, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Reservar acá", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ModoChip(texto: String, icono: ImageVector, elegido: Boolean, onClick: () -> Unit) {
    FilterChip(selected = elegido, onClick = onClick, label = { Text(texto) },
        leadingIcon = { Icon(icono, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MediColors.PrimarioSuave,
            selectedLabelColor = MediColors.Ink, selectedLeadingIconColor = MediColors.Primario))
}
