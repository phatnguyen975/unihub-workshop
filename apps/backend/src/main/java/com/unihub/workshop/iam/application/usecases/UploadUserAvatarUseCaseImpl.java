package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.UploadUserAvatarUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.storage.MediaType;
import com.unihub.workshop.shared.storage.MediaValidationUtils;
import com.unihub.workshop.shared.storage.StoredMedia;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadUserAvatarUseCaseImpl implements UploadUserAvatarUseCase {

    private final UserRepository userRepository;
    private final MediaStoragePort mediaStoragePort;

    @Override
    @Transactional
    public User uploadAvatar(UUID userId, MultipartFile file) {
        if (userId == null) {
            throw new UnauthorizedException("User not found");
        }

        String extension = MediaValidationUtils.validateImage(file);

        User user = userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        String oldAvatarUrl = user.avatarUrl();

        StoredMedia storedMedia;
        try (InputStream inputStream = file.getInputStream()) {
            storedMedia = mediaStoragePort.upload(
                inputStream,
                file.getSize(),
                file.getContentType(),
                MediaType.USER_AVATAR,
                extension
            );
        } catch (IOException e) {
            throw new BadRequestException(
                "Failed to read uploaded file: " + e.getMessage()
            );
        }

        User updated = user.updateAvatar(storedMedia.publicUrl());
        User saved = userRepository.save(updated);

        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            try {
                mediaStoragePort.deleteByUrl(oldAvatarUrl);
            } catch (Exception ex) {
                log.warn(
                    "Failed to delete previous avatar object: {}",
                    oldAvatarUrl,
                    ex
                );
            }
        }

        return saved;
    }
}
