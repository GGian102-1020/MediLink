package ar.edu.uade.medlink.suspension.service;

import ar.edu.uade.medlink.suspension.dto.SuspensionRequestDTO;
import ar.edu.uade.medlink.suspension.entity.EstadoSuspension;
import ar.edu.uade.medlink.suspension.entity.Suspension;
import ar.edu.uade.medlink.suspension.repository.SuspensionRepository;
import ar.edu.uade.medlink.usuario.entity.Paciente;
import ar.edu.uade.medlink.usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class SuspensionService {

    private final SuspensionRepository suspensionRepository;
    private final UsuarioRepository usuarioRepository;

    public SuspensionService(
        SuspensionRepository suspensionRepo,
        UsuarioRepository usuarioRepo) {
        this.suspensionRepository = suspensionRepo;
        this.usuarioRepository = usuarioRepo;
    }

    public Suspension suspender(SuspensionRequestDTO dto) {
        Paciente paciente = (Paciente) usuarioRepository
            .findById(dto.idPaciente())
            .orElseThrow(() ->
                new RuntimeException("Paciente no encontrado"));

        Suspension suspension = new Suspension(
            paciente,
            LocalDateTime.now(),
            dto.fechaFin(),
            dto.motivo()
        );

        return suspensionRepository.save(suspension);
    }

    public void levantar(Long idSuspension) {
        Suspension s = suspensionRepository
            .findById(idSuspension)
            .orElseThrow(() ->
                new RuntimeException("Suspension no encontrada"));
        s.levantar();
        suspensionRepository.save(s);
    }

    @Transactional(readOnly = true)
    public boolean tieneSuspensionActiva(Long idPaciente) {
        return suspensionRepository
            .findByPacienteIdUsuarioAndEstado(
                idPaciente, EstadoSuspension.ACTIVA)
            .map(Suspension::estaActiva)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Suspension> listarPorPaciente(Long idPaciente) {
        return suspensionRepository
            .findByPacienteIdUsuario(idPaciente);
    }
}
