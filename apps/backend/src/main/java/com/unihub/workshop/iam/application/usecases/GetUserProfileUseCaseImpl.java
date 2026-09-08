package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetUserProfileUseCaseImpl implements GetUserProfileUseCase {

    private final UserRepository userRepository;

    public GetUserProfileUseCaseImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User getProfile(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("User not found");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
