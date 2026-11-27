package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.common.error.ApiException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The attempt commits BEFORE OCR. Failure/restart cannot refund it. Lease spans instances and UTC days. */
@Service
public class CloudScanBudget {
    private final JdbcTemplate db;
    private final int limit;
    public CloudScanBudget(JdbcTemplate db,@Value("${medlink.cloud.scan-daily-limit:2}") int limit) {
        if (limit<1 || limit>2) throw new IllegalArgumentException("Initial cloud scan budget must be 1 or 2");
        this.db=db; this.limit=limit;
    }
    @Transactional
    public UUID acquire() {
        UUID owner=UUID.randomUUID();
        if (db.update("UPDATE cloud_scan_lease SET owner=?,expires_at=clock_timestamp()+INTERVAL '15 minutes' "
            +"WHERE singleton=TRUE AND (owner IS NULL OR expires_at<=clock_timestamp())",owner)!=1)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"ESCANEO_LIMITE","Hay un análisis en curso. Probá más tarde.");
        var claimed=db.query("INSERT INTO cloud_scan_budget(usage_date,attempts) VALUES ((clock_timestamp() AT TIME ZONE 'UTC')::date,1) "
            +"ON CONFLICT(usage_date) DO UPDATE SET attempts=cloud_scan_budget.attempts+1 "
            +"WHERE cloud_scan_budget.attempts < ? RETURNING attempts",(r,n)->r.getInt(1),limit);
        if (claimed.isEmpty()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"ESCANEO_LIMITE_DIARIO","Se alcanzó el límite diario de análisis.");
        return owner;
    }
    public void renew(UUID owner) {
        if (db.update("UPDATE cloud_scan_lease SET expires_at=clock_timestamp()+INTERVAL '15 minutes' "
            +"WHERE singleton=TRUE AND owner=? AND expires_at>clock_timestamp()",owner)!=1)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ESCANEO_PERMISO_VENCIDO","El permiso de análisis venció. No se envió el documento.");
    }
    public void release(UUID owner) {
        db.update("UPDATE cloud_scan_lease SET owner=NULL,expires_at=NULL WHERE singleton=TRUE AND owner=?",owner);
    }
}
