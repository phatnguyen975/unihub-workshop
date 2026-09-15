package com.unihub.workshop.iam.presentation.dto;

import lombok.Builder;

@Builder
public record LogoutRequest(String refreshToken) {}
