package com.unihub.workshop.attendances.presentation.controllers;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesUseCase;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapper;
import com.unihub.workshop.attendances.presentation.validation.AttendanceSyncBatchValidator;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.IdempotencyKeyValidator;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceSyncController {

    private static final String SYNC_PATH =
        "/api/v1/workshops/{workshopId}/attendances/sync";
    private static final String STAFF_ROLE = "STAFF";
    private static final String SUCCESS_MESSAGE = "Attendance sync processed";
    private static final int RETRY_AFTER_SECONDS = 30;

    private final SyncAttendancesUseCase syncAttendancesUseCase;
    private final AttendancePresentationMapper presentationMapper;
    private final AttendanceSyncBatchValidator batchValidator;

    @PostMapping(SYNC_PATH)
    @PreAuthorize("hasRole('STAFF')")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    public ApiResponse<List<AttendanceSyncOutcomeResponse>> syncAttendances(
        @PathVariable UUID workshopId,
        @RequestHeader(
            value = IdempotencyKeyValidator.HEADER_NAME,
            required = false
        ) String idempotencyKeyHeader,
        @RequestBody AttendanceSyncRequest request,
        Authentication authentication
    ) {
        SecurityContextUtils.verifyRole(authentication, STAFF_ROLE);
        UUID staffUserId = SecurityContextUtils.extractUserId(authentication);
        String idempotencyKey = requireIdempotencyKey(idempotencyKeyHeader);
        batchValidator.validate(request);

        SyncAttendancesCommand command = presentationMapper.toCommand(
            request,
            workshopId,
            staffUserId,
            idempotencyKey
        );
        List<AttendanceOutcome> outcomes;
        try {
            outcomes = syncAttendancesUseCase.execute(command);
        } catch (
            DataAccessResourceFailureException
            | TransactionSystemException ex
        ) {
            throw new ServiceUnavailableException(
                "Attendance sync is temporarily unavailable. Please retry.",
                RETRY_AFTER_SECONDS,
                ex
            );
        }
        List<AttendanceSyncOutcomeResponse> responses =
            presentationMapper.toResponses(outcomes);

        return ApiResponse.success(responses, SUCCESS_MESSAGE);
    }

    private static String requireIdempotencyKey(String header) {
        try {
            return IdempotencyKeyValidator.validateAndNormalize(header);
        } catch (ValidationException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }
}
