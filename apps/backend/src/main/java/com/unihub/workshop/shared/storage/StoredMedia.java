package com.unihub.workshop.shared.storage;

public record StoredMedia(
    String fileKey,
    String publicUrl,
    MediaType mediaType,
    long size,
    String contentType
) {}
