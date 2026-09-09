package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.presentation.dto.MemberSummaryResponse;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizationUserController {

    private final ListOrganizationMembersUseCase listOrganizationMembersUseCase;

    @GetMapping
    public ApiResponse<MemberSummaryResponse.Page> listMembers(
            Authentication authentication,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        UUID callerId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = extractAndVerifyOrganizerRole(authentication);

        if (limit == null || limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }

        UserRole userRole = null;
        if (role != null && !role.isBlank()) {
            try {
                userRole = UserRole.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role filter. Allowed values: ORGANIZER, STAFF, STUDENT");
            }
        }

        if (search != null && search.length() > 200) {
            throw new BadRequestException("search parameter cannot exceed 200 characters");
        }

        ListMembersQuery query = new ListMembersQuery(
                organizationId,
                callerId,
                userRole,
                isActive,
                search != null ? search.trim() : null,
                cursor,
                limit);

        CursorPage<User> page = listOrganizationMembersUseCase.execute(query);

        List<MemberSummaryResponse> items = page.items().stream()
                .map(MemberSummaryResponse::from)
                .toList();

        MemberSummaryResponse.Page response = new MemberSummaryResponse.Page(
                items,
                new MemberSummaryResponse.Cursor(page.nextCursor(), page.hasMore()));

        return ApiResponse.success(response, "Organization members retrieved");
    }

    private UUID extractAndVerifyOrganizerRole(Authentication authentication) {
        String role = null;
        String orgStr = null;

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            role = jwtPrincipal.role();
            orgStr = jwtPrincipal.org();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            role = jwt.getClaimAsString("role");
            orgStr = jwt.getClaimAsString("org");
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }

        if (role == null || !"ORGANIZER".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only organizers can access organization members");
        }

        if (orgStr == null || orgStr.isBlank()) {
            throw new ForbiddenException("Missing organization context");
        }

        try {
            return UUID.fromString(orgStr);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid organization context");
        }
    }
}
