package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.out.PasswordResetTokenRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.InvalidOtpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateAccountUseCaseImpl implements ActivateAccountUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpService otpService;

    public ActivateAccountUseCaseImpl(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.otpService = otpService;
    }

    @Override
    @Transactional
    public void activate(String email, String otp) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidOtpException::new);

        if (user.isActive()) {
            throw new ConflictException("Account is already active.");
        }

        PasswordResetToken token = passwordResetTokenRepository.findLatestByUserId(user.id())
                .orElseThrow(InvalidOtpException::new);

        if (token.isConsumed() || token.isExpired()) {
            throw new InvalidOtpException();
        }

        if (!otpService.verifyOtp(otp, token.tokenHash())) {
            throw new InvalidOtpException();
        }

        passwordResetTokenRepository.markConsumed(token.id());
        userRepository.activate(user.id());
    }
}
