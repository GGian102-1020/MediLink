package ar.edu.uade.medilink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
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
import ar.edu.uade.medilink.android.ui.screens.AuthScreen
import ar.edu.uade.medilink.android.ui.screens.CatalogoScreen
import ar.edu.uade.medilink.android.ui.screens.DisponibilidadScreen
import ar.edu.uade.medilink.android.ui.screens.PerfilScreen
import ar.edu.uade.medilink.android.ui.screens.PresentacionesScreen
import ar.edu.uade.medilink.android.ui.screens.RegistroScreen
import ar.edu.uade.medilink.android.ui.screens.ReservasScreen
import ar.edu.uade.medilink.android.ui.screens.RequiereSesion
import ar.edu.uade.medilink.android.ui.viewmodel.AuthVm
import ar.edu.uade.medilink.android.ui.viewmodel.CatalogoVm
import ar.edu.uade.medilink.android.ui.viewmodel.DisponibilidadVm
import ar.edu.uade.medilink.android.ui.viewmodel.PresentacionesVm
import ar.edu.uade.medilink.android.ui.viewmodel.ReservasVm
import ar.edu.uade.medilink.android.ui.viewmodel.VmsFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MediLinkApp() }
    }
}

/** La sesion en memoria sobrevive rotaciones, pero no el cierre del proceso. */
class MediLinkAppVm : ViewModel() {
    val repository = MediLinkRepository()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediLinkApp() {
    val appVm: MediLinkAppVm = viewModel()
    val repository = appVm.repository
    val sesion by repository.session.login.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF78D6C9), secondary = Color(0xFFAACCC7))
    } else {
        lightColorScheme(primary = Color(0xFF006B60), secondary = Color(0xFF526560))
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("MediLink") },
                    navigationIcon = {
                        if (nav.previousBackStackEntry != null && route !in listOf("catalogo", "reservas", "perfil")) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(
                        Triple("catalogo", "Buscar", Icons.Default.Search),
                        Triple("reservas", "Reservas", Icons.Default.List),
                        Triple("perfil", "Cuenta", Icons.Default.AccountCircle),
                    ).forEach { (destino, titulo, icono) ->
                        NavigationBarItem(
                            selected = route == destino,
                            onClick = {
                                nav.navigate(destino) {
                                    launchSingleTop = true
                                    popUpTo("catalogo") { saveState = false }
                                }
                            },
                            icon = { Icon(icono, contentDescription = titulo) },
                            label = { Text(titulo) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "catalogo",
                modifier = Modifier.padding(padding),
            ) {
                composable("catalogo") {
                    val vm: CatalogoVm = viewModel(factory = VmsFactory(repository))
                    CatalogoScreen(vm, onAbrir = { nav.navigate("presentaciones/$it") })
                }
                composable(
                    "presentaciones/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = requireNotNull(entry.arguments).getLong("id")
                    val vm: PresentacionesVm = viewModel(factory = VmsFactory(repository, id))
                    PresentacionesScreen(vm, onAbrir = { nav.navigate("disponibilidad/$it") })
                }
                composable(
                    "disponibilidad/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = requireNotNull(entry.arguments).getLong("id")
                    val vm: DisponibilidadVm = viewModel(factory = VmsFactory(repository, id))
                    DisponibilidadScreen(
                        vm = vm,
                        logueado = sesion != null,
                        onLogin = { nav.navigate("login") },
                        onReservaCreada = {
                            nav.navigate("reservas") {
                                popUpTo("catalogo")
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable("reservas") {
                    if (sesion == null) {
                        RequiereSesion(onLogin = { nav.navigate("login") })
                    } else {
                        val vm: ReservasVm = viewModel(factory = VmsFactory(repository))
                        ReservasScreen(vm)
                    }
                }
                composable("perfil") {
                    PerfilScreen(repository, sesion, onLogin = { nav.navigate("login") })
                }
                composable("login") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    AuthScreen(
                        vm = vm,
                        onSuccess = { nav.popBackStack() },
                        onRegistro = { nav.navigate("registro") },
                    )
                }
                composable("registro") {
                    val vm: AuthVm = viewModel(factory = VmsFactory(repository))
                    RegistroScreen(vm, onVolver = { nav.popBackStack() })
                }
            }
        }
    }
}
