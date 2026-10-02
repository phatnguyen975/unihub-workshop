package com.unihub.workshop.workers.search_sync.inbound.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SearchSyncEventMapper {

    public record ReschedulePayload(
        UUID workshopId,
        Instant startsAt,
        Instant endsAt,
        String timezone
    ) {}

    public WorkshopDocument toWorkshopDocument(JsonNode root) {
        if (root == null) {
            return null;
        }

        UUID workshopId = extractWorkshopId(root, "WorkshopEvent");
        UUID organizationId = parseUuid(
            root.path("organizationId").asText(null)
        );
        String title = root.path("title").asText(null);
        String slug = root.path("slug").asText(null);
        String description = root.path("description").asText(null);
        String coverImageUrl = root.path("coverImageUrl").asText(null);
        String location = root.path("location").asText(null);
        String timezone = root.path("timezone").asText(null);
        Instant startsAt = parseInstant(root.path("startsAt").asText(null));
        Instant endsAt = parseInstant(root.path("endsAt").asText(null));
        Integer capacity = root.hasNonNull("capacity")
            ? root.path("capacity").asInt()
            : null;
        BigDecimal price = root.hasNonNull("price")
            ? new BigDecimal(root.path("price").asText())
            : BigDecimal.ZERO;
        String currency = root.path("currency").asText("USD");
        Instant publishedAt = parseInstant(
            root.path("publishedAt").asText(null)
        );

        List<WorkshopDocument.SpeakerDocument> speakers = new ArrayList<>();
        JsonNode speakersNode = root.path("speakers");
        if (speakersNode != null && speakersNode.isArray()) {
            for (JsonNode s : speakersNode) {
                speakers.add(
                    new WorkshopDocument.SpeakerDocument(
                        parseUuid(s.path("id").asText(null)),
                        s.path("name").asText(null),
                        s.path("bio").asText(null),
                        s.path("avatarUrl").asText(null),
                        s.path("linkedinUrl").asText(null),
                        s.path("email").asText(null),
                        s.hasNonNull("sortOrder")
                            ? s.path("sortOrder").asInt()
                            : 0
                    )
                );
            }
        }

        return new WorkshopDocument(
            workshopId,
            organizationId,
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            startsAt,
            endsAt,
            capacity,
            price,
            currency,
            "PUBLISHED",
            publishedAt,
            speakers
        );
    }

    public ReschedulePayload toReschedulePayload(JsonNode root) {
        if (root == null) {
            throw new IllegalArgumentException(
                "WorkshopRescheduledEvent payload must not be null"
            );
        }
        UUID workshopId = extractWorkshopId(root, "WorkshopRescheduledEvent");
        Instant startsAt = parseInstant(
            root.hasNonNull("newStartsAt")
                ? root.path("newStartsAt").asText()
                : root.path("startsAt").asText(null)
        );
        Instant endsAt = parseInstant(
            root.hasNonNull("newEndsAt")
                ? root.path("newEndsAt").asText()
                : root.path("endsAt").asText(null)
        );
        String timezone = root.path("timezone").asText(null);
        return new ReschedulePayload(workshopId, startsAt, endsAt, timezone);
    }

    public UUID extractWorkshopId(JsonNode root, String eventType) {
        if (root == null) {
            throw new IllegalArgumentException(
                eventType + " payload must not be null"
            );
        }
        UUID workshopId = parseUuid(root.path("workshopId").asText(null));
        if (workshopId == null) {
            throw new IllegalArgumentException(
                eventType + " missing required workshopId"
            );
        }
        return workshopId;
    }

    public UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
