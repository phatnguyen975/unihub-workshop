package com.unihub.workshop.attendances.presentation.controllers;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesUseCase;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapper;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceSyncController {

    private static final String SYNC_PATH =
        "/api/v1/workshops/{workshopId}/attendances/sync";
    private static final String STAFF_ROLE = "STAFF";
    private static final String SUCCESS_MESSAGE = "Attendance sync processed";
    private static final String ENTRIES_FIELD = "entries";

    private final SyncAttendancesUseCase syncAttendancesUseCase;
    private final AttendancePresentationMapper presentationMapper;

    @PostMapping(SYNC_PATH)
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<
        ApiResponse<List<AttendanceSyncOutcomeResponse>>
    > syncAttendances(
        @PathVariable UUID workshopId,
        @RequestBody AttendanceSyncRequest request,
        Authentication authentication
    ) {
        SecurityContextUtils.verifyRole(authentication, STAFF_ROLE);
        UUID staffUserId = SecurityContextUtils.extractUserId(authentication);
        requireValidBatch(request);

        SyncAttendancesCommand command = presentationMapper.toCommand(
            request,
            workshopId,
            staffUserId
        );
        List<AttendanceOutcome> outcomes = syncAttendancesUseCase.execute(
            command
        );
        List<AttendanceSyncOutcomeResponse> responses =
            presentationMapper.toResponses(outcomes);

        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(
            ApiResponse.success(responses, SUCCESS_MESSAGE)
        );
    }

    private static void requireValidBatch(AttendanceSyncRequest request) {
        if (
            request == null ||
            request.entries() == null ||
            request.entries().isEmpty()
        ) {
            throw new ValidationException(
                ENTRIES_FIELD,
                "Attendance sync batch must contain at least one entry"
            );
        }
        List<AttendanceSyncEntryRequest> entries = request.entries();
        for (int index = 0; index < entries.size(); index++) {
            requireValidEntry(entries.get(index), index);
        }
    }

    private static void requireValidEntry(
        AttendanceSyncEntryRequest entry,
        int index
    ) {
        String prefix = ENTRIES_FIELD + "[" + index + "]";
        if (entry == null) {
            throw new ValidationException(prefix, "entry must not be null");
        }
        if (entry.syncId() == null) {
            throw new ValidationException(
                prefix + ".syncId",
                "syncId is required"
            );
        }
        if (entry.registrationId() == null) {
            throw new ValidationException(
                prefix + ".registrationId",
                "registrationId is required"
            );
        }
        if (entry.scannedAt() == null) {
            throw new ValidationException(
                prefix + ".scannedAt",
                "scannedAt is required"
            );
        }
    }
}
