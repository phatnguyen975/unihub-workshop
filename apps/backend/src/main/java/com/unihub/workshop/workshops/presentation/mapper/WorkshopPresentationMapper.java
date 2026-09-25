package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.presentation.dto.SpeakerResponse;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkshopPresentationMapper {
    WorkshopResponse toResponse(Workshop workshop);

    SpeakerResponse toSpeakerResponse(Speaker speaker);
}
