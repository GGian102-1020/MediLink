package ar.edu.uade.medlink.farmacia.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="farmacia")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Farmacia {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id_farmacia")
    private Long id;
    @Column(nullable=false, length=120)
    private String nombre;
    @Column(nullable=false, length=200)
    private String direccion;
    @Column(length=30)
    private String telefono;
    @Column(name="email_contacto", length=254)
    private String emailContacto;
    @Column(precision=10, scale=7)
    private BigDecimal latitud;
    @Column(precision=10, scale=7)
    private BigDecimal longitud;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false, columnDefinition="estado_farmacia")
    private EstadoFarmacia estado = EstadoFarmacia.ACTIVA;

    public Farmacia(String nombre, String direccion, String telefono, String emailContacto,
                   BigDecimal latitud, BigDecimal longitud) {
        editar(nombre, direccion, telefono, emailContacto, latitud, longitud);
    }
    public void editar(String nombre, String direccion, String telefono, String emailContacto,
                       BigDecimal latitud, BigDecimal longitud) {
        this.nombre=nombre; this.direccion=direccion;
        this.telefono=telefono; this.emailContacto=emailContacto;
        this.latitud=latitud; this.longitud=longitud;
    }
    public void cambiarEstado(EstadoFarmacia estado) { this.estado=estado; }
}
