package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.presentation.dto.BrochureUploadResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BrochurePresentationMapper {
    BrochureUploadResponse toResponse(BrochureUploadResult result);
}
