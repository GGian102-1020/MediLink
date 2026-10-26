package ar.edu.uade.medlink.prescripcion.entity;

import ar.edu.uade.medlink.presentacion.entity.PresentacionMedicamento;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Simulated external prescription (RN-11). Structural data is immutable in the schema. */
@Entity
@Table(name="prescripcion")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Prescripcion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_prescripcion")
    private Long id;
    @Column(name="codigo_externo",nullable=false,length=80,updatable=false)
    private String codigoExterno;
    @Column(name="id_usuario",nullable=false,updatable=false)
    private long usuarioId;
    @Column(name="fecha_emision",nullable=false,updatable=false)
    private LocalDate fechaEmision;
    @Column(name="fecha_vencimiento",nullable=false,updatable=false)
    private LocalDate fechaVencimiento;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="estado",nullable=false,columnDefinition="estado_prescripcion")
    private EstadoPrescripcion estado=EstadoPrescripcion.ACTIVA;
    @Column(name="fecha_hora_creacion",nullable=false,updatable=false,columnDefinition="timestamptz(3)")
    private Instant fechaHoraCreacion;
    @OneToMany(mappedBy="prescripcion",cascade=CascadeType.PERSIST)
    @OrderBy("id")
    private List<ItemPrescripcion> items=new ArrayList<>();

    public Prescripcion(String codigoExterno, long usuarioId, LocalDate fechaEmision,
                        LocalDate fechaVencimiento, Instant fechaHoraCreacion) {
        this.codigoExterno=codigoExterno; this.usuarioId=usuarioId;
        this.fechaEmision=fechaEmision; this.fechaVencimiento=fechaVencimiento;
        this.fechaHoraCreacion=fechaHoraCreacion;
    }
    // Items are created together with the prescription; the API never adds them later.
    public ItemPrescripcion agregarItem(PresentacionMedicamento presentacion, int cantidadAutorizada) {
        if (id!=null) throw new IllegalStateException("No se pueden agregar ítems a una prescripción ya registrada.");
        var item=new ItemPrescripcion(this,presentacion,cantidadAutorizada);
        items.add(item);
        return item;
    }
    public List<ItemPrescripcion> getItems() { return Collections.unmodifiableList(items); }
    public void anular() { this.estado=EstadoPrescripcion.ANULADA; }
    // Validity is a calendar date in Argentina, both bounds inclusive.
    public boolean vigente(LocalDate hoy) {
        return estado==EstadoPrescripcion.ACTIVA && !hoy.isBefore(fechaEmision) && !hoy.isAfter(fechaVencimiento);
    }
}
