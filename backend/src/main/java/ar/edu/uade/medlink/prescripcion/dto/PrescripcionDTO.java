package ar.edu.uade.medlink.prescripcion.dto;

import ar.edu.uade.medlink.prescripcion.entity.EstadoPrescripcion;
import java.time.LocalDate;
import java.util.List;

public record PrescripcionDTO(long id,String codigoExterno,long usuarioId,LocalDate fechaEmision,
    LocalDate fechaVencimiento,EstadoPrescripcion estado,List<ItemPrescripcionDTO> items) { }
