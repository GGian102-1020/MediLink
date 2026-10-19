package ar.edu.uade.medlink.controller;

import ar.edu.uade.medlink.dto.MedicamentoRequestDTO;
import ar.edu.uade.medlink.dto.MedicamentoResponseDTO;
import ar.edu.uade.medlink.model.Medicamento;
import ar.edu.uade.medlink.service.MedicamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/Medicamento")
public class MedicamentoController{
    private final MedicamentoService medicamentoService;

    public MedicamentoController(MedicamentoService service){
        this.medicamentoService = service;
    }
    @PostMapping
    public ResponseEntity<MedicamentoResponseDTO> registrar(@RequestBody MedicamentoRequestDTO dto){
        Medicamento m = medicamentoService.registrar(dto);
        return ResponseEntity.status(201).body(toDTO(m))
    }
    @GetMapping("/buscar")
    public ResponseEntity<List<MedicamentoResponseDTO>> buscar(@RequestParam String nombre){
        return ResponseEntity.ok(medicamentoService.buscar(nombre).stream().map(this::toDTO).toList())
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void>
    desactivar(@PathVariable Long id) {
        medicamentoService.desactivar(id);
        return ResponseEntity
            .noContent().build();
    }




    private MedicamentoResponseDTO toDTO(
        Medicamento m) {
        return new MedicamentoResponseDTO(
            m.getIdMedicamento(),
            m.getNombre(),
            m.getPrincipioActivo(),
            m.estaActivo()
        );
    }
}