package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.repository.OtpGenerator;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecureOtpGenerator implements OtpGenerator {

    private final SecureRandom secureRandom = new SecureRandom();
    private final String fixedOtp;

    public SecureOtpGenerator(@Value("${app.otp.fixed:}") String fixedOtp) {
        this.fixedOtp = fixedOtp == null ? "" : fixedOtp.trim();
    }

    @Override
    public String generateOtp() {
        if (!fixedOtp.isBlank()) {
            return fixedOtp;
        }
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
