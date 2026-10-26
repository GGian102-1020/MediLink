package ar.edu.uade.medlink.prescripcion.dto;

public record ItemPrescripcionDTO(long id,long presentacionId,String descripcionPresentacion,
    int cantidadAutorizada,int cantidadComprometida,int cantidadUtilizada,int cantidadDisponible) { }
