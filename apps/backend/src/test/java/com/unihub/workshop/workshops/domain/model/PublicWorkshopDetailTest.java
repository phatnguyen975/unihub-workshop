package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicWorkshopDetailTest {

    @Test
    @DisplayName(
        "from: creates detail with speakers sorted by sortOrder ASC and derives priceType FREE"
    )
    void shouldCreateDetailWithSortedSpeakersAndFreePrice() {
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Speaker speaker2 = Speaker.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .name("Bob")
            .sortOrder(2)
            .build();
        Speaker speaker1 = Speaker.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .name("Alice")
            .sortOrder(1)
            .build();

        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Clean Architecture")
            .slug("clean-architecture")
            .description("Practical guide")
            .coverImageUrl("https://cdn.example.com/cover.jpg")
            .location("Room 101")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.parse("2026-11-20T09:00:00Z"))
            .endsAt(Instant.parse("2026-11-20T11:00:00Z"))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .publishedAt(Instant.parse("2026-11-01T10:00:00Z"))
            .speakers(List.of(speaker2, speaker1))
            .build();

        PublicWorkshopDetail detail = PublicWorkshopDetail.from(workshop, 42);

        assertThat(detail.id()).isEqualTo(workshopId);
        assertThat(detail.title()).isEqualTo("Clean Architecture");
        assertThat(detail.priceType()).isEqualTo("FREE");
        assertThat(detail.availableSeats()).isEqualTo(42);
        assertThat(detail.speakers()).hasSize(2);
        assertThat(detail.speakers().get(0).name()).isEqualTo("Alice");
        assertThat(detail.speakers().get(1).name()).isEqualTo("Bob");
    }

    @Test
    @DisplayName(
        "from: derives priceType PAID when price > 0 and clamps negative availableSeats to 0"
    )
    void shouldDerivePaidPriceTypeAndClampNegativeSeats() {
        UUID workshopId = UUID.randomUUID();
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(UUID.randomUUID())
            .title("Advanced Microservices")
            .slug("adv-microservices")
            .capacity(50)
            .price(new BigDecimal("25.00"))
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .build();

        PublicWorkshopDetail detail = PublicWorkshopDetail.from(workshop, -5);

        assertThat(detail.priceType()).isEqualTo("PAID");
        assertThat(detail.availableSeats()).isZero();
    }
}
