package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.StudentDirectoryWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class PostgresStudentDirectoryWriter implements StudentDirectoryWriter {

    private final JdbcTemplate jdbcTemplate;

    public PostgresStudentDirectoryWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void bulkUpsert(List<StudentDirectoryRow> rows, String sourceFileKey) {
        if (rows == null || rows.isEmpty())
            return;

        String sql = """
                    INSERT INTO student_directory (
                        organization_id, student_number, email, full_name, faculty, status, source_file_key, imported_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                    ON CONFLICT (organization_id, student_number) DO UPDATE SET
                        email = EXCLUDED.email,
                        full_name = EXCLUDED.full_name,
                        faculty = EXCLUDED.faculty,
                        status = 'ACTIVE',
                        source_file_key = EXCLUDED.source_file_key,
                        imported_at = EXCLUDED.imported_at,
                        updated_at = EXCLUDED.updated_at
                """;

        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        jdbcTemplate.batchUpdate(sql, rows, 100, (PreparedStatement ps, StudentDirectoryRow row) -> {
            ps.setObject(1, row.organizationId());
            ps.setString(2, row.studentNumber());
            ps.setString(3, row.email());
            ps.setString(4, row.fullName());
            ps.setString(5, row.faculty());
            ps.setString(6, sourceFileKey);
            ps.setTimestamp(7, nowTs);
            ps.setTimestamp(8, nowTs);
            ps.setTimestamp(9, nowTs);
        });
    }
}
