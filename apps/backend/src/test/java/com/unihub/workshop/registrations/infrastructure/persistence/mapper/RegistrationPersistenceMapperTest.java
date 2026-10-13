package com.unihub.workshop.registrations.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.infrastructure.persistence.RegistrationJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RegistrationPersistenceMapperTest {

    private RegistrationPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RegistrationPersistenceMapper.class);
    }

    @Test
    @DisplayName(
        "toDomain maps RegistrationJpaEntity to Registration with QrTicket"
    )
    void toDomain_WithQrTicket_MapsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID idempotencyKeyId = UUID.randomUUID();
        Instant now = Instant.now();

        RegistrationJpaEntity entity = RegistrationJpaEntity.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status("CONFIRMED")
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyId)
            .qrPayload("{\"regId\":\"" + id + "\"}")
            .qrSignature("sig123")
            .qrIssuedAt(now)
            .expiresAt(null)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();

        Registration domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.id()).isEqualTo(id);
        assertThat(domain.workshopId()).isEqualTo(workshopId);
        assertThat(domain.studentUserId()).isEqualTo(studentUserId);
        assertThat(domain.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(domain.priceAtRegistration()).isEqualByComparingTo(
            BigDecimal.ZERO
        );
        assertThat(domain.currency()).isEqualTo("USD");
        assertThat(domain.idempotencyKeyId()).isEqualTo(idempotencyKeyId);
        assertThat(domain.qrTicket()).isNotNull();
        assertThat(domain.qrTicket().qrPayload()).isEqualTo(
            "{\"regId\":\"" + id + "\"}"
        );
        assertThat(domain.qrTicket().qrSignature()).isEqualTo("sig123");
        assertThat(domain.qrTicket().qrIssuedAt()).isEqualTo(now);
        assertThat(domain.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "toDomain maps RegistrationJpaEntity with null QR fields to null QrTicket"
    )
    void toDomain_WithoutQrTicket_MapsQrTicketAsNull() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Instant now = Instant.now();

        RegistrationJpaEntity entity = RegistrationJpaEntity.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status("CONFIRMED")
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrPayload(null)
            .qrSignature(null)
            .qrIssuedAt(null)
            .createdAt(now)
            .build();

        Registration domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.qrTicket()).isNull();
    }

    @Test
    @DisplayName("toDomain returns null when entity is null")
    void toDomain_NullEntity_ReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("toDomainList maps entity list correctly")
    void toDomainList_MapsList() {
        RegistrationJpaEntity entity = RegistrationJpaEntity.builder()
            .id(UUID.randomUUID())
            .workshopId(UUID.randomUUID())
            .studentUserId(UUID.randomUUID())
            .status("CONFIRMED")
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .createdAt(Instant.now())
            .build();

        List<Registration> domains = mapper.toDomainList(List.of(entity));

        assertThat(domains).hasSize(1);
        assertThat(domains.get(0).id()).isEqualTo(entity.getId());
    }

    @Test
    @DisplayName("toEntity maps Registration to RegistrationJpaEntity")
    void toEntity_WithQrTicket_MapsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        UUID idempotencyKeyId = UUID.randomUUID();
        Instant now = Instant.now();

        Registration domain = Registration.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyId)
            .qrTicket(new QrTicket("{\"regId\":\"" + id + "\"}", "sig123", now))
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();

        RegistrationJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getWorkshopId()).isEqualTo(workshopId);
        assertThat(entity.getStudentUserId()).isEqualTo(studentUserId);
        assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        assertThat(entity.getPriceAtRegistration()).isEqualByComparingTo(
            BigDecimal.ZERO
        );
        assertThat(entity.getCurrency()).isEqualTo("USD");
        assertThat(entity.getIdempotencyKeyId()).isEqualTo(idempotencyKeyId);
        assertThat(entity.getQrPayload()).isEqualTo(
            "{\"regId\":\"" + id + "\"}"
        );
        assertThat(entity.getQrSignature()).isEqualTo("sig123");
        assertThat(entity.getQrIssuedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "toEntity maps Registration without QrTicket to entity with null QR fields"
    )
    void toEntity_WithoutQrTicket_MapsNullQrFields() {
        Registration domain = Registration.builder()
            .id(UUID.randomUUID())
            .workshopId(UUID.randomUUID())
            .studentUserId(UUID.randomUUID())
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(null)
            .createdAt(Instant.now())
            .build();

        RegistrationJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getQrPayload()).isNull();
        assertThat(entity.getQrSignature()).isNull();
        assertThat(entity.getQrIssuedAt()).isNull();
    }

    @Test
    @DisplayName("toEntity returns null when domain is null")
    void toEntity_NullDomain_ReturnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }
}
