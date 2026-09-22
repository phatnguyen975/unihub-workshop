package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserCommand;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusUseCase;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.presentation.dto.MemberDetailResponse;
import com.unihub.workshop.iam.presentation.dto.MemberSummaryResponse;
import com.unihub.workshop.iam.presentation.dto.ProvisionUserRequest;
import com.unihub.workshop.iam.presentation.dto.ProvisionUserResponse;
import com.unihub.workshop.iam.presentation.dto.UpdateMemberStatusRequest;
import com.unihub.workshop.iam.presentation.dto.UpdateMemberStatusResponse;
import com.unihub.workshop.iam.presentation.mapper.OrganizationPresentationMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/organizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizationController {

    private final ListOrganizationMembersUseCase listOrganizationMembersUseCase;
    private final GetOrganizationMemberUseCase getOrganizationMemberUseCase;
    private final ProvisionUserUseCase provisionUserUseCase;
    private final UpdateMemberStatusUseCase updateMemberStatusUseCase;
    private final OrganizationPresentationMapper organizationMapper;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProvisionUserResponse> provisionMember(
        @Valid @RequestBody ProvisionUserRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        ProvisionUserCommand command = new ProvisionUserCommand(
            organizationId,
            callerId,
            request.email(),
            request.fullName(),
            request.phoneNumber(),
            request.parseRole()
        );

        User user = provisionUserUseCase.execute(command);
        return ApiResponse.success(
            organizationMapper.toProvisionResponse(user),
            "User provisioned successfully. Temporary credentials sent via email."
        );
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<UpdateMemberStatusResponse> updateMemberStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateMemberStatusRequest request,
        Authentication authentication
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        User user = updateMemberStatusUseCase.execute(
            new UpdateMemberStatusCommand(
                organizationId,
                callerId,
                userId,
                request.isActive()
            )
        );

        return ApiResponse.success(
            organizationMapper.toStatusResponse(user),
            "Member account status updated successfully."
        );
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<MemberDetailResponse> getMemberDetail(
        @PathVariable UUID userId,
        Authentication authentication
    ) {
        UUID organizationId = getVerifiedOrganizationId(authentication);
        User user = getOrganizationMemberUseCase.execute(
            organizationId,
            userId
        );
        return ApiResponse.success(
            organizationMapper.toDetailResponse(user),
            "Member details retrieved"
        );
    }

    @GetMapping("/users")
    public ApiResponse<MemberSummaryResponse.Page> listMembers(
        Authentication authentication,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = getVerifiedOrganizationId(authentication);

        ListMembersQuery query = new ListMembersQuery(
            organizationId,
            callerId,
            parseRole(role),
            isActive,
            search,
            cursor,
            size
        );

        CursorPage<User> page = listOrganizationMembersUseCase.execute(query);

        List<MemberSummaryResponse> items =
            organizationMapper.toSummaryResponseList(page.items());

        MemberSummaryResponse.Page response = new MemberSummaryResponse.Page(
            items,
            new MemberSummaryResponse.Cursor(page.nextCursor(), page.hasMore())
        );

        return ApiResponse.success(response, "Organization members retrieved");
    }

    private UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                "Invalid role filter. Allowed values: ORGANIZER, STAFF, STUDENT"
            );
        }
    }

    private UUID getVerifiedOrganizationId(Authentication authentication) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        return SecurityContextUtils.extractOrganizationId(authentication);
    }
}
