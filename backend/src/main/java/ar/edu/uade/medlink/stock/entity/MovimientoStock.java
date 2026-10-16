package ar.edu.uade.medlink.stock.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Immutable @Table(name="movimiento_stock")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MovimientoStock {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_movimiento")
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_stock",updatable=false)
    private Stock stock;
    @Column(name="id_reserva",updatable=false)
    private Long reservaId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false,columnDefinition="tipo_movimiento_stock",updatable=false)
    private TipoMovimientoStock tipo;
    @Column(name="cantidad_anterior",nullable=false,updatable=false)
    private int cantidadAnterior;
    @Column(name="cantidad_nueva",nullable=false,updatable=false)
    private int cantidadNueva;
    @Column(nullable=false,updatable=false)
    private int delta;
    @Column(name="fecha_hora",nullable=false,updatable=false,columnDefinition="timestamptz(3)")
    private Instant fechaHora;
    @Column(name="actor_cuenta_id",updatable=false)
    private Long actorCuentaId;
    @Column(length=500,updatable=false)
    private String motivo;

    public MovimientoStock(Stock stock, Long reservaId, TipoMovimientoStock tipo, int anterior,
                            int nueva, Instant t, Long actorCuentaId, String motivo) {
        this.stock=stock; this.reservaId=reservaId; this.tipo=tipo; this.cantidadAnterior=anterior;
        this.cantidadNueva=nueva; this.delta=nueva-anterior; this.fechaHora=t;
        this.actorCuentaId=actorCuentaId; this.motivo=motivo;
    }
}
