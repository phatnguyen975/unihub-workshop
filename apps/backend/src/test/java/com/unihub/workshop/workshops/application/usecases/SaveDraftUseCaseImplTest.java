package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveDraftUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private SlugGeneratorPort slugGeneratorPort;

    @Mock
    private MediaStoragePort mediaStoragePort;

    @InjectMocks
    private SaveDraftUseCaseImpl useCase;

    @Test
    @DisplayName("Successfully creates new draft when id is null")
    void shouldCreateNewDraftSuccessfully() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("Spring Modulith Deep Dive")
            .timezone("Asia/Ho_Chi_Minh")
            .currency("vnd")
            .price(new BigDecimal("100000.00"))
            .capacity(50)
            .location("Hall A")
            .coverImageUrl("https://example.com/banner.jpg")
            .build();

        when(
            slugGeneratorPort.generateSlug("Spring Modulith Deep Dive")
        ).thenReturn("spring-modulith-deep-dive-abc123");
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.organizationId()).isEqualTo(orgId);
        assertThat(result.title()).isEqualTo("Spring Modulith Deep Dive");
        assertThat(result.slug()).isEqualTo("spring-modulith-deep-dive-abc123");
        assertThat(result.status()).isEqualTo(WorkshopStatus.DRAFT);
        assertThat(result.version()).isEqualTo(0);
        assertThat(result.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.price()).isEqualByComparingTo(
            new BigDecimal("100000.00")
        );
        assertThat(result.capacity()).isEqualTo(50);
        assertThat(result.location()).isEqualTo("Hall A");
        assertThat(result.coverImageUrl()).isEqualTo(
            "https://example.com/banner.jpg"
        );

        verify(workshopRepository).save(any(Workshop.class));
    }

    @Test
    @DisplayName("Throws ValidationException when title is null or blank")
    void shouldThrowWhenTitleIsBlank() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("   ")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Title must not be blank");
    }

    @Test
    @DisplayName("Throws ValidationException when title exceeds 255 characters")
    void shouldThrowWhenTitleTooLong() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("A".repeat(256))
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Title must not exceed 255 characters");
    }

    @Test
    @DisplayName(
        "Throws BadRequestException when organizationId or callerId is missing"
    )
    void shouldThrowWhenAuthContextMissing() {
        SaveDraftCommand commandWithoutOrg = SaveDraftCommand.builder()
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .build();

        assertThatThrownBy(() ->
            useCase.execute(commandWithoutOrg)
        ).isInstanceOf(BadRequestException.class);

        SaveDraftCommand commandWithoutCaller = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .title("Valid Title")
            .build();

        assertThatThrownBy(() ->
            useCase.execute(commandWithoutCaller)
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName(
        "Throws ValidationException when capacity is invalid (0, negative, or > 1,000,000)"
    )
    void shouldThrowWhenCapacityInvalid() {
        SaveDraftCommand commandZero = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .capacity(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(commandZero))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Capacity must be at least 1");

        SaveDraftCommand commandOverMax = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .capacity(1_000_001)
            .build();

        assertThatThrownBy(() -> useCase.execute(commandOverMax))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Capacity must not exceed 1,000,000");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when price is negative, exceeds max, or has > 2 decimal places"
    )
    void shouldThrowWhenPriceInvalid() {
        SaveDraftCommand commandNegative = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .price(new BigDecimal("-0.01"))
            .build();

        assertThatThrownBy(() -> useCase.execute(commandNegative))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Price must be non-negative");

        SaveDraftCommand commandTooManyDecimals = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .price(new BigDecimal("10.999"))
            .build();

        assertThatThrownBy(() -> useCase.execute(commandTooManyDecimals))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("at most 2 decimal places");

        SaveDraftCommand commandExceedsMax = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .price(new BigDecimal("100000000.00"))
            .build();

        assertThatThrownBy(() -> useCase.execute(commandExceedsMax))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Price must not exceed 99,999,999.99");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when timezone is not a valid IANA identifier"
    )
    void shouldThrowWhenTimezoneInvalid() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .timezone("Invalid/Timezone")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid IANA timezone identifier");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when currency is not a valid ISO 4217 code"
    )
    void shouldThrowWhenCurrencyInvalid() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .currency("ZZZ")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid ISO 4217 currency code");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when coverImageUrl does not start with http:// or https://"
    )
    void shouldThrowWhenCoverImageUrlInvalid() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .coverImageUrl("ftp://example.com/image.png")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("valid HTTP or HTTPS URL");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when location exceeds 255 characters"
    )
    void shouldThrowWhenLocationTooLong() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .location("L".repeat(256))
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Location must not exceed 255 characters");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when endsAt is not strictly after startsAt"
    )
    void shouldThrowWhenEndsAtBeforeOrEqualStartsAt() {
        Instant now = Instant.parse("2026-10-01T10:00:00Z");

        SaveDraftCommand commandEqual = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .startsAt(now)
            .endsAt(now)
            .build();

        assertThatThrownBy(() -> useCase.execute(commandEqual))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("endsAt must be strictly after startsAt");

        SaveDraftCommand commandBefore = SaveDraftCommand.builder()
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .startsAt(now)
            .endsAt(now.minusSeconds(3600))
            .build();

        assertThatThrownBy(() -> useCase.execute(commandBefore))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("endsAt must be strictly after startsAt");
    }

    @Test
    @DisplayName(
        "Successfully updates draft and regenerates slug when title changes"
    )
    void shouldUpdateDraftSuccessfullyAndRegenerateSlugWhenTitleChanges() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();

        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Old Title")
            .slug("old-title-123")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(callerId)
            .updatedBy(callerId)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Updated Title")
            .description("New Description")
            .location("Room 101")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(slugGeneratorPort.generateSlug("Updated Title")).thenReturn(
            "updated-title-456"
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> {
                Workshop w = invocation.getArgument(0);
                return w
                    .toBuilder()
                    .version(w.version() + 1)
                    .build();
            }
        );

        Workshop result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(workshopId);
        assertThat(result.title()).isEqualTo("Updated Title");
        assertThat(result.slug()).isEqualTo("updated-title-456");
        assertThat(result.description()).isEqualTo("New Description");
        assertThat(result.location()).isEqualTo("Room 101");
        assertThat(result.version()).isEqualTo(1);
        verify(slugGeneratorPort).generateSlug("Updated Title");
        verify(workshopRepository).save(any(Workshop.class));
    }

    @Test
    @DisplayName(
        "Successfully updates draft and retains existing slug when title does not change"
    )
    void shouldUpdateDraftSuccessfullyAndRetainSlugWhenTitleDoesNotChange() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();

        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Same Title")
            .slug("same-title-123")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(callerId)
            .updatedBy(callerId)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Same Title")
            .location("Room 102")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> {
                Workshop w = invocation.getArgument(0);
                return w
                    .toBuilder()
                    .version(w.version() + 1)
                    .build();
            }
        );

        Workshop result = useCase.execute(command);

        assertThat(result.slug()).isEqualTo("same-title-123");
        assertThat(result.location()).isEqualTo("Room 102");
        assertThat(result.version()).isEqualTo(1);
        verify(slugGeneratorPort, org.mockito.Mockito.never()).generateSlug(
            any()
        );
    }

    @Test
    @DisplayName(
        "Throws ValidationException when id is provided but version is null"
    )
    void shouldThrowValidationExceptionWhenVersionMissingOnUpdate() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(UUID.randomUUID())
            .version(null)
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Valid Title")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Version is required");
    }

    @Test
    @DisplayName(
        "Throws ConflictException with VERSION_CONFLICT when version mismatch occurs"
    )
    void shouldThrowConflictExceptionWhenVersionMismatch() {
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Title")
            .status(WorkshopStatus.DRAFT)
            .version(6)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(5)
            .organizationId(orgId)
            .callerId(UUID.randomUUID())
            .title("Title")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> {
                ConflictException ce = (ConflictException) e;
                assertThat(ce.getType()).isEqualTo("urn:unihub:error:conflict");
                assertThat(ce.getMessage()).contains(
                    "Workshop version conflict"
                );
            });
    }

    @Test
    @DisplayName(
        "Throws ConflictException when workshop is not in DRAFT status"
    )
    void shouldThrowConflictExceptionWhenWorkshopNotDraft() {
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        Workshop published = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Title")
            .status(WorkshopStatus.PUBLISHED)
            .version(1)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(1)
            .organizationId(orgId)
            .callerId(UUID.randomUUID())
            .title("Title")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(published)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> {
                ConflictException ce = (ConflictException) e;
                assertThat(ce.getType()).isEqualTo("urn:unihub:error:conflict");
                assertThat(ce.getMessage()).contains(
                    "Only workshops in DRAFT status"
                );
            });
    }

    @Test
    @DisplayName(
        "Throws ForbiddenException when workshop belongs to a different organization"
    )
    void shouldThrowForbiddenExceptionWhenWorkshopBelongsToDifferentOrg() {
        UUID workshopId = UUID.randomUUID();
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgB)
            .title("Title")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgA)
            .callerId(UUID.randomUUID())
            .title("Title")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            ForbiddenException.class
        );
    }

    @Test
    @DisplayName("Throws NotFoundException when workshop does not exist")
    void shouldThrowNotFoundExceptionWhenWorkshopNotFound() {
        UUID workshopId = UUID.randomUUID();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(UUID.randomUUID())
            .callerId(UUID.randomUUID())
            .title("Title")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            NotFoundException.class
        );
    }
}
