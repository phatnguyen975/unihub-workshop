package com.unihub.workshop.iam.application.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(min = 1, max = 120, message = "Full name must be between 1 and 120 characters")
    private String fullName;
    private boolean fullNameProvided;

    @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "Phone number is invalid")
    private String phone;
    private boolean phoneProvided;

    public String fullName() {
        return fullName;
    }

    @JsonSetter("fullName")
    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.fullNameProvided = true;
    }

    public boolean isFullNameProvided() {
        return fullNameProvided;
    }

    public String phone() {
        return phone;
    }

    @JsonSetter("phone")
    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneProvided = true;
    }

    public boolean isPhoneProvided() {
        return phoneProvided;
    }
}
