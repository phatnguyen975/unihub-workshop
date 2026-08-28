package com.unihub.workshop.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

class CorsConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CorsProperties props = new CorsProperties(List.of("http://localhost:[*]", "https://app.unihub.com"));
        CorsConfig corsConfig = new CorsConfig(props);
        CorsFilter corsFilter = corsConfig.corsFilter();

        // Set up MockMvc in standalone mode with just the CorsFilter
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .addFilters(corsFilter)
                .build();
    }

    @Test
    void optionsRequestShouldExposeRateLimitHeaders() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/workshops")
                .header("Origin", "https://app.unihub.com")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-RateLimit-Limit")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-RateLimit-Remaining")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-RateLimit-Retry-After")));
    }

    @org.springframework.web.bind.annotation.RestController
    static class DummyController {
        @org.springframework.web.bind.annotation.GetMapping("/api/v1/workshops")
        public String get() {
            return "ok";
        }
    }
}
