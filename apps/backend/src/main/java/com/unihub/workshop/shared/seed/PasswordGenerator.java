package com.unihub.workshop.shared.seed;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordGenerator {

    private final PasswordEncoder encoder;
    private final SecureRandom secureRandom;

    public PasswordGenerator(PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(
            encoder,
            "PasswordEncoder must not be null"
        );
        this.secureRandom = new SecureRandom();
    }

    public GeneratedPassword generate() {
        byte[] randomBytes = new byte[18];
        secureRandom.nextBytes(randomBytes);
        String rawPassword = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes);

        String hash = encoder.encode(rawPassword);
        return new GeneratedPassword(rawPassword, hash);
    }

    public record GeneratedPassword(String plaintext, String hash) {}
}
