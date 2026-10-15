package com.unihub.workshop.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Verifies that paths listed under {@code rate-limit.excluded-paths} are completely
 * bypassed by {@link RateLimitFilter} — no bucket lookup, no token consumption,
 * no rate-limit response headers written.
 *
 * <p>This is the regression guard for F2 (Stripe webhook bypass) from the
 * {@code paid-workshop-registration} review findings.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterExcludedPathsTest {

    private static final String WEBHOOK_PATH = "/api/v1/payments/webhook";

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RateLimitMetrics rateLimitMetrics;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = RateLimitProperties.builder()
            .defaultTier(100, 100, Duration.ofSeconds(60))
            .excludedPaths(List.of(WEBHOOK_PATH))
            .build();

        // No ProxyManager needed — excluded paths must never reach bucket logic.
        filter = new RateLimitFilter(
            proxyManager,
            properties,
            rateLimitMetrics
        );
    }

    @Test
    @DisplayName(
        "shouldNotFilter returns true for the Stripe webhook path (F2 regression guard)"
    )
    void shouldNotFilter_webhookPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            WEBHOOK_PATH
        );
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/api/v1/registrations",
            "/api/v1/auth/login",
            "/api/v1/workshops",
        }
    )
    @DisplayName("shouldNotFilter returns false for non-excluded paths")
    void shouldNotFilter_nonExcludedPath_returnsFalse(String path)
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET",
            path
        );
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName(
        "Excluded path passes through filter chain without bucket interaction"
    )
    void excludedPath_passesFilterChain_withoutBucketLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            WEBHOOK_PATH
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // When shouldNotFilter returns true, OncePerRequestFilter skips doFilterInternal
        // and delegates directly to the chain. Verify no rate-limit headers are set.
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNull();
    }

    @Test
    @DisplayName("Multiple excluded paths are all bypassed")
    void multipleExcludedPaths_allBypassed() throws Exception {
        RateLimitProperties properties = RateLimitProperties.builder()
            .defaultTier(100, 100, Duration.ofSeconds(60))
            .excludedPaths(List.of(WEBHOOK_PATH, "/api/v1/health/**"))
            .build();
        RateLimitFilter multiFilter = new RateLimitFilter(
            proxyManager,
            properties,
            rateLimitMetrics
        );

        assertThat(
            multiFilter.shouldNotFilter(
                new MockHttpServletRequest("POST", WEBHOOK_PATH)
            )
        ).isTrue();
        assertThat(
            multiFilter.shouldNotFilter(
                new MockHttpServletRequest("GET", "/api/v1/health/liveness")
            )
        ).isTrue();
        assertThat(
            multiFilter.shouldNotFilter(
                new MockHttpServletRequest("GET", "/api/v1/registrations")
            )
        ).isFalse();
    }

    @Test
    @DisplayName("Empty excluded-paths list bypasses nothing")
    void emptyExcludedPaths_bypassesNothing() throws Exception {
        RateLimitProperties properties = RateLimitProperties.builder()
            .defaultTier(100, 100, Duration.ofSeconds(60))
            .build(); // no excludedPaths — defaults to List.of()
        RateLimitFilter filterWithNoExclusions = new RateLimitFilter(
            proxyManager,
            properties,
            rateLimitMetrics
        );

        assertThat(
            filterWithNoExclusions.shouldNotFilter(
                new MockHttpServletRequest("POST", WEBHOOK_PATH)
            )
        ).isFalse();
    }
}
