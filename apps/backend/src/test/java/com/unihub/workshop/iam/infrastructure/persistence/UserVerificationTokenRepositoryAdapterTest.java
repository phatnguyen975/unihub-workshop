package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserVerificationTokenPersistenceMapper;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserVerificationTokenPersistenceMapperImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserVerificationTokenRepositoryAdapterTest {

    private final UserVerificationTokenPersistenceMapper mapper =
        new UserVerificationTokenPersistenceMapperImpl();

    @Test
    @DisplayName("should save UserVerificationToken and map to entity and back")
    void shouldSaveUserVerificationToken() {
        UserVerificationTokenJpaRepository jpaRepo = mock(
            UserVerificationTokenJpaRepository.class
        );
        UserVerificationTokenRepositoryAdapter adapter =
            new UserVerificationTokenRepositoryAdapter(jpaRepo, mapper);

        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(900);
        UserVerificationToken token = UserVerificationToken.createActivationOtp(
            userId,
            "hash",
            expiresAt
        );

        when(
            jpaRepo.save(any(UserVerificationTokenJpaEntity.class))
        ).thenAnswer(i -> i.getArgument(0));

        UserVerificationToken result = adapter.save(token);

        ArgumentCaptor<UserVerificationTokenJpaEntity> captor =
            ArgumentCaptor.forClass(UserVerificationTokenJpaEntity.class);
        verify(jpaRepo).save(captor.capture());
        UserVerificationTokenJpaEntity saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenType()).isEqualTo(
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );
        assertThat(saved.getTokenHash()).isEqualTo("hash");
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.tokenType()).isEqualTo(
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );
    }

    @Test
    @DisplayName("should find latest by user id and token type")
    void shouldFindLatestByUserIdAndType() {
        UserVerificationTokenJpaRepository jpaRepo = mock(
            UserVerificationTokenJpaRepository.class
        );
        UserVerificationTokenRepositoryAdapter adapter =
            new UserVerificationTokenRepositoryAdapter(jpaRepo, mapper);

        UUID userId = UUID.randomUUID();
        UserVerificationTokenJpaEntity entity =
            new UserVerificationTokenJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTokenType(UserVerificationToken.TYPE_ACTIVATION_OTP);
        entity.setTokenHash("hash");
        entity.setExpiresAt(Instant.now().plusSeconds(900));

        when(
            jpaRepo.findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(
                userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP
            )
        ).thenReturn(Optional.of(entity));

        Optional<UserVerificationToken> result =
            adapter.findLatestByUserIdAndType(
                userId,
                UserVerificationToken.TYPE_ACTIVATION_OTP
            );

        assertThat(result).isPresent();
        assertThat(result.get().tokenType()).isEqualTo(
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );
    }

    @Test
    @DisplayName("should mark token consumed")
    void shouldMarkConsumed() {
        UserVerificationTokenJpaRepository jpaRepo = mock(
            UserVerificationTokenJpaRepository.class
        );
        UserVerificationTokenRepositoryAdapter adapter =
            new UserVerificationTokenRepositoryAdapter(jpaRepo, mapper);

        UUID tokenId = UUID.randomUUID();
        when(jpaRepo.markConsumed(eq(tokenId), any(Instant.class))).thenReturn(
            1
        );

        boolean result = adapter.markConsumed(tokenId);

        assertThat(result).isTrue();
        verify(jpaRepo).markConsumed(eq(tokenId), any(Instant.class));
    }

    @Test
    @DisplayName("should invalidate active tokens scoped by type")
    void shouldInvalidateActiveTokens() {
        UserVerificationTokenJpaRepository jpaRepo = mock(
            UserVerificationTokenJpaRepository.class
        );
        UserVerificationTokenRepositoryAdapter adapter =
            new UserVerificationTokenRepositoryAdapter(jpaRepo, mapper);

        UUID userId = UUID.randomUUID();
        adapter.invalidateActiveTokens(
            userId,
            UserVerificationToken.TYPE_ACTIVATION_OTP
        );

        verify(jpaRepo).invalidateActiveTokens(
            eq(userId),
            eq(UserVerificationToken.TYPE_ACTIVATION_OTP),
            any(Instant.class)
        );
    }
}
