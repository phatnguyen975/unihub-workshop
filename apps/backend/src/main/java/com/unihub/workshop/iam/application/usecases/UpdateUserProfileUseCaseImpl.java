package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileUseCaseImpl implements UpdateUserProfileUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User updateProfile(UUID userId, UpdateUserProfileCommand command) {
        if (userId == null) {
            throw new UnauthorizedException("User not found");
        }
        if (
            command == null ||
            (!command.hasFullName() &&
                !command.hasPhoneNumber() &&
                !command.hasAvatarUrl())
        ) {
            throw new ValidationException(
                "At least one field must be provided for update."
            );
        }

        User existing = userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        String newFullName = command.hasFullName()
            ? command.fullName()
            : existing.fullName();
        String newPhoneNumber = command.hasPhoneNumber()
            ? command.phoneNumber()
            : existing.phoneNumber();
        String newAvatarUrl = command.hasAvatarUrl()
            ? command.avatarUrl()
            : existing.avatarUrl();

        User updated = existing.updateProfile(
            newFullName,
            newPhoneNumber,
            newAvatarUrl
        );
        return userRepository.save(updated);
    }
}
