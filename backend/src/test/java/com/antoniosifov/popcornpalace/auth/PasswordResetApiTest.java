package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.outbox.OutboxRelay;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.KafkaTestConsumer;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A forgotten password: asking for the link, and using it. */
class PasswordResetApiTest extends ApiIntegrationTest
{
    private static final String REQUEST = AUTH + "/password-reset";
    private static final String CONFIRM = AUTH + "/password-reset/confirm";
    private static final String ME = "/api/v1/users/me";
    private static final String ACCOUNT_TOPIC = "popcorn.account-events";

    private static final String EMAIL = "anna@example.com";
    private static final String OLD_PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "a-new-horse-battery";

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private KafkaContainer kafka;

    // ------------------------------------------------------------ asking for the link

    @DisplayName("Asking for a link answers the same whether or not the email has an account")
    @Test
    void doesNotSayWhetherTheEmailIsRegistered() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);

        String known = requestReset(EMAIL).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = requestReset("nobody@example.com").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEmpty();
        assertThat(unknown).isEmpty();
    }

    @DisplayName("An email without an account is sent nothing")
    @Test
    void sendsNothingToAnUnknownEmail() throws Exception
    {
        requestReset("nobody@example.com").andExpect(status().isAccepted());

        assertThat(count("password_reset_tokens")).isZero();
        assertThat(count("outbox_events")).isZero();
    }

    @DisplayName("An account gets an event with a link to the site, and only a hash of the link's token is stored")
    @Test
    void storesAHashAndAnnouncesTheLink() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);

        requestReset(" Anna@Example.com ").andExpect(status().isAccepted());

        String event = onlyEvent();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("PasswordResetRequested");
        assertThat(JsonPath.<String>read(event, "$.account.email")).isEqualTo(EMAIL);
        assertThat(JsonPath.<String>read(event, "$.account.displayName")).isEqualTo("Anna");
        String url = JsonPath.read(event, "$.passwordReset.url");
        assertThat(url).startsWith("http://localhost:3000/reset-password?token=");

        String token = tokenIn(url);
        // 32 random bytes, written so that they survive a URL untouched.
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class);
        assertThat(stored).matches("[0-9a-f]{64}").isEqualTo(PasswordResetService.hash(token)).doesNotContain(token);

        Instant expiresAt = Instant.parse(JsonPath.read(event, "$.passwordReset.expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
    }

    @DisplayName("The event with the link is deleted from the outbox as soon as Kafka has it, not kept for a week")
    @Test
    void theLinkDoesNotStayInTheOutbox() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        requestReset(EMAIL).andExpect(status().isAccepted());
        String userId = jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(jdbc.queryForObject("SELECT confidential FROM outbox_events", Boolean.class)).isTrue();

        assertThat(relay.publishPending()).isEqualTo(1);

        assertThat(count("outbox_events")).isZero();
        List<ConsumerRecord<String, String>> delivered = KafkaTestConsumer.readByKey(
                kafka.getBootstrapServers(), ACCOUNT_TOPIC, userId, 1, Duration.ofSeconds(20));
        assertThat(delivered).hasSize(1);
        assertThat(KafkaTestConsumer.header(delivered.getFirst(), OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("PasswordResetRequested");
    }

    @DisplayName("An ordinary event is still kept after it is published")
    @Test
    void ordinaryEventsAreKept() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        long showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        createBooking(showtimeId, seatId(hallId, 1, 1));

        assertThat(relay.publishPending()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Long.class)).isEqualTo(1);
    }

    @DisplayName("One account is sent three links an hour, whoever asks; further requests are answered the same and send nothing")
    @Test
    void limitsEmailsPerAccount() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);

        for (int attempt = 1; attempt <= 5; attempt++) {
            requestReset(EMAIL).andExpect(status().isAccepted());
        }

        assertThat(count("outbox_events")).isEqualTo(3);
        // Another account is not affected.
        requestReset("customer@test.local").andExpect(status().isAccepted());
        assertThat(count("outbox_events")).isEqualTo(4);
    }

    @DisplayName("An invalid email is rejected before anything else happens")
    @Test
    void rejectsAnInvalidEmail() throws Exception
    {
        requestReset("not-an-email")
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    // ------------------------------------------------------------ using the link

    @DisplayName("The link sets a new password: the new one logs in, the old one does not")
    @Test
    void setsANewPassword() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String token = requestedToken(EMAIL);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""));

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        login(EMAIL, OLD_PASSWORD).andExpect(problem(401, "INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL))
                .startsWith("$2").doesNotContain(NEW_PASSWORD);
    }

    @DisplayName("A link works once")
    @Test
    void aLinkWorksOnce() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String token = requestedToken(EMAIL);
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        confirm(token, "yet-another-password").andExpect(problem(400, "RESET_LINK_INVALID"));

        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @DisplayName("Of two simultaneous uses of one link, exactly one sets the password")
    @Test
    void aLinkWorksOnceUnderARace() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String token = requestedToken(EMAIL);

        List<Integer> statuses = SimultaneousRequests.run(8, () ->
                confirm(token, NEW_PASSWORD).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 204).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 400).hasSize(7);
        // The account moved on exactly one version, not once per attempt.
        assertThat(jdbc.queryForObject("SELECT session_version FROM users WHERE email = ?", Integer.class, EMAIL)).isEqualTo(1);
    }

    @DisplayName("A link that has expired is refused")
    @Test
    void anExpiredLinkIsRefused() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String token = requestedToken(EMAIL);
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 second'");

        confirm(token, NEW_PASSWORD).andExpect(problem(400, "RESET_LINK_INVALID"));

        login(EMAIL, OLD_PASSWORD).andExpect(status().isOk());
    }

    @DisplayName("Asking again replaces the earlier link: only the newest email works")
    @Test
    void aNewerLinkReplacesTheOlderOne() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String first = requestedToken(EMAIL);
        String second = requestedToken(EMAIL);

        confirm(first, NEW_PASSWORD).andExpect(problem(400, "RESET_LINK_INVALID"));
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(count("password_reset_tokens")).isZero();
    }

    @DisplayName("A made-up token is refused with the same answer as an expired one")
    @Test
    void anUnknownTokenIsRefused() throws Exception
    {
        confirm("x".repeat(43), NEW_PASSWORD)
                .andExpect(problem(400, "RESET_LINK_INVALID"))
                .andExpect(jsonPath("$.detail").value("This link has expired or has already been used. Ask for a new one."));
    }

    @DisplayName("A password that is not acceptable is rejected without spending the link")
    @Test
    void aRejectedPasswordDoesNotSpendTheLink() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);
        String token = requestedToken(EMAIL);

        confirm(token, "short")
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
        confirm(token, "é".repeat(40)).andExpect(problem(400, "VALIDATION_FAILED"));

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ sessions

    @DisplayName("Whoever was logged in before the password changed is logged out; a new login works")
    @Test
    void endsEarlierSessions() throws Exception
    {
        String before = JsonPath.read(register(EMAIL, OLD_PASSWORD), "$.accessToken");
        whoAmI(before).andExpect(status().isOk());

        confirm(requestedToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        whoAmI(before).andExpect(problem(401, "UNAUTHENTICATED"));
        String after = JsonPath.read(
                login(EMAIL, NEW_PASSWORD).andReturn().getResponse().getContentAsString(), "$.accessToken");
        whoAmI(after).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL));
    }

    @DisplayName("A change of one account's password does not end anybody else's session")
    @Test
    void otherAccountsStayLoggedIn() throws Exception
    {
        register(EMAIL, OLD_PASSWORD);

        confirm(requestedToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        whoAmI(customerToken).andExpect(status().isOk());
    }

    @DisplayName("If Redis forgets, tokens are judged by signature and expiry, as before; nobody is locked out")
    @Test
    void sessionsSurviveAForgetfulRedis() throws Exception
    {
        String before = JsonPath.read(register(EMAIL, OLD_PASSWORD), "$.accessToken");
        confirm(requestedToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());
        whoAmI(before).andExpect(status().isUnauthorized());

        redis.delete(redis.keys("auth:session-version:*"));

        // The documented limit of keeping this in Redis: a restart within the hour forgets it.
        whoAmI(before).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ helpers

    /** Registers Anna and returns the response body. */
    private String register(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "displayName": "Anna"}
                        """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    private ResultActions requestReset(String email) throws Exception
    {
        return anonymous.perform(post(REQUEST).contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(email)));
    }

    private ResultActions confirm(String token, String password) throws Exception
    {
        return anonymous.perform(post(CONFIRM).contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "%s", "password": "%s"}
                """.formatted(token, password)));
    }

    private ResultActions whoAmI(String accessToken) throws Exception
    {
        return anonymous.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
    }

    /** Asks for a link and reads its token from where the email would get it: the newest event in the outbox. */
    private String requestedToken(String email) throws Exception
    {
        requestReset(email).andExpect(status().isAccepted());
        String event = jdbc.queryForObject("SELECT payload::text FROM outbox_events ORDER BY id DESC LIMIT 1", String.class);
        return tokenIn(JsonPath.read(event, "$.passwordReset.url"));
    }

    private String onlyEvent()
    {
        return jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class);
    }

    private static String tokenIn(String url)
    {
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private long count(String table)
    {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
