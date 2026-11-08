package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
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
            var nombre by remember { mutableStateOf("") }
            var total by remember { mutableStateOf<Long?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(farmaciaId) {
                try {
                    nombre = repository.miPerfil().perfil.nombre
                    if (farmaciaId != null) total = repository.parity.reservasFarmacia(farmaciaId, null, 0).totalElements
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e) }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroPanel("ESPACIO DE FARMACIA", "Hola, ${nombre.ifBlank { "equipo" }}",
                    "Administrá reservas y disponibilidad de tu sucursal desde un solo lugar.",
                    Icons.Default.LocalPharmacy)
                SeccionTitulo("Tu sucursal", "Farmacia #${farmaciaId ?: "—"}")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricaTarjeta((total ?: "—").toString(), "Reservas registradas", Icons.Default.Assignment,
                        modifier = Modifier.weight(1f))
                    MetricaTarjeta("3", "Áreas de trabajo", Icons.Default.Inventory2,
                        modifier = Modifier.weight(1f))
                }
                AvisoError(error)
                SeccionTitulo("Accesos rápidos", "Las tareas de todos los días")
                AccesoTarjeta("Gestionar reservas", "Retiros, confirmaciones y cancelaciones",
                    Icons.Default.CalendarMonth, onReservas)
                AccesoTarjeta("Inventario y stock", "Consultá y ajustá unidades disponibles",
                    Icons.Default.Inventory2, onStock)
                NotaInformativa("Los movimientos de inventario quedan registrados en el sistema para su trazabilidad.")
            }
        }
        "ADMINISTRADOR" -> {
            var farmacias by remember { mutableStateOf<Long?>(null) }
            var medicamentos by remember { mutableStateOf<Long?>(null) }
            var usuarios by remember { mutableStateOf<Long?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    farmacias = repository.parity.adminList("api/admin/farmacias",
                        mapOf("page" to "0", "size" to "1")).totalElements
                    medicamentos = repository.parity.adminList("api/admin/medicamentos",
                        mapOf("page" to "0", "size" to "1")).totalElements
                    usuarios = repository.parity.adminList("api/admin/usuarios",
                        mapOf("page" to "0", "size" to "1")).totalElements
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = mensajeDeError(e) }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroPanel("PANEL DE CONTROL", "Tu sistema, de un vistazo",
                    "Administrá MediLink, las farmacias y su operación desde un mismo espacio.",
                    Icons.Default.AdminPanelSettings)
                SeccionTitulo("Resumen general", "Datos actuales del servidor")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricaTarjeta((farmacias ?: "—").toString(), "Farmacias",
                        Icons.Default.Store, Modifier.weight(1f))
                    MetricaTarjeta((medicamentos ?: "—").toString(), "Medicamentos",
                        Icons.Default.Medication, Modifier.weight(1f))
                    MetricaTarjeta((usuarios ?: "—").toString(), "Usuarios",
                        Icons.Default.People, Modifier.weight(1f))
                }
                AvisoError(error)
                SeccionTitulo("¿Qué querés hacer?", "Accedé a los módulos principales")
                AccesoTarjeta("Administrar sistema", "Farmacias, cuentas, prescripciones y auditoría",
                    Icons.Default.Security, onAdmin)
                AccesoTarjeta("Consultar catálogo", "Ver medicamentos y presentaciones disponibles",
                    Icons.Default.Search, onCatalogo)
                NotaInformativa("MediLink es un proyecto académico. El stock y las prescripciones son de demostración.")
            }
        }
    }
}

@Composable
fun AdminMenuScreen(abrir: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroPanel("ADMINISTRACIÓN", "Centro de gestión",
            "Todos los módulos para gestionar el sistema de forma segura.", Icons.Default.AdminPanelSettings)
        SeccionTitulo("Catálogos y sucursales", "Elegí el área que necesitás administrar")
        AdminSection.entries.forEach { section ->
            val icono = when (section) {
                AdminSection.FARMACIAS -> Icons.Default.Store
                AdminSection.MEDICAMENTOS -> Icons.Default.Medication
                AdminSection.PRESENTACIONES -> Icons.Default.Inventory2
                AdminSection.OPERADORES -> Icons.Default.People
            }
            AccesoTarjeta(section.titulo, "Listar, filtrar y gestionar ${section.titulo.lowercase()}",
                icono, onClick = { abrir("admin/catalogo/${section.name.lowercase()}") })
        }
        SeccionTitulo("Control y trazabilidad")
        AccesoTarjeta("Usuarios y suspensiones", "Cuentas, estados e historial",
            Icons.Default.People, { abrir("admin/usuarios") })
        AccesoTarjeta("Prescripciones simuladas", "Emisión, consulta y anulación",
            Icons.Default.Assignment, { abrir("admin/prescripciones") })
        AccesoTarjeta("Auditoría", "Acciones y eventos del sistema",
            Icons.Default.History, { abrir("admin/auditoria") })
    }
}

@Composable
fun FarmaciaMenuScreen(id: Long, abrir: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroPanel("SUCURSAL", "Farmacia #$id",
            "Reservas, inventario y movimientos en un solo lugar.", Icons.Default.Store)
        SeccionTitulo("Áreas de operación")
        AccesoTarjeta("Reservas", "Retiros y cancelaciones operativas",
            Icons.Default.CalendarMonth, { abrir("farmacia/$id/reservas") })
        AccesoTarjeta("Inventario", "Stock y ajustes de cantidades",
            Icons.Default.Inventory2, { abrir("farmacia/$id/stock") })
        AccesoTarjeta("Movimientos", "Historial y trazabilidad",
            Icons.Default.History, { abrir("farmacia/$id/movimientos") })
    }
}

@Composable
fun InicioPublico(onCatalogo: () -> Unit, onLogin: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroPanel("BIENVENIDO A MEDILINK", "Tu salud, más cerca",
            "Encontrá medicamentos, consultá disponibilidad y reservá en farmacias participantes.",
            Icons.Default.LocalPharmacy) {
            Button(onClick = onCatalogo, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text("Explorar medicamentos")
            }
        }
        SeccionTitulo("Empezá por acá", "Buscá y elegí la mejor opción")
        AccesoTarjeta("Buscar medicamentos", "Consultá presentaciones y stock",
            Icons.Default.Search, onCatalogo)
        AccesoTarjeta("Ingresar a mi cuenta", "Reservas y seguimiento de tus pedidos",
            Icons.Default.People, onLogin)
        NotaInformativa("Sistema académico de demostración: la disponibilidad y las prescripciones no son datos clínicos reales.")
    }
}
