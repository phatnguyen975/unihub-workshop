package com.unihub.workshop.registrations.presentation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RegistrationPresentationMapperTest {

    private RegistrationPresentationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RegistrationPresentationMapper.class);
    }

    @Test
    @DisplayName(
        "toResponse maps Registration with QrTicket to RegistrationResponse"
    )
    void toResponse_WithQrTicket_MapsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration registration = Registration.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(
                new QrTicket("{\"regId\":\"" + id + "\"}", "sig-abc", now)
            )
            .createdAt(now)
            .build();

        RegistrationResponse response = mapper.toResponse(registration);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.workshopId()).isEqualTo(workshopId);
        assertThat(response.studentId()).isEqualTo(studentUserId);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.priceAtRegistration()).isEqualByComparingTo(
            BigDecimal.ZERO
        );
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.qrPayload()).isEqualTo(
            "{\"regId\":\"" + id + "\"}"
        );
        assertThat(response.qrSignature()).isEqualTo("sig-abc");
        assertThat(response.qrIssuedAt()).isEqualTo(now);
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "toResponse maps Registration without QrTicket with null QR fields"
    )
    void toResponse_WithoutQrTicket_MapsNullQrFields() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration registration = Registration.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(null)
            .createdAt(now)
            .build();

        RegistrationResponse response = mapper.toResponse(registration);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.studentId()).isEqualTo(studentUserId);
        assertThat(response.qrPayload()).isNull();
        assertThat(response.qrSignature()).isNull();
        assertThat(response.qrIssuedAt()).isNull();
    }

    @Test
    @DisplayName("toResponse returns null when domain registration is null")
    void toResponse_NullDomain_ReturnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
