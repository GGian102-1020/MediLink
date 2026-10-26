package ar.edu.uade.medlink.prescripcion.dto;

public record ItemPrescripcionDTO(long id,long presentacionId,String medicamentoNombre,
    String presentacionDescripcion,int cantidadAutorizada,int cantidadComprometida,
    int cantidadUtilizada,int cantidadDisponible) { }
