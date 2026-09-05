package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.events.OtpRequestedEvent;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    public ResendOtpUseCaseImpl(
            UserRepository userRepository,
            UserVerificationTokenRepository userVerificationTokenRepository,
            OtpService otpService,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userVerificationTokenRepository = userVerificationTokenRepository;
        this.otpService = otpService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new NotFoundException("No account found for this email."));

        if (user.isActive()) {
            // TODO: Send PASSWORD_RESET_OTP for active accounts.
            // Left blank for now per Feature 1.1 scope; will be implemented in Feature 1.4.
            return;
        }

        userVerificationTokenRepository.invalidateActiveTokens(
                user.id(), UserVerificationToken.TYPE_ACTIVATION_OTP);

        String otp = otpService.generateOtp();
        String tokenHash = otpService.hashOtp(otp);

        UserVerificationToken token = UserVerificationToken.createActivationOtp(
                user.id(),
                tokenHash,
                otpService.calculateExpiryInstant());
        userVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(new OtpRequestedEvent(
                user.id(),
                user.email(),
                otp,
                OtpRequestedEvent.PURPOSE_ACTIVATION));
    }
}
