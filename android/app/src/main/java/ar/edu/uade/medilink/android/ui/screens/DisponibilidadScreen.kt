package ar.edu.uade.medilink.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.data.obtenerUbicacion
import ar.edu.uade.medilink.android.data.PERMISOS_UBICACION
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.mensajeDeError
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Cuántas farmacias se muestran de entrada y cuántas más agrega "Ver más". */
/** Solo las 3 más cercanas: el usuario filtra con el buscador o ve el resto en el mapa. */
private const val MAX_FARMACIAS = 3

/** Minúsculas y sin tildes, para que "cordoba" encuentre "Av. Córdoba". */
private fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** Todas las palabras escritas tienen que aparecer en el nombre o en la dirección. */
private fun DisponibilidadDto.coincideCon(busqueda: String): Boolean {
    val palabras = normalizar(busqueda).split(Regex("[\\s,.]+")).filter { it.isNotBlank() }
    if (palabras.isEmpty()) return true
    val texto = normalizar("$nombre $direccion")
    return palabras.all { texto.contains(it) }
}

@Composable
fun DisponibilidadScreen(
    vm: DisponibilidadVm,
    repository: MediLinkRepository,
    logueado: Boolean,
    onLogin: () -> Unit,
    pedirSesion: (String) -> Unit,
    onReservaCreada: (String) -> Unit,
    onMapa: () -> Unit,
    onVerEnMapa: (Long) -> Unit,
    farmaciaDesdeMapa: Long? = null,
    onFarmaciaDesdeMapaUsada: () -> Unit = {},
    /** BAJO_RECETA: receta adjuntada en el paso anterior (código + cantidad autorizada). */
    receta: ar.edu.uade.medilink.android.ui.viewmodel.RecetaLista? = null,
    /** BAJO_RECETA sin receta todavía: vuelve al paso "Adjuntá tu receta". */
    onAdjuntarReceta: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val foco = LocalFocusManager.current
    val origen by repository.origen.collectAsState()
    val origenEtiqueta by repository.origenEtiqueta.collectAsState()
    var farmaciaElegida by rememberSaveable { mutableStateOf(-1L) }
    var cantidad by rememberSaveable { mutableStateOf("1") }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var ubicando by remember { mutableStateOf(false) }
    var avisoUbicacion by remember { mutableStateOf<String?>(null) }
    val presentacion = state.presentacion
    val seleccion = state.farmacias.find { it.farmaciaId == farmaciaElegida }
    val bajoReceta = presentacion?.condicionDispensa == "BAJO_RECETA"
    val numero = cantidad.toIntOrNull()
    val esCantidadValida = numero != null && numero > 0 &&
        seleccion != null && numero <= seleccion.cantidadDisponible
    val scroll = rememberScrollState()
    var posicionConfirmar by remember { mutableFloatStateOf(0f) }

    val filtradas = remember(state.farmacias, busqueda) { state.farmacias.filter { it.coincideCon(busqueda) } }
    val mostradas = filtradas.take(MAX_FARMACIAS)
    // Al cambiar la búsqueda o el origen se vuelve a mostrar solo la primera tanda.

    fun usarMiUbicacion() {
        if (ubicando) return
        scope.launch {
            ubicando = true; avisoUbicacion = null
            try {
                val ubicacion = obtenerUbicacion(context)
                repository.origen.value = ubicacion.punto
                repository.origenEtiqueta.value = ubicacion.etiqueta
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { avisoUbicacion = mensajeDeError(e) }
            finally { ubicando = false }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
        if (concedidos.values.any { it }) usarMiUbicacion()
        else avisoUbicacion = "Sin permiso de ubicación. Podés buscar por dirección o barrio."
    }
    val tienePermiso = {
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val pedirUbicacion = {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) usarMiUbicacion() else permiso.launch(PERMISOS_UBICACION)
    }
    // Primera vez: se intenta ubicar al usuario para mostrarle primero lo más cercano.
    LaunchedEffect(Unit) {
        if (repository.origen.value == null) {
            if (tienePermiso()) usarMiUbicacion()
            else if (!repository.permisoUbicacionPedido) {
                repository.permisoUbicacionPedido = true
                permiso.launch(PERMISOS_UBICACION)
            }
        }
    }
    // Sin resultados por nombre/dirección: se puede buscar esa dirección y ordenar por cercanía.
    val buscarCercaDe: (String) -> Unit = { texto ->
        scope.launch {
            ubicando = true; avisoUbicacion = null
            try {
                repository.origen.value = geocodificar(context, texto)
                repository.origenEtiqueta.value = texto.trim()
                busqueda = ""
                foco.clearFocus()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { avisoUbicacion = mensajeDeError(e) }
            finally { ubicando = false }
        }
    }

    // Al elegir farmacia bajamos hasta el bloque de confirmación (antes quedaba fuera de pantalla).
    LaunchedEffect(farmaciaElegida, posicionConfirmar) {
        if (seleccion != null && posicionConfirmar > 0f) scroll.animateScrollTo(posicionConfirmar.toInt())
    }
    // "Reservar acá" desde el mapa: vuelve con la farmacia ya elegida.
    LaunchedEffect(farmaciaDesdeMapa, state.farmacias) {
        val id = farmaciaDesdeMapa ?: return@LaunchedEffect
        if (state.farmacias.any { it.farmaciaId == id }) { farmaciaElegida = id; onFarmaciaDesdeMapaUsada() }
    }
    // Reserva creada: se abre su detalle, donde el paciente ve el código y el PIN de retiro.
    LaunchedEffect(state.confirmacion) {
        state.confirmacion?.let { onReservaCreada(it.codigoVisible) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val que = presentacion?.let { "${it.medicamentoNombre} ${it.concentracionTexto}" } ?: "este medicamento"
        Encabezado("Elegí una farmacia", if (logueado) "Dónde retirar $que."
            else "Farmacias con stock de $que. Iniciá sesión para reservar.")
        CampoTexto(value = busqueda, onValueChange = { busqueda = it.take(80) },
            placeholder = { Text("Buscá por calle, barrio o farmacia") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (busqueda.isNotEmpty()) { {
                IconButton(onClick = { busqueda = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                }
            } } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { foco.clearFocus() }),
            singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = pedirUbicacion, enabled = !ubicando, modifier = Modifier.weight(1f)) {
                if (ubicando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" Mi ubicación", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onMapa, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" Ver mapa")
            }
        }
        avisoUbicacion?.let { NotaInformativa(it) }
        if (seleccion == null) AvisoError(state.error)
        if (state.cargando && state.farmacias.isEmpty()) IndicadorCargando()

        val titulo = if (busqueda.isNotBlank()) "Resultados de la búsqueda" else "Farmacias con stock"
        val detalle = when {
            filtradas.isEmpty() -> "Sin coincidencias"
            busqueda.isNotBlank() -> "Las ${mostradas.size} que mejor coinciden"
            origen != null && origenEtiqueta != null && origenEtiqueta != "Tu ubicación" ->
                "Las ${mostradas.size} más cercanas a $origenEtiqueta"
            origen != null -> "Las ${mostradas.size} más cercanas a vos"
            else -> "Activá tu ubicación para ver las más cercanas"
        }
        SeccionTitulo(titulo, detalle)

        if (state.farmacias.isEmpty() && !state.cargando && state.error == null) {
            VacioEstado("Sin stock disponible", "Ninguna farmacia tiene stock de esta presentación por ahora.")
        } else if (filtradas.isEmpty() && busqueda.isNotBlank()) {
            VacioEstado("No encontramos \"${busqueda.trim()}\"",
                "Ninguna farmacia con stock coincide por nombre o dirección.")
            if (busqueda.trim().length >= 3) {
                OutlinedButton(onClick = { buscarCercaDe(busqueda) }, enabled = !ubicando,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text(" Ver las más cercanas a \"${busqueda.trim()}\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        mostradas.forEach { farmacia ->
            FarmaciaCard(farmacia = farmacia, seleccionada = farmacia.farmaciaId == farmaciaElegida,
                onVerEnMapa = { onVerEnMapa(farmacia.farmaciaId) },
                onSeleccionar = {
                    // Invitado: no puede iniciar una reserva; se le pide iniciar sesión.
                    if (logueado) farmaciaElegida = farmacia.farmaciaId
                    else pedirSesion("Para reservar ${presentacion?.medicamentoNombre ?: "este medicamento"} " +
                        "en ${farmacia.nombre} necesitás iniciar sesión con tu cuenta de paciente.")
                })
        }
        if (seleccion != null) {
            Column(Modifier.onGloballyPositioned { posicionConfirmar = it.positionInParent().y },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SeccionTitulo("Confirmar reserva", "${seleccion.nombre} · ${plural(seleccion.cantidadDisponible, "unidad disponible", "unidades disponibles")}")
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!logueado) {
                            NotaInformativa("Iniciá sesión con una cuenta de paciente para confirmar la reserva.")
                            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                        } else if (bajoReceta && receta == null) {
                            NotaInformativa("Este medicamento se vende bajo receta. Adjuntala para poder reservar.")
                            Button(onClick = onAdjuntarReceta, modifier = Modifier.fillMaxWidth()) { Text("Adjuntar receta") }
                        } else if (bajoReceta && receta != null) {
                            RecetaListaFila(receta.cantidad)
                            val alcanza = receta.cantidad <= seleccion.cantidadDisponible
                            if (!alcanza) AvisoError("Esta farmacia tiene ${plural(seleccion.cantidadDisponible, "unidad", "unidades")} " +
                                "y tu receta indica ${receta.cantidad}. Elegí otra farmacia.")
                            AvisoError(state.error)
                            Button(onClick = {
                                vm.reservar(farmaciaId = seleccion.farmaciaId, cantidad = receta.cantidad,
                                    codigoPrescripcion = receta.codigo)
                            }, enabled = !state.reservando && alcanza && state.confirmacion == null,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.reservando) "Reservando..." else "Confirmar reserva")
                            }
                            Text("La farmacia revisa tu receta. Cuando la confirme, te mostramos el PIN y tenés 4 horas para retirarlo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            CampoTexto(value = cantidad,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) cantidad = it },
                                label = { Text("Cantidad (máximo ${seleccion.cantidadDisponible})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = cantidad.isNotEmpty() && !esCantidadValida,
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            AvisoError(state.error)
                            Button(onClick = {
                                vm.reservar(farmaciaId = seleccion.farmaciaId,
                                    cantidad = requireNotNull(numero),
                                    codigoPrescripcion = null)
                            }, enabled = !state.reservando && esCantidadValida && state.confirmacion == null,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.reservando) "Reservando..." else "Confirmar reserva")
                            }
                            Text("Vas a tener 4 horas para retirarlo. Al confirmar te mostramos el código y el PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Indica que la receta del paso anterior ya está adjunta y cuántas unidades autoriza. */
@Composable
private fun RecetaListaFila(cantidad: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text("Receta verificada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Cantidad: ${plural(cantidad, "unidad", "unidades")} (según tu receta)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FarmaciaCard(farmacia: DisponibilidadDto, seleccionada: Boolean, onVerEnMapa: () -> Unit,
    onSeleccionar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (seleccionada)
            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.LocalPharmacy, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(farmacia.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(farmacia.direccion, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EstadoPildora(plural(farmacia.cantidadDisponible, "unidad", "unidades"),
                    if (farmacia.cantidadDisponible <= 3) TonoPildora.ALERTA else TonoPildora.EXITO)
                farmacia.distanciaKm?.let {
                    Text(if (it < 1) "a ${(it * 1000).toInt()} m" else "a %.1f km".format(it),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSeleccionar, modifier = Modifier.weight(1f)) {
                    Text(if (seleccionada) "Elegida" else "Elegir")
                }
                if (farmacia.latitud != null && farmacia.longitud != null) {
                    OutlinedButton(onClick = onVerEnMapa, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(" Ver en mapa")
                    }
                }
            }
        }
    }
}
