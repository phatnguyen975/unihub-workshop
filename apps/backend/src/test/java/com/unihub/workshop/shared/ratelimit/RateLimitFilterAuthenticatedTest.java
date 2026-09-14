package com.unihub.workshop.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterAuthenticatedTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> builderMock;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = RateLimitProperties.builder()
            .defaultTier(100, 100, Duration.ofSeconds(60))
            .addTier(
                "registration",
                5,
                5,
                Duration.ofSeconds(10),
                java.util.List.of()
            )
            .build();
        filter = new RateLimitFilter(
            proxyManager,
            props,
            mock(RateLimitMetrics.class)
        );
    }

    private void stubConsumption(
        boolean isConsumed,
        long remainingTokens,
        long nanosToWaitForRefill
    ) {
        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(isConsumed);
        when(probe.getRemainingTokens()).thenReturn(remainingTokens);
        when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWaitForRefill);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(proxyManager.builder()).thenReturn(builderMock);
        when(
            builderMock.build(
                any(byte[].class),
                org.mockito.ArgumentMatchers.<
                    Supplier<BucketConfiguration>
                >any()
            )
        ).thenReturn(bucket);
    }

    @Test
    void shouldAllowRequestAndSetHeadersWhenBucketHasTokens() throws Exception {
        stubConsumption(true, 4, 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            "Authorization",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXV1aWQtMTIzIn0.xyz"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeader("X-RateLimit-Retry-After")).isEqualTo(
            "0"
        );
    }

    @Test
    void shouldBlockRequestWith429AndRFC7807WhenBucketIsExhausted()
        throws Exception {
        stubConsumption(false, 0, 5000000000L); // 5 seconds

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            "Authorization",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXV1aWQtMTIzIn0.xyz"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo(
            "application/problem+json"
        );
        assertThat(response.getContentAsString()).contains("\"status\":429");
        assertThat(response.getContentAsString()).contains(
            "\"type\":\"urn:unihub:error:rate-limited\""
        );
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Retry-After")).isEqualTo(
            "5"
        );
    }
}
