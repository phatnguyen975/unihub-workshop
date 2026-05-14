package com.unihub.workshop.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.shared.exception.MetadataGenerator;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TemporaryPasswordEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final MetadataGenerator metadataGenerator;
    private final ObjectMapper objectMapper;

    public TemporaryPasswordEnforcementFilter(
            UserRepository userRepository,
            MetadataGenerator metadataGenerator,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.metadataGenerator = metadataGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String subject) || subject.isBlank() || "anonymousUser".equals(subject)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            filterChain.doFilter(request, response);
            return;
        }

        var user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isTemporaryPassword()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiResponse.ProblemDetails error = new ApiResponse.ProblemDetails(
                "https://api.unihub.local/problems/forbidden",
                "Forbidden",
                "Temporary password accounts can access only Update Password and Logout endpoints.",
                request.getRequestURI(),
                null);

        ApiResponse<Void> body = ApiResponse.failure(
                "Password update required",
                metadataGenerator.create(HttpStatus.FORBIDDEN),
                error);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            return true;
        }
        return path.startsWith("/api/v1/auth/");
    }

    private boolean isAllowedPath(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        if (path == null || method == null) {
            return false;
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        return ("PUT".equalsIgnoreCase(method) && "/api/v1/users/me/password".equals(path))
                || ("POST".equalsIgnoreCase(method) && "/api/v1/users/me/logout".equals(path));
    }
}
