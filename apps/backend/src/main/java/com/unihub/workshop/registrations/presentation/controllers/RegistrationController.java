package com.unihub.workshop.registrations.presentation.controllers;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsQuery;
import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetRegistrationDetailUseCase;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.registrations.presentation.dto.RegisterWorkshopRequest;
import com.unihub.workshop.registrations.presentation.dto.RegistrationDetailResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationListResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import com.unihub.workshop.registrations.presentation.mapper.RegistrationPresentationMapper;
import com.unihub.workshop.shared.idempotency.IdempotencyKeyValidator;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegisterWorkshopUseCase registerWorkshopUseCase;
    private final GetMyRegistrationsUseCase getMyRegistrationsUseCase;
    private final GetRegistrationDetailUseCase getRegistrationDetailUseCase;
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
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationResponse> register(
        Authentication authentication,
        @RequestHeader(
            value = "Idempotency-Key",
            required = false
        ) String idempotencyKeyHeader,
        @Valid @RequestBody RegisterWorkshopRequest request
    ) {
        SecurityContextUtils.verifyRole(authentication, "STUDENT");
        UUID studentUserId = SecurityContextUtils.extractUserId(authentication);

        String idempotencyKey = IdempotencyKeyValidator.validateAndNormalize(
            idempotencyKeyHeader
        );

        Registration registration = registerWorkshopUseCase.register(
            studentUserId,
            request.workshopId(),
            idempotencyKey
        );

        RegistrationResponse response = registrationMapper.toResponse(
            registration
        );

        return ApiResponse.success(response, "Registration successful");
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
    public ApiResponse<RegistrationListResponse> getMyRegistrations(
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

        return ApiResponse.success(response, "Registrations retrieved");
    }

    /**
     * Retrieves single registration details with ownership check and conditional QR ticket fields.
     *
     * @param authentication current security context
     * @param id registration ID
     * @return 200 OK with registration details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<RegistrationDetailResponse> getRegistrationDetail(
        Authentication authentication,
        @PathVariable("id") UUID id
    ) {
        SecurityContextUtils.verifyRole(authentication, "STUDENT");
        UUID studentUserId = SecurityContextUtils.extractUserId(authentication);

        RegistrationDetail detail =
            getRegistrationDetailUseCase.getRegistrationDetail(
                studentUserId,
                id
            );

        RegistrationDetailResponse response =
            registrationMapper.toDetailResponse(detail);

        return ApiResponse.success(response, "Registration retrieved");
    }
}
