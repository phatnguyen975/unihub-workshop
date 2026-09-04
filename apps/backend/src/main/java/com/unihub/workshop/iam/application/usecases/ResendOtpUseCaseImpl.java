package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.application.ports.out.PasswordResetTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.events.OtpRequestedEvent;
import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import com.unihub.workshop.iam.domain.model.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    public ResendOtpUseCaseImpl(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            OtpService otpService,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.otpService = otpService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("No inactive account found for this email."));

        if (user.isActive()) {
            throw new IllegalStateException("No inactive account found for this email.");
        }

        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user.id());

        String otp = otpService.generateOtp();
        String tokenHash = otpService.hashOtp(otp);

        PasswordResetToken token = PasswordResetToken.create(
                user.id(),
                tokenHash,
                otpService.calculateExpiryInstant());
        passwordResetTokenRepository.save(token);

        eventPublisher.publishEvent(new OtpRequestedEvent(
                user.id(),
                user.email(),
                otp,
                OtpRequestedEvent.PURPOSE_ACTIVATION));
    }
}
