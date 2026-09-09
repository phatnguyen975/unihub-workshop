package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.WorkshopAuthorizationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JDBC adapter querying the workshops and workshop_assignments tables
 * to verify organizer or staff access without violating module boundaries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JdbcWorkshopAuthorizationAdapter implements WorkshopAuthorizationPort {

    private static final String SQL_FILTER_AUTHORIZED_USERS = """
            SELECT DISTINCT u.id
            FROM users u
            WHERE u.id IN (:userIds) AND (
                EXISTS (
                    SELECT 1
                    FROM workshops w
                    WHERE w.id = :workshopId AND w.organization_id = u.organization_id
                )
                OR EXISTS (
                    SELECT 1
                    FROM workshop_assignments wa
                    WHERE wa.workshop_id = :workshopId AND wa.staff_user_id = u.id
                )
            )
            """;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @Override
    public Set<UUID> filterAuthorizedUserIds(Set<UUID> userIds, UUID workshopId) {
        if (userIds == null || userIds.isEmpty() || workshopId == null) {
            return Collections.emptySet();
        }

        Set<UUID> nonNullUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (nonNullUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            Map<String, Object> params = Map.of(
                    "userIds", nonNullUserIds,
                    "workshopId", workshopId);
            List<UUID> authorizedList = namedJdbcTemplate.query(
                    SQL_FILTER_AUTHORIZED_USERS,
                    params,
                    (rs, rowNum) -> rs.getObject("id", UUID.class));
            return new HashSet<>(authorizedList);
        } catch (Exception ex) {
            log.warn("Failed to filter authorized users for workshop {}: {}", workshopId, ex.getMessage());
            return Collections.emptySet();
        }
    }
}
