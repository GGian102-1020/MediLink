package ar.edu.uade.medlink.medicamento.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "medicamento")
@Getter
@NoArgsConstructor(access = AccessLevel.Protected)
public class Medicamento{
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long idMedicamento;

    @Column(nullable=false)
    private String nombre;

    @Column(nullable=false)
    private String principioActivo;

    @Column(nullable=false)
    private boolean activo;

    public Medicamento(
        String nombre,
        String principioActivo) {
        this.nombre = nombre;
        this.principioActivo = principioActivo;
        this.activo = true;
    }
     public boolean estaActivo() {
        return activo;
    }
}