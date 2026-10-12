package com.unihub.workshop.workshops.application.ports.in;

import java.io.InputStream;
import java.util.UUID;

public record UploadBrochureCommand(
    UUID workshopId,
    UUID organizerId,
    UUID organizationId,
    String fileName,
    String contentType,
    long byteSize,
    InputStream inputStream
) {}
