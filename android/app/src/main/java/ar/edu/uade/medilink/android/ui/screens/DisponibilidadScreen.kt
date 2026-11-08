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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.DisponibilidadDto
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm

@Composable
fun DisponibilidadScreen(
    vm: DisponibilidadVm,
    logueado: Boolean,
    onLogin: () -> Unit,
    onReservaCreada: () -> Unit,
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Encabezado("Disponibilidad", "Elegí una farmacia con stock para continuar.")
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
                        Text("${presentacion.concentracionTexto} · ${presentacion.formaFarmaceutica}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Envase: ${presentacion.cantidadEnvase} ${presentacion.unidadExpendio}",
                            style = MaterialTheme.typography.bodySmall)
                        EstadoPildora(if (bajoReceta) "Bajo receta" else "Venta libre")
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
        AvisoError(state.error)
        if (state.cargando) IndicadorCargando()
        SeccionTitulo("Farmacias con stock", "${state.farmacias.size} opciones disponibles")
        if (state.farmacias.isEmpty() && !state.cargando && state.error == null) {
            VacioEstado("Sin stock disponible", "No hay farmacias con stock para esta presentación.")
        }
        state.farmacias.forEach { farmacia ->
            FarmaciaCard(farmacia = farmacia, seleccionada = farmacia.farmaciaId == farmaciaElegida,
                onSeleccionar = { farmaciaElegida = farmacia.farmaciaId })
        }
        if (seleccion != null) {
            SeccionTitulo("Confirmar reserva", "${seleccion.nombre} · ${seleccion.cantidadDisponible} disponibles")
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!logueado) {
                        NotaInformativa("Iniciá sesión con una cuenta de paciente para confirmar la reserva.")
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                    } else {
                        OutlinedTextField(value = cantidad,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) cantidad = it },
                            label = { Text("Cantidad (máximo ${seleccion.cantidadDisponible})") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (bajoReceta) {
                            OutlinedTextField(value = prescripcion, onValueChange = { prescripcion = it },
                                label = { Text("Código de prescripción demo") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Button(onClick = {
                            vm.reservar(farmaciaId = seleccion.farmaciaId,
                                cantidad = requireNotNull(numero),
                                codigoPrescripcion = if (bajoReceta) prescripcion else null)
                        }, enabled = !state.reservando && esCantidadValida &&
                            (!bajoReceta || prescripcion.isNotBlank()) && state.confirmacion == null,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.reservando) "Reservando..." else "Confirmar reserva")
                        }
                    }
                }
            }
        }
        if (state.confirmacion != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text("¡Reserva creada!", style = MaterialTheme.typography.titleLarge)
                    Text("Código: ${state.confirmacion?.codigoVisible}")
                    Button(onClick = onReservaCreada, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver mis reservas")
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
                EstadoPildora("${farmacia.cantidadDisponible} unidades")
                farmacia.distanciaKm?.let { Text("%.1f km".format(it),
                    style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = onSeleccionar, modifier = Modifier.fillMaxWidth()) {
                Text(if (seleccionada) "Farmacia seleccionada" else "Elegir farmacia")
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
                }, modifier = Modifier.fillMaxWidth()) { Text("Cómo llegar") }
            }
        }
    }
}
