package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaUploadResponse(
    String url,
    String fileKey,
    String type,
    long size,
    String contentType
) {}
