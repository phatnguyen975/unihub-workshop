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
            (!command.hasFullName() && !command.hasPhoneNumber())
        ) {
            throw new ValidationException(
                "At least one field must be provided for update."
            );
        }
        if (command.hasFullName()) {
            if (command.fullName() == null) {
                throw new ValidationException(
                    "Full name may not be set to null"
                );
            }
            if (command.fullName().trim().isEmpty()) {
                throw new ValidationException("Full name must not be blank");
            }
        }

        User existing = userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!existing.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        String newFullName = command.hasFullName()
            ? command.fullName().trim()
            : existing.fullName();
        String newPhoneNumber = command.hasPhoneNumber()
            ? command.phoneNumber() != null
                ? command.phoneNumber().trim()
                : null
            : existing.phoneNumber();

        User updated = existing.updateProfile(newFullName, newPhoneNumber);
        return userRepository.save(updated);
    }
}
