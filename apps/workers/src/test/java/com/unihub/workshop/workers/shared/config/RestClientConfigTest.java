package com.unihub.workshop.workers.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    @Test
    @DisplayName("Should provide non-null RestClient.Builder instance")
    void shouldProvideRestClientBuilder() {
        RestClientConfig config = new RestClientConfig();
        RestClient.Builder builder = config.restClientBuilder();

        assertThat(builder).isNotNull();
        RestClient client = builder.build();
        assertThat(client).isNotNull();
    }
}
