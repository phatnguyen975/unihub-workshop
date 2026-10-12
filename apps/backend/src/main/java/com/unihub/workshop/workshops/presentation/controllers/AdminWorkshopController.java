package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.storage.MediaAssetType;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.storage.MediaValidationUtils;
import com.unihub.workshop.shared.storage.StoredMedia;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.DeleteDraftUseCase;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureCommand;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureUseCase;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import com.unihub.workshop.workshops.presentation.dto.AssignStaffRequest;
import com.unihub.workshop.workshops.presentation.dto.AssignedStaffItemResponse;
import com.unihub.workshop.workshops.presentation.dto.AvailableStaffItemResponse;
import com.unihub.workshop.workshops.presentation.dto.BatchAssignResponse;
import com.unihub.workshop.workshops.presentation.dto.BrochureUploadResponse;
import com.unihub.workshop.workshops.presentation.dto.CancelWorkshopRequest;
import com.unihub.workshop.workshops.presentation.dto.MediaUploadResponse;
import com.unihub.workshop.workshops.presentation.dto.PublishWorkshopRequest;
import com.unihub.workshop.workshops.presentation.dto.RescheduleWorkshopRequest;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.UpdateWorkshopRequest;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import com.unihub.workshop.workshops.presentation.dto.WorkshopSummaryResponse;
import com.unihub.workshop.workshops.presentation.mapper.BrochurePresentationMapper;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/workshops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class AdminWorkshopController {

    private final SaveDraftUseCase saveDraftUseCase;
    private final GetWorkshopUseCase getWorkshopUseCase;
    private final DeleteDraftUseCase deleteDraftUseCase;
    private final ListAdminWorkshopsUseCase listAdminWorkshopsUseCase;
    private final ListAssignedStaffUseCase listAssignedStaffUseCase;
    private final ListAvailableStaffUseCase listAvailableStaffUseCase;
    private final AssignStaffUseCase assignStaffUseCase;
    private final RevokeStaffUseCase revokeStaffUseCase;
    private final PublishWorkshopUseCase publishWorkshopUseCase;
    private final UpdateWorkshopUseCase updateWorkshopUseCase;
    private final RescheduleWorkshopUseCase rescheduleWorkshopUseCase;
    private final CancelWorkshopUseCase cancelWorkshopUseCase;
    private final WorkshopPresentationMapper workshopMapper;
    private final MediaStoragePort mediaStoragePort;
    private final UploadBrochureUseCase uploadBrochureUseCase;
    private final BrochurePresentationMapper brochureMapper;

    @GetMapping
    public ApiResponse<PageResponse<WorkshopSummaryResponse>> listWorkshops(
        @RequestParam(name = "status", required = false) String statusStr,
        @RequestParam(name = "priceType", required = false) String priceType,
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "from", required = false) String fromStr,
        @RequestParam(name = "to", required = false) String toStr,
        @RequestParam(
            name = "sort",
            required = false,
            defaultValue = "createdAt,desc"
        ) String sort,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(
            name = "size",
            required = false,
            defaultValue = "20"
        ) Integer size,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);

        WorkshopStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = WorkshopStatus.valueOf(
                    statusStr.trim().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + statusStr);
            }
        }

        Instant from = parseInstant(fromStr, "from");
        Instant to = parseInstant(toStr, "to");

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(organizationId)
            .status(status)
            .priceType(priceType)
            .q(q)
            .from(from)
            .to(to)
            .sort(sort)
            .cursor(cursor)
            .size(size)
            .build();

        CursorPage<WorkshopSummary> page = listAdminWorkshopsUseCase.execute(
            query
        );
        PageResponse<WorkshopSummaryResponse> response =
            workshopMapper.toPageResponse(page);

        return ApiResponse.success(
            response,
            "Workshops retrieved successfully."
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkshopResponse> getWorkshop(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        Workshop workshop = getWorkshopUseCase.execute(id, organizationId);
        WorkshopResponse response = workshopMapper.toResponse(workshop);
        return ApiResponse.success(
            response,
            "Workshop retrieved successfully."
        );
    }

    @GetMapping("/{id}/staff")
    public ApiResponse<
        PageResponse<AssignedStaffItemResponse>
    > listAssignedStaff(
        @PathVariable UUID id,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(
            name = "size",
            required = false,
            defaultValue = "20"
        ) Integer size,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        ListAssignedStaffQuery query = new ListAssignedStaffQuery(
            id,
            organizationId,
            cursor,
            size
        );
        CursorPage<AssignedStaff> page = listAssignedStaffUseCase.execute(
            query
        );

        return ApiResponse.success(
            workshopMapper.toAssignedStaffPageResponse(page),
            "Assigned staff retrieved successfully."
        );
    }

    @GetMapping("/{id}/staff/available")
    public ApiResponse<
        PageResponse<AvailableStaffItemResponse>
    > listAvailableStaff(
        @PathVariable UUID id,
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(
            name = "size",
            required = false,
            defaultValue = "20"
        ) Integer size,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            id,
            organizationId,
            q,
            cursor,
            size
        );
        CursorPage<AvailableStaff> page = listAvailableStaffUseCase.execute(
            query
        );

        return ApiResponse.success(
            workshopMapper.toAvailableStaffPageResponse(page),
            "Available staff retrieved successfully."
        );
    }

    @PostMapping("/{id}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BatchAssignResponse> assignStaff(
        @PathVariable UUID id,
        @Valid @RequestBody AssignStaffRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        AssignStaffCommand command = new AssignStaffCommand(
            id,
            organizationId,
            callerId,
            request.staffUserIds()
        );

        List<AssignedStaff> assigned = assignStaffUseCase.execute(command);
        BatchAssignResponse response = workshopMapper.toBatchAssignResponse(
            id,
            assigned
        );

        return ApiResponse.success(
            response,
            "Staff members assigned successfully."
        );
    }

    @DeleteMapping("/{id}/staff/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeStaff(
        @PathVariable UUID id,
        @PathVariable UUID userId,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        RevokeStaffCommand command = new RevokeStaffCommand(
            id,
            userId,
            organizationId,
            callerId
        );

        revokeStaffUseCase.execute(command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkshop(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        deleteDraftUseCase.execute(id, organizationId);
    }

    @PostMapping("/drafts")
    public ApiResponse<WorkshopResponse> saveDraft(
        @Valid @RequestBody SaveDraftRequest request,
        Authentication authentication,
        HttpServletResponse httpServletResponse
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        SaveDraftCommand command = workshopMapper.toCommand(
            request,
            organizationId,
            callerId
        );

        Workshop workshop = saveDraftUseCase.execute(command);
        WorkshopResponse response = workshopMapper.toResponse(workshop);

        if (request.id() == null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_CREATED);
            return ApiResponse.success(
                response,
                "Workshop draft created successfully."
            );
        }

        return ApiResponse.success(
            response,
            "Workshop draft updated successfully."
        );
    }

    @PatchMapping("/{id}/publish")
    public ApiResponse<WorkshopResponse> publishWorkshop(
        @PathVariable UUID id,
        @Valid @RequestBody PublishWorkshopRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(id)
            .organizationId(organizationId)
            .callerId(callerId)
            .version(request.version())
            .build();

        Workshop published = publishWorkshopUseCase.execute(command);
        WorkshopResponse response = workshopMapper.toResponse(published);

        return ApiResponse.success(
            response,
            "Workshop published successfully."
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkshopResponse> updateWorkshop(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateWorkshopRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(id)
            .organizationId(organizationId)
            .callerId(callerId)
            .version(request.version())
            .title(request.title())
            .description(request.description())
            .coverImageUrl(request.coverImageUrl())
            .capacity(request.capacity())
            .startsAt(request.startsAt())
            .endsAt(request.endsAt())
            .price(request.price())
            .currency(request.currency())
            .status(request.status())
            .requestedOrgId(request.organizationId())
            .speakers(request.speakers())
            .build();

        Workshop updated = updateWorkshopUseCase.execute(command);
        WorkshopResponse response = workshopMapper.toResponse(updated);

        return ApiResponse.success(response, "Workshop updated successfully.");
    }

    @PatchMapping("/{id}/reschedule")
    public ApiResponse<WorkshopResponse> rescheduleWorkshop(
        @PathVariable UUID id,
        @Valid @RequestBody RescheduleWorkshopRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(id)
            .organizationId(organizationId)
            .callerId(callerId)
            .version(request.version())
            .startsAt(request.startsAt())
            .endsAt(request.endsAt())
            .title(request.title())
            .description(request.description())
            .coverImageUrl(request.coverImageUrl())
            .location(request.location())
            .capacity(request.capacity())
            .price(request.price())
            .currency(request.currency())
            .speakers(request.speakers())
            .status(request.status())
            .requestedOrgId(request.organizationId())
            .build();

        Workshop rescheduled = rescheduleWorkshopUseCase.execute(command);
        WorkshopResponse response = workshopMapper.toResponse(rescheduled);

        return ApiResponse.success(
            response,
            "Workshop rescheduled successfully."
        );
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<WorkshopResponse> cancelWorkshop(
        @PathVariable UUID id,
        @Valid @RequestBody CancelWorkshopRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(id)
            .organizationId(organizationId)
            .callerId(callerId)
            .version(request.version())
            .reason(request.reason())
            .title(request.title())
            .description(request.description())
            .coverImageUrl(request.coverImageUrl())
            .location(request.location())
            .timezone(request.timezone())
            .startsAt(request.startsAt())
            .endsAt(request.endsAt())
            .capacity(request.capacity())
            .price(request.price())
            .currency(request.currency())
            .speakers(request.speakers())
            .status(request.status())
            .requestedOrgId(request.organizationId())
            .build();

        Workshop canceled = cancelWorkshopUseCase.execute(command);
        WorkshopResponse response = workshopMapper.toResponse(canceled);

        return ApiResponse.success(response, "Workshop canceled successfully.");
    }

    @PostMapping(
        value = "/media/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadMedia(
        @RequestParam("file") MultipartFile file,
        @RequestParam("type") String type,
        Authentication authentication
    ) {
        // Verify caller is an active ORGANIZER with a valid tenant organization
        getVerifiedOrganizationId(authentication);

        if (type == null || type.isBlank()) {
            throw new BadRequestException("Media type is required");
        }

        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (
            !"COVER_IMAGE".equals(normalizedType) &&
            !"SPEAKER_AVATAR".equals(normalizedType)
        ) {
            throw new BadRequestException(
                "Invalid media type. Allowed values: COVER_IMAGE, SPEAKER_AVATAR"
            );
        }

        MediaAssetType assetType = MediaAssetType.valueOf(normalizedType);
        String extension = MediaValidationUtils.validateImage(file);

        StoredMedia storedMedia;
        try {
            storedMedia = mediaStoragePort.upload(
                file.getInputStream(),
                file.getSize(),
                file.getContentType(),
                assetType,
                extension
            );
        } catch (IOException e) {
            throw new InternalServerException(
                "Failed to read uploaded file stream",
                e
            );
        }

        return ApiResponse.success(
            workshopMapper.toMediaUploadResponse(storedMedia),
            "Media uploaded successfully."
        );
    }

    @PostMapping(
        value = "/{id}/brochure",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<BrochureUploadResponse> uploadBrochure(
        @PathVariable("id") UUID id,
        @RequestParam("file") MultipartFile file,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        UUID organizerId = SecurityContextUtils.extractUserId(authentication);

        if (file == null || file.isEmpty()) {
            throw new ValidationException(
                "file",
                "Uploaded file must not be empty"
            );
        }

        try {
            BrochureUploadResult result = uploadBrochureUseCase.uploadBrochure(
                new UploadBrochureCommand(
                    id,
                    organizerId,
                    organizationId,
                    file.getOriginalFilename() != null
                        ? file.getOriginalFilename()
                        : "brochure.pdf",
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream()
                )
            );

            return ApiResponse.success(
                brochureMapper.toResponse(result),
                "Brochure upload accepted. AI summarization is processing."
            );
        } catch (IOException e) {
            throw new InternalServerException(
                "Failed to read uploaded brochure file stream",
                e
            );
        }
    }

    private Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                "Invalid '" +
                    paramName +
                    "' timestamp format: " +
                    value +
                    ". Expected ISO-8601 UTC timestamp."
            );
        }
    }

    private UUID getVerifiedOrganizationId(Authentication authentication) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        return SecurityContextUtils.extractOrganizationId(authentication);
    }
}
