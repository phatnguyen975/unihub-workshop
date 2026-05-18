package com.unihub.workshop.iam.infrastructure.bootstrap;

import com.unihub.workshop.iam.domain.events.OrganizerSeededEvent;
import com.unihub.workshop.iam.domain.events.UserAccountCreatedEvent;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserStatus;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.shared.exception.AppException;
import java.security.SecureRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizerSeedingService {

    private static final Logger log = LoggerFactory.getLogger(OrganizerSeedingService.class);
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
    private static final int PASSWORD_LENGTH = 16;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(OrganizerSeedingRunner.SeededOrganizer organizer) {
        String email = normalizeEmail(organizer.email());
        if (email == null || email.isBlank()) {
            return;
        }

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
            throw ex;
        }

        try {
            eventPublisher.publishEvent(
                    new OrganizerSeededEvent(user.getId(), user.getEmail(), user.getFullName(), temporaryPassword));
            eventPublisher.publishEvent(new UserAccountCreatedEvent(
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole().name(),
                    temporaryPassword));
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Event publication failed", "event_infra");
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
}
