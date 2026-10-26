package ar.edu.uade.medlink.auditoria;

import java.util.Map;

/** REST contract 11.3: rol is the public name of auditoria.actor_rol. */
public record AuditoriaDTO(long id, String fechaHora, Long actorCuentaId, String actorNombre,
    String rol, String accion, String tipoEntidad, long entidadId, Long usuarioRelacionadoId,
    String motivo, String correlationId, Map<String, Object> resumenAnterior,
    Map<String, Object> resumenPosterior) { }
