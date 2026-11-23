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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ar.edu.uade.medilink.android.ui.screens.ChatScreen
import ar.edu.uade.medilink.android.ui.screens.ReservaConRecetaScreen
import ar.edu.uade.medilink.android.ui.viewmodel.RecetaLista
import ar.edu.uade.medilink.android.ui.viewmodel.ReservaConRecetaVm
import ar.edu.uade.medilink.android.ui.screens.ConversacionesScreen
import ar.edu.uade.medilink.android.ui.screens.DetalleReservaPaciente
import ar.edu.uade.medilink.android.ui.screens.DialogoRequiereSesion
import ar.edu.uade.medilink.android.ui.screens.PanelInvitado
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediLinkApp() }
    }
}

/** La sesión vive en memoria: sobrevive rotaciones pero no al cierre del proceso. */
class MediLinkAppVm : ViewModel() {
    val repository = MediLinkRepository()
    /** Modo invitado: navega sin cuenta; toda acción que requiera sesión muestra un aviso. */
    var invitado by mutableStateOf(false)
}

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
    // Invitado solo mientras no haya sesión: al iniciar sesión deja de aplicar.
    val modoInvitado = appVm.invitado && rol == null
    var avisoSesion by remember { mutableStateOf<String?>(null) }
    val pedirSesion: (String) -> Unit = { avisoSesion = it }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val ruta = backStack?.destination?.route.orEmpty()
    // La app es solo para pacientes. El personal de farmacia usa el dashboard web.
    val barra = listOf(DestinoBarra("panel", "Inicio", Icons.Default.Home),
        DestinoBarra("catalogo", "Buscar", Icons.Default.Search),
        DestinoBarra("reservas", "Reservas", Icons.AutoMirrored.Filled.ReceiptLong),
        DestinoBarra("perfil", "Cuenta", Icons.Default.Person))

    // Acceso obligatorio: sin sesión (y fuera del modo invitado) solo se puede estar en
    // splash, login o registro. También cubre el cierre de sesión y el JWT vencido.
    val enAcceso = ruta in RUTAS_ACCESO
    val vista = androidx.compose.ui.platform.LocalView.current
    val modoOscuro = isSystemInDarkTheme()
    SideEffect {
        val ventana = (vista.context as? android.app.Activity)?.window ?: return@SideEffect
        val control = WindowCompat.getInsetsController(ventana, vista)
        // Rediseño claro: íconos oscuros en las barras del sistema en todas las pantallas.
        control.isAppearanceLightStatusBars = !modoOscuro
        control.isAppearanceLightNavigationBars = !modoOscuro
    }
    LaunchedEffect(rol, ruta, modoInvitado) {
        if (rol == null && ruta.isNotEmpty() && !enAcceso && !modoInvitado) {
            nav.navigate("login") {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val entrarAlHome: () -> Unit = {
        appVm.invitado = false
        nav.navigate("panel") {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val entrarComoInvitado: () -> Unit = {
        appVm.invitado = true
        nav.navigate("panel") {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    val irALogin: () -> Unit = { nav.navigate("login") { launchSingleTop = true } }
    val irARegistro: () -> Unit = { nav.navigate("registro") { launchSingleTop = true } }
    // Secciones que requieren cuenta: el invitado ve el aviso en vez de entrar.
    val mensajesProtegidos = mapOf(
        "reservas" to "Para ver y gestionar tus reservas necesitás iniciar sesión.",
        "mis-recetas" to "Para vincular y consultar tus recetas necesitás iniciar sesión.",
        "chats" to "Para chatear con una farmacia necesitás iniciar sesión.",
    )

    val rutaPrincipal = ruta in barra.map { it.ruta }
    val seleccionActual = when {
        ruta.startsWith("reserva/") -> "reservas"
        ruta == "chats" || ruta.startsWith("chat/") -> "panel"
        ruta.startsWith("presentaciones/") || ruta.startsWith("disponibilidad/") || ruta.startsWith("mapa/") ||
            ruta.startsWith("receta/") -> "catalogo"
        else -> ruta
    }
    MediLinkTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Las pantallas de acceso manejan sus propios insets (status bar, teclado).
            contentWindowInsets = if (enAcceso) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                // Barra superior compacta y clara: logo chico, sin fondos oscuros.
                // En el inicio del paciente no hay barra: el saludo queda arriba de todo.
                if (!enAcceso && !(ruta == "panel" && !modoInvitado)) TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.logo_cruz), contentDescription = null,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = if (modoOscuro) Color.White else MediColors.Navy)) { append("Medi") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Farm") }
                            }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (modoInvitado) TextButton(onClick = irALogin) {
                            Text("Ingresar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
            },
            bottomBar = {
                if (rutaPrincipal && !enAcceso) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            barra.forEach { dest ->
                                NavigationBarItem(
                                    selected = seleccionActual == dest.ruta,
                                    onClick = {
                                        val aviso = mensajesProtegidos[dest.ruta]
                                        if (modoInvitado && aviso != null) pedirSesion(aviso)
                                        else nav.irAPestana(dest.ruta)
                                    },
                                    icon = { Icon(dest.icono, contentDescription = null) },
                                    label = { Text(dest.titulo, fontWeight = FontWeight.Medium) },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "splash", modifier = Modifier.padding(padding)) {
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
                    // BAJO_RECETA con sesión: primero se adjunta la receta y después se eligen farmacias.
                    PresentacionesScreen(vm, onAbrir = { p ->
                        if (p.condicionDispensa == "BAJO_RECETA" && rol == "USUARIO") nav.navigate("receta/${p.id}")
                        else nav.navigate("disponibilidad/${p.id}")
                    })
                }
                composable("receta/{presentacionId}", arguments = listOf(navArgument("presentacionId") { type = NavType.LongType })) { e ->
                    if (rol == "USUARIO") {
                        val presentacionId = e.arguments!!.getLong("presentacionId")
                        val contexto = androidx.compose.ui.platform.LocalContext.current.applicationContext
                        val vm = viewModel<ReservaConRecetaVm>(key = "receta-$presentacionId") {
                            ReservaConRecetaVm(repository, ArchivosReceta(contexto), presentacionId)
                        }
                        ReservaConRecetaScreen(vm, onContinuar = { r ->
                            nav.navigate("disponibilidad/$presentacionId?receta=${android.net.Uri.encode(r.codigo)}&cantidad=${r.cantidad}")
                        })
                    } else SoloPacientes()
                }
                composable("disponibilidad/{id}?receta={receta}&cantidad={cantidad}", arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("receta") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("cantidad") { type = NavType.IntType; defaultValue = 1 },
                )) { e ->
                    val id = e.arguments!!.getLong("id")
                    val receta = e.arguments?.getString("receta")?.let { RecetaLista(it, e.arguments!!.getInt("cantidad")) }
                    val vm: DisponibilidadVm = viewModel(factory = VmsFactory(repository, id))
                    val desdeMapa by e.savedStateHandle.getStateFlow<Long?>("farmaciaElegida", null)
                        .collectAsStateWithLifecycle()
                    DisponibilidadScreen(vm, repository, logueado = rol == "USUARIO", onLogin = irALogin,
                        pedirSesion = pedirSesion,
                        // Tras reservar se abre el detalle: ahí están el código y el PIN de retiro.
                        onReservaCreada = { codigo ->
                            nav.navigate("reserva/$codigo") { popUpTo("panel") }
                        },
                        onMapa = { nav.navigate("mapa/$id") },
                        onVerEnMapa = { farmacia -> nav.navigate("mapa/$id?farmacia=$farmacia") },
                        farmaciaDesdeMapa = desdeMapa,
                        onFarmaciaDesdeMapaUsada = { e.savedStateHandle["farmaciaElegida"] = null },
                        receta = receta,
                        onAdjuntarReceta = { nav.navigate("receta/$id") })
                }
                composable("mapa/{id}?farmacia={farmacia}", arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("farmacia") { type = NavType.LongType; defaultValue = -1L },
                )) { e ->
                    MapasScreen(repository, e.arguments!!.getLong("id"),
                        farmaciaInicial = e.arguments!!.getLong("farmacia").takeIf { it > 0 },
                        logueado = rol == "USUARIO", pedirSesion = pedirSesion,
                        onReservar = { farmacia ->
                            nav.previousBackStackEntry?.savedStateHandle?.set("farmaciaElegida", farmacia)
                            nav.popBackStack()
                        })
                }
                composable("panel") {
                    if (rol == "USUARIO") PanelPaciente(repository,
                        onCatalogo = { nav.irAPestana("catalogo") },
                        onReservas = { nav.irAPestana("reservas") },
                        onRecetas = { nav.navigate("mis-recetas") { launchSingleTop = true } },
                        onMensajes = { nav.navigate("chats") { launchSingleTop = true } },
                        onDetalle = { nav.navigate("reserva/$it") })
                    else if (modoInvitado) PanelInvitado(
                        onCatalogo = { nav.irAPestana("catalogo") },
                        onIniciarSesion = irALogin,
                        pedirSesion = pedirSesion)
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
                        e.arguments?.getString("codigo").orEmpty(), onVolver = { nav.popBackStack() },
                        onChatear = { id, nombre -> nav.navigate(rutaChat(id, nombre)) })
                    else SoloPacientes()
                }
                composable("chats") {
                    if (rol == "USUARIO") ConversacionesScreen(repository,
                        onAbrir = { nav.navigate(rutaChat(it.farmaciaId, it.nombre)) })
                    else SoloPacientes()
                }
                composable("chat/{farmaciaId}?nombre={nombre}", arguments = listOf(
                    navArgument("farmaciaId") { type = NavType.LongType },
                    navArgument("nombre") { type = NavType.StringType; defaultValue = "Farmacia" },
                )) { e ->
                    if (rol == "USUARIO") ChatScreen(repository, e.arguments!!.getLong("farmaciaId"),
                        e.arguments?.getString("nombre").orEmpty().ifBlank { "Farmacia" })
                    else SoloPacientes()
                }
                composable("perfil") {
                    PerfilScreen(repository, sesion, onLogin = irALogin)
                }
                composable("login") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    AuthScreen(vm, onSuccess = entrarAlHome,
                        onRegistro = { nav.navigate("registro") { launchSingleTop = true } },
                        onInvitado = entrarComoInvitado)
                }
                composable("registro") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    RegistroScreen(vm, onVolver = {
                        if (!nav.popBackStack("login", inclusive = false)) nav.navigate("login")
                    }, onSuccess = entrarAlHome)
                }
            }
        }
        avisoSesion?.let { mensaje ->
            DialogoRequiereSesion(mensaje,
                onIniciarSesion = { avisoSesion = null; irALogin() },
                onRegistrarse = { avisoSesion = null; irARegistro() },
                onCerrar = { avisoSesion = null })
        }
    }
}

private fun rutaChat(farmaciaId: Long, nombre: String) = "chat/$farmaciaId?nombre=${android.net.Uri.encode(nombre)}"

@Composable
private fun SoloPacientes() = Box(Modifier.padding(18.dp)) {
    VacioEstado("Sección para pacientes", "Iniciá sesión con una cuenta de paciente para ver esta pantalla.")
}
