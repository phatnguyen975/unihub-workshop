package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserRepositoryAdapterTest {

    @Test
    void shouldFindUserByEmail() {
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(jpaRepo);

        UUID userId = UUID.randomUUID();
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(userId);
        entity.setEmail("student@test.com");
        entity.setRole(UserRole.STUDENT.name());
        entity.setPasswordHash("hash");
        entity.setActive(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(jpaRepo.findByEmail("student@test.com")).thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByEmail("student@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().email()).isEqualTo("student@test.com");
        assertThat(result.get().isActive()).isFalse();
    }

    @Test
    void shouldActivateUser() {
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(jpaRepo);

        UUID userId = UUID.randomUUID();
        adapter.activate(userId);

        verify(jpaRepo).activateUser(eq(userId), any(Instant.class));
    }
}
