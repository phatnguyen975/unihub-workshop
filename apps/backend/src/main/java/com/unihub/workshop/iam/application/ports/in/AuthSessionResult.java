package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;

public interface AuthSessionResult extends TokenResult {
    User user();
}
