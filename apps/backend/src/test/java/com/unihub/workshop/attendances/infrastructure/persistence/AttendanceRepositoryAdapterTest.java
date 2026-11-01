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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    @Captor
    private ArgumentCaptor<RowMapper<Attendance>> attendanceRowMapperCaptor;

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

    @Test
    @DisplayName(
        "findByWorkshopIdAndRegistrationIds returns an empty map without touching the database for an absent or empty lookup"
    )
    void findByWorkshopIdAndRegistrationIds_absentOrEmptyLookup_returnsEmptyMap() {
        assertThat(
            adapter.findByWorkshopIdAndRegistrationIds(
                UUID.randomUUID(),
                List.of()
            )
        ).isEmpty();
        assertThat(
            adapter.findByWorkshopIdAndRegistrationIds(UUID.randomUUID(), null)
        ).isEmpty();
        assertThat(
            adapter.findByWorkshopIdAndRegistrationIds(
                null,
                List.of(UUID.randomUUID())
            )
        ).isEmpty();

        verifyNoInteractions(namedJdbcTemplate);
    }

    @Test
    @DisplayName(
        "findByWorkshopIdAndRegistrationIds issues exactly one workshop-scoped SELECT over the requested registrations"
    )
    void findByWorkshopIdAndRegistrationIds_issuesSingleWorkshopScopedSelect() {
        UUID workshopId = UUID.randomUUID();
        List<UUID> registrationIds = List.of(
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        adapter.findByWorkshopIdAndRegistrationIds(workshopId, registrationIds);

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            attendanceRowMapperCaptor.capture()
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).startsWith("SELECT ");
        assertThat(sql).contains("FROM attendances");
        assertThat(sql).contains("WHERE workshop_id = :workshopId");
        assertThat(sql).contains("registration_id IN (:registrationIds)");
        assertThat(parameterCaptor.getValue().getValue("workshopId")).isEqualTo(
            workshopId
        );
        assertThat(
            parameterCaptor.getValue().getValue("registrationIds")
        ).isEqualTo(registrationIds);
    }

    @Test
    @DisplayName(
        "findByWorkshopIdAndRegistrationIds keys every loaded record by its registration identifier"
    )
    void findByWorkshopIdAndRegistrationIds_keysRecordsByRegistrationId() {
        Attendance first = attendance(UUID.randomUUID(), UUID.randomUUID());
        Attendance second = attendance(UUID.randomUUID(), UUID.randomUUID());

        when(
            namedJdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Attendance>>any()
            )
        ).thenReturn(List.of(first, second));

        Map<UUID, Attendance> byRegistrationId =
            adapter.findByWorkshopIdAndRegistrationIds(
                UUID.randomUUID(),
                List.of(first.registrationId(), second.registrationId())
            );

        assertThat(byRegistrationId).hasSize(2);
        assertThat(byRegistrationId.get(first.registrationId())).isEqualTo(
            first
        );
        assertThat(byRegistrationId.get(second.registrationId())).isEqualTo(
            second
        );
    }

    @Test
    @DisplayName(
        "the attendance row mapper reads all thirteen columns, parsing status and every timestamp shape"
    )
    void attendanceRowMapper_readsEveryColumn() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        UUID checkedInByUserId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        UUID updatedBy = UUID.randomUUID();

        adapter.findByWorkshopIdAndRegistrationIds(
            workshopId,
            List.of(registrationId)
        );

        verify(namedJdbcTemplate).query(
            sqlCaptor.capture(),
            parameterCaptor.capture(),
            attendanceRowMapperCaptor.capture()
        );

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getObject("workshop_id", UUID.class)).thenReturn(
            workshopId
        );
        when(resultSet.getObject("registration_id", UUID.class)).thenReturn(
            registrationId
        );
        when(
            resultSet.getObject("checked_in_by_user_id", UUID.class)
        ).thenReturn(checkedInByUserId);
        when(resultSet.getObject("sync_id", UUID.class)).thenReturn(syncId);
        when(resultSet.getString("device_id")).thenReturn("device-abc-123");
        when(resultSet.getString("check_in_method")).thenReturn(
            Attendance.CHECK_IN_METHOD_OFFLINE_SYNC
        );
        when(resultSet.getString("status")).thenReturn("INVALIDATED");
        when(resultSet.getObject("checked_in_at")).thenReturn(
            Timestamp.from(NOW)
        );
        when(resultSet.getObject("created_at")).thenReturn(
            OffsetDateTime.ofInstant(NOW.plusSeconds(5), ZoneOffset.UTC)
        );
        when(resultSet.getObject("updated_at")).thenReturn(NOW.plusSeconds(10));
        when(resultSet.getObject("created_by", UUID.class)).thenReturn(
            createdBy
        );
        when(resultSet.getObject("updated_by", UUID.class)).thenReturn(
            updatedBy
        );

        Attendance mapped = attendanceRowMapperCaptor
            .getValue()
            .mapRow(resultSet, 0);

        assertThat(mapped.id()).isEqualTo(id);
        assertThat(mapped.workshopId()).isEqualTo(workshopId);
        assertThat(mapped.registrationId()).isEqualTo(registrationId);
        assertThat(mapped.checkedInByUserId()).isEqualTo(checkedInByUserId);
        assertThat(mapped.syncId()).isEqualTo(syncId);
        assertThat(mapped.deviceId()).isEqualTo("device-abc-123");
        assertThat(mapped.checkInMethod()).isEqualTo(
            Attendance.CHECK_IN_METHOD_OFFLINE_SYNC
        );
        assertThat(mapped.status()).isEqualTo(AttendanceStatus.INVALIDATED);
        assertThat(mapped.checkedInAt()).isEqualTo(NOW);
        assertThat(mapped.createdAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(mapped.updatedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(mapped.createdBy()).isEqualTo(createdBy);
        assertThat(mapped.updatedBy()).isEqualTo(updatedBy);
    }
}
