package ar.edu.uade.medlink.auditoria;

import ar.edu.uade.medlink.common.error.ApiException;
import java.time.OffsetDateTime;
import java.util.Set;

/** Validated query parameters, using the domains in specification 5.2.1. */
public record AuditoriaFiltro(Long usuarioId, Long actorCuentaId, String rol, String accion,
    String tipoEntidad, Long entidadId, OffsetDateTime desde, OffsetDateTime hasta) {
    private static final Set<String> ROLES=Set.of("USUARIO","OPERADOR","ADMINISTRADOR","SISTEMA");
    private static final Set<String> ENTIDADES=Set.of("CUENTA","FARMACIA","MEDICAMENTO","PRESENTACION",
        "STOCK","RESERVA","PRESCRIPCION","OPERADOR","SUSPENSION");
    private static final Set<String> ACCIONES=Set.of("AJUSTAR_STOCK","CORREGIR_STOCK_INCONSISTENTE",
        "CANCELAR_RESERVA_OPERATIVA","CONFIRMAR_RETIRO","CREAR_FARMACIA","EDITAR_FARMACIA",
        "DESACTIVAR_FARMACIA","REACTIVAR_FARMACIA","CREAR_MEDICAMENTO","EDITAR_MEDICAMENTO",
        "DESACTIVAR_MEDICAMENTO","REACTIVAR_MEDICAMENTO","CREAR_PRESENTACION",
        "DESACTIVAR_PRESENTACION","REACTIVAR_PRESENTACION","CREAR_OPERADOR","EDITAR_OPERADOR",
        "DESHABILITAR_CUENTA","REACTIVAR_CUENTA","CREAR_PRESCRIPCION","ANULAR_PRESCRIPCION",
        "CAMBIAR_CONTRASENA","CREAR_SUSPENSION");

    public AuditoriaFiltro {
        positive(usuarioId); positive(actorCuentaId); positive(entidadId);
        rol=domain(rol,ROLES); accion=domain(accion,ACCIONES); tipoEntidad=domain(tipoEntidad,ENTIDADES);
        precision(desde); precision(hasta);
        if (desde!=null && hasta!=null && !desde.isBefore(hasta))
            throw ApiException.validation("desde debe ser anterior a hasta.");
    }
    private static void positive(Long id) {
        if (id!=null && id<=0) throw ApiException.validation("Los identificadores deben ser positivos.");
    }
    private static String domain(String value, Set<String> allowed) {
        if (value==null) return null;
        String trimmed=value.strip();
        if (!allowed.contains(trimmed)) throw ApiException.validation("Filtro de auditoría inválido.");
        return trimmed;
    }
    private static void precision(OffsetDateTime value) {
        if (value!=null && (value.getNano()%1_000_000!=0 || value.getYear()<1 || value.getYear()>9999))
            throw ApiException.validation("El instante debe tener precisión de milisegundos y año entre 1 y 9999.");
    }
}
