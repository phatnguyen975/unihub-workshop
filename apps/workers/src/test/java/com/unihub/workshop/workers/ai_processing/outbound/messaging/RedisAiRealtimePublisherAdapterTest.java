package com.unihub.workshop.workers.ai_processing.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisAiRealtimePublisherAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private RedisAiRealtimePublisherAdapter adapter;

    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        adapter = new RedisAiRealtimePublisherAdapter(
            redisTemplate,
            objectMapper,
            Optional.empty()
        );
    }

    @Test
    @DisplayName(
        "Should publish ai.summary.completed RealtimeEvent envelope to user channel"
    )
    void shouldPublishCompletedEvent() throws Exception {
        String resultText = "## Summary\nObjectives: Test";
        adapter.publishSummaryCompleted(
            ORGANIZER_ID,
            JOB_ID,
            WORKSHOP_ID,
            resultText
        );

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(
            String.class
        );
        verify(redisTemplate).convertAndSend(
            eq("unihub:stream:user:" + ORGANIZER_ID),
            messageCaptor.capture()
        );

        JsonNode root = objectMapper.readTree(messageCaptor.getValue());
        assertThat(root.get("eventType").asText()).isEqualTo(
            "ai.summary.completed"
        );
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("timestamp").asText()).isNotBlank();
        assertThat(root.get("payload").get("jobId").asText()).isEqualTo(
            JOB_ID.toString()
        );
        assertThat(root.get("payload").get("workshopId").asText()).isEqualTo(
            WORKSHOP_ID.toString()
        );
        assertThat(root.get("payload").get("resultText").asText()).isEqualTo(
            resultText
        );
    }

    @Test
    @DisplayName(
        "Should publish ai.summary.failed RealtimeEvent envelope to user channel"
    )
    void shouldPublishFailedEvent() throws Exception {
        String errorMessage = "No extractable text found in PDF brochure";
        adapter.publishSummaryFailed(
            ORGANIZER_ID,
            JOB_ID,
            WORKSHOP_ID,
            errorMessage
        );

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(
            String.class
        );
        verify(redisTemplate).convertAndSend(
            eq("unihub:stream:user:" + ORGANIZER_ID),
            messageCaptor.capture()
        );

        JsonNode root = objectMapper.readTree(messageCaptor.getValue());
        assertThat(root.get("eventType").asText()).isEqualTo(
            "ai.summary.failed"
        );
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("timestamp").asText()).isNotBlank();
        assertThat(root.get("payload").get("jobId").asText()).isEqualTo(
            JOB_ID.toString()
        );
        assertThat(root.get("payload").get("workshopId").asText()).isEqualTo(
            WORKSHOP_ID.toString()
        );
        assertThat(root.get("payload").get("errorMessage").asText()).isEqualTo(
            errorMessage
        );
    }
}
