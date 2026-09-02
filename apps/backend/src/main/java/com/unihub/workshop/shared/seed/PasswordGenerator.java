package com.unihub.workshop.shared.seed;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordGenerator {

    private final Argon2PasswordEncoder encoder;
    private final SecureRandom secureRandom;

    public PasswordGenerator() {
        // Defaults: salt=16, hash=32, parallel=1, memory=16384, iter=2
        this.encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.secureRandom = new SecureRandom();
    }

    public GeneratedPassword generate() {
        byte[] randomBytes = new byte[18];
        secureRandom.nextBytes(randomBytes);
        String rawPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String hash = encoder.encode(rawPassword);
        return new GeneratedPassword(rawPassword, hash);
    }

    public record GeneratedPassword(String plaintext, String hash) {
    }
}
