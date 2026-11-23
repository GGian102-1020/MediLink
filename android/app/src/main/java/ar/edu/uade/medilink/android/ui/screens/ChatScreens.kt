package ar.edu.uade.medilink.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.uade.medilink.android.data.ConversacionChatDto
import ar.edu.uade.medilink.android.data.EstadoChatDto
import ar.edu.uade.medilink.android.data.MediLinkRepository
import ar.edu.uade.medilink.android.data.MensajeChatDto
import ar.edu.uade.medilink.android.data.mensajeDeError
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MENSAJES_MS = 4_000L
private const val POLL_LISTA_MS = 10_000L
private const val MAX_TEXTO = 1000
private val ZONA_AR: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun horaDe(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZONA_AR).format(HORA) }.getOrDefault("")

/** Lista de farmacias con las que el paciente puede chatear (donde tiene o tuvo reservas). */
@Composable
fun ConversacionesScreen(repository: MediLinkRepository, onAbrir: (ConversacionChatDto) -> Unit) {
    var lista by remember { mutableStateOf<List<ConversacionChatDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository.session.token) {
        while (true) {
            try { lista = repository.chats(); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (lista == null) error = mensajeDeError(e) }
            delay(POLL_LISTA_MS)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Encabezado("Mensajes", "Hablá con las farmacias donde reservaste.") }
        item { AvisoError(error) }
        val items = lista
        when {
            items == null && error == null -> item { IndicadorCargando() }
            items != null && items.isEmpty() -> item {
                VacioEstado("Sin conversaciones", "Cuando reserves en una farmacia vas a poder escribirle desde acá.")
            }
            items != null -> items(items, key = { it.farmaciaId }) { c -> FilaConversacion(c) { onAbrir(c) } }
        }
    }
}

@Composable
private fun FilaConversacion(c: ConversacionChatDto, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.medicamento ?: c.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(c.nombre.takeIf { c.medicamento != null }, c.codigoReserva?.let { "Reserva $it" },
                    "Cerrada".takeIf { !c.abierta }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary)
                val resumen = c.ultimoMensaje?.let { (if (c.ultimoAutor == "USUARIO") "Vos: " else "") + it }
                    ?: c.direccion
                Text(resumen, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (c.noLeidos > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (c.noLeidos > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.noLeidos > 0) InsigniaNoLeidos(c.noLeidos)
        }
    }
}

@Composable
fun InsigniaNoLeidos(n: Long) {
    Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else n.toString(), color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Conversación con una farmacia: historial + envío, con polling liviano de mensajes nuevos. */
@Composable
fun ChatScreen(repository: MediLinkRepository, farmaciaId: Long, nombreFarmacia: String) {
    var mensajes by remember(farmaciaId) { mutableStateOf<List<MensajeChatDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var texto by rememberSaveable { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var errorEnvio by remember { mutableStateOf<String?>(null) }
    var estado by remember(farmaciaId) { mutableStateOf<EstadoChatDto?>(null) }
    val scope = rememberCoroutineScope()
    val listaState = rememberLazyListState()

    LaunchedEffect(farmaciaId, repository.session.token) {
        while (true) {
            // Se trae la conversación completa (máx. 200): así también se actualiza el "Leído".
            try { mensajes = repository.mensajesChat(farmaciaId); error = null } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (mensajes == null) error = mensajeDeError(e) }
            // Si la reserva termina mientras la conversación está abierta, el envío se bloquea solo.
            try { estado = repository.estadoChat(farmaciaId) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(POLL_MENSAJES_MS)
        }
    }
    LaunchedEffect(mensajes?.size) { mensajes?.let { if (it.isNotEmpty()) listaState.animateScrollToItem(it.lastIndex) } }

    val enviar: () -> Unit = {
        val limpio = texto.trim()
        if (limpio.isNotEmpty() && !enviando) {
            enviando = true; errorEnvio = null
            scope.launch {
                try {
                    val m = repository.enviarMensaje(farmaciaId, limpio)
                    mensajes = (mensajes ?: emptyList()).let { lista -> if (lista.any { it.id == m.id }) lista else lista + m }
                    texto = ""
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    errorEnvio = mensajeDeError(e)
                    runCatching { estado = repository.estadoChat(farmaciaId) }
                }
                finally { enviando = false }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(estado?.medicamento ?: nombreFarmacia, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(nombreFarmacia, estado?.codigoReserva?.let { "Reserva $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val lista = mensajes
            when {
                lista == null && error != null -> Box(Modifier.padding(16.dp)) { AvisoError(error) }
                lista == null -> IndicadorCargando()
                lista.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    VacioEstado("Escribile a la farmacia", "Podés consultar por tu reserva, el horario de retiro o tu receta.")
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listaState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lista, key = { it.id }) { m -> Burbuja(m) }
                }
            }
        }
        val cerrada = estado?.abierta == false
        if (!cerrada) errorEnvio?.let { Box(Modifier.padding(horizontal = 16.dp)) { AvisoError(it) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (cerrada) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
            Text("Tu reserva terminó, así que esta conversación quedó cerrada. Ya no se pueden enviar mensajes." +
                (estado?.fechaHoraBorrado?.let { " El historial se borra automáticamente el ${fechaLegible(it)}." } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it.take(MAX_TEXTO) },
                modifier = Modifier.weight(1f), placeholder = { Text("Escribí un mensaje…") },
                maxLines = 4, shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface),
            )
            FilledIconButton(onClick = enviar, enabled = texto.isNotBlank() && !enviando,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

@Composable
private fun Burbuja(m: MensajeChatDto) {
    val propio = m.autor == "USUARIO"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (propio) 18.dp else 4.dp, bottomEnd = if (propio) 4.dp else 18.dp),
            color = if (propio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (propio) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.texto, style = MaterialTheme.typography.bodyLarge,
                    color = if (propio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                val estado = if (propio) (if (m.leido) " · Leído" else " · Enviado") else ""
                Text(horaDe(m.fechaHora) + estado, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                    color = if (propio) MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
