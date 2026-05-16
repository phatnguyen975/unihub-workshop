package com.unihub.workshop.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.iam.seed")
@Getter
@Setter
public class OrganizerSeedProperties {

    private String organizersJson = "[]";
}
