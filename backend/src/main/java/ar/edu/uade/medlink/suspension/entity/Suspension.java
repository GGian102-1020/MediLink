package ar.edu.uade.medlink.suspension.entity;

import ar.edu.uade.medlink.usuario.entity.Paciente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "suspension")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSuspension;

    @ManyToOne
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(nullable = false)
    private LocalDateTime fechaInicio;

    @Column(nullable = false)
    private LocalDateTime fechaFin;

    @Column(nullable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuspension estado;

    public Suspension(
        Paciente paciente,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        String motivo) {
        this.paciente = paciente;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.motivo = motivo;
        this.estado = EstadoSuspension.ACTIVA;
    }

    public boolean estaActiva() {
        return this.estado == EstadoSuspension.ACTIVA
            && LocalDateTime.now().isBefore(this.fechaFin);
    }

    public void levantar() {
        this.estado = EstadoSuspension.LEVANTADA;
    }

    public void vencer() {
        this.estado = EstadoSuspension.VENCIDA;
    }
}
