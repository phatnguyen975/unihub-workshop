package com.unihub.workshop.shared.storage;

import lombok.Builder;

@Builder
public record StoredMedia(
    String fileKey,
    String publicUrl,
    MediaAssetType assetType,
    long size,
    String contentType
) {}
