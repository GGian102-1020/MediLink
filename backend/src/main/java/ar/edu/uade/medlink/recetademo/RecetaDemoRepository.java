package ar.edu.uade.medlink.recetademo;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class RecetaDemoRepository {
    private static final RowMapper<RecetaDemo> ROW = (r,n) -> new RecetaDemo(r.getLong("id_receta_demo"),
        r.getObject("lote",UUID.class),r.getString("codigo"),r.getString("codigo_verificacion"),r.getString("estado"),
        r.getObject("fecha_emision",LocalDate.class),r.getObject("fecha_vencimiento",LocalDate.class),
        r.getLong("id_presentacion"),r.getString("medicamento"),r.getString("concentracion"),
        r.getString("presentacion"),r.getInt("cantidad_autorizada"),r.getObject("id_usuario",Long.class),
        r.getObject("id_prescripcion",Long.class));
    private final JdbcTemplate db;

    RecetaDemoRepository(JdbcTemplate db) { this.db=db; }

    Optional<RecetaDemo> insertar(UUID lote, String codigo, String codigoVerificacion, LocalDate emision, LocalDate vencimiento,
                                  Instant ahora, long presentacionId, String medicamento,
                                  String concentracion, String presentacion) {
        return db.query("""
            INSERT INTO receta_demo(lote,codigo,codigo_verificacion,fecha_emision,fecha_vencimiento,fecha_creacion,
                id_presentacion,medicamento,concentracion,presentacion)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT DO NOTHING RETURNING *
            """,ROW,lote,codigo,codigoVerificacion,emision,vencimiento,Timestamp.from(ahora),
            presentacionId,medicamento,concentracion,presentacion).stream().findFirst();
    }

    Optional<RecetaDemo> porId(long id) {
        return db.query("SELECT * FROM receta_demo WHERE id_receta_demo=?",ROW,id).stream().findFirst();
    }

    List<RecetaDemo> porLote(UUID lote) {
        return db.query("SELECT * FROM receta_demo WHERE lote=? ORDER BY id_receta_demo",ROW,lote);
    }

    Optional<RecetaDemo> bloquear(String codigo) {
        return db.query("SELECT * FROM receta_demo WHERE codigo=? FOR UPDATE",ROW,codigo).stream().findFirst();
    }

    Optional<Long> bloquearUsuario(long cuentaId) {
        return db.query("SELECT id_usuario FROM usuario WHERE id_cuenta=? FOR UPDATE",
            (r,n)->r.getLong(1),cuentaId).stream().findFirst();
    }

    void asignar(long id, long usuario, long prescripcion, Instant ahora) {
        int filas=db.update("""
            UPDATE receta_demo SET estado='ASIGNADA', id_usuario=?, id_prescripcion=?, fecha_asignacion=?
            WHERE id_receta_demo=? AND estado='DISPONIBLE'
            """,usuario,prescripcion,Timestamp.from(ahora),id);
        if (filas!=1) throw new IllegalStateException("Estado inesperado al asignar receta demo bloqueada");
    }
}
