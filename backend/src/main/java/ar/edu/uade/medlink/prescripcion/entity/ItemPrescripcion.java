package ar.edu.uade.medlink.prescripcion.entity;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.presentacion.entity.PresentacionMedicamento;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RN-13/RN-20: comprometida + utilizada never exceeds autorizada.
 * Callers must hold the canonical locks (Prescripción -> ItemPrescripción) before mutating.
 */
@Entity
@Table(name="item_prescripcion",
    uniqueConstraints=@UniqueConstraint(name="uq_item_prescripcion_presentacion",columnNames={"id_prescripcion","id_presentacion"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ItemPrescripcion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_item_prescripcion")
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="id_prescripcion",nullable=false,updatable=false)
    private Prescripcion prescripcion;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="id_presentacion",nullable=false,updatable=false)
    private PresentacionMedicamento presentacion;
    @Column(name="cantidad_autorizada",nullable=false,updatable=false)
    private int cantidadAutorizada;
    @Column(name="cantidad_comprometida",nullable=false)
    private int cantidadComprometida;
    @Column(name="cantidad_utilizada",nullable=false)
    private int cantidadUtilizada;

    ItemPrescripcion(Prescripcion prescripcion, PresentacionMedicamento presentacion, int cantidadAutorizada) {
        if (cantidadAutorizada<1) throw ApiException.validation("La cantidad autorizada debe ser mayor a cero.");
        this.prescripcion=prescripcion; this.presentacion=presentacion; this.cantidadAutorizada=cantidadAutorizada;
    }
    public int getCantidadDisponible() {
        return cantidadAutorizada-cantidadComprometida-cantidadUtilizada;
    }
    // Active reservation created.
    public void comprometer(int cantidad) {
        positive(cantidad);
        if (cantidad>getCantidadDisponible())
            throw ApiException.conflict("PRESCRIPTION_QUANTITY_INSUFFICIENT","La receta no autoriza la cantidad solicitada.");
        cantidadComprometida+=cantidad;
    }
    // Reservation cancelled or expired.
    public void liberar(int cantidad) {
        positive(cantidad);
        if (cantidad>cantidadComprometida)
            throw ApiException.conflict("INVALID_STATE_TRANSITION","Compromiso de receta inconsistente.");
        cantidadComprometida-=cantidad;
    }
    // Reservation completed: the committed units become definitively used.
    public void utilizar(int cantidad) {
        positive(cantidad);
        if (cantidad>cantidadComprometida)
            throw ApiException.conflict("INVALID_STATE_TRANSITION","Compromiso de receta inconsistente.");
        cantidadComprometida-=cantidad;
        cantidadUtilizada+=cantidad;
    }
    private static void positive(int cantidad) {
        if (cantidad<1) throw ApiException.validation("La cantidad debe ser mayor a cero.");
    }
}
