package ar.edu.uade.medilink.android.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.medilink.android.data.AdjuntoReceta
import ar.edu.uade.medilink.android.data.PrescripcionDto
import ar.edu.uade.medilink.android.data.TIPOS_RECETA
import ar.edu.uade.medilink.android.data.VinculacionRecetaDto
import ar.edu.uade.medilink.android.ui.viewmodel.AccionReceta
import ar.edu.uade.medilink.android.ui.viewmodel.MisRecetasVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MisRecetasScreen(vm: MisRecetasVm) {
    val estado by vm.state.collectAsStateWithLifecycle()
    var codigo by rememberSaveable { mutableStateOf("") }
    var mostrarSelector by rememberSaveable { mutableStateOf(false) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::finalizarCaptura)
    val documentos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::finalizarArchivos)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Encabezado("Mis recetas", "Adjuntá o vinculá recetas ficticias de MediLink.")
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { mostrarSelector = true }, enabled = !estado.ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Adjuntar receta") }
                    Text("Foto o PDF de una página (JPG, PNG o WebP), hasta 6 MB.",
                        style = MaterialTheme.typography.bodySmall)
                    estado.adjunto?.let { adjunto ->
                        Text(if (estado.foto) "Fotografía capturada" else adjunto.nombre,
                            style = MaterialTheme.typography.titleSmall)
                        if (adjunto.esImagen) VistaPreviaReceta(adjunto)
                        TextButton(onClick = vm::quitarAdjunto, enabled = !estado.ocupado) { Text("Quitar adjunto") }
                        Button(onClick = vm::escanear, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) {
                            Text("Analizar y verificar receta con IA")
                        }
                    }
                    estado.accion?.takeUnless { it == AccionReceta.VINCULANDO }?.let { accion ->
                        ProgresoReceta(when (accion) {
                            AccionReceta.PREPARANDO -> "Preparando el documento…"
                            AccionReceta.ENVIANDO -> "Enviando archivo…"
                            else -> "Archivo enviado. Analizando y verificando receta…"
                        })
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeccionTitulo("Vincular por código")
                    Text("Si tu receta tiene un código (por ejemplo MLD-…), ingresalo para vincularla a tu cuenta.",
                        style = MaterialTheme.typography.bodySmall)
                    CampoTexto(
                        value = codigo, onValueChange = { codigo = it }, enabled = !estado.ocupado,
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Código de receta") }, placeholder = { Text("MLD-...") },
                    )
                    OutlinedButton(
                        onClick = { vm.vincular(codigo) }, enabled = !estado.ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Vincular receta") }
                    if (estado.accion == AccionReceta.VINCULANDO) ProgresoReceta("Vinculando receta…")
                }
            }
        }
        estado.error?.let { error -> item { AvisoError(error) } }
        estado.resultado?.let { resultado ->
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mensajeResultadoReceta(resultado), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.titleMedium)
                        DatosPrescripcion(resultado.prescripcion, estado.medicamentos)
                    }
                }
            }
        }
        item {
            SeccionTitulo("Mis prescripciones")
            OutlinedButton(onClick = vm::actualizar, enabled = !estado.cargando) { Text("Actualizar listado") }
            AvisoError(estado.errorLista)
            if (estado.cargando) IndicadorCargando()
            if (!estado.cargando && estado.errorLista == null && estado.lista.isEmpty()) {
                VacioEstado("Todavía no tenés recetas", "Las prescripciones vinculadas aparecen acá.")
            }
        }
        items(estado.lista, key = { it.id }) { receta ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatosPrescripcion(receta, estado.medicamentos)
                    TextButton(onClick = { vm.abrirDetalle(receta.id) }) { Text("Ver detalle") }
                }
            }
        }
        if (estado.hayMas) {
            item {
                OutlinedButton(onClick = vm::mas, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
                    Text("Cargar más recetas")
                }
            }
        }
    }
    if (mostrarSelector) {
        ModalBottomSheet(onDismissRequest = { mostrarSelector = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                SeccionTitulo("Adjuntar receta")
                TextButton(onClick = {
                    mostrarSelector = false
                    try {
                        camara.launch(vm.abrirCamara())
                    } catch (_: Exception) {
                        vm.falloSelector("No se pudo abrir la cámara. Podés elegir una foto desde Archivos.")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Cámara") }
                TextButton(onClick = {
                    mostrarSelector = false
                    try {
                        vm.abrirArchivos()
                        documentos.launch(TIPOS_RECETA)
                    } catch (_: Exception) {
                        vm.falloSelector("No se pudo abrir el selector de archivos.")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Archivos o galería") }
            }
        }
    }
    if (estado.cargandoDetalle || estado.detalle != null || estado.errorDetalle != null) {
        AlertDialog(
            onDismissRequest = vm::cerrarDetalle,
            title = { Text("Detalle de la receta") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    if (estado.cargandoDetalle) IndicadorCargando()
                    AvisoError(estado.errorDetalle)
                    estado.detalle?.let { DatosPrescripcion(it, estado.medicamentos) }
                }
            },
            confirmButton = { TextButton(onClick = vm::cerrarDetalle) { Text("Cerrar") } },
        )
    }
}

internal fun mensajeResultadoReceta(resultado: VinculacionRecetaDto): String = when {
    !resultado.nueva -> "Esta receta ya estaba vinculada a tu cuenta."
    resultado.codigo == "RECETA_VERIFICADA" -> "Receta verificada y vinculada correctamente."
    resultado.codigo == "RECETA_VERIFICADA_LEGADA" -> "Receta de emisión anterior verificada y vinculada."
    else -> "Receta vinculada correctamente."
}

@Composable
private fun ProgresoReceta(mensaje: String) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(mensaje, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DatosPrescripcion(receta: PrescripcionDto, medicamentos: Map<Long, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SelectionContainer {
            Text(receta.codigoExterno, style = MaterialTheme.typography.titleSmall)
        }
        EstadoPildora(receta.estado)
        DatoFila("Emisión", fechaLegible(receta.fechaEmision))
        DatoFila("Vencimiento", fechaLegible(receta.fechaVencimiento))
        receta.items.forEach { item ->
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            medicamentos[item.presentacionId]?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(item.descripcionPresentacion, style = MaterialTheme.typography.bodyMedium)
            DatoFila("Autorizada", item.cantidadAutorizada.toString())
            DatoFila("Comprometida", item.cantidadComprometida.toString())
            DatoFila("Utilizada", item.cantidadUtilizada.toString())
            DatoFila("Disponible", item.cantidadDisponible.toString())
        }
    }
}

@Composable
private fun VistaPreviaReceta(adjunto: AdjuntoReceta) {
    val imagen by produceState<ImageBitmap?>(null, adjunto.archivo) {
        value = withContext(Dispatchers.IO) {
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(adjunto.archivo.path, opciones)
            val lado = maxOf(opciones.outHeight, opciones.outWidth)
            if (lado <= 0) return@withContext null
            var muestra = 1
            while (lado / muestra > 900) muestra *= 2
            opciones.inJustDecodeBounds = false
            opciones.inSampleSize = muestra
            BitmapFactory.decodeFile(adjunto.archivo.path, opciones)?.asImageBitmap()
        }
    }
    imagen?.let { Image(it, "Vista previa del documento", Modifier.fillMaxWidth().heightIn(max = 200.dp),
        contentScale = ContentScale.Fit) }
}
