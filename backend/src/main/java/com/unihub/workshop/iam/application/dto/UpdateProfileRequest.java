package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public class UpdateProfileRequest {

    @Size(min = 1, max = 120, message = "Full name must be between 1 and 120 characters")
    private JsonNullable<String> fullName = JsonNullable.undefined();

    @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "Phone number is invalid")
    private JsonNullable<String> phone = JsonNullable.undefined();

    public JsonNullable<String> fullName() {
        return fullName;
    }

    public void setFullName(JsonNullable<String> fullName) {
        this.fullName = fullName;
    }

    public JsonNullable<String> phone() {
        return phone;
    }

    public void setPhone(JsonNullable<String> phone) {
        this.phone = phone;
    }
}
