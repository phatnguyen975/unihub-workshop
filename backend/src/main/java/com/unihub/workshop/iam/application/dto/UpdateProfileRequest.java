package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
                @Size(min = 1, max = 120) String fullName,
                @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "must be a valid phone format") String phone) {
}
