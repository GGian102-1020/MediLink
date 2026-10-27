package ar.edu.uade.medlink.suspension.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Historical interval. Its dates determine vigencia; no editable status exists (RN-31..36). */
@Entity
@Immutable
@Table(name="suspension")
public class Suspension {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_suspension")
    private Long id;

    // Usuario does not have a JPA entity; the schema already enforces its foreign key.
    @Column(name="id_usuario", nullable=false, updatable=false)
    private long usuarioId;

    @Column(name="fecha_hora_inicio", nullable=false, updatable=false, columnDefinition="timestamptz(3)")
    private Instant fechaHoraInicio;

    @Column(name="fecha_hora_fin", nullable=false, updatable=false, columnDefinition="timestamptz(3)")
    private Instant fechaHoraFin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="motivo", nullable=false, updatable=false, columnDefinition="motivo_suspension")
    private MotivoSuspension motivo;

    protected Suspension() { } // Only Hibernate reads these immutable historical records.

    public Long getId() { return id; }
    public long getUsuarioId() { return usuarioId; }
    public Instant getFechaHoraInicio() { return fechaHoraInicio; }
    public Instant getFechaHoraFin() { return fechaHoraFin; }
    public MotivoSuspension getMotivo() { return motivo; }

    public boolean estaVigente(Instant instante) {
        return !instante.isBefore(fechaHoraInicio) && instante.isBefore(fechaHoraFin);
    }
}
