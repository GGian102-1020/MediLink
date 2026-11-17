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
private const val TANDA_FARMACIAS = 5

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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val foco = LocalFocusManager.current
    val origen by repository.origen.collectAsState()
    val origenEtiqueta by repository.origenEtiqueta.collectAsState()
    var farmaciaElegida by rememberSaveable { mutableStateOf(-1L) }
    var cantidad by rememberSaveable { mutableStateOf("1") }
    var prescripcion by rememberSaveable { mutableStateOf("") }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var visibles by rememberSaveable { mutableIntStateOf(TANDA_FARMACIAS) }
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
    val mostradas = filtradas.take(visibles)
    // Al cambiar la búsqueda o el origen se vuelve a mostrar solo la primera tanda.
    LaunchedEffect(busqueda, origen) { visibles = TANDA_FARMACIAS }

    fun usarMiUbicacion() {
        scope.launch {
            ubicando = true; avisoUbicacion = null
            try {
                repository.origen.value = obtenerUbicacion(context)
                repository.origenEtiqueta.value = "Tu ubicación"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { avisoUbicacion = mensajeDeError(e) }
            finally { ubicando = false }
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) usarMiUbicacion()
        else avisoUbicacion = "Sin permiso de ubicación. Podés buscar por dirección o barrio."
    }
    val tienePermiso = {
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val pedirUbicacion = {
        if (tienePermiso()) usarMiUbicacion() else permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    // Primera vez: se intenta ubicar al usuario para mostrarle primero lo más cercano.
    LaunchedEffect(Unit) {
        if (repository.origen.value == null) {
            if (tienePermiso()) usarMiUbicacion()
            else if (!repository.permisoUbicacionPedido) {
                repository.permisoUbicacionPedido = true
                permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
        Encabezado("Disponibilidad", if (logueado) "Elegí una farmacia con stock para reservar."
            else "Estas farmacias tienen stock. Iniciá sesión para reservar.")
        if (presentacion != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(46.dp).background(MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Medication, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(presentacion.medicamentoNombre, style = MaterialTheme.typography.titleLarge)
                        Text("${presentacion.concentracionTexto} · ${presentacion.formaFarmaceutica} · " +
                            envaseLegible(presentacion.unidadExpendio, presentacion.cantidadEnvase),
                            style = MaterialTheme.typography.bodyMedium)
                        EstadoPildora(presentacion.condicionDispensa)
                    }
                }
            }
        }

        BarraUbicacion(etiqueta = origenEtiqueta.takeIf { origen != null }, ubicando = ubicando,
            onUsarUbicacion = pedirUbicacion,
            onOlvidar = { repository.origen.value = null; repository.origenEtiqueta.value = null })
        avisoUbicacion?.let { NotaInformativa(it) }

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
            OutlinedButton(onClick = vm::actualizar, enabled = !state.cargando && !state.reservando,
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" Actualizar")
            }
            Button(onClick = onMapa, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(" Ver mapa")
            }
        }
        if (seleccion == null) AvisoError(state.error)
        if (state.cargando && state.farmacias.isEmpty()) IndicadorCargando()

        val titulo = when {
            busqueda.isNotBlank() -> "Resultados de la búsqueda"
            origen != null -> "Farmacias cerca tuyo"
            else -> "Farmacias con stock"
        }
        val detalle = if (filtradas.isEmpty()) "Sin coincidencias"
            else "Mostrando ${mostradas.size} de ${plural(filtradas.size, "farmacia", "farmacias")}"
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
        if (filtradas.size > mostradas.size) {
            TextButton(onClick = { visibles += TANDA_FARMACIAS }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" Ver más farmacias (${filtradas.size - mostradas.size} restantes)",
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
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
                        } else {
                            CampoTexto(value = cantidad,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) cantidad = it },
                                label = { Text("Cantidad (máximo ${seleccion.cantidadDisponible})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = cantidad.isNotEmpty() && !esCantidadValida,
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (bajoReceta) {
                                CampoTexto(value = prescripcion, onValueChange = { prescripcion = it },
                                    label = { Text("Código de la receta") },
                                    supportingText = { Text("Este medicamento se vende bajo receta.") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            AvisoError(state.error)
                            Button(onClick = {
                                vm.reservar(farmaciaId = seleccion.farmaciaId,
                                    cantidad = requireNotNull(numero),
                                    codigoPrescripcion = if (bajoReceta) prescripcion else null)
                            }, enabled = !state.reservando && esCantidadValida &&
                                (!bajoReceta || prescripcion.isNotBlank()) && state.confirmacion == null,
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
        NotaInformativa("Los datos de stock y las prescripciones son de demostración. Confirmá siempre con la farmacia.")
    }
}

/** Desde dónde se ordenan las farmacias: la ubicación del usuario, una dirección o ninguna. */
@Composable
private fun BarraUbicacion(etiqueta: String?, ubicando: Boolean, onUsarUbicacion: () -> Unit, onOlvidar: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(
                if (etiqueta != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (ubicando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                else Icon(if (etiqueta != null) Icons.Default.MyLocation else Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = if (etiqueta != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(if (etiqueta != null) "Ordenadas por cercanía" else "Ubicación desactivada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    ubicando -> "Buscando tu ubicación…"
                    etiqueta != null -> etiqueta
                    else -> "Activala para ver las más cercanas"
                }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (etiqueta == null) {
                TextButton(onClick = onUsarUbicacion, enabled = !ubicando) {
                    Text("Activar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(onClick = onUsarUbicacion, enabled = !ubicando) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Usar mi ubicación actual", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOlvidar, enabled = !ubicando) {
                    Icon(Icons.Default.Close, contentDescription = "Dejar de ordenar por cercanía",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
