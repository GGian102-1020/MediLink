package ar.edu.uade.medlink.stock.dto;

import ar.edu.uade.medlink.stock.entity.TipoMovimientoStock;

public record MovimientoStockDTO(long id, long stockId, TipoMovimientoStock tipo, int cantidadAnterior,
    int cantidadNueva, int delta, String fechaHora, String reservaCodigo, Long actorCuentaId, String motivo) { }
