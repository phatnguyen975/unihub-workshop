package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PasswordResetTokenRepositoryAdapterTest {

    @Test
    void shouldSavePasswordResetToken() {
        PasswordResetTokenJpaRepository jpaRepo = mock(PasswordResetTokenJpaRepository.class);
        PasswordResetTokenRepositoryAdapter adapter = new PasswordResetTokenRepositoryAdapter(jpaRepo);

        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(900);
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", expiresAt);

        when(jpaRepo.save(any(PasswordResetTokenJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        PasswordResetToken result = adapter.save(token);

        ArgumentCaptor<PasswordResetTokenJpaEntity> captor = ArgumentCaptor.forClass(PasswordResetTokenJpaEntity.class);
        verify(jpaRepo).save(captor.capture());
        PasswordResetTokenJpaEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).isEqualTo("hash");
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.userId()).isEqualTo(userId);
    }
}
