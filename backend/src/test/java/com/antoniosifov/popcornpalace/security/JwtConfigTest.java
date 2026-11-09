package com.antoniosifov.popcornpalace.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test of how the signing key is chosen: no Spring context. */
class JwtConfigTest
{
    private final JwtConfig config = new JwtConfig();

    @DisplayName("The configured secret becomes the signing key")
    @Test
    void usesConfiguredSecret()
    {
        String secret = "a-secret-that-is-at-least-32-bytes-long";

        SecretKey key = config.jwtSigningKey(properties(secret));

        assertThat(key.getEncoded()).isEqualTo(secret.getBytes(StandardCharsets.UTF_8));
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @DisplayName("A secret too short for HS256 stops the startup")
    @Test
    void rejectsShortSecret()
    {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be at least 32 bytes");
    }

    @DisplayName("Without a secret, a fresh random key is generated each time")
    @Test
    void generatesRandomKeyWhenNoSecretIsSet()
    {
        SecretKey first = config.jwtSigningKey(properties(""));
        SecretKey second = config.jwtSigningKey(properties(null));

        assertThat(first.getEncoded()).hasSize(32);
        assertThat(first.getEncoded()).isNotEqualTo(second.getEncoded());
    }

    private static SecurityProperties properties(String secret)
    {
        return new SecurityProperties(new SecurityProperties.Jwt(secret, Duration.ofHours(1), "popcorn-palace"), null);
    }
}
