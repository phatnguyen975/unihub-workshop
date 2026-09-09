package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;

public interface ListOrganizationMembersUseCase {
    CursorPage<User> execute(ListMembersQuery query);
}
