package com.unihub.workshop.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.shared.exception.MetadataGenerator;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rate_limit:ip:";
    private static final String ERROR_TYPE = "urn:unihub:error:too_many_requests";
    private static final String ERROR_TITLE = "Too Many Requests";
    private static final String ERROR_DETAIL = "Too many requests. Please try again later.";
    private static final BucketConfiguration RATE_LIMIT_CONFIGURATION = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(100)
                    .refillIntervally(100, Duration.ofMinutes(1))
                    .build())
            .build();

    private final ProxyManager<String> rateLimitingProxyManager;
    private final ObjectMapper objectMapper;
    private final MetadataGenerator metadataGenerator;

    public RateLimitingFilter(
            ProxyManager<String> rateLimitingProxyManager,
            ObjectMapper objectMapper,
            MetadataGenerator metadataGenerator) {
        this.rateLimitingProxyManager = rateLimitingProxyManager;
        this.objectMapper = objectMapper;
        this.metadataGenerator = metadataGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String bucketKey = KEY_PREFIX + clientIp;
        BucketProxy bucket = rateLimitingProxyManager.getProxy(bucketKey, () -> RATE_LIMIT_CONFIGURATION);

        if (!bucket.tryConsume(1)) {
            writeTooManyRequests(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int separator = forwardedFor.indexOf(',');
            return separator >= 0 ? forwardedFor.substring(0, separator).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ApiResponse<Void> body = ApiResponse.failure(
                ERROR_DETAIL,
                metadataGenerator.create(HttpStatus.TOO_MANY_REQUESTS),
                new ApiResponse.ProblemDetails(
                        ERROR_TYPE,
                        ERROR_TITLE,
                        ERROR_DETAIL,
                        request.getRequestURI(),
                        List.of()));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
