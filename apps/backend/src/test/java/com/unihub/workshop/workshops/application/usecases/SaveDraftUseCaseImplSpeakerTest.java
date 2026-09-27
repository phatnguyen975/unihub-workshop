package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveDraftUseCaseImplSpeakerTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private SlugGeneratorPort slugGeneratorPort;

    @Mock
    private MediaStoragePort mediaStoragePort;

    @InjectMocks
    private SaveDraftUseCaseImpl useCase;

    private final UUID orgId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
    private final Instant endsAt = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    @DisplayName("Successfully creates new draft with speakers when valid")
    void shouldCreateNewDraftWithSpeakersSuccessfully() {
        List<SaveDraftCommand.SpeakerCommand> speakers = List.of(
            SaveDraftCommand.SpeakerCommand.builder()
                .name("Alice Wonderland")
                .email("Alice@Example.com")
                .bio("Senior AI Engineer")
                .avatarUrl("https://example.com/alice.jpg")
                .linkedinUrl("https://linkedin.com/in/alice")
                .build(),
            SaveDraftCommand.SpeakerCommand.builder()
                .name("Bob Builder")
                .email("bob@example.com")
                .build()
        );

        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(speakers)
            .build();

        when(slugGeneratorPort.generateSlug("AI Workshop")).thenReturn(
            "ai-workshop-abc123"
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                List.of("alice@example.com", "bob@example.com"),
                startsAt,
                endsAt,
                null
            )
        ).thenReturn(List.of());
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.speakers()).hasSize(2);
        assertThat(result.speakers().get(0).name()).isEqualTo(
            "Alice Wonderland"
        );
        assertThat(result.speakers().get(0).email()).isEqualTo(
            "alice@example.com"
        );
        assertThat(result.speakers().get(0).sortOrder()).isEqualTo(0);
        assertThat(result.speakers().get(1).email()).isEqualTo(
            "bob@example.com"
        );
        assertThat(result.speakers().get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fails when more than 10 speakers are provided")
    void shouldFailWhenMoreThan10Speakers() {
        List<SaveDraftCommand.SpeakerCommand> speakers = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            speakers.add(
                SaveDraftCommand.SpeakerCommand.builder()
                    .name("Speaker " + i)
                    .email("speaker" + i + "@example.com")
                    .build()
            );
        }

        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("Big Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(speakers)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("more than 10 speakers");
    }

    @Test
    @DisplayName("Fails when speakers provided but startsAt is null")
    void shouldFailWhenSpeakersProvidedWithoutStartsAt() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice")
                        .email("alice@example.com")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("startsAt and endsAt are mandatory");
    }

    @Test
    @DisplayName("Fails when speakers provided but endsAt is null")
    void shouldFailWhenSpeakersProvidedWithoutEndsAt() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice")
                        .email("alice@example.com")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("startsAt and endsAt are mandatory");
    }

    @Test
    @DisplayName("Fails when speaker name is blank")
    void shouldFailWhenSpeakerNameIsBlank() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("   ")
                        .email("alice@example.com")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("name is mandatory");
    }

    @Test
    @DisplayName("Fails when speaker email is blank")
    void shouldFailWhenSpeakerEmailIsBlank() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice")
                        .email("  ")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("email is mandatory");
    }

    @Test
    @DisplayName("Fails when speaker email is not a valid email format")
    void shouldFailWhenSpeakerEmailIsInvalid() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice")
                        .email("not-an-email")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("valid email address");
    }

    @Test
    @DisplayName(
        "Fails when duplicate speaker emails in same request (case-insensitive)"
    )
    void shouldFailWhenDuplicateSpeakerEmails() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice First")
                        .email("alice@example.com")
                        .build(),
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Alice Second")
                        .email("ALICE@EXAMPLE.COM")
                        .build()
                )
            )
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Duplicate speaker email");
    }

    @Test
    @DisplayName("Fails when speaker schedule conflict is detected")
    void shouldFailWhenScheduleConflictDetected() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("AI Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Bob")
                        .email("bob@example.com")
                        .build()
                )
            )
            .build();

        when(
            workshopRepository.findConflictingSpeakerEmails(
                List.of("bob@example.com"),
                startsAt,
                endsAt,
                null
            )
        ).thenReturn(List.of("bob@example.com"));

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Speaker schedule conflict detected");
    }

    @Test
    @DisplayName(
        "Updating draft replaces speakers when speakers array is provided"
    )
    void shouldReplaceSpeakersOnUpdateWhenSpeakersProvided() {
        UUID workshopId = UUID.randomUUID();
        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Original Title")
            .slug("original-title")
            .timezone("Asia/Ho_Chi_Minh")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .speakers(
                List.of(
                    Speaker.builder()
                        .name("Old Speaker 1")
                        .email("old1@example.com")
                        .build(),
                    Speaker.builder()
                        .name("Old Speaker 2")
                        .email("old2@example.com")
                        .build()
                )
            )
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Original Title")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("New Speaker")
                        .email("new@example.com")
                        .build()
                )
            )
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                List.of("new@example.com"),
                startsAt,
                endsAt,
                workshopId
            )
        ).thenReturn(List.of());
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.speakers()).hasSize(1);
        assertThat(result.speakers().get(0).name()).isEqualTo("New Speaker");
        assertThat(result.speakers().get(0).email()).isEqualTo(
            "new@example.com"
        );
    }

    @Test
    @DisplayName("Updating draft retains speakers when speakers is null")
    void shouldRetainSpeakersOnUpdateWhenSpeakersIsNull() {
        UUID workshopId = UUID.randomUUID();
        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Original Title")
            .slug("original-title")
            .timezone("Asia/Ho_Chi_Minh")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .speakers(
                List.of(
                    Speaker.builder()
                        .name("Existing Speaker")
                        .email("existing@example.com")
                        .build()
                )
            )
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Original Title")
            .speakers(null)
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.speakers()).hasSize(1);
        assertThat(result.speakers().get(0).name()).isEqualTo(
            "Existing Speaker"
        );
    }

    @Test
    @DisplayName("Updating draft clears speakers when speakers is empty list")
    void shouldClearSpeakersOnUpdateWhenSpeakersIsEmptyList() {
        UUID workshopId = UUID.randomUUID();
        Workshop existing = Workshop.builder()
            .id(workshopId)
            .organizationId(orgId)
            .title("Original Title")
            .slug("original-title")
            .timezone("Asia/Ho_Chi_Minh")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .speakers(
                List.of(
                    Speaker.builder()
                        .name("Existing Speaker")
                        .email("existing@example.com")
                        .build()
                )
            )
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(workshopId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Original Title")
            .speakers(List.of())
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.speakers()).isEmpty();
    }
}
