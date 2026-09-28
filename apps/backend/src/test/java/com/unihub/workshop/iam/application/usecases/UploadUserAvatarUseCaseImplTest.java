package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.storage.MediaAssetType;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.storage.StoredMedia;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class UploadUserAvatarUseCaseImplTest {

    private UserRepository userRepository;
    private MediaStoragePort mediaStoragePort;
    private UploadUserAvatarUseCaseImpl useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID STUDENT_RECORD_ID = UUID.randomUUID();

    // Valid PNG magic bytes header
    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
    };

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mediaStoragePort = mock(MediaStoragePort.class);
        useCase = new UploadUserAvatarUseCaseImpl(
            userRepository,
            mediaStoragePort
        );
    }

    private User createSampleUser(String avatarUrl, boolean isActive) {
        return new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "student@university.edu",
            "+84912345678",
            "hash",
            "Alice Smith",
            avatarUrl,
            isActive,
            false,
            Instant.now(),
            STUDENT_RECORD_ID,
            Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(3600),
            null,
            null
        );
    }

    @Test
    @DisplayName(
        "Successfully uploads avatar, updates database, and purges previous avatar from MinIO"
    )
    void shouldUploadAvatarSuccessfully_andPurgeOldAvatar() {
        String oldAvatarUrl =
            "http://localhost:9000/unihub-media/users/avatars/old-123.jpg";
        String newAvatarUrl =
            "http://localhost:9000/unihub-media/users/avatars/new-456.png";

        User existing = createSampleUser(oldAvatarUrl, true);
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        StoredMedia storedMedia = new StoredMedia(
            "users/avatars/new-456.png",
            newAvatarUrl,
            MediaAssetType.USER_AVATAR,
            (long) VALID_PNG_BYTES.length,
            "image/png"
        );
        when(
            mediaStoragePort.upload(
                any(InputStream.class),
                eq((long) VALID_PNG_BYTES.length),
                eq("image/png"),
                eq(MediaAssetType.USER_AVATAR),
                eq(".png")
            )
        ).thenReturn(storedMedia);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        User result = useCase.uploadAvatar(USER_ID, file);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.avatarUrl()).isEqualTo(newAvatarUrl);
        assertThat(saved.updatedBy()).isEqualTo(USER_ID);
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(result.avatarUrl()).isEqualTo(newAvatarUrl);

        verify(mediaStoragePort).deleteByUrl(oldAvatarUrl);
    }

    @Test
    @DisplayName(
        "Successfully uploads avatar when user had no previous avatar (no purge call)"
    )
    void shouldUploadAvatarSuccessfully_whenNoOldAvatar() {
        String newAvatarUrl =
            "http://localhost:9000/unihub-media/users/avatars/new-456.png";

        User existing = createSampleUser(null, true);
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        StoredMedia storedMedia = new StoredMedia(
            "users/avatars/new-456.png",
            newAvatarUrl,
            MediaAssetType.USER_AVATAR,
            (long) VALID_PNG_BYTES.length,
            "image/png"
        );
        when(
            mediaStoragePort.upload(
                any(InputStream.class),
                eq((long) VALID_PNG_BYTES.length),
                eq("image/png"),
                eq(MediaAssetType.USER_AVATAR),
                eq(".png")
            )
        ).thenReturn(storedMedia);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        User result = useCase.uploadAvatar(USER_ID, file);

        assertThat(result.avatarUrl()).isEqualTo(newAvatarUrl);
        verify(mediaStoragePort, never()).deleteByUrl(any());
    }

    @Test
    @DisplayName("Throws UnauthorizedException when userId is null")
    void shouldThrowUnauthorizedException_whenUserIdIsNull() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        assertThatThrownBy(() -> useCase.uploadAvatar(null, file))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found");
    }

    @Test
    @DisplayName("Throws UnauthorizedException when user does not exist")
    void shouldThrowUnauthorizedException_whenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        assertThatThrownBy(() -> useCase.uploadAvatar(USER_ID, file))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found");
    }

    @Test
    @DisplayName(
        "Throws UnauthorizedException when user account is deactivated"
    )
    void shouldThrowUnauthorizedException_whenAccountDeactivated() {
        User deactivated = createSampleUser(null, false);
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(deactivated)
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            VALID_PNG_BYTES
        );

        assertThatThrownBy(() -> useCase.uploadAvatar(USER_ID, file))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Account is deactivated");
    }

    @Test
    @DisplayName("Throws ValidationException when file is empty")
    void shouldThrowValidationException_whenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.png",
            "image/png",
            new byte[0]
        );

        assertThatThrownBy(() -> useCase.uploadAvatar(USER_ID, file))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("File is empty or missing.");

        verify(mediaStoragePort, never()).upload(
            any(),
            anyLong(),
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("Throws ValidationException when MIME type is unsupported")
    void shouldThrowValidationException_whenMimeTypeUnsupported() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            new byte[] { 0x25, 0x50, 0x44, 0x46 }
        );

        assertThatThrownBy(() -> useCase.uploadAvatar(USER_ID, file))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Unsupported MIME type");

        verify(mediaStoragePort, never()).upload(
            any(),
            anyLong(),
            any(),
            any(),
            any()
        );
    }
}
