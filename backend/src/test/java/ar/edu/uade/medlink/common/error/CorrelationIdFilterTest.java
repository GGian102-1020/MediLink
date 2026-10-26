package ar.edu.uade.medlink.common.error;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test void carriesCorrelationIdIntoLogsAndRemovesItAfterRequest() throws Exception {
        var req=new MockHttpServletRequest("GET","/api/health");
        String id=UUID.randomUUID().toString();
        req.addHeader("X-Correlation-Id",id);
        var response=new MockHttpServletResponse();
        AtomicReference<String> seen=new AtomicReference<>();
        filter.doFilter(req,response,(r,s)->seen.set(MDC.get("correlationId")));
        assertEquals(id,response.getHeader("X-Correlation-Id"));
        assertEquals(id,seen.get());
        assertNull(MDC.get("correlationId"));
    }

    @Test void invalidCorrelationIdIsReplacedWithUuid() throws Exception {
        var req=new MockHttpServletRequest("GET","/api/health");
        req.addHeader("X-Correlation-Id","invalid");
        var response=new MockHttpServletResponse();
        filter.doFilter(req,response,(r,s)->{});
        assertDoesNotThrow(()->UUID.fromString(response.getHeader("X-Correlation-Id")));
        assertNull(MDC.get("correlationId"));
    }

    @Test void shortenedUuidIsReplacedAndMdcIsClearedEvenOnFailure() throws Exception {
        var req=new MockHttpServletRequest("GET","/api/admin/auditoria");
        req.addHeader("X-Correlation-Id","1-1-1-1-1");
        var response=new MockHttpServletResponse();
        assertThrows(jakarta.servlet.ServletException.class,()->filter.doFilter(req,response,(r,s)-> {
            assertEquals(r.getAttribute("correlationId"),MDC.get("correlationId"));
            throw new jakarta.servlet.ServletException("test");
        }));
        assertNotEquals("00000001-0001-0001-0001-000000000001",response.getHeader("X-Correlation-Id"));
        assertDoesNotThrow(()->UUID.fromString(response.getHeader("X-Correlation-Id")));
        assertNull(MDC.get("correlationId"));
    }

    @Test void missingCorrelationIdIsGeneratedAndSharedWithRequest() throws Exception {
        var req=new MockHttpServletRequest("GET","/api/admin/auditoria");
        var response=new MockHttpServletResponse();
        filter.doFilter(req,response,(r,s)->assertEquals(r.getAttribute("correlationId"),MDC.get("correlationId")));
        assertEquals(req.getAttribute("correlationId"),response.getHeader("X-Correlation-Id"));
        assertDoesNotThrow(()->UUID.fromString(response.getHeader("X-Correlation-Id")));
        assertNull(MDC.get("correlationId"));
    }
}
