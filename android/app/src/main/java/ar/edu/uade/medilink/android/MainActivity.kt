package ar.edu.uade.medilink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.ui.screens.AdminAuditoriaScreen
import ar.edu.uade.medilink.android.ui.screens.AdminCatalogScreen
import ar.edu.uade.medilink.android.ui.screens.AdminMenuScreen
import ar.edu.uade.medilink.android.ui.screens.AdminPrescripcionesScreen
import ar.edu.uade.medilink.android.ui.screens.AdminSection
import ar.edu.uade.medilink.android.ui.screens.AdminUsuarioDetalleScreen
import ar.edu.uade.medilink.android.ui.screens.AdminUsuariosScreen
import ar.edu.uade.medilink.android.ui.screens.AuthScreen
import ar.edu.uade.medilink.android.ui.screens.CatalogoScreen
import ar.edu.uade.medilink.android.ui.screens.DetalleReservaPaciente
import ar.edu.uade.medilink.android.ui.screens.DisponibilidadScreen
import ar.edu.uade.medilink.android.ui.screens.Encabezado
import ar.edu.uade.medilink.android.ui.screens.FarmaciaMenuScreen
import ar.edu.uade.medilink.android.ui.screens.InicioPublico
import ar.edu.uade.medilink.android.ui.screens.MapasScreen
import ar.edu.uade.medilink.android.ui.screens.MovimientosFarmaciaScreen
import ar.edu.uade.medilink.android.ui.screens.PanelPorRol
import ar.edu.uade.medilink.android.ui.screens.PerfilScreen
import ar.edu.uade.medilink.android.ui.screens.PresentacionesScreen
import ar.edu.uade.medilink.android.ui.screens.RegistroScreen
import ar.edu.uade.medilink.android.ui.screens.ReservasFarmaciaScreen
import ar.edu.uade.medilink.android.ui.screens.ReservasScreen
import ar.edu.uade.medilink.android.ui.screens.RequiereSesion
import ar.edu.uade.medilink.android.ui.screens.StockFarmaciaScreen
import ar.edu.uade.medilink.android.ui.viewmodel.AuthVm
import ar.edu.uade.medilink.android.ui.viewmodel.CatalogoVm
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm
import ar.edu.uade.medilink.android.ui.viewmodel.PresentacionesVm
import ar.edu.uade.medilink.android.ui.viewmodel.ReservasVm
import ar.edu.uade.medilink.android.ui.viewmodel.VmsFactory
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediLinkApp() }
    }
}

/** La sesión vive en memoria: sobrevive rotaciones pero no al cierre del proceso. */
class MediLinkAppVm : ViewModel() { val repository = MediLinkRepository() }

private data class DestinoBarra(val ruta: String, val titulo: String, val icono: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediLinkApp() {
    val appVm: MediLinkAppVm = viewModel()
    val repository = appVm.repository
    val sesion by repository.session.login.collectAsStateWithLifecycle()
    val rol = sesion?.perfil?.rol
    val farmaciaOperador = sesion?.perfil?.farmaciaId
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val ruta = backStack?.destination?.route.orEmpty()
    val colores = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF78D6C9), secondary = Color(0xFFAACCC7))
    } else lightColorScheme(primary = Color(0xFF006B60), secondary = Color(0xFF526560))
    val barra = when (rol) {
        "USUARIO" -> listOf(DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("reservas", "Reservas", Icons.Default.List),
            DestinoBarra("perfil", "Cuenta", Icons.Default.AccountCircle))
        "OPERADOR" -> listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("operacion/reservas", "Reservas", Icons.Default.List),
            DestinoBarra("operacion/stock", "Stock", Icons.Default.Settings),
            DestinoBarra("operacion/movimientos", "Movimientos", Icons.Default.List),
            DestinoBarra("perfil", "Cuenta", Icons.Default.AccountCircle))
        "ADMINISTRADOR" -> listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("admin/menu", "Gestión", Icons.Default.Settings),
            DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("perfil", "Cuenta", Icons.Default.AccountCircle))
        else -> listOf(DestinoBarra("inicio", "Inicio", Icons.Default.Home),
            DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("perfil", "Cuenta", Icons.Default.AccountCircle))
    }

    MaterialTheme(colorScheme = colores) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("MediLink") }, navigationIcon = {
                    if (nav.previousBackStackEntry != null && barra.none { it.ruta == ruta }) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                })
            },
            bottomBar = {
                NavigationBar {
                    barra.forEach { dest ->
                        NavigationBarItem(selected = ruta == dest.ruta,
                            onClick = { nav.navigate(dest.ruta) {
                                launchSingleTop = true
                                popUpTo("catalogo") { saveState = false }
                            } },
                            icon = { Icon(dest.icono, contentDescription = dest.titulo) },
                            label = { Text(dest.titulo) })
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "catalogo", modifier = Modifier.padding(padding)) {
                composable("inicio") { InicioPublico(onCatalogo = { nav.navigate("catalogo") },
                    onLogin = { nav.navigate("login") }) }
                composable("catalogo") {
                    val vm: CatalogoVm = viewModel(factory = VmsFactory(repository))
                    CatalogoScreen(vm, onAbrir = { nav.navigate("presentaciones/$it") })
                }
                composable("presentaciones/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val vm: PresentacionesVm = viewModel(factory = VmsFactory(repository, e.arguments!!.getLong("id")))
                    PresentacionesScreen(vm, onAbrir = { nav.navigate("disponibilidad/$it") })
                }
                composable("disponibilidad/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments!!.getLong("id")
                    val vm: DisponibilidadVm = viewModel(factory = VmsFactory(repository, id))
                    DisponibilidadScreen(vm, logueado = rol == "USUARIO", onLogin = { nav.navigate("login") },
                        onReservaCreada = { nav.navigate("reservas") { popUpTo("catalogo") } },
                        onMapa = { nav.navigate("mapa/$id") })
                }
                composable("mapa/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    MapasScreen(repository, e.arguments!!.getLong("id"))
                }
                composable("panel") {
                    if (rol == null) RequiereSesion { nav.navigate("login") }
                    else PanelPorRol(repository, rol, farmaciaOperador,
                        onCatalogo = { nav.navigate("catalogo") },
                        onReservas = { nav.navigate(if (rol == "USUARIO") "reservas" else "operacion/reservas") },
                        onAdmin = { nav.navigate("admin/menu") },
                        onStock = { nav.navigate("operacion/stock") })
                }
                composable("reservas") {
                    if (rol == null) RequiereSesion { nav.navigate("login") }
                    else if (rol != "USUARIO") Text("El historial de pacientes corresponde al rol USUARIO.")
                    else {
                        val vm: ReservasVm = viewModel(factory = VmsFactory(repository))
                        ReservasScreen(vm, onDetalle = { nav.navigate("reserva/$it") })
                    }
                }
                composable("reserva/{codigo}") { e ->
                    if (rol == "USUARIO") DetalleReservaPaciente(repository,
                        e.arguments?.getString("codigo").orEmpty(), onVolver = { nav.popBackStack() })
                    else Text("Este historial requiere un paciente autenticado.")
                }
                composable("perfil") {
                    PerfilScreen(repository, sesion, onLogin = { nav.navigate("login") })
                }
                composable("login") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    AuthScreen(vm, onSuccess = {
                        // Retomar la selección de farmacia/presentación después del login.
                        val anterior = nav.previousBackStackEntry?.destination?.route.orEmpty()
                        if (anterior.startsWith("disponibilidad/")) nav.popBackStack()
                        else nav.navigate("panel") {
                            popUpTo("catalogo") { inclusive = false }
                            launchSingleTop = true
                        }
                    }, onRegistro = { nav.navigate("registro") })
                }
                composable("registro") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    RegistroScreen(vm, onVolver = { nav.popBackStack() })
                }
                composable("operacion/reservas") {
                    if (rol == "OPERADOR" && farmaciaOperador != null)
                        ReservasFarmaciaScreen(repository, farmaciaOperador, rol)
                    else Text("La operación requiere una cuenta OPERADOR con farmacia asignada.")
                }
                composable("operacion/stock") {
                    if (rol == "OPERADOR" && farmaciaOperador != null)
                        StockFarmaciaScreen(repository, farmaciaOperador)
                    else Text("La operación requiere una cuenta OPERADOR con farmacia asignada.")
                }
                composable("operacion/movimientos") {
                    if (rol == "OPERADOR" && farmaciaOperador != null)
                        MovimientosFarmaciaScreen(repository, farmaciaOperador)
                    else Text("La operación requiere una cuenta OPERADOR con farmacia asignada.")
                }
                composable("admin/menu") {
                    if (rol == "ADMINISTRADOR") AdminMenuScreen { path -> nav.navigate(path) }
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/catalogo/{section}") { e ->
                    if (rol == "ADMINISTRADOR") {
                        val sectionName = e.arguments?.getString("section").orEmpty().uppercase(Locale.ROOT)
                        val section = AdminSection.entries.firstOrNull { it.name == sectionName }
                        if (section != null) AdminCatalogScreen(repository, section,
                            abrirFarmacia = { nav.navigate("farmacia/$it") },
                            abrirPresentacion = { nav.navigate("disponibilidad/$it") },
                            abrirPresentacionesMedicamento = { nav.navigate("admin/presentaciones/medicamento/$it") })
                        else Text("Módulo desconocido")
                    } else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/presentaciones/medicamento/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") AdminCatalogScreen(repository, AdminSection.PRESENTACIONES,
                        abrirFarmacia = { nav.navigate("farmacia/$it") },
                        abrirPresentacion = { nav.navigate("disponibilidad/$it") },
                        abrirPresentacionesMedicamento = { nav.navigate("admin/presentaciones/medicamento/$it") },
                        medicamentoInicial = e.arguments!!.getLong("id"))
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/usuarios") {
                    if (rol == "ADMINISTRADOR") AdminUsuariosScreen(repository) { nav.navigate("admin/usuarios/$it") }
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/usuarios/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") AdminUsuarioDetalleScreen(repository, e.arguments!!.getLong("id"))
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/prescripciones") {
                    if (rol == "ADMINISTRADOR") AdminPrescripcionesScreen(repository)
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("admin/auditoria") {
                    if (rol == "ADMINISTRADOR") AdminAuditoriaScreen(repository)
                    else Text("Se requiere una cuenta administradora.")
                }
                composable("farmacia/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") FarmaciaMenuScreen(e.arguments!!.getLong("id")) { nav.navigate(it) }
                    else Text("Solo administración puede seleccionar otra farmacia.")
                }
                composable("farmacia/{id}/reservas", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") ReservasFarmaciaScreen(repository, e.arguments!!.getLong("id"), rol)
                    else Text("Se requiere administración.")
                }
                composable("farmacia/{id}/stock", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") StockFarmaciaScreen(repository, e.arguments!!.getLong("id"))
                    else Text("Se requiere administración.")
                }
                composable("farmacia/{id}/movimientos", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    if (rol == "ADMINISTRADOR") MovimientosFarmaciaScreen(repository, e.arguments!!.getLong("id"))
                    else Text("Se requiere administración.")
                }
            }
        }
    }
}
