package com.unihub.workshop.notifications.application;

import com.unihub.workshop.iam.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RegistrationQrCodeJwtService {

    private final JwtTokenProvider jwtTokenProvider;

    public RegistrationQrCodeJwtService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String generate(UUID registrationId, UUID userId, UUID workshopId) {
        return jwtTokenProvider.generateTicketToken(registrationId, workshopId, userId);
    }
}
