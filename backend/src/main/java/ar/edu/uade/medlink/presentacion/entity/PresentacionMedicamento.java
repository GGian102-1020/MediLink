package ar.edu.uade.medlink.presentacion.entity;

import ar.edu.uade.medlink.medicamento.entity.Medicamento;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="presentacion_medicamento")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PresentacionMedicamento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_presentacion")
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="id_medicamento",nullable=false,updatable=false)
    private Medicamento medicamento;
    @Column(name="concentracion_texto",nullable=false,length=80,updatable=false)
    private String concentracionTexto;
    @Column(name="forma_farmaceutica",nullable=false,length=80,updatable=false)
    private String formaFarmaceutica;
    @Column(name="cantidad_envase",nullable=false,updatable=false)
    private int cantidadEnvase;
    @Column(name="unidad_expendio",nullable=false,length=80,updatable=false)
    private String unidadExpendio;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="condicion_dispensa",nullable=false,columnDefinition="condicion_dispensa",updatable=false)
    private CondicionDispensa condicionDispensa;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="estado",nullable=false,columnDefinition="estado_presentacion")
    private EstadoPresentacion estado=EstadoPresentacion.ACTIVA;

    public PresentacionMedicamento(Medicamento medicamento,String concentracionTexto,String formaFarmaceutica,
                                   int cantidadEnvase,String unidadExpendio,CondicionDispensa condicionDispensa) {
        this.medicamento=medicamento;
        this.concentracionTexto=concentracionTexto;
        this.formaFarmaceutica=formaFarmaceutica;
        this.cantidadEnvase=cantidadEnvase;
        this.unidadExpendio=unidadExpendio;
        this.condicionDispensa=condicionDispensa;
    }
    public boolean requiereReceta() { return condicionDispensa==CondicionDispensa.BAJO_RECETA; }
    public void cambiarEstado(EstadoPresentacion estado) { this.estado=estado; }
}
