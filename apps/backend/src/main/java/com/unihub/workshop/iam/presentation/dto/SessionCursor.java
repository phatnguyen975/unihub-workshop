package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionCursor(String nextCursor, boolean hasMore) {}
