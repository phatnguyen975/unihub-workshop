package com.unihub.workshop.attendances.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class AttendanceRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<SqlParameterSource> parameterCaptor;

    @Captor
    private ArgumentCaptor<RowMapper<UUID>> rowMapperCaptor;

    private AttendanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AttendanceRepositoryAdapter(namedJdbcTemplate);
    }

    private static Attendance attendance(UUID registrationId, UUID syncId) {
        return Attendance.builder()
            .id(UUID.randomUUID())
            .workshopId(UUID.randomUUID())
            .registrationId(registrationId)
            .checkedInByUserId(UUID.randomUUID())
            .syncId(syncId)
            .deviceId("device-abc-123")
            .checkInMethod(Attendance.CHECK_IN_METHOD_OFFLINE_SYNC)
            .status(AttendanceStatus.VALID)
            .checkedInAt(NOW)
            .createdAt(NOW)
            .updatedAt(NOW)
            .createdBy(UUID.randomUUID())
            .updatedBy(UUID.randomUUID())
            .build();
    }

    @Test
    @DisplayName(
        "insertAll returns an empty set without touching the database when the batch is empty"
    )
    void insertAll_emptyBatch_returnsEmptySet() {
        assertThat(adapter.insertAll(List.of())).isEmpty();
        assertThat(adapter.insertAll(null)).isEmpty();

        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @DisplayName(
        "insertAll issues exactly one multi-row statement with an ON CONFLICT DO NOTHING RETURNING clause"
    )
    void insertAll_issuesSingleBulkStatement() {
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();
        UUID thirdSync = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(firstSync, secondSync, thirdSync));

        adapter.insertAll(
            List.of(
                attendance(UUID.randomUUID(), firstSync),
                attendance(UUID.randomUUID(), secondSync),
                attendance(UUID.randomUUID(), thirdSync)
            )
        );

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            rowMapperCaptor.capture()
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).startsWith("INSERT INTO attendances (");
        assertThat(sql).contains("ON CONFLICT DO NOTHING RETURNING sync_id");
        assertThat(sql).contains(":id0", ":syncId0", ":id2", ":syncId2");
        assertThat(sql).doesNotContain(":id3");
        assertThat(parameterCaptor.getValue().hasValue("id0")).isTrue();
        assertThat(parameterCaptor.getValue().hasValue("id2")).isTrue();
        assertThat(parameterCaptor.getValue().hasValue("id3")).isFalse();
    }

    @Test
    @DisplayName(
        "insertAll returns exactly the identifiers reported as persisted by the database"
    )
    void insertAll_returnsPersistedIdentifiers() {
        UUID persistedSync = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(persistedSync));

        Set<UUID> persisted = adapter.insertAll(
            List.of(
                attendance(UUID.randomUUID(), persistedSync),
                attendance(UUID.randomUUID(), UUID.randomUUID())
            )
        );

        assertThat(persisted).containsExactly(persistedSync);
    }

    @Test
    @DisplayName(
        "insertAll binds the server-authoritative columns for every row"
    )
    void insertAll_bindsEveryColumnForEveryRow() {
        UUID firstSync = UUID.randomUUID();
        UUID secondSync = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(firstSync, secondSync));

        Attendance first = attendance(UUID.randomUUID(), firstSync);
        Attendance second = attendance(UUID.randomUUID(), secondSync);

        adapter.insertAll(List.of(first, second));

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            rowMapperCaptor.capture()
        );

        SqlParameterSource parameters = parameterCaptor.getValue();
        assertThat(parameters.getValue("workshopId0")).isEqualTo(
            first.workshopId()
        );
        assertThat(parameters.getValue("registrationId0")).isEqualTo(
            first.registrationId()
        );
        assertThat(parameters.getValue("checkedInByUserId0")).isEqualTo(
            first.checkedInByUserId()
        );
        assertThat(parameters.getValue("syncId0")).isEqualTo(firstSync);
        assertThat(parameters.getValue("deviceId0")).isEqualTo(
            first.deviceId()
        );
        assertThat(parameters.getValue("checkInMethod0")).isEqualTo(
            Attendance.CHECK_IN_METHOD_OFFLINE_SYNC
        );
        assertThat(parameters.getValue("status0")).isEqualTo("VALID");
        assertThat(parameters.getValue("checkedInAt0")).isEqualTo(
            Timestamp.from(NOW)
        );
        assertThat(parameters.getValue("createdAt0")).isEqualTo(
            Timestamp.from(NOW)
        );
        assertThat(parameters.getValue("updatedAt0")).isEqualTo(
            Timestamp.from(NOW)
        );
        assertThat(parameters.getValue("createdBy0")).isEqualTo(
            first.createdBy()
        );
        assertThat(parameters.getValue("updatedBy0")).isEqualTo(
            first.updatedBy()
        );
        assertThat(parameters.getValue("syncId1")).isEqualTo(secondSync);
        assertThat(parameters.getValue("registrationId1")).isEqualTo(
            second.registrationId()
        );
    }

    @Test
    @DisplayName(
        "insertAll binds a null deviceId when the batch did not carry one"
    )
    void insertAll_withoutDeviceId_bindsNull() {
        UUID syncId = UUID.randomUUID();
        Attendance withoutDevice = Attendance.builder()
            .id(UUID.randomUUID())
            .workshopId(UUID.randomUUID())
            .registrationId(UUID.randomUUID())
            .checkedInByUserId(UUID.randomUUID())
            .syncId(syncId)
            .deviceId(null)
            .checkInMethod(Attendance.CHECK_IN_METHOD_OFFLINE_SYNC)
            .status(AttendanceStatus.VALID)
            .checkedInAt(NOW)
            .createdAt(NOW)
            .updatedAt(NOW)
            .createdBy(UUID.randomUUID())
            .updatedBy(UUID.randomUUID())
            .build();

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(syncId));

        adapter.insertAll(List.of(withoutDevice));

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            rowMapperCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("deviceId0")).isNull();
    }

    @Test
    @DisplayName("the row mapper reads the returned sync_id column as a UUID")
    void rowMapper_readsSyncIdColumn() throws SQLException {
        UUID syncId = UUID.randomUUID();

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<UUID>>any()
            )
        ).thenReturn(List.of(syncId));

        adapter.insertAll(List.of(attendance(UUID.randomUUID(), syncId)));

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            rowMapperCaptor.capture()
        );

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("sync_id", UUID.class)).thenReturn(syncId);

        assertThat(rowMapperCaptor.getValue().mapRow(resultSet, 0)).isEqualTo(
            syncId
        );
    }
}
