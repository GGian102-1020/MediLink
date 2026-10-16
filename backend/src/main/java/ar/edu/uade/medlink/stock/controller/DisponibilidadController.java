package ar.edu.uade.medlink.stock.controller;

import ar.edu.uade.medlink.stock.dto.DisponibilidadFarmaciaDTO;
import ar.edu.uade.medlink.stock.service.StockService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/presentaciones")
public class DisponibilidadController {
    private final StockService service;
    public DisponibilidadController(StockService service) { this.service=service; }
    @GetMapping("/{id}/disponibilidad")
    public List<DisponibilidadFarmaciaDTO> disponibilidad(@PathVariable long id) {
        return service.disponibilidad(id);
    }
}
