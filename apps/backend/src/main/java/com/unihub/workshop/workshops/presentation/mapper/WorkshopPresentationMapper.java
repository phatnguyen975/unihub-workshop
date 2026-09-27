package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.presentation.dto.SaveDraftRequest;
import com.unihub.workshop.workshops.presentation.dto.SpeakerRequest;
import com.unihub.workshop.workshops.presentation.dto.SpeakerResponse;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkshopPresentationMapper {
    WorkshopResponse toResponse(Workshop workshop);

    SpeakerResponse toSpeakerResponse(Speaker speaker);

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
