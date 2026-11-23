package ar.edu.uade.medlink.reserva.dto;

/** Contract 11.3. All operational instants are formatted with milliseconds and -03:00.
 * {@code pinRetiro} is only populated for the owning patient (USUARIO view) while the
 * reservation is ACTIVA; operators and administrators never receive it.
 * {@code estado} PENDIENTE: BAJO_RECETA waiting for the pharmacy to confirm the prescription
 * (no PIN yet; {@code segundosRestantes} is the review window).
 */
public record ReservaDTO(long id, String codigoVisible, String estado, int cantidad,
    String fechaHoraCreacion, String fechaHoraVencimiento, long segundosRestantes,
    String fechaHoraCancelacion, String fechaHoraRetiro,
    long farmaciaId, String farmaciaNombreSnapshot, String farmaciaDireccionSnapshot,
    long presentacionId, String medicamentoNombreSnapshot, String presentacionDescripcionSnapshot,
    String condicionDispensa, String usuarioNombre, String usuarioApellido,
    String origenCancelacion, String causaCancelacion, String motivoCancelacion,
    String prescripcionCodigo, String pinRetiro,
    /** true si la reserva BAJO_RECETA tiene una receta adjunta (PDF/imagen) que la farmacia puede ver. */
    boolean recetaAdjunta,
    /** Momento en que la farmacia confirmó la receta; null si no requiere confirmación o sigue PENDIENTE. */
    String fechaHoraConfirmacion) { }
