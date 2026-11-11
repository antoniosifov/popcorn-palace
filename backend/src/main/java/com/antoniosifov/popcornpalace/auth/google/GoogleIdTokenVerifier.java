package com.antoniosifov.popcornpalace.auth.google;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Decides whether an ID token really is Google saying "this person signed in,
 * for this application, just now".
 *
 * <p>The token arrives from the web client, which got it from Google. It is
 * checked here anyway, in full, because this is where accounts are opened:
 *
 * <ul>
 *   <li>the signature, against the keys Google publishes (fetched on first use and cached);</li>
 *   <li>the issuer: Google, nobody else;</li>
 *   <li>the audience: this application's client id. Without this check a token that Google
 *       issued to <em>any other</em> site the person signed in to would open their account here;</li>
 *   <li>the expiry;</li>
 *   <li>the nonce: the token answers the sign-in that this browser started, not one replayed
 *       from somewhere else.</li>
 * </ul>
 */
@Component
public class GoogleIdTokenVerifier
{
    /** What Google says about the person who signed in. */
    public record GoogleAccount(String subject, String email, boolean emailVerified, String name)
    {
    }

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private final GoogleProperties properties;
    /** Built on first use: the application must start without Google being reachable. */
    private volatile JwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleProperties properties)
    {
        this.properties = properties;
    }

    /**
     * @param nonce the value the web client sent to Google when it started this sign-in
     * @throws ApiException 404 GOOGLE_SIGN_IN_DISABLED when no client id is configured,
     *                      401 GOOGLE_SIGN_IN_FAILED for a token that fails any check; the answer does not say which
     */
    public GoogleAccount verify(String idToken, String nonce)
    {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.GOOGLE_SIGN_IN_DISABLED,
                    "Signing in with Google is not set up here");
        }
        Jwt token;
        try {
            token = decoder().decode(idToken);
        }
        catch (JwtException exception) {
            // The reason is for whoever runs the service; the caller only learns that it did not work.
            log.info("Rejected a Google ID token: {}", exception.getMessage());
            throw failed();
        }
        if (!sameText(nonce, token.getClaimAsString("nonce"))) {
            log.info("Rejected a Google ID token: it answers another sign-in (nonce)");
            throw failed();
        }
        String email = token.getClaimAsString("email");
        if (token.getSubject() == null || email == null || email.isBlank()) {
            log.info("Rejected a Google ID token: no subject or no email in it");
            throw failed();
        }
        return new GoogleAccount(token.getSubject(), email,
                Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")), token.getClaimAsString("name"));
    }

    private JwtDecoder decoder()
    {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<String>(JwtClaimNames.ISS, properties.issuers()::contains),
                            audienceIsThisApplication()));
                    decoder = current = built;
                }
            }
        }
        return current;
    }

    private OAuth2TokenValidator<Jwt> audienceIsThisApplication()
    {
        OAuth2Error wrongAudience = new OAuth2Error("invalid_token", "The token was issued for another application", null);
        return token -> {
            List<String> audience = token.getAudience();
            return audience != null && audience.contains(properties.clientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(wrongAudience);
        };
    }

    private static boolean sameText(String expected, String actual)
    {
        return expected != null && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static ApiException failed()
    {
        return ApiException.unauthorized(ErrorCode.GOOGLE_SIGN_IN_FAILED, "Signing in with Google did not work. Try again.");
    }
}
