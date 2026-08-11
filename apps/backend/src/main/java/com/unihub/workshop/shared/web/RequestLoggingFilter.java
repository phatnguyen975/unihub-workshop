package com.unihub.workshop.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that produces one structured log entry per inbound HTTP
 * request.
 *
 * <p>
 * For every request, this filter:
 * <ol>
 *   <li>Generates a unique {@code traceId} (UUID v4).</li>
 *   <li>Reads {@code X-Correlation-ID} from the request headers (defaults to
 *   empty string).</li>
 *   <li>Resolves {@code processedBy} from the configured node identifier.</li>
 *   <li>Injects all three — plus HTTP method, URI, and status — into MDC before
 *   delegating to the filter chain.</li>
 *   <li>Logs a single entry after the response is committed, with the log level
 *   driven by the HTTP status: {@code INFO} for 2xx/3xx, {@code WARN} for
 *   4xx, {@code ERROR} for 5xx.</li>
 *   <li>Clears MDC unconditionally in a {@code finally} block — even if the chain
 *   throws.</li>
 * </ol>
 *
 * <p>
 * Registered at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} by
 * {@link WebConfig} so it fires before Spring Security and any other filter.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_CORRELATION_ID = "correlationId";
    static final String MDC_PROCESSED_BY = "processedBy";
    static final String MDC_HTTP_METHOD = "http.method";
    static final String MDC_HTTP_URI = "http.uri";
    static final String MDC_HTTP_STATUS = "http.status";
    static final String MDC_DURATION_MS = "duration_ms";
    static final String MDC_CLIENT_IP = "clientIp";
    static final String MDC_USER_AGENT = "userAgent";

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final String nodeId;

    /**
     * Creates a filter that tags every log entry with the given node identifier.
     *
     * @param nodeId the value to use for the {@code processedBy} MDC key;
     *               typically sourced from {@code HOSTNAME} or a Spring property
     */
    public RequestLoggingFilter(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        long startNanos = System.nanoTime();

        // Populate MDC before delegating to the chain
        String traceId = UUID.randomUUID().toString();
        String correlationId = headerOrEmpty(request, CORRELATION_HEADER);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_PROCESSED_BY, nodeId);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_HTTP_URI, request.getRequestURI());
        MDC.put(MDC_CLIENT_IP, getClientIp(request));
        MDC.put(MDC_USER_AGENT, headerOrEmpty(request, USER_AGENT_HEADER));

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            int status = response.getStatus();

            MDC.put(MDC_HTTP_STATUS, String.valueOf(status));
            MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));

            writeLog(status);

            // Clear MDC unconditionally so no context bleeds into the next request
            MDC.clear();
        }
    }

    /**
     * Writes a single log entry at the level appropriate for the given HTTP status.
     *
     * <ul>
     *   <li>2xx / 3xx → {@code INFO}</li>
     *   <li>4xx → {@code WARN}</li>
     *   <li>5xx (or any other) → {@code ERROR}</li>
     * </ul>
     */
    private void writeLog(int status) {
        String level = resolveLogLevel(status);
        String message = "API Request Completed";
        switch (level) {
            case "WARN" -> log.warn(message);
            case "ERROR" -> log.error(message);
            default -> log.info(message);
        }
    }

    /**
     * Maps an HTTP status code to a log level string.
     *
     * <p>
     * Package-private to allow direct unit testing without involving the Servlet
     * API.
     *
     * @param status the HTTP response status code
     * @return one of {@code "INFO"}, {@code "WARN"}, or {@code "ERROR"}
     */
    String resolveLogLevel(int status) {
        if (status >= 500)
            return "ERROR";
        if (status >= 400)
            return "WARN";
        return "INFO";
    }

    private static String headerOrEmpty(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return value != null ? value : "";
    }

    private static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        
        // Trust X-Forwarded-For only if the request comes from an internal Docker network
        boolean isInternal = remoteAddr != null && (
                remoteAddr.startsWith("10.") || 
                remoteAddr.startsWith("172.") || 
                remoteAddr.startsWith("192.168.") || 
                remoteAddr.equals("127.0.0.1") || 
                remoteAddr.equals("0:0:0:0:0:0:0:1")
        );
        
        if (isInternal) {
            String xForwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr != null ? remoteAddr : "";
    }
}
