package com.unihub.workshop.iam.domain.repository;

import com.unihub.workshop.iam.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findById(UUID id);

    User save(User user);
}
