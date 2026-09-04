package com.unihub.workshop.iam.application.ports.in;

public interface ActivateAccountUseCase {
    void activate(String email, String otp);
}
