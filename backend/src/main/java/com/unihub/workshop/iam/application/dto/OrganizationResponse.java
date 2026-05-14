package com.unihub.workshop.iam.application.dto;

public record OrganizationResponse(
        String id,
        String name,
        String description,
        String address,
        String status,
        String ownerId) {
}
