package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.presentation.dto.LoginRequest;
import com.unihub.workshop.iam.presentation.dto.LoginResponse;
import com.unihub.workshop.shared.web.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();

        LoginUseCase.LoginResult result = loginUseCase.login(
                request.email(),
                request.password(),
                userAgent,
                ipAddress);

        LoginResponse response = new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn());

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
}
