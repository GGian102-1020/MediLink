package ar.edu.uade.medlink.medicamento.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="medicamento")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Medicamento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_medicamento")
    private Long id;
    @Column(name="nombre",nullable=false,length=160)
    private String nombre;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="estado",nullable=false,columnDefinition="estado_medicamento")
    private EstadoMedicamento estado=EstadoMedicamento.ACTIVO;

    public Medicamento(String nombre) { this.nombre=nombre; }
    public void renombrar(String nombre) { this.nombre=nombre; }
    public void cambiarEstado(EstadoMedicamento estado) { this.estado=estado; }
}
