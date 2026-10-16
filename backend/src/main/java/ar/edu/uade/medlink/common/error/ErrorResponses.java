package ar.edu.uade.medlink.common.error;

import ar.edu.uade.medlink.common.ApiClock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponses {
    private final Clock clock;

    public ErrorResponses(Clock clock) { this.clock = clock; }

    public String correlationId(HttpServletRequest request) {
        Object id = request.getAttribute("correlationId");
        return id instanceof String ? (String) id : UUID.randomUUID().toString();
    }

    public Map<String, Object> body(HttpServletRequest request, String codigo, String mensaje) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("mensaje", mensaje);
        body.put("detalle", null);
        body.put("timestamp", ApiClock.timestamp(clock.instant()));
        body.put("correlationId", correlationId(request));
        return body;
    }

    private static String escape(String text) {
        StringBuilder result = new StringBuilder();
        for (char ch: text.toCharArray()) {
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (ch < 32) result.append(String.format("\\u%04x", (int)ch)); else result.append(ch); }
            }
        }
        return result.toString();
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String codigo, String mensaje) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String,Object> values = body(request,codigo,mensaje);
        response.getWriter().write("{\"codigo\":\"" + escape(codigo) + "\",\"mensaje\":\""
            + escape(mensaje) + "\",\"detalle\":null,\"timestamp\":\"" + values.get("timestamp")
            + "\",\"correlationId\":\"" + values.get("correlationId") + "\"}");
    }
}
