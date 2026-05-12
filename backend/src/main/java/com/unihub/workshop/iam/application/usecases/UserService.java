package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.shared.exception.AppException;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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
    @Transactional
    public UseCaseResult<UserProfileResponse> updateProfile(String userId, UpdateProfileRequest request) {
        User user = loadUser(userId);

        boolean fullNameProvided = request.fullName() != null && request.fullName().isPresent();
        boolean phoneProvided = request.phone() != null && request.phone().isPresent();
        if (!fullNameProvided && !phoneProvided) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one field is required", "validation");
        }

        if (fullNameProvided) {
            String fullName = normalizeFullName(request.fullName());
            user.setFullName(fullName);
        }

        if (phoneProvided) {
            JsonNullable<String> phoneValue = request.phone();
            String phone = phoneValue.get();
            if (phone == null) {
                user.setPhone(null);
            } else {
                user.setPhone(normalizePhone(phone));
            }
        }

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

    private String normalizeFullName(JsonNullable<String> fullNameValue) {
        String fullName = fullNameValue.get();
        if (fullName == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid full name", "validation");
        }
        String trimmed = fullName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid full name", "validation");
        }
        return trimmed;
    }

    private String normalizePhone(String phone) {
        String trimmed = phone.trim();
        if (trimmed.isEmpty() || !trimmed.matches("^\\+?[0-9\\- ]{7,20}$")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid phone", "validation");
        }
        return trimmed;
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
}
