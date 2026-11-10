package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the web client or a reverse proxy every request reaches the API from
 * the same address. With {@code server.forward-headers-strategy=native} the
 * address is taken from {@code X-Forwarded-For} instead, so login attempts are
 * counted per visitor. This needs the real server: the header is applied by
 * Tomcat, before the request reaches Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.forward-headers-strategy=native", "management.server.port=0"})
@Import(TestcontainersConfiguration.class)
class ForwardedClientAddressTest
{
    /** The default of app.rate-limit.auth.max-requests. */
    private static final int LIMIT = 10;

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private StringRedisTemplate redis;

    private final RestClient http = RestClient.create();

    @BeforeEach
    void forgetEarlierAttempts()
    {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @DisplayName("Login attempts are counted per forwarded visitor address, not per proxy")
    @Test
    void countsAttemptsPerForwardedAddress()
    {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(loginFrom("203.0.113.7")).isEqualTo(401);
        }

        assertThat(loginFrom("203.0.113.7")).isEqualTo(429);
        // Another visitor behind the same proxy is not affected.
        assertThat(loginFrom("203.0.113.8")).isEqualTo(401);
    }

    @DisplayName("Through a chain of proxies, the visitor is the address the first trusted proxy saw")
    @Test
    void usesTheAddressNearestToTheTrustedProxies()
    {
        // "198.51.100.1" is what the visitor claims; 203.0.113.9 is who the proxy actually saw.
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            assertThat(loginFrom("198.51.100." + attempt + ", 203.0.113.9")).isEqualTo(401);
        }

        // Changing the claimed part did not buy more attempts.
        assertThat(loginFrom("198.51.100.99, 203.0.113.9")).isEqualTo(429);
    }

    private int loginFrom(String forwardedFor)
    {
        return http.post()
                .uri("http://localhost:" + port + "/api/v1/auth/login")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email": "anna@example.com", "password": "a-wrong-guess"}
                        """)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
