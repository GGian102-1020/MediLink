package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.auth.AuthDtos.SuspensionDTO;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.PageRequests;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only historical view of existing, immutable suspension intervals.
 * The policy is owned by SuspensionPlanner/ReservationClosureService (RN-31..36),
 * never by a second CRUD model. This service does not create or lift suspensions.
 */
@Service
public class SuspensionConsultaService {
    private static final Set<String> SORTS = Set.of("fechaHoraInicio", "id");
    private static final Sort DEFAULT_SORT = Sort.by(
        Sort.Order.desc("fechaHoraInicio"), Sort.Order.desc("id"));
    private static final Map<String, String> COLUMNS = Map.of(
        "fechaHoraInicio", "s.fecha_hora_inicio", "id", "s.id_suspension");
    private final JdbcTemplate db;
    private final Clock clock;

    public SuspensionConsultaService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageDTO<SuspensionDTO> listar(long usuarioId, Boolean vigente,
        OffsetDateTime desde, OffsetDateTime hasta, int page, int size,
        List<String> sort, CurrentAccount actor) {
        if (actor == null || !"ADMINISTRADOR".equals(actor.rol())) throw ApiException.forbidden();
        if (usuarioId <= 0) throw ApiException.validation("El identificador de Usuario debe ser positivo.");
        checkPrecision(desde);
        checkPrecision(hasta);
        if (desde != null && hasta != null && !desde.isBefore(hasta))
            throw ApiException.validation("desde debe ser anterior a hasta.");
        var pageable = PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT);
        Instant t = clock.instant(); // Single functional decision instant (CV-05).

        // Do not expose a query for arbitrary, non-existent Usuario identifiers.
        if (!Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM usuario WHERE id_usuario=?)", Boolean.class, usuarioId)))
            throw ApiException.notFound();

        StringBuilder where = new StringBuilder(" WHERE s.id_usuario=?");
        List<Object> args = new ArrayList<>();
        args.add(usuarioId);
        if (vigente != null) {
            if (vigente) {
                where.append(" AND s.fecha_hora_inicio<=? AND s.fecha_hora_fin>?");
            } else {
                where.append(" AND (s.fecha_hora_inicio>? OR s.fecha_hora_fin<=?)");
            }
            args.add(Timestamp.from(t));
            args.add(Timestamp.from(t));
        }
        // [desde, hasta) filters on fechaHoraInicio as specified in 11.8.
        if (desde != null) {
            where.append(" AND s.fecha_hora_inicio>=?");
            args.add(Timestamp.from(desde.toInstant()));
        }
        if (hasta != null) {
            where.append(" AND s.fecha_hora_inicio<?");
            args.add(Timestamp.from(hasta.toInstant()));
        }
        long total = db.queryForObject("SELECT COUNT(*) FROM suspension s" + where,
            Long.class, args.toArray());
        String order = String.join(",", pageable.getSort().stream()
            .map(o -> COLUMNS.get(o.getProperty()) + " " + o.getDirection().name()).toList());
        args.add(size);
        args.add(pageable.getOffset());
        var rows = db.query("SELECT s.id_suspension,s.fecha_hora_inicio,s.fecha_hora_fin " +
            "FROM suspension s" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            (rs, row) -> {
                Instant start = rs.getObject("fecha_hora_inicio", OffsetDateTime.class).toInstant();
                Instant end = rs.getObject("fecha_hora_fin", OffsetDateTime.class).toInstant();
                return new SuspensionDTO(rs.getLong("id_suspension"), ApiClock.argentina(start),
                    ApiClock.argentina(end), !t.isBefore(start) && t.isBefore(end),
                    "INCUMPLIMIENTOS_REITERADOS");
            }, args.toArray());
        return PageDTO.from(rows, page, size, total);
    }

    private static void checkPrecision(OffsetDateTime time) {
        if (time != null && time.getNano() % 1_000_000 != 0)
            throw ApiException.validation("El instante debe tener precisión de milisegundos.");
    }
}
