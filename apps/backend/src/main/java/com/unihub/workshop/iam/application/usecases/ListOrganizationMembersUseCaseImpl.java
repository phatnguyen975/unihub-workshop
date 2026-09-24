package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListOrganizationMembersUseCaseImpl
    implements ListOrganizationMembersUseCase
{

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<User> execute(ListMembersQuery query) {
        if (query == null) {
            throw new BadRequestException("Query must not be null");
        }

        return userRepository.findMembersByOrganizationId(
            query.organizationId(),
            query.callerId(),
            query.role(),
            query.isActive(),
            query.search(),
            query.cursor(),
            query.size()
        );
    }
}
