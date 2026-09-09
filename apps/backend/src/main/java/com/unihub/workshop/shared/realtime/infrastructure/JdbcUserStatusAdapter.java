package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.UserStatusPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight JDBC adapter checking user active status from users table.
 */
@Component
public class JdbcUserStatusAdapter implements UserStatusPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserStatusAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isUserActive(UUID userId) {
        if (userId == null) {
            return false;
        }
        List<Boolean> results = jdbcTemplate.query(
                "SELECT is_active FROM users WHERE id = ?",
                (rs, rowNum) -> rs.getBoolean("is_active"),
                userId);
        return !results.isEmpty() && Boolean.TRUE.equals(results.getFirst());
    }
}
