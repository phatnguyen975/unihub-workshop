package com.unihub.workshop.attendances.infrastructure.persistence;

import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceRepositoryAdapter implements AttendanceRepository {

    @Override
    public List<Attendance> findRecentValidByWorkshopId(UUID workshopId) {
        if (workshopId == null) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue(
            "workshopId",
            workshopId
        );
        return namedJdbcTemplate.query(
            SELECT_PREFIX +
                "WHERE workshop_id = :workshopId" +
                VALID_STATUS_PREDICATE +
                " " +
                "ORDER BY checked_in_at DESC, id DESC LIMIT 10",
            parameters,
            (resultSet, rowNumber) -> mapRow(resultSet)
        );
    }

    private static final String[] COLUMN_NAMES = {
        "id",
        "workshop_id",
        "registration_id",
        "checked_in_by_user_id",
        "sync_id",
        "device_id",
        "check_in_method",
        "status",
        "checked_in_at",
        "created_at",
        "updated_at",
        "created_by",
        "updated_by",
    };

    private static final String[] PARAMETER_NAMES = {
        "id",
        "workshopId",
        "registrationId",
        "checkedInByUserId",
        "syncId",
        "deviceId",
        "checkInMethod",
        "status",
        "checkedInAt",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy",
    };

    private static final String SELECT_PREFIX =
        "SELECT " + String.join(", ", COLUMN_NAMES) + " FROM attendances ";

    private static final String INSERT_PREFIX =
        "INSERT INTO attendances (" +
        String.join(", ", COLUMN_NAMES) +
        ") VALUES ";

    private static final String CONFLICT_CLAUSE =
        " ON CONFLICT DO NOTHING RETURNING sync_id";

    private static final String WORKSHOP_SCOPED_REGISTRATION_PREDICATE =
        "WHERE workshop_id = :workshopId" +
        " AND registration_id IN (:registrationIds)";

    private static final String VALID_STATUS_PREDICATE =
        " AND status = 'VALID'";

    private static final String COUNT_VALID_BY_WORKSHOP_STATEMENT =
        "SELECT COUNT(*) FROM attendances" +
        " WHERE workshop_id = :workshopId" +
        VALID_STATUS_PREDICATE;

    private static final String SYNC_ID_COLUMN = "sync_id";

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @Override
    public Set<UUID> insertAll(Collection<Attendance> attendances) {
        if (attendances == null || attendances.isEmpty()) {
            return Set.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(INSERT_PREFIX);
        int rowIndex = 0;
        for (Attendance attendance : attendances) {
            if (rowIndex > 0) {
                sql.append(", ");
            }
            sql.append('(');
            appendPlaceholders(sql, rowIndex);
            sql.append(')');
            bindRow(parameters, attendance, rowIndex);
            rowIndex++;
        }
        sql.append(CONFLICT_CLAUSE);

        List<UUID> persisted = namedJdbcTemplate.query(
            sql.toString(),
            parameters,
            (resultSet, rowNumber) ->
                resultSet.getObject(SYNC_ID_COLUMN, UUID.class)
        );
        return new HashSet<>(persisted);
    }

    @Override
    public Map<UUID, Attendance> findByWorkshopIdAndRegistrationIds(
        UUID workshopId,
        Collection<UUID> registrationIds
    ) {
        if (
            workshopId == null ||
            registrationIds == null ||
            registrationIds.isEmpty()
        ) {
            return Map.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("workshopId", workshopId)
            .addValue("registrationIds", registrationIds);

        List<Attendance> rows = namedJdbcTemplate.query(
            SELECT_PREFIX + WORKSHOP_SCOPED_REGISTRATION_PREDICATE,
            parameters,
            (resultSet, rowNumber) -> mapRow(resultSet)
        );

        Map<UUID, Attendance> byRegistrationId = new LinkedHashMap<>();
        for (Attendance row : rows) {
            byRegistrationId.put(row.registrationId(), row);
        }
        return Map.copyOf(byRegistrationId);
    }

    @Override
    public long countValidByWorkshopId(UUID workshopId) {
        if (workshopId == null) {
            return 0L;
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue(
            "workshopId",
            workshopId
        );

        Long count = namedJdbcTemplate.queryForObject(
            COUNT_VALID_BY_WORKSHOP_STATEMENT,
            parameters,
            Long.class
        );
        return count != null ? count : 0L;
    }

    private static void appendPlaceholders(StringBuilder sql, int rowIndex) {
        for (int column = 0; column < PARAMETER_NAMES.length; column++) {
            if (column > 0) {
                sql.append(", ");
            }
            sql.append(':').append(
                parameterName(PARAMETER_NAMES[column], rowIndex)
            );
        }
    }

    private static void bindRow(
        MapSqlParameterSource parameters,
        Attendance attendance,
        int rowIndex
    ) {
        parameters.addValue(parameterName("id", rowIndex), attendance.id());
        parameters.addValue(
            parameterName("workshopId", rowIndex),
            attendance.workshopId()
        );
        parameters.addValue(
            parameterName("registrationId", rowIndex),
            attendance.registrationId()
        );
        parameters.addValue(
            parameterName("checkedInByUserId", rowIndex),
            attendance.checkedInByUserId()
        );
        parameters.addValue(
            parameterName("syncId", rowIndex),
            attendance.syncId()
        );
        parameters.addValue(
            parameterName("deviceId", rowIndex),
            attendance.deviceId()
        );
        parameters.addValue(
            parameterName("checkInMethod", rowIndex),
            attendance.checkInMethod()
        );
        parameters.addValue(
            parameterName("status", rowIndex),
            attendance.status() != null ? attendance.status().name() : null
        );
        parameters.addValue(
            parameterName("checkedInAt", rowIndex),
            toTimestamp(attendance.checkedInAt())
        );
        parameters.addValue(
            parameterName("createdAt", rowIndex),
            toTimestamp(attendance.createdAt())
        );
        parameters.addValue(
            parameterName("updatedAt", rowIndex),
            toTimestamp(attendance.updatedAt())
        );
        parameters.addValue(
            parameterName("createdBy", rowIndex),
            attendance.createdBy()
        );
        parameters.addValue(
            parameterName("updatedBy", rowIndex),
            attendance.updatedBy()
        );
    }

    private static Attendance mapRow(ResultSet resultSet) throws SQLException {
        return Attendance.builder()
            .id(resultSet.getObject("id", UUID.class))
            .workshopId(resultSet.getObject("workshop_id", UUID.class))
            .registrationId(resultSet.getObject("registration_id", UUID.class))
            .checkedInByUserId(
                resultSet.getObject("checked_in_by_user_id", UUID.class)
            )
            .syncId(resultSet.getObject("sync_id", UUID.class))
            .deviceId(resultSet.getString("device_id"))
            .checkInMethod(resultSet.getString("check_in_method"))
            .status(toStatus(resultSet.getString("status")))
            .checkedInAt(toInstant(resultSet.getObject("checked_in_at")))
            .createdAt(toInstant(resultSet.getObject("created_at")))
            .updatedAt(toInstant(resultSet.getObject("updated_at")))
            .createdBy(resultSet.getObject("created_by", UUID.class))
            .updatedBy(resultSet.getObject("updated_by", UUID.class))
            .build();
    }

    private static AttendanceStatus toStatus(String value) {
        return value != null ? AttendanceStatus.valueOf(value) : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static String parameterName(String baseName, int rowIndex) {
        return baseName + rowIndex;
    }
}
