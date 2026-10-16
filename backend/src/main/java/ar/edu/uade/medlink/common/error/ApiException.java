package ar.edu.uade.medlink.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String message) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }
    public HttpStatus status() { return status; }
    public String codigo() { return codigo; }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso solicitado no existe o no está disponible.");
    }
    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tenés permisos para realizar esta operación.");
    }
    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    public static ApiException conflict(String codigo, String message) {
        return new ApiException(HttpStatus.CONFLICT, codigo, message);
    }
}
