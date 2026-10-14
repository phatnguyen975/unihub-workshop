package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetRegistrationDetailUseCaseImplTest {

    private RegistrationRepository registrationRepository;
    private WorkshopApi workshopApi;
    private GetRegistrationDetailUseCaseImpl useCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        workshopApi = mock(WorkshopApi.class);
        useCase = new GetRegistrationDetailUseCaseImpl(
            registrationRepository,
            workshopApi
        );
    }

    @Test
    @DisplayName(
        "getRegistrationDetail: when registration is CONFIRMED and caller is owner, returns full detail with QR and workshop info"
    )
    void getRegistrationDetail_whenConfirmedAndOwner_returnsFullDetailWithQrAndEnrichedWorkshop() {
        QrTicket qrTicket = new QrTicket("payload-data", "signature-data", now);
        Registration registration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(qrTicket)
            .createdAt(now)
            .updatedAt(now)
            .build();

        WorkshopRegistrationDetailsDto workshopDto =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Cloud Architecture 101",
                "cloud-arch-101",
                "PUBLISHED",
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                BigDecimal.ZERO,
                100,
                "Hall A-102"
            );

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(registration)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshopDto)
        );

        RegistrationDetail result = useCase.getRegistrationDetail(
            studentUserId,
            registrationId
        );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(registrationId);
        assertThat(result.workshopId()).isEqualTo(workshopId);
        assertThat(result.workshopTitle()).isEqualTo("Cloud Architecture 101");
        assertThat(result.workshopStartsAt()).isEqualTo(workshopDto.startsAt());
        assertThat(result.workshopEndsAt()).isEqualTo(workshopDto.endsAt());
        assertThat(result.workshopLocation()).isEqualTo("Hall A-102");
        assertThat(result.studentId()).isEqualTo(studentUserId);
        assertThat(result.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.qrPayload()).isEqualTo("payload-data");
        assertThat(result.qrSignature()).isEqualTo("signature-data");
        assertThat(result.qrIssuedAt()).isEqualTo(now);

        verify(registrationRepository).findById(registrationId);
        verify(workshopApi).getWorkshopForRegistration(workshopId);
    }

    @Test
    @DisplayName(
        "getRegistrationDetail: when registration is CANCELED, returns detail with QR fields null"
    )
    void getRegistrationDetail_whenCanceled_returnsDetailWithoutQr() {
        QrTicket qrTicket = new QrTicket("payload-data", "signature-data", now);
        Registration registration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CANCELED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(qrTicket)
            .createdAt(now)
            .updatedAt(now)
            .build();

        WorkshopRegistrationDetailsDto workshopDto =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Cloud Architecture 101",
                "cloud-arch-101",
                "PUBLISHED",
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                BigDecimal.ZERO,
                100,
                "Hall A-102"
            );

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(registration)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshopDto)
        );

        RegistrationDetail result = useCase.getRegistrationDetail(
            studentUserId,
            registrationId
        );

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RegistrationStatus.CANCELED);
        assertThat(result.qrPayload()).isNull();
        assertThat(result.qrSignature()).isNull();
        assertThat(result.qrIssuedAt()).isNull();
    }

    @Test
    @DisplayName(
        "getRegistrationDetail: when registration not found, throws NotFoundException"
    )
    void getRegistrationDetail_whenNotFound_throwsNotFoundException() {
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.getRegistrationDetail(studentUserId, registrationId)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(registrationId.toString());

        verifyNoInteractions(workshopApi);
    }

    @Test
    @DisplayName(
        "getRegistrationDetail: when caller is not the owner, throws ForbiddenException"
    )
    void getRegistrationDetail_whenNotOwner_throwsForbiddenException() {
        UUID differentStudentId = UUID.randomUUID();
        Registration registration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(differentStudentId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(registration)
        );

        assertThatThrownBy(() ->
            useCase.getRegistrationDetail(studentUserId, registrationId)
        )
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining(
                "You do not have permission to view this registration"
            );

        verifyNoInteractions(workshopApi);
    }

    @Test
    @DisplayName(
        "getRegistrationDetail: when workshop details missing from facade, returns fallback metadata"
    )
    void getRegistrationDetail_whenWorkshopMissing_returnsFallbackMetadata() {
        Registration registration = Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(registration)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );

        RegistrationDetail result = useCase.getRegistrationDetail(
            studentUserId,
            registrationId
        );

        assertThat(result).isNotNull();
        assertThat(result.workshopTitle()).isEqualTo("Unknown Workshop");
        assertThat(result.workshopStartsAt()).isNull();
        assertThat(result.workshopEndsAt()).isNull();
        assertThat(result.workshopLocation()).isNull();
    }
}
