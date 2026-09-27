package com.unihub.workshop.shared.storage;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum MediaType {
    COVER_IMAGE("workshops/covers"),
    SPEAKER_AVATAR("speakers/avatars"),
    USER_AVATAR("users/avatars");

    private final String folderPrefix;

    MediaType(String folderPrefix) {
        this.folderPrefix = folderPrefix;
    }

    public String getFolderPrefix() {
        return folderPrefix;
    }

    public static MediaType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Media type is required");
        }
        try {
            return MediaType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
            throw new BadRequestException(
                "Invalid media type: " + value + ". Allowed: " + allowed
            );
        }
    }
}
