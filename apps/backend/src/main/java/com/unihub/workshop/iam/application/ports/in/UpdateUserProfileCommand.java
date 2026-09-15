package com.unihub.workshop.iam.application.ports.in;

import lombok.Builder;

@Builder
public record UpdateUserProfileCommand(
    String fullName,
    boolean hasFullName,
    String phoneNumber,
    boolean hasPhoneNumber,
    String avatarUrl,
    boolean hasAvatarUrl
) {}
