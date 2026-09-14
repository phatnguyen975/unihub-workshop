package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.WorkshopAuthorizationPort;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JDBC adapter querying the workshops and workshop_assignments tables
 * to find organizer or staff user IDs authorized for a specific workshop.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JdbcWorkshopAuthorizationAdapter
    implements WorkshopAuthorizationPort
{

    private static final String SQL_FIND_AUTHORIZED_USERS = """
    SELECT u.id
    FROM users u
    JOIN workshops w ON w.organization_id = u.organization_id
    WHERE w.id = :workshopId AND u.role = 'ORGANIZER' AND u.is_active = true
    UNION
    SELECT wa.staff_user_id AS id
    FROM workshop_assignments wa
    JOIN users u ON u.id = wa.staff_user_id
    WHERE wa.workshop_id = :workshopId AND u.is_active = true
    """;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @Override
    public Set<UUID> findAuthorizedUserIds(UUID workshopId) {
        if (workshopId == null) {
            return Collections.emptySet();
        }

        try {
            Map<String, Object> params = Map.of("workshopId", workshopId);
            List<UUID> authorizedList = namedJdbcTemplate.query(
                SQL_FIND_AUTHORIZED_USERS,
                params,
                (rs, rowNum) -> rs.getObject("id", UUID.class)
            );
            return new HashSet<>(authorizedList);
        } catch (Exception ex) {
            log.warn(
                "Failed to find authorized users for workshop {}: {}",
                workshopId,
                ex.getMessage()
            );
            return Collections.emptySet();
        }
    }
}
