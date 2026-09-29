package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import com.unihub.workshop.workshops.presentation.dto.AssignedStaffItemResponse;
import com.unihub.workshop.workshops.presentation.dto.AvailableStaffItemResponse;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.SpeakerRequest;
import com.unihub.workshop.workshops.presentation.dto.SpeakerResponse;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import com.unihub.workshop.workshops.presentation.dto.WorkshopSummaryResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkshopPresentationMapper {
    WorkshopResponse toResponse(Workshop workshop);

    SpeakerResponse toSpeakerResponse(Speaker speaker);

    WorkshopSummaryResponse toSummaryResponse(WorkshopSummary summary);

    default PageResponse<WorkshopSummaryResponse> toPageResponse(
        CursorPage<WorkshopSummary> page
    ) {
        return PageResponse.from(page, this::toSummaryResponse);
    }

    AssignedStaffItemResponse toAssignedStaffItemResponse(AssignedStaff domain);

    default PageResponse<AssignedStaffItemResponse> toAssignedStaffPageResponse(
        CursorPage<AssignedStaff> page
    ) {
        return PageResponse.from(page, this::toAssignedStaffItemResponse);
    }

    AvailableStaffItemResponse toAvailableStaffItemResponse(
        AvailableStaff domain
    );

    default PageResponse<AvailableStaffItemResponse> toAvailableStaffPageResponse(
        CursorPage<AvailableStaff> page
    ) {
        return PageResponse.from(page, this::toAvailableStaffItemResponse);
    }

    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "version", source = "request.version")
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "callerId", source = "callerId")
    @Mapping(target = "title", source = "request.title")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "coverImageUrl", source = "request.coverImageUrl")
    @Mapping(target = "location", source = "request.location")
    @Mapping(target = "timezone", source = "request.timezone")
    @Mapping(target = "startsAt", source = "request.startsAt")
    @Mapping(target = "endsAt", source = "request.endsAt")
    @Mapping(target = "capacity", source = "request.capacity")
    @Mapping(target = "price", source = "request.price")
    @Mapping(target = "currency", source = "request.currency")
    @Mapping(target = "speakers", source = "request.speakers")
    SaveDraftCommand toCommand(
        SaveDraftRequest request,
        UUID organizationId,
        UUID callerId
    );

    SaveDraftCommand.SpeakerCommand toSpeakerCommand(
        SpeakerRequest speakerRequest
    );
}
