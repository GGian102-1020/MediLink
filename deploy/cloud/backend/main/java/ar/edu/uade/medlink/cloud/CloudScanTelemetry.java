package ar.edu.uade.medlink.cloud;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A closed metadata schema. Never accepts a request body, OCR text, document name, email or key. */
public final class CloudScanTelemetry {
    private static final Logger LOG=LoggerFactory.getLogger(CloudScanTelemetry.class);
    private static final ThreadLocal<Attempt> CURRENT=new ThreadLocal<>();
    private static final class Attempt {
        final String id=UUID.randomUUID().toString(); final long start=System.nanoTime();
        Long input,output,providerMs; String providerId="unknown",providerResult="NOT_CALLED"; BigDecimal cost;
    }
    private CloudScanTelemetry() { }
    static void begin() { CURRENT.set(new Attempt()); }
    public static void provider(String requestId,Long input,Long output,long millis,String result,String model) {
        var a=CURRENT.get(); if(a==null) return;
        a.providerId=requestId!=null && requestId.matches("(?:req_|resp_)[A-Za-z0-9_-]{1,96}")?requestId:"unknown";
        a.input=valid(input); a.output=valid(output); a.providerMs=Math.max(0,millis);
        a.providerResult=result.matches("[A-Z0-9_]{1,40}")?result:"UNKNOWN";
        if (a.input!=null && a.output!=null && ("gpt-5.4-mini".equals(model)||"gpt-5.4-mini-2026-03-17".equals(model)))
            a.cost=BigDecimal.valueOf(a.input).multiply(new BigDecimal("0.75"))
                .add(BigDecimal.valueOf(a.output).multiply(new BigDecimal("4.50"))).movePointLeft(6);
    }
    private static Long valid(Long n) { return n!=null && n>=0 && n<=Integer.MAX_VALUE?n:null; }
    static void finish(int status) {
        var a=CURRENT.get();
        try { if(a!=null) LOG.info("cloud_scan scan_id={} provider_request_id={} input_tokens={} output_tokens={} estimated_cost_usd={} latency_ms={} provider_latency_ms={} provider_result={} http_status={}",
            a.id,a.providerId,a.input,a.output,a.cost,(System.nanoTime()-a.start)/1000000,a.providerMs,a.providerResult,status); }
        finally { CURRENT.remove(); }
    }
}
