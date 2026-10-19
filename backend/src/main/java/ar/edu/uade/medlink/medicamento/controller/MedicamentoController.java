package ar.edu.uade.medlink.medicamento.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.medicamento.dto.MedicamentoDTO;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medicamentos")
public class MedicamentoController {
    private final MedicamentoService service;
    public MedicamentoController(MedicamentoService service) { this.service=service; }
    @GetMapping
    public PageDTO<MedicamentoDTO> buscar(@RequestParam(required=false) String nombre,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam MultiValueMap<String,String> params) {
        return service.listarPublico(nombre,page,size,params.get("sort"));
    }
}
