package ar.edu.uade.medlink.chat;

import java.time.OffsetDateTime;

public final class ChatDtos {
    private ChatDtos() { }

    /** Mensaje de una conversación. autor: FARMACIA o USUARIO. */
    public record MensajeDTO(long id, String autor, String texto, OffsetDateTime fechaHora, boolean leido) { }

    /**
     * Conversación vista desde la farmacia: un paciente con reservas en ella.
     * medicamento/codigoReserva/estadoReserva describen la reserva de referencia (la abierta más
     * reciente o, si no hay, la última). abierta=false: solo lectura.
     */
    public record ConversacionFarmaciaDTO(long usuarioId, String nombre, String apellido,
                                          String ultimoMensaje, String ultimoAutor, OffsetDateTime ultimaActividad,
                                          long noLeidos, String medicamento, String codigoReserva,
                                          String estadoReserva, boolean abierta, OffsetDateTime fechaHoraBorrado) { }

    /** Conversación vista desde el paciente: una farmacia donde reservó. Mismos campos de reserva. */
    public record ConversacionUsuarioDTO(long farmaciaId, String nombre, String direccion,
                                         String ultimoMensaje, String ultimoAutor, OffsetDateTime ultimaActividad,
                                         long noLeidos, String medicamento, String codigoReserva,
                                         String estadoReserva, boolean abierta, OffsetDateTime fechaHoraBorrado) { }

    /**
     * Estado de una conversación: si se puede escribir y a qué reserva corresponde.
     * fechaHoraBorrado: cuándo se borra solo el historial de una conversación cerrada (null si está abierta).
     */
    public record EstadoConversacionDTO(boolean abierta, String medicamento, String codigoReserva, String estadoReserva,
                                        OffsetDateTime fechaHoraBorrado) { }

    public record EnviarMensajeRequest(String texto) { }

    public record NoLeidosDTO(long total) { }
}
