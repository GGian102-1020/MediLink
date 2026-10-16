package ar.edu.uade.medlink.stock.entity;

import ar.edu.uade.medlink.common.error.ApiException;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="stock", uniqueConstraints=@UniqueConstraint(name="uq_stock_farmacia_presentacion",columnNames={"id_farmacia","id_presentacion"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Stock {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_stock")
    private Long id;
    @Column(name="id_farmacia",nullable=false,updatable=false)
    private long farmaciaId;
    @Column(name="id_presentacion",nullable=false,updatable=false)
    private long presentacionId;
    @Column(name="cantidad_disponible",nullable=false)
    private int cantidadDisponible;
    @Column(name="fecha_hora_actualizacion",nullable=false,columnDefinition="timestamptz(3)")
    private Instant fechaHoraActualizacion;

    public Stock(long farmaciaId, long presentacionId, Instant t) {
        this.farmaciaId=farmaciaId; this.presentacionId=presentacionId; this.fechaHoraActualizacion=t;
    }
    public void establecerCantidad(int cantidad, Instant t) {
        if (cantidad<0) throw ApiException.validation("La cantidad disponible no puede ser negativa.");
        this.cantidadDisponible=cantidad; this.fechaHoraActualizacion=t;
    }
}
