package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.events.OtpRequestedEvent;
import com.unihub.workshop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final OtpService otpService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void resendOtp(String email) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";
        User user = userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() ->
                new NotFoundException("No account found for this email.")
            );

        if (user.isActive()) {
            userVerificationTokenRepository.invalidateActiveTokens(
                user.id(),
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            );

            String otp = otpService.generateOtp();
            String tokenHash = otpService.hashOtp(otp);

            UserVerificationToken token =
                UserVerificationToken.createPasswordResetOtp(
                    user.id(),
                    tokenHash,
                    otpService.calculateExpiryInstant()
                );
            userVerificationTokenRepository.save(token);

            eventPublisher.publishEvent(
                new OtpRequestedEvent(
                    user.id(),
                    user.email(),
                    otp,
                    OtpRequestedEvent.PURPOSE_PASSWORD_RESET
                )
            );
            return;
        }

        userVerificationTokenRepository.invalidateActiveTokens(
            user.id(),
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );

        String otp = otpService.generateOtp();
        String tokenHash = otpService.hashOtp(otp);

        UserVerificationToken token = UserVerificationToken.createActivationOtp(
            user.id(),
            tokenHash,
            otpService.calculateExpiryInstant()
        );
        userVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(
            new OtpRequestedEvent(
                user.id(),
                user.email(),
                otp,
                OtpRequestedEvent.PURPOSE_ACTIVATION
            )
        );
    }
}
