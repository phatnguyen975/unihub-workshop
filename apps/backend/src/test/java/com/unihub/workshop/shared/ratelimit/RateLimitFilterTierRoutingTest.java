package com.unihub.workshop.shared.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitFilterTierRoutingTest {

    private ProxyManager<byte[]> proxyManager;
    private RemoteBucketBuilder<byte[]> builderMock;
    private FilterChain filterChain;
    private RateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        proxyManager = mock(ProxyManager.class);
        builderMock = mock(RemoteBucketBuilder.class);
        filterChain = mock(FilterChain.class);

        RateLimitProperties.Tier regTier = new RateLimitProperties.Tier(5, 5, Duration.ofSeconds(10));
        RateLimitProperties.Tier browsTier = new RateLimitProperties.Tier(100, 100, Duration.ofSeconds(60));
        RateLimitProperties properties = new RateLimitProperties(regTier, browsTier,
                List.of("/api/v1/registrations/**"));

        filter = new RateLimitFilter(proxyManager, properties, mock(RateLimitMetrics.class));

        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);

        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(1L);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        when(proxyManager.builder()).thenReturn(builderMock);
        when(builderMock.build(any(byte[].class), ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/registrations, 5",
            "/api/v1/registrations/123, 5",
            "/api/v1/workshops, 100",
            "/api/v1/unknown, 100",
            "/api/v1/stream/events, 10"
    })
    void shouldRouteToCorrectTierBasedOnPath(String path, String expectedLimit) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.1"); // force IP fallback for simplicity
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Verify the X-RateLimit-Limit header matches the expected tier capacity
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo(expectedLimit);
        verify(builderMock).build(eq("rate_limit:10.0.0.1".getBytes(StandardCharsets.UTF_8)),
                ArgumentMatchers.<Supplier<BucketConfiguration>>any());
        verify(filterChain).doFilter(request, response);
    }
}
