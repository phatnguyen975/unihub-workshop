package com.unihub.workshop.workshops.application.dto;

import java.util.UUID;

public record BrochureUploadResponse(
        UUID brochureId,
        String fileUrl,
        String brochureStatus,
        String aiSummaryStatus) {
}
