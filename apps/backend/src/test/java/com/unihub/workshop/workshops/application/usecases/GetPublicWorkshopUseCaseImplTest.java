package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPublicWorkshopUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopAvailabilityPort availabilityPort;

    private GetPublicWorkshopUseCaseImpl useCase;

    private final UUID workshopId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetPublicWorkshopUseCaseImpl(
            workshopRepository,
            availabilityPort
        );
    }

    private Workshop createWorkshop(
        UUID id,
        String slug,
        WorkshopStatus status
    ) {
        return Workshop.builder()
            .id(id)
            .organizationId(orgId)
            .title("Test Workshop")
            .slug(slug)
            .description("Description")
            .coverImageUrl("https://cdn.example.com/cover.jpg")
            .location("Hall A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.parse("2026-11-20T09:00:00Z"))
            .endsAt(Instant.parse("2026-11-20T11:00:00Z"))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(status)
            .publishedAt(
                status == WorkshopStatus.PUBLISHED
                    ? Instant.parse("2026-11-01T10:00:00Z")
                    : null
            )
            .build();
    }

    @Test
    @DisplayName("execute: retrieves published workshop by UUID identifier")
    void shouldRetrieveWorkshopByUuid() {
        Workshop workshop = createWorkshop(
            workshopId,
            "test-workshop",
            WorkshopStatus.PUBLISHED
        );
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            availabilityPort.resolveAvailableSeats(workshopId, 100)
        ).thenReturn(65);

        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(
            workshopId.toString()
        );
        PublicWorkshopDetail detail = useCase.execute(query);

        assertThat(detail.id()).isEqualTo(workshopId);
        assertThat(detail.slug()).isEqualTo("test-workshop");
        assertThat(detail.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(detail.availableSeats()).isEqualTo(65);
        verify(workshopRepository).findById(workshopId);
        verify(workshopRepository, never()).findBySlug(any());
    }

    @Test
    @DisplayName("execute: retrieves published workshop by slug identifier")
    void shouldRetrieveWorkshopBySlug() {
        Workshop workshop = createWorkshop(
            workshopId,
            "spring-boot-3",
            WorkshopStatus.PUBLISHED
        );
        when(workshopRepository.findBySlug("spring-boot-3")).thenReturn(
            Optional.of(workshop)
        );
        when(
            availabilityPort.resolveAvailableSeats(workshopId, 100)
        ).thenReturn(40);

        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(
            "spring-boot-3"
        );
        PublicWorkshopDetail detail = useCase.execute(query);

        assertThat(detail.id()).isEqualTo(workshopId);
        assertThat(detail.slug()).isEqualTo("spring-boot-3");
        assertThat(detail.availableSeats()).isEqualTo(40);
        verify(workshopRepository).findBySlug("spring-boot-3");
        verify(workshopRepository, never()).findById(any());
    }

    @Test
    @DisplayName(
        "execute: throws NotFoundException when workshop does not exist"
    )
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findBySlug("unknown-slug")).thenReturn(
            Optional.empty()
        );

        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(
            "unknown-slug"
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Workshop not found.");

        verify(availabilityPort, never()).resolveAvailableSeats(
            any(),
            any(Integer.class)
        );
    }

    @Test
    @DisplayName(
        "execute: throws NotFoundException when workshop is DRAFT (anti-enumeration)"
    )
    void shouldThrowNotFoundWhenWorkshopIsDraft() {
        Workshop draftWorkshop = createWorkshop(
            workshopId,
            "draft-slug",
            WorkshopStatus.DRAFT
        );
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(draftWorkshop)
        );

        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(
            workshopId.toString()
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Workshop not found.");

        verify(availabilityPort, never()).resolveAvailableSeats(
            any(),
            any(Integer.class)
        );
    }

    @Test
    @DisplayName(
        "execute: throws NotFoundException when workshop is CANCELED (anti-enumeration)"
    )
    void shouldThrowNotFoundWhenWorkshopIsCanceled() {
        Workshop canceledWorkshop = createWorkshop(
            workshopId,
            "canceled-slug",
            WorkshopStatus.CANCELED
        );
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(canceledWorkshop)
        );

        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(
            workshopId.toString()
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Workshop not found.");

        verify(availabilityPort, never()).resolveAvailableSeats(
            any(),
            any(Integer.class)
        );
    }

    @Test
    @DisplayName(
        "query: rejects blank or null identifier with ValidationException"
    )
    void shouldRejectBlankIdentifier() {
        assertThatThrownBy(() -> GetPublicWorkshopQuery.of("   ")).isInstanceOf(
            ValidationException.class
        );

        assertThatThrownBy(() -> GetPublicWorkshopQuery.of(null)).isInstanceOf(
            ValidationException.class
        );
    }
}
