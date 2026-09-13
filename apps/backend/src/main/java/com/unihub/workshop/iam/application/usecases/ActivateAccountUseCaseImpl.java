package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.application.utils.OtpService;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateAccountUseCaseImpl implements ActivateAccountUseCase {

    private final UserRepository userRepository;
    private final UserVerificationTokenRepository userVerificationTokenRepository;
    private final OtpService otpService;

    public ActivateAccountUseCaseImpl(
        UserRepository userRepository,
        UserVerificationTokenRepository userVerificationTokenRepository,
        OtpService otpService
    ) {
        this.userRepository = userRepository;
        this.userVerificationTokenRepository = userVerificationTokenRepository;
        this.otpService = otpService;
    }

    @Override
    @Transactional
    public void activate(String email, String otp) {
        String normalizedEmail =
            email != null ? email.toLowerCase().trim() : "";
        User user = userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(this::invalidOtp);

        if (user.isActive()) {
            throw new ConflictException("Account is already active.");
        }

        UserVerificationToken token = userVerificationTokenRepository
            .findLatestByUserIdAndType(
                user.id(),
                UserVerificationToken.TYPE_ACTIVATION_OTP
            )
            .orElseThrow(this::invalidOtp);

        if (token.isConsumed() || token.isExpired()) {
            throw invalidOtp();
        }

        if (!otpService.verifyOtp(otp, token.tokenHash())) {
            throw invalidOtp();
        }

        if (!userVerificationTokenRepository.markConsumed(token.id())) {
            throw invalidOtp();
        }
        userRepository.activate(user.id());
    }

    private ValidationException invalidOtp() {
        return new ValidationException(
            "otp",
            "Invalid or expired activation code."
        );
    }
}
