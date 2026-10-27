package ar.edu.uade.medlink.suspension.repository;

import ar.edu.uade.medlink.suspension.entity.EstadoSuspension;
import ar.edu.uade.medlink.suspension.entity.Suspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuspensionRepository
    extends JpaRepository<Suspension, Long> {

    List<Suspension> findByPacienteIdUsuario(Long idPaciente);

    Optional<Suspension> findByPacienteIdUsuarioAndEstado(
        Long idPaciente, EstadoSuspension estado);
}
