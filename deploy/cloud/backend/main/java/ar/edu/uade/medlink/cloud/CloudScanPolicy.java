package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor;
import ar.edu.uade.medlink.security.CurrentAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Empty configuration denies everyone; public registration never grants OCR access. */
@Component
public class CloudScanPolicy {
    private final JdbcTemplate db;
    private final PrescriptionImageExtractor extractor;
    private final boolean enabled;
    private final Long allowed;
    public CloudScanPolicy(JdbcTemplate db, PrescriptionImageExtractor extractor,
        @Value("${medlink.recetas-demo.scan.enabled:false}") boolean enabled,
        @Value("${medlink.cloud.scan-allowed-account-ids:}") String accounts) {
        this.db=db; this.extractor=extractor; this.enabled=enabled;
        String value=accounts.strip();
        if (!value.isEmpty() && !value.matches("[1-9][0-9]{0,17}"))
            throw new IllegalArgumentException("Configure exactly one positive account ID, or leave scan access empty");
        allowed=value.isEmpty()?null:Long.valueOf(value);
    }
    public void authorize(CurrentAccount actor) {
        if (actor==null) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","Se requiere una sesión válida.");
        if (!"USUARIO".equals(actor.rol())) throw ApiException.forbidden();
        boolean eligible=Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM cuenta c JOIN usuario u USING(id_cuenta)
                WHERE c.id_cuenta=? AND c.rol='USUARIO' AND c.estado='ACTIVA' AND c.token_version=?
                AND NOT EXISTS(SELECT 1 FROM sesion_revocada WHERE jti=?)
                AND NOT EXISTS(SELECT 1 FROM suspension s WHERE s.id_usuario=u.id_usuario
                    AND s.fecha_hora_inicio<=CURRENT_TIMESTAMP AND s.fecha_hora_fin>CURRENT_TIMESTAMP))
            """,Boolean.class,actor.id(),actor.tokenVersion(),actor.jti()));
        if (!eligible) throw ApiException.forbidden();
        if (!enabled || !extractor.disponible())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ESCANEO_NO_CONFIGURADO","El análisis automático está desactivado.");
        if (allowed==null || allowed.longValue()!=actor.id())
            throw new ApiException(HttpStatus.FORBIDDEN,"ESCANEO_NO_AUTORIZADO","El análisis está limitado a la cuenta de prueba autorizada.");
    }
    public boolean enabledFor(CurrentAccount actor) {
        try { authorize(actor); return true; } catch (ApiException ex) { return false; }
    }
}
