package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;
import java.util.UUID;

public interface UpdateUserProfileUseCase {
    User updateProfile(UUID userId, UpdateUserProfileCommand command);
}
