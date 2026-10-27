package ar.edu.uade.medlink.suspension.controller;

import ar.edu.uade.medlink.suspension.dto.SuspensionRequestDTO;
import ar.edu.uade.medlink.suspension.dto.SuspensionResponseDTO;
import ar.edu.uade.medlink.suspension.entity.Suspension;
import ar.edu.uade.medlink.suspension.service.SuspensionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/suspensiones")
public class SuspensionController {

    private final SuspensionService suspensionService;

    public SuspensionController(SuspensionService service) {
        this.suspensionService = service;
    }

    @PostMapping
    public ResponseEntity<SuspensionResponseDTO> suspender(
        @RequestBody SuspensionRequestDTO dto) {
        Suspension s = suspensionService.suspender(dto);
        return ResponseEntity.status(201).body(toDTO(s));
    }

    @PatchMapping("/{id}/levantar")
    public ResponseEntity<Void> levantar(@PathVariable Long id) {
        suspensionService.levantar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/paciente/{idPaciente}")
    public ResponseEntity<List<SuspensionResponseDTO>>
        listarPorPaciente(@PathVariable Long idPaciente) {
        List<SuspensionResponseDTO> lista =
            suspensionService
                .listarPorPaciente(idPaciente)
                .stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    private SuspensionResponseDTO toDTO(Suspension s) {
        return new SuspensionResponseDTO(
            s.getIdSuspension(),
            s.getPaciente().getIdUsuario(),
            s.getPaciente().getNombre()
                + " " + s.getPaciente().getApellido(),
            s.getFechaInicio(),
            s.getFechaFin(),
            s.getMotivo(),
            s.getEstado()
        );
    }
}
