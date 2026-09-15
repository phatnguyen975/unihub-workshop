package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrganizationMemberUseCaseImpl
    implements GetOrganizationMemberUseCase
{

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public User execute(UUID organizationId, UUID memberUserId) {
        if (organizationId == null) {
            throw new BadRequestException("organizationId must not be null");
        }
        if (memberUserId == null) {
            throw new BadRequestException("memberUserId must not be null");
        }

        return userRepository
            .findByIdAndOrganizationId(memberUserId, organizationId)
            .orElseThrow(() ->
                new NotFoundException("Member not found in your organization.")
            );
    }
}
