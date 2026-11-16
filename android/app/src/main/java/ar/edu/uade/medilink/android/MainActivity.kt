package ar.edu.uade.medilink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ar.edu.uade.medilink.android.data.ArchivosReceta
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.ui.screens.AuthScreen
import ar.edu.uade.medilink.android.ui.screens.CatalogoScreen
import ar.edu.uade.medilink.android.ui.screens.CirculosMarca
import ar.edu.uade.medilink.android.ui.screens.DetalleReservaPaciente
import ar.edu.uade.medilink.android.ui.screens.DisponibilidadScreen
import ar.edu.uade.medilink.android.ui.screens.MapasScreen
import ar.edu.uade.medilink.android.ui.screens.MisRecetasScreen
import ar.edu.uade.medilink.android.ui.screens.PanelPaciente
import ar.edu.uade.medilink.android.ui.screens.PerfilScreen
import ar.edu.uade.medilink.android.ui.screens.PresentacionesScreen
import ar.edu.uade.medilink.android.ui.screens.RegistroScreen
import ar.edu.uade.medilink.android.ui.screens.ReservasScreen
import ar.edu.uade.medilink.android.ui.screens.SplashScreen
import ar.edu.uade.medilink.android.ui.screens.VacioEstado
import ar.edu.uade.medilink.android.ui.theme.MediColors
import ar.edu.uade.medilink.android.ui.theme.MediLinkTheme
import ar.edu.uade.medilink.android.ui.viewmodel.AuthVm
import ar.edu.uade.medilink.android.ui.viewmodel.CatalogoVm
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm
import ar.edu.uade.medilink.android.ui.viewmodel.MisRecetasVm
import ar.edu.uade.medilink.android.ui.viewmodel.PresentacionesVm
import ar.edu.uade.medilink.android.ui.viewmodel.ReservasVm
import ar.edu.uade.medilink.android.ui.viewmodel.VmsFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediLinkApp() }
    }
}

/** La sesión vive en memoria: sobrevive rotaciones pero no al cierre del proceso. */
class MediLinkAppVm : ViewModel() { val repository = MediLinkRepository() }

private val RUTAS_ACCESO = setOf("splash", "login", "registro")

private data class DestinoBarra(val ruta: String, val titulo: String, val icono: ImageVector)

/**
 * Navegación entre pestañas: siempre se vuelve a "panel" como raíz y se abre la pestaña de cero.
 * No se usa saveState/restoreState porque restauraba pilas viejas (al tocar "Inicio" aparecía
 * "Reservas" y las listas mostraban datos desactualizados).
 */
private fun NavHostController.irAPestana(ruta: String) {
    if (ruta == "panel") {
        if (!popBackStack("panel", inclusive = false)) navigate("panel") { launchSingleTop = true }
        return
    }
    navigate(ruta) {
        popUpTo("panel")
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediLinkApp() {
    val appVm: MediLinkAppVm = viewModel()
    val repository = appVm.repository
    val sesion by repository.session.login.collectAsStateWithLifecycle()
    val rol = sesion?.perfil?.rol
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val ruta = backStack?.destination?.route.orEmpty()
    // La app es solo para pacientes. El personal de farmacia usa el dashboard web.
    val barra = listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
        DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
        DestinoBarra("reservas", "Reservas", Icons.Default.ReceiptLong),
        DestinoBarra("perfil", "Cuenta", Icons.Default.Person))

    // Acceso obligatorio: sin sesión solo se puede estar en splash, login o registro.
    // También cubre el cierre de sesión y el JWT vencido (la sesión pasa a null).
    val enAcceso = ruta in RUTAS_ACCESO
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

    val rutaPrincipal = ruta in barra.map { it.ruta }
    val seleccionActual = when {
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
                                onClick = { nav.irAPestana(dest.ruta) },
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
                        // Tras reservar se abre el detalle: ahí están el código y el PIN de retiro.
                        onReservaCreada = { codigo ->
                            nav.navigate("reserva/$codigo") { popUpTo("panel") }
                        },
                        onMapa = { nav.navigate("mapa/$id") })
                }
                composable("mapa/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    MapasScreen(repository, e.arguments!!.getLong("id"))
                }
                composable("panel") {
                    if (rol == "USUARIO") PanelPaciente(repository,
                        onCatalogo = { nav.irAPestana("catalogo") },
                        onReservas = { nav.irAPestana("reservas") },
                        onRecetas = { nav.navigate("mis-recetas") { launchSingleTop = true } },
                        onDetalle = { nav.navigate("reserva/$it") })
                }
                composable("mis-recetas") {
                    if (rol == "USUARIO") {
                        val contexto = androidx.compose.ui.platform.LocalContext.current.applicationContext
                        val vm = viewModel<MisRecetasVm>(key = "recetas-${sesion?.perfil?.cuentaId}") {
                            MisRecetasVm(repository, ArchivosReceta(contexto))
                        }
                        MisRecetasScreen(vm)
                    } else SoloPacientes()
                }
                composable("reservas") {
                    if (rol == "USUARIO") {
                        val vm: ReservasVm = viewModel(factory = VmsFactory(repository))
                        ReservasScreen(vm, onDetalle = { nav.navigate("reserva/$it") })
                    } else SoloPacientes()
                }
                composable("reserva/{codigo}") { e ->
                    if (rol == "USUARIO") DetalleReservaPaciente(repository,
                        e.arguments?.getString("codigo").orEmpty(), onVolver = { nav.popBackStack() })
                    else SoloPacientes()
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
            }
        }
    }
}

@Composable
private fun SoloPacientes() = Box(Modifier.padding(18.dp)) {
    VacioEstado("Sección para pacientes", "Iniciá sesión con una cuenta de paciente para ver esta pantalla.")
}
