package ar.edu.uade.medilink.android.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Encabezado("Disponibilidad", "Stock informado por las farmacias del sistema de demostración.")
        if (presentacion != null) {
            Text("${presentacion.medicamentoNombre} · ${presentacion.concentracionTexto}")
            Text("${presentacion.formaFarmaceutica} · ${presentacion.cantidadEnvase} ${presentacion.unidadExpendio}")
            Text(if (bajoReceta) "Requiere prescripción (validación demo)" else "Venta libre")
        }
        OutlinedButton(onClick = vm::actualizar, enabled = !state.cargando && !state.reservando) {
            Text("Actualizar disponibilidad")
        }
        AvisoError(state.error)
        if (state.cargando) IndicadorCargando()
        if (state.farmacias.isEmpty() && !state.cargando && state.error == null) {
            Text("No hay farmacias con stock para esta presentación.")
        }
        state.farmacias.forEach { farmacia ->
            FarmaciaCard(
                farmacia = farmacia,
                seleccionada = farmacia.farmaciaId == farmaciaElegida,
                onSeleccionar = { farmaciaElegida = farmacia.farmaciaId },
            )
        }
        if (seleccion != null) {
            Text("Reservar en ${seleccion.nombre}")
            if (!logueado) {
                Text("Iniciá sesión para confirmar la reserva.")
                Button(onClick = onLogin) { Text("Iniciar sesión") }
            } else {
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) cantidad = it },
                    label = { Text("Cantidad (máximo disponible: ${seleccion.cantidadDisponible})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (bajoReceta) {
                    OutlinedTextField(
                        value = prescripcion,
                        onValueChange = { prescripcion = it },
                        label = { Text("Código de prescripción demo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        vm.reservar(
                            farmaciaId = seleccion.farmaciaId,
                            cantidad = requireNotNull(numero),
                            codigoPrescripcion = if (bajoReceta) prescripcion else null,
                        )
                    },
                    enabled = !state.reservando && esCantidadValida &&
                        (!bajoReceta || prescripcion.isNotBlank()) && state.confirmacion == null,
                ) { Text(if (state.reservando) "Reservando..." else "Confirmar reserva") }
            }
        }
        if (state.confirmacion != null) {
            Text("¡Reserva creada! Código: ${state.confirmacion?.codigoVisible}")
            Button(onClick = onReservaCreada) { Text("Ver mis reservas") }
        }
    }
}

@Composable
private fun FarmaciaCard(
    farmacia: DisponibilidadDto,
    seleccionada: Boolean,
    onSeleccionar: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(farmacia.nombre)
            Text(farmacia.direccion)
            Text("Stock: ${farmacia.cantidadDisponible} unidades")
            farmacia.distanciaKm?.let { Text("Distancia aproximada: %.1f km".format(it)) }
            OutlinedButton(onClick = onSeleccionar) {
                Text(if (seleccionada) "Farmacia seleccionada" else "Elegir farmacia")
            }
            if (farmacia.latitud != null && farmacia.longitud != null) {
                OutlinedButton(onClick = {
                    val lat = farmacia.latitud
                    val lng = farmacia.longitud
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(farmacia.nombre)})")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No hay app de mapas instalada", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Cómo llegar (mapas)") }
            }
        }
    }
}
