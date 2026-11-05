package ar.edu.uade.medlink.escaneo;

import ar.edu.uade.medlink.common.error.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiPrescriptionImageExtractorTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private final Imagen imagen=new Imagen(new byte[]{1,2,3},"image/jpeg");

    @Test void contratoResponsesMultimodalUsaJsonSchemaEstrictoYStoreFalse() throws Exception {
        var lectura=new Lectura("MLD-DEMO","MED-8K29-P7Q4-ABCD","2026-11-05","2026-11-15",new Documento("t","l","p","i","p","i"),
            List.of(new Medicamento("Levotiroxina","100 mcg","Caja x 50",1)),List.of(),Estado.COMPLETA);
        var respuesta=json.writeValueAsString(Map.of("status","completed","output",List.of(
            Map.of("type","reasoning"),Map.of("type","message","content",
                List.of(Map.of("type","output_text","text",json.writeValueAsString(lectura)))))));
        var recibido=new AtomicReference<JsonNode>();
        var servidor=servidor(200,respuesta,recibido);
        try {
            assertEquals(lectura,extractor(servidor).extraer(imagen));
            var request=recibido.get();
            assertNotNull(request);
            assertTrue(request.path("store").isBoolean());
            assertFalse(request.path("store").asBoolean());
            assertEquals("data:image/jpeg;base64,AQID",request.at("/input/0/content/1/image_url").asText());
            assertEquals("json_schema",request.at("/text/format/type").asText());
            assertTrue(request.at("/text/format/strict").asBoolean());
            var requeridos=request.at("/text/format/schema/required");
            assertTrue(requeridos.isArray());
            assertTrue(requeridos.valueStream().anyMatch(n -> "codigoVerificacion".equals(n.asText())));
            assertEquals("null",request.at("/text/format/schema/properties/codigoVerificacion/type/1").asText());
        } finally { servidor.stop(0); }
    }

    @Test void negativaORespuestaIncompletaSeTraducenAInconclusa() throws Exception {
        for (String body:List.of("{\"status\":\"incomplete\"}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}")) {
            var servidor=servidor(200,body,new AtomicReference<>());
            try { assertEquals(Estado.INCONCLUSA,extractor(servidor).extraer(imagen).estado()); }
            finally { servidor.stop(0); }
        }
    }

    @Test void respuestaInvalidaYErrorHttpOcultanElContenidoDelProveedor() throws Exception {
        for (int status:List.of(200,429)) {
            var servidor=servidor(status,"texto privado simulado",new AtomicReference<>());
            try {
                var ex=assertThrows(ApiException.class,()->extractor(servidor).extraer(imagen));
                assertEquals("ESCANEO_PROVEEDOR_ERROR",ex.codigo());
                assertFalse(ex.getMessage().contains("privado"));
            } finally { servidor.stop(0); }
        }
    }

    @Test void plazoIncluyeLaLecturaDelCuerpoAunqueYaLlegaronLosHeaders() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var liberar=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/v1/responses",exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200,0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                try { liberar.await(4,java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            var cliente=new OpenAiPrescriptionImageExtractor(true,"test-key","modelo-falso",1,
                URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/responses"));
            assertEquals("ESCANEO_TIMEOUT",assertThrows(ApiException.class,()->cliente.extraer(imagen)).codigo());
        } finally {
            liberar.countDown();
            server.stop(0);
        }
    }

    private OpenAiPrescriptionImageExtractor extractor(HttpServer servidor) {
        return new OpenAiPrescriptionImageExtractor(true,"test-key","modelo-falso",3,
            URI.create("http://127.0.0.1:"+servidor.getAddress().getPort()+"/v1/responses"));
    }

    private HttpServer servidor(int status,String body,AtomicReference<JsonNode> recibido) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/responses",exchange -> {
            try (exchange) {
                recibido.set(json.readTree(exchange.getRequestBody().readAllBytes()));
                var bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status,bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        return server;
    }
}
