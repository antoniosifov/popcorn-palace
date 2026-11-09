package com.antoniosifov.popcornpalace.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * The application signs its own access tokens (HS256) and verifies them on
 * every request. There is no session and no token table: a request is
 * authenticated by the signature, issuer and expiry of the token it carries.
 */
@Configuration
class JwtConfig
{
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties)
    {
        String secret = properties.jwt().secret();
        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            // Safe default: nothing guessable is ever used as the key.
            keyBytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("JWT_SECRET is not set: using a random signing key. "
                    + "Access tokens will stop working when the application restarts.");
        }
        else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "JWT_SECRET must be at least %d bytes long for HS256, but is %d".formatted(MIN_SECRET_BYTES, keyBytes.length));
            }
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey)
    {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties)
    {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Checks expiry and not-before, and that the token was issued by this application.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}
