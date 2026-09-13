package com.unihub.workshop.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTierRoutingTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder<byte[]> builderMock;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = RateLimitProperties.builder()
            .defaultTier(100, 100, Duration.ofSeconds(60))
            .addTier(
                "registration",
                5,
                5,
                Duration.ofSeconds(10),
                List.of("/api/v1/registrations/**")
            )
            .addTier(
                "auth",
                20,
                20,
                Duration.ofSeconds(60),
                List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/activate",
                    "/api/v1/auth/otp/**",
                    "/api/v1/auth/password/**",
                    "/api/v1/auth/refresh"
                )
            )
            .addTier(
                "stream",
                10,
                10,
                Duration.ofSeconds(60),
                List.of("/api/v1/stream/**")
            )
            .build();

        filter = new RateLimitFilter(
            proxyManager,
            properties,
            mock(RateLimitMetrics.class)
        );

        BucketProxy bucket = mock(BucketProxy.class);
        ConsumptionProbe probe = mock(ConsumptionProbe.class);

        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(1L);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        when(proxyManager.builder()).thenReturn(builderMock);
        when(
            builderMock.build(
                any(byte[].class),
                ArgumentMatchers.<Supplier<BucketConfiguration>>any()
            )
        ).thenReturn(bucket);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/registrations, 5",
        "/api/v1/registrations/123, 5",
        "/api/v1/auth/login, 20",
        "/api/v1/auth/register, 20",
        "/api/v1/auth/activate, 20",
        "/api/v1/auth/otp/resend, 20",
        "/api/v1/auth/password/reset, 20",
        "/api/v1/auth/password/temporary, 20",
        "/api/v1/auth/refresh, 20",
        "/api/v1/auth/logout, 100",
        "/api/v1/auth/public-key, 100",
        "/api/v1/auth/sessions, 100",
        "/api/v1/auth/sessions/s-123, 100",
        "/api/v1/users/me, 100",
        "/api/v1/users/me/password, 100",
        "/api/v1/admin/organizations/users, 100",
        "/api/v1/admin/organizations/users/u-123, 100",
        "/api/v1/workshops, 100",
        "/api/v1/admin/ingestion-jobs, 100",
        "/api/v1/admin/ingestion-jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890/reject-artifact, 100",
        "/api/v1/unknown, 100",
        "/api/v1/stream/events, 10",
    })
    void shouldRouteToCorrectTierBasedOnPath(String path, String expectedLimit)
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            path
        );
        request.setRemoteAddr("10.0.0.1"); // force IP fallback for simplicity
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Verify the X-RateLimit-Limit header matches the expected tier capacity
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo(
            expectedLimit
        );
        verify(builderMock).build(
            eq("rate_limit:10.0.0.1".getBytes(StandardCharsets.UTF_8)),
            ArgumentMatchers.<Supplier<BucketConfiguration>>any()
        );
        verify(filterChain).doFilter(request, response);
    }
}
