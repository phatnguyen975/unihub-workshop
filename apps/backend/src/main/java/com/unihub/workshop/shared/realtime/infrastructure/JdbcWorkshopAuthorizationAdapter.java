package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.WorkshopAuthorizationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JDBC adapter querying the workshops and workshop_assignments tables
 * to verify organizer or staff access without violating module boundaries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JdbcWorkshopAuthorizationAdapter implements WorkshopAuthorizationPort {

    private static final String SQL_CHECK_AUTHORIZATION = """
            SELECT EXISTS (
                SELECT 1
                FROM workshops w
                JOIN users u ON u.organization_id = w.organization_id
                WHERE w.id = ? AND u.id = ?
            ) OR EXISTS (
                SELECT 1
                FROM workshop_assignments wa
                WHERE wa.workshop_id = ? AND wa.staff_user_id = ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isUserAuthorizedForWorkshop(UUID userId, UUID workshopId) {
        if (userId == null || workshopId == null) {
            return false;
        }

        try {
            Boolean authorized = jdbcTemplate.queryForObject(
                    SQL_CHECK_AUTHORIZATION,
                    Boolean.class,
                    workshopId,
                    userId,
                    workshopId,
                    userId);
            return Boolean.TRUE.equals(authorized);
        } catch (Exception ex) {
            log.warn("Failed to check workshop authorization for user {} on workshop {}: {}",
                    userId, workshopId, ex.getMessage());
            return false;
        }
    }
}
