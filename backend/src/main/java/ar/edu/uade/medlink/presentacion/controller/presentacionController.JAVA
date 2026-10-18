package ar.edu.uade.medlink.presentacion.controller;
import ar.edu.uade.medlink.dto.PresentacionRequestDTO;
import ar.edu.uade.medlink.dto.PresentacionResponseDTO;
import ar.edu.uade.medlink.model.presentacionMedicamento;
import ar.edu.uade.medlink.service.presentacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/presentaciones")
public class PresentacionController {
    private final presentacionService pService;
    public PresentacionController(presentacionService service){
        this.presentacionService = service;
    }
    @PostMapping 
    public ResponseEntity<PresentacionResponseDTO>
    registrar(
        @RequestBody PresentacionRequestDTO dto){
            return ResponseEntity
            .status(201)
            .body(toDTO(pService.registrar(dto)));
        }
    @GetMapping("/medicamento/{id}")
    publicPorMedicamento(PathVariable Long idMedicamento){
        return ResponseEntity.ok(pService
        .buscarPorMedicamento(idMedicamento)
        .stream()
        .map(this::toDTO)
        .toList()
        );
    }
    @DeleteMapping ("/{id}")
    public ResponseEntity<Void>
    desactivarPresentacion(@PathVariable Long idPresentacion){
        pService.desactivar(idPresentacion);
        return ResponseEntity.noContent().build();
    }
    private PresentacionResponseDTO toDTO(
        PresentacionMedicamento p) {
        return new PresentacionResponseDTO(
            p.getIdPresentacion(),
            p.getMedicamento().getIdMedicamento(),
            p.getMedicamento().getNombre(),
            p.getConcentracion(),
            p.getFormaFarmaceutica(),
            p.getCantidadEnvase(),
            p.getUnidadExpendio(),
            p.getCondicionDispensa(),
            p.estaActiva()
        );
    }

}