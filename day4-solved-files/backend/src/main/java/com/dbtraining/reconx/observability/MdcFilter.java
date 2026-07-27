package com.dbtraining.reconx.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET-ADV061 — MdcFilter: put correlationId + tradeRef into SLF4J MDC
 *
 * WHAT:    Reads X-Correlation-Id (falling back to a random UUID) and the
 *          optional X-Trade-Ref header on every request, publishes both into
 *          MDC so downstream log lines can pattern-substitute them, and
 *          clears MDC in a finally block so the values don't leak onto the
 *          next request that reuses the Tomcat thread.
 * HOW:     @Order(1) ensures this runs before Spring's own filters so
 *          controller/service/repository logs all see the correlation id.
 *          @Component makes it a bean; the servlet container picks it up via
 *          Spring's DelegatingFilterProxy.
 * WHY:     Day 6's Kafka consumer, Day 8's recon engine, and Day 10's Compose
 *          stack all share the same logical request. Without a stable id
 *          stitched through MDC, tracing a single trade across services is
 *          log archaeology.
 * OBSERVE: `curl -H "X-Correlation-Id: foo-123" ...` under dev shows
 *          `foo-123` in every log line for that request; under uat/prod the
 *          JSON encoder emits `"mdc":{"correlationId":"foo-123"}`.
 * ============================================================================
 */
@Component
@Order(1)
public class MdcFilter implements Filter {

    static final String HDR_CORRELATION = "X-Correlation-Id";
    static final String HDR_TRADE_REF   = "X-Trade-Ref";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String correlationId = header(http, HDR_CORRELATION, UUID.randomUUID().toString());
        String tradeRef      = header(http, HDR_TRADE_REF, null);
        try {
            MDC.put("correlationId", correlationId);
            if (tradeRef != null) MDC.put("tradeRef", tradeRef);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }

    private static String header(HttpServletRequest r, String name, String fallback) {
        String v = r.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
