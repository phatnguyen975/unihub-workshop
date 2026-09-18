package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ResetPasswordUseCase;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private final SessionTrackerPort sessionTrackerPort;
    private final JtiBlacklistPort jtiBlacklistPort;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";

        User user = userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() ->
                new NotFoundException("No account found for this email.")
            );

        if (!user.isActive()) {
            throw invalidOtp();
        }

        UserVerificationToken token = userVerificationTokenRepository
            .findLatestByUserIdAndType(
                user.id(),
                UserVerificationToken.TYPE_PASSWORD_RESET_OTP
            )
            .orElseThrow(this::invalidOtp);

        if (token.isConsumed() || token.isExpired()) {
            throw invalidOtp();
        }

        if (!otpService.verifyOtp(otp, token.tokenHash())) {
            throw invalidOtp();
        }

        boolean consumed = userVerificationTokenRepository.markConsumed(
            token.id()
        );
        if (!consumed) {
            throw invalidOtp();
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        userRepository.save(user.updatePassword(newPasswordHash));

        Instant now = Instant.now();
        List<RefreshToken> activeSessions =
            refreshTokenRepository.findAllActiveSessionsForUser(user.id(), now);

        for (RefreshToken session : activeSessions) {
            Optional<String> remoteJti = sessionTrackerPort.getActiveJti(
                session.id()
            );
            if (remoteJti.isPresent()) {
                Duration remainingTtl = sessionTrackerPort.getRemainingTtl(
                    session.id()
                );
                jtiBlacklistPort.blacklistJti(remoteJti.get(), remainingTtl);
            }
            sessionTrackerPort.removeSession(session.id());
        }

        refreshTokenRepository.revokeAllActiveTokensForUser(user.id(), now);
    }

    private ValidationException invalidOtp() {
        return new ValidationException(
            "otp",
            "Invalid or expired verification code."
        );
    }
}
