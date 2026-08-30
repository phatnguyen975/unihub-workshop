package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgresStudentDirectoryWriterTest {

    @Test
    void shouldBulkUpsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostgresStudentDirectoryWriter writer = new PostgresStudentDirectoryWriter(jdbcTemplate);

        UUID orgId = UUID.randomUUID();
        StudentDirectoryRow row1 = new StudentDirectoryRow(orgId, "23120001", "23120001@student.hcmus.edu.vn",
                "John Doe", "FIT");

        writer.bulkUpsert(List.of(row1), "inbox/it/file1.csv");

        verify(jdbcTemplate).batchUpdate(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.<Collection<StudentDirectoryRow>>any(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.<ParameterizedPreparedStatementSetter<StudentDirectoryRow>>any());
    }
}
