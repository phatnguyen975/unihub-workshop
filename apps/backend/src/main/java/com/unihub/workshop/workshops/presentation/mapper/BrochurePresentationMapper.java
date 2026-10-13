package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.presentation.dto.BrochureSummaryJobResponse;
import com.unihub.workshop.workshops.presentation.dto.BrochureUploadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BrochurePresentationMapper {
    BrochureUploadResponse toResponse(BrochureUploadResult result);

    @Mapping(target = "jobId", source = "id")
    @Mapping(
        target = "status",
        expression = "java(job.status() != null ? job.status().name() : null)"
    )
    BrochureSummaryJobResponse toResponse(AiSummaryJob job);
}
