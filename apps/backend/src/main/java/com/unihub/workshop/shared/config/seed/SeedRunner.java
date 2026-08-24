package com.unihub.workshop.shared.config.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SeedProperties.class)
@Order(10)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedProperties seedProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordGenerator passwordGenerator;

    public SeedRunner(SeedProperties seedProperties, JdbcTemplate jdbcTemplate, PasswordGenerator passwordGenerator) {
        this.seedProperties = seedProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.passwordGenerator = passwordGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<OrganizationSeedEntry> organizations = parseJson(seedProperties.organizations(), new TypeReference<>() {
        });
        List<OrganizerSeedEntry> organizers = parseJson(seedProperties.rootOrganizers(), new TypeReference<>() {
        });

        seedOrganizations(organizations);
        seedOrganizers(organizers);

        validateOrganizationsExist();
    }

    private void seedOrganizations(List<OrganizationSeedEntry> organizations) {
        for (OrganizationSeedEntry org : organizations) {
            String sql = "INSERT INTO organizations (name, slug) VALUES (?, ?) ON CONFLICT (slug) DO NOTHING";
            int rowsAffected = jdbcTemplate.update(sql, org.name(), org.slug());

            if (rowsAffected > 0) {
                log.info("Seeded organization: {}", org.slug());
            } else {
                log.debug("Skipped existing organization: {}", org.slug());
            }
        }
    }

    private void seedOrganizers(List<OrganizerSeedEntry> organizers) {
        for (OrganizerSeedEntry organizer : organizers) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)", Boolean.class, organizer.email());
            if (Boolean.TRUE.equals(exists)) {
                log.debug("Skipped existing root organizer: {}", organizer.email());
                continue;
            }

            UUID orgId = resolveOrganizationId(organizer.orgSlug());
            PasswordGenerator.GeneratedPassword generatedPassword = passwordGenerator.generate();

            String sql = "INSERT INTO users (organization_id, role, email, password_hash, full_name, is_temporary_password) "
                    + "VALUES (?, 'ORGANIZER', ?, ?, ?, true) ON CONFLICT (email) DO NOTHING";
            int rowsAffected = jdbcTemplate.update(sql, orgId, organizer.email(), generatedPassword.hash(),
                    organizer.displayName());

            if (rowsAffected > 0) {
                // TODO: remove plaintext password from logs in production
                // TODO: publish domain event or send notification email to organizer.email()
                log.info("Seeded root organizer: {} with temporary password: {}", organizer.email(),
                        generatedPassword.plaintext());
            } else {
                log.debug("Skipped existing root organizer: {}", organizer.email());
            }
        }
    }

    private UUID resolveOrganizationId(String slug) {
        List<UUID> ids = jdbcTemplate.query("SELECT id FROM organizations WHERE slug = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                slug);

        if (ids.isEmpty()) {
            throw new IllegalStateException("Cannot seed organizer: unknown org_slug '" + slug + "'");
        }
        return ids.get(0);
    }

    private void validateOrganizationsExist() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organizations", Integer.class);
        if (count == null || count == 0) {
            log.warn("System started with zero organizations. Features requiring tenants will fail.");
        }
    }

    private <T> List<T> parseJson(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse seed JSON", e);
        }
    }
}
