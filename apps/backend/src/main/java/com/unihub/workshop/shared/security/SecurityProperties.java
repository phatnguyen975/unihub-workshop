package com.unihub.workshop.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    List<String> publicPaths,
    int presignTtlMinutes,
    int accessTokenTtlMinutes,
    int refreshTokenTtlDays
) {}
