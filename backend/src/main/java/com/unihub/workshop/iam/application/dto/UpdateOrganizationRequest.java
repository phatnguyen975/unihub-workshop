package com.unihub.workshop.iam.application.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;

public class UpdateOrganizationRequest {

    @Size(min = 1, max = 200, message = "Organization name must be between 1 and 200 characters")
    private String name;
    private boolean nameProvided;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;
    private boolean descriptionProvided;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;
    private boolean addressProvided;

    public String name() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public boolean isNameProvided() {
        return nameProvided;
    }

    public String description() {
        return description;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    public boolean isDescriptionProvided() {
        return descriptionProvided;
    }

    public String address() {
        return address;
    }

    @JsonSetter("address")
    public void setAddress(String address) {
        this.address = address;
        this.addressProvided = true;
    }

    public boolean isAddressProvided() {
        return addressProvided;
    }
}
