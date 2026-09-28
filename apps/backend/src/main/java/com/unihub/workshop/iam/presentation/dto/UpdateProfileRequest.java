package com.unihub.workshop.iam.presentation.dto;

import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.shared.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UpdateProfileRequest {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[0-9]{8,15}$"
    );
    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "fullName",
        "phoneNumber"
    );

    private final String fullName;
    private final boolean fullNamePresent;
    private final String phoneNumber;
    private final boolean phoneNumberPresent;

    public static UpdateProfileRequest from(Map<String, Object> body) {
        if (body == null) {
            throw new ValidationException("Request body must not be null");
        }

        List<String> errors = new ArrayList<>();

        List<String> unknownFields = new ArrayList<>();
        for (String key : body.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) {
                unknownFields.add(key);
            }
        }
        for (String field : unknownFields.stream().sorted().toList()) {
            errors.add(field + ": Field is immutable or unrecognized");
        }

        boolean hasFullName = body.containsKey("fullName");
        boolean hasPhoneNumber = body.containsKey("phoneNumber");

        if (!hasFullName && !hasPhoneNumber && unknownFields.isEmpty()) {
            throw new ValidationException(
                "At least one field must be provided for update."
            );
        }

        String parsedFullName = null;
        if (hasFullName) {
            Object raw = body.get("fullName");
            if (raw == null) {
                errors.add("fullName: Full name may not be set to null");
            } else if (!(raw instanceof String)) {
                errors.add("fullName: Full name must be a string");
            } else {
                String str = ((String) raw).trim();
                if (str.isEmpty()) {
                    errors.add("fullName: Full name must not be blank");
                } else if (str.length() > 200) {
                    errors.add(
                        "fullName: Full name must not exceed 200 characters"
                    );
                } else {
                    parsedFullName = str;
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
                    String str = ((String) raw).trim();
                    if (str.length() > 20) {
                        errors.add(
                            "phoneNumber: Phone number must not exceed 20 characters"
                        );
                    } else if (!PHONE_PATTERN.matcher(str).matches()) {
                        errors.add(
                            "phoneNumber: Phone number must match international format ^\\+?[0-9]{8,15}$"
                        );
                    } else {
                        parsedPhoneNumber = str;
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }

        return new UpdateProfileRequest(
            parsedFullName,
            hasFullName,
            parsedPhoneNumber,
            hasPhoneNumber
        );
    }

    public UpdateUserProfileCommand toCommand() {
        return new UpdateUserProfileCommand(
            fullName,
            fullNamePresent,
            phoneNumber,
            phoneNumberPresent
        );
    }
}
