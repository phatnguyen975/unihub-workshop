package com.unihub.workshop.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ClientIpExtractor;
import com.unihub.workshop.shared.web.RequestLoggingFilter;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(
        RateLimitFilter.class
    );

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher;
    private final RateLimitMetrics metrics;
    private final ObjectMapper objectMapper;
    private final BucketConfiguration defaultConfig;
    private final List<ConfiguredTier> configuredTiers;

    private record ConfiguredTier(
        String name,
        RateLimitProperties.Tier tier,
        BucketConfiguration config,
        List<String> pathPatterns
    ) {}

    private record ResolvedTier(
        RateLimitProperties.Tier tier,
        BucketConfiguration config,
        String tag
    ) {}

    public RateLimitFilter(
        ProxyManager<byte[]> proxyManager,
        RateLimitProperties properties,
        RateLimitMetrics metrics
    ) {
        this.proxyManager = proxyManager;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper();
        this.pathMatcher = new AntPathMatcher();

        this.defaultConfig = buildBucketConfig(properties.defaultTier());
        if (properties.tiers() != null && !properties.tiers().isEmpty()) {
            this.configuredTiers = properties
                .tiers()
                .entrySet()
                .stream()
                .map(entry ->
                    new ConfiguredTier(
                        entry.getKey(),
                        entry.getValue().toTier(),
                        buildBucketConfig(entry.getValue().toTier()),
                        entry.getValue().pathPatterns()
                    )
                )
                .toList();
        } else {
            this.configuredTiers = java.util.List.of();
        }
    }

    private static BucketConfiguration buildBucketConfig(
        RateLimitProperties.Tier tier
    ) {
        return BucketConfiguration.builder()
            .addLimit(limit ->
                limit
                    .capacity(tier.capacity())
                    .refillGreedy(tier.refillTokens(), tier.refillDuration())
            )
            .build();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String bucketKey = extractKey(request);
        ResolvedTier resolved = resolveTier(request.getRequestURI());

        try {
            var bucket = proxyManager
                .builder()
                .build(
                    bucketKey.getBytes(StandardCharsets.UTF_8),
                    resolved::config
                );
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            response.setHeader(
                "X-RateLimit-Limit",
                String.valueOf(resolved.tier().capacity())
            );
            response.setHeader(
                "X-RateLimit-Remaining",
                String.valueOf(probe.getRemainingTokens())
            );
            response.setHeader(
                "X-RateLimit-Retry-After",
                String.valueOf(
                    TimeUnit.NANOSECONDS.toSeconds(
                        probe.getNanosToWaitForRefill()
                    )
                )
            );

            if (probe.isConsumed()) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(
                    MediaType.APPLICATION_PROBLEM_JSON_VALUE
                );

                String traceId = RequestLoggingFilter.getCurrentTraceId();
                String processedBy =
                    RequestLoggingFilter.getCurrentProcessedBy();

                String detail = String.format(
                    "Rate limit exceeded. Try again in %d seconds.",
                    TimeUnit.NANOSECONDS.toSeconds(
                        probe.getNanosToWaitForRefill()
                    )
                );

                ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    detail
                );
                problem.setType(URI.create("urn:unihub:error:rate-limited"));
                problem.setTitle("Too Many Requests");
                problem.setInstance(URI.create(request.getRequestURI()));

                ApiResponse<Void> apiResponse = ApiResponse.error(problem);
                apiResponse.setMetadata(
                    new ApiResponse.Metadata(traceId, processedBy)
                );

                response
                    .getWriter()
                    .write(objectMapper.writeValueAsString(apiResponse));
            }
        } catch (Exception ex) {
            log.warn(
                "Redis unavailable for rate limiting. Failing open. Key: {}, Path: {}, Error: {}",
                bucketKey,
                request.getRequestURI(),
                ex.getClass().getName()
            );
            metrics.incrementRedisUnavailable(resolved.tag());
            filterChain.doFilter(request, response);
        }
    }

    private ResolvedTier resolveTier(String uri) {
        for (ConfiguredTier configuredTier : configuredTiers) {
            for (String pattern : configuredTier.pathPatterns()) {
                if (pathMatcher.match(pattern, uri)) {
                    return new ResolvedTier(
                        configuredTier.tier(),
                        configuredTier.config(),
                        configuredTier.name()
                    );
                }
            }
        }
        return new ResolvedTier(
            properties.defaultTier(),
            defaultConfig,
            "default"
        );
    }

    private String extractKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                try {
                    String payload = new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        StandardCharsets.UTF_8
                    );
                    var node = objectMapper.readTree(payload);
                    var subNode = node.get("sub");
                    if (subNode != null && !subNode.asText().isBlank()) {
                        return "rate_limit:" + subNode.asText();
                    }
                } catch (Exception ignored) {
                    // Fallback to IP keying on malformed token
                }
            }
        }
        // Fallback for unauthenticated requests
        return "rate_limit:" + ClientIpExtractor.extractIp(request);
    }
}
