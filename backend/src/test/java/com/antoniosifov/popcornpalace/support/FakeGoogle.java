package com.antoniosifov.popcornpalace.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Stands in for Google in tests: publishes signing keys over HTTP, the way
 * Google does, and issues ID tokens signed with them. The application under
 * test fetches the keys and verifies the tokens exactly as it would Google's.
 *
 * <p>One instance for the whole test run, started when first used, on a port
 * of its own.
 */
public final class FakeGoogle
{
    /** The OAuth client id the application under test is configured with. */
    public static final String CLIENT_ID = "popcorn-test.apps.googleusercontent.com";
    public static final String ISSUER = "https://accounts.google.com";

    private static final RSAKey KEY = newKey("google-key-1");
    /** A key Google never published: what a forged token is signed with. */
    private static final RSAKey SOMEBODY_ELSES_KEY = newKey("google-key-1");
    private static final HttpServer SERVER = start();

    private FakeGoogle()
    {
    }

    public static String jwkSetUri()
    {
        return "http://localhost:" + SERVER.getAddress().getPort() + "/oauth2/v3/certs";
    }

    /** An ID token as Google issues it for a person whose email it has confirmed. Change what the test is about. */
    public static IdToken idToken(String subject, String email)
    {
        return new IdToken(subject, email);
    }

    public static final class IdToken
    {
        private final String subject;
        private final String email;
        private boolean emailVerified = true;
        private String name = "Anna Kowalska";
        private String nonce = "nonce-" + UUID.randomUUID();
        private String audience = CLIENT_ID;
        private String issuer = ISSUER;
        private Instant expiresAt = Instant.now().plusSeconds(3600);
        private RSAKey signingKey = KEY;

        private IdToken(String subject, String email)
        {
            this.subject = subject;
            this.email = email;
        }

        public IdToken emailNotConfirmed()
        {
            emailVerified = false;
            return this;
        }

        public IdToken name(String value)
        {
            name = value;
            return this;
        }

        public IdToken nonce(String value)
        {
            nonce = value;
            return this;
        }

        public IdToken issuedFor(String otherClientId)
        {
            audience = otherClientId;
            return this;
        }

        public IdToken issuedBy(String otherIssuer)
        {
            issuer = otherIssuer;
            return this;
        }

        public IdToken expired()
        {
            expiresAt = Instant.now().minusSeconds(120);
            return this;
        }

        public IdToken forged()
        {
            signingKey = SOMEBODY_ELSES_KEY;
            return this;
        }

        /** The nonce the web client would have sent to Google for this sign-in. */
        public String nonce()
        {
            return nonce;
        }

        public String signed()
        {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .claim("email", email)
                    .claim("email_verified", emailVerified)
                    .claim("nonce", nonce)
                    .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                    .expirationTime(Date.from(expiresAt));
            if (name != null) {
                claims.claim("name", name);
            }
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims.build());
            try {
                token.sign(new RSASSASigner(signingKey));
            }
            catch (JOSEException exception) {
                throw new IllegalStateException(exception);
            }
            return token.serialize();
        }
    }

    private static RSAKey newKey(String id)
    {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        }
        catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer start()
    {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] keys = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/oauth2/v3/certs", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, keys.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(keys);
                }
            });
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
