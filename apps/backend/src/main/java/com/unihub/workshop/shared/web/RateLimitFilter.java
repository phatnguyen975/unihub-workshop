package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.config.RateLimitProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher;
    private final com.unihub.workshop.shared.observability.RateLimitMetrics metrics;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, RateLimitProperties properties,
            com.unihub.workshop.shared.observability.RateLimitMetrics metrics) {
        this.proxyManager = proxyManager;
        this.properties = properties;
        this.metrics = metrics;
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String bucketKey = extractKey(request);

        RateLimitProperties.Tier tier = resolveTier(request.getRequestURI());

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(tier.capacity()).refillGreedy(tier.refillTokens(),
                        tier.refillDuration()))
                .build();

        try {
            Bucket bucket = proxyManager.builder()
                    .build(bucketKey.getBytes(StandardCharsets.UTF_8), () -> configuration);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            response.setHeader("X-RateLimit-Limit", String.valueOf(tier.capacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-RateLimit-Retry-After",
                    String.valueOf(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill())));

            if (probe.isConsumed()) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

                String traceId = org.slf4j.MDC.get("traceId");
                if (traceId == null) {
                    traceId = java.util.UUID.randomUUID().toString();
                }

                String problemJson = String.format(
                        "{\"type\":\"urn:unihub:error:rate-limit\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again in %d seconds.\",\"instance\":\"%s\",\"traceId\":\"%s\"}",
                        TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()),
                        request.getRequestURI(),
                        traceId);
                response.getWriter().write(problemJson);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable for rate limiting. Failing open. Key: {}, Path: {}, Error: {}",
                    bucketKey, request.getRequestURI(), ex.getClass().getName());
            String tierTag = (tier == properties.registrationTier()) ? "registration" : "browsing";
            metrics.incrementRedisUnavailable(tierTag);
            filterChain.doFilter(request, response);
        }
    }

    private RateLimitProperties.Tier resolveTier(String uri) {
        for (String pattern : properties.registrationPathPatterns()) {
            if (pathMatcher.match(pattern, uri)) {
                return properties.registrationTier();
            }
        }
        return properties.browsingTier();
    }

    private String extractKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                try {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    // Minimal parsing to extract "sub" claim
                    int subIndex = payload.indexOf("\"sub\":\"");
                    if (subIndex != -1) {
                        int endQuote = payload.indexOf("\"", subIndex + 7);
                        if (endQuote != -1) {
                            String sub = payload.substring(subIndex + 7, endQuote);
                            return "rate_limit:" + sub;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        // Fallback for unauthenticated requests
        return "rate_limit:" + ClientIpExtractor.extractIp(request);
    }
}
