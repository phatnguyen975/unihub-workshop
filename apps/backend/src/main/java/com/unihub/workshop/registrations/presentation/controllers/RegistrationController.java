package com.unihub.workshop.registrations.presentation.controllers;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsQuery;
import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsUseCase;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.registrations.presentation.dto.RegistrationListResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import com.unihub.workshop.registrations.presentation.mapper.RegistrationPresentationMapper;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegisterWorkshopUseCase registerWorkshopUseCase;
    private final GetMyRegistrationsUseCase getMyRegistrationsUseCase;
    private final RegistrationPresentationMapper registrationMapper;

    /**
     * Registers an authenticated student for a workshop.
     *
     * @param authentication current security context
     * @param idempotencyKeyHeader Idempotency-Key header (UUIDv4)
     * @param request registration request payload
     * @return 201 Created with registration details and signed QR ticket
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
        Authentication authentication,
        @RequestHeader(
            value = "Idempotency-Key",
            required = false
        ) String idempotencyKeyHeader,
        @Valid @RequestBody RegisterWorkshopRequest request
    ) {
        SecurityContextUtils.verifyRole(authentication, "STUDENT");
        UUID studentUserId = SecurityContextUtils.extractUserId(authentication);

        String idempotencyKey = validateIdempotencyKey(idempotencyKeyHeader);

        Registration registration = registerWorkshopUseCase.register(
            studentUserId,
            request.workshopId(),
            idempotencyKey
        );

        RegistrationResponse response = registrationMapper.toResponse(
            registration
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(response, "Registration successful")
        );
    }

    /**
     * Retrieves the authenticated student's own registrations as a paginated list.
     *
     * @param authentication current security context
     * @param cursor optional keyset cursor
     * @param size page window size (default 20)
     * @param status optional status filter
     * @return 200 OK with paginated registration list
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<
        ApiResponse<RegistrationListResponse>
    > getMyRegistrations(
        Authentication authentication,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(
            value = "size",
            required = false,
            defaultValue = "20"
        ) Integer size,
        @RequestParam(value = "status", required = false) String status
    ) {
        SecurityContextUtils.verifyRole(authentication, "STUDENT");
        UUID studentUserId = SecurityContextUtils.extractUserId(authentication);

        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            status,
            cursor,
            size
        );

        CursorPage<RegistrationSummary> page =
            getMyRegistrationsUseCase.getMyRegistrations(query);

        RegistrationListResponse response = registrationMapper.toListResponse(
            page
        );

        return ResponseEntity.ok(
            ApiResponse.success(response, "Registrations retrieved")
        );
    }

    private String validateIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw new ValidationException(
                "Idempotency-Key",
                "Idempotency-Key header is required"
            );
        }
        String trimmed = header.trim();
        if (trimmed.length() > 64) {
            throw new ValidationException(
                "Idempotency-Key",
                "Idempotency-Key header must not exceed 64 characters"
            );
        }
        try {
            UUID.fromString(trimmed);
            return trimmed;
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "Idempotency-Key",
                "Idempotency-Key header must be a valid UUIDv4"
            );
        }
    }
}
