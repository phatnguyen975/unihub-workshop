package com.unihub.workshop.iam.application.utils;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OtpService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;

    private final String hmacSecret;
    private final int ttlMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
        @Value("${app.otp.hmac-secret:}") String hmacSecret,
        @Value("${app.otp.ttl-minutes:15}") int ttlMinutes
    ) {
        this.hmacSecret = hmacSecret;
        this.ttlMinutes = ttlMinutes > 0 ? ttlMinutes : 15;
    }

    @PostConstruct
    public void init() {
        if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                "OTP HMAC secret must be configured via app.otp.hmac-secret and cannot be blank"
            );
        }
        if (hmacSecret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "OTP HMAC secret must be at least " +
                    MIN_SECRET_LENGTH +
                    " characters long"
            );
        }
    }

    public Instant calculateExpiryInstant() {
        return Instant.now().plus(Duration.ofMinutes(ttlMinutes));
    }

    public String generateOtp() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    public String hashOtp(String otp) {
        if (otp == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to compute HMAC-SHA256 for OTP",
                e
            );
        }
    }

    public boolean verifyOtp(String rawOtp, String expectedHash) {
        if (rawOtp == null || expectedHash == null) {
            return false;
        }
        String calculatedHash = hashOtp(rawOtp);
        if (calculatedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
            calculatedHash.getBytes(StandardCharsets.UTF_8),
            expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
