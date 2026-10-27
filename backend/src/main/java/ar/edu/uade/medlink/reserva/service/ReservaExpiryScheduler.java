package ar.edu.uade.medlink.reserva.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** <=60 seconds between runs; individual transactions allow safe retry on errors. */
@Component
// Test environments can disable the background worker to exercise races deterministically.
// Production remains enabled by default.
@ConditionalOnProperty(name="medlink.reservas.scheduler.enabled", havingValue="true", matchIfMissing=true)
public class ReservaExpiryScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(ReservaExpiryScheduler.class);
    private final JdbcTemplate db;
    private final ReservaService service;
    private final Clock clock;
    public ReservaExpiryScheduler(JdbcTemplate db,ReservaService service,Clock clock) {
        this.db=db; this.service=service; this.clock=clock;
    }
    @Scheduled(fixedRate=30000)
    public void process() {
        Instant t=clock.instant();
        // Re-read each candidate under canonical locks in the transactional worker.
        List<Long> ids=db.query("SELECT id_reserva FROM reserva WHERE estado_persistido='ACTIVA' " +
            "AND fecha_hora_vencimiento<=? ORDER BY fecha_hora_vencimiento,id_reserva LIMIT 500",
            (rs,n)->rs.getLong(1),Timestamp.from(t));
        for (Long id:ids) {
            try { service.materializar(id,null); }
            catch (RuntimeException ex) {
                // Error details might include SQL or personal data. Only type and reservation ID.
                LOG.warn("Vencimiento pendiente: reservaId={} tipo={}",id,ex.getClass().getSimpleName());
            }
        }
    }
}
