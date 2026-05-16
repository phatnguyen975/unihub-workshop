package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.LoginRequest;
import com.unihub.workshop.iam.application.dto.LoginResponse;
import com.unihub.workshop.iam.application.dto.RefreshRequest;
import com.unihub.workshop.iam.application.dto.RefreshResponse;
import com.unihub.workshop.iam.application.dto.ForgotPasswordRequest;
import com.unihub.workshop.iam.application.dto.ForgotPasswordResponse;
import com.unihub.workshop.iam.application.dto.ResetPasswordRequest;
import com.unihub.workshop.iam.application.dto.ResendOtpRequest;
import com.unihub.workshop.iam.application.dto.RegisterRequest;
import com.unihub.workshop.iam.application.dto.RegisterResponse;
import com.unihub.workshop.iam.application.dto.VerifyOtpRequest;
import com.unihub.workshop.iam.application.dto.VerifyOtpResponse;
import com.unihub.workshop.shared.web.UseCaseResult;

public interface AuthUseCase {

    UseCaseResult<RegisterResponse> register(RegisterRequest request);

    UseCaseResult<VerifyOtpResponse> verifyOtp(VerifyOtpRequest request);

    UseCaseResult<Void> resendOtp(ResendOtpRequest request);

    UseCaseResult<LoginResponse> login(LoginRequest request);

    UseCaseResult<RefreshResponse> refresh(RefreshRequest request);

    UseCaseResult<ForgotPasswordResponse> forgotPassword(ForgotPasswordRequest request);

    UseCaseResult<Void> resetPassword(ResetPasswordRequest request);
}
