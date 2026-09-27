package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.storage.MediaAssetType;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.storage.MediaValidationUtils;
import com.unihub.workshop.shared.storage.StoredMedia;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.presentation.dto.MediaUploadResponse;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final WorkshopPresentationMapper workshopMapper;
    private final MediaStoragePort mediaStoragePort;

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

    @PostMapping("/drafts")
    public ApiResponse<WorkshopResponse> saveDraft(
        @Valid @RequestBody SaveDraftRequest request,
        Authentication authentication,
        HttpServletResponse httpServletResponse
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        List<SaveDraftCommand.SpeakerCommand> speakerCommands =
            request.speakers() != null
                ? request
                      .speakers()
                      .stream()
                      .map(s ->
                          SaveDraftCommand.SpeakerCommand.builder()
                              .name(s.name())
                              .email(s.email())
                              .bio(s.bio())
                              .avatarUrl(s.avatarUrl())
                              .linkedinUrl(s.linkedinUrl())
                              .sortOrder(s.sortOrder())
                              .build()
                      )
                      .toList()
                : null;

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(request.id())
            .version(request.version())
            .organizationId(organizationId)
            .callerId(callerId)
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
            .speakers(speakerCommands)
            .build();

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

    @PostMapping(
        value = "/media/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadMedia(
        @RequestParam("file") MultipartFile file,
        @RequestParam("type") String type,
        Authentication authentication,
        HttpServletResponse httpServletResponse
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

        MediaUploadResponse response = MediaUploadResponse.builder()
            .url(storedMedia.publicUrl())
            .fileKey(storedMedia.fileKey())
            .type(storedMedia.assetType().name())
            .size(storedMedia.size())
            .contentType(storedMedia.contentType())
            .build();

        httpServletResponse.setStatus(HttpServletResponse.SC_CREATED);
        return ApiResponse.success(response, "Media uploaded successfully.");
    }

    private UUID getVerifiedOrganizationId(Authentication authentication) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        return SecurityContextUtils.extractOrganizationId(authentication);
    }
}
