package com.unihub.workshop.iam.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.iam.seed")
public class OrganizerSeedProperties {

    private String organizersJson = "[]";

    public String getOrganizersJson() {
        return organizersJson;
    }

    public void setOrganizersJson(String organizersJson) {
        this.organizersJson = organizersJson;
    }
}
