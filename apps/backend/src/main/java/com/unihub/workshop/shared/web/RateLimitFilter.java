package com.unihub.workshop.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.shared.config.RateLimitProperties;
import com.unihub.workshop.shared.observability.RateLimitMetrics;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher;
    private final RateLimitMetrics metrics;
    private final ObjectMapper objectMapper;

    private final BucketConfiguration registrationConfig;
    private final BucketConfiguration browsingConfig;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, RateLimitProperties properties,
            RateLimitMetrics metrics) {
        this.proxyManager = proxyManager;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper();
        this.pathMatcher = new AntPathMatcher();

        this.registrationConfig = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(properties.registrationTier().capacity())
                        .refillGreedy(properties.registrationTier().refillTokens(),
                                properties.registrationTier().refillDuration()))
                .build();

        this.browsingConfig = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(properties.browsingTier().capacity())
                        .refillGreedy(properties.browsingTier().refillTokens(),
                                properties.browsingTier().refillDuration()))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String bucketKey = extractKey(request);
        RateLimitProperties.Tier tier = resolveTier(request.getRequestURI());
        BucketConfiguration configuration = (tier == properties.registrationTier()) ? registrationConfig
                : browsingConfig;

        try {
            var bucket = proxyManager.builder()
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

                String traceId = MDC.get("traceId");
                if (traceId == null) {
                    traceId = UUID.randomUUID().toString();
                }

                String detail = String.format("Rate limit exceeded. Try again in %d seconds.",
                        TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));

                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail);
                problem.setType(URI.create("urn:unihub:error:rate-limit"));
                problem.setTitle("Too Many Requests");
                problem.setInstance(URI.create(request.getRequestURI()));
                problem.setProperty("traceId", traceId);

                response.getWriter().write(objectMapper.writeValueAsString(problem));
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
