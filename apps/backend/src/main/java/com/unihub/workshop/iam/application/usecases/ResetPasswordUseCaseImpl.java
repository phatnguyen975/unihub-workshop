package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry meterRegistry;

    private volatile String dummyTokenHash;

    @PostConstruct
    void init() {
        if (dummyTokenHash == null) {
            dummyTokenHash = otpService.hashOtp("000000");
        }
    }

    private String getDummyTokenHash() {
        if (dummyTokenHash == null) {
            dummyTokenHash = otpService.hashOtp("000000");
        }
        return dummyTokenHash;
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            otpService.verifyOtp(otp, getDummyTokenHash());
            recordFailure("user_not_found");
            throw invalidOtp();
        }

        if (!user.isActive()) {
            otpService.verifyOtp(otp, getDummyTokenHash());
            recordFailure("inactive_user");
            throw invalidOtp();
        }

        UserVerificationToken token = userVerificationTokenRepository
            .findLatestByUserIdAndType(
                user.id(),
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
            .orElse(null);

        if (token == null) {
            otpService.verifyOtp(otp, getDummyTokenHash());
            recordFailure("token_not_found");
            throw invalidOtp();
        }

        if (token.isConsumed() || token.isExpired()) {
            otpService.verifyOtp(otp, getDummyTokenHash());
            recordFailure(
                token.isConsumed() ? "already_consumed" : "expired_otp"
            );
            throw invalidOtp();
        }

        if (!otpService.verifyOtp(otp, token.tokenHash())) {
            recordFailure("invalid_otp");
            throw invalidOtp();
        }

        boolean consumed = userVerificationTokenRepository.markConsumed(
            token.id()
        );
        if (!consumed) {
            recordFailure("already_consumed");
            throw invalidOtp();
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        userRepository.save(user.updatePassword(newPasswordHash));

        refreshTokenRepository.revokeAllActiveTokensForUser(
            user.id(),
            Instant.now()
        );

        meterRegistry
            .counter("auth.password.reset.attempts", "outcome", "success")
            .increment();
    }

    private void recordFailure(String reason) {
        meterRegistry
            .counter(
                "auth.password.reset.attempts",
                "outcome",
                "failure",
                "reason",
                reason
            )
            .increment();
    }

    private ValidationException invalidOtp() {
        return new ValidationException(
            "otp",
            "Invalid or expired verification code."
        );
    }
}
