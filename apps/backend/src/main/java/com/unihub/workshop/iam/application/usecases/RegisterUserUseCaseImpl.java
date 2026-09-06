package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.events.OtpRequestedEvent;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterUserUseCaseImpl(
            UserRepository userRepository,
            StudentDirectoryRepository studentDirectoryRepository,
            UserVerificationTokenRepository userVerificationTokenRepository,
            OtpService otpService,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.studentDirectoryRepository = studentDirectoryRepository;
        this.userVerificationTokenRepository = userVerificationTokenRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID register(String email, String password, String fullName) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists.");
        }

        Optional<StudentDirectory> directoryOpt = studentDirectoryRepository.findActiveByEmail(normalizedEmail);
        UUID studentRecordId = directoryOpt.map(StudentDirectory::id).orElse(null);
        UUID organizationId = directoryOpt.map(StudentDirectory::organizationId).orElse(null);

        String passwordHash = passwordEncoder.encode(password);
        User newUser = User.createStudent(
                UUID.randomUUID(),
                normalizedEmail,
                passwordHash,
                fullName,
                studentRecordId,
                organizationId);

        User savedUser = userRepository.save(newUser);

        String otp = otpService.generateOtp();
        String otpHash = otpService.hashOtp(otp);
        Instant expiresAt = otpService.calculateExpiryInstant();

        UserVerificationToken token = UserVerificationToken.createActivationOtp(savedUser.id(), otpHash, expiresAt);
        userVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(new OtpRequestedEvent(
                savedUser.id(),
                savedUser.email(),
                otp,
                OtpRequestedEvent.PURPOSE_ACTIVATION));

        return savedUser.id();
    }
}
