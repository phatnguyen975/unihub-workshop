package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.in.ChangeTemporaryPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.GetActiveSessionsUseCase;
import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.RevokeAllOtherSessionsUseCase;
import com.unihub.workshop.iam.application.ports.in.RevokeSessionUseCase;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.presentation.dto.ActivateAccountRequest;
import com.unihub.workshop.iam.presentation.dto.ChangeTemporaryPasswordRequest;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.iam.presentation.dto.LoginRequest;
import com.unihub.workshop.iam.presentation.dto.LoginResponse;
import com.unihub.workshop.iam.presentation.dto.LogoutRequest;
import com.unihub.workshop.iam.presentation.dto.PublicKeyResponse;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenRequest;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenResponse;
import com.unihub.workshop.iam.presentation.dto.RegisterRequest;
import com.unihub.workshop.iam.presentation.dto.RegisterResponse;
import com.unihub.workshop.iam.presentation.dto.ResendOtpRequest;
import com.unihub.workshop.iam.presentation.dto.ResetPasswordRequest;
import com.unihub.workshop.iam.presentation.dto.SessionCursor;
import com.unihub.workshop.iam.presentation.dto.SessionResponse;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final PublicKeyService publicKeyService;
    private final RegisterUserUseCase registerUserUseCase;
    private final ActivateAccountUseCase activateAccountUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangeTemporaryPasswordUseCase changeTemporaryPasswordUseCase;
    private final GetActiveSessionsUseCase getActiveSessionsUseCase;
    private final RevokeSessionUseCase revokeSessionUseCase;
    private final RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = registerUserUseCase.register(request.email(), request.password(), request.fullName());
        return ApiResponse.success(new RegisterResponse(userId),
                "Registration successful. Please check your email for the activation code.");
    }

    @PostMapping("/activate")
    public ApiResponse<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        activateAccountUseCase.activate(request.email(), request.otp());
        return ApiResponse.success(null, "Account activated successfully. You may now log in.");
    }

    @PostMapping("/otp/resend")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        resendOtpUseCase.resendOtp(request.email());
        return ApiResponse.success(null, "A verification code has been sent to your email.");
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.email(), request.otp(), request.newPassword());
        return ApiResponse.success(null, "Password reset successfully. Please sign in with your new password.");
    }

    @PostMapping("/password/temporary")
    public ApiResponse<LoginResponse> changeTemporaryPassword(
            @Valid @RequestBody ChangeTemporaryPasswordRequest request,
            HttpServletRequest httpRequest) {
        ChangeTemporaryPasswordUseCase.ChangeTemporaryPasswordResult result = changeTemporaryPasswordUseCase
                .changeTemporaryPassword(
                        request.email(),
                        request.currentPassword(),
                        request.newPassword(),
                        httpRequest.getHeader("User-Agent"),
                        httpRequest.getRemoteAddr());

        return ApiResponse.success(
                LoginResponse.from(result),
                "Password updated successfully. Welcome to UniHub!");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginUseCase.LoginResult result = loginUseCase.login(
                request.email(),
                request.password(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr());

        return ApiResponse.success(LoginResponse.from(result), "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        RefreshTokenUseCase.RefreshResult result = refreshTokenUseCase.refresh(
                request.refreshToken(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr());

        return ApiResponse.success(RefreshTokenResponse.from(result), "Token refreshed");
    }

    @GetMapping("/public-key")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<PublicKeyResponse> getPublicKey() {
        return ApiResponse.success(PublicKeyResponse.from(publicKeyService), "Public key retrieved successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String refreshToken = request != null ? request.refreshToken() : null;

        logoutUseCase.logout(
                jwt.getTokenValue(),
                jwt.getId(),
                jwt.getExpiresAt(),
                UUID.fromString(jwt.getSubject()),
                refreshToken);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ApiResponse<SessionResponse> getActiveSessions(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        UUID userId;
        UUID currentSessionId = null;

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            userId = UUID.fromString(jwtPrincipal.sub());
            if (jwtPrincipal.sid() != null && !jwtPrincipal.sid().isBlank()) {
                currentSessionId = UUID.fromString(jwtPrincipal.sid());
            }
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            userId = UUID.fromString(jwt.getSubject());
            String sid = jwt.getClaimAsString("sid");
            if (sid != null && !sid.isBlank()) {
                currentSessionId = UUID.fromString(sid);
            }
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }

        CursorPage<ActiveSession> page = getActiveSessionsUseCase.getActiveSessions(userId, currentSessionId, cursor,
                size);

        List<SessionResponse.SessionItem> items = page.items().stream()
                .map(s -> new SessionResponse.SessionItem(
                        s.id(),
                        s.deviceInfo(),
                        s.ipAddress(),
                        s.createdAt(),
                        s.isCurrentSession()))
                .toList();

        SessionResponse response = new SessionResponse(
                items,
                new SessionCursor(page.nextCursor(), page.hasMore()));

        return ApiResponse.success(response, "Active sessions retrieved");
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        UUID userId;
        UUID currentSessionId = null;
        String currentJti = null;
        Instant exp = null;

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            userId = UUID.fromString(jwtPrincipal.sub());
            if (jwtPrincipal.sid() != null && !jwtPrincipal.sid().isBlank()) {
                currentSessionId = UUID.fromString(jwtPrincipal.sid());
            }
            currentJti = jwtPrincipal.jti();
            exp = jwtPrincipal.exp();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            userId = UUID.fromString(jwt.getSubject());
            String sid = jwt.getClaimAsString("sid");
            if (sid != null && !sid.isBlank()) {
                currentSessionId = UUID.fromString(sid);
            }
            currentJti = jwt.getId();
            exp = jwt.getExpiresAt();
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }

        revokeSessionUseCase.revokeSession(userId, id, currentSessionId, currentJti, exp);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllOtherSessions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        UUID userId;
        UUID currentSessionId = null;

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            userId = UUID.fromString(jwtPrincipal.sub());
            if (jwtPrincipal.sid() != null && !jwtPrincipal.sid().isBlank()) {
                try {
                    currentSessionId = UUID.fromString(jwtPrincipal.sid());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid session ID in token");
                }
            }
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            userId = UUID.fromString(jwt.getSubject());
            String sid = jwt.getClaimAsString("sid");
            if (sid != null && !sid.isBlank()) {
                try {
                    currentSessionId = UUID.fromString(sid);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid session ID in token");
                }
            }
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }

        if (currentSessionId == null) {
            throw new BadRequestException("Current session ID (sid) is required for bulk revocation");
        }

        revokeAllOtherSessionsUseCase.revokeAllOtherSessions(userId, currentSessionId);

        return ResponseEntity.noContent().build();
    }
}
