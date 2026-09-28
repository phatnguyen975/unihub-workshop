package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GetWorkshopUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private GetWorkshopUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OTHER_ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        useCase = new GetWorkshopUseCaseImpl(workshopRepository);
    }

    private Workshop buildSampleWorkshop(
        UUID id,
        UUID organizationId,
        WorkshopStatus status
    ) {
        Speaker speaker = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Dr. Alan Turing")
            .email("alan.turing@university.edu")
            .bio("AI Researcher")
            .avatarUrl(
                "http://localhost:9000/unihub-media/speakers/avatars/alan.jpg"
            )
            .linkedinUrl("https://linkedin.com/in/alan-turing")
            .sortOrder(0)
            .build();

        return Workshop.builder()
            .id(id)
            .organizationId(organizationId)
            .title("Advanced AI Workshop")
            .slug("advanced-ai-workshop")
            .description("Comprehensive workshop on neural networks")
            .coverImageUrl(
                "http://localhost:9000/unihub-media/workshops/covers/ai.jpg"
            )
            .location("Hall A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(Instant.now().plusSeconds(3600))
            .endsAt(Instant.now().plusSeconds(7200))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(status)
            .version(1)
            .speakers(List.of(speaker))
            .createdAt(Instant.now().minusSeconds(86400))
            .updatedAt(Instant.now().minusSeconds(3600))
            .build();
    }

    @Test
    @DisplayName(
        "Successfully returns full workshop details when owned by caller's organization"
    )
    void shouldReturnWorkshop_whenOwnedByCallerOrganization() {
        Workshop sample = buildSampleWorkshop(
            WORKSHOP_ID,
            ORG_ID,
            WorkshopStatus.DRAFT
        );
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(sample)
        );

        Workshop result = useCase.execute(WORKSHOP_ID, ORG_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(WORKSHOP_ID);
        assertThat(result.organizationId()).isEqualTo(ORG_ID);
        assertThat(result.title()).isEqualTo("Advanced AI Workshop");
        assertThat(result.speakers()).hasSize(1);
        assertThat(result.speakers().get(0).name()).isEqualTo(
            "Dr. Alan Turing"
        );

        verify(workshopRepository).findById(WORKSHOP_ID);
    }

    @ParameterizedTest
    @EnumSource(WorkshopStatus.class)
    @DisplayName(
        "Allows retrieving workshop details in any status (DRAFT, PUBLISHED, COMPLETED, CANCELED)"
    )
    void shouldAllowRetrievalInAnyStatus(WorkshopStatus status) {
        Workshop sample = buildSampleWorkshop(WORKSHOP_ID, ORG_ID, status);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(sample)
        );

        Workshop result = useCase.execute(WORKSHOP_ID, ORG_ID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(status);
    }

    @Test
    @DisplayName(
        "Throws NotFoundException when workshop identifier does not exist"
    )
    void shouldThrowNotFoundException_whenWorkshopDoesNotExist() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, ORG_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");
    }

    @Test
    @DisplayName(
        "Throws ForbiddenException when workshop belongs to a different organization"
    )
    void shouldThrowForbiddenException_whenWorkshopBelongsToDifferentOrganization() {
        Workshop sample = buildSampleWorkshop(
            WORKSHOP_ID,
            OTHER_ORG_ID,
            WorkshopStatus.DRAFT
        );
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(sample)
        );

        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, ORG_ID))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("Throws BadRequestException when workshopId is null")
    void shouldThrowBadRequestException_whenWorkshopIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null, ORG_ID))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Workshop ID is required");
    }

    @Test
    @DisplayName("Throws BadRequestException when callerOrganizationId is null")
    void shouldThrowBadRequestException_whenCallerOrganizationIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, null))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Organization ID is required");
    }
}
