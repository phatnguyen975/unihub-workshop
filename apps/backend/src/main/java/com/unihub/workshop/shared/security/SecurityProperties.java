package com.unihub.workshop.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        List<String> publicPaths,
        int presignTtlMinutes,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays) {
}
