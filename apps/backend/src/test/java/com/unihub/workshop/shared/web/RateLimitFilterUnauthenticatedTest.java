package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.config.RateLimitProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterUnauthenticatedTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> builderMock;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private RateLimitProperties properties;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        RateLimitProperties.Tier regTier = new RateLimitProperties.Tier(5, 5, Duration.ofSeconds(10));
        RateLimitProperties.Tier browsTier = new RateLimitProperties.Tier(100, 100, Duration.ofSeconds(60));
        properties = new RateLimitProperties(regTier, browsTier);

        filter = new RateLimitFilter(proxyManager, properties);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        io.github.bucket4j.distributed.BucketProxy bucket = mock(io.github.bucket4j.distributed.BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);

        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(99L);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);

        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        when(proxyManager.builder()).thenReturn(builderMock);
        when(builderMock.build(any(byte[].class), org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
    }

    @Test
    void shouldKeyByIpWhenNoAuthHeader() throws Exception {
        request.setRemoteAddr("192.168.1.50");

        filter.doFilterInternal(request, response, filterChain);

        verify(builderMock).build(eq("rate_limit:192.168.1.50".getBytes(StandardCharsets.UTF_8)),
                org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldKeyByIpFromXForwardedForWhenNoAuthHeader() throws Exception {
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");

        filter.doFilterInternal(request, response, filterChain);

        verify(builderMock).build(eq("rate_limit:203.0.113.1".getBytes(StandardCharsets.UTF_8)),
                org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldFallbackToIpWhenJwtIsMalformed() throws Exception {
        request.setRemoteAddr("192.168.1.50");
        request.addHeader("Authorization", "Bearer not.a.valid.jwt");

        filter.doFilterInternal(request, response, filterChain);

        // Fallback to IP key, without throwing 400/401
        verify(builderMock).build(eq("rate_limit:192.168.1.50".getBytes(StandardCharsets.UTF_8)),
                org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any());
        verify(filterChain).doFilter(request, response);
    }
}
