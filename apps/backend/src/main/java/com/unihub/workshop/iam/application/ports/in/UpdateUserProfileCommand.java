package com.unihub.workshop.iam.application.ports.in;

public record UpdateUserProfileCommand(
        String fullName,
        boolean hasFullName,
        String phoneNumber,
        boolean hasPhoneNumber,
        String avatarUrl,
        boolean hasAvatarUrl) {
}
