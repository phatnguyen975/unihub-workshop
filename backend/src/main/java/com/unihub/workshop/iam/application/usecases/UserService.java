package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.CreateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.OrganizationResponse;
import com.unihub.workshop.iam.application.dto.OrganizationStaffPageResponse;
import com.unihub.workshop.iam.application.dto.OrganizationStaffResponse;
import com.unihub.workshop.iam.application.dto.PaginationMetadata;
import com.unihub.workshop.iam.application.dto.ProvisionStaffRequest;
import com.unihub.workshop.iam.application.dto.ProvisionStaffResponse;
import com.unihub.workshop.iam.application.dto.UpdateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusResponse;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.iam.domain.events.OrganizationCreatedEvent;
import com.unihub.workshop.iam.domain.events.OrganizationUpdatedEvent;
import com.unihub.workshop.iam.domain.events.StaffAccountProvisionedEvent;
import com.unihub.workshop.iam.domain.events.StaffStatusUpdatedEvent;
import com.unihub.workshop.iam.domain.model.Organization;
import com.unihub.workshop.iam.domain.model.OrganizationStatus;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.model.UserStatus;
import com.unihub.workshop.iam.domain.repository.OrganizationRepository;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.shared.exception.AppException;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService implements UserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
    private static final int TEMP_PASSWORD_LENGTH = 16;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
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

        boolean fullNameProvided = request.isFullNameProvided();
        boolean phoneProvided = request.isPhoneProvided();
        if (!fullNameProvided && !phoneProvided) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one field is required", "validation");
        }

        if (fullNameProvided) {
            user.setFullName(normalizeRequiredText(request.fullName(), 120, "full name"));
        }

        if (phoneProvided) {
            String phone = request.phone();
            user.setPhone(phone == null ? null : normalizePhone(phone));
        }

        userRepository.save(user);
        return new UseCaseResult<>(HttpStatus.OK, "Profile updated", toProfileResponse(user));
    }

    @Override
    @Transactional
    public UseCaseResult<OrganizationResponse> createOrganization(String userId, CreateOrganizationRequest request) {
        User organizer = loadOrganizer(userId);
        if (organizationRepository.findByOrganizerId(organizer.getId()).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "Organization already exists", "organization_exists");
        }

        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName(normalizeRequiredText(request.name(), 200, "organization name"));
        organization.setDescription(normalizeOptionalText(request.description(), 2000));
        organization.setAddress(normalizeOptionalText(request.address(), 255));
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setOrganizerId(organizer.getId());
        organizationRepository.save(organization);

        organizer.setOrganizationId(organization.getId());
        userRepository.save(organizer);

        publishEvent(new OrganizationCreatedEvent(organization.getId(), organizer.getId(), organization.getName()));
        return new UseCaseResult<>(HttpStatus.CREATED, "Organization created", toOrganizationResponse(organization));
    }

    @Override
    @Transactional
    public UseCaseResult<OrganizationResponse> updateOrganization(
            String userId,
            String organizationId,
            UpdateOrganizationRequest request) {
        User organizer = loadOrganizer(userId);
        Organization organization = loadOwnedOrganization(organizer, organizationId);

        if (!request.isNameProvided() && !request.isDescriptionProvided() && !request.isAddressProvided()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one field is required", "validation");
        }

        if (request.isNameProvided()) {
            organization.setName(normalizeRequiredText(request.name(), 200, "organization name"));
        }
        if (request.isDescriptionProvided()) {
            organization.setDescription(normalizeOptionalText(request.description(), 2000));
        }
        if (request.isAddressProvided()) {
            organization.setAddress(normalizeOptionalText(request.address(), 255));
        }

        organizationRepository.save(organization);
        publishEvent(new OrganizationUpdatedEvent(organization.getId(), organizer.getId()));
        return new UseCaseResult<>(HttpStatus.OK, "Organization updated", toOrganizationResponse(organization));
    }

    @Override
    public UseCaseResult<OrganizationStaffPageResponse> listOrganizationStaff(
            String userId,
            String organizationId,
            Pageable pageable) {
        User organizer = loadOrganizer(userId);
        Organization organization = loadOwnedOrganization(organizer, organizationId);

        Page<User> staffPage = userRepository.findByOrganizationIdAndRole(organization.getId(), UserRole.STAFF, pageable);
        List<OrganizationStaffResponse> staff = staffPage.getContent().stream()
                .map(this::toOrganizationStaffResponse)
                .toList();

        OrganizationStaffPageResponse response = new OrganizationStaffPageResponse(
            staff,
                new PaginationMetadata(
                        staffPage.getNumber(),
                        staffPage.getSize(),
                        staffPage.getTotalElements(),
                        staffPage.getTotalPages(),
                        staffPage.hasNext(),
                        staffPage.hasPrevious()));

        return new UseCaseResult<>(HttpStatus.OK, "Staff list retrieved", response);
    }

    @Override
    @Transactional
    public UseCaseResult<ProvisionStaffResponse> provisionInternalAccount(
            String userId,
            String organizationId,
            ProvisionStaffRequest request) {
        User organizer = loadOrganizer(userId);
        Organization organization = loadOwnedOrganization(organizer, organizationId);

        String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "Email already exists", "email_exists");
        }

        if (!"STAFF".equals(request.role())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid role", "validation");
        }

        String temporaryPassword = generateTemporaryPassword();
        // TEMP TEST ONLY: remove after QA because this exposes the plaintext temporary password in logs.
        log.info("TEMP TEST ONLY - Generated temporary staff password for {}: {}", email, temporaryPassword);

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmail(email);
        staff.setFullName(normalizeRequiredText(request.fullName(), 120, "full name"));
        staff.setPhone(null);
        staff.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        staff.setRole(UserRole.STAFF);
        staff.setStatus(UserStatus.ACTIVE);
        staff.setOrganizationId(organization.getId());
        staff.setTemporaryPassword(true);
        staff.setFailedLoginAttempts(0);
        staff.setLockoutUntil(null);
        userRepository.save(staff);

        publishEvent(new StaffAccountProvisionedEvent(
                staff.getId(),
                organization.getId(),
                staff.getEmail(),
                staff.getFullName(),
                temporaryPassword));

        ProvisionStaffResponse response = new ProvisionStaffResponse(
                staff.getId().toString(),
                staff.getEmail(),
                staff.getFullName(),
                staff.getRole().name(),
                organization.getId().toString(),
                true);
        return new UseCaseResult<>(HttpStatus.CREATED, "Staff account provisioned", response);
    }

    @Override
    @Transactional
    public UseCaseResult<UpdateStaffStatusResponse> manageStaffStatus(
            String userId,
            String targetUserId,
            UpdateStaffStatusRequest request) {
        User organizer = loadOrganizer(userId);
        User targetUser = loadUser(targetUserId);

        if (targetUser.getRole() != UserRole.STAFF) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only staff accounts can be managed", "validation");
        }

        if (organizer.getOrganizationId() == null || targetUser.getOrganizationId() == null
                || !organizer.getOrganizationId().equals(targetUser.getOrganizationId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Staff account is outside organizer ownership", "forbidden");
        }

        UserStatus targetStatus = UserStatus.valueOf(request.status());
        if (organizer.getId().equals(targetUser.getId()) && targetStatus == UserStatus.LOCKED) {
            throw new AppException(HttpStatus.FORBIDDEN, "Organizer cannot lock own account", "forbidden");
        }

        targetUser.setStatus(targetStatus);
        userRepository.save(targetUser);

        publishEvent(new StaffStatusUpdatedEvent(
                targetUser.getId(),
                targetUser.getOrganizationId(),
                targetStatus.name()));

        UpdateStaffStatusResponse response = new UpdateStaffStatusResponse(
                targetUser.getId().toString(),
                targetStatus.name());
        return new UseCaseResult<>(HttpStatus.OK, "User status updated", response);
    }

    private User loadOrganizer(String userId) {
        User organizer = loadUser(userId);
        if (organizer.getRole() != UserRole.ORGANIZER) {
            throw new AppException(HttpStatus.FORBIDDEN, "Organizer role is required", "forbidden");
        }
        return organizer;
    }

    private Organization loadOwnedOrganization(User organizer, String organizationId) {
        UUID parsedOrganizationId = parseUuid(organizationId, HttpStatus.BAD_REQUEST, "Invalid organization identifier", "validation");
        Organization organization = organizationRepository.findById(parsedOrganizationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Organization not found", "organization_not_found"));
        if (!organizer.getId().equals(organization.getOrganizerId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Organization is outside organizer ownership", "forbidden");
        }
        return organization;
    }

    private User loadUser(String rawId) {
        UUID userId = parseUuid(rawId, HttpStatus.UNAUTHORIZED, "Invalid token subject", "invalid_token");
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found", "user_not_found"));
    }

    private UUID parseUuid(String value, HttpStatus status, String message, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new AppException(status, message, errorCode);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new AppException(status, message, errorCode);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid email", "validation");
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid email", "validation");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        if (value == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName, "validation");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.length() > maxLength) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName, "validation");
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid text length", "validation");
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

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int idx = secureRandom.nextInt(PASSWORD_ALPHABET.length());
            password.append(PASSWORD_ALPHABET.charAt(idx));
        }
        return password.toString();
    }

    private void publishEvent(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Event publication failed", "event_infra");
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

    private OrganizationResponse toOrganizationResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getId() == null ? null : organization.getId().toString(),
                organization.getName(),
                organization.getDescription(),
                organization.getAddress(),
                organization.getStatus() == null ? null : organization.getStatus().name(),
                organization.getOrganizerId() == null ? null : organization.getOrganizerId().toString());
    }

    private OrganizationStaffResponse toOrganizationStaffResponse(User user) {
        return new OrganizationStaffResponse(
                user.getId() == null ? null : user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.isTemporaryPassword());
    }
}
