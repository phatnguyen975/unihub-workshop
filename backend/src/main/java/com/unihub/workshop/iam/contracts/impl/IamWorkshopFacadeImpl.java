package com.unihub.workshop.iam.contracts.impl;

import com.unihub.workshop.iam.contracts.IamWorkshopFacade;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.domain.repository.OrganizationRepository;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamWorkshopFacadeImpl implements IamWorkshopFacade {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    public boolean verifyStaffExistsInOrganization(UUID staffId, UUID organizationId) {
        return userRepository.findById(staffId)
                .map(user -> user.getRole() == UserRole.STAFF &&
                        organizationId.equals(user.getOrganizationId()))
                .orElse(false);
    }

    @Override
    public boolean verifyUserIsStaff(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == UserRole.STAFF)
                .orElse(false);
    }

    @Override
    public boolean verifyOrganizationExists(UUID organizationId) {
        return organizationRepository.findById(organizationId).isPresent();
    }
}
