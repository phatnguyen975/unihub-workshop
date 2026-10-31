package com.unihub.workshop.attendances.infrastructure.persistence;

import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.Attendance;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceRepositoryAdapter implements AttendanceRepository {

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

    private static final String INSERT_PREFIX =
        "INSERT INTO attendances (" +
        String.join(", ", COLUMN_NAMES) +
        ") VALUES ";

    private static final String CONFLICT_CLAUSE =
        " ON CONFLICT DO NOTHING RETURNING sync_id";

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

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static String parameterName(String baseName, int rowIndex) {
        return baseName + rowIndex;
    }
}
