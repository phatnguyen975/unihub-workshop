package com.unihub.workshop.attendances.presentation.mapper;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryQuery;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.AttendanceSummary;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSummaryResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttendancePresentationMapper {
    GetAttendanceSummaryQuery toSummaryQuery(
        UUID workshopId,
        UUID organizationId
    );

    AttendanceSummaryResponse toSummaryResponse(AttendanceSummary summary);

    AttendanceSummaryResponse.RecentScan toRecentScan(Attendance attendance);

    @Mapping(target = "workshopId", source = "workshopId")
    @Mapping(target = "staffUserId", source = "staffUserId")
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    SyncAttendancesCommand toCommand(
        AttendanceSyncRequest request,
        UUID workshopId,
        UUID staffUserId,
        String idempotencyKey
    );

    SyncEntry toSyncEntry(AttendanceSyncEntryRequest request);

    /**
     * Converts a validated UUID string into its typed form.
     *
     * @param value validated UUID string, or {@code null}
     * @return parsed UUID, or {@code null}
     */
    default UUID toUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    /**
     * Converts a validated ISO-8601 UTC string into an instant.
     *
     * @param value validated ISO-8601 UTC string, or {@code null}
     * @return parsed instant, or {@code null}
     */
    default Instant toInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    @Mapping(
        target = "status",
        expression = "java(outcome.classification() != null ? outcome.classification().name() : null)"
    )
    AttendanceSyncOutcomeResponse toResponse(AttendanceOutcome outcome);

    List<AttendanceSyncOutcomeResponse> toResponses(
        List<AttendanceOutcome> outcomes
    );
}
