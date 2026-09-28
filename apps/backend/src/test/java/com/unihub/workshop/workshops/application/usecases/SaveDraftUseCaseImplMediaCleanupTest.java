package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
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
class SaveDraftUseCaseImplMediaCleanupTest {

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
    private final UUID draftId = UUID.randomUUID();
    private final Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
    private final Instant endsAt = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    @DisplayName(
        "Updating draft with new cover image deletes old cover image from storage"
    )
    void shouldDeleteOldCoverImageWhenCoverImageUrlIsUpdatedWithNewValue() {
        String oldCover =
            "http://localhost:9000/unihub-media/workshops/covers/old-banner.jpg";
        String newCover =
            "http://localhost:9000/unihub-media/workshops/covers/new-banner.jpg";

        Workshop existing = Workshop.builder()
            .id(draftId)
            .organizationId(orgId)
            .status(WorkshopStatus.DRAFT)
            .title("Clean Code Workshop")
            .slug("clean-code-workshop")
            .version(0)
            .coverImageUrl(oldCover)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(draftId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Clean Code Workshop")
            .coverImageUrl(newCover)
            .build();

        when(workshopRepository.findById(draftId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.coverImageUrl()).isEqualTo(newCover);
        verify(mediaStoragePort).deleteByUrl(oldCover);
    }

    @Test
    @DisplayName(
        "Updating draft with null cover image deletes old cover image from storage"
    )
    void shouldDeleteOldCoverImageWhenCoverImageUrlIsUpdatedToNull() {
        String oldCover =
            "http://localhost:9000/unihub-media/workshops/covers/old-banner.jpg";

        Workshop existing = Workshop.builder()
            .id(draftId)
            .organizationId(orgId)
            .status(WorkshopStatus.DRAFT)
            .title("Clean Code Workshop")
            .slug("clean-code-workshop")
            .version(0)
            .coverImageUrl(oldCover)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(draftId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Clean Code Workshop")
            .coverImageUrl(null)
            .build();

        when(workshopRepository.findById(draftId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.coverImageUrl()).isNull();
        verify(mediaStoragePort).deleteByUrl(oldCover);
    }

    @Test
    @DisplayName(
        "Updating draft with same cover image does not delete cover image"
    )
    void shouldNotDeleteOldCoverImageWhenCoverImageUrlIsUnchanged() {
        String sameCover =
            "http://localhost:9000/unihub-media/workshops/covers/same-banner.jpg";

        Workshop existing = Workshop.builder()
            .id(draftId)
            .organizationId(orgId)
            .status(WorkshopStatus.DRAFT)
            .title("Clean Code Workshop")
            .slug("clean-code-workshop")
            .version(0)
            .coverImageUrl(sameCover)
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(draftId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Clean Code Workshop")
            .coverImageUrl(sameCover)
            .build();

        when(workshopRepository.findById(draftId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        useCase.execute(command);

        verify(mediaStoragePort, never()).deleteByUrl(any());
    }

    @Test
    @DisplayName("Replacing speaker list deletes avatars of omitted speakers")
    void shouldDeleteOmittedSpeakerAvatarsWhenSpeakersListIsReplaced() {
        String avatar1 =
            "http://localhost:9000/unihub-media/speakers/avatars/speaker1.jpg";
        String avatar2 =
            "http://localhost:9000/unihub-media/speakers/avatars/speaker2.jpg";

        Speaker s1 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Speaker One")
            .email("s1@example.com")
            .avatarUrl(avatar1)
            .build();

        Speaker s2 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Speaker Two")
            .email("s2@example.com")
            .avatarUrl(avatar2)
            .build();

        Workshop existing = Workshop.builder()
            .id(draftId)
            .organizationId(orgId)
            .status(WorkshopStatus.DRAFT)
            .title("Clean Code Workshop")
            .slug("clean-code-workshop")
            .version(0)
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(List.of(s1, s2))
            .build();

        // New speakers only include Speaker 1
        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(draftId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Clean Code Workshop")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .speakers(
                List.of(
                    SaveDraftCommand.SpeakerCommand.builder()
                        .name("Speaker One")
                        .email("s1@example.com")
                        .avatarUrl(avatar1)
                        .build()
                )
            )
            .build();

        when(workshopRepository.findById(draftId)).thenReturn(
            Optional.of(existing)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(List.of());
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        useCase.execute(command);

        // avatar2 must be deleted because s2 was omitted
        verify(mediaStoragePort).deleteByUrl(avatar2);
        // avatar1 must NOT be deleted because s1 was retained
        verify(mediaStoragePort, never()).deleteByUrl(avatar1);
    }

    @Test
    @DisplayName(
        "Omitting speakers on update does not delete any speaker avatars"
    )
    void shouldNotDeleteSpeakerAvatarsWhenSpeakersFieldIsOmittedOnUpdate() {
        String avatar1 =
            "http://localhost:9000/unihub-media/speakers/avatars/speaker1.jpg";

        Speaker s1 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Speaker One")
            .email("s1@example.com")
            .avatarUrl(avatar1)
            .build();

        Workshop existing = Workshop.builder()
            .id(draftId)
            .organizationId(orgId)
            .status(WorkshopStatus.DRAFT)
            .title("Clean Code Workshop")
            .slug("clean-code-workshop")
            .version(0)
            .speakers(List.of(s1))
            .build();

        SaveDraftCommand command = SaveDraftCommand.builder()
            .id(draftId)
            .version(0)
            .organizationId(orgId)
            .callerId(callerId)
            .title("Clean Code Workshop")
            .speakers(null) // omitted
            .build();

        when(workshopRepository.findById(draftId)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        useCase.execute(command);

        verify(mediaStoragePort, never()).deleteByUrl(any());
    }

    @Test
    @DisplayName("Creating new draft never triggers media deletion")
    void shouldNotCallDeleteWhenCreatingNewDraft() {
        SaveDraftCommand command = SaveDraftCommand.builder()
            .organizationId(orgId)
            .callerId(callerId)
            .title("New Workshop")
            .coverImageUrl(
                "http://localhost:9000/unihub-media/workshops/covers/new.jpg"
            )
            .build();

        when(slugGeneratorPort.generateSlug("New Workshop")).thenReturn(
            "new-workshop"
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        useCase.execute(command);

        verify(mediaStoragePort, never()).deleteByUrl(any());
    }
}
