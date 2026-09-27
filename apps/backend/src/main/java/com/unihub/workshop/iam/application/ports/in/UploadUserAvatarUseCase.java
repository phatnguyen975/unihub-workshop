package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UploadUserAvatarUseCase {
    User uploadAvatar(UUID userId, MultipartFile file);
}
