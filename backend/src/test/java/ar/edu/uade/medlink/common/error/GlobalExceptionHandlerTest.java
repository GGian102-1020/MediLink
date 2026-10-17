package ar.edu.uade.medlink.common.error;

import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorResponses(Clock.systemUTC()));
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/operadores");

    @Test void unexpectedConstraintIsNotReportedAsClientValidationError() {
        var response=handler.integrity(new DataIntegrityViolationException("error interno"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,response.getStatusCode());
        assertEquals("INTERNAL_ERROR",response.getBody().get("codigo"));
    }

    @Test void unknownUniqueViolationIsNotInventedAsInvalidStateTransition() {
        var sql=new SQLException("duplicate","23505");
        var response=handler.integrity(new DataIntegrityViolationException("constraint",sql), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,response.getStatusCode());
        assertEquals("INTERNAL_ERROR",response.getBody().get("codigo"));
        assertNotEquals("INVALID_STATE_TRANSITION",response.getBody().get("codigo"));
        assertFalse(response.getBody().toString().contains("duplicate"));
    }

    @Test void domainConflictKeepsItsNormativeCode() {
        var response=handler.api(ApiException.conflict(
            "EMAIL_ALREADY_EXISTS", "Ya existe una cuenta con ese correo."),request);
        assertEquals(HttpStatus.CONFLICT,response.getStatusCode());
        assertEquals("EMAIL_ALREADY_EXISTS",response.getBody().get("codigo"));
    }

    @Test void genericServerErrorDoesNotExposeExceptionDetails() {
        var response=handler.unexpected(new IllegalStateException("password-or-token-internal"),request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,response.getStatusCode());
        assertFalse(response.getBody().toString().contains("password-or-token-internal"));
        assertNotNull(response.getBody().get("correlationId"));
    }
}
