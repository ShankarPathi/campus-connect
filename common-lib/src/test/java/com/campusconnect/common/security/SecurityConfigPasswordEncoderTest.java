package com.campusconnect.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigPasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void usesBCryptWithStrength12() {
        String hash = encoder.encode("s3cret-pw");
        // BCrypt with strength 12 → "$2a$12$..."
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void matchesCorrectPassword_andRejectsWrong() {
        String hash = encoder.encode("s3cret-pw");

        assertThat(encoder.matches("s3cret-pw", hash)).isTrue();
        assertThat(encoder.matches("wrong-pw", hash)).isFalse();
    }
}
