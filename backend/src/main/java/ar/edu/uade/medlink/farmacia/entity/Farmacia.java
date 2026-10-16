package ar.edu.uade.medlink.farmacia.entity;

import jakarta.persistence.*;
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
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false, columnDefinition="estado_farmacia")
    private EstadoFarmacia estado = EstadoFarmacia.ACTIVA;

    public Farmacia(String nombre, String direccion, String telefono, String emailContacto) {
        editar(nombre,direccion,telefono,emailContacto);
    }
    public void editar(String nombre, String direccion, String telefono, String emailContacto) {
        this.nombre=nombre; this.direccion=direccion;
        this.telefono=telefono; this.emailContacto=emailContacto;
    }
    public void cambiarEstado(EstadoFarmacia estado) { this.estado=estado; }
}
