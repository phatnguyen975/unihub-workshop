package com.unihub.workshop.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link RequestLoggingFilter}.
 */
class RequestLoggingFilterTest {

    private static final String NODE_ID = "test-node";

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new RequestLoggingFilter(NODE_ID);
    }

    @Test
    void shouldPopulateMdcWithRequiredKeys_duringChainExecution() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader("X-Correlation-ID", "corr-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();
        AtomicReference<String> capturedProcessedBy = new AtomicReference<>();
        AtomicReference<String> capturedClientIp = new AtomicReference<>();
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain(
                new jakarta.servlet.http.HttpServlet() {
                },
                (req, resp, filterChain) -> {
                    capturedTraceId.set(MDC.get("traceId"));
                    capturedCorrelationId.set(MDC.get("correlationId"));
                    capturedProcessedBy.set(MDC.get("processedBy"));
                    capturedClientIp.set(MDC.get("clientIp"));
                    capturedUserAgent.set(MDC.get("userAgent"));
                    filterChain.doFilter(req, resp);
                });

        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        assertThat(capturedTraceId.get()).isNotBlank();
        assertThat(capturedCorrelationId.get()).isEqualTo("corr-abc");
        assertThat(capturedProcessedBy.get()).isEqualTo(NODE_ID);
        assertThat(capturedClientIp.get()).isEqualTo("127.0.0.1"); // Default mock IP
        assertThat(capturedUserAgent.get()).isEmpty();
    }

    @Test
    void shouldClearMdc_afterFilterCompletes() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldResolveCorrectLogLevel_basedOnResponseStatus() {
        // Assert on the pure mapping function — no I/O needed
        assertThat(filter.resolveLogLevel(200)).isEqualTo("INFO");
        assertThat(filter.resolveLogLevel(201)).isEqualTo("INFO");
        assertThat(filter.resolveLogLevel(301)).isEqualTo("INFO");
        assertThat(filter.resolveLogLevel(400)).isEqualTo("WARN");
        assertThat(filter.resolveLogLevel(404)).isEqualTo("WARN");
        assertThat(filter.resolveLogLevel(500)).isEqualTo("ERROR");
        assertThat(filter.resolveLogLevel(503)).isEqualTo("ERROR");
    }

    @Test
    void shouldSetCorrelationIdToEmpty_whenHeaderIsMissing() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        // No X-Correlation-ID header
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain(
                new jakarta.servlet.http.HttpServlet() {
                },
                (req, resp, filterChain) -> {
                    capturedCorrelationId.set(MDC.get("correlationId"));
                    filterChain.doFilter(req, resp);
                });

        // Act
        filter.doFilterInternal(request, response, chain);

        // Assert
        assertThat(capturedCorrelationId.get()).isEmpty();
    }

    @Test
    void shouldClearMdc_evenWhenChainThrows() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain throwingChain = new MockFilterChain(
                new jakarta.servlet.http.HttpServlet() {
                },
                (req, resp, filterChain) -> {
                    throw new RuntimeException("downstream error");
                });

        // Act & Assert: the filter should propagate the exception but still clear MDC
        assertThatCode(() -> filter.doFilterInternal(request, response, throwingChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("downstream error");

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
