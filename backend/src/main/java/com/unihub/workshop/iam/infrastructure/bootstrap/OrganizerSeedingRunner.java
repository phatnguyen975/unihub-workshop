package com.unihub.workshop.iam.infrastructure.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.infrastructure.config.OrganizerSeedProperties;
import com.unihub.workshop.shared.exception.AppException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrganizerSeedingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizerSeedingRunner.class);
    private final OrganizerSeedProperties seedProperties;
    private final ObjectMapper objectMapper;
    private final OrganizerSeedingService seedingService;

    @Override
    public void run(ApplicationArguments args) {
        List<SeededOrganizer> organizers = readSeedOrganizers();
        for (SeededOrganizer organizer : organizers) {
            seedOrganizerIfAbsent(organizer);
        }
    }

    private void seedOrganizerIfAbsent(SeededOrganizer organizer) {
        try {
            seedingService.createIfAbsent(organizer);
        } catch (DataIntegrityViolationException ex) {
            String email = normalizeEmail(organizer.email());
            log.info("Organizer {} was already seeded by another instance. Skipping.", email);
        }
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
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
