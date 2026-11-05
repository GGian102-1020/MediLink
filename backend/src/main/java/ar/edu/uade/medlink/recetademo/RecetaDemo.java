package ar.edu.uade.medlink.recetademo;

import java.time.LocalDate;
import java.util.UUID;

/** Snapshot of the printable fields, plus the internal claim state. */
record RecetaDemo(long id, UUID lote, String codigo, String codigoVerificacion, String estado, LocalDate fechaEmision,
                  LocalDate fechaVencimiento, long presentacionId, String medicamento,
                  String concentracion, String presentacion, int cantidadAutorizada,
                  Long usuarioId, Long prescripcionId) { }
