package ar.edu.uade.medilink.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.PuntoGeo
import ar.edu.uade.medilink.android.data.mensajeDeError
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
import org.json.JSONArray
import org.json.JSONObject

private fun DisponibilidadDto.punto(): PuntoGeo? = try {
    if (latitud == null || longitud == null) null else PuntoGeo(latitud, longitud)
} catch (_: IllegalArgumentException) { null }

/** LocationManager evita exigir Play Services. No pide localización en segundo plano. */
@Suppress("DEPRECATION")
private suspend fun obtenerUbicacion(context: Context): PuntoGeo = suspendCancellableCoroutine { cont ->
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
private suspend fun geocodificar(context: Context, texto: String): PuntoGeo = withContext(Dispatchers.IO) {
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

private fun mapaHtml(farmacias: List<DisponibilidadDto>, origen: PuntoGeo?,
    ruta: RutaDemo?): String {
    val marcadores = JSONArray()
    farmacias.forEach { farmacia ->
        val p = farmacia.punto() ?: return@forEach
        marcadores.put(JSONObject().put("lat", p.lat).put("lng", p.lng)
            .put("nombre", farmacia.nombre).put("stock", farmacia.cantidadDisponible))
    }
    val markersEncoded = android.util.Base64.encodeToString(
        marcadores.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    val rutaJson = JSONArray()
    ruta?.puntos?.forEach { rutaJson.put(JSONArray().put(it.lat).put(it.lng)) }
    val centro = origen ?: farmacias.firstNotNullOfOrNull { it.punto() } ?: PuntoGeo(-34.6118, -58.4173)
    val origenJs = if (origen == null) "null" else "[${origen.lat},${origen.lng}]"
    return """<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>html,body,#map{margin:0;width:100%;height:100%}</style></head><body><div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
const map=L.map('map').setView([${centro.lat},${centro.lng}],13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);
const bounds=[];
JSON.parse(new TextDecoder('utf-8').decode(Uint8Array.from(atob('$markersEncoded'),c=>c.charCodeAt(0)))).forEach(f=>{const p=[f.lat,f.lng]; bounds.push(p);
L.marker(p).addTo(map).bindPopup(document.createTextNode(f.nombre+' · '+f.stock+' unidades'));});
const origen=$origenJs;if(origen){bounds.push(origen);L.circleMarker(origen,{color:'blue'}).addTo(map).bindPopup('Tu origen');}
const ruta=${rutaJson};if(ruta.length){L.polyline(ruta,{color:'blue',weight:5}).addTo(map);bounds.push(...ruta);}
if(bounds.length>1) map.fitBounds(bounds,{padding:[24,24],maxZoom:15});
</script></body></html>"""
}

@Composable
fun MapasScreen(repository: MediLinkRepository, presentacionId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var farmacias by remember { mutableStateOf<List<DisponibilidadDto>>(emptyList()) }
    val origen by repository.origen.collectAsState()
    var direccion by remember { mutableStateOf("") }
    var elegido by remember { mutableStateOf<DisponibilidadDto?>(null) }
    var aPie by remember { mutableStateOf(false) }
    var ruta by remember { mutableStateOf<RutaDemo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    LaunchedEffect(presentacionId, origen, revision) {
        try {
            farmacias = repository.disponibilidad(presentacionId, origen?.lat, origen?.lng)
            error = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = mensajeDeError(e) }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) scope.launch {
            ocupado = true
            try { repository.origen.value = obtenerUbicacion(context); ruta = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = mensajeDeError(e) }
            finally { ocupado = false }
        } else error = "No diste permiso de ubicación. Podés buscar una dirección."
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Encabezado("Mapa de farmacias", "Disponibilidad, origen y ruta hasta la farmacia")
        Text("Vista de mapa basada en Leaflet. Las teselas y las rutas requieren Internet.")
        AvisoError(error)
        Button(onClick = {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    ocupado = true
                    try { repository.origen.value = obtenerUbicacion(context); ruta = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                    finally { ocupado = false }
                }
            } else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !ocupado) { Text("Usar mi ubicación (opcional)") }
        OutlinedTextField(direccion, { direccion = it }, label = { Text("Dirección en CABA") },
            modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !ocupado && direccion.trim().length >= 3, onClick = {
            scope.launch {
                ocupado = true
                try { repository.origen.value = geocodificar(context, direccion); ruta = null }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e) }
                finally { ocupado = false }
            }
        }) { Text("Buscar dirección") }
        origen?.let {
            Text("Origen aproximado: ${it.lat}, ${it.lng}")
            OutlinedButton(onClick = { repository.origen.value = null; ruta = null }) {
                Text("Olvidar mi origen")
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(290.dp),
            factory = { ctx -> WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = WebViewClient()
                settings.userAgentString = settings.userAgentString + " MediLink-Academic-Demo/0.2"
            } },
            update = { view ->
                view.loadDataWithBaseURL("https://www.openstreetmap.org/",
                    mapaHtml(farmacias, origen, ruta), "text/html", "UTF-8", null)
            },
        )
        Text("Farmacias con coordenadas: ${farmacias.count { it.punto() != null }}")
        farmacias.forEach { f ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${f.nombre} · ${f.cantidadDisponible} unidades")
                    Text(f.direccion)
                    f.distanciaKm?.let { Text("A ${"%.1f".format(Locale.forLanguageTag("es-AR"), it)} km") }
                    if (f.punto() != null) OutlinedButton(onClick = { elegido = f; ruta = null }) {
                        Text("Elegir destino")
                    }
                }
            }
        }
        elegido?.let { f ->
            Text("Destino: ${f.nombre}")
            ComboSimple("Modo de viaje", listOf("AUTO", "PIE"), if (aPie) "PIE" else "AUTO") {
                aPie = it == "PIE"; ruta = null
            }
            Button(enabled = !ocupado && origen != null, onClick = {
                val desde = origen ?: return@Button
                val destino = f.punto() ?: return@Button
                scope.launch {
                    ocupado = true
                    try { ruta = calcularRuta(desde, destino, aPie) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = mensajeDeError(e) }
                    finally { ocupado = false }
                }
            }) { Text("Calcular ruta") }
            OutlinedButton(onClick = {
                val punto = f.punto() ?: return@OutlinedButton
                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${punto.lat},${punto.lng}&travelmode=${if (aPie) "walking" else "driving"}")
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (_: Exception) { error = "No se encontró una app para abrir la ruta." }
            }) { Text("Abrir indicaciones externas") }
        }
        ruta?.let { r ->
            Text("Distancia: ${"%.1f".format(Locale.forLanguageTag("es-AR"), r.metros / 1000)} km")
            Text("Duración estimada: ${kotlin.math.round(r.segundos / 60).toInt()} min")
            r.pasos.forEachIndexed { i, paso -> Text("${i + 1}. $paso") }
        }
        Text("Demo local: antes de distribuir, configurar proveedores con cuotas propias y revisar privacidad de coordenadas.")
        OutlinedButton(onClick = { revision++ }) { Text("Actualizar disponibilidad") }
    }
}
