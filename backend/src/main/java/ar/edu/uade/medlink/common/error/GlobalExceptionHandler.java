package ar.edu.uade.medlink.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ErrorResponses errors;
    public GlobalExceptionHandler(ErrorResponses errors) { this.errors = errors; }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,Object>> api(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(errors.body(request, ex.codigo(), ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
                       ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,Object>> validation(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errors.body(request, "VALIDATION_ERROR", "Datos de entrada inválidos."));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String,Object>> invalidParameter(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errors.body(request, "VALIDATION_ERROR", "Parámetro de solicitud inválido."));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> missing(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errors.body(request, "RESOURCE_NOT_FOUND", "Recurso no encontrado."));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> denied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errors.body(request, "FORBIDDEN", "No tenés permisos para realizar esta operación."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errors.body(request, "VALIDATION_ERROR", "Los datos no cumplen una restricción de integridad."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception ex, HttpServletRequest request) {
        // Never expose SQL, hashes, tokens, stack traces or secrets to clients.
        return ResponseEntity.internalServerError().body(errors.body(request, "INTERNAL_ERROR", "No fue posible completar la operación."));
    }
}
