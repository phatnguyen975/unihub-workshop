package com.unihub.workshop.shared.realtime.presentation;

import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.realtime.application.StreamSubscriptionService;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller exposing the Server-Sent Events handshake endpoint.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class StreamController {

    private final StreamSubscriptionService subscriptionService;

    public StreamController(StreamSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Establishes a real-time event streaming connection for the authenticated client.
     *
     * @param authentication the authenticated user principal from security context
     * @param response       the HTTP servlet response to configure streaming
     *                       headers
     * @return an SseEmitter streaming live platform events
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication, HttpServletResponse response) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        UUID userId = SecurityContextUtils.extractUserId(authentication);
        String role = null;
        String orgId = null;

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            role = jwtPrincipal.role();
            orgId = jwtPrincipal.org();
        }

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CONNECTION, "keep-alive");

        return subscriptionService.subscribe(userId, role, orgId);
    }
}
