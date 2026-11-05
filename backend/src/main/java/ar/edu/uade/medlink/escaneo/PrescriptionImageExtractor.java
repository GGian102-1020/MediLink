package ar.edu.uade.medlink.escaneo;

import java.util.List;

/** Extraction only. The returned values are untrusted and confer no permissions. */
public interface PrescriptionImageExtractor {
    boolean disponible();
    Lectura extraer(Imagen imagen);

    record Imagen(byte[] bytes, String tipo) { }
    enum Estado { COMPLETA, INCONCLUSA }
    record Documento(String titulo, String leyenda, String profesional, String institucion,
                     String paciente, String indicaciones) { }
    record Medicamento(String medicamento, String concentracion, String presentacion, Integer cantidadAutorizada) { }
    record Lectura(String codigoReceta, String codigoVerificacion, String fechaEmision, String fechaVencimiento, Documento documento,
                   List<Medicamento> medicamentos, List<String> camposFaltantes, Estado estado) { }
}
