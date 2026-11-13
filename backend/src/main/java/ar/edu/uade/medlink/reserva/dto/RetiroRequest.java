package ar.edu.uade.medlink.reserva.dto;

/** Body of POST /api/farmacias/{id}/reservas/{codigo}/retiro: the 6-digit PIN the patient provides. */
public record RetiroRequest(String pin) { }
