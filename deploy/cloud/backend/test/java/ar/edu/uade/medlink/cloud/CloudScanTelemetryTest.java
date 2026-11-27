package ar.edu.uade.medlink.cloud;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class CloudScanTelemetryTest {
    @Test void logsOnlyClosedMetadataWithEstimatedCostAndClearsThreadContext() {
        var logger=(Logger)LoggerFactory.getLogger(CloudScanTelemetry.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        try {
            CloudScanTelemetry.begin();
            CloudScanTelemetry.provider("req_synthetic_123",4500L,1800L,42,"HTTP_200","gpt-5.4-mini");
            CloudScanTelemetry.finish(409);
            var line=logs.list.getFirst().getFormattedMessage();
            assertTrue(line.contains("provider_request_id=req_synthetic_123"));
            assertTrue(line.contains("input_tokens=4500 output_tokens=1800 estimated_cost_usd=0.01147500"));
            assertTrue(line.contains("provider_latency_ms=42"));assertTrue(line.contains("http_status=409"));
            CloudScanTelemetry.finish(200);assertEquals(1,logs.list.size());
            CloudScanTelemetry.begin();
            CloudScanTelemetry.provider("untrusted\nprivate OCR or secret",null,null,9,"bad\nresult","unpriced-model");
            CloudScanTelemetry.finish(504);
            line=logs.list.getLast().getFormattedMessage();
            assertFalse(line.contains("private"));assertFalse(line.contains("secret"));
            assertTrue(line.contains("provider_request_id=unknown"));assertTrue(line.contains("estimated_cost_usd=null"));
        } finally {logger.detachAppender(logs);logs.stop();}
    }
}
