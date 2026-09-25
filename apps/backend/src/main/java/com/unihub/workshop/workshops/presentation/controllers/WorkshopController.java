package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class WorkshopController {

    private final SaveDraftUseCase saveDraftUseCase;
    private final WorkshopPresentationMapper workshopMapper;

    @PostMapping("/drafts")
    public ApiResponse<WorkshopResponse> saveDraft(
        @Valid @RequestBody SaveDraftRequest request,
        Authentication authentication,
        HttpServletResponse httpServletResponse
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        List<SaveDraftCommand.SpeakerCommand> speakerCommands = request.speakers() != null
            ? request.speakers().stream()
                .map(s -> SaveDraftCommand.SpeakerCommand.builder()
                    .name(s.name())
                    .email(s.email())
                    .bio(s.bio())
                    .avatarUrl(s.avatarUrl())
                    .linkedinUrl(s.linkedinUrl())
                    .build())
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

    private UUID getVerifiedOrganizationId(Authentication authentication) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        return SecurityContextUtils.extractOrganizationId(authentication);
    }
}
