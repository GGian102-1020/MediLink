package ar.edu.uade.medlink.presentacion.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.presentacion.dto.PresentacionDTO;
import ar.edu.uade.medlink.presentacion.service.PresentacionService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class PresentacionController {
    private final PresentacionService service;
    public PresentacionController(PresentacionService service) { this.service=service; }
    @GetMapping("/api/medicamentos/{id}/presentaciones")
    public PageDTO<PresentacionDTO> porMedicamento(@PathVariable long id,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam MultiValueMap<String,String> params) {
        return service.listarPublico(id,page,size,params.get("sort"));
    }
    @GetMapping("/api/presentaciones/{id}")
    public PresentacionDTO detalle(@PathVariable long id) { return service.detallePublico(id); }
}
