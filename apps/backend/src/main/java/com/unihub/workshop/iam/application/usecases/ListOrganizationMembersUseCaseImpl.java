package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListOrganizationMembersUseCaseImpl
    implements ListOrganizationMembersUseCase
{

    private final UserRepository userRepository;

    public ListOrganizationMembersUseCaseImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<User> execute(ListMembersQuery query) {
        if (query == null) {
            throw new BadRequestException("Query must not be null");
        }
        if (query.organizationId() == null) {
            throw new BadRequestException("organizationId must not be null");
        }
        if (query.callerId() == null) {
            throw new BadRequestException("callerId must not be null");
        }

        int limit = query.limit() != null ? query.limit() : 20;
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }

        String search = query.search();
        if (search != null) {
            search = search.trim();
            if (search.isEmpty()) {
                search = null;
            }
        }

        return userRepository.findMembersByOrganizationId(
            query.organizationId(),
            query.callerId(),
            query.role(),
            query.isActive(),
            search,
            query.cursor(),
            limit
        );
    }
}
