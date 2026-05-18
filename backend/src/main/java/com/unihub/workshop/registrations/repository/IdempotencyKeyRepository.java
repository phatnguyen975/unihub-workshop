package com.unihub.workshop.registrations.repository;

import com.unihub.workshop.registrations.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByIdempotencyKey(String key);
}
