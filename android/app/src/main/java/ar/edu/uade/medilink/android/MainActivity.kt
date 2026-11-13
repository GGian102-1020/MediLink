package ar.edu.uade.medilink.android

import ar.edu.uade.medilink.android.ui.screens.CirculosMarca
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.NavigationBarItemDefaults
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ar.edu.uade.medilink.android.ui.theme.MediLinkTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
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
import ar.edu.uade.medilink.android.ui.screens.SplashScreen
import ar.edu.uade.medilink.android.ui.theme.MediColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
        setContent {
            // El color de los íconos de las barras del sistema se decide en MediLinkApp según la ruta.
            MediLinkApp()
        }
    }
}

/** La sesión vive en memoria: sobrevive rotaciones pero no al cierre del proceso. */
class MediLinkAppVm : ViewModel() { val repository = MediLinkRepository() }

private val RUTAS_ACCESO = setOf("splash", "login", "registro")

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
    val barra = when (rol) {
        "USUARIO" -> listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("reservas", "Reservas", Icons.Default.ReceiptLong),
            DestinoBarra("perfil", "Cuenta", Icons.Default.Person))
        "OPERADOR" -> listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("operacion/reservas", "Reservas", Icons.Default.ReceiptLong),
            DestinoBarra("operacion/stock", "Stock", Icons.Default.Inventory2),
            DestinoBarra("operacion/movimientos", "Historial", Icons.Default.List),
            DestinoBarra("perfil", "Cuenta", Icons.Default.Person))
        "ADMINISTRADOR" -> listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
            DestinoBarra("admin/menu", "Gestión", Icons.Default.AdminPanelSettings),
            DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("perfil", "Cuenta", Icons.Default.Person))
        else -> listOf(DestinoBarra("inicio", "Inicio", Icons.Default.Home),
            DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
            DestinoBarra("perfil", "Cuenta", Icons.Default.Person))
    }

    // Acceso obligatorio: sin sesión solo se puede estar en splash, login o registro.
    // También cubre el cierre de sesión y el JWT vencido (la sesión pasa a null).
    val enAcceso = ruta in RUTAS_ACCESO
    // Todas las pantallas (menos el splash blanco) tienen encabezado azul noche:
    // íconos claros arriba y abajo (el fondo detrás del contenido también es azul noche).
    val vista = androidx.compose.ui.platform.LocalView.current
    val modoOscuro = isSystemInDarkTheme()
    SideEffect {
        val ventana = (vista.context as? android.app.Activity)?.window ?: return@SideEffect
        val control = WindowCompat.getInsetsController(ventana, vista)
        control.isAppearanceLightStatusBars = !modoOscuro && (ruta == "splash" || ruta.isEmpty())
        control.isAppearanceLightNavigationBars = !modoOscuro && enAcceso
    }
    LaunchedEffect(rol, ruta) {
        if (rol == null && ruta.isNotEmpty() && !enAcceso) {
            nav.navigate("login") {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val entrarAlHome: () -> Unit = {
        nav.navigate("panel") {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val principales = barra.map { it.ruta }.toSet()
    val rutaPrincipal = ruta in principales
    val seleccionActual = when {
        ruta.startsWith("admin/") -> "admin/menu"
        ruta.startsWith("operacion/") -> ruta
        ruta.startsWith("reserva/") -> "reservas"
        ruta.startsWith("presentaciones/") || ruta.startsWith("disponibilidad/") || ruta.startsWith("mapa/") -> "catalogo"
        else -> ruta
    }
    MediLinkTheme {
        Scaffold(
            // Azul noche detrás de todo: el contenido se recorta con una esquina curva sobre él.
            containerColor = if (enAcceso) MaterialTheme.colorScheme.background else MediColors.Noche,
            // Las pantallas de acceso manejan sus propios insets (status bar, teclado).
            contentWindowInsets = if (enAcceso) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!enAcceso) Box(Modifier.background(MediColors.Noche)) {
                    CirculosMarca(Modifier.matchParentSize().alpha(0.7f), escala = 0.75f)
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = Color.White,
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White),
                                    contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.logo_cruz), contentDescription = null,
                                        modifier = Modifier.size(27.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.White)) { append("Med") }
                                    withStyle(SpanStyle(color = MediColors.Aqua)) { append("link") }
                                }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                        },
                        navigationIcon = {
                            if (!rutaPrincipal && nav.previousBackStackEntry != null) {
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (rutaPrincipal && !enAcceso) {
                    NavigationBar(containerColor = MediColors.Noche, tonalElevation = 0.dp) {
                        barra.forEach { dest ->
                            NavigationBarItem(
                                selected = seleccionActual == dest.ruta,
                                onClick = { nav.navigate(dest.ruta) {
                                    launchSingleTop = true
                                    restoreState = true
                                    // "panel" es la raíz después del login.
                                    popUpTo("panel") { saveState = true }
                                } },
                                icon = { Icon(dest.icono, contentDescription = null) },
                                label = { Text(dest.titulo) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MediColors.Noche,
                                    selectedTextColor = MediColors.Aqua,
                                    indicatorColor = MediColors.Aqua,
                                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "splash",
                modifier = Modifier.padding(padding).then(
                    if (enAcceso) Modifier
                    else Modifier.clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                        .background(MaterialTheme.colorScheme.background)
                )) {
                composable("splash") {
                    SplashScreen(onTerminado = {
                        if (rol != null) entrarAlHome()
                        else nav.navigate("login") { popUpTo("splash") { inclusive = true } }
                    })
                }
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
                    AuthScreen(vm, onSuccess = entrarAlHome,
                        onRegistro = { nav.navigate("registro") { launchSingleTop = true } })
                }
                composable("registro") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    RegistroScreen(vm, onVolver = {
                        if (!nav.popBackStack("login", inclusive = false)) nav.navigate("login")
                    }, onSuccess = entrarAlHome)
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
