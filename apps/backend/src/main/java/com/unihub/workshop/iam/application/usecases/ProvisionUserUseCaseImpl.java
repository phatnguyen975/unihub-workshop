package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ProvisionUserCommand;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.events.UserProvisionedEvent;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.seed.PasswordGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProvisionUserUseCaseImpl implements ProvisionUserUseCase {

    private final UserRepository userRepository;
    private final PasswordGenerator passwordGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public User execute(ProvisionUserCommand command) {
        if (command == null) {
            throw new BadRequestException("Command must not be null");
        }
        if (command.organizationId() == null) {
            throw new BadRequestException("organizationId must not be null");
        }
        if (command.callerId() == null) {
            throw new BadRequestException("callerId must not be null");
        }
        if (command.role() == UserRole.STUDENT) {
            throw new ValidationException(
                "urn:unihub:error:student-provisioning-forbidden",
                "The STUDENT role cannot be provisioned by an organization. Students must self-register.",
                List.of("role: STUDENT role cannot be provisioned")
            );
        }

        String email =
            command.email() != null
                ? command.email().trim().toLowerCase()
                : null;
        if (email == null || email.isBlank()) {
            throw new ValidationException("email", "Email must not be blank");
        }

        String fullName =
            command.fullName() != null ? command.fullName().trim() : null;
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException(
                "fullName",
                "Full name must not be blank"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(
                "An account with this email already exists."
            );
        }

        String phoneNumber =
            command.phoneNumber() != null ? command.phoneNumber().trim() : null;
        if (phoneNumber != null && phoneNumber.isEmpty()) {
            phoneNumber = null;
        }

        PasswordGenerator.GeneratedPassword generatedPassword =
            passwordGenerator.generate();

        User user = User.provisionMember(
            null,
            command.organizationId(),
            command.role(),
            email,
            phoneNumber,
            generatedPassword.hash(),
            fullName,
            command.callerId()
        );

        User saved = userRepository.save(user);

        eventPublisher.publishEvent(
            new UserProvisionedEvent(
                saved.id(),
                saved.organizationId(),
                null,
                saved.email(),
                saved.fullName(),
                saved.role().name(),
                generatedPassword.plaintext()
            )
        );

        return saved;
    }
}
