package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ChangeUserPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.RevokeAllOtherSessionsUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ChangeUserPasswordUseCaseImpl implements ChangeUserPasswordUseCase {

    private static final Pattern PASSWORD_STRENGTH_PATTERN = Pattern
            .compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).+$");

    private final UserRepository userRepository;
    private final RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry meterRegistry;

    public ChangeUserPasswordUseCaseImpl(
            UserRepository userRepository,
            RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase,
            PasswordEncoder passwordEncoder,
            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.revokeAllOtherSessionsUseCase = revokeAllOtherSessionsUseCase;
        this.passwordEncoder = passwordEncoder;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        if (userId == null) {
            throw new UnauthorizedException("User not found");
        }
        if (currentSessionId == null) {
            throw new BadRequestException("Current session ID (sid) is required for password change");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ValidationException("currentPassword", "Current password does not match");
        }

        if (currentPassword.equals(newPassword)) {
            throw new ValidationException("newPassword", "New password must be different from current password");
        }

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 255
                || !PASSWORD_STRENGTH_PATTERN.matcher(newPassword).matches()) {
            throw new ValidationException("newPassword",
                    "Password must be between 8 and 255 characters and contain at least one uppercase letter, one lowercase letter, one digit, and one special character");
        }

        // 1. Revoke all remote sessions (fail-closed: Redis operations run first)
        revokeAllOtherSessionsUseCase.revokeAllOtherSessions(userId, currentSessionId);

        // 2. Persist updated password and audit trail in Primary DB
        User updated = user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(updated);

        meterRegistry.counter("auth.password.changed", "outcome", "success").increment();
    }
}
