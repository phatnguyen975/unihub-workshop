package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.CreateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.OrganizationResponse;
import com.unihub.workshop.iam.application.dto.OrganizationStaffPageResponse;
import com.unihub.workshop.iam.application.dto.ProvisionStaffRequest;
import com.unihub.workshop.iam.application.dto.ProvisionStaffResponse;
import com.unihub.workshop.iam.application.dto.UpdateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusResponse;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.iam.application.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.application.dto.LogoutRequest;
import org.springframework.data.domain.Pageable;

public interface UserUseCase {

    UseCaseResult<UserProfileResponse> getProfile(String userId);

    UseCaseResult<UserProfileResponse> updateProfile(String userId, UpdateProfileRequest request);

    UseCaseResult<OrganizationResponse> createOrganization(String userId, CreateOrganizationRequest request);

    UseCaseResult<OrganizationResponse> updateOrganization(String userId, String organizationId, UpdateOrganizationRequest request);

    UseCaseResult<OrganizationStaffPageResponse> listOrganizationStaff(String userId, String organizationId, Pageable pageable);

    UseCaseResult<ProvisionStaffResponse> provisionInternalAccount(String userId, String organizationId, ProvisionStaffRequest request);

    UseCaseResult<UpdateStaffStatusResponse> manageStaffStatus(String userId, String targetUserId, UpdateStaffStatusRequest request);

    UseCaseResult<com.unihub.workshop.iam.application.dto.LoginResponse> changePassword(String userId, ChangePasswordRequest request);

    UseCaseResult<Void> logout(String userId, LogoutRequest request, String accessToken);
}
