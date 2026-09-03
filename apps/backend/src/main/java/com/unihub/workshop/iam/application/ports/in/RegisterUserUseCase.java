package com.unihub.workshop.iam.application.ports.in;

import java.util.UUID;

public interface RegisterUserUseCase {
    UUID register(String email, String password, String fullName);
}
