package com.unihub.workshop.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.shared.events.UserProvisionedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class SeedRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordGenerator passwordGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ApplicationArguments args;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void run_insertsOrganizationAndOrganizer_onFirstStartup() {
        SeedProperties props = new SeedProperties(
            "[{\"name\":\"Engineering\",\"slug\":\"engineering\"}]",
            "[{\"email\":\"admin@engineering.com\",\"orgSlug\":\"engineering\",\"displayName\":\"Admin\"}]"
        );
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        when(
            jdbcTemplate.update(
                anyString(),
                eq("Engineering"),
                eq("engineering")
            )
        ).thenReturn(1);

        // User does not exist yet — existence check returns false
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("admin@engineering.com")
            )
        ).thenReturn(false);

        UUID orgId = UUID.randomUUID();
        when(
            jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
                eq("engineering")
            )
        ).thenReturn(List.of(orgId));

        when(passwordGenerator.generate()).thenReturn(
            new PasswordGenerator.GeneratedPassword("plaintext123", "hashed123")
        );
        when(
            jdbcTemplate.update(
                anyString(),
                eq(orgId),
                eq("admin@engineering.com"),
                eq("hashed123"),
                eq("Admin")
            )
        ).thenReturn(1);

        UUID userId = UUID.randomUUID();
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(UUID.class),
                eq("admin@engineering.com")
            )
        ).thenReturn(userId);

        when(
            jdbcTemplate.queryForObject(anyString(), eq(Integer.class))
        ).thenReturn(1);

        runner.run(args);

        verify(jdbcTemplate).update(
            "INSERT INTO organizations (name, slug) VALUES (?, ?) ON CONFLICT (slug) DO NOTHING",
            "Engineering",
            "engineering"
        );
        verify(jdbcTemplate).update(
            "INSERT INTO users (organization_id, role, email, password_hash, full_name, is_temporary_password) VALUES (?, 'ORGANIZER', ?, ?, ?, true) ON CONFLICT (email) DO NOTHING",
            orgId,
            "admin@engineering.com",
            "hashed123",
            "Admin"
        );

        ArgumentCaptor<UserProvisionedEvent> eventCaptor =
            ArgumentCaptor.forClass(UserProvisionedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserProvisionedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.userId()).isEqualTo(userId);
        assertThat(publishedEvent.organizationId()).isEqualTo(orgId);
        assertThat(publishedEvent.orgSlug()).isEqualTo("engineering");
        assertThat(publishedEvent.email()).isEqualTo("admin@engineering.com");
        assertThat(publishedEvent.fullName()).isEqualTo("Admin");
        assertThat(publishedEvent.role()).isEqualTo("ORGANIZER");
        assertThat(publishedEvent.temporaryPassword()).isEqualTo(
            "plaintext123"
        );
    }

    @Test
    void run_skipsExistingOrganizer_onRestart_withoutHashing() {
        SeedProperties props = new SeedProperties(
            "[{\"name\":\"Engineering\",\"slug\":\"engineering\"}]",
            "[{\"email\":\"admin@engineering.com\",\"orgSlug\":\"engineering\",\"displayName\":\"Admin\"}]"
        );
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        when(
            jdbcTemplate.update(
                anyString(),
                eq("Engineering"),
                eq("engineering")
            )
        ).thenReturn(0);

        // User already exists — hashing is skipped entirely
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("admin@engineering.com")
            )
        ).thenReturn(true);

        when(
            jdbcTemplate.queryForObject(anyString(), eq(Integer.class))
        ).thenReturn(1);

        runner.run(args);

        verify(jdbcTemplate).update(
            anyString(),
            eq("Engineering"),
            eq("engineering")
        );
        // Argon2 must NOT be called when organizer already exists
        verify(passwordGenerator, never()).generate();
        // No user INSERT attempted for existing organizer
        verify(jdbcTemplate, never()).update(
            anyString(),
            eq("admin@engineering.com")
        );
        // No event published for existing organizer
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void run_failsFast_onUnknownOrgSlug() {
        SeedProperties props = new SeedProperties(
            "[]",
            "[{\"email\":\"admin@engineering.com\",\"orgSlug\":\"unknown\",\"displayName\":\"Admin\"}]"
        );
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        // User does not exist — proceed to resolveOrganizationId
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq("admin@engineering.com")
            )
        ).thenReturn(false);

        when(
            jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
                eq("unknown")
            )
        ).thenReturn(List.of());

        assertThatThrownBy(() -> runner.run(args))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknown org_slug 'unknown'");
    }

    @Test
    void run_withEmptyArrays_logsWarnAndDoesNoInserts() {
        SeedProperties props = new SeedProperties("[]", "[]");
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        when(
            jdbcTemplate.queryForObject(anyString(), eq(Integer.class))
        ).thenReturn(0);

        runner.run(args);

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void run_withSingleOrgAndNoOrganizers_insertsOrgAndValidates() {
        SeedProperties props = new SeedProperties(
            "[{\"name\":\"Engineering\",\"slug\":\"engineering\"}]",
            "[]"
        );
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        when(
            jdbcTemplate.update(
                anyString(),
                eq("Engineering"),
                eq("engineering")
            )
        ).thenReturn(1);
        when(
            jdbcTemplate.queryForObject(anyString(), eq(Integer.class))
        ).thenReturn(1);

        runner.run(args);

        verify(jdbcTemplate).update(
            anyString(),
            eq("Engineering"),
            eq("engineering")
        );
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void run_withMalformedOrganizationsJson_throwsIllegalStateException() {
        SeedProperties props = new SeedProperties("not-valid-json", "[]");
        SeedRunner runner = new SeedRunner(
            props,
            jdbcTemplate,
            objectMapper,
            passwordGenerator,
            eventPublisher
        );

        assertThatThrownBy(() -> runner.run(args))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to parse seed JSON");
    }
}
