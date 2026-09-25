package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.presentation.dto.WorkshopResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkshopPresentationMapper {
    @Mapping(target = "speakers", expression = "java(java.util.List.of())")
    WorkshopResponse toResponse(Workshop workshop);
}
