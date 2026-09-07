package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.*;
import com.unihub.workshop.shared.security.PublicKeyService;

import static org.mockito.Mockito.mock;

public class AuthControllerTestBuilder {

    private LoginUseCase loginUseCase = mock(LoginUseCase.class);
    private RefreshTokenUseCase refreshTokenUseCase = mock(RefreshTokenUseCase.class);
    private LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
    private PublicKeyService publicKeyService = mock(PublicKeyService.class);
    private RegisterUserUseCase registerUserUseCase = mock(RegisterUserUseCase.class);
    private ActivateAccountUseCase activateAccountUseCase = mock(ActivateAccountUseCase.class);
    private ResendOtpUseCase resendOtpUseCase = mock(ResendOtpUseCase.class);
    private ResetPasswordUseCase resetPasswordUseCase = mock(ResetPasswordUseCase.class);
    private ChangeTemporaryPasswordUseCase changeTemporaryPasswordUseCase = mock(ChangeTemporaryPasswordUseCase.class);
    private GetActiveSessionsUseCase getActiveSessionsUseCase = mock(GetActiveSessionsUseCase.class);
    private RevokeSessionUseCase revokeSessionUseCase = mock(RevokeSessionUseCase.class);

    public static AuthControllerTestBuilder builder() {
        return new AuthControllerTestBuilder();
    }

    public AuthControllerTestBuilder withLoginUseCase(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
        return this;
    }

    public AuthControllerTestBuilder withRefreshTokenUseCase(RefreshTokenUseCase refreshTokenUseCase) {
        this.refreshTokenUseCase = refreshTokenUseCase;
        return this;
    }

    public AuthControllerTestBuilder withLogoutUseCase(LogoutUseCase logoutUseCase) {
        this.logoutUseCase = logoutUseCase;
        return this;
    }

    public AuthControllerTestBuilder withPublicKeyService(PublicKeyService publicKeyService) {
        this.publicKeyService = publicKeyService;
        return this;
    }

    public AuthControllerTestBuilder withRegisterUserUseCase(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        return this;
    }

    public AuthControllerTestBuilder withActivateAccountUseCase(ActivateAccountUseCase activateAccountUseCase) {
        this.activateAccountUseCase = activateAccountUseCase;
        return this;
    }

    public AuthControllerTestBuilder withResendOtpUseCase(ResendOtpUseCase resendOtpUseCase) {
        this.resendOtpUseCase = resendOtpUseCase;
        return this;
    }

    public AuthControllerTestBuilder withResetPasswordUseCase(ResetPasswordUseCase resetPasswordUseCase) {
        this.resetPasswordUseCase = resetPasswordUseCase;
        return this;
    }

    public AuthControllerTestBuilder withChangeTemporaryPasswordUseCase(
            ChangeTemporaryPasswordUseCase changeTemporaryPasswordUseCase) {
        this.changeTemporaryPasswordUseCase = changeTemporaryPasswordUseCase;
        return this;
    }

    public AuthControllerTestBuilder withGetActiveSessionsUseCase(GetActiveSessionsUseCase getActiveSessionsUseCase) {
        this.getActiveSessionsUseCase = getActiveSessionsUseCase;
        return this;
    }

    public AuthControllerTestBuilder withRevokeSessionUseCase(RevokeSessionUseCase revokeSessionUseCase) {
        this.revokeSessionUseCase = revokeSessionUseCase;
        return this;
    }

    public AuthController build() {
        return new AuthController(
                loginUseCase,
                refreshTokenUseCase,
                logoutUseCase,
                publicKeyService,
                registerUserUseCase,
                activateAccountUseCase,
                resendOtpUseCase,
                resetPasswordUseCase,
                changeTemporaryPasswordUseCase,
                getActiveSessionsUseCase,
                revokeSessionUseCase);
    }
}
