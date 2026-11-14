package ar.edu.uade.medilink.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import ar.edu.uade.medilink.android.data.DatosPersonales
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.R
import ar.edu.uade.medilink.android.ui.theme.MediColors
import ar.edu.uade.medilink.android.ui.viewmodel.AuthVm

private val PillShape = RoundedCornerShape(20.dp)
private val CampoGris = Color(0xFFE5E8ED)
private const val CLAVE_MIN = 8
private const val CLAVE_MAX = 72
private val CORREO_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Login obligatorio: encabezado oscuro con círculos y tarjeta blanca (diseño de pantallas.zip). */
@Composable
fun AuthScreen(vm: AuthVm, onSuccess: () -> Unit, onRegistro: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    val foco = LocalFocusManager.current
    LaunchedEffect(state.inicioExitoso) { if (state.inicioExitoso) onSuccess() }
    val puedeEntrar = !state.cargando && correo.isNotBlank() && contrasena.isNotBlank()
    val entrar = { foco.clearFocus(); vm.login(correo, contrasena) }

    PantallaAuth(
        altoEncabezado = 270.dp,
        formaTarjeta = RoundedCornerShape(topEnd = 96.dp),
        encabezado = { LogoEnCaja(Modifier.align(Alignment.Center)) },
        pie = {
            BotonPrincipal(if (state.cargando) "Ingresando" else "Ingresar", cargando = state.cargando,
                habilitado = puedeEntrar, modifier = Modifier.fillMaxWidth(), onClick = entrar)
            EnlaceCentrado("¿No tenés cuenta?", "Registrate", habilitado = !state.cargando, onClick = onRegistro)
        },
    ) {
        TituloGrande("Iniciar sesión", "Ingresá para continuar.")
        Spacer(Modifier.height(28.dp))
        CampoPildora("Correo electrónico", correo, { correo = it.trim() },
            teclado = KeyboardType.Email, accion = ImeAction.Next,
            onAccion = { foco.moveFocus(FocusDirection.Down) })
        Spacer(Modifier.height(18.dp))
        CampoPildora("Contraseña", contrasena, { contrasena = it }, esClave = true,
            accion = ImeAction.Done, onAccion = { if (puedeEntrar) entrar() })
        if (state.error != null) {
            Spacer(Modifier.height(14.dp))
            AvisoError(state.error)
        }
    }
}

/**
 * Registro de paciente en tres pasos: cuenta → datos personales → contraseña.
 * Al crear la cuenta inicia sesión automáticamente y entra a la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(vm: AuthVm, onVolver: () -> Unit, onSuccess: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var paso by rememberSaveable { mutableIntStateOf(1) }
    var nombre by rememberSaveable { mutableStateOf("") }
    var apellido by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var dni by rememberSaveable { mutableStateOf("") }
    var nacimientoIso by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    var obraSocial by rememberSaveable { mutableStateOf(SIN_OBRA_SOCIAL) }
    var otraObraSocial by rememberSaveable { mutableStateOf("") }
    var afiliado by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var verCalendario by remember { mutableStateOf(false) }
    val foco = LocalFocusManager.current
    LaunchedEffect(state.inicioExitoso) { if (state.inicioExitoso) onSuccess() }
    BackHandler(enabled = paso > 1 && !state.cargando) { paso-- }

    val correoValido = CORREO_REGEX.matches(correo)
    val paso1Ok = nombre.isNotBlank() && apellido.isNotBlank() && correoValido

    val hoy = remember { LocalDate.now() }
    val nacimiento = nacimientoIso.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    val dniOk = dni.length in 7..8
    val telefonoOk = telefono.isBlank() || telefono.filter(Char::isDigit).length in 6..15
    val nombreObraSocial = when (obraSocial) {
        SIN_OBRA_SOCIAL -> null
        OTRA_OBRA_SOCIAL -> otraObraSocial.trim().ifBlank { null }
        else -> obraSocial
    }
    val obraSocialOk = obraSocial != OTRA_OBRA_SOCIAL || nombreObraSocial != null
    val paso2Ok = dniOk && nacimiento != null && telefonoOk && obraSocialOk

    val claveBytes = clave.toByteArray(Charsets.UTF_8).size
    val claveOk = clave.length >= CLAVE_MIN && claveBytes <= CLAVE_MAX
    val paso3Ok = claveOk && clave == repetir && !state.cargando

    val crear: () -> Unit = {
        foco.clearFocus()
        if (nacimiento != null) {
            vm.registrarYEntrar(nombre, apellido, correo, clave, DatosPersonales(
                dni = dni, fechaNacimiento = nacimiento, telefono = telefono,
                obraSocial = nombreObraSocial, numeroAfiliado = afiliado.takeIf { nombreObraSocial != null },
            ))
        }
    }
    val avanzar: () -> Unit = { foco.clearFocus(); paso++ }

    PantallaAuth(
        altoEncabezado = 104.dp,
        formaTarjeta = RoundedCornerShape(topStart = 96.dp),
        encabezado = {
            IconButton(onClick = { if (paso > 1) paso-- else onVolver() }, enabled = !state.cargando,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text("Paso $paso de $PASOS_REGISTRO", color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
        },
        pie = {
        when (paso) {
            1 -> BotonPrincipal("Siguiente", habilitado = paso1Ok, modifier = Modifier.fillMaxWidth(), onClick = avanzar)
            2 -> BotonPrincipal("Siguiente", habilitado = paso2Ok, modifier = Modifier.fillMaxWidth(), onClick = avanzar)
            else -> BotonPrincipal(if (state.cargando) "Creando cuenta" else "Crear cuenta", cargando = state.cargando,
                habilitado = paso3Ok, modifier = Modifier.fillMaxWidth(), onClick = crear)
        }
    }) {
        TituloGrande("Crear cuenta", null)
        EnlaceCentrado("¿Ya tenés cuenta?", "Iniciá sesión", habilitado = !state.cargando, onClick = onVolver)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { paso / PASOS_REGISTRO.toFloat() },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = MediColors.Teal, trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
        Spacer(Modifier.height(24.dp))

        AnimatedContent(targetState = paso, label = "paso", transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it * dir / 3 } + fadeOut())
        }) { actual ->
            Column {
                when (actual) {
                    1 -> {
                        Titulo("Creá tu cuenta", "Contanos quién sos. La cuenta es de paciente.")
                        CampoPildora("Nombre", nombre, { nombre = it.take(80) }, accion = ImeAction.Next,
                            mayusculas = true, onAccion = { foco.moveFocus(FocusDirection.Down) })
                        Spacer(Modifier.height(16.dp))
                        CampoPildora("Apellido", apellido, { apellido = it.take(80) }, accion = ImeAction.Next,
                            mayusculas = true, onAccion = { foco.moveFocus(FocusDirection.Down) })
                        Spacer(Modifier.height(16.dp))
                        CampoPildora("Correo electrónico", correo, { correo = it.trim() },
                            teclado = KeyboardType.Email, accion = ImeAction.Done,
                            error = if (correo.isNotBlank() && !correoValido) "Revisá el formato del correo." else null,
                            onAccion = { if (paso1Ok) avanzar() })
                    }
                    2 -> {
                        Titulo("Tus datos", "Los usamos para validar tus recetas al retirar.")
                        CampoPildora("DNI", dni, { dni = it.filter(Char::isDigit).take(8) },
                            teclado = KeyboardType.Number, accion = ImeAction.Next,
                            error = if (dni.isNotEmpty() && !dniOk) "Ingresá 7 u 8 números, sin puntos." else null,
                            onAccion = { foco.clearFocus(); verCalendario = true })
                        Spacer(Modifier.height(16.dp))
                        CampoPildora("Fecha de nacimiento", nacimiento?.format(FORMATO_FECHA) ?: "", {},
                            soloLectura = true, placeholder = "dd/mm/aaaa", icono = Icons.Default.DateRange,
                            onClick = { foco.clearFocus(); verCalendario = true })
                        Spacer(Modifier.height(16.dp))
                        CampoPildora("Teléfono (opcional)", telefono,
                            { telefono = it.filter { c -> c.isDigit() || c in "+ -" }.take(20) },
                            teclado = KeyboardType.Phone, accion = ImeAction.Done,
                            error = if (!telefonoOk) "Revisá el número." else null,
                            onAccion = { foco.clearFocus() })
                        Spacer(Modifier.height(16.dp))
                        SelectorObraSocial(obraSocial) { obraSocial = it; if (it == SIN_OBRA_SOCIAL) afiliado = "" }
                        if (obraSocial == OTRA_OBRA_SOCIAL) {
                            Spacer(Modifier.height(16.dp))
                            CampoPildora("Nombre de la obra social", otraObraSocial, { otraObraSocial = it.take(80) },
                                mayusculas = true, accion = ImeAction.Next,
                                onAccion = { foco.moveFocus(FocusDirection.Down) })
                        }
                        if (obraSocial != SIN_OBRA_SOCIAL) {
                            Spacer(Modifier.height(16.dp))
                            CampoPildora("Número de afiliado (opcional)", afiliado, { afiliado = it.take(30) },
                                accion = ImeAction.Done, onAccion = { if (paso2Ok) avanzar() })
                        }
                    }
                    else -> {
                        Titulo("Elegí una contraseña", "Usá al menos $CLAVE_MIN caracteres.")
                        CampoPildora("Contraseña", clave, { clave = it }, esClave = true, accion = ImeAction.Next,
                            error = when {
                                clave.isEmpty() -> null
                                clave.length < CLAVE_MIN -> "Faltan ${CLAVE_MIN - clave.length} caracteres."
                                claveBytes > CLAVE_MAX -> "La contraseña es demasiado larga."
                                else -> null
                            },
                            onAccion = { foco.moveFocus(FocusDirection.Down) })
                        Spacer(Modifier.height(16.dp))
                        CampoPildora("Repetir contraseña", repetir, { repetir = it }, esClave = true,
                            accion = ImeAction.Done,
                            error = if (repetir.isNotEmpty() && repetir != clave) "Las contraseñas no coinciden." else null,
                            onAccion = { if (paso3Ok) crear() })
                    }
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(14.dp))
            AvisoError(state.error)
        }
    }

    if (verCalendario) {
        val utc = ZoneOffset.UTC
        val limite = hoy.atStartOfDay(utc).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = nacimiento?.atStartOfDay(utc)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = (nacimiento ?: hoy.minusYears(30)).atStartOfDay(utc).toInstant().toEpochMilli(),
            yearRange = (hoy.year - 120)..hoy.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= limite
                override fun isSelectableYear(year: Int) = year <= hoy.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { verCalendario = false },
            confirmButton = {
                TextButton(enabled = pickerState.selectedDateMillis != null, onClick = {
                    pickerState.selectedDateMillis?.let {
                        nacimientoIso = Instant.ofEpochMilli(it).atZone(utc).toLocalDate().toString()
                    }
                    verCalendario = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { verCalendario = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = pickerState, title = {
                Text("Fecha de nacimiento", modifier = Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

private const val PASOS_REGISTRO = 3
private const val SIN_OBRA_SOCIAL = "Sin obra social / particular"
private const val OTRA_OBRA_SOCIAL = "Otra"
private val OBRAS_SOCIALES = listOf(
    SIN_OBRA_SOCIAL, "OSDE", "Swiss Medical", "Galeno", "Medifé", "Omint", "Sancor Salud",
    "PAMI", "IOMA", "OSECAC", "Hospital Italiano", OTRA_OBRA_SOCIAL,
)
private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
private fun Titulo(titulo: String, subtitulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(22.dp))
}

/** Título grande y centrado de la tarjeta, como "Login" en el diseño de referencia. */
@Composable
private fun ColumnScope.TituloGrande(titulo: String, subtitulo: String?) {
    Text(titulo, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally))
    if (subtitulo != null) {
        Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    }
}

@Composable
private fun ColumnScope.EnlaceCentrado(texto: String, enlace: String, habilitado: Boolean, onClick: () -> Unit) {
    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick, enabled = habilitado, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(enlace, fontWeight = FontWeight.Bold, color = MediColors.Teal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorObraSocial(seleccion: String, onSeleccion: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        CampoPildora("Obra social o prepaga", seleccion, {}, soloLectura = true,
            icono = if (abierto) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            OBRAS_SOCIALES.forEach { opcion ->
                DropdownMenuItem(text = { Text(opcion) }, onClick = { onSeleccion(opcion); abierto = false })
            }
        }
    }
}

/* ---------- Piezas compartidas de las pantallas de acceso ---------- */

/** Fondo de las pantallas de acceso: azul noche con círculos de la marca. */
private val FondoAuth = MediColors.Noche
private val BotonOscuro = MediColors.Noche

/**
 * Encabezado oscuro con círculos superpuestos y, debajo, una tarjeta con una esquina muy
 * redondeada. El [contenido] se desplaza; el [pie] (botones) queda fijo sobre el teclado.
 */
@Composable
private fun PantallaAuth(
    altoEncabezado: Dp,
    formaTarjeta: Shape,
    encabezado: @Composable BoxScope.() -> Unit,
    pie: @Composable ColumnScope.() -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    // Los íconos claros de la barra de estado se manejan en MediLinkApp según la ruta.
    Box(Modifier.fillMaxSize().background(FondoAuth)) {
        CirculosMarca(Modifier.fillMaxWidth().fillMaxHeight(0.6f))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(altoEncabezado), content = encabezado)
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(formaTarjeta)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp).padding(top = 36.dp, bottom = 12.dp),
                    content = contenido,
                )
                Column(
                    Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                        .padding(horizontal = 32.dp).padding(top = 8.dp, bottom = 12.dp),
                    content = pie,
                )
            }
        }
    }
}

/** La cruz del logo dentro de una caja blanca redondeada, con "Medlink" debajo. */
@Composable
private fun LogoEnCaja(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.logo_cruz), contentDescription = null, modifier = Modifier.size(82.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Med") }
                withStyle(SpanStyle(color = MediColors.Aqua)) { append("link") }
            },
            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
        )
    }
}

/** Cruz del logo + "Medlink" en los colores de la marca (legible también en modo oscuro). */
@Composable
fun MarcaMediLink(modifier: Modifier = Modifier, tamanoCruz: Int = 84) {
    val oscuro = MaterialTheme.colorScheme.surface.red < 0.3f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo_cruz), contentDescription = null, modifier = Modifier.size(tamanoCruz.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (oscuro) Color(0xFF8CC4FF) else MediColors.Navy)) { append("Med") }
                withStyle(SpanStyle(color = if (oscuro) Color(0xFF5EE0CF) else MediColors.Teal)) { append("link") }
            },
            fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
private fun CampoPildora(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    esClave: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    accion: ImeAction = ImeAction.Next,
    mayusculas: Boolean = false,
    error: String? = null,
    soloLectura: Boolean = false,
    placeholder: String? = null,
    icono: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAccion: () -> Unit = {},
) {
    var verClave by rememberSaveable { mutableStateOf(false) }
    val campo = if (MaterialTheme.colorScheme.surface.red < 0.3f) MaterialTheme.colorScheme.surfaceVariant else CampoGris
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(etiqueta.uppercase(), fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Box {
        TextField(
            value = valor, onValueChange = onCambio, singleLine = true, isError = error != null,
            readOnly = soloLectura, shape = PillShape, modifier = modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it) } },
            visualTransformation = if (esClave && !verClave) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (esClave) KeyboardType.Password else teclado, imeAction = accion,
                capitalization = if (mayusculas) androidx.compose.ui.text.input.KeyboardCapitalization.Words
                    else androidx.compose.ui.text.input.KeyboardCapitalization.None,
                autoCorrectEnabled = !esClave && teclado == KeyboardType.Text),
            keyboardActions = KeyboardActions(onAny = { onAccion() }),
            trailingIcon = if (esClave) { {
                IconButton(onClick = { verClave = !verClave }) {
                    Icon(if (verClave) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (verClave) "Ocultar contraseña" else "Mostrar contraseña")
                }
            } } else icono?.let { { Icon(it, contentDescription = null) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = campo, unfocusedContainerColor = campo, errorContainerColor = campo,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            ),
        )
        // Campo de solo lectura que abre un selector (por ejemplo, el calendario).
        if (onClick != null) {
            Box(Modifier.matchParentSize().clip(PillShape).clickable(onClickLabel = etiqueta, onClick = onClick))
        }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun BotonPrincipal(
    texto: String,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    onClick: () -> Unit,
) {
    val oscuro = MaterialTheme.colorScheme.surface.red < 0.3f
    Button(onClick = onClick, enabled = habilitado, shape = RoundedCornerShape(14.dp), modifier = modifier.height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (oscuro) MaterialTheme.colorScheme.primary else BotonOscuro,
            contentColor = Color.White)) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.size(10.dp))
        }
        Text(texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
