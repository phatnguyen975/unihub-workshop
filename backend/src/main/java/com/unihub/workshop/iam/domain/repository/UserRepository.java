package com.unihub.workshop.iam.domain.repository;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findById(UUID id);

    Page<User> findByOrganizationIdAndRole(UUID organizationId, UserRole role, Pageable pageable);

    User save(User user);
}
