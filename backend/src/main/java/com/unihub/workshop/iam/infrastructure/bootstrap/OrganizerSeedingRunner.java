package com.unihub.workshop.iam.infrastructure.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.domain.events.OrganizerSeededEvent;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserStatus;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.iam.infrastructure.config.OrganizerSeedProperties;
import com.unihub.workshop.shared.exception.AppException;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OrganizerSeedingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizerSeedingRunner.class);
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
    private static final int PASSWORD_LENGTH = 16;

    private final OrganizerSeedProperties seedProperties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OrganizerSeedingRunner(
            OrganizerSeedProperties seedProperties,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate) {
        this.seedProperties = seedProperties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SeededOrganizer> organizers = readSeedOrganizers();
        for (SeededOrganizer organizer : organizers) {
            seedOrganizerIfAbsent(organizer);
        }
    }

    private void seedOrganizerIfAbsent(SeededOrganizer organizer) {
        String email = normalizeEmail(organizer.email());
        if (email == null || email.isBlank()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
                return;
            }

            String temporaryPassword = generateTemporaryPassword();
            // TEMP TEST ONLY: remove after QA because this exposes the plaintext password in logs
            log.info("TEMP TEST ONLY - Seeded organizer password for {}: {}", email, temporaryPassword);

            User user = new User();
            user.setId(UUID.randomUUID());
            user.setEmail(email);
            user.setFullName(normalizeRequiredText(organizer.fullName(), "full name"));
            user.setPhone(normalizeOptionalText(organizer.phone()));
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            user.setRole(UserRole.ORGANIZER);
            user.setStatus(UserStatus.ACTIVE);
            user.setTemporaryPassword(true);
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);

            try {
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException ex) {
                log.info("Seed organizer already exists, skipping {}", email);
                return;
            }

            publishEvent(
                    new OrganizerSeededEvent(user.getId(), user.getEmail(), user.getFullName(), temporaryPassword));
        });
    }

    private List<SeededOrganizer> readSeedOrganizers() {
        String raw = normalizeSeedJson(seedProperties.getOrganizersJson());
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<SeededOrganizer>>() {
            });
        } catch (Exception ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid organizer seed configuration", "seed_config");
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(PASSWORD_ALPHABET.length());
            password.append(PASSWORD_ALPHABET.charAt(index));
        }
        return password.toString();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName, "seed_config");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName, "seed_config");
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void publishEvent(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Event publication failed", "event_infra");
        }
    }

    private String normalizeSeedJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    public record SeededOrganizer(String email, String fullName, String phone) {
    }
}
