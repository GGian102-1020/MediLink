package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.mensajeDeError
import kotlinx.coroutines.CancellationException

@Composable
fun PanelPorRol(repository: MediLinkRepository, rol: String, farmaciaId: Long?,
    onCatalogo: () -> Unit, onReservas: () -> Unit, onAdmin: () -> Unit, onStock: () -> Unit) {
    when (rol) {
        "USUARIO" -> PanelPaciente(repository, onCatalogo, onReservas)
        "OPERADOR" -> {
            var cuenta by remember { mutableStateOf("") }
            var total by remember { mutableStateOf<Long?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(farmaciaId) {
                try {
                    cuenta = repository.miPerfil().perfil.nombre
                    if (farmaciaId != null) total = repository.parity.reservasFarmacia(farmaciaId, null, 0).totalElements
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e) }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Encabezado("Buen día, $cuenta", "Operación de farmacia #${farmaciaId ?: "—"}")
                Text("Reservas registradas: ${total ?: "—"}")
                AvisoError(error)
                Button(onClick = onReservas) { Text("Gestionar reservas") }
                OutlinedButton(onClick = onStock) { Text("Consultar y ajustar stock") }
                Text("Los movimientos se encuentran en la sección de stock o en el historial de operaciones.")
            }
        }
        "ADMINISTRADOR" -> {
            var farmaciaCount by remember { mutableStateOf<Long?>(null) }
            var medsCount by remember { mutableStateOf<Long?>(null) }
            var usuariosCount by remember { mutableStateOf<Long?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    farmaciaCount = repository.parity.adminList("api/admin/farmacias", mapOf("page" to "0", "size" to "1")).totalElements
                    medsCount = repository.parity.adminList("api/admin/medicamentos", mapOf("page" to "0", "size" to "1")).totalElements
                    usuariosCount = repository.parity.adminList("api/admin/usuarios", mapOf("page" to "0", "size" to "1")).totalElements
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e) }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Encabezado("Administración MediLink", "Gestión integral del sistema")
                Text("Farmacias: ${farmaciaCount ?: "—"}")
                Text("Medicamentos: ${medsCount ?: "—"}")
                Text("Usuarios: ${usuariosCount ?: "—"}")
                AvisoError(error)
                Button(onClick = onAdmin) { Text("Abrir módulos de administración") }
                OutlinedButton(onClick = onCatalogo) { Text("Consultar catálogo público") }
            }
        }
    }
}

@Composable
fun AdminMenuScreen(abrir: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Administración", "Farmacias, medicamentos, cuentas y trazabilidad")
        AdminSection.entries.forEach { section ->
            Button(onClick = { abrir("admin/catalogo/${section.name.lowercase()}") }) { Text(section.titulo) }
        }
        OutlinedButton(onClick = { abrir("admin/usuarios") }) { Text("Usuarios y suspensiones") }
        OutlinedButton(onClick = { abrir("admin/prescripciones") }) { Text("Prescripciones simuladas") }
        OutlinedButton(onClick = { abrir("admin/auditoria") }) { Text("Auditoría") }
    }
}

@Composable
fun FarmaciaMenuScreen(id: Long, abrir: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("Farmacia #$id", "Módulos de operación y stock")
        Button(onClick = { abrir("farmacia/$id/reservas") }) { Text("Reservas") }
        Button(onClick = { abrir("farmacia/$id/stock") }) { Text("Inventario") }
        Button(onClick = { abrir("farmacia/$id/movimientos") }) { Text("Movimientos") }
    }
}

@Composable
fun InicioPublico(onCatalogo: () -> Unit, onLogin: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Encabezado("MediLink · Tu salud, más cerca", "Encontrá medicamentos y farmacias con disponibilidad")
        Text("Consultá el catálogo, las presentaciones y las farmacias participantes. " +
            "Para reservar necesitás una cuenta de paciente.")
        Button(onClick = onCatalogo) { Text("Buscar medicamentos") }
        OutlinedButton(onClick = onLogin) { Text("Iniciar sesión") }
        Text("Sistema académico con disponibilidad y prescripciones simuladas.")
    }
}
