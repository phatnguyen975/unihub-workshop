package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.events.OtpRequestedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UUID register(String email, String password, String fullName) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException(
                "An account with this email already exists."
            );
        }

        Optional<StudentDirectory> directoryOpt =
            studentDirectoryRepository.findActiveByEmail(normalizedEmail);
        UUID studentRecordId = directoryOpt
            .map(StudentDirectory::id)
            .orElse(null);
        UUID organizationId = directoryOpt
            .map(StudentDirectory::organizationId)
            .orElse(null);

        String passwordHash = passwordEncoder.encode(password);
        User newUser = User.createStudent(
            UUID.randomUUID(),
            normalizedEmail,
            passwordHash,
            fullName,
            studentRecordId,
            organizationId
        );

        User savedUser = userRepository.save(newUser);

        String otp = otpService.generateOtp();
        String otpHash = otpService.hashOtp(otp);
        Instant expiresAt = otpService.calculateExpiryInstant();

        // TODO: Remove OTP logging before production deployment once notifications worker is fully active.
        log.info(
            "Generated activation OTP for user {} (email: {}): {}",
            savedUser.id(),
            savedUser.email(),
            otp
        );

        UserVerificationToken token = UserVerificationToken.createActivationOtp(
            savedUser.id(),
            otpHash,
            expiresAt
        );
        userVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(
            new OtpRequestedEvent(
                savedUser.id(),
                savedUser.email(),
                otp,
                OtpRequestedEvent.PURPOSE_ACTIVATION
            )
        );

        return savedUser.id();
    }
}
