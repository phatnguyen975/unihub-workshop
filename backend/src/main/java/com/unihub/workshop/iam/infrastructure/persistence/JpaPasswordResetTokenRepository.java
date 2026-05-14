package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import com.unihub.workshop.iam.domain.repository.PasswordResetTokenRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaPasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID>, PasswordResetTokenRepository {

    @Override
    @Query("""
            select t from PasswordResetToken t
            where t.tokenHash = :tokenHash
              and t.usedAt is null
            """)
    Optional<PasswordResetToken> findActiveByTokenHash(@Param("tokenHash") String tokenHash);
}
