package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    public ResetPasswordUseCaseImpl(
            UserRepository userRepository,
            UserVerificationTokenRepository userVerificationTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            OtpService otpService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userVerificationTokenRepository = userVerificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(this::invalidOtp);

        if (!user.isActive()) {
            throw invalidOtp();
        }

        UserVerificationToken token = userVerificationTokenRepository.findLatestByUserIdAndType(
                user.id(), UserVerificationToken.TYPE_PASSWORD_RESET_OTP)
                .orElseThrow(this::invalidOtp);

        if (token.isConsumed() || token.isExpired()) {
            throw invalidOtp();
        }

        if (!otpService.verifyOtp(otp, token.tokenHash())) {
            throw invalidOtp();
        }

        userVerificationTokenRepository.markConsumed(token.id());

        String newPasswordHash = passwordEncoder.encode(newPassword);
        userRepository.save(user.updatePassword(newPasswordHash));

        refreshTokenRepository.revokeAllActiveTokensForUser(user.id(), Instant.now());
    }

    private ValidationException invalidOtp() {
        return new ValidationException("otp", "Invalid or expired verification code.");
    }
}
