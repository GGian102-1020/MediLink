package ar.edu.uade.medlink.reserva.dto;

/** Contract 11.3. All operational instants are formatted with milliseconds and -03:00. */
public record ReservaDTO(long id, String codigoVisible, String estado, int cantidad,
    String fechaHoraCreacion, String fechaHoraVencimiento, long segundosRestantes,
    String fechaHoraCancelacion, String fechaHoraRetiro,
    long farmaciaId, String farmaciaNombreSnapshot, String farmaciaDireccionSnapshot,
    long presentacionId, String medicamentoNombreSnapshot, String presentacionDescripcionSnapshot,
    String condicionDispensa, String usuarioNombre, String usuarioApellido,
    String origenCancelacion, String causaCancelacion, String motivoCancelacion,
    String prescripcionCodigo) { }
