package com.unihub.workshop.shared.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordGeneratorTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void generate_creates24CharacterPassword_andHashesWithEncoder() {
        when(passwordEncoder.encode(anyString())).thenAnswer(
            invocation -> "hashed_" + invocation.getArgument(0)
        );

        PasswordGenerator generator = new PasswordGenerator(passwordEncoder);
        PasswordGenerator.GeneratedPassword result = generator.generate();

        assertThat(result.plaintext()).hasSize(24);
        assertThat(result.hash()).isEqualTo("hashed_" + result.plaintext());
        verify(passwordEncoder).encode(result.plaintext());
    }

    @Test
    void generate_producesDistinctPasswordsOnSuccessiveCalls() {
        when(passwordEncoder.encode(anyString())).thenReturn("some_hash");

        PasswordGenerator generator = new PasswordGenerator(passwordEncoder);
        PasswordGenerator.GeneratedPassword first = generator.generate();
        PasswordGenerator.GeneratedPassword second = generator.generate();

        assertThat(first.plaintext()).isNotEqualTo(second.plaintext());
    }
}
