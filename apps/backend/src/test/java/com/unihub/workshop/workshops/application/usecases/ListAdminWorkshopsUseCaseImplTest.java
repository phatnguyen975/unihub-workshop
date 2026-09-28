package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ListAdminWorkshopsUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private ListAdminWorkshopsUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        useCase = new ListAdminWorkshopsUseCaseImpl(workshopRepository);
    }

    @Test
    @DisplayName("Should throw BadRequestException when organizationId is null")
    void shouldThrowWhenOrgIdIsNull() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(null)
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Organization ID is required");
    }

    @Test
    @DisplayName("Should throw BadRequestException when size is less than 1")
    void shouldThrowWhenSizeTooSmall() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .size(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("size must be between 1 and 100");
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when size is greater than 100"
    )
    void shouldThrowWhenSizeTooLarge() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .size(101)
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("size must be between 1 and 100");
    }

    @Test
    @DisplayName(
        "Should default size to 20 and sort to createdAt,desc when omitted"
    )
    void shouldDefaultSizeAndSort() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .size(null)
            .sort(null)
            .build();

        when(
            workshopRepository.findAllByOrganizationWithFilters(any())
        ).thenReturn(new CursorPage<>(List.of(), null, false));

        useCase.execute(query);

        ArgumentCaptor<ListAdminWorkshopsQuery> captor =
            ArgumentCaptor.forClass(ListAdminWorkshopsQuery.class);
        verify(workshopRepository).findAllByOrganizationWithFilters(
            captor.capture()
        );
        ListAdminWorkshopsQuery captured = captor.getValue();

        assertThat(captured.size()).isEqualTo(20);
        assertThat(captured.sort()).isEqualTo("createdAt,desc");
    }

    @Test
    @DisplayName("Should throw BadRequestException when priceType is invalid")
    void shouldThrowWhenPriceTypeIsInvalid() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .priceType("EXPENSIVE")
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid priceType: EXPENSIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = { "FREE", "free", "PAID", "paid" })
    @DisplayName(
        "Should accept valid priceType values (FREE, PAID case-insensitively)"
    )
    void shouldAcceptValidPriceType(String priceType) {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .priceType(priceType)
            .build();

        when(
            workshopRepository.findAllByOrganizationWithFilters(any())
        ).thenReturn(new CursorPage<>(List.of(), null, false));

        useCase.execute(query);

        ArgumentCaptor<ListAdminWorkshopsQuery> captor =
            ArgumentCaptor.forClass(ListAdminWorkshopsQuery.class);
        verify(workshopRepository).findAllByOrganizationWithFilters(
            captor.capture()
        );
        assertThat(captor.getValue().priceType()).isEqualTo(
            priceType.toUpperCase()
        );
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when sort parameter is unsupported"
    )
    void shouldThrowWhenSortIsUnsupported() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .sort("title,asc")
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid sort parameter: title,asc");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "createdAt,desc",
            "createdAt,asc",
            "startsAt,asc",
            "startsAt,desc",
            "capacity,asc",
            "capacity,desc",
            "price,asc",
            "price,desc",
        }
    )
    @DisplayName("Should accept all supported sort parameters")
    void shouldAcceptSupportedSorts(String sort) {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .sort(sort)
            .build();

        when(
            workshopRepository.findAllByOrganizationWithFilters(any())
        ).thenReturn(new CursorPage<>(List.of(), null, false));

        useCase.execute(query);

        ArgumentCaptor<ListAdminWorkshopsQuery> captor =
            ArgumentCaptor.forClass(ListAdminWorkshopsQuery.class);
        verify(workshopRepository).findAllByOrganizationWithFilters(
            captor.capture()
        );
        assertThat(captor.getValue().sort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("Should throw BadRequestException when 'from' is after 'to'")
    void shouldThrowWhenFromIsAfterTo() {
        Instant from = Instant.parse("2026-10-15T12:00:00Z");
        Instant to = Instant.parse("2026-10-15T09:00:00Z");

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .from(from)
            .to(to)
            .build();

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "'from' timestamp cannot be after 'to' timestamp"
            );
    }

    @Test
    @DisplayName(
        "Should return cursor page of workshop summaries from repository"
    )
    void shouldReturnCursorPageFromRepository() {
        WorkshopSummary summary = WorkshopSummary.builder()
            .id(UUID.randomUUID())
            .title("Spring Boot Workshop")
            .slug("spring-boot-workshop")
            .status(WorkshopStatus.DRAFT)
            .price(BigDecimal.ZERO)
            .speakerCount(2)
            .createdAt(Instant.now())
            .build();

        CursorPage<WorkshopSummary> expectedPage = new CursorPage<>(
            List.of(summary),
            "next-cursor",
            true
        );
        when(
            workshopRepository.findAllByOrganizationWithFilters(any())
        ).thenReturn(expectedPage);

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .q("spring")
            .build();

        CursorPage<WorkshopSummary> result = useCase.execute(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo(
            "Spring Boot Workshop"
        );
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
        assertThat(result.hasMore()).isTrue();
    }
}
