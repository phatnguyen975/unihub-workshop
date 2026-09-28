package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.pagination.CursorPage;

public interface ListOrganizationMembersUseCase {
    CursorPage<User> execute(ListMembersQuery query);
}
