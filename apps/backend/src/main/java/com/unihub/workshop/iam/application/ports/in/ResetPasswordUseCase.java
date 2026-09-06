package com.unihub.workshop.iam.application.ports.in;

public interface ResetPasswordUseCase {
    void resetPassword(String email, String otp, String newPassword);
}
