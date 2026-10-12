package com.unihub.workshop.workers.ai_processing.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.ai_processing.core.ports.out.RealtimePublisherPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisAiRealtimePublisherAdapter implements RealtimePublisherPort {

    public static final String EVENT_TYPE_COMPLETED = "ai.summary.completed";
    public static final String EVENT_TYPE_FAILED = "ai.summary.failed";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RedisAiRealtimePublisherAdapter(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        Optional<MeterRegistry> meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry =
            meterRegistry != null ? meterRegistry.orElse(null) : null;
    }

    @Override
    public void publishSummaryCompleted(
        UUID organizerId,
        UUID jobId,
        UUID workshopId,
        String resultText
    ) {
        Map<String, Object> payload = Map.of(
            "jobId",
            jobId.toString(),
            "workshopId",
            workshopId.toString(),
            "resultText",
            resultText != null ? resultText : ""
        );
        publishEvent(organizerId, EVENT_TYPE_COMPLETED, payload);
    }

    @Override
    public void publishSummaryFailed(
        UUID organizerId,
        UUID jobId,
        UUID workshopId,
        String errorMessage
    ) {
        Map<String, Object> payload = Map.of(
            "jobId",
            jobId.toString(),
            "workshopId",
            workshopId.toString(),
            "errorMessage",
            errorMessage != null ? errorMessage : ""
        );
        publishEvent(organizerId, EVENT_TYPE_FAILED, payload);
    }

    private void publishEvent(
        UUID organizerId,
        String eventType,
        Map<String, Object> payload
    ) {
        if (organizerId == null) {
            log.warn("Skipping real-time event publish: organizerId is null");
            return;
        }

        String channel = "unihub:stream:user:" + organizerId;
        Map<String, Object> envelope = Map.of(
            "eventId",
            UUID.randomUUID().toString(),
            "eventType",
            eventType,
            "timestamp",
            Instant.now().toString(),
            "payload",
            payload
        );

        try {
            String jsonMessage = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(channel, jsonMessage);

            if (meterRegistry != null) {
                meterRegistry
                    .counter(
                        "ai.summarization.realtime.success",
                        "type",
                        eventType
                    )
                    .increment();
            }
            log.debug(
                "Published real-time event '{}' to channel '{}'",
                eventType,
                channel
            );
        } catch (Exception e) {
            if (meterRegistry != null) {
                meterRegistry
                    .counter(
                        "ai.summarization.realtime.failures",
                        "type",
                        eventType
                    )
                    .increment();
            }
            log.warn(
                "Failed to publish real-time event '{}' to channel '{}': {}",
                eventType,
                channel,
                e.getMessage()
            );
        }
    }
}
