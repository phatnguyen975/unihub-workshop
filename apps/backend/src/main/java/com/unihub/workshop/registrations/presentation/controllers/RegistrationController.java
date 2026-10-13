package com.unihub.workshop.registrations.presentation.controllers;

import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import com.unihub.workshop.registrations.presentation.mapper.RegistrationPresentationMapper;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegisterWorkshopUseCase registerWorkshopUseCase;
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

        UUID idempotencyKeyId = parseIdempotencyKey(idempotencyKeyHeader);

        Registration registration = registerWorkshopUseCase.register(
            studentUserId,
            request.workshopId(),
            idempotencyKeyId
        );

        RegistrationResponse response = registrationMapper.toResponse(
            registration
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(response, "Registration successful")
        );
    }

    private UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            throw new ValidationException(
                "Idempotency-Key",
                "Idempotency-Key header is required"
            );
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "Idempotency-Key",
                "Idempotency-Key header must be a valid UUIDv4"
            );
        }
    }
}
