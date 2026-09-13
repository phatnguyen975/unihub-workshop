package com.unihub.workshop.shared.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seed")
public record SeedProperties(String organizations, String rootOrganizers) {}
