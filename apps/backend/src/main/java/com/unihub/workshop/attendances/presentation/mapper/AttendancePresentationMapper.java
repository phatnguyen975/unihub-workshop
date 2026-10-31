package com.unihub.workshop.attendances.presentation.mapper;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttendancePresentationMapper {
    @Mapping(target = "workshopId", source = "workshopId")
    @Mapping(target = "staffUserId", source = "staffUserId")
    SyncAttendancesCommand toCommand(
        AttendanceSyncRequest request,
        UUID workshopId,
        UUID staffUserId
    );

    SyncEntry toSyncEntry(AttendanceSyncEntryRequest request);

    @Mapping(
        target = "status",
        expression = "java(outcome.classification() != null ? outcome.classification().name() : null)"
    )
    AttendanceSyncOutcomeResponse toResponse(AttendanceOutcome outcome);

    List<AttendanceSyncOutcomeResponse> toResponses(
        List<AttendanceOutcome> outcomes
    );
}
