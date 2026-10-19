package ar.edu.uade.medlink.presentacion.dto;

import ar.edu.uade.medlink.presentacion.entity.*;

public record PresentacionDTO(long id,long medicamentoId,String medicamentoNombre,String concentracionTexto,
    String formaFarmaceutica,int cantidadEnvase,String unidadExpendio,
    CondicionDispensa condicionDispensa,EstadoPresentacion estado) { }
