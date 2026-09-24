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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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
            issueOtp(
                user,
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP,
                OtpRequestedEvent.PURPOSE_PASSWORD_RESET
            );
        } else {
            issueOtp(
                user,
                UserVerificationToken.TYPE_ACTIVATION_OTP,
                OtpRequestedEvent.PURPOSE_ACTIVATION
            );
        }
    }

    private void issueOtp(User user, String tokenType, String purpose) {
        userVerificationTokenRepository.invalidateActiveTokens(
            user.id(),
            tokenType
        );

        String otp = otpService.generateOtp();
        String tokenHash = otpService.hashOtp(otp);

        // TODO: Remove OTP logging before production deployment once notifications worker is fully active.
        log.info(
            "Resent {} OTP for user {} (email: {}): {}",
            purpose.toLowerCase(),
            user.id(),
            user.email(),
            otp
        );

        UserVerificationToken token =
            UserVerificationToken.TYPE_PASSWORD_RESET_OTP.equals(tokenType)
                ? UserVerificationToken.createPasswordResetOtp(
                      user.id(),
                      tokenHash,
                      otpService.calculateExpiryInstant()
                  )
                : UserVerificationToken.createActivationOtp(
                      user.id(),
                      tokenHash,
                      otpService.calculateExpiryInstant()
                  );

        userVerificationTokenRepository.save(token);

        eventPublisher.publishEvent(
            new OtpRequestedEvent(user.id(), user.email(), otp, purpose)
        );
    }
}
