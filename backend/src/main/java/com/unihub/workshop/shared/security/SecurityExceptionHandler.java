package com.unihub.workshop.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.shared.exception.MetadataGenerator;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MetadataGenerator metadataGenerator;
    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(MetadataGenerator metadataGenerator, ObjectMapper objectMapper) {
        this.metadataGenerator = metadataGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        writeFailure(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        writeFailure(response, HttpStatus.FORBIDDEN, "Access Denied");
    }

    private void writeFailure(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse.ProblemDetails error = new ApiResponse.ProblemDetails(
                "urn:unihub:error:security",
                status == HttpStatus.FORBIDDEN ? "Forbidden" : "Unauthorized",
                message,
                "",
                null);
        ApiResponse<Void> body = ApiResponse.failure(message, metadataGenerator.create(status), error);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
