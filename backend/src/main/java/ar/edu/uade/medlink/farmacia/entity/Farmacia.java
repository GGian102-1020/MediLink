package ar.edu.uade.medlink.farmacia.entity;

import java.math.BigDecimal;

public class Farmacia {

    private Long idFarmacia;
    private String nombre;
    private String direccion;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String telefono;
    private String email;
    private boolean activo;


    public boolean estaActiva(boolean activo) {
        return this.activo;
    };




}
