package ar.edu.uade.medlink.common;

import ar.edu.uade.medlink.common.error.ApiException;

/** Validation shared by the existing administrative mutations. */
public final class RequestValidation {
    private RequestValidation() { }

    public static String requiredReason(String raw) {
        if (raw == null || raw.strip().isEmpty() || raw.strip().length() > 500)
            throw ApiException.validation("El motivo es obligatorio (1 a 500 caracteres).");
        return raw.strip();
    }
}
