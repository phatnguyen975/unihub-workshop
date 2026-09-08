package com.unihub.workshop.iam.presentation.dto;

import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.shared.exception.ValidationException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class UpdateProfileRequest {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Set<String> ALLOWED_FIELDS = Set.of("fullName", "phoneNumber", "avatarUrl");

    private final String fullName;
    private final boolean fullNamePresent;
    private final String phoneNumber;
    private final boolean phoneNumberPresent;
    private final String avatarUrl;
    private final boolean avatarUrlPresent;

    public UpdateProfileRequest(
            String fullName,
            boolean fullNamePresent,
            String phoneNumber,
            boolean phoneNumberPresent,
            String avatarUrl,
            boolean avatarUrlPresent) {
        this.fullName = fullName;
        this.fullNamePresent = fullNamePresent;
        this.phoneNumber = phoneNumber;
        this.phoneNumberPresent = phoneNumberPresent;
        this.avatarUrl = avatarUrl;
        this.avatarUrlPresent = avatarUrlPresent;
    }

    public static UpdateProfileRequest from(Map<String, Object> body) {
        if (body == null) {
            throw new ValidationException("Request body must not be null");
        }

        List<String> unknownFields = new ArrayList<>();
        for (String key : body.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) {
                unknownFields.add(key);
            }
        }

        if (!unknownFields.isEmpty()) {
            List<String> errors = unknownFields.stream()
                    .sorted()
                    .map(field -> field + ": Field is immutable or unrecognized")
                    .toList();
            throw new ValidationException("Validation failed", errors);
        }

        boolean hasFullName = body.containsKey("fullName");
        boolean hasPhoneNumber = body.containsKey("phoneNumber");
        boolean hasAvatarUrl = body.containsKey("avatarUrl");

        if (!hasFullName && !hasPhoneNumber && !hasAvatarUrl) {
            throw new ValidationException("At least one field must be provided for update.");
        }

        List<String> errors = new ArrayList<>();

        String parsedFullName = null;
        if (hasFullName) {
            Object raw = body.get("fullName");
            if (raw == null) {
                errors.add("fullName: Full name may not be set to null");
            } else if (!(raw instanceof String)) {
                errors.add("fullName: Full name must be a string");
            } else {
                parsedFullName = (String) raw;
                if (parsedFullName.trim().isEmpty()) {
                    errors.add("fullName: Full name must not be blank");
                } else if (parsedFullName.length() > 200) {
                    errors.add("fullName: Full name must not exceed 200 characters");
                }
            }
        }

        String parsedPhoneNumber = null;
        if (hasPhoneNumber) {
            Object raw = body.get("phoneNumber");
            if (raw != null) {
                if (!(raw instanceof String)) {
                    errors.add("phoneNumber: Phone number must be a string");
                } else {
                    parsedPhoneNumber = (String) raw;
                    if (parsedPhoneNumber.length() > 20) {
                        errors.add("phoneNumber: Phone number must not exceed 20 characters");
                    } else if (!PHONE_PATTERN.matcher(parsedPhoneNumber).matches()) {
                        errors.add("phoneNumber: Phone number must match international format ^\\+?[0-9]{8,15}$");
                    }
                }
            }
        }

        String parsedAvatarUrl = null;
        if (hasAvatarUrl) {
            Object raw = body.get("avatarUrl");
            if (raw != null) {
                if (!(raw instanceof String)) {
                    errors.add("avatarUrl: Avatar URL must be a string");
                } else {
                    parsedAvatarUrl = (String) raw;
                    if (parsedAvatarUrl.length() > 2048) {
                        errors.add("avatarUrl: Avatar URL must not exceed 2048 characters");
                    } else if (!isValidAbsoluteUrl(parsedAvatarUrl)) {
                        errors.add("avatarUrl: Avatar URL must be a well-formed absolute URL");
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }

        return new UpdateProfileRequest(
                parsedFullName != null ? parsedFullName.trim() : null,
                hasFullName,
                parsedPhoneNumber,
                hasPhoneNumber,
                parsedAvatarUrl,
                hasAvatarUrl);
    }

    private static boolean isValidAbsoluteUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }

    public UpdateUserProfileCommand toCommand() {
        return new UpdateUserProfileCommand(
                fullName,
                fullNamePresent,
                phoneNumber,
                phoneNumberPresent,
                avatarUrl,
                avatarUrlPresent);
    }
}
