package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.config.RateLimitProperties;
import com.unihub.workshop.shared.observability.RateLimitMetrics;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.BucketProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Duration;

class RateLimitFilterAuthenticatedTest {

    private RateLimitFilter filter;
    private ProxyManager<byte[]> proxyManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        proxyManager = mock(ProxyManager.class);
        RateLimitProperties props = new RateLimitProperties(
                new RateLimitProperties.Tier(5, 5, Duration.ofSeconds(10)),
                new RateLimitProperties.Tier(100, 100, Duration.ofSeconds(60)), java.util.List.of());
        filter = new RateLimitFilter(proxyManager, props, mock(RateLimitMetrics.class));
    }

    private void stubConsumption(boolean isConsumed, long remainingTokens, long nanosToWaitForRefill) {
        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(isConsumed);
        when(probe.getRemainingTokens()).thenReturn(remainingTokens);
        when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWaitForRefill);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        @SuppressWarnings("unchecked")
        io.github.bucket4j.distributed.proxy.RemoteBucketBuilder<byte[]> builderMock = mock(
                io.github.bucket4j.distributed.proxy.RemoteBucketBuilder.class);
        when(proxyManager.builder()).thenReturn(builderMock);
        when(builderMock.build(any(byte[].class),
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
    }

    @Test
    void shouldAllowRequestAndSetHeadersWhenBucketHasTokens() throws Exception {
        stubConsumption(true, 4, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization",
                "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXV1aWQtMTIzIn0.xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeader("X-RateLimit-Retry-After")).isEqualTo("0");
    }

    @Test
    void shouldBlockRequestWith429AndRFC7807WhenBucketIsExhausted() throws Exception {
        stubConsumption(false, 0, 5000000000L); // 5 seconds wait

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization",
                "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXV1aWQtMTIzIn0.xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":429");
        assertThat(response.getContentAsString()).contains("\"type\":\"urn:unihub:error:rate-limited\"");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Retry-After")).isEqualTo("5"); // 5 seconds
    }
}
