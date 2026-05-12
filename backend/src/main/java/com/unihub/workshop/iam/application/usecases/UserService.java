package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.shared.exception.AppException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserUseCase {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UseCaseResult<UserProfileResponse> getProfile(String userId) {
        User user = loadUser(userId);
        return new UseCaseResult<>(HttpStatus.OK, "Profile retrieved", toProfileResponse(user));
    }

    @Override
    public UseCaseResult<UserProfileResponse> updateProfile(String userId, UpdateProfileRequest request) {
        String fullName = normalize(request.fullName());
        String phone = normalize(request.phone());

        if (fullName == null && phone == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one field is required", "validation");
        }

        User user = loadUser(userId);
        if (fullName != null) {
            user.setFullName(fullName);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        return new UseCaseResult<>(HttpStatus.OK, "Profile updated", toProfileResponse(user));
    }

    private User loadUser(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid token subject", "invalid_token");
        }
        try {
            UUID userId = UUID.fromString(rawId);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found", "user_not_found"));
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid token subject", "invalid_token");
        }
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId() == null ? null : user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getOrganizationId() == null ? null : user.getOrganizationId().toString(),
                user.getStudentId(),
                user.isTemporaryPassword());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
