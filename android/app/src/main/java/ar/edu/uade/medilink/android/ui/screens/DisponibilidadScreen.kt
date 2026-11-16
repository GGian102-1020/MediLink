package ar.edu.uade.medilink.android.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm

@Composable
fun DisponibilidadScreen(
    vm: DisponibilidadVm,
    logueado: Boolean,
    onLogin: () -> Unit,
    onReservaCreada: (String) -> Unit,
    onMapa: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var farmaciaElegida by rememberSaveable { mutableStateOf(-1L) }
    var cantidad by rememberSaveable { mutableStateOf("1") }
    var prescripcion by rememberSaveable { mutableStateOf("") }
    val presentacion = state.presentacion
    val seleccion = state.farmacias.find { it.farmaciaId == farmaciaElegida }
    val bajoReceta = presentacion?.condicionDispensa == "BAJO_RECETA"
    val numero = cantidad.toIntOrNull()
    val esCantidadValida = numero != null && numero > 0 &&
        seleccion != null && numero <= seleccion.cantidadDisponible
    val scroll = rememberScrollState()
    var posicionConfirmar by remember { mutableFloatStateOf(0f) }

    // Al elegir farmacia bajamos hasta el bloque de confirmación (antes quedaba fuera de pantalla).
    LaunchedEffect(farmaciaElegida, posicionConfirmar) {
        if (seleccion != null && posicionConfirmar > 0f) scroll.animateScrollTo(posicionConfirmar.toInt())
    }
    // Reserva creada: se abre su detalle, donde el paciente ve el código y el PIN de retiro.
    LaunchedEffect(state.confirmacion) {
        state.confirmacion?.let { onReservaCreada(it.codigoVisible) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Encabezado("Disponibilidad", "Elegí una farmacia con stock para reservar.")
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
        if (state.cargando) IndicadorCargando()
        SeccionTitulo("Farmacias con stock", plural(state.farmacias.size, "opción disponible", "opciones disponibles"))
        if (state.farmacias.isEmpty() && !state.cargando && state.error == null) {
            VacioEstado("Sin stock disponible", "Ninguna farmacia tiene stock de esta presentación por ahora.")
        }
        state.farmacias.forEach { farmacia ->
            FarmaciaCard(farmacia = farmacia, seleccionada = farmacia.farmaciaId == farmaciaElegida,
                onSeleccionar = { farmaciaElegida = farmacia.farmaciaId })
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

@Composable
private fun FarmaciaCard(farmacia: DisponibilidadDto, seleccionada: Boolean, onSeleccionar: () -> Unit) {
    val context = LocalContext.current
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
                farmacia.distanciaKm?.let { Text("%.1f km".format(it),
                    style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSeleccionar, modifier = Modifier.weight(1f)) {
                    Text(if (seleccionada) "Elegida" else "Elegir")
                }
                if (farmacia.latitud != null && farmacia.longitud != null) {
                    OutlinedButton(onClick = {
                        val lat = farmacia.latitud
                        val lng = farmacia.longitud
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(farmacia.nombre)})")
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No hay app de mapas instalada", Toast.LENGTH_LONG).show()
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(" Cómo llegar")
                    }
                }
            }
        }
    }
}
