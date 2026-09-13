package com.unihub.workshop.shared.realtime.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RealtimeEvent Serialization Unit Tests")
class RealtimeEventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should serialize notification.new event matching API contract schema")
    void shouldSerialize_notificationNew() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        Instant timestamp = Instant.parse("2026-09-09T05:15:00Z");
        Map<String, Object> payload = Map.of(
                "id", "7f2c1b4d-9a8e-4a6c-b3d1-e5c7a9f8b012",
                "title", "Workshop Venue Changed",
                "type", "WORKSHOP_RESCHEDULED",
                "linkUrl", "/workshops/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "createdAt", "2026-09-09T05:15:00Z"
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.NOTIFICATION_NEW.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventId").asText()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
        assertThat(root.get("eventType").asText()).isEqualTo("notification.new");
        assertThat(root.get("timestamp").asText()).isEqualTo("2026-09-09T05:15:00Z");
        assertThat(root.get("payload").get("title").asText()).isEqualTo("Workshop Venue Changed");
        assertThat(root.get("payload").get("type").asText()).isEqualTo("WORKSHOP_RESCHEDULED");
    }

    @Test
    @DisplayName("should serialize notification.read event matching API contract schema")
    void shouldSerialize_notificationRead() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
        Instant timestamp = Instant.parse("2026-09-09T05:15:30Z");
        Map<String, Object> payload = Map.of(
                "id", "7f2c1b4d-9a8e-4a6c-b3d1-e5c7a9f8b012",
                "all", false,
                "unreadCount", 2,
                "readAt", "2026-09-09T05:15:30Z"
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.NOTIFICATION_READ.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("notification.read");
        assertThat(root.get("payload").get("unreadCount").asInt()).isEqualTo(2);
        assertThat(root.get("payload").get("all").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("should serialize ai.summary.completed event matching API contract schema")
    void shouldSerialize_aiSummaryCompleted() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
        Instant timestamp = Instant.parse("2026-09-09T05:16:00Z");
        Map<String, Object> payload = Map.of(
                "jobId", "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                "workshopId", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "resultText", "This interactive workshop covers advanced Cloud-Native architecture..."
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.AI_SUMMARY_COMPLETED.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("ai.summary.completed");
        assertThat(root.get("payload").get("resultText").asText()).startsWith("This interactive");
    }

    @Test
    @DisplayName("should serialize ai.summary.failed event matching API contract schema")
    void shouldSerialize_aiSummaryFailed() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
        Instant timestamp = Instant.parse("2026-09-09T05:16:15Z");
        Map<String, Object> payload = Map.of(
                "jobId", "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                "workshopId", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "errorMessage", "PDF document contains encrypted text layer and cannot be extracted."
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.AI_SUMMARY_FAILED.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("ai.summary.failed");
        assertThat(root.get("payload").get("errorMessage").asText()).contains("encrypted text layer");
    }

    @Test
    @DisplayName("should serialize attendance.updated event matching API contract schema")
    void shouldSerialize_attendanceUpdated() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440005");
        Instant timestamp = Instant.parse("2026-09-09T05:17:00Z");
        Map<String, Object> payload = Map.of(
                "workshopId", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "checkedInCount", 85,
                "capacity", 100,
                "lastSyncedAt", "2026-09-09T05:17:00Z"
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.ATTENDANCE_UPDATED.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("attendance.updated");
        assertThat(root.get("payload").get("checkedInCount").asInt()).isEqualTo(85);
        assertThat(root.get("payload").get("capacity").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("should serialize registration.updated event matching API contract schema")
    void shouldSerialize_registrationUpdated() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440006");
        Instant timestamp = Instant.parse("2026-09-09T05:17:30Z");
        Map<String, Object> payload = Map.of(
                "workshopId", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "confirmedCount", 95,
                "pendingCount", 5,
                "capacity", 100,
                "availableSeats", 0,
                "lastUpdatedAt", "2026-09-09T05:17:30Z"
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.REGISTRATION_UPDATED.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("registration.updated");
        assertThat(root.get("payload").get("confirmedCount").asInt()).isEqualTo(95);
        assertThat(root.get("payload").get("availableSeats").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("should serialize payment.confirmed event matching API contract schema")
    void shouldSerialize_paymentConfirmed() throws Exception {
        UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440007");
        Instant timestamp = Instant.parse("2026-09-09T05:18:00Z");
        Map<String, Object> payload = Map.of(
                "registrationId", "3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f",
                "workshopId", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                "qrPayload", "unihub:reg:3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f:sig:abc123xyz",
                "qrIssuedAt", "2026-09-09T05:18:00Z"
        );

        RealtimeEvent<Map<String, Object>> event = new RealtimeEvent<>(
                eventId,
                RealtimeEventType.PAYMENT_CONFIRMED.getValue(),
                timestamp,
                payload
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("eventType").asText()).isEqualTo("payment.confirmed");
        assertThat(root.get("payload").get("registrationId").asText()).isEqualTo("3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f");
        assertThat(root.get("payload").get("qrPayload").asText()).startsWith("unihub:reg:");
    }
}
